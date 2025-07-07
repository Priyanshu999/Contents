package com.example.demoisp;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ExifInterface;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore; 
import java.io.OutputStream;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class demorawrec extends AppCompatActivity {
    private static final String TAG = "AndroidCameraApi";
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    // UI Components
    private TextureView textureView;

    // Camera Components
    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;

    // Background Processing
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private ExecutorService processingExecutor;

    private SeekBar qpSeekBar;
    private SeekBar qualitySeekBar;
    private TextView qpValueText;
    private TextView qualityValueText;
    private int currentQpValue = 25; // Default QP value (0-51)
    private int currentQualityValue = 85; // Default quality value (1-100)

    // Image Processing
    private ByteBuffer output_jpg_buffer;
    private ByteBuffer directbuffer;
    private float state = 0;
    private int output_image_width = 4080;
    private int output_image_height = 3060;

    // State tracking
    private boolean isCameraInitialized = false;
    private boolean isRequestingPermissions = false;

    private static final int TEST_MODE_VARY_QUALITY = 1;
    private static final int TEST_MODE_VARY_QP = 2;
    private int currentTestMode = TEST_MODE_VARY_QUALITY;

    private void initializeTestModeButton() {
        Button testModeButton = findViewById(R.id.btn_test_mode);

        // Set initial text based on current mode
        updateTestModeButtonText(testModeButton);

        testModeButton.setOnClickListener(v -> {
            // Toggle between test modes
            if (currentTestMode == TEST_MODE_VARY_QUALITY) {
                currentTestMode = TEST_MODE_VARY_QP;
            } else {
                currentTestMode = TEST_MODE_VARY_QUALITY;
            }

            updateTestModeButtonText(testModeButton);

            // Show toast to confirm mode change
            String modeText = (currentTestMode == TEST_MODE_VARY_QUALITY)
                    ? "Vary Quality Mode (QP=30)"
                    : "Vary QP Mode (Quality=75)";
            Toast.makeText(demorawrec.this, "Switched to: " + modeText, Toast.LENGTH_SHORT).show();
        });
    }

    private void updateTestModeButtonText(Button button) {
        String buttonText = (currentTestMode == TEST_MODE_VARY_QUALITY)
                ? "Mode: Vary Quality"
                : "Mode: Vary QP";
        button.setText(buttonText);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // Initialize UI components
        textureView = findViewById(R.id.texture);
        processingExecutor = Executors.newSingleThreadExecutor();

        initializeSeekBars();
        initializeTestModeButton();

        Button takePictureButton = findViewById(R.id.btn_takepicture);
        takePictureButton.setOnClickListener(v -> {
            Toast.makeText(this, "Capturing image...", Toast.LENGTH_SHORT).show();
            takePicture();
        });

        // Check permissions first
        if (checkCameraPermissions()) {
            // If permissions are already granted, set up the camera
            setupCamera();
        } else {
            // Request permissions
            requestCameraPermissions();
        }

    }

    private boolean checkCameraPermissions() {
        // For Android 10+ (API 29+), external storage permissions work differently
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // On Android 10+, we only need CAMERA permission for basic functionality
            // Storage access is handled through scoped storage
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        } else {
            // For older versions, check all permissions
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestCameraPermissions() {
        if (!isRequestingPermissions) {
            isRequestingPermissions = true;

            // For Android 13+ (API 33+), use READ_MEDIA_IMAGES instead of READ_EXTERNAL_STORAGE
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.READ_MEDIA_IMAGES
                }, REQUEST_CAMERA_PERMISSION);
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10-12: Only request CAMERA permission
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.CAMERA
                }, REQUEST_CAMERA_PERMISSION);
            } else {
                // Android 9 and below: Request all permissions
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                }, REQUEST_CAMERA_PERMISSION);
            }
        }
    }

    private void setupCamera() {
        if (!isCameraInitialized) {
            isCameraInitialized = true;
            startBackgroundThread();
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (mBackgroundThread != null) {
            mBackgroundThread.quitSafely();
            try {
                mBackgroundThread.join();
                mBackgroundThread = null;
                mBackgroundHandler = null;
            } catch (InterruptedException e) {
            }
        }
    }

    byte[] rotateImage(byte[] jpegData) {
        try {
            Bitmap bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
            ExifInterface exif = new ExifInterface(new ByteArrayInputStream(jpegData));
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);

            int rotation = orientation == 3 ? 180 : orientation == 6 ? 90 : orientation == 8 ? 270 : 0;
            if (rotation == 0) return jpegData;

            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            rotated.compress(Bitmap.CompressFormat.JPEG, 100, out);
            bitmap.recycle();
            rotated.recycle();
            return out.toByteArray();
        } catch (Exception e) {
            return jpegData;
        }
    }

    private final TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
        }
    };

    private void openCamera() {
        // Prevent multiple camera openings
        if (cameraDevice != null) {
            return;
        }

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            cameraId = manager.getCameraIdList()[0];

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];

            // Double-check permissions before opening camera
            if (!checkCameraPermissions()) {
                return;
            }

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            manager.openCamera(cameraId, stateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Toast.makeText(this, "Camera access failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            closeCamera();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            closeCamera();
            Toast.makeText(demorawrec.this, "Camera error: " + error, Toast.LENGTH_LONG).show();
        }
    };

    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;

            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if (cameraDevice == null) {
                        return;
                    }
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(demorawrec.this, "Preview setup failed", Toast.LENGTH_SHORT).show();
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
        }
    }

    private void initializeSeekBars() {
        qpSeekBar = findViewById(R.id.qp_seekbar);
        qpValueText = findViewById(R.id.qp_value_text);

        qpSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentQpValue = progress;
                qpValueText.setText(String.valueOf(currentQpValue));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Toast.makeText(demorawrec.this, "QP Value: " + currentQpValue, Toast.LENGTH_SHORT).show();
            }
        });

        qualitySeekBar = findViewById(R.id.quality_seekbar);
        qualityValueText = findViewById(R.id.quality_value_text);

        qualitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                currentQualityValue = Math.max(1, progress);
                qualityValueText.setText(String.valueOf(currentQualityValue));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Toast.makeText(demorawrec.this, "Quality: " + currentQualityValue, Toast.LENGTH_SHORT).show();
            }
        });

        qpSeekBar.setProgress(currentQpValue);
        qualitySeekBar.setProgress(currentQualityValue);
        qpValueText.setText(String.valueOf(currentQpValue));
        qualityValueText.setText(String.valueOf(currentQualityValue));
    }


    protected void takePicture() {
        if (cameraDevice == null) {
            Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        ImageReader jpegReader = null;
        ImageReader yuvReader = null;
        ImageReader jpegYuvReader = null;
        ImageReader yuvReaderYuy = null;
        ImageReader yuvReader444 = null;

        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] jpegSizes = map.getOutputSizes(ImageFormat.JPEG);

            Size jpegSize = (jpegSizes != null && jpegSizes.length > 0) ? jpegSizes[0] : new Size(640, 480);


            jpegReader = ImageReader.newInstance(jpegSize.getWidth(), jpegSize.getHeight(), ImageFormat.JPEG, 1);
            jpegYuvReader = ImageReader.newInstance(jpegSize.getWidth(), jpegSize.getHeight(), ImageFormat.YUV_420_888, 1);
            yuvReader = ImageReader.newInstance(jpegSize.getWidth(), jpegSize.getHeight(), ImageFormat.YUV_420_888, 1);
            yuvReaderYuy = ImageReader.newInstance(jpegSize.getWidth(), jpegSize.getHeight(), ImageFormat.YUV_420_888, 1);
//            yuvReader444 = ImageReader.newInstance(jpegSize.getWidth(), jpegSize.getHeight(), ImageFormat.YUV_420_888, 1);


            List<Surface> outputSurfaces = new ArrayList<>(7);
            outputSurfaces.add(jpegReader.getSurface());
            outputSurfaces.add(yuvReader.getSurface());
            outputSurfaces.add(jpegYuvReader.getSurface());
            outputSurfaces.add(yuvReaderYuy.getSurface());
//            outputSurfaces.add(yuvReader444.getSurface());
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));

            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(jpegReader.getSurface());
            captureBuilder.addTarget(yuvReader.getSurface());
            captureBuilder.addTarget(jpegYuvReader.getSurface());
            captureBuilder.addTarget(yuvReaderYuy.getSurface());
