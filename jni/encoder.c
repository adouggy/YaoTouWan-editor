//
//  encoder.c
//  screenrecorder-android
//
//  Created by Jason Hsu on 14-3-5.
//
//

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <math.h>

#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/types.h>

#include "screenshot.h"
#include "log.h"
#include "encoder.h"

FBInfo fb;
int video_frame_rate = 7;
AVFormatContext *oc = NULL;
AVStream *audio_st = NULL, *video_st = NULL;
int ret = 0;
int recording = 0;

AVFrame *video_frame = NULL;
AVPicture dst_picture;
int frame_count = 0;

AVFrame *audio_frame = NULL;
int samples_count = 0;
struct SwsContext *sws_ctx = NULL;

int16_t *audio_samples_local_buffer = NULL;
int audio_samples_local_buffer_length = 0;

int rotation = 0;
int video_bit_rate = 0;

#define MAX_AUDIO_SAMLE_VALUE 32768

void log_packet(const AVFormatContext *fmt_ctx, const AVPacket *pkt)
{
    AVRational *time_base = &fmt_ctx->streams[pkt->stream_index]->time_base;
    
    LOGI("pts:%s pts_time:%s dts:%s dts_time:%s duration:%s duration_time:%s stream_index:%d\n",
         av_ts2str(pkt->pts), av_ts2timestr(pkt->pts, time_base),
         av_ts2str(pkt->dts), av_ts2timestr(pkt->dts, time_base),
         av_ts2str(pkt->duration), av_ts2timestr(pkt->duration, time_base),
         pkt->stream_index);
}

int write_frame
(
    AVFormatContext *fmt_ctx,
    const AVRational *time_base,
    AVStream *st,
    AVPacket *pkt
)
{
    pkt->pts = av_rescale_q_rnd(pkt->pts, *time_base, st->time_base, AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX);
    pkt->dts = av_rescale_q_rnd(pkt->dts, *time_base, st->time_base, AV_ROUND_NEAR_INF|AV_ROUND_PASS_MINMAX);
    pkt->duration = av_rescale_q(pkt->duration, *time_base, st->time_base);
    pkt->stream_index = st->index;

    return av_interleaved_write_frame(fmt_ctx, pkt);
}

AVStream *add_stream
(
    AVFormatContext *oc,
    AVCodec **codec,
    enum AVCodecID codec_id
)
{
    AVCodecContext *c;
    AVStream *st;
    
    *codec = avcodec_find_encoder(codec_id);
    if (!(*codec)) {
        LOGE("Could not find encoder for '%s'\n",
             avcodec_get_name(codec_id));
        return NULL;
    }
    
    st = avformat_new_stream(oc, *codec);
    if (!st) {
        LOGE("Could not allocate stream\n");
        return NULL;
    }
    st->id = oc->nb_streams - 1;
    c = st->codec;
    
    switch ((*codec)->type) {
        case AVMEDIA_TYPE_AUDIO:
            c->sample_fmt  = (*codec)->sample_fmts ?
            (*codec)->sample_fmts[0] : AV_SAMPLE_FMT_FLTP;
            c->bit_rate    = 16 * 1000;
            c->sample_rate = 44100 / 2;
            c->channels    = 1;
            break;
            
        case AVMEDIA_TYPE_VIDEO:
            if (fb_open(&fb)) {
                return NULL;
            }
            c->codec_id = codec_id;
            c->bit_rate = video_bit_rate;
            c->width = fb_width(&fb) / 4 * 2;
            c->height = fb_height(&fb) / 4 * 2;
            c->time_base.den = video_frame_rate;
            c->time_base.num = 1;
            c->gop_size = c->time_base.den;
            c->pix_fmt = AV_PIX_FMT_YUV420P;
            if(c->codec_id == CODEC_ID_H264) {
                av_opt_set(c->priv_data, "preset", "ultrafast", 0); // ultrafast,superfast, veryfast, faster, fast, medium, slow, slower, veryslow, placebo
                av_opt_set(c->priv_data, "profile", "baseline", 0); // baseline, main, high, high10, high422, high444
            }
            fb_close(&fb);
            break;
            
        default:
            break;
    }
    
    if (oc->oformat->flags & AVFMT_GLOBALHEADER)
        c->flags |= CODEC_FLAG_GLOBAL_HEADER;
    
    return st;
}

void open_audio
(
    AVFormatContext *oc,
    AVCodec *codec,
    AVStream *st
)
{
    AVCodecContext *c;
    int ret;
    
    c = st->codec;
    
    audio_frame = av_frame_alloc();
    if (!audio_frame) {
        LOGE("Could not allocate audio frame\n");
        exit(1);
    }
    
    ret = avcodec_open2(c, codec, NULL);
    if (ret < 0) {
        LOGE("Could not open audio codec: %s\n", av_err2str(ret));
        exit(1);
    }
}

