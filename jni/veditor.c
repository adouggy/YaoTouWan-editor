#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <math.h>
#include <sys/time.h>

#include <libavutil/opt.h>
#include <libavutil/mathematics.h>
#include <libavutil/timestamp.h>
#include <libavformat/avformat.h>
#include <libswscale/swscale.h>
#include <libswresample/swresample.h>
#include <libavutil/imgutils.h>
#include <libavutil/samplefmt.h>

#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <linux/fb.h>
#include <linux/kd.h>

#include <jni.h>
#include "log.h"

static AVFormatContext *fmt_ctx = NULL;
static AVCodecContext *video_dec_ctx = NULL, *audio_dec_ctx;
static AVStream *video_stream = NULL, *audio_stream = NULL;
static const char *src_filename = NULL;
static const char *dst_filename = NULL;
static FILE *dst_file = NULL;
static double start_progress;
static double end_progress;

static uint8_t *video_dst_data[4] = {NULL};
static int      video_dst_linesize[4];

static uint8_t *video_dst_data_large[4] = {NULL};
static int      video_dst_linesize_large[4];
static int      video_dst_bufsize_large;
static uint8_t *video_dst_data_small[4] = {NULL};
static int      video_dst_linesize_small[4];
static int      video_dst_bufsize_small;

static int width_small = 100;
static int height_small = 100;

static int video_stream_idx = -1, audio_stream_idx = -1;
static AVFrame *frame = NULL;
static AVPacket pkt;
static int video_frame_count = 0;
static int audio_frame_count = 0;

static struct SwsContext *sws_ctx;

static struct SwsContext *sws_ctx_large;
static struct SwsContext *sws_ctx_small;

static enum AVPixelFormat device_pix_fmt = PIX_FMT_RGBA;

enum {
    API_MODE_OLD                  = 0, /* old method, deprecated */
    API_MODE_NEW_API_REF_COUNT    = 1, /* new method, using the frame reference counting */
    API_MODE_NEW_API_NO_REF_COUNT = 2, /* new method, without reference counting */
};

static int api_mode = API_MODE_OLD;

static int decode_packet(int *got_frame, int width, int height)
{
    int ret = 0;
    int decoded = pkt.size;
    
    *got_frame = 0;
    
    if (pkt.stream_index == video_stream_idx) {
        ret = avcodec_decode_video2(video_dec_ctx, frame, got_frame, &pkt);
        if (ret < 0) {
            LOGE("Error decoding video frame (%s)\n", av_err2str(ret));
            return ret;
        }
        
        if (*got_frame) {
            if (width == 0 && height == 0) {
                width = video_dec_ctx->width;
                height = video_dec_ctx->height;
            } else if (width == 0) {
                height = video_dec_ctx->height * 1.0 / video_dec_ctx->width * width;
            } else if (height == 0) {
                width = video_dec_ctx->width * 1.0 / video_dec_ctx->height * height;
            }
            static struct SwsContext *sws_ctx;
            if (sws_ctx == NULL) {
                sws_ctx = sws_getContext(video_dec_ctx->width, video_dec_ctx->height,
                                         video_dec_ctx->pix_fmt,
                                         width, height,
                                         device_pix_fmt,
                                         SWS_POINT, NULL, NULL, NULL);
                if (sws_ctx == NULL) {
                    LOGE("Cannot initialize the conversion context\n");
                    return -1;
                }
            }
            int decoded_height = sws_scale(sws_ctx,
                      (const uint8_t **)frame->data,
                      frame->linesize,
                      0,
                      video_dec_ctx->height,
                      video_dst_data,
                      video_dst_linesize);
            return width * height; // end if got image, by jason
        } else {
            return 0;
        }
    } else {
        return 0;
    }
    
    if (*got_frame && api_mode == API_MODE_NEW_API_REF_COUNT)
        av_frame_unref(frame);
    
    return decoded;
}

