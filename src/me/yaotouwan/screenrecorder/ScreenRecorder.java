package me.yaotouwan.screenrecorder;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import me.yaotouwan.R;
import me.yaotouwan.util.YTWHelper;

/**
 * Created by jason on 14-5-21.
 */
public class ScreenRecorder {

    public Activity context;
    public int showTouches;
    public String videoPath;
    public boolean videoLandscape;
    public int videoQuality;
    public boolean moveToBackAlert;
    public ScreenRecorderListener listener;
    private boolean recordedVideoLandscape;

    public static Class getScreenServiceClassBasedOnSystem() {
        if (Build.MANUFACTURER.equals("Xiaomi")) {
            return SRecorderServiceIndependent.class;
        }
        return SRecorderService.class;
    }

    public interface ScreenRecorderListener {
        public void onStartedScreenRecorder();
        public void onStoppedScreenRecorder();
    }

    public ScreenRecorder(Activity context, ScreenRecorderListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public int getVideoWidthByQuality(int videoQuality) {
        int w = 360;
        if (videoQuality == 0) {
            w = 360;
        } else if (videoQuality == 1) {
            w = 540;
        } else if (videoQuality == -1) {
            w = 240;
        }
        return w;
    }

    public int getVideoHeightByQuality(int videoQuality) {
        int w = getVideoWidthByQuality(videoQuality);
        int winWidth = context.getWindowManager().getDefaultDisplay().getWidth();
        int winHeight = context.getWindowManager().getDefaultDisplay().getHeight();
        if (winWidth < winHeight)
            return (int) (w * 1.0 * winHeight / winWidth);
        else
            return (int) (w * 1.0 * winWidth / winHeight);
    }

    public int getVideoBitrateByQuality(int videoQuality) {
        if (videoQuality == 0) {
            return 400000;
        } else if (videoQuality == 1) {
            return 800000;
        } else if (videoQuality == -1) {
            return 200000;
        }
        return 0;
    }

    public void start() {
        if (YTWHelper.isLowMemory(context)) {
            YTWHelper.alert(context,
                    context.getString(R.string.low_memory_alert_for_recording_screen));
            return;
        }

        if (moveToBackAlert) {
            YTWHelper.alertWithNeverAgain(context,
                    "record_screen_will_enter_background_alert",
                    context.getString(R.string.record_screen_will_enter_background_alert),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            startRecording();
                        }}
            );
            return;
        }

        startRecording();
    }

    void startRecording() {
        setShowTouches();
        startRecordingService();
    }

    void startRecordingService() {
        Intent recordIntent = new Intent(context, getScreenServiceClassBasedOnSystem());
        recordIntent.setData(Uri.parse(videoPath));
        recordIntent.putExtra("video_landscape", videoLandscape);
        recordIntent.putExtra("video_width", getVideoWidthByQuality(videoQuality));
        recordIntent.putExtra("video_height", getVideoHeightByQuality(videoQuality));
        recordIntent.putExtra("video_bitrate", getVideoBitrateByQuality(videoQuality));
        recordedVideoLandscape = videoLandscape;
        Log.d("Recorder", "start service");
        context.startService(recordIntent);
    }

    public boolean stop() {
        Log.d("Recorder", "recorder stop");
        if (isRecordingServiceRunning()) {
            Log.d("Recorder", "stop service");
            Intent recordIntent = new Intent(context, getScreenServiceClassBasedOnSystem());
            context.stopService(recordIntent);
            return true;
        }
        return false;
    }

    private void waitForRecordingServiceStop() {
        new AsyncTask<Integer, Integer, Boolean>() {

            ProgressDialog mProgressDialog;

            @Override
            protected void onPreExecute() {
                mProgressDialog = new ProgressDialog(context);
                mProgressDialog.setMessage(context.getString(R.string.please_wait));
                mProgressDialog.setCancelable(true);
                mProgressDialog.show();
                super.onPreExecute();
            }

            @Override
            protected Boolean doInBackground(Integer... params) {
                for (int i=0; i<100; i++) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (YTWHelper.hasBuildinScreenRecorder()) {
                        if (!YTWHelper.isBuildinScreenRecorderRunning()) {
                            return true;
                        }
                    } else {
                        if (!isRecordingServiceRunning()) {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            return true;
                        }
                    }
                }
                return false;
            }

            @Override
            protected void onPostExecute(Boolean succ) {
                super.onPostExecute(succ);
                mProgressDialog.dismiss();
                if (succ) {
                    listener.onStoppedScreenRecorder();
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private boolean isRecordingServiceRunning() {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (getScreenServiceClassBasedOnSystem().getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void setShowTouches() {
        try {
            int showTouchesBeforeStartRecording =
                    Settings.System.getInt(context.getContentResolver(),
                            "show_touches");
            if (showTouchesBeforeStartRecording != showTouches) {
                Settings.System.putInt(context.getContentResolver(),
                        "show_touches", showTouches);
            }
            YTWHelper.saveProperty(context,
                    "show_touches_before_start_recording",
                    showTouchesBeforeStartRecording);
        } catch (Settings.SettingNotFoundException e) {
        }
    }

    private void resetShowTouches() {
        int showTouchesBeforeStartRecording = YTWHelper.getIntProperty(context,
                "show_touches_before_start_recording");
        try {
            int currentShowTouches = Settings.System.getInt(context.getContentResolver(),
                    "show_touches");
            if (currentShowTouches != showTouchesBeforeStartRecording) {
                Settings.System.putInt(context.getContentResolver(),
                        "show_touches", showTouchesBeforeStartRecording);
            }
        } catch (Settings.SettingNotFoundException e) {
        }
    }

}
