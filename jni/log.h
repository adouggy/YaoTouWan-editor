//
//  log.h
//  screenrecorder-android
//
//  Created by Jason Hsu on 14-3-4.
//
//

#include <android/log.h>

#define LOG_TAG "recorder"
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
