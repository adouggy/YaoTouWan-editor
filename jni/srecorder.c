//
//  srecorder.c
//  screenrecorder-android
//
//  Created by Jason Hsu on 14-3-4.
//
//

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <math.h>

#include <libavutil/opt.h>
#include <libavutil/mathematics.h>
#include <libavutil/timestamp.h>
#include <libavformat/avformat.h>
#include <libswscale/swscale.h>
#include <libswresample/swresample.h>

#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/types.h>

#include <jni.h>

#include "encoder.h"
#include "log.h"

int pid = 0;

jint Java_me_yaotouwan_screenrecorder_SRecorderService_startBuildinRecorder
(
 JNIEnv *env,
  jobject this,
  jstring command_jni
)
{
    const jbyte *command = (*env)->GetStringUTFChars(env, command_jni, NULL);

    if (pid = fork()) {// parent
        return pid;
    } else {
        return system(command);
    }
}

jint Java_me_yaotouwan_screenrecorder_SRecorderService_stopBuildinRecorder
(
 JNIEnv *env,
 jobject this,
 jstring command_jni
)
{
    const jbyte *command = (*env)->GetStringUTFChars(env, command_jni, NULL);

    return system(command);
}

jint Java_me_yaotouwan_screenrecorder_SRecorderService_initRecorder
(
 JNIEnv *env,
 jobject this,
 jstring filename_jni,
 jint rotation,
 jint video_bit_rate,
 jboolean record_video_jni
)
{
    const jbyte *filename = (*env)->GetStringUTFChars(env, filename_jni, NULL);

    return encoder_init_recorder(filename, rotation, video_bit_rate, record_video_jni ? 1 : 0);
}

jint Java_me_yaotouwan_screenrecorder_SRecorderService_encodeFrame
(
 JNIEnv *env,
 jobject this,
 jbyteArray audio_samples_bytearray,
 jsize audio_samples_count
)
{
    jbyte* audio_samples = (*env)->GetByteArrayElements(env, audio_samples_bytearray, NULL);
    audio_samples_count /= 2;
    
    int ret = encoder_encode_frame(audio_samples, audio_samples_count);
    (*env)->ReleaseByteArrayElements(env, audio_samples_bytearray, audio_samples, 0);
    return ret;
}

jint Java_me_yaotouwan_screenrecorder_SRecorderService_stopRecording
(
 JNIEnv *env,
 jobject this
)
{
    return encoder_stop_recording();
}

jint Java_me_yaotouwan_screenrecorder_MainActivity_getPreviewWidth
(
 JNIEnv *env,
 jobject this)
{
    return encoder_preview_width();
}

jint Java_me_yaotouwan_screenrecorder_MainActivity_getPreviewHeight
(
 JNIEnv *env,
 jobject this)
{
    return encoder_preview_height();
}