static int open_codec_context(int *stream_idx,
                              AVFormatContext *fmt_ctx, enum AVMediaType type)
{
    int ret;
    AVStream *st;
    AVCodecContext *dec_ctx = NULL;
    AVCodec *dec = NULL;
    AVDictionary *opts = NULL;
    
    ret = av_find_best_stream(fmt_ctx, type, -1, -1, NULL, 0);
    if (ret < 0) {
        LOGE("Could not find %s stream in input file '%s' error: %s",
                av_get_media_type_string(type), src_filename, av_err2str(ret));
        return ret;
    } else {
        *stream_idx = ret;
        st = fmt_ctx->streams[*stream_idx];
        
        dec_ctx = st->codec;
        dec = avcodec_find_decoder(dec_ctx->codec_id);
        if (!dec) {
            LOGE("Failed to find %s codec\n",
                    av_get_media_type_string(type));
            return AVERROR(EINVAL);
        }
        
        if (api_mode == API_MODE_NEW_API_REF_COUNT)
            av_dict_set(&opts, "refcounted_frames", "1", 0);
        if ((ret = avcodec_open2(dec_ctx, dec, &opts)) < 0) {
            LOGE("Failed to open %s codec\n",
                    av_get_media_type_string(type));
            return ret;
        }
    }
    
    return 0;
}

int extract_frame(int maxWidth, int maxHeight)
{
    av_register_all();
    
    // LOGI("1");
    int err;
    if ((err = avformat_open_input(&fmt_ctx, src_filename, NULL, NULL)) < 0) {
        LOGE("Could not open source file %s, error: %s", src_filename, av_err2str(err));
        return 0;
    }

    // LOGI("2");
    if ((err = avformat_find_stream_info(fmt_ctx, NULL)) < 0) {
        LOGE("Could not find stream information, error: %s", av_err2str(err));
        return 0;
    }

    // LOGI("3");
    if ((err = open_codec_context(&video_stream_idx, fmt_ctx, AVMEDIA_TYPE_VIDEO)) < 0) {
        LOGE("Could not open codec context, error: %s", av_err2str(err));
        return 0;
    }

    video_stream = fmt_ctx->streams[video_stream_idx];
    // LOGI("4");
    if (!video_stream) {
        LOGE("Could not find audio or video stream in the input, aborting");
        err = 1;
        goto end;
    }
    video_dec_ctx = video_stream->codec;
    
    int width = 0;
    int height = 0;
    if (maxWidth == 0 && maxHeight == 0) {
        width = video_dec_ctx->width;
        height = video_dec_ctx->height;
    } else if (maxWidth == 0) {
        height = maxHeight;
        width = video_dec_ctx->width * 1.0 / video_dec_ctx->height * maxHeight;
    } else if (maxHeight == 0) {
        width = maxWidth;
        height = video_dec_ctx->height * 1.0 / video_dec_ctx->width * maxWidth;
    }

    int video_dst_bufsize = av_image_alloc(video_dst_data, 
                                    video_dst_linesize,
                                    width, height,
                                    device_pix_fmt, 1);
    if (video_dst_bufsize < 0) {
        LOGE("Could not allocate raw video buffer, error: %s", av_err2str(video_dst_bufsize));
        err = 1;
        goto end;
    }
    
    // LOGI("5");
    if (api_mode == API_MODE_OLD)
        frame = avcodec_alloc_frame();
    else
        frame = av_frame_alloc();
    
    // LOGI("6");
    if (!frame) {
        LOGE("Could not allocate frame");
        err = AVERROR(ENOMEM);
        goto end;
    }
    
    // LOGI("7");
    av_init_packet(&pkt);
    pkt.data = NULL;
    pkt.size = 0;
    
    if (start_progress > 0) {
        av_seek_frame(fmt_ctx, video_stream_idx, start_progress * video_stream->duration, 0);
    }
    
    while (av_read_frame(fmt_ctx, &pkt) >= 0) {
        do {
            int got_frame = 0;
            if (pkt.stream_index == video_stream_idx) {
                if ((err = avcodec_decode_video2(video_dec_ctx, frame, &got_frame, &pkt)) < 0) {
                    LOGE("Error decoding video frame %s", av_err2str(err));
                    break;
                }

                if (got_frame) {
                    static struct SwsContext *sws_ctx;
                    if (sws_ctx == NULL) {
                        sws_ctx = sws_getContext(video_dec_ctx->width, video_dec_ctx->height,
                                                 video_dec_ctx->pix_fmt,
                                                 width, height,
                                                 device_pix_fmt,
                                                 SWS_POINT, NULL, NULL, NULL);
                        if (sws_ctx == NULL) {
                            LOGE("Cannot initialize the conversion context");
                            break;
                        }
                    }
                    int decoded_height = sws_scale(sws_ctx,
                              (const uint8_t **)frame->data,
                              frame->linesize,
                              0,
                              video_dec_ctx->height,
                              video_dst_data,
                              video_dst_linesize);
                    if (decoded_height) {
                        goto end;
                    }
                } else {
                    break;
                }
            } else {
                break;
            }
            
            if (got_frame && api_mode == API_MODE_NEW_API_REF_COUNT)
                av_frame_unref(frame);
            
        } while (pkt.size > 0);
    }
    
end:
   // LOGI("8");
    avcodec_close(video_dec_ctx);
    avcodec_close(audio_dec_ctx);
    avformat_close_input(&fmt_ctx);
    if (dst_file)
        fclose(dst_file);
    if (api_mode == API_MODE_OLD)
        avcodec_free_frame(&frame);
    else
        av_frame_free(&frame);
   // LOGI("9");

    if (maxWidth)
        return height;
    else
        return width;
}

