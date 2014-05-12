package me.yaotouwan.screenrecorder;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Point;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.*;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.*;
import me.yaotouwan.R;
import me.yaotouwan.post.BaseActivity;
import me.yaotouwan.util.YTWHelper;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class PreviewVideoActivity extends BaseActivity implements SurfaceHolder.Callback {

    private MediaPlayer mPlayer;
    boolean isPlayerPrepared;
    Timer updateProgressTimer;
    private RelativeLayout previewGroup;
    private SurfaceView surface;
    private SurfaceHolder sHolder;
    private int videoWidth;
    private int videoHeight;
    private int videoDuration;
    String videoPath;
    private ImageButton playButton;
    private SeekBar seekBar;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.preview_video);

        hideActionBar();
        enterFullscreen();

        previewGroup = (RelativeLayout) findViewById(R.id.preview_group);
        playButton = (ImageButton) findViewById(R.id.preview_video_play_btn);
        seekBar = (SeekBar) findViewById(R.id.seek_bar);

        surface = (SurfaceView) findViewById(R.id.surfaceView);
        sHolder = surface.getHolder();
        sHolder.addCallback(this);
        sHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        videoPath = getIntent().getData().getPath();
        mPlayer = new MediaPlayer();
        try {
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mPlayer.setDataSource(videoPath);
            mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mPlayer.setDisplay(sHolder);
                    videoWidth = mp.getVideoWidth();
                    videoHeight = mp.getVideoHeight();
                    isPlayerPrepared = true;
                    layoutSurface();
                    play();
                }
            });
            mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    updateUIForPlayerPaused();
                }
            });
            mPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
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

    @Override
    protected void onViewSizeChanged() {
        super.onViewSizeChanged();

        layoutSurface();
    }

    @Override
    public void onDestroy(){
        super.onDestroy();

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

        if (mPlayer.isPlaying()) {
            pause();
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

    @Override
    public void onBackPressed() {
        setResult(101);
        super.onBackPressed();
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

    void updateUIForPlayerPaused() {
        exitFullscreen();
        playButton.setImageResource(R.drawable.btn_play_video);
        playButton.setBackgroundColor(Color.parseColor("#55000000"));
        AlphaAnimation animation = new AlphaAnimation(0, 1);
        animation.setDuration(defaultAnimationDuration);
        playButton.startAnimation(animation);

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

    void play() {
        mPlayer.start();
        enterFullscreen();

        AlphaAnimation animation = new AlphaAnimation(1, 0);
        animation.setDuration(defaultAnimationDuration);
        animation.setAnimationListener(new Animation.AnimationListener() {
            public void onAnimationStart(Animation animation) {
            }

            public void onAnimationRepeat(Animation animation) {
            }

            public void onAnimationEnd(Animation animation) {
                playButton.setImageDrawable(null);
                playButton.setBackgroundColor(Color.parseColor("#00000000"));
            }
        });
        playButton.startAnimation(animation);

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
                        seekBar.post(new Runnable() {
                            @Override
                            public void run() {
                                seekBar.setProgress((int) (progress * seekBar.getMax()));
                            }
                        });
                    }
                }
            }, 0, 200);
        }
    }
}