//            captureBuilder.addTarget(yuvReader444.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            jpegReader.setOnImageAvailableListener(reader -> {
                processJpegImage(reader);
            }, mBackgroundHandler);

            yuvReader.setOnImageAvailableListener(reader -> {
                processYuvImage(reader);
            }, mBackgroundHandler);

            jpegYuvReader.setOnImageAvailableListener(reader -> {
                processJpegYuvImage(reader);
            }, mBackgroundHandler);

            yuvReaderYuy.setOnImageAvailableListener(reader -> {
                processYuvYuyImage(reader);
            }, mBackgroundHandler);

//            yuvReader444.setOnImageAvailableListener(reader -> {
//                processYuv444(reader);
//            }, mBackgroundHandler);

            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                           @NonNull CaptureRequest request,
                                                           @NonNull TotalCaptureResult result) {
                                super.onCaptureCompleted(session, request, result);
                                createCameraPreview();
                            }
                        }, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Toast.makeText(demorawrec.this, "Capture setup failed", Toast.LENGTH_SHORT).show();
                }
            }, mBackgroundHandler);

        } catch (CameraAccessException e) {
        }
    }

    private void processJpegImage(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            bytes = rotateImage(bytes);
            byte[] finalBytes = bytes;
            processingExecutor.execute(() -> {
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                String filename = "1_DIRECT_" + timestamp + ".jpg";
                File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), filename);

                try (FileOutputStream output = new FileOutputStream(file)) {
                    output.write(finalBytes);
                    output.flush();

                    runOnUiThread(() -> Toast.makeText(this, "Direct image saved: " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show());
                    MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null, null);
                } catch (IOException e) {
                    runOnUiThread(() -> Toast.makeText(this, "Failed to save direct image", Toast.LENGTH_SHORT).show());
                }
            });
        } catch (Exception e) {
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }


    public static byte[] convertPlanarYUV422ToNV21(ByteBuffer buffer, int width, int height) {
        int ySize = width * height;
        int chromaWidth = width / 2;
        int chromaHeight = height;
        int uSize = chromaWidth * chromaHeight;
        int vSize = uSize;

        byte[] nv21 = new byte[ySize + (width * height / 2)];
        byte[] yuvData = new byte[buffer.remaining()];
        buffer.get(yuvData);

        int yOffset = 0;
        int uOffset = ySize;
        int vOffset = uOffset + uSize;

        System.arraycopy(yuvData, yOffset, nv21, 0, ySize);

        int chromaOutOffset = ySize;
        for (int row = 0; row < height; row += 2) {
            for (int col = 0; col < chromaWidth; col++) {
                int index1 = row * chromaWidth + col;
                int index2 = (row + 1 < chromaHeight ? (row + 1) * chromaWidth + col : index1);

                int u = ((yuvData[uOffset + index1] & 0xFF) + (yuvData[uOffset + index2] & 0xFF)) / 2;
                int v = ((yuvData[vOffset + index1] & 0xFF) + (yuvData[vOffset + index2] & 0xFF)) / 2;

                if (chromaOutOffset + 1 < nv21.length) {
                    nv21[chromaOutOffset++] = (byte) v;
                    nv21[chromaOutOffset++] = (byte) u;
                }
            }
        }

        return nv21;
    }

    public static byte[] convertPlanarYUV420ToNV21(ByteBuffer buffer, int width, int height) {
        int ySize = width * height;
        int chromaWidth = width / 2;
        int chromaHeight = height / 2;
        int chromaSize = chromaWidth * chromaHeight;
        int vSize = chromaSize;

        byte[] nv21 = new byte[ySize + chromaSize*2];
        byte[] yuvData = new byte[buffer.remaining()];
        buffer.get(yuvData);

        int yOffset = 0;
        int uOffset = ySize;
        int vOffset = uOffset + chromaSize;

        System.arraycopy(yuvData, yOffset, nv21, 0, ySize);
        int uvIndex = ySize;
        for (int i = 0; i < chromaSize; i++){
            nv21[uvIndex++] = yuvData[vOffset + i];
            nv21[uvIndex++] = yuvData[uOffset + i];
        }

        return nv21;
    }

    public static byte[] convertPlanarYUV422ToYUY2(ByteBuffer buffer, int width, int height) {
        int ySize = width * height;
        int chromaWidth = width / 2;
        int chromaHeight = height;
        int uSize = chromaWidth * chromaHeight;
        int vSize = uSize;

        // YUY2 requires 2 bytes per pixel (packed format)
        byte[] yuy2 = new byte[width * height * 2];
        byte[] yuvData = new byte[buffer.remaining()];
        buffer.get(yuvData);

        int yOffset = 0;
        int uOffset = ySize;
        int vOffset = uOffset + uSize;

        int yuy2Index = 0;

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col += 2) {
                // Get Y values for both pixels
                int y0Index = row * width + col;
                int y1Index = row * width + col + 1;

                // Get U and V values (shared between pixel pair)
                int chromaRow = row;
                int chromaCol = col / 2;
                int chromaIndex = chromaRow * chromaWidth + chromaCol;

                byte y0 = yuvData[yOffset + y0Index];
                byte y1 = (col + 1 < width) ? yuvData[yOffset + y1Index] : y0;
                byte u = yuvData[uOffset + chromaIndex];
                byte v = yuvData[vOffset + chromaIndex];

                // Pack into YUY2 format: Y0-U-Y1-V
                yuy2[yuy2Index++] = y0;
                yuy2[yuy2Index++] = u;
                yuy2[yuy2Index++] = y1;
                yuy2[yuy2Index++] = v;
            }
        }

        return yuy2;
    }

    private Bitmap convertYuv444ToRgbBitmap(byte[] yuvData, int width, int height) {
        int[] rgbPixels = new int[width * height];

        for (int i = 0; i < width * height; i++) {
            int y = yuvData[i] & 0xFF;
            int u = yuvData[width * height + i] & 0xFF;
            int v = yuvData[2 * width * height + i] & 0xFF;

            // Convert YUV to RGB using standard conversion formulas
            int r = (int) (y + 1.402 * (v - 128));
            int g = (int) (y - 0.344136 * (u - 128) - 0.714136 * (v - 128));
            int b = (int) (y + 1.772 * (u - 128));

            // Clamp values to 0-255
            r = Math.max(0, Math.min(255, r));
            g = Math.max(0, Math.min(255, g));
            b = Math.max(0, Math.min(255, b));

            // Pack RGB into ARGB format
            rgbPixels[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }

        return Bitmap.createBitmap(rgbPixels, width, height, Bitmap.Config.ARGB_8888);
    }


    private byte[] compressBitmapToJpeg(Bitmap bitmap, int quality) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        boolean success = bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);

        if (success) {
            return outputStream.toByteArray();
        } else {
            return null;
        }
    }




    private void displayPSNRResults(double[] psnrValues) {
        if (psnrValues != null && psnrValues.length >= 4) {
            String psnrMessage = String.format(Locale.US,
                    "PSNR - Y: %.2f dB, U: %.2f dB, V: %.2f dB, Avg: %.2f dB",
                    psnrValues[0], psnrValues[1], psnrValues[2], psnrValues[3]);

            runOnUiThread(() -> {
                Toast.makeText(this, psnrMessage, Toast.LENGTH_LONG).show();
            });

            // Log the PSNR values
            Log.d("PSNR", psnrMessage);
        }
    }

