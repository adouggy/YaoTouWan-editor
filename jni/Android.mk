LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := libx264
LOCAL_SRC_FILES := ffmpeg/lib/libx264.a
LOCAL_CFLAGS := -march=armv7-a -mfloat-abi=softfp -mfpu=neon

include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE := libfaac
LOCAL_SRC_FILES := ffmpeg/lib/libfaac.a
LOCAL_CFLAGS := -march=armv7-a -mfloat-abi=softfp -mfpu=neon

include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_LDFLAGS := -Wl,-rpath-link=/Users/jason/android/android-ndk-r9c/platforms/android-8/arch-arm/usr/lib/ -rpath-link=/Users/jason/android/android-ndk-r9c/platforms/android-8/arch-arm/usr/lib/
LOCAL_MODULE := libffmpeg
LOCAL_SRC_FILES := ffmpeg/lib/libffmpeg.a
LOCAL_CFLAGS := -march=armv7-a -mfloat-abi=softfp -mfpu=neon
LOCAL_LDLIBS := -lz -lm -llog -lc -L$(call host-path, $(LOCAL_PATH))/$(TARGET_ARCH_ABI)

include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_LDLIBS += -llog -lz
LOCAL_STATIC_LIBRARIES := libffmpeg libx264 libfaac
LOCAL_C_INCLUDES += $(LOCAL_PATH)/ffmpeg/include
LOCAL_SRC_FILES := screenshot.c encoder.c srecorder.c veditor.c
LOCAL_CFLAGS := -march=armv7-a -mfloat-abi=softfp -mfpu=neon
LOCAL_MODULE := srecorder

include $(BUILD_SHARED_LIBRARY)