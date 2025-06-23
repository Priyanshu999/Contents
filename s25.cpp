#include <jni.h>
#include <iostream>
#include <chrono>
#include <android/log.h>
#include "oapv.h"
#include "oapv_app_util.h"
#include <android/file_descriptor_jni.h>
using namespace std;

#define TAG "CAMERA_ENC_DEC"
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, TAG, __VA_ARGS__)

#define MAX_BS_BUF   (128 * 1024 * 1024)
#define MAX_NUM_FRMS (1)
#define FRM_IDX      (0)

oapve_t id_encoder;
oapvm_t id_metadata;
oapve_cdesc_t  cdesc;
oapve_param_t *param = nullptr;
oapv_bitb_t    bitb;
oapve_stat_t   stat;
oapv_imgb_t   *imgb_r = nullptr;
oapv_imgb_t   *imgb_w = nullptr;
oapv_imgb_t   *imgb_i = nullptr;
oapv_imgb_t   *imgb_o = nullptr;
unsigned char *bs_buf = nullptr;
oapv_frms_t    rfrms = { 0 };
oapv_frms_t    ifrms = { 0 };
int input_depth;
int cfmt;
const int      num_frames = MAX_NUM_FRMS;
static unsigned char* g_original_buffer = nullptr;
static unsigned char* g_reconstructed_buffer = nullptr;
static int g_frame_width = 0;
static int g_frame_height = 0;
static size_t g_buffer_size = 0; // Track buffer size

