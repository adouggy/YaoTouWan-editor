package me.yaotouwan.post;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.ImageButton;

/**
 * Created by jason on 14-4-15.
 */
public class PreviewImageButton extends CachedImageButton {
    public PreviewImageButton(Context context) {
        super(context);
    }

    public PreviewImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PreviewImageButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return isEnabled() && super.dispatchTouchEvent(event);
    }
}
