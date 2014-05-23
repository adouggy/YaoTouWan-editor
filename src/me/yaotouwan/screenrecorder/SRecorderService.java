package me.yaotouwan.screenrecorder;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioRecord.OnRecordPositionUpdateListener;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;
import android.view.OrientationEventListener;
import android.widget.Toast;
import me.yaotouwan.util.StreamHelper;
import me.yaotouwan.util.YTWHelper;

import java.io.*;

public class SRecorderService extends Service {

	public static final String TAG = "SRecorderService";

    private int mAudioBufferSize;
    private int mAudioBufferSampleSize;
    private AudioRecord mAudioRecord;
    private MediaRecorder mMediaRecorder;
    private boolean inRecordMode = false;
    private byte[] audioBuffer;
    private int audioSamplesRead;
    String videoPath;
    int videoWidth, videoHeight, videoBitrate;

    public static final String ACTION_SCREEN_RECORDER_STARTED
            = "me.yaotouwan.screenrecorder.action.started";
    public static final String ACTION_SCREEN_RECORDER_STOPPED
            = "me.yaotouwan.screenrecorder.action.stopped";

    // parameters for video
    boolean videoLandscape;

	private native int initRecorder(String filename, int rotation);
	private native int encodeFrame(byte[] audioBuffer, int audioSamplesSize);
	private native int stopRecording();

    int pid;

    private native int startBuildinRecorder(String command);
    private native int stopBuildinRecorder(String command);

    static {
        System.loadLibrary("srecorder");
    }

    String buildCommandLine() {
        String size = videoWidth + "x" + videoHeight;
        if (videoLandscape) {
            size = videoHeight + "x" + videoWidth;
        }
        String bitrate = "";
        if (videoBitrate > 0) {
            bitrate += videoBitrate;
        }
        String firstVideoPath = YTWHelper
                .correctFilePath(videoPath.substring(0, videoPath.length() - 4));
        String recordScriptPath = YTWHelper.screenrecordScriptPath();
        String cmd = "su -c sh " + recordScriptPath + " " +
                indicatorFilePath() + " " + size + " " +
                bitrate + " " + firstVideoPath;
        return cmd;
    }

    boolean startBuildinRecorder() {
        stopBuildinRecorder();
        String cmd = buildCommandLine();
        logd(cmd);
        prepareRecordScript();
        touchIndicatorFile();
        pid = startBuildinRecorder(cmd);
        logd("started build in ss " + pid);

        return true;
    }

    private void stopBuildinRecorder() {
        rmIndicatorFile();
        BufferedReader pbr = null;
        try {
            Process p = Runtime.getRuntime().exec("ps | grep screenrecord");
            pbr = StreamHelper.reader(p.getInputStream());
            while (true) {
                String line = pbr.readLine();
                if (line == null) return;
                if (line.trim().startsWith("USER")) continue;
                logd(line);
                String[] parts = line.split("\\s+");
                if (parts.length > 1) {
                    String pidStr = parts[1];
                    try {
                        int aPid = Integer.parseInt(pidStr);
                        if (aPid > pid) {
                            pid = aPid;
                            break;
                        }
                    } catch (NumberFormatException e) {
                    }
                }
            }
            p.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (pbr != null) {
                try {
                    pbr.close();
                } catch (IOException e) {
                }
            }
        }

        stopBuildinRecorder("su -c kill -2 " + pid);
        logd("kill ss with pid " + pid);
        pid = 0;
    }
    
	public void startRecordingScreen() {
        logd("start recording screen");
        if (YTWHelper.hasBuildinScreenRecorder()) {
            startBuildinRecorder();
            startAudioRecorder(false);
        } else {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
            mOrientationListener = new OrientationListener();
            registerReceiver(mOrientationListener, filter);

            mRotateListener = new MyOrientationEventListener(this,
                    SensorManager.SENSOR_DELAY_NORMAL);
            if (mRotateListener.canDetectOrientation()) {
                mRotateListener.enable();
            }
            startAudioRecorder(true);
        }
	}

