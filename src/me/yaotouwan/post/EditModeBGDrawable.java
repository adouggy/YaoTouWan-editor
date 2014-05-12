package me.yaotouwan.post;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.util.Log;
import me.yaotouwan.util.YTWHelper;

/**
 * Created by jason on 14-4-29.
 */
public class EditModeBGDrawable extends Drawable {

    private int bgBitmapID;
    private Context context;
    private Paint paint;

    public EditModeBGDrawable(Context context, int bgBitmapID) {
        super();

        this.bgBitmapID = bgBitmapID;
        this.context = context;
        paint = new Paint();
    }

    @Override
    public void draw(Canvas canvas) {
        if (bgBitmapID > 0) {
            NinePatchDrawable npd = (NinePatchDrawable)context.getResources().getDrawable(bgBitmapID);
            Rect npdBounds = new Rect(0, 0, canvas.getWidth(), canvas.getHeight());
            npd.setBounds(npdBounds);
            npd.draw(canvas);
        }

        int marginLeft = YTWHelper.dpToPx(context, 0);
        int marginRight = YTWHelper.dpToPx(context, 0);
        int marginTop = YTWHelper.dpToPx(context, 10);
        int marginBottom = YTWHelper.dpToPx(context, 15);

        paint.setARGB(255, 62, 87, 195);

        int x = marginLeft;
        int y = marginTop;
        int len = YTWHelper.dpToPx(context, 4);
        int width = YTWHelper.dpToPx(context, 1);
        int sep = YTWHelper.dpToPx(context, 1);
        while (x + len < canvas.getWidth() - marginRight) {
            canvas.drawRect(x, y, x + len, y + width, paint);
            x += len + sep;
        }

        x = marginLeft;
        y = marginTop;
        while (y < canvas.getHeight() - marginBottom) {
            canvas.drawRect(x, y, x + width, y + len, paint);
            y += len + sep;
        }

        x = marginLeft;
        y = canvas.getHeight() - marginBottom - width;
        while (x < canvas.getWidth() - marginRight) {
            canvas.drawRect(x, y, x + len, y + width, paint);
            x += len + sep;
        }

        x = canvas.getWidth() - marginRight - width;
        y = marginTop;
        while (y < canvas.getHeight() - marginBottom) {
            canvas.drawRect(x, y, x + width, y + len, paint);
            y += len + sep;
        }
    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(ColorFilter cf) {

    }

    @Override
    public int getOpacity() {
        return 0;
    }
}
