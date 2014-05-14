package me.yaotouwan.screenrecorder;

import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioRecord.OnRecordPositionUpdateListener;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
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
    int videoQuality;
    int screenWidth, screenHeight;

    // parameters for video
    int videoEncoderParameterRotate; // 0->Up, 1->Left, 2->Bottom, 3->Right
    int audioEncoderParameterBitRate; // XXbps, default 64000


	private native int initRecorder(String filename, int rotation);
	private native int encodeFrame(byte[] audioBuffer, int audioSamplesSize);
	private native int stopRecording();

    int pid;

    private native int startBuildinRecorder(String command);
    private native int stopBuildinRecorder(String command);

    static {
        System.loadLibrary("srecorder");
    }

    public static int getVideoWidthByQuality(int videoQuality) {
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

    public static String getVideoBitrateByQuality(int videoQuality) {
        String bitrate = "";
        if (videoQuality == 0) {
            bitrate = "800000";
        } else if (videoQuality == 1) {
            bitrate = "800000";
        } else if (videoQuality == -1) {
            bitrate = "200000";
        }
        return bitrate;
    }

    String buildCommandLine() {
        String bitrate = getVideoBitrateByQuality(videoQuality);
        double hwratio = screenHeight * 1.0 / screenWidth;
        int w = getVideoWidthByQuality(videoQuality);
        int h = (int) (w * hwratio * 1.0 / 2 * 2);
        String size = w + "x" + h;
        String firstVideoPath = YTWHelper.correctFilePath(videoPath.substring(0, videoPath.length() - 4));
        String recordScriptPath = YTWHelper.screenrecordScriptPath();
        String cmd = "su -c sh " + recordScriptPath + " " +
                indicatorFilePath() + " " + size + " " + bitrate + " " + firstVideoPath;
        logd(cmd);
        return cmd;
    }

    boolean startBuildinRecorder() {
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
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            if (pbr != null) {
                try {
                    pbr.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        stopBuildinRecorder("su -c kill -2 " + pid);
        logd("kill ss with pid " + pid);
        pid = 0;
    }
    
	public void startRecordingScreen() {
        if (YTWHelper.hasBuildinScreenRecorder()) {
            startBuildinRecorder();
            startAudioRecorder(false);
        } else {
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
                    recorder.stop();
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

            new Thread(new Runnable() {
                public void run() {
                    while (inRecordMode) {
                        audioSamplesRead = mAudioRecord.read(audioBuffer, 0, mAudioBufferSize);
                    }
                    mAudioRecord.stop();
                }
            }).start();
        } else {
            mMediaRecorder.start();
        }
    }

    void startAudioRecorder(final boolean recordVideo) {
        new Thread(new Runnable() {

            public void run() {
                try {
                    if (doInitAudioRecorder(recordVideo)) {
                        if (recordVideo) {
                            initRecorder(YTWHelper.correctFilePath(videoPath),
                                    videoEncoderParameterRotate);
                            doStartAudioRecorder(recordVideo);
                        } else {
                            for (int i=0; i<100; i++) {
                                if (YTWHelper.isBuildinScreenRecorderRunning()) {
                                    doStartAudioRecorder(recordVideo);
                                    break;
                                } else {
                                    Thread.sleep(100);
                                }
                            }
                        }
                    }
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void stopRecordingScreen() {
        if (pid > 0) {
            stopBuildinRecorder();
            if (mMediaRecorder != null)
                try {
                    mMediaRecorder.stop();
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
        } else {
            inRecordMode = false;
            stopRecording();
        }
    }

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
        videoPath = intent.getData().getPath();
        videoEncoderParameterRotate = intent.getIntExtra("video_orientation", 0);
        videoQuality = intent.getIntExtra("video_quality", 0);
        screenWidth = intent.getIntExtra("screen_width", 0);
        screenHeight = intent.getIntExtra("screen_height", 0);
		startRecordingScreen();
		return super.onStartCommand(intent, flags, startId);
    }
    
	@Override
	public void onDestroy() {
		stopRecordingScreen();
        
		super.onDestroy();
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
            String content = "c=0\n" +
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