    boolean doInitAudioRecorder(final boolean recordVideo) {
        if (recordVideo) {
            int sampleRate = 44100 / 2;
            int channelConfig = AudioFormat.CHANNEL_IN_MONO;
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            mAudioBufferSampleSize = sampleRate / 7;
            mAudioBufferSize = mAudioBufferSampleSize * 2;
            mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    sampleRate, channelConfig, audioFormat, mAudioBufferSize);

            if (mAudioRecord == null)
                return false;

            audioBuffer = new byte[mAudioBufferSize];
            mAudioRecord.setPositionNotificationPeriod(mAudioBufferSampleSize);
            mAudioRecord.setRecordPositionUpdateListener(new OnRecordPositionUpdateListener() {
                public void onPeriodicNotification(AudioRecord recorder) {
                    encodeFrame(audioBuffer, audioSamplesRead);
                }
                public void onMarkerReached(AudioRecord recorder) {
                    inRecordMode = false;
                    try {
                        recorder.stop();
                    } catch (IllegalStateException e) {
                    } finally {
                        recorder.release();
                        mAudioRecord = null;
                    }
                }
            });
        } else {
            try {
                mMediaRecorder = new MediaRecorder();
                mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mMediaRecorder.setAudioSamplingRate(44100/2);
                mMediaRecorder.setAudioChannels(1);
                mMediaRecorder.setOutputFile(videoPath.substring(0, videoPath.length()-4) + "-a.mp4");
                mMediaRecorder.prepare();
            } catch (Exception e){
                e.printStackTrace();
                mMediaRecorder = null;
                return false;
            }
        }
        return true;
    }

    void doStartAudioRecorder(final boolean recordVideo) {
        if (recordVideo) {
            mAudioRecord.startRecording();
            int audioRecordingState = mAudioRecord.getRecordingState();
            if (audioRecordingState != AudioRecord.RECORDSTATE_RECORDING) {
                gotError();
            }
            inRecordMode = true;
            new AsyncTask<Void, Void, Boolean>() {

                @Override
                protected Boolean doInBackground(Void... params) {
                    while (inRecordMode) {
                        audioSamplesRead = mAudioRecord.read(audioBuffer, 0, mAudioBufferSize);
                    }
                    return true;
                }

                @Override
                protected void onPostExecute(Boolean done) {
                    if (done) {
                        try {
                            mAudioRecord.stop();
                        } catch (IllegalStateException e) {
                        } finally {
                            mAudioRecord.release();
                            mAudioRecord = null;
                        }
                    }
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            mMediaRecorder.start();
        }
    }

    void startAudioRecorder(final boolean recordVideo) {
        try {
            if (doInitAudioRecorder(recordVideo)) {
                if (recordVideo) {
                    initRecorder(YTWHelper.correctFilePath(videoPath), 0);
                    doStartAudioRecorder(recordVideo);
                    sendBroadcast(new Intent().setAction(ACTION_SCREEN_RECORDER_STARTED));
                } else {
                    for (int i=0; i<100; i++) {
                        if (YTWHelper.isBuildinScreenRecorderRunning()) {
                            try {
                                doStartAudioRecorder(recordVideo);
                                logd("started audio recorder");
                                sendBroadcast(new Intent().setAction(ACTION_SCREEN_RECORDER_STARTED));
                            } catch (IllegalStateException e) {
                                // todo tell user to restart photo to fix the issue
                                Toast.makeText(this, "无法启动录音机\n请重启手机重新录制", Toast.LENGTH_LONG).show();
                                stopBuildinRecorder();
                                // broadcast
                            }
                            break;
                        } else {
                            Thread.sleep(100);
                        }
                    }
                }
            }
        } catch (IllegalArgumentException e) {
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopRecordingScreen() {
        if (pid > 0) {
            stopBuildinRecorder();
            if (mMediaRecorder != null)
                try {
                    mMediaRecorder.stop();
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                } finally {
                    mMediaRecorder.reset();
                    mMediaRecorder.release();
                    mMediaRecorder = null;
                }
            // test it, may be need delay
            sendBroadcast(new Intent().setAction(ACTION_SCREEN_RECORDER_STOPPED));
        } else {
            unregisterReceiver(mOrientationListener);
            mOrientationListener.onUpdateOrientation();
            if (mRotateListener.canDetectOrientation()) {
                mRotateListener.disable();
            }
            inRecordMode = false;
            stopRecording();
            sendBroadcast(new Intent().setAction(ACTION_SCREEN_RECORDER_STOPPED)
                    .putExtra("orientation", estimateOrientation()));
        }
    }

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getData() != null) {
            videoPath = intent.getData().getPath();
            Log.d("Recorder", "start recording screen " + videoPath);
            videoLandscape = intent.getBooleanExtra("video_landscape", false);
            videoWidth = intent.getIntExtra("video_width", 0);
            videoHeight = intent.getIntExtra("video_height", 0);
            videoBitrate = intent.getIntExtra("video_bitrate", 0);

            startRecordingScreen();
        }
		return super.onStartCommand(intent, flags, startId);
    }
    
	@Override
	public void onDestroy() {
        stopRecordingScreen();
        
		super.onDestroy();
	}

    MyOrientationEventListener mRotateListener;
    class MyOrientationEventListener extends OrientationEventListener {

        long timeAtOrientationLeft;
        long timeAtOrientationRight;

        public boolean estimateOrientation() {
            return timeAtOrientationLeft > timeAtOrientationRight;
        }

        public MyOrientationEventListener(Context context, int rate) {
            super(context, rate);
        }

        @Override
        public void onOrientationChanged(int rotate) {
            if (rotate < 180) {
                timeAtOrientationLeft ++;
            } else {
                timeAtOrientationRight ++;
            }
        }
    }


    OrientationListener mOrientationListener;
    class OrientationListener extends BroadcastReceiver {
        long updateTime;

        long timeAtOrientationLandscape;
        long timeAtOrientationPortrait;

        int orientation;

        public boolean estimateOrientation() {
            return timeAtOrientationLandscape > timeAtOrientationPortrait;
        }

        public OrientationListener() {
            updateTime = System.currentTimeMillis();
            orientation = getResources().getConfiguration().orientation;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
                onUpdateOrientation();
            }
        }

        public void onUpdateOrientation() {
            if (orientation != getResources().getConfiguration().orientation) {
                long currentTime = System.currentTimeMillis();
                orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    timeAtOrientationPortrait += currentTime - updateTime;
                } else {
                    timeAtOrientationLandscape += currentTime - updateTime;
                }
                updateTime = currentTime;
            }
        }
    }

    public int estimateOrientation() {
        if (mOrientationListener.estimateOrientation()) { // is landscape
            if (mRotateListener.estimateOrientation()) { // is left
                return 3; // landscape left
            } else {
                return 1; // landscape right
            }
        } else {
            return 0; // portrait
        }
    }

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

    private void gotError() {
        Log.i(TAG, "stop by exception");
    }
    
    public void logd(String msg) {
        Log.d(TAG, msg);
    }
      
    String indicatorFilePath() {
        return new File(new File(videoPath).getParent(), ".record").getAbsolutePath();
    }

    void touchIndicatorFile() {
        try {
            new File(indicatorFilePath()).createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    void rmIndicatorFile() {
        new File(indicatorFilePath()).delete();
    }

    void prepareRecordScript() {
        String sp = YTWHelper.screenrecordScriptPath();
        OutputStream out = null;
        try {
            out = new FileOutputStream(sp);
            BufferedWriter writer = StreamHelper.writer(out);
            String content =
//                    "su -c settings put system show_touches $5\n" +
                    "c=0\n" +
                    "while [ $c -lt 20 ]\n" +
                    "do\n" +
                    "\tif [ -e $1 ]; then\n" +
                    "\t\t/system/bin/screenrecord  --size $2  --bit-rate $3 $4-${c}.mp4\n" +
                    "\tfi\n" +
                    "\tlet c=c+1\n" +
                    "done\n";
            writer.write(content);
            writer.flush();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