void write_audio_frame
(
    AVFormatContext *oc,
    AVStream *st,
    uint8_t *audio_samples,
    int audio_samples_count,
    float audio_gain
)
{
    AVCodecContext *c;
    AVPacket pkt = { 0 };
    int got_packet, ret;
    
    c = st->codec;

    // gain volume
    int16_t *audio_samples_16 = (int16_t *)audio_samples;
    int i;
    for (i=0; i<audio_samples_count; i++) {
        audio_samples_16[i] *= audio_gain;
    }
//    LOGI("write audio frame, audio_samples_16 %d", audio_samples_count);

    audio_frame->nb_samples = audio_samples_count;
    audio_frame->pts = av_rescale_q(samples_count, (AVRational){1, c->sample_rate}, c->time_base);
    avcodec_fill_audio_frame(audio_frame, c->channels, c->sample_fmt,
                             audio_samples, audio_samples_count*2, 0);
    samples_count += audio_samples_count;

    ret = avcodec_encode_audio2(c, &pkt, audio_frame, &got_packet);
    if (ret < 0) {
        LOGE("Error encoding audio frame: %s\n", av_err2str(ret));
        exit(1);
    }

    if (!got_packet) {
        return;
    }
    
    ret = write_frame(oc, &c->time_base, st, &pkt);
    if (ret < 0) {
        LOGE("Error while writing audio frame: %s",
             av_err2str(ret));
        return;
    }
}

void close_audio(AVFormatContext *oc, AVStream *st)
{
    avcodec_close(st->codec);
    av_frame_free(&audio_frame);
}

void open_video(AVFormatContext *oc, AVCodec *codec, AVStream *st)
{
    int ret;
    AVCodecContext *c = st->codec;
    
    ret = avcodec_open2(c, codec, NULL);
    if (ret < 0) {
        LOGE("Could not open video codec: %s\n", av_err2str(ret));
        exit(1);
    }
    
    video_frame = av_frame_alloc();
    if (!video_frame) {
        LOGE("Could not allocate video frame\n");
        exit(1);
    }
    video_frame->format = c->pix_fmt;
    video_frame->width = c->width;
    video_frame->height = c->height;

    ret = avpicture_alloc(&dst_picture, c->pix_fmt, c->width, c->height);
    if (ret < 0) {
        LOGE("Could not allocate picture: %s\n", av_err2str(ret));
        return;
    }
    
    *((AVPicture *)video_frame) = dst_picture;
}

int cur_fb_pix_fmt = 0;
int cur_fb_size = 0;
int write_video_frame(AVFormatContext *oc, AVStream *st)
{
    int ret;
    AVCodecContext *c = st->codec;
    
    if (fb_open(&fb) == 0) {
//        LOGE("fi.line_length = %u, vi.bits_per_pixel = %u, vi.xres = %u, vi.yres = %u", fb.fi.line_length, fb.vi.bits_per_pixel, fb_width(&fb), fb_height(&fb));

        static uint8_t *bits = NULL;

        int size = fb_size(&fb);
        if (cur_fb_size && cur_fb_size != size) {
            LOGI("ignore a frame");
            log_fb(&fb);
            return -1;
        }

        int fmt = fb_pix_fmt(&fb);
        if (cur_fb_pix_fmt != fmt) {
            if (sws_ctx) {
                sws_freeContext(sws_ctx);
                sws_ctx = NULL;
            }
            if (bits) {
                free(bits);
                bits = NULL;
            }
            cur_fb_pix_fmt = fmt;
        }

        const uint8_t *inData[1];
        int linesize = fb.fi.line_length;
        if (linesize > fb_width(&fb) * fb_bpp(&fb)) {
            int diffHeight = ceilf((linesize / fb_bpp(&fb) - fb_width(&fb)) * fb_height(&fb) / fb_width(&fb));
            if (bits == NULL) {
                LOGI("create new bits");
                bits = (uint8_t *)malloc(fb_size(&fb));
            }
            int line = 0;
            for (; line < fb_height(&fb) - diffHeight; line ++) {
                memcpy(bits + line * fb_width(&fb) * fb_bpp(&fb),
                       fb.bits + line * fb.fi.line_length,
                       fb_width(&fb) * fb_bpp(&fb));
            }
            inData[0] = bits;
        } else {
            inData[0] = fb.bits;
        }

        if (sws_ctx == NULL) {
            LOGI("create new sws_ctx");
            sws_ctx = sws_getContext(fb_width(&fb), fb_height(&fb),
                                     fmt,
                                     c->width, c->height,
                                     c->pix_fmt,
                                     SWS_POINT, NULL, NULL, NULL);
            if (sws_ctx == NULL) {
                LOGE("Cannot initialize the conversion context\n");
                return -1;
            }
            log_fb(&fb);
        }

        int inLinesize[1];
        inLinesize[0] = fb_bpp(&fb) * fb_width(&fb);
        int height = fb_height(&fb);
        sws_scale(
            sws_ctx,
            inData,
            inLinesize,
            0,
            height,
            dst_picture.data,
            dst_picture.linesize);
        fb_close(&fb);

        AVPacket pkt = { 0 };
        int got_packet;

        video_frame->pts = frame_count;
        ret = avcodec_encode_video2(c, &pkt, video_frame, &got_packet);
        if (ret < 0) {
            LOGE("Error encoding video frame: %s\n", av_err2str(ret));
            exit(1);
        }

        if (got_packet) {
            ret = write_frame(oc, &c->time_base, st, &pkt);
        } else {
            ret = 0;
        }

        if (ret < 0) {
            LOGE("Error while writing video frame: %s\n", av_err2str(ret));
            exit(1);
        }
        frame_count++;

        return frame_count;
    }
    return -1;
}