jintArray Java_me_yaotouwan_screenrecorder_EditVideoActivity_prepareDecoder
(
    JNIEnv *env,
    jobject this,
    jstring filename_jni
)
{
    src_filename = (*env)->GetStringUTFChars(env, filename_jni, NULL);

    av_register_all();
//    LOGI("1");

    int err;
    if ((err = avformat_open_input(&fmt_ctx, src_filename, NULL, NULL)) < 0) {
        LOGE("Could not open source file %s, error: %s", src_filename, av_err2str(err));
        return NULL;
    }
//    LOGI("2");

    if ((err = avformat_find_stream_info(fmt_ctx, NULL)) < 0) {
        LOGE("Could not find stream information, error: %s", av_err2str(err));
        return NULL;
    }
//    LOGI("3");

    if ((err = open_codec_context(&video_stream_idx, fmt_ctx, AVMEDIA_TYPE_VIDEO)) < 0) {
        LOGE("Could not open codec context, error: %s", av_err2str(err));
        return NULL;
    }
//    LOGI("4");

    video_stream = fmt_ctx->streams[video_stream_idx];
    if (!video_stream) {
        LOGE("Could not find audio or video stream in the input, aborting");
        return NULL;
    }
//    LOGI("5");
    int rotate = 0;
    AVDictionary *metadata = video_stream->metadata;
    if (metadata) {
        AVDictionaryEntry *item = av_dict_get(metadata, "rotate", NULL, 0);
        if (item) {
            char *value = item->value;
            if (value) {
                if (strcmp(value, "90") == 0) {
                    rotate = 90;
                } else if (strcmp(value, "180") == 0) {
                    rotate = 180;
                } else if (strcmp(value, "270") == 0) {
                    rotate = 270;
                }
            }
        }
    }
//    LOGI("6");

    video_dec_ctx = video_stream->codec;

    height_small = video_dec_ctx->height * 1.0 / video_dec_ctx->width * width_small;
    sws_ctx_small = sws_getContext(video_dec_ctx->width, video_dec_ctx->height,
                             video_dec_ctx->pix_fmt,
                             width_small, height_small,
                             device_pix_fmt,
                             SWS_POINT, NULL, NULL, NULL);
    if (sws_ctx_small == NULL) {
        LOGE("Cannot initialize the conversion context");
        return NULL;
    }
//    LOGI("7");

    int width_large = video_dec_ctx->width;
    int height_large = video_dec_ctx->height;

    sws_ctx_large = sws_getContext(video_dec_ctx->width, video_dec_ctx->height,
                             video_dec_ctx->pix_fmt,
                             width_large, height_large,
                             device_pix_fmt,
                             SWS_POINT, NULL, NULL, NULL);
    if (sws_ctx_large == NULL) {
        LOGE("Cannot initialize the conversion context");
        return NULL;
    }
//    LOGI("8");

    video_dst_bufsize_small = av_image_alloc(video_dst_data_small, 
                                    video_dst_linesize_small,
                                    width_small, height_small,
                                    device_pix_fmt, 1);
    if (video_dst_bufsize_small < 0) {
        LOGE("Could not allocate raw video buffer, error: %s", av_err2str(video_dst_bufsize_small));
        return NULL;
    }
//    LOGI("9");

    video_dst_bufsize_large = av_image_alloc(video_dst_data_large, 
                                    video_dst_linesize_large,
                                    width_large, height_large,
                                    device_pix_fmt, 1);
    if (video_dst_bufsize_large < 0) {
        LOGE("Could not allocate raw video buffer, error: %s", av_err2str(video_dst_bufsize_large));
        return NULL;
    }
//    LOGI("10");

    if (api_mode == API_MODE_OLD)
        frame = avcodec_alloc_frame();
    else
        frame = av_frame_alloc();
    
    if (!frame) {
        LOGE("Could not allocate frame");
        return NULL;
    }
//    LOGI("11");

    av_init_packet(&pkt);
    pkt.data = NULL;
    pkt.size = 0;

    int size[4];
    size[0] = video_stream->codec->width;
    size[1] = video_stream->codec->height;
    size[2] = video_stream->duration * 1000 / video_stream->time_base.den;
    size[3] = rotate;
    jintArray result = (*env)->NewIntArray(env, 4);
    (*env)->SetIntArrayRegion(env, result, 0, 4, size);
    return result;
}

