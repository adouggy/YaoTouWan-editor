package me.yaotouwan.post;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;

/**
 * Created by jason on 14-5-26.
 */
public class UIButton extends ImageButton {
    public UIButton(Context context) {
        super(context);
    }

    public UIButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public UIButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public static final int STATE_NORMAL = 1;
    public static final int STATE_PRESSED = 1;

    private Object srcImage;
    private Object srcImagePressed;
    public void setImageForState(Bitmap bitmap, int state) {

    }
    public void setImageForState(Drawable drawable, int state) {

    }
    public void setImageForState(int resourceId, int state) {

    }

    private Object bgImage;
    private Object bgImagePressed;
    public void setBackgroundImageForState(Bitmap bitmap, int state) {

    }
    public void setBackgroundImageForState(Drawable drawable, int state) {

    }
    public void setBackgroundImageForState(int resourceId, int state) {

    }

    private OnTouchListener customTouchListener;
    private OnTouchListener onTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (srcImagePressed != null) {
                    if (srcImagePressed instanceof Bitmap) {
                        setImageBitmap((Bitmap) srcImagePressed);
                    } else if (srcImagePressed instanceof Drawable) {
                        setImageDrawable((Drawable) srcImagePressed);
                    } else if (srcImagePressed instanceof Integer) {
                        setImageResource((Integer) srcImagePressed);
                    }
                }
                if (bgImagePressed != null) {
                    if (bgImagePressed instanceof Bitmap) {
                        setImageBitmap((Bitmap) bgImagePressed);
                    } else if (bgImagePressed instanceof Drawable) {
                        setImageDrawable((Drawable) bgImagePressed);
                    } else if (bgImagePressed instanceof Integer) {
                        setImageResource((Integer) bgImagePressed);
                    }
                }
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL
                    || event.getAction() == MotionEvent.ACTION_UP) {

            }
            if (customTouchListener != null) {
                customTouchListener.onTouch(v, event);
            }
            return true;
        }
    };

    @Override
    public void setOnTouchListener(OnTouchListener onTouchListener) {
        this.customTouchListener = onTouchListener;
    }
}
