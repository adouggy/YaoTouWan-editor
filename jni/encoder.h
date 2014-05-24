//
//  encoder.h
//  screenrecorder-android
//
//  Created by Jason Hsu on 14-3-5.
//
//


#include <libavutil/opt.h>
#include <libavutil/mathematics.h>
#include <libavutil/timestamp.h>
#include <libavformat/avformat.h>
#include <libswscale/swscale.h>
#include <libswresample/swresample.h>

void close_video(AVFormatContext *oc, AVStream *st);
void write_video_frame(AVFormatContext *oc, AVStream *st, int flush);
void open_video(AVFormatContext *oc, AVCodec *codec, AVStream *st);
void close_audio(AVFormatContext *oc, AVStream *st);
void write_audio_frame(AVFormatContext *oc, AVStream *st, int flush, uint8_t *audio_samples, int audio_samples_count, float audio_gain);
void open_audio(AVFormatContext *oc, AVCodec *codec, AVStream *st);
AVStream *add_stream(AVFormatContext *oc, AVCodec **codec, enum AVCodecID codec_id);
int write_frame(AVFormatContext *fmt_ctx, const AVRational *time_base, AVStream *st, AVPacket *pkt);
void log_packet(const AVFormatContext *fmt_ctx, const AVPacket *pkt);
int encoder_encode_frame(char *audio_samples, size_t audio_samples_count, float audio_gain);
int encoder_stop_recording();