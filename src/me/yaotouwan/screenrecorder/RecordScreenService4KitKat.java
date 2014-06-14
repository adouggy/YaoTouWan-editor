package me.yaotouwan.screenrecorder;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.IBinder;
import android.util.Log;
import android.view.OrientationEventListener;
import android.widget.Toast;
import me.yaotouwan.util.StreamHelper;
import me.yaotouwan.util.YTWHelper;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jason on 14-6-9.
 */
public class RecordScreenService4KitKat extends SRecorderService {

    public static final String TAG = "RecordScreenService4KitKat";

    private int mAudioBufferSize;
    AudioRecord mAudioRecord;
    private List<byte[]> audioBuffers;
    private int audioBufferWriteOffset;
    private int audioBufferReadOffset;
    private int audioSamplesRead;
    String videoPath;
    int videoWidth, videoHeight, videoBitrate;
    boolean isRecording;
    boolean isEncoding;
    private int videoFPS = 7;

    public static final String ACTION_SCREEN_RECORDER_STARTED
            = "me.yaotouwan.screenrecorder.action.started";
    public static final String ACTION_SCREEN_RECORDER_STOPPED
            = "me.yaotouwan.screenrecorder.action.stopped";

    // parameters for video
    boolean videoLandscape;

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
        if (pid > 0) {
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
    }

    public void startRecordingScreen() {
        logd("start recording screen");
        startBuildinRecorder();
        startAudioRecorder(false);
    }

    boolean doInitAudioRecorder() {
        int sampleRate = 44100 / 2;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        mAudioBufferSize = sampleRate / videoFPS * 2;
        int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 4;
        if (bufferSize <= mAudioBufferSize) {
            bufferSize = mAudioBufferSize;
        }
        if (mAudioRecord == null)
            mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    sampleRate, channelConfig, audioFormat, bufferSize);

