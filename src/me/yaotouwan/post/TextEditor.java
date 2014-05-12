package me.yaotouwan.post;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.EditText;

/**
 * Created by jason on 14-4-14.
 */
public class TextEditor extends EditText {

    public TextEditor(Context ctx) {
        super(ctx);
    }

    public TextEditor(Context ctx, AttributeSet attributeSet) {
        super(ctx, attributeSet);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return isEnabled() && focusable && super.dispatchTouchEvent(event);
    }

    private boolean focusable = true;
    public void setFocuable(boolean focuable) {
        this.focusable = focuable;
    }
}
