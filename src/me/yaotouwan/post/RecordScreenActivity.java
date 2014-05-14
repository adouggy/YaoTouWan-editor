package me.yaotouwan.post;

import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import me.yaotouwan.R;
import me.yaotouwan.screenrecorder.EditVideoActivity;
import me.yaotouwan.screenrecorder.SRecorderService;
import me.yaotouwan.uicommon.ActionSheet;
import me.yaotouwan.uicommon.ActionSheetItem;
import me.yaotouwan.util.YTWHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jason on 14-3-24.
 */
public class RecordScreenActivity extends BaseActivity {

    Handler timerHandler;
    String videoPath;

    private static final int INTENT_REQUEST_CODE_CUT_VIDEO = 1;

    private SensorManager sm;
    private int orientation = -1;

    String packageName;
    String gameName;

    int videoQuality; // low: -1, middle: 0 (default), high: 1
    boolean showTouches;

    int recordedVideoOrientation;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.record_screen);

        hideActionBar();

        sm = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        packageName = getIntent().getStringExtra("package_name");
        gameName = getIntent().getStringExtra("game_name");
    }

    public void startRecordButtonClicked(View v) {

        if (YTWHelper.isLowMemory(RecordScreenActivity.this)) {
            YTWHelper.alert(RecordScreenActivity.this,
                    getString(R.string.low_memory_alert_for_recording_screen));
            return;
        }

        if (!YTWHelper.hasBuildinScreenRecorder() && !YTWHelper.checkFBPermission()) {
            TextView stateLabel = (TextView) findViewById(R.id.state_label);
            stateLabel.setText(R.string.root_permission_needed);
            return;
        }

        if (packageName == null) {
            YTWHelper.alertWithNeverAgain(RecordScreenActivity.this,
                    "record_screen_will_enter_background_alert",
                    getString(R.string.record_screen_will_enter_background_alert),
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

        try {
            int showTouchesBeforeStartRecording = Settings.System.getInt(getContentResolver(), "touch_exploration_enabled");
            YTWHelper.saveProperty(this, "touch_exploration_enabled_before_start_recording", showTouchesBeforeStartRecording > 0);
        } catch (Settings.SettingNotFoundException e) {
//            e.printStackTrace();
        }
        if (showTouches)
            YTWHelper.runRootCommand("su -c settings put system show_touches 1");

        videoPath = YTWHelper.prepareFilePathForVideoSave();
        startRecordingService();
    }

    void startRecordingService() {
        Intent recordIntent = new Intent(this, SRecorderService.class);
        recordIntent.setData(Uri.parse(videoPath));
        recordIntent.putExtra("video_orientation", orientation);
        recordIntent.putExtra("video_quality", videoQuality);
        recordIntent.putExtra("screen_width", getWindowManager().getDefaultDisplay().getWidth());
        recordIntent.putExtra("screen_height", getWindowManager().getDefaultDisplay().getHeight());
        recordedVideoOrientation = orientation;
        startService(recordIntent);

        startGame();
    }

    void startGame() {
        if (YTWHelper.hasBuildinScreenRecorder()) {
            new AsyncTask<Integer, Integer, Boolean>() {
                @Override
                protected Boolean doInBackground(Integer... params) {
                    for (int i=0; i<100; i++) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        if (YTWHelper.isBuildinScreenRecorderRunning()) {
                            return true;
                        }
                    }
                    return false;
                }

                @Override
                protected void onPostExecute(Boolean succ) {
                    super.onPostExecute(succ);
                    if (succ) {
                        doStartGame();
                    }
                }
            }.execute();
        } else {
            doStartGame();
        }
    }

    void doStartGame() {
        if (packageName == null) {
            moveTaskToBack(true);
        } else {
            timerHandler = new Handler();
            timerHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Intent gameIntent = getPackageManager().getLaunchIntentForPackage(packageName);
                    startActivity(gameIntent);
                }
            }, 300);
        }
    }

    void stopRecording() {
        timerHandler = new Handler();
        timerHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent recordIntent = new Intent(RecordScreenActivity.this, SRecorderService.class);
                stopService(recordIntent);
            }
        }, 300);
    }

    void editVideo() {
        new AsyncTask<Integer, Integer, Boolean>() {

            ProgressDialog mProgressDialog;

            @Override
            protected void onPreExecute() {
                mProgressDialog = new ProgressDialog(RecordScreenActivity.this);
                mProgressDialog.setMessage(getString(R.string.please_wait));
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
                    doEditVideo();
                }
            }
        }.execute();
    }

    void doEditVideo() {
        startActivityForResult(new Intent(this, EditVideoActivity.class)
                .setData(Uri.parse(videoPath)), INTENT_REQUEST_CODE_CUT_VIDEO);
    }

    private boolean isRecordingServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (SRecorderService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == INTENT_REQUEST_CODE_CUT_VIDEO) {
            if (resultCode == RESULT_OK) {
                if (videoPath != null
                        && !data.getData().getPath().equals(videoPath)) {
                    // 产生了一个新的文件，于是把旧文件删除
                    new File(videoPath).delete();
                }
                Intent intent = new Intent();
                intent.setData(data.getData());
                intent.putExtra("game_name", gameName);
                if (!intent.hasExtra("video_width")) {
                    int width = SRecorderService.getVideoWidthByQuality(videoQuality);
                    int height = width * getWindowSize().y / getWindowSize().x;
                    if (recordedVideoOrientation % 2 == 0) {
                        intent.putExtra("video_width", height);
                        intent.putExtra("video_height", width);
                    } else {
                        intent.putExtra("video_width", width);
                        intent.putExtra("video_height", height);
                    }
                }
                setResult(RESULT_OK, intent);
            } else {
                if (videoPath != null)
                    new File(videoPath).delete();
                setResult(RESULT_CANCELED);
            }
            finish();
        }
    }

    public void videoQualityOptionOnClick(View view) {
        final Button optBtn = (Button) view;
        List<ActionSheetItem> items = new ArrayList<ActionSheetItem>();
        items.add(new ActionSheetItem(getString(R.string.video_encoder_quality_option_high),
                new ActionSheetItem.ActionSheetItemOnClickListener() {
                    public void onClick() {
                        videoQuality = 1;
                        optBtn.setText(getString(R.string.video_encoder_quality_option_title_high));
                    }
                }));
        items.add(new ActionSheetItem(getString(R.string.video_encoder_quality_option_middle),
                new ActionSheetItem.ActionSheetItemOnClickListener() {
                    public void onClick() {
                        videoQuality = 0;
                        optBtn.setText(getString(R.string.video_encoder_quality_option_title_middle));
                    }
                }));
        items.add(new ActionSheetItem(getString(R.string.video_encoder_quality_option_low),
                new ActionSheetItem.ActionSheetItemOnClickListener() {
                    public void onClick() {
                        videoQuality = -1;
                        optBtn.setText(getString(R.string.video_encoder_quality_option_title_low));
                    }
                }));
        ActionSheet.showWithItems(this, items);
    }

    public void showTouchesOptionOnClick(View view) {
        final Button optBtn = (Button) view;
        showTouches = !showTouches;
        optBtn.setText(showTouches ?
                getString(R.string.video_encoder_show_touches_option_title_on) :
                getString(R.string.video_encoder_show_touches_option_title_off));
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        YTWHelper.alertWithNeverAgain(this,
                "record_screen_auto_stopped_alert",
                getString(R.string.record_screen_auto_stopped_alert),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        editVideo();
                    }
                });

        boolean showTouchesBeforeStartRecording = YTWHelper.getBooleanProperty(this, "touch_exploration_enabled_before_start_recording");
        if (!showTouchesBeforeStartRecording && showTouches)
            YTWHelper.runRootCommand("su -c settings put system show_touches 0");
        stopRecording();
    }

    @Override
    protected void onPause() {
        super.onPause();
        sm.unregisterListener(deviceRotateListener);
    }

    @Override
    protected void onResume() {
        super.onResume();

        sm.registerListener(deviceRotateListener,
                sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        getScreenRotation();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sm.unregisterListener(deviceRotateListener);
    }

    private void getScreenRotation() {
        if (isTabletDevice()) {
            getScreenRotationOnTablet();
        } else {
            getScreenRotationOnPhone();
        }
    }

    private void getScreenRotationOnPhone() {

        final Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();

        switch (display.getRotation()) {
            case Surface.ROTATION_0:
                System.out.println("SCREEN_ORIENTATION_PORTRAIT");
                break;

            case Surface.ROTATION_90:
                System.out.println("SCREEN_ORIENTATION_LANDSCAPE");
                break;

            case Surface.ROTATION_180:
                System.out.println("SCREEN_ORIENTATION_REVERSE_PORTRAIT");
                break;

            case Surface.ROTATION_270:
                System.out.println("SCREEN_ORIENTATION_REVERSE_LANDSCAPE");
                ImageView nexus = (ImageView) findViewById(R.id.nexus_image);
                nexus.setImageResource(R.drawable.nexus_one_land_left);
                break;
        }
    }

    private void getScreenRotationOnTablet() {

        final Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();

        switch (display.getRotation()) {
            case Surface.ROTATION_0:
                System.out.println("SCREEN_ORIENTATION_LANDSCAPE");
                break;

            case Surface.ROTATION_90:
                System.out.println("SCREEN_ORIENTATION_REVERSE_PORTRAIT");
                break;

            case Surface.ROTATION_180:
                System.out.println("SCREEN_ORIENTATION_REVERSE_LANDSCAPE");
                ImageView nexus = (ImageView) findViewById(R.id.nexus_image);
                nexus.setImageResource(R.drawable.nexus_one_land);
                break;

            case Surface.ROTATION_270:
                System.out.println("SCREEN_ORIENTATION_PORTRAIT");
                break;
        }
    }

    private boolean isTabletDevice(){
        return getResources().getBoolean(R.bool.isTablet);
    }

    final SensorEventListener deviceRotateListener = new SensorEventListener() {

        public void onSensorChanged(SensorEvent event) {
            if (Sensor.TYPE_ACCELEROMETER != event.sensor.getType()) {
                return;
            }

            float[] values = event.values;
            float ax = values[0];
            float ay = values[1];

            if (ax*ax > ay*ay) {
                if (ax < 0) {
                    orientation = 1;
                } else {
                    orientation = 3;
                }
            } else {
                if (ay > 0) {
                    orientation = 0;
                } else {
                    orientation = 2;
                }
            }
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };
}