jintArray Java_me_yaotouwan_screenrecorder_EditVideoActivity_decodeFrame
(
    JNIEnv *env,
    jobject this,
    jdouble progress,
    jboolean is_large
)
{
    int err;

    if (progress > 0) {
        progress = progress * video_stream->duration;
        if ((err = av_seek_frame(fmt_ctx, video_stream_idx, progress, 0)) < 0) {
            LOGE("Error seek frame %s", av_err2str(err));
        }
    }

    while (av_read_frame(fmt_ctx, &pkt) >= 0) {
        do {
            int got_frame = 0;
            if (pkt.stream_index == video_stream_idx) {
                if ((err = avcodec_decode_video2(video_dec_ctx, frame, &got_frame, &pkt)) < 0) {
                    LOGE("Error decoding video frame %s", av_err2str(err));
                    break;
                }

                if (got_frame) {
                    if (pkt.flags & AV_PKT_FLAG_KEY == 0) {
                        break;
                    }

                    uint8_t **video_dst_data = is_large ? video_dst_data_large : video_dst_data_small;
                    int decoded_height = sws_scale(is_large ? sws_ctx_large : sws_ctx_small,
                                                  (const uint8_t **)frame->data,
                                                  frame->linesize,
                                                  0,
                                                  video_dec_ctx->height,
                                                  video_dst_data,
                                                  is_large ? video_dst_linesize_large : video_dst_linesize_small);
                    if (decoded_height) {
                        int image_buffer_size;
                        if (is_large) {
                            image_buffer_size = video_dec_ctx->width * video_dec_ctx->height;
                        } else {
                            image_buffer_size = width_small * height_small;
                        }

                        jintArray result = (*env)->NewIntArray(env, image_buffer_size);
                        (*env)->SetIntArrayRegion(env, result, 0, image_buffer_size, (int *)video_dst_data[0]);
                        return result;
                    }
                } else {
                    break;
                }
            } else {
                break;
            }
            
            if (got_frame && api_mode == API_MODE_NEW_API_REF_COUNT)
                av_frame_unref(frame);
            
        } while (pkt.size > 0);
    }

    return NULL;
}

jintArray Java_me_yaotouwan_screenrecorder_EditVideoActivity_clearDecoder
(
    JNIEnv *env,
    jobject this
)
{
    avcodec_close(video_dec_ctx);
    avcodec_close(audio_dec_ctx);
    avformat_close_input(&fmt_ctx);
    if (dst_file)
        fclose(dst_file);
    if (api_mode == API_MODE_OLD)
        avcodec_free_frame(&frame);
    else
        av_frame_free(&frame);
    av_free(video_dst_data_small[0]);
    av_free(video_dst_data_large[0]);
    sws_freeContext(sws_ctx_small);
    sws_freeContext(sws_ctx_large);
    src_filename = NULL;
}