extern "C"
{

void Java_com_example_demoisp_demorawrec_initiate_1encoding(JNIEnv *env, jclass clazz, jint width,
                                                            jint height, int cs, int quality) {
    int ret = 0;
    input_depth = 8;
    cfmt = cs + 10;

    // Clean up any existing state first
//    Java_com_example_demoisp_demorawrec_deinit_1encoding(env, clazz);

    /* set default parameters */
    memset(&cdesc, 0, sizeof(oapve_cdesc_t));
    param = &cdesc.param[FRM_IDX];
    ret = oapve_param_default(param);

    /* setting fixed parameters as per oapv specifications */
    param->color_description_present_flag = 0;
    param->csp = cs;
    param->fps_num = 30;
    param->fps_den = 1;
    param->h = height;
    param->w = width;
    param->qp = quality;
    param->use_q_matrix = 0;

    if (OAPV_FAILED(ret)) {
        LOGV("cannot set default parameter\n");
        return;
    }

    cdesc.max_bs_buf_size = MAX_BS_BUF;
    cdesc.max_num_frms = MAX_NUM_FRMS;
    cdesc.threads = OAPV_CDESC_THREADS_AUTO;

    bs_buf = (unsigned char *) malloc(MAX_BS_BUF);
    if (bs_buf == nullptr) {
        LOGV("cannot allocate bitstream buffer, size=%d", MAX_BS_BUF);
        return;
    }

    /* create encoder */
    id_encoder = oapve_create(&cdesc, &ret);
    if (id_encoder == nullptr) {
        LOGV("cannot create OAPV encoder\n");
        return;
    }

    /* create metadata handler */
    id_metadata = oapvm_create(&ret);
    if (id_metadata == nullptr || OAPV_FAILED(ret)) {
        LOGV("cannot create OAPV metadata handler\n");
        return;
    }

    bitb.addr = bs_buf;
    bitb.bsize = MAX_BS_BUF;

    // create input image buffers
    memset(&ifrms, 0, sizeof(oapv_frm_t));
    memset(&rfrms, 0, sizeof(oapv_frm_t));

    for (int i = 0; i < num_frames; i++) {
        if (input_depth == 10) {
            ifrms.frm[i].imgb = imgb_create(param->w, param->h, OAPV_CS_SET(cfmt, input_depth, 0));
        } else {
            imgb_r = imgb_create(param->w, param->h, OAPV_CS_SET(cfmt, input_depth, 0));
            ifrms.frm[i].imgb = imgb_create(param->w, param->h, OAPV_CS_SET(cfmt, 10, 0));
        }

        if (input_depth == 10) {
            rfrms.frm[i].imgb = imgb_create(param->w, param->h, OAPV_CS_SET(cfmt, input_depth, 0));
        } else {
            imgb_w = imgb_create(param->w, param->h, OAPV_CS_SET(cfmt, input_depth, 0));
            rfrms.frm[i].imgb = imgb_create(param->w, param->h, OAPV_CS_SET(cfmt, 10, 0));
        }

        rfrms.num_frms++;
        ifrms.num_frms++;
    }

    g_frame_width = param->w;
    g_frame_height = param->h;
    g_buffer_size = param->w * param->h * 2; // For YUV422/444

    g_original_buffer = (unsigned char *) malloc(g_buffer_size);
    g_reconstructed_buffer = (unsigned char *) malloc(g_buffer_size);

    if (g_original_buffer == nullptr || g_reconstructed_buffer == nullptr) {
        LOGV("Error: Failed to allocate global buffers");
        return;
    }

    LOGV("Encoder initialization completed successfully");
}


static void calculate_psnr_yuv422(unsigned char *original, unsigned char *reconstructed,
                                  int width, int height, double psnr[4]) {
    double sum[3] = {0.0, 0.0, 0.0};
    double mse[3];
    int plane_sizes[3];
    int plane_widths[3];
    int plane_heights[3];

    plane_widths[0] = width;
    plane_widths[1] = width / 2;
    plane_widths[2] = width / 2;

    plane_heights[0] = height;
    plane_heights[1] = height;
    plane_heights[2] = height;

    plane_sizes[0] = width * height;
    plane_sizes[1] = (width / 2) * height;
    plane_sizes[2] = (width / 2) * height;

    unsigned char *org_planes[3];
    unsigned char *rec_planes[3];

    org_planes[0] = original;
    org_planes[1] = original + plane_sizes[0];
    org_planes[2] = original + plane_sizes[0] + plane_sizes[1];

    rec_planes[0] = reconstructed;
    rec_planes[1] = reconstructed + plane_sizes[0];
    rec_planes[2] = reconstructed + plane_sizes[0] + plane_sizes[1];

    for (int plane = 0; plane < 3; plane++) {
        unsigned char *o = org_planes[plane];
        unsigned char *r = rec_planes[plane];

        sum[plane] = 0.0;

        for (int row = 0; row < plane_heights[plane]; row++) {
            for (int col = 0; col < plane_widths[plane]; col++) {
                double diff = (double) o[col] - (double) r[col];
                sum[plane] += diff * diff;
            }
            o += plane_widths[plane];
            r += plane_widths[plane];
        }

        mse[plane] = sum[plane] / (plane_widths[plane] * plane_heights[plane]);
        psnr[plane] = (mse[plane] == 0.0) ? 100.0 : 10.0 * log10((255.0 * 255.0) / mse[plane]);
    }

    double total_sum = 0.0;
    double total_pixels = 0.0;

    for (int plane = 0; plane < 3; plane++) {
        total_sum += sum[plane];
        total_pixels += (double)(plane_widths[plane] * plane_heights[plane]);
    }

    double overall_mse = total_sum / total_pixels;
    psnr[3] = (overall_mse == 0.0) ? 100.0 : 10.0 * log10((255.0 * 255.0) / overall_mse);
}


jdoubleArray
Java_com_example_demoisp_demorawrec_encode(JNIEnv *env, jclass clazz, jobject input_buffer,
                                           jobject recon) {
    int ret = 0;
    uint8_t *data = nullptr;

    // Check if encoder is initialized
    if (id_encoder == nullptr || param == nullptr) {
        LOGV("Error: Encoder not initialized");
        return nullptr;
    }

    unsigned char *pinput = static_cast<unsigned char *>(env->GetDirectBufferAddress(input_buffer));
    unsigned char *recon_output = static_cast<unsigned char *>(env->GetDirectBufferAddress(recon));

    if (pinput == nullptr || recon_output == nullptr) {
        LOGV("Error: Invalid buffer addresses");
        return nullptr;
    }

    jlong input_size = env->GetDirectBufferCapacity(input_buffer);
    int exp_size_420 = param->w * param->h * 3 / 2;

    if (input_size < exp_size_420) {
        LOGV("Error: Input buffer is too small for YUV format");
        return nullptr;
    }

    // Convert YUV format
    if (cfmt == 12)
        data = yuv420_to_yuv422(pinput, param->w, param->h);
    else
        data = yuv420_to_yuv444(pinput, param->w, param->h);

    if (data == nullptr) {
        LOGV("Error: failed to convert yuv420 to yuv422/444");
        return nullptr;
    }

    int exp_size_converted = param->w * param->h * 2;

    // Check if global buffers are valid
    if (g_original_buffer == nullptr || g_reconstructed_buffer == nullptr) {
        LOGV("Error: Global buffers not allocated");
        free(data);
        return nullptr;
    }

    // Copy original data for PSNR calculation
    memcpy(g_original_buffer, data, exp_size_converted);

    // Process frames
    for (int i = 0; i < num_frames; i++) {
        if (input_depth == 10) {
            imgb_i = ifrms.frm[i].imgb;
        } else {
            imgb_i = imgb_r;
        }

        ret = imgb_read_from_bufer(data, imgb_i, param->w, param->h);
        if (ret < 0) {
            LOGV("Error reading image buffer\n");
            free(data);
            return nullptr;
        }

        if (input_depth != 10) {
            imgb_cpy(ifrms.frm[i].imgb, imgb_i);
        }

        ifrms.frm[i].group_id = 1;
        ifrms.frm[i].pbu_type = OAPV_PBU_TYPE_PRIMARY_FRAME;
    }

    // Encode
    ret = oapve_encode(id_encoder, &ifrms, id_metadata, &bitb, &stat, &rfrms);
    if (OAPV_FAILED(ret)) {
        LOGV("encoding failed");
        free(data);
        return nullptr;
    }

    // Process output
    for (int fidx = 0; fidx < num_frames; fidx++) {
        if (input_depth != 10) {
            imgb_cpy(imgb_w, rfrms.frm[fidx].imgb);
            imgb_o = imgb_w;
        } else {
            imgb_o = rfrms.frm[fidx].imgb;
        }

        // Store recon image
        if (imgb_write_from_buffer(recon_output, imgb_o)) {
            LOGV("cannot write reconstructed video\n");
            free(data);
            return nullptr;
        }

        memcpy(g_reconstructed_buffer, recon_output, exp_size_converted);
    }

    double psnr_values[4];
    if (cfmt == 12) {
        calculate_psnr_yuv422(g_original_buffer, g_reconstructed_buffer,
                              param->w, param->h, psnr_values);
    }

    jdoubleArray result = env->NewDoubleArray(4);
    if (result == nullptr) {
        LOGV("Error: Failed to create double array for PSNR results");
        free(data);
        return nullptr;
    }

    env->SetDoubleArrayRegion(result, 0, 4, psnr_values);
    LOGV("PSNR calculated - Y: %.2f, U: %.2f, V: %.2f, Avg: %.2f",
         psnr_values[0], psnr_values[1], psnr_values[2], psnr_values[3]);

    free(data);

    return result;
}

void Java_com_example_demoisp_demorawrec_deinit_1encoding(JNIEnv *env, jclass clazz) {
    LOGV("=== DEINITIALIZING ENCODER ===");

    // Free image buffers
    if (imgb_r != nullptr) {
        imgb_r->release(imgb_r);
        imgb_r = nullptr;
    }
    if (imgb_w != nullptr) {
        imgb_w->release(imgb_w);
        imgb_w = nullptr;
    }

    for (int i = 0; i < num_frames; i++) {
        if (ifrms.frm[i].imgb != nullptr) {
            ifrms.frm[i].imgb->release(ifrms.frm[i].imgb);
            ifrms.frm[i].imgb = nullptr;
        }
    }
}
}
