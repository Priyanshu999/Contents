private void saveYuvData(ByteBuffer yuvBuffer, int width, int height, String prefix) {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String filename = prefix + "_" + width + "x" + height + "_" + timestamp + ".yuv";

            // For Android 10+ (API 29+), use MediaStore with Download directory
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/YUV_Files");

                ContentResolver resolver = getContentResolver();
                Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

                if (uri != null) {
                    byte[] yuvData = new byte[yuvBuffer.remaining()];
                    yuvBuffer.get(yuvData);
                    yuvBuffer.rewind();

                    try (OutputStream output = resolver.openOutputStream(uri)) {
                        output.write(yuvData);
                        output.flush();
                        Log.d("YUV_SAVE", "Saved YUV file: " + uri.toString());

                        runOnUiThread(() -> {
                            Toast.makeText(this, "YUV saved to Downloads/YUV_Files: " + filename, Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    Log.e("YUV_SAVE", "Failed to create file URI");
                }
            } else {
                // For Android 9 and below, save to Downloads directory
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File yuvDir = new File(downloadsDir, "YUV_Files");
                if (!yuvDir.exists()) {
                    yuvDir.mkdirs();
                }

                File file = new File(yuvDir, filename);

                byte[] yuvData = new byte[yuvBuffer.remaining()];
                yuvBuffer.get(yuvData);
                yuvBuffer.rewind();

                try (FileOutputStream output = new FileOutputStream(file)) {
                    output.write(yuvData);
                    output.flush();
                    Log.d("YUV_SAVE", "Saved YUV file: " + file.getAbsolutePath());

                    // Notify media scanner
                    MediaScannerConnection.scanFile(this,
                            new String[]{file.getAbsolutePath()},
                            null,
                            (path, uri) -> {
                                Log.d("YUV_SAVE", "Media scan completed for: " + path);
                            });

                    runOnUiThread(() -> {
                        Toast.makeText(this, "YUV saved to Downloads/YUV_Files: " + filename, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        } catch (IOException e) {
            Log.e("YUV_SAVE", "Failed to save YUV file", e);
            runOnUiThread(() -> {
                Toast.makeText(this, "Failed to save YUV file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
        } catch (Exception e) {
            Log.e("YUV_SAVE", "Unexpected error saving YUV file", e);
            runOnUiThread(() -> {
                Toast.makeText(this, "Error saving YUV: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
        }
    }



    public void processYuvImage(ImageReader reader) {
        Image image = reader.acquireLatestImage();

        if (image == null) {
            return;
        }

        try {
            if (image.getFormat() != ImageFormat.YUV_420_888) {
                return;
            }

            int width = image.getWidth();
            int height = image.getHeight();
            int yuvSize = width * height * 3 / 2;
            Image.Plane[] planes = image.getPlanes();
            int y_size = planes[0].getBuffer().remaining();
            int u_size = planes[1].getBuffer().remaining();
            int v_size = planes[2].getBuffer().remaining();
            int total_size = y_size + u_size + v_size;

            if (directbuffer == null || directbuffer.capacity() < total_size) {
                directbuffer = ByteBuffer.allocateDirect(yuvSize);
            } else {
                directbuffer.clear();
            }

            copyYUV420ToBuffer(image, directbuffer);
            directbuffer.rewind();

            if (output_jpg_buffer == null) {
                output_jpg_buffer = ByteBuffer.allocateDirect(width * height * 3);
            } else {
                output_jpg_buffer.rewind();
            }
            int type = 12;
            int csp = 2;
            initiate_encoding(width, height, csp, currentQpValue);
            encode(directbuffer, output_jpg_buffer);
            deinit_encoding();

            byte[] yuvData = new byte[output_jpg_buffer.remaining()];
            output_jpg_buffer.get(yuvData);
            output_jpg_buffer.rewind();
            ByteBuffer buffer = output_jpg_buffer.duplicate();
            buffer.rewind();
            byte[] nv21Bytes = convertPlanarYUV422ToNV21(buffer, width, height);
            YuvImage yuvImage = new YuvImage(nv21Bytes, ImageFormat.NV21, width, height, null);

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());

            // Save first image with user-set quality
            ByteArrayOutputStream out1 = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, width, height), currentQualityValue, out1);
            byte[] jpegData1 = out1.toByteArray();
            jpegData1 = rotateImage(jpegData1);

            String filename1 = "3_Prep_NV21_Q" + currentQualityValue + "_QP" + currentQpValue + "_" + timestamp + ".jpg";
            File file1 = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), filename1);

            try (FileOutputStream output = new FileOutputStream(file1)) {
                output.write(jpegData1);
                output.flush();
                runOnUiThread(() -> Toast.makeText(this, "Pre-processed image: " + file1.getAbsolutePath(), Toast.LENGTH_SHORT).show());
                MediaScannerConnection.scanFile(this, new String[]{file1.getAbsolutePath()}, null, null);
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, "Failed to save JPEG (Quality " + currentQualityValue + ")", Toast.LENGTH_SHORT).show());
            }

            int secondQuality = Math.max(1, currentQualityValue - 25); // Ensure quality doesn't go below 1
            ByteArrayOutputStream out2 = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, width, height), secondQuality, out2);
            byte[] jpegData2 = out2.toByteArray();
            jpegData2 = rotateImage(jpegData2);

            String filename2 = "3_Prep_NV21_Q" + secondQuality + "_QP" + currentQpValue + "_" + timestamp + ".jpg";
            File file2 = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), filename2);

            try (FileOutputStream output = new FileOutputStream(file2)) {
                output.write(jpegData2);
                output.flush();
                runOnUiThread(() -> Toast.makeText(this, "Pre-processed image: " + file2.getAbsolutePath(), Toast.LENGTH_SHORT).show());
                MediaScannerConnection.scanFile(this, new String[]{file2.getAbsolutePath()}, null, null);
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, "Failed to save JPEG (Quality " + secondQuality + ")", Toast.LENGTH_SHORT).show());
            }

        } catch (Exception e) {
        } finally {
            image.close();
        }
    }

    public void processYuvYuyImage(ImageReader reader) {
        Image image = reader.acquireLatestImage();

        if (image == null) {
            return;
        }

        try {
            if (image.getFormat() != ImageFormat.YUV_420_888) {
                return;
            }

            int width = image.getWidth();
            int height = image.getHeight();
            int yuvSize = width * height * 3 / 2;
            Image.Plane[] planes = image.getPlanes();
            int y_size = planes[0].getBuffer().remaining();
            int u_size = planes[1].getBuffer().remaining();
            int v_size = planes[2].getBuffer().remaining();
            int total_size = y_size + u_size + v_size;

            if (directbuffer == null || directbuffer.capacity() < total_size) {
                directbuffer = ByteBuffer.allocateDirect(yuvSize);
            } else {
                directbuffer.clear();
            }

            copyYUV420ToBuffer(image, directbuffer);
            directbuffer.rewind();

            // Save original YUV420 data (only once per capture)
            saveYuvData(directbuffer.duplicate(), width, height, "ORIGINAL_YUV420");

            if (output_jpg_buffer == null) {
                output_jpg_buffer = ByteBuffer.allocateDirect(width * height * 3);
            } else {
                output_jpg_buffer.rewind();
            }

            if (currentTestMode == TEST_MODE_VARY_QUALITY) {
                // Test Mode 1: Fixed QP=30, vary quality
                int fixedQP = 30;
                int csp = 2;
                initiate_encoding(width, height, csp, fixedQP);
                double[] psnrValues = encode(directbuffer, output_jpg_buffer);
                if (psnrValues != null && psnrValues.length >= 4) {
                    displayPSNRResults(psnrValues);
                }
                deinit_encoding();

                byte[] yuvData = new byte[output_jpg_buffer.remaining()];
                output_jpg_buffer.get(yuvData);
                output_jpg_buffer.rewind();
                ByteBuffer buffer = output_jpg_buffer.duplicate();
                buffer.rewind();

                byte[] yuy2Bytes = convertPlanarYUV422ToYUY2(buffer, width, height);
                YuvImage yuvImage = new YuvImage(yuy2Bytes, ImageFormat.YUY2, width, height, null);

                // Save with multiple quality values
                int[] qualityValues = {10, 25, 50, 75, 100};
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());

                for (int quality : qualityValues) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    yuvImage.compressToJpeg(new Rect(0, 0, width, height), quality, out);
                    byte[] jpegData = out.toByteArray();
                    jpegData = rotateImage(jpegData);

                    String filename = "PREP_YUY2_Q" + quality + "_QP" + fixedQP + "_" + timestamp + ".jpg";
                    File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), filename);

                    try (FileOutputStream output = new FileOutputStream(file)) {
                        output.write(jpegData);
                        output.flush();
                        Log.d("SAVE", "Saved: " + file.getAbsolutePath());
                        MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null, null);
                    }
                }
            } else {
                // Test Mode 2: Fixed quality=75, vary QP
                int fixedQuality = 75;
                int[] qpValues = {0, 5, 10, 15, 25, 40, 50};
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());

                for (int qp : qpValues) {
                    output_jpg_buffer.clear();
                    int csp = 2;
                    initiate_encoding(width, height, csp, qp);
                    double[] psnrValues = encode(directbuffer, output_jpg_buffer);
                    if (psnrValues != null && psnrValues.length >= 4) {
                        displayPSNRResults(psnrValues);
                    }
                    deinit_encoding();

                    byte[] yuvData = new byte[output_jpg_buffer.remaining()];
                    output_jpg_buffer.get(yuvData);
                    output_jpg_buffer.rewind();
                    ByteBuffer buffer = output_jpg_buffer.duplicate();
                    buffer.rewind();

                    byte[] yuy2Bytes = convertPlanarYUV422ToYUY2(buffer, width, height);
                    YuvImage yuvImage = new YuvImage(yuy2Bytes, ImageFormat.YUY2, width, height, null);

                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    yuvImage.compressToJpeg(new Rect(0, 0, width, height), fixedQuality, out);
                    byte[] jpegData = out.toByteArray();
                    jpegData = rotateImage(jpegData);

                    String filename = "PREP_YUY2_Q" + fixedQuality + "_QP" + qp + "_" + timestamp + ".jpg";
                    File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), filename);

                    try (FileOutputStream output = new FileOutputStream(file)) {
                        output.write(jpegData);
                        output.flush();
                        Log.d("SAVE", "Saved: " + file.getAbsolutePath());
                        MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null, null);
                    }
                }
            }

        } catch (Exception e) {
            Log.e("processYuvYuyImage", "Error processing image", e);
        } finally {
            image.close();
        }
    }

    // Modified processJpegYuvImage method
    public void processJpegYuvImage(ImageReader reader) {
        Image image = reader.acquireLatestImage();

        if (image == null) {
            return;
        }
        try {
            if (image.getFormat() != ImageFormat.YUV_420_888) {
                return;
            }

            int width = image.getWidth();
            int height = image.getHeight();
            int yuvSize = width * height * 3 / 2;
            Image.Plane[] planes = image.getPlanes();
            int y_size = planes[0].getBuffer().remaining();
            int u_size = planes[1].getBuffer().remaining();
            int v_size = planes[2].getBuffer().remaining();
            int total_size = y_size + u_size + v_size;

            if (directbuffer == null || directbuffer.capacity() < total_size) {
                directbuffer = ByteBuffer.allocateDirect(yuvSize);
            } else {
                directbuffer.clear();
            }

            copyYUV420ToBuffer(image, directbuffer);
            directbuffer.rewind();

            byte[] yuvData = new byte[directbuffer.remaining()];
            directbuffer.get(yuvData);
            directbuffer.rewind();
            ByteBuffer buffer = directbuffer.duplicate();
            buffer.rewind();
            byte[] nv21Bytes = convertPlanarYUV420ToNV21(buffer, width, height);
            YuvImage yuvImage = new YuvImage(nv21Bytes, ImageFormat.NV21, width, height, null);

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());

            if (currentTestMode == TEST_MODE_VARY_QUALITY) {
                // Test Mode 1: Fixed QP=30 (not used in this path), vary quality
                int[] qualityValues = {10, 25, 50, 75, 100};

                for (int quality : qualityValues) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    yuvImage.compressToJpeg(new Rect(0, 0, width, height), quality, out);
                    byte[] jpegData = out.toByteArray();
                    jpegData = rotateImage(jpegData);

                    String filename = "CASCADE_Q" + quality + "_" + timestamp + ".jpg";
                    File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), filename);

                    try (FileOutputStream output = new FileOutputStream(file)) {
                        output.write(jpegData);
                        output.flush();
                        Log.d("SAVE", "Saved: " + file.getAbsolutePath());
                        MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null, null);
                    }
                }
            } else {
                // Test Mode 2: Fixed quality=75
                int fixedQuality = 75;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                yuvImage.compressToJpeg(new Rect(0, 0, width, height), fixedQuality, out);
                byte[] jpegData = out.toByteArray();
                jpegData = rotateImage(jpegData);

                String filename = "CASCADE_Q" + fixedQuality + "_" + timestamp + ".jpg";
                File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), filename);

                try (FileOutputStream output = new FileOutputStream(file)) {
                    output.write(jpegData);
                    output.flush();
                    Log.d("SAVE", "Saved: " + file.getAbsolutePath());
                    MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null, null);
                }
            }

        } catch (Exception e) {
            Log.e("processJpegYuvImage", "Error processing image", e);
        } finally {
            image.close();
        }
    }

    // Add a method to switch between test modes
    public void setTestMode(int mode) {
        currentTestMode = mode;
        runOnUiThread(() -> {
            String modeText = (mode == TEST_MODE_VARY_QUALITY) ? "Vary Quality Mode" : "Vary QP Mode";
            Toast.makeText(this, "Test Mode: " + modeText, Toast.LENGTH_LONG).show();
        });
    }



    public void processYuv444(ImageReader reader) {
        Image image = reader.acquireLatestImage();

        if (image == null) {
            return;
        }


        try {
            if (image.getFormat() != ImageFormat.YUV_420_888) {
                return;
            }

            int width = image.getWidth();
            int height = image.getHeight();
            int yuvSize = width * height * 3 / 2;
            Image.Plane[] planes = image.getPlanes();
            int y_size = planes[0].getBuffer().remaining();
            int u_size = planes[1].getBuffer().remaining();
            int v_size = planes[2].getBuffer().remaining();
            int total_size = y_size +u_size+v_size;

            if (directbuffer == null || directbuffer.capacity() < total_size) {
                directbuffer = ByteBuffer.allocateDirect(yuvSize);
            } else {
                directbuffer.clear();
            }

            copyYUV420ToBuffer(image, directbuffer);
            directbuffer.rewind();


            if (output_jpg_buffer == null) {
                output_jpg_buffer = ByteBuffer.allocateDirect(width * height * 3);
            } else {
                output_jpg_buffer.rewind();
            }
            int type = 13;
            int csp = 3;
            initiate_encoding(width, height, csp, currentQpValue);
            encode(directbuffer, output_jpg_buffer);
            deinit_encoding();

            byte[] yuvData = new byte[output_jpg_buffer.remaining()];
            output_jpg_buffer.get(yuvData);
            output_jpg_buffer.rewind();

            Bitmap rgbBitmap = convertYuv444ToRgbBitmap(yuvData, width, height);
            byte[] jpegData = compressBitmapToJpeg(rgbBitmap, currentQualityValue);
            jpegData = rotateImage(jpegData);

            if (rgbBitmap != null && !rgbBitmap.isRecycled()){
                rgbBitmap.recycle();
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String filename = "PROCESSED_YUV444_" + timestamp + ".jpg";
            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), filename);

            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(jpegData);
                output.flush();
            runOnUiThread(() -> Toast.makeText(this, "Compressed using YUV444 JPEG saved: " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show());
                MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null, null);
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, "Failed to save JPEG", Toast.LENGTH_SHORT).show());
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            image.close();
        }
    }

    private void copyYUV420ToBuffer(Image image, ByteBuffer outputBuffer) {
        int width = image.getWidth();
        int height = image.getHeight();

        Image.Plane[] planes = image.getPlanes();

        ByteBuffer yPlane = planes[0].getBuffer();
        ByteBuffer uPlane = planes[1].getBuffer();
        ByteBuffer vPlane = planes[2].getBuffer();

        int yRowStride = planes[0].getRowStride();
        int yPixelStride = planes[0].getPixelStride();

        int uRowStride = planes[1].getRowStride();
        int uPixelStride = planes[1].getPixelStride();

        int vRowStride = planes[2].getRowStride();
        int vPixelStride = planes[2].getPixelStride();
        int countY=0, countU=0, countV=0;

        for (int row = 0; row < height; row++) {
            int yOffset = row * yRowStride;
            for (int col = 0; col < width; col++) {
                outputBuffer.put(yPlane.get(yOffset + col * yPixelStride));
                countY++;
            }
        }

        int chromaWidth = width / 2;
        int chromaHeight = height / 2;

        for (int row = 0; row < chromaHeight; row++) {
            int uOffset = row * uRowStride;
            for (int col = 0; col < chromaWidth; col++) {
                outputBuffer.put(uPlane.get(uOffset + col * uPixelStride));
                countU++;
            }
        }

        for (int row = 0; row < chromaHeight; row++) {
            int vOffset = row * vRowStride;
            for (int col = 0; col < chromaWidth; col++) {
                outputBuffer.put(vPlane.get(vOffset + col * vPixelStride));
                countV++;
            }
        }
        outputBuffer.rewind();
    }


    protected void updatePreview() {
        if (cameraDevice == null) {
            return;
        }

        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
        }
    }

    private void closeCamera() {
        if (cameraCaptureSessions != null) {
            cameraCaptureSessions.close();
            cameraCaptureSessions = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        isRequestingPermissions = false;

        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show();
                setupCamera();
            } else {
                Toast.makeText(this, "Camera permission is required to use this app", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isCameraInitialized && textureView.isAvailable() && cameraDevice == null) {
            openCamera();
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        closeCamera();
        cleanupNativeResources();
        stopBackgroundThread();

        if (processingExecutor != null) {
            processingExecutor.shutdown();
        }

        super.onDestroy();
    }

    private void cleanupNativeResources() {
        synchronized (this) {
            if (state == 1) {
                deinit_encoding();
                state = 0;
            }
        }
    }

    // Native methods for ISP processing
    public static synchronized native void initiate_encoding(int width, int height, int csp, int qp);
    public static synchronized native double[] encode(ByteBuffer input, ByteBuffer recon);
    public static synchronized native void deinit_encoding();

    static {
        System.loadLibrary("nisp_jni");
    }
}