jintArray Java_me_yaotouwan_screenrecorder_PreviewVideoActivity_decodeFrame
(
    JNIEnv *env,
    jobject this,
    jstring filename,
    jdouble progress)
{
    //Java_me_yaotouwan_screenrecorder_EditVideoActivity_decodeFrame(env, this, filename, progress);
}

jintArray Java_me_yaotouwan_screenrecorder_EditVideoActivity_videoInfo
(
    JNIEnv *env,
    jobject this,
    jstring filename)
{
    const jbyte *dst_path;
    src_filename = (*env)->GetStringUTFChars(env, filename, NULL);
    
    av_register_all();

    int err;
    if ((err = avformat_open_input(&fmt_ctx, src_filename, NULL, NULL)) < 0) {
        LOGE("Could not open source file %s, error: %s", src_filename, av_err2str(err));
        return NULL;
    }
    
    if ((err = avformat_find_stream_info(fmt_ctx, NULL)) < 0) {
        LOGE("Could not find stream information, error: %s", av_err2str(err));
        return NULL;
    }

    if (open_codec_context(&video_stream_idx, fmt_ctx, AVMEDIA_TYPE_VIDEO) >= 0) {
        video_stream = fmt_ctx->streams[video_stream_idx];
        video_dec_ctx = video_stream->codec;
        
        int size[3];
        size[0] = video_stream->codec->width;
        size[1] = video_stream->codec->height;
        size[2] = video_stream->duration * 1000 / video_stream->time_base.den;
        jintArray result = (*env)->NewIntArray(env, 3);
        (*env)->SetIntArrayRegion(env, result, 0, 3, size);
        return result;
    }
    return NULL;
}

jintArray Java_me_yaotouwan_screenrecorder_PreviewVideoActivity_videoInfo
(
    JNIEnv *env,
    jobject this,
    jstring filename)
{
    return Java_me_yaotouwan_screenrecorder_EditVideoActivity_videoInfo(env, this, filename);
}

static void log_packet(const AVFormatContext *fmt_ctx, const AVPacket *pkt, const char *tag)
{
    AVRational *time_base = &fmt_ctx->streams[pkt->stream_index]->time_base;
    
    printf("%s: pts:%s pts_time:%s dts:%s dts_time:%s duration:%s duration_time:%s stream_index:%d\n",
           tag,
           av_ts2str(pkt->pts), av_ts2timestr(pkt->pts, time_base),
           av_ts2str(pkt->dts), av_ts2timestr(pkt->dts, time_base),
           av_ts2str(pkt->duration), av_ts2timestr(pkt->duration, time_base),
           pkt->stream_index);
}