        audioBuffers = new ArrayList<byte[]>(5);
        for (int i=0; i<5; i++) {
            byte[] audioBuffer = new byte[mAudioBufferSize];
            audioBuffers.add(audioBuffer);
        }
        return true;
    }

    boolean doStartAudioRecorder() {
        logd("start audio recorder...");
        mAudioRecord.startRecording();
        if (mAudioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            logd("failed");
            mAudioRecord.stop();
            mAudioRecord = null;
            Toast.makeText(this, "录音机启动失败", Toast.LENGTH_LONG);
            return false;
        }
        logd("done");

        isRecording = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                logd("start read audio samples");
                while (isRecording) {
                    if (mAudioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                        break;
                    }
                    audioSamplesRead = mAudioRecord.read(
                            audioBuffers.get(audioBufferWriteOffset % audioBuffers.size()), 0, mAudioBufferSize);
                    audioBufferWriteOffset ++;
//                    logd("read audio samples " + audioBufferWriteOffset + "/" + audioSamplesRead);
                    synchronized (audioBuffers) {
                        audioBuffers.notifyAll();
                    }
                }
                mAudioRecord.stop();
                synchronized (mAudioRecord) {
                    mAudioRecord.notify();
                }
                logd("end reading audio samples");
            }
        }).start();

        new Thread(new Runnable() {
            AudioManager audio;
            @Override
            public void run() {
                isEncoding = true;
                logd("start to encode frames");
                while (isRecording) {
                    synchronized (audioBuffers) {
                        try {
                            audioBuffers.wait(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            break;
                        }
                    }
                    if (audio == null)
                        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    int currentVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                    int maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    float audioGain = maxVolume * 0.95f / currentVolume;

                    // use loop to consume all missed audio samples
                    while (audioBufferReadOffset < audioBufferWriteOffset && isRecording) {
                        if (audioBufferReadOffset < audioBufferWriteOffset - videoFPS) { // give up too many missed
                            audioBufferReadOffset = audioBufferWriteOffset - videoFPS;
                        }
//                        logd("encode frame");
                        encodeFrame(audioBuffers.get(audioBufferReadOffset % audioBuffers.size()), audioSamplesRead, audioGain);
                        audioBufferReadOffset ++;
//                        logd("encode frame " + audioBufferReadOffset);
                    }
                }
                stopRecording();
                logd("stopRecording done");
                synchronized (audioBuffers) {
                    audioBuffers.notify();
                }
                logd("audioBuffers.notify() done");
                synchronized (RecordScreenService4KitKat.this) {
                    isEncoding = false;
                }
                logd("end encode frame");
            }
        }).start();
        return true;
    }

    void startAudioRecorder() {
        try {
            if (doInitAudioRecorder()) {
                for (int i=0; i<100; i++) {
                    if (YTWHelper.getPid(BUILDIN_RECORDER_NAME) > 0) {
                        String audioFilePath = videoPath.substring(0, videoPath.length() - 4) + "-a.mp4";
                        initRecorder(YTWHelper.correctFilePath(audioFilePath), 0, 0, 0, false);
                        if (doStartAudioRecorder()) {
                            sendBroadcast(new Intent().setAction(ACTION_SCREEN_RECORDER_STARTED));
                        } else {
                            stopBuildinRecorder();
                            Toast.makeText(this, "无法启动录音机\n请重启手机重新录制", Toast.LENGTH_LONG).show();
                        }
                        return;
                    } else {
                        Thread.sleep(100);
                    }
                }
                // todo wait 10s, but buildin recorder not started
            }
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopRecordingScreen() {
        stopBuildinRecorder();
        isRecording = false;
        logd("stopRecording " + mAudioRecord);
        try {
            logd("wait audio thread stop");
            synchronized (mAudioRecord) { // wait audio thread stop
                mAudioRecord.wait();
            }
            logd("waited audio thread stop");
            boolean isNowEncoding = false;
            synchronized (this) {
                isNowEncoding = isEncoding;
            }
            if (isNowEncoding) {
                logd("wait video thread stop");
                synchronized (audioBuffers) { // wait video thread stop
                    audioBuffers.wait();
                }
                logd("waited video thread stop");
            } else {
                logd("video recorder has stopped");
            }
        } catch (IllegalStateException e) {
            logd(e.toString());
            e.printStackTrace();
        } catch (Exception e) {
            logd(e.toString());
            e.printStackTrace();
        } finally {
            logd("send broad cast");
            Intent intent = new Intent().setAction(ACTION_SCREEN_RECORDER_STOPPED);
            if (!YTWHelper.hasBuildinScreenRecorder()) {
                intent.putExtra("orientation", estimateOrientation());
            }
            sendBroadcast(intent);
            logd("screen recoder done.");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        logd("onStartCommand");
        if (intent != null && intent.getData() != null) {
            videoPath = intent.getData().getPath();
            logd("start recording screen " + videoPath);
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
        logd("onDestroy");
        stopRecordingScreen();

        super.onDestroy();
    }

    MyOrientationEventListener mRotateListener;
    class MyOrientationEventListener extends OrientationEventListener {

        long timeAtOrientationLeft;
        long timeAtOrientationRight;

        public boolean estimateOrientation() { // is left
            return timeAtOrientationLeft > timeAtOrientationRight;
        }

        public MyOrientationEventListener(Context context, int rate) {
            super(context, rate);
        }

        @Override
        public void onOrientationChanged(int rotate) {
//            logd("rotate = " + rotate);
            if (rotate < 0) return;
            if (rotate > 0 && rotate <= 180) {
                timeAtOrientationRight ++;
            } else {
                timeAtOrientationLeft ++;
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
                logd("update orientation");
                long currentTime = System.currentTimeMillis();
                orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    timeAtOrientationPortrait += currentTime - updateTime;
                } else {
                    timeAtOrientationLandscape += currentTime - updateTime;
                }
                logd("timeAtOrientationPortrait = " + timeAtOrientationPortrait + ", timeAtOrientationLandscape = " + timeAtOrientationLandscape);
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

    public void logd(String msg) {
        Log.d("Yaotouwan_" + getClass().getSimpleName().toString(), msg);
    }

    void touchIndicatorFile() {
        try {
            new File(indicatorFilePath()).createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void prepareRecordScript() {
        String sp = YTWHelper.screenrecordScriptPath();
        OutputStream out = null;
        try {
            out = new FileOutputStream(sp);
            BufferedWriter writer = StreamHelper.writer(out);
            String content =
                    "sleep 3\n" +
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
