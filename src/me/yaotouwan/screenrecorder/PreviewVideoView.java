package me.yaotouwan.screenrecorder;

import android.content.Context;
import android.util.AttributeSet;

public class PreviewVideoView extends android.widget.VideoView {
    
    private PlayPauseListener mListener;
    
    public PreviewVideoView(Context context) {
        super(context);
    }
    
    public PreviewVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    public PreviewVideoView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    
    public void setPlayPauseListener(PlayPauseListener listener) {
        mListener = listener;
    }
    
    @Override
    public void pause() {
        super.pause();
        if (mListener != null) {
            mListener.onPause();
        }
    }
    
    @Override
    public void start() {
        super.start();
        if (mListener != null) {
            mListener.onPlay();
        }
    }
    
    interface PlayPauseListener {
        void onPlay();
        void onPause();
    }
    
}