jint Java_me_yaotouwan_screenrecorder_EditVideoActivity_cutVideo
(
 JNIEnv *env,
 jobject this,
 jstring src_filename_jni,
 jstring dst_filename_jni,
 jdouble start_progress_jni,
 jdouble end_progress_jni)
{
    AVOutputFormat *ofmt = NULL;
    AVFormatContext *ifmt_ctx = NULL, *ofmt_ctx = NULL;
    AVPacket pkt;
    const char *in_filename, *out_filename;
    int ret, i;

//    LOGI("~~~~~~~~~ cut video 1");
    in_filename  = (*env)->GetStringUTFChars(env, src_filename_jni, NULL);
    out_filename = (*env)->GetStringUTFChars(env, dst_filename_jni, NULL);
    
    av_register_all();

//    LOGI("~~~~~~~~~ cut video 2");
    if ((ret = avformat_open_input(&ifmt_ctx, in_filename, 0, 0)) < 0) {
        LOGE("Could not open input file '%s'", in_filename);
        goto end;
    }

//    LOGI("~~~~~~~~~ cut video 3");
    if ((ret = avformat_find_stream_info(ifmt_ctx, 0)) < 0) {
        LOGE("Failed to retrieve input stream information");
        goto end;
    }

//    LOGI("~~~~~~~~~ cut video 4");
    avformat_alloc_output_context2(&ofmt_ctx, NULL, NULL, out_filename);
    if (!ofmt_ctx) {
        LOGE("Could not create output context\n");
        ret = AVERROR_UNKNOWN;
        goto end;
    }

//    LOGI("~~~~~~~~~ cut video 5");
    ofmt = ofmt_ctx->oformat;
    
    for (i = 0; i < ifmt_ctx->nb_streams; i++) {
        AVStream *in_stream = ifmt_ctx->streams[i];
        AVStream *out_stream = avformat_new_stream(ofmt_ctx, in_stream->codec->codec);
        if (!out_stream) {
            LOGE("Failed allocating output stream\n");
            ret = AVERROR_UNKNOWN;
            goto end;
        }
        
        ret = avcodec_copy_context(out_stream->codec, in_stream->codec);
        if (ret < 0) {
            LOGE("Failed to copy context from input to output stream codec context\n");
            goto end;
        }
        av_dict_copy(&(out_stream->metadata), in_stream->metadata, 0);
        out_stream->codec->codec_tag = 0;
        if (ofmt_ctx->oformat->flags & AVFMT_GLOBALHEADER)
            out_stream->codec->flags |= CODEC_FLAG_GLOBAL_HEADER;
    }

//    LOGI("~~~~~~~~~ cut video 6");
    if (!(ofmt->flags & AVFMT_NOFILE)) {
        ret = avio_open(&ofmt_ctx->pb, out_filename, AVIO_FLAG_WRITE);
        if (ret < 0) {
            LOGE("Could not open output file '%s' error: %s", out_filename, av_err2str(ret));
            goto end;
        }
    }

//    LOGI("~~~~~~~~~ cut video 7");
    ret = avformat_write_header(ofmt_ctx, NULL);
    if (ret < 0) {
        LOGE("Error occurred when opening output file\n");
        goto end;
    }
    
    double start_progress = start_progress_jni;
    double end_progress = end_progress_jni;
    int started = 0;
    while (1) {
//        LOGI("~~~~~~~~~ cut video 8");
        AVStream *in_stream, *out_stream;
        
        ret = av_read_frame(ifmt_ctx, &pkt);
        if (ret < 0) {
//            LOGE("read frame failed, error: %s", av_err2str(ret));
            break;
        }
        
        in_stream  = ifmt_ctx->streams[pkt.stream_index];
        out_stream = ofmt_ctx->streams[pkt.stream_index];

//        LOGE("pkt.pts = %ld, start_progress = %g, in_stream->duration = %ld", (long)pkt.pts, start_progress, (long)in_stream->duration);
        
        if (pkt.pts >= start_progress * in_stream->duration
            && pkt.flags & AV_PKT_FLAG_KEY > 0
            && !started)
        {
            started = 1;
        }
        if (pkt.pts >= end_progress * in_stream->duration) {
            av_free_packet(&pkt);
            LOGE("free packet failed, error: %s", av_err2str(ret));
            break;
        }
        
        if (started) {

//            LOGI("demuxed a frame");
            pkt.pts -= start_progress * in_stream->duration;
            pkt.dts -= start_progress * in_stream->duration;
            pkt.pts = av_rescale_q_rnd(pkt.pts, in_stream->time_base, out_stream->time_base, AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX);
            pkt.dts = av_rescale_q_rnd(pkt.dts, in_stream->time_base, out_stream->time_base, AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX);
            pkt.duration = av_rescale_q(pkt.duration, in_stream->time_base, out_stream->time_base);
            pkt.pos = -1;
            
            ret = av_interleaved_write_frame(ofmt_ctx, &pkt);
            if (ret < 0) {
                fprintf(stderr, "Error muxing packet\n");
                break;
            }
        }
        
        av_free_packet(&pkt);
    }
    
    av_write_trailer(ofmt_ctx);
end:
    
    avformat_close_input(&ifmt_ctx);
    avformat_free_context(ifmt_ctx);
    
    if (ofmt_ctx && !(ofmt->flags & AVFMT_NOFILE))
        avio_close(ofmt_ctx->pb);
    avformat_free_context(ofmt_ctx);
    
    if (ret < 0 && ret != AVERROR_EOF) {
        fprintf(stderr, "Error occurred: %s\n", av_err2str(ret));
        return 1;
    }
    
    return 0;
}

