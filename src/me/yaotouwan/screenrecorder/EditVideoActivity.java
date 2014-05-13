package me.yaotouwan.screenrecorder;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.ActionBar;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;
import com.actionbarsherlock.view.MenuItem;
import me.yaotouwan.R;
import me.yaotouwan.post.BaseActivity;
import me.yaotouwan.post.CutVideoSelector;
import me.yaotouwan.post.PostActivity;
import me.yaotouwan.uicommon.ActionSheet;
import me.yaotouwan.uicommon.ActionSheetItem;
import me.yaotouwan.util.YTWHelper;

import java.io.File;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.*;

public class EditVideoActivity extends BaseActivity implements SurfaceHolder.Callback {

//    private VideoStream player;
    private MediaPlayer mPlayer;
    private RelativeLayout previewGroup;
    private SurfaceView surface;
    private SurfaceHolder sHolder;
    private CutVideoSelector selector;
    private int videoWidth;
    private int videoHeight;
    private int videoDuration;
    private int rotate;
    private boolean isVideoRotated;
    private ImageView previewImageView;
    private ImageButton playButton;
    private ProgressDialog mDialog;
    String videoPath;
    boolean didLayout;
    boolean isShouldReplay;

    boolean isPlayerPrepared;
    Timer updateProgressTimer;

    private native int cutVideo(String srcFilename,
                                String dstFilename,
                                double startProgress,
                                double endProgress);
    private native int mergeVideo(String srcVideoFilename,
                                  String srcAudioFilename,
                                  String dstFilename);
    private native int[] prepareDecoder(String filename);
    private native int[] decodeFrame(double progress, boolean isLarge);
    private native void clearDecoder();

    static {
        System.loadLibrary("srecorder");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit_video);

        menuResId = R.menu.cut_video_actions;

