#!/bin/bash

package=me.yaotouwan
appname=Yaotouwan

function die {
	echo "$1 failed" && exit 1
}

level=0

if [ "$1" == "build" ]; then
	level=0
	if [ "$2" == "java" ]; then
		level=1
	fi
elif [ "$1" == "install" ]; then
	level=2
elif [ "$1" == "start" ]; then
	level=3
fi

if [ "$level" -le "0" ]; then
	echo "Build JNI"
	pushd jni/ >/dev/null
	ndk-build 2>&1 | grep -v "Cortex"
	if [ "$?" -ne "0" ]; then
		die "nkd-build failed"
	fi
	popd >/dev/null
fi

if [ "$level" -le "1" ]; then
	echo "Build Application"
#	ant clean >/dev/null
	ant debug install || die "ant build failed"
fi

if [ "$level" -eq "2" ]; then
	echo "Install Application"
	adb uninstall $package >/dev/null
	adb install bin/$appname-debug.apk >/dev/null 2>&1
fi

if [ "$level" -le "3" ]; then
	echo "Start Application"
	#adb shell am force-stop $package >/dev/null
	for pid in `adb shell ps | grep $package | cut -c11-15`; do adb shell su -c kill -9 $pid; done
	adb logcat -c
	adb shell am start -n $package/$package.MainActivity >/dev/null
	sleep 1
	adb logcat | grep `adb shell ps | grep $package | cut -c10-15`
	#adb logcat -s "recorder" | grep -v "beginning of"
fi