jint Java_me_yaotouwan_screenrecorder_EditVideoActivity_mergeVideo
(
 JNIEnv *env,
 jobject this,
 jstring src_video_filename_jni,
 jstring src_audio_filename_jni,
 jstring dst_filename_jni)
{
    AVOutputFormat *ofmt = NULL;
    AVFormatContext *vifmt_ctx = NULL, *aifmt_ctx = NULL, *ofmt_ctx = NULL;
    AVPacket pkt;
    int ret, i;

//    LOGI("~~~~~~~~~ cut video 1");
    const char *in_video_fn  = (*env)->GetStringUTFChars(env, src_video_filename_jni, NULL);
    const char *in_audio_fn = (*env)->GetStringUTFChars(env, src_audio_filename_jni, NULL);
    const char *out_fn = (*env)->GetStringUTFChars(env, dst_filename_jni, NULL);

    av_register_all();

    int64_t lastVideoPTS = 0, lastVideoDTS = 0;
    double audioTime = 0;
    double videoTime = 0;
    int videoEnd = 0;
    int audioEnd = 0;

    int c = 0;

    while(1) {
        char split_video_fn[200];
        strcpy(split_video_fn, in_video_fn);
        char ext[8];
        sprintf(ext, "-%d.mp4", c);
        strcat(split_video_fn, ext);

        LOGI("source video file %s", split_video_fn);

        if( access( split_video_fn, F_OK ) == -1 ) {
        // file not exists
            if (c == 0)
                return -1;
            else
                break;
        }

//        LOGI("~~~~~~~~~ cut video 2");
        if ((ret = avformat_open_input(&vifmt_ctx, split_video_fn, 0, 0)) < 0) {
            LOGE("Could not open input file '%s', %s", split_video_fn, av_err2str(ret));
            goto end;
        }

//        LOGI("~~~~~~~~~ cut video 3");
        if ((ret = avformat_find_stream_info(vifmt_ctx, 0)) < 0) {
            LOGE("Failed to retrieve input stream information %s", av_err2str(ret));
            goto end;
        }

        if (c == 0) {

            if ((ret = avformat_open_input(&aifmt_ctx, in_audio_fn, 0, 0)) < 0) {
                LOGE("Could not open input audio file '%s', %s", in_audio_fn, av_err2str(ret));
                goto end;
            }
            if ((ret = avformat_find_stream_info(aifmt_ctx, 0)) < 0) {
                LOGE("Failed to retrieve input audio stream information %s", av_err2str(ret));
                goto end;
            }

//            LOGI("~~~~~~~~~ cut video 4");
            avformat_alloc_output_context2(&ofmt_ctx, NULL, NULL, out_fn);
            if (!ofmt_ctx) {
                LOGE("Could not create output context\n");
                ret = AVERROR_UNKNOWN;
                goto end;
            }

//            LOGI("~~~~~~~~~ cut video 5");
            ofmt = ofmt_ctx->oformat;

            // assume only contains video stream
            AVStream *video_in_stream = vifmt_ctx->streams[0];
            AVStream *video_out_stream = avformat_new_stream(ofmt_ctx, video_in_stream->codec->codec);
            if (!video_out_stream) {
                LOGE("Failed allocating video output stream\n");
                ret = AVERROR_UNKNOWN;
                goto end;
            }

            ret = avcodec_copy_context(video_out_stream->codec, video_in_stream->codec);
            if (ret < 0) {
                LOGE("Failed to copy context from input to output stream codec context\n");
                goto end;
            }
            video_out_stream->codec->codec_tag = 0;
            if (ofmt_ctx->oformat->flags & AVFMT_GLOBALHEADER)
                video_out_stream->codec->flags |= CODEC_FLAG_GLOBAL_HEADER;

            // assume only contains audio stream
            AVStream *audio_in_stream = aifmt_ctx->streams[0];
            AVStream *audio_out_stream = avformat_new_stream(ofmt_ctx, audio_in_stream->codec->codec);
            if (!audio_out_stream) {
                LOGE("Failed allocating audio output stream\n");
                ret = AVERROR_UNKNOWN;
                goto end;
            }

            ret = avcodec_copy_context(audio_out_stream->codec, audio_in_stream->codec);
            if (ret < 0) {
                LOGE("Failed to copy context from input to output stream codec context\n");
                goto end;
            }
            audio_out_stream->codec->codec_tag = 0;
            if (ofmt_ctx->oformat->flags & AVFMT_GLOBALHEADER)
                audio_out_stream->codec->flags |= CODEC_FLAG_GLOBAL_HEADER;

            if (!(ofmt->flags & AVFMT_NOFILE)) {
                ret = avio_open(&ofmt_ctx->pb, out_fn, AVIO_FLAG_WRITE);
                if (ret < 0) {
                    LOGE("Could not open output file '%s' error: %s", out_fn, av_err2str(ret));
                    goto end;
                }
            }
            ret = avformat_write_header(ofmt_ctx, NULL);
            if (ret < 0) {
                LOGE("Error occurred when opening output file %s", av_err2str(ret));
                goto end;
            }
        }

        while (1) {
            while (videoTime <= audioTime || audioEnd > 0) {
                AVStream *video_in_stream = vifmt_ctx->streams[0];
                AVStream *video_out_stream = ofmt_ctx->streams[0];

                ret = av_read_frame(vifmt_ctx, &pkt);
                if (ret < 0) {
                    LOGE("av_read_frame video failed %s", av_err2str(ret));
                    lastVideoPTS += video_in_stream->duration;
                    lastVideoDTS += video_in_stream->duration;
                    goto end_split;
                } else {
                    pkt.pts += lastVideoPTS;
                    pkt.dts += lastVideoDTS;
                    pkt.pts = av_rescale_q_rnd(pkt.pts, video_in_stream->time_base, video_out_stream->time_base, AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX);
                    pkt.dts = av_rescale_q_rnd(pkt.dts, video_in_stream->time_base, video_out_stream->time_base, AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX);
                    pkt.duration = av_rescale_q(pkt.duration, video_in_stream->time_base, video_out_stream->time_base);
                    pkt.stream_index = 0;
                    pkt.pos = -1;

                    videoTime += pkt.duration * 1.0 / video_out_stream->time_base.den;
//                    LOGI("video time %g", videoTime);

                    ret = av_interleaved_write_frame(ofmt_ctx, &pkt);
                    if (ret < 0) {
                        LOGE("write video packet Error muxing packet, %s", av_err2str(ret));
                        break;
                    }
                    av_free_packet(&pkt);
                }
            }

            while (!audioEnd && audioTime <= videoTime) {
                AVStream *audio_in_stream = aifmt_ctx->streams[0];
                AVStream *audio_out_stream = ofmt_ctx->streams[1];

                ret = av_read_frame(aifmt_ctx, &pkt);
                if (ret < 0) {
                    LOGE("av_read_frame audio failed %s", av_err2str(ret));
                    audioEnd = 1;
                    break;
                } else {
                    pkt.stream_index = 1;
                    pkt.pos = -1;

                    audioTime += pkt.duration * 1.0 / audio_out_stream->codec->sample_rate;
//                    LOGI("audio time %g", audioTime);

                    ret = av_interleaved_write_frame(ofmt_ctx, &pkt);
                    if (ret < 0) {
                        av_free_packet(&pkt);
                        LOGE("write audio packet Error muxing packet, %s", av_err2str(ret));
                        goto end_split;
                    }
                    av_free_packet(&pkt);
                }
            }
        }
end_split:
        LOGI("end split file");
        c ++;
    }

end:
    av_write_trailer(ofmt_ctx);

    avformat_close_input(&vifmt_ctx);
    avformat_close_input(&aifmt_ctx);

    if (ofmt_ctx && !(ofmt->flags & AVFMT_NOFILE))
        avio_close(ofmt_ctx->pb);
    avformat_free_context(ofmt_ctx);

    if (ret < 0 && ret != AVERROR_EOF) {
        fprintf(stderr, "Error occurred: %s\n", av_err2str(ret));
        return 1;
    }

    return 0;
}

