#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <math.h>
#include <sys/time.h>

#include <libavutil/timestamp.h>
#include <libavformat/avformat.h>
#include <libswscale/swscale.h>
#include <libswresample/swresample.h>

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
        
        av_dict_set(&opts, "refcounted_frames", "1", 0);
        if ((ret = avcodec_open2(dec_ctx, dec, &opts)) < 0) {
            LOGE("Failed to open %s codec\n",
                    av_get_media_type_string(type));
            return ret;
        }
    }
    
    return 0;
}

int get_rotate_from_video_stream(AVStream *video_stream) {
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
    return rotate;
}

int set_rotate_to_video_stream(AVStream *video_stream, int rotate) {
    AVDictionary *metadata = video_stream->metadata;
    if (metadata) {
        char rotate_str[4] = {0};
        sprintf(rotate_str, "%d", rotate);
        av_dict_set(&metadata, "rotate", rotate_str, 0);
    }
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

    int err;
    if ((err = avformat_open_input(&fmt_ctx, src_filename, NULL, NULL)) < 0) {
        LOGE("Could not open source file %s, error: %s", src_filename, av_err2str(err));
        return NULL;
    }

    if ((err = avformat_find_stream_info(fmt_ctx, NULL)) < 0) {
        LOGE("Could not find stream information, error: %s", av_err2str(err));
        return NULL;
    }

    if ((err = open_codec_context(&video_stream_idx, fmt_ctx, AVMEDIA_TYPE_VIDEO)) < 0) {
        LOGE("Could not open codec context, error: %s", av_err2str(err));
        return NULL;
    }

    video_stream = fmt_ctx->streams[video_stream_idx];
    if (!video_stream) {
        LOGE("Could not find audio or video stream in the input, aborting");
        return NULL;
    }
    int rotate = get_rotate_from_video_stream(video_stream);
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

    video_dst_bufsize_small = av_image_alloc(video_dst_data_small, 
                                    video_dst_linesize_small,
                                    width_small, height_small,
                                    device_pix_fmt, 1);
    if (video_dst_bufsize_small < 0) {
        LOGE("Could not allocate raw video buffer, error: %s", av_err2str(video_dst_bufsize_small));
        return NULL;
    }

    video_dst_bufsize_large = av_image_alloc(video_dst_data_large, 
                                    video_dst_linesize_large,
                                    width_large, height_large,
                                    device_pix_fmt, 1);
    if (video_dst_bufsize_large < 0) {
        LOGE("Could not allocate raw video buffer, error: %s", av_err2str(video_dst_bufsize_large));
        return NULL;
    }

    frame = av_frame_alloc();
    
    if (!frame) {
        LOGE("Could not allocate frame");
        return NULL;
    }

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
    while (av_read_frame(fmt_ctx, &pkt) >= 0) {
        do {
            int got_frame = 0;
            if (pkt.stream_index == video_stream_idx) {
                if ((pkt.flags & AV_PKT_FLAG_KEY) == 0
                    || pkt.pts < progress * video_stream->duration) {

                    av_free_packet(&pkt);
                    break;
                }
                if ((err = avcodec_decode_video2(video_dec_ctx, frame, &got_frame, &pkt)) < 0) {
                    av_free_packet(&pkt);
                    LOGE("Error decoding video frame %s", av_err2str(err));
                    break;
                }

                av_free_packet(&pkt);
                if (!got_frame) {
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

                av_frame_unref(frame);
            } else {
                break;
            }
            
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
    av_frame_free(&frame);
    av_free(video_dst_data_small[0]);
    av_free(video_dst_data_large[0]);
    sws_freeContext(sws_ctx_small);
    sws_freeContext(sws_ctx_large);
    src_filename = NULL;
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
 jdouble end_progress_jni,
 jint rotate_jni
 )
{
    AVOutputFormat *ofmt = NULL;
    AVFormatContext *ifmt_ctx = NULL, *ofmt_ctx = NULL;
    AVPacket pkt;
    const char *in_filename, *out_filename;
    int ret, i;

    in_filename  = (*env)->GetStringUTFChars(env, src_filename_jni, NULL);
    out_filename = (*env)->GetStringUTFChars(env, dst_filename_jni, NULL);
    
    av_register_all();

    if ((ret = avformat_open_input(&ifmt_ctx, in_filename, 0, 0)) < 0) {
        LOGE("Could not open input file '%s'", in_filename);
        goto end;
    }

    if ((ret = avformat_find_stream_info(ifmt_ctx, 0)) < 0) {
        LOGE("Failed to retrieve input stream information");
        goto end;
    }

    avformat_alloc_output_context2(&ofmt_ctx, NULL, NULL, out_filename);
    if (!ofmt_ctx) {
        LOGE("Could not create output context\n");
        ret = AVERROR_UNKNOWN;
        goto end;
    }

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
        if (in_stream->codec->codec_type == AVMEDIA_TYPE_VIDEO) {
            int rotate = get_rotate_from_video_stream(in_stream);
            rotate += rotate_jni;
            rotate %= 360;
            set_rotate_to_video_stream(out_stream, rotate);
        }
        out_stream->codec->codec_tag = 0;
        if (ofmt_ctx->oformat->flags & AVFMT_GLOBALHEADER)
            out_stream->codec->flags |= CODEC_FLAG_GLOBAL_HEADER;
    }

    if (!(ofmt->flags & AVFMT_NOFILE)) {
        ret = avio_open(&ofmt_ctx->pb, out_filename, AVIO_FLAG_WRITE);
        if (ret < 0) {
            LOGE("Could not open output file '%s' error: %s", out_filename, av_err2str(ret));
            goto end;
        }
    }

    ret = avformat_write_header(ofmt_ctx, NULL);
    if (ret < 0) {
        LOGE("Error occurred when opening output file\n");
        goto end;
    }
    
    double start_progress = start_progress_jni;
    double end_progress = end_progress_jni;
    int started = 0;
    long start_pts = 0;
    while (1) {
        AVStream *in_stream, *out_stream;
        
        ret = av_read_frame(ifmt_ctx, &pkt);
        if (ret < 0) {
            break;
        }
        
        in_stream  = ifmt_ctx->streams[pkt.stream_index];
        out_stream = ofmt_ctx->streams[pkt.stream_index];

        if (in_stream->codec->codec_type == AVMEDIA_TYPE_VIDEO
            && pkt.pts >= start_progress * in_stream->duration
            && pkt.flags & AV_PKT_FLAG_KEY
            && !started)
        {
            start_pts = pkt.pts;
            started = 1;
        }
        if (pkt.pts >= end_progress * in_stream->duration) {
            av_free_packet(&pkt);
            LOGE("free packet failed, error: %s", av_err2str(ret));
            break;
        }
        
        if (started) {
            pkt.pts -= start_pts;
            pkt.dts -= start_pts;
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
 jstring dst_filename_jni,
 jint rotate_jni
 )
{
    AVOutputFormat *ofmt = NULL;
    AVFormatContext *vifmt_ctx = NULL, *aifmt_ctx = NULL, *ofmt_ctx = NULL;
    AVPacket pkt;
    int ret, i;

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

        if ((ret = avformat_open_input(&vifmt_ctx, split_video_fn, 0, 0)) < 0) {
            LOGE("Could not open video input file '%s', %s", split_video_fn, av_err2str(ret));
            if (c > 0) {
                ret = 100;
                goto end_write;
            }
            goto end;
        }

        if ((ret = avformat_find_stream_info(vifmt_ctx, 0)) < 0) {
            LOGE("Failed to retrieve input stream information %s", av_err2str(ret));
            goto end;
        }

        if (c == 0) {

            if ((ret = avformat_open_input(&aifmt_ctx, in_audio_fn, 0, 0)) < 0) {
                LOGE("Could not open input audio file '%s', %s", in_audio_fn, av_err2str(ret));
//                goto end;
            }
            if (aifmt_ctx && (ret = avformat_find_stream_info(aifmt_ctx, 0)) < 0) {
                LOGE("Failed to retrieve input audio stream information %s", av_err2str(ret));
//                goto end;
            }

            avformat_alloc_output_context2(&ofmt_ctx, NULL, NULL, out_fn);
            if (!ofmt_ctx) {
                LOGE("Could not create output context\n");
                ret = AVERROR_UNKNOWN;
                goto end;
            }

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

            int rotate = get_rotate_from_video_stream(video_in_stream);
            rotate += rotate_jni;
            rotate %= 360;
            set_rotate_to_video_stream(video_out_stream, rotate);

            if (aifmt_ctx) {
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
            }

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
            while (videoTime <= audioTime || audioEnd > 0 || !aifmt_ctx) {
                AVStream *video_in_stream = vifmt_ctx->streams[0];
                AVStream *video_out_stream = ofmt_ctx->streams[0];

                ret = av_read_frame(vifmt_ctx, &pkt);
                if (ret < 0) {
                    if (ret != AVERROR_EOF)
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
                    ret = av_interleaved_write_frame(ofmt_ctx, &pkt);
                    if (ret < 0) {
                        LOGE("write video packet Error muxing packet, %s", av_err2str(ret));
                        break;
                    }
//                    av_free_packet(&pkt);
                }
            }

            while (aifmt_ctx && !audioEnd && audioTime <= videoTime) {
                AVStream *audio_in_stream = aifmt_ctx->streams[0];
                AVStream *audio_out_stream = ofmt_ctx->streams[1];

                ret = av_read_frame(aifmt_ctx, &pkt);
                if (ret < 0) {
                    if (ret != AVERROR_EOF)
                        LOGE("av_read_frame audio failed %s", av_err2str(ret));
                    audioEnd = 1;
                    break;
                } else {
                    pkt.stream_index = 1;
                    pkt.pos = -1;

                    audioTime += pkt.duration * 1.0 / audio_out_stream->codec->sample_rate;

                    ret = av_interleaved_write_frame(ofmt_ctx, &pkt);
                    if (ret < 0) {
                        av_free_packet(&pkt);
                        LOGE("write audio packet Error muxing packet, %s", av_err2str(ret));
                        goto end_split;
                    }
//                    av_free_packet(&pkt);
                }
            }
        }
end_split:
        LOGI("end split file");
        avformat_close_input(&vifmt_ctx);
        c ++;

        jclass java_class = (*env)->GetObjectClass(env, this);
        jmethodID java_method_id = (*env)->GetMethodID(env, java_class, "mergeProgressUpdated", "(I)V");
        (*env)->CallVoidMethod(env, this, java_method_id, c);
    }

end_write:
    av_write_trailer(ofmt_ctx);
    if (aifmt_ctx)
        avformat_close_input(&aifmt_ctx);

end:
    if (ofmt_ctx && !(ofmt->flags & AVFMT_NOFILE))
        avio_close(ofmt_ctx->pb);
    avformat_free_context(ofmt_ctx);

    if (ret < 0 && ret != AVERROR_EOF) {
        fprintf(stderr, "Error occurred: %s\n", av_err2str(ret));
        return 1;
    }

    return 0;
}