        ActionBar bar = getActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setDisplayShowHomeEnabled(false);
            bar.setTitle(R.string.back);
        }

        previewGroup = (RelativeLayout) findViewById(R.id.preview_group);
        surface = (SurfaceView) findViewById(R.id.surfaceView);
        previewImageView = (ImageView) findViewById(R.id.preview_image_view);
        playButton = (ImageButton) findViewById(R.id.preview_video_play_btn);
        selector = (CutVideoSelector) findViewById(R.id.cut_video_selector);

        sHolder = surface.getHolder();
        assert sHolder != null;
        sHolder.addCallback(this);
        sHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        try {
            assert getIntent() != null && getIntent().getData() != null;
            videoPath = getIntent().getData().getPath();
            if (new File(videoPath).exists()) {
                prepareVideoPlayer();
            } else {
                String p = videoPath.substring(0, videoPath.length()-4);
                String firstPath = p + "-0.mp4";
                if (new File(firstPath).exists()) { // merge files
                    mergeVideo();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onViewSizeChanged() {
        super.onViewSizeChanged();

        layoutSurface();
    }

    @Override
    public void onDestroy(){
        super.onDestroy();

        clearDecoder();

        if (mPlayer != null) {
            if (mPlayer.isPlaying()) {
                mPlayer.stop();
            }
            mPlayer.release();
            mPlayer = null;
        }
        if (updateProgressTimer != null) {
            updateProgressTimer.cancel();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mPlayer != null && mPlayer.isPlaying()) {
            pause();
        }
    }

    @Override
    public void onBackPressed() {
        if (mPlayer.isPlaying()) {
            pause();
        } else if (selector.startProgress() > 0 || selector.endProgress() < 1) {
            List<ActionSheetItem> items = new ArrayList<ActionSheetItem>();
            items.add(new ActionSheetItem(getString(R.string.cut_video_giveup),
                    new ActionSheetItem.ActionSheetItemOnClickListener() {
                        @Override
                        public void onClick() {
                            finish();
                        }
                    }));
            items.add(new ActionSheetItem(getString(R.string.cancel), null));
            ActionSheet.showWithItems(this, items);
        } else {
            super.onBackPressed();
        }
    }

    void prepareVideoPlayer() {
        if (!readVideoInfo(getIntent().getData())) {
            loge("failed to get video size");
        }
    }

    private void cutVideo() {
        mDialog = new ProgressDialog(this);
        mDialog.setMessage(getString(R.string.please_wait));
        mDialog.setCancelable(false);
        mDialog.show();

        new CutVideoTask().execute();
    }

    void mergeVideo() {
        mDialog = new ProgressDialog(this);
        mDialog.setMessage(getString(R.string.please_wait));
        mDialog.setCancelable(false);
        mDialog.show();

        new MergeVideoTask().execute();
    }

    void play() {
        if (isShouldReplay) {
            mPlayer.seekTo((int) (selector.startProgress() * mPlayer.getDuration()));
        }
        mPlayer.start();
        toggleActionBar();
        enterFullscreen();
        hideView(previewImageView);

        AlphaAnimation animation = new AlphaAnimation(1, 0);
        animation.setDuration(defaultAnimationDuration);
        animation.setAnimationListener(new Animation.AnimationListener() {
            public void onAnimationStart(Animation animation) {}
            public void onAnimationRepeat(Animation animation) {}
            public void onAnimationEnd(Animation animation) {
                playButton.setImageDrawable(null);
                playButton.setBackgroundColor(Color.parseColor("#00000000"));
            }
        });
        playButton.startAnimation(animation);

        TranslateAnimation selectorAnimation =
                new TranslateAnimation(0, 0, 0, selector.getHeight() + marginBottom(selector));
        selectorAnimation.setFillAfter(true);
        selectorAnimation.setDuration(defaultAnimationDuration);
        selector.startAnimation(selectorAnimation);

        Integer colorFrom = getResources().getColor(R.color.white);
        Integer colorTo = getResources().getColor(R.color.black);
        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
        colorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                if (animator.getAnimatedValue() == null) return;
                getRootViewGroup().setBackgroundColor((Integer)animator.getAnimatedValue());
            }
        });
        colorAnimation.setDuration(defaultAnimationDuration);
        colorAnimation.start();

        if (updateProgressTimer == null) {
            updateProgressTimer = new Timer();
            updateProgressTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if (mPlayer != null && mPlayer.isPlaying()) {
                        final double progress = mPlayer.getCurrentPosition() * 1.0 / mPlayer.getDuration();
                        selector.post(new Runnable() {
                            @Override
                            public void run() {
                                selector.setProgress(progress);
                            }
                        });

                        if (progress > selector.endProgress()) {
                            pause();
                        }
                    }
                }
            }, 0, 200);
        }
    }

    void pause() {
        if (mPlayer != null) {
            mPlayer.pause();
        }
        if (updateProgressTimer != null) {
            updateProgressTimer.cancel();
        }
        updateUIForPlayerPaused();
    }

    void updateUIForPlayerPaused() {
        toggleActionBar();
        exitFullscreen();
        showView(selector);
        playButton.setImageResource(R.drawable.btn_play_video);
        playButton.setBackgroundColor(Color.parseColor("#55000000"));
        AlphaAnimation animation = new AlphaAnimation(0, 1);
        animation.setDuration(defaultAnimationDuration);
        playButton.startAnimation(animation);

        TranslateAnimation selectorAnimation =
                new TranslateAnimation(0, 0, selector.getHeight() + marginBottom(selector), 0);
        selectorAnimation.setFillAfter(true);
        selectorAnimation.setDuration(defaultAnimationDuration);
        selector.startAnimation(selectorAnimation);

        Integer colorFrom = getResources().getColor(R.color.black);
        Integer colorTo = getResources().getColor(R.color.white);
        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
        colorAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                if (animator.getAnimatedValue() == null) return;
                getRootViewGroup().setBackgroundColor((Integer)animator.getAnimatedValue());
            }
        });
        colorAnimation.setDuration(defaultAnimationDuration);
        colorAnimation.start();
    }

    public void onClickPlayButton(View view) {
        if (mPlayer.isPlaying()) {
            pause();
        } else {
            play();
        }
    }

    public void onClickFinishMenuItem(MenuItem menuItem) {
        if (selector.startProgress() == 0 && selector.endProgress() == 1) {
            Intent intent = new Intent(this, PostActivity.class);
            intent.setData(Uri.parse(videoPath));
            intent.putExtra("crop_video_finish", true);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            setResult(RESULT_OK, intent);

            startActivity(intent);
        } else {
            cutVideo();
        }
    }

    public class CutVideoTask extends AsyncTask<Integer, Integer, Boolean> {
        public static final String TAG	= "recorder";
        String dstfpath;

        protected Boolean doInBackground(Integer... args) {
            String srcfpath = videoPath;
            dstfpath = YTWHelper.prepareFilePathForVideoSave();
            cutVideo(srcfpath, dstfpath,
                    selector.startProgress(),
                    selector.endProgress());
            return true;
        }

        protected void onPostExecute(Boolean result) {
            mDialog.dismiss();
            Intent intent = new Intent();
            intent.setData(Uri.parse(dstfpath));
            setResult(RESULT_OK, intent);
            finish();
        }
    }

    class MergeVideoTask extends AsyncTask<Integer, Integer, Boolean> {
        public static final String TAG	= "recorder";

        protected Boolean doInBackground(Integer... args) {
            String srcfpath = videoPath.substring(0, videoPath.length()-4);
            String srcAfPath = videoPath.substring(0, videoPath.length()-4)+"-a.mp4";
            if (new File(srcAfPath).exists())
                mergeVideo(srcfpath, srcAfPath, videoPath);
            else
                return false;
            // remove old fragment files
            return true;
        }

        protected void onPostExecute(Boolean result) {
            mDialog.dismiss();

            if (result && new File(videoPath).exists()) {
                prepareVideoPlayer();
            } else {
                Toast.makeText(EditVideoActivity.this, "合并文件失败", 1000);
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    private IntBuffer makeBuffer(int[] src) {
        IntBuffer dst = IntBuffer.allocate(src.length);
        dst.put(src);
        dst.rewind();
        return dst;
    }

    private class ReadVideoInfoTask extends AsyncTask<String, Integer, Boolean> {

        Bitmap previewImage;
        Queue<Bitmap> sliceImages;
        Bitmap sliceImageLastOne;

        @Override
        protected Boolean doInBackground(String... params) {
            assert params.length > 0;
            String videoPath = params[0];
            // start to read video infomation
            logd("start to read video infomation");
            int[] info = prepareDecoder(videoPath);
            rotate = info[3];
            if (rotate == 0 || rotate == 180) {
                isVideoRotated = false;
                videoWidth = info[0];
                videoHeight = info[1];
            } else {
                isVideoRotated = true;
                videoWidth = info[1];
                videoHeight = info[0];
            }
            logd("video " + videoWidth + "x" + videoHeight);
            videoDuration = info[2];
            logd("got video info via ffmpeg duration " + videoDuration);

            if (videoWidth <= 0 || videoHeight <= 0 || videoDuration <= 0) {
                loge("video error");
                return false;
            }

            int[] frameBytes = decodeFrame(0, true);
            if (isVideoRotated) {
                previewImage = Bitmap.createBitmap(videoHeight, videoWidth, Bitmap.Config.ARGB_8888);
                previewImage.copyPixelsFromBuffer(makeBuffer(frameBytes));
                previewImage = YTWHelper.rotateBitmap(previewImage, rotate);
            } else {
                previewImage = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888);
                previewImage.copyPixelsFromBuffer(makeBuffer(frameBytes));
            }

            publishProgress(-1);

            int sliceCount = 10;
            sliceImages = new LinkedList<Bitmap>();
            for (int p=0; p<sliceCount; p++) {
                frameBytes = decodeFrame(p * 1.0 / sliceCount, false);
                if (frameBytes == null) {
                    if (sliceImageLastOne != null) {
                        sliceImages.add(sliceImageLastOne);
                        publishProgress(p);
                    }
                    continue;
                }
                Bitmap sliceImage;
                if (isVideoRotated) {
                    sliceImage = Bitmap.createBitmap(100, 100 * videoWidth / videoHeight, Bitmap.Config.ARGB_8888);
                    sliceImage.copyPixelsFromBuffer(makeBuffer(frameBytes));
                    sliceImage = YTWHelper.rotateBitmap(sliceImage, rotate);
                } else {
                    sliceImage = Bitmap.createBitmap(100, 100 * videoHeight / videoWidth, Bitmap.Config.ARGB_8888);
                    sliceImage.copyPixelsFromBuffer(makeBuffer(frameBytes));
                }
                sliceImages.add(sliceImage);
                sliceImageLastOne = sliceImage;
                publishProgress(p);
            }
            return true;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            if (values.length == 0) return;

            int progress = values[0];
            if (progress == -1) {
                layoutSurface();

                previewImageView.setImageDrawable(new BitmapDrawable(getResources(), previewImage));
                previewImage = null;
            } else if (progress >= 0) {
                try {
                    selector.addPreview(sliceImages.poll());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            super.onProgressUpdate(values);
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            super.onPostExecute(aBoolean);

            logd("Complete read video info");
            mPlayer = new MediaPlayer();
            try {
                mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mPlayer.setDataSource(videoPath);
                mPlayer.setDisplay(sHolder);
                mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        isPlayerPrepared = true;
                    }
                });
                mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        updateUIForPlayerPaused();
                    }
                });
                mPlayer.prepareAsync();

                selector.onValueChangedListener =
                        new CutVideoSelector.OnValueChangedListener() {
                            @Override
                            public void onChangePreviewProgress(final double previewProgress) {
                                if (mPlayer.isPlaying()) {
                                    pause();
                                }
                                hideView(previewImageView);
                                hideView(playButton);
                                mPlayer.seekTo((int) (previewProgress * videoDuration));
                                isShouldReplay = false;
                            }

                            @Override
                            public void onStopChangingPreviewProgress() {
                                showView(playButton);
                                isShouldReplay = false;
                            }

                            @Override
                            public void onStopChangingStartProgress() {
                                showView(playButton);
                                isShouldReplay = false;
                                updateUIForSelectorValueChanged();
                            }

                            @Override
                            public void onStopChangingEndProgress() {
                                showView(playButton);
                                isShouldReplay = true;
                                updateUIForSelectorValueChanged();
                            }
                        };

                selector.minInterval = 1000.0 / videoDuration;

                mDialog.dismiss();
                showView(playButton);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    void updateUIForSelectorValueChanged() {
        MenuItem menuItem = actionsMenu.findItem(R.id.post_action_finish);
        if (selector.startProgress() > 0 || selector.endProgress() < 1) {
            menuItem.setIcon(null);
            menuItem.setTitle(R.string.cut_video_action_cut);
        } else {
            menuItem.setIcon(R.drawable.post_action_bar_icon_done);
            menuItem.setTitle(R.string.finish);
        }
    }

    void layoutSurface() {
        View rootViewGroup = getRootViewGroup();
        Point rootSize = new Point(rootViewGroup.getWidth(), rootViewGroup.getHeight());
        logd(rootSize.toString());
        Point surfaceSize = YTWHelper.restrictSizeInSize(new Point(videoWidth, videoHeight), rootSize);
        logd(surfaceSize.toString());
        setViewSize(previewGroup, surfaceSize.x, surfaceSize.y);
    }

    boolean readVideoInfo(Uri uri) {
        logd("get video size " + uri.getPath());
        assert uri != null && uri.getPath() != null;
        if (uri.getPath().endsWith(".mp4")) {
            mDialog = new ProgressDialog(this);
            mDialog.setMessage(getString(R.string.please_wait));
            mDialog.setCancelable(false);
            mDialog.show();
            new ReadVideoInfoTask().execute(uri.getPath());
            return true;
        }
        return false;
    }
}