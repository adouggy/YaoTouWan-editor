package me.yaotouwan.post;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.TextView;

/**
 * Created by jason on 14-6-2.
 */
public class ReadText extends TextView {
    public ReadText(Context context) {
        super(context);
    }

    public ReadText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ReadText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return isEnabled() && super.dispatchTouchEvent(event);
    }
}