void close_video(AVFormatContext *oc, AVStream *st)
{
    avcodec_close(st->codec);
    av_free(dst_picture.data[0]);
    av_frame_free(&video_frame);
}

int encoder_init_recorder(const char *filename, int rotation_, int video_bit_rate_, int video_fps, int record_video)
{
    recording = 1;
    rotation = rotation_;
    video_bit_rate = video_bit_rate_;
    video_frame_rate = video_fps;
    av_register_all();
    frame_count = 0;
    LOGI("record_video = %d", record_video);

    avformat_alloc_output_context2(&oc, NULL, NULL, filename);
    if (!oc)
        return 1;

    AVCodec *audio_codec;
    if (oc->oformat->audio_codec != AV_CODEC_ID_NONE)
        audio_st = add_stream(oc, &audio_codec, oc->oformat->audio_codec);

    AVCodec *video_codec;
    if (record_video) {
        if (oc->oformat->video_codec != AV_CODEC_ID_NONE)
            video_st = add_stream(oc, &video_codec, oc->oformat->video_codec);
    }

    if (video_st)
        open_video(oc, video_codec, video_st);
    if (audio_st)
        open_audio(oc, audio_codec, audio_st);
    
    if (!(oc->oformat->flags & AVFMT_NOFILE)) {
        ret = avio_open(&oc->pb, filename, AVIO_FLAG_WRITE);
        if (ret < 0) {
            LOGE("Could not open '%s': %s\n", filename,
                 av_err2str(ret));
            return 1;
        }
    }
    
    ret = avformat_write_header(oc, NULL);
    if (ret < 0) {
        LOGE("Error occurred when opening output file %s: %s\n",
             filename,
             av_err2str(ret));
        return 1;
    }
    
    return 0;
}

int write_video_frame_failed = 0;

int encoder_encode_frame
(
    char *audio_samples,
    size_t audio_samples_count,
    float audio_gain
)
{
//    LOGI("Encode a frame by FFMPEG");
    if (recording) {
        if (!write_video_frame_failed) {
            int frame_size = audio_st->codec->frame_size;
            if (audio_samples_local_buffer == NULL) {
                audio_samples_local_buffer = (int16_t *)malloc(frame_size * sizeof(int16_t) * 2);
            }

            int i = 0;
            if (audio_samples_local_buffer_length > 0) {
                memcpy(audio_samples_local_buffer + audio_samples_local_buffer_length,
                       audio_samples,
                       (frame_size - audio_samples_local_buffer_length) * 2);
                write_audio_frame(oc, audio_st, (uint8_t *)audio_samples_local_buffer, frame_size, audio_gain);
                i = frame_size - audio_samples_local_buffer_length;
                audio_samples_local_buffer_length = 0;
            }

            for (; i <= audio_samples_count - frame_size; i += frame_size) {
                write_audio_frame(oc, audio_st, audio_samples + i*2, frame_size, audio_gain);
            }
            if (i < audio_samples_count) {
                int buffer_left = audio_samples_count - i;
                memcpy(audio_samples_local_buffer + audio_samples_local_buffer_length, audio_samples + i * 2, buffer_left * 2);
                audio_samples_local_buffer_length += buffer_left;

                if (audio_samples_local_buffer_length >= frame_size) {
                    write_audio_frame(oc, audio_st, (uint8_t *)audio_samples_local_buffer, frame_size, audio_gain);
                    for (i=0; i<audio_samples_local_buffer_length - frame_size; i++) {
                        audio_samples_local_buffer[i] = audio_samples_local_buffer[i+frame_size];
                    }
                    audio_samples_local_buffer_length -= frame_size;
                }
            }
        }
        if (video_st) {
            if (write_video_frame(oc, video_st) < 0) {
                write_video_frame_failed = 1;
            } else {
                write_video_frame_failed = 0;
            }
        }
    }
    return recording;
}

int encoder_stop_recording()
{
    LOGI("stop recording");
    recording = 0;

    if (oc == NULL) return 0;

    av_write_trailer(oc);

    if (video_st)
        close_video(oc, video_st);
    if (audio_st)
        close_audio(oc, audio_st);

    if (!(oc->oformat->flags & AVFMT_NOFILE))
        avio_close(oc->pb);

    avformat_free_context(oc);
    free(audio_samples_local_buffer);
    audio_samples_local_buffer = NULL;
    sws_ctx = NULL;

    return 0;
}

void set_rotation(int rotation_)
{
    rotation = rotation_;
}

int encoder_preview_width()
{
    if (video_st) {
        return video_st->codec->width * 2;
    } else {
        return 0;
    }
}

int encoder_preview_height()
{
    if (video_st) {
        return video_st->codec->height * 2;
    } else {
        return 0;
    }
}
