package com.mobeta.android.dslv;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.widget.ListView;
import android.widget.ImageView;
import android.view.View;
import android.view.ViewGroup;
import android.util.Log;

/**
 * Simple implementation of the FloatViewManager class. Uses list
 * items as they appear in the ListView to create the floating View.
 */
public class SimpleFloatViewManager implements DragSortListView.FloatViewManager {

    public int dragModeShadowImage;

    private Bitmap mFloatBitmap;

    private ImageView mImageView;

    private int mFloatBGColor = Color.BLACK;

    private ListView mListView;

    public SimpleFloatViewManager(ListView lv) {
        mListView = lv;
    }

    public void setBackgroundColor(int color) {
        mFloatBGColor = color;
    }

    /**
     * This simple implementation creates a Bitmap copy of the
     * list item currently shown at ListView <code>position</code>.
     */
    @Override
    public View onCreateFloatView(int position) {
        // Guaranteed that this will not be null? I think so. Nope, got
        // a NullPointerException once...
        View v = mListView.getChildAt(position + mListView.getHeaderViewsCount() - mListView.getFirstVisiblePosition());

        if (v == null) {
            return null;
        }

        v.setPressed(false);

        // Create a copy of the drawing cache so that it does not get
        // recycled by the framework when the list tries to clean up memory
        //v.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
        v.setDrawingCacheEnabled(true);
        mFloatBitmap = Bitmap.createBitmap(v.getDrawingCache());
        v.setDrawingCacheEnabled(false);

        if (mImageView == null) {
            mImageView = new DragModeBackgroundView(mListView.getContext());
        }
//        mImageView.setBackgroundColor(mFloatBGColor);
        mImageView.setPadding(0, 0, 0, 0);
        mImageView.setImageBitmap(mFloatBitmap);
        mImageView.setLayoutParams(new ViewGroup.LayoutParams(v.getWidth(), v.getHeight()));

        return mImageView;
    }

    protected int dpToPx(Context context, int dp) {
        final float scale = context.getResources().getDisplayMetrics().density;
        int px = (int) (dp * scale + 0.5f);
        return px;
    }

    class DragModeBackgroundView extends ImageView {

        private Paint paint;

        public DragModeBackgroundView(Context context) {
            super(context);
            paint = new Paint();
        }

        @Override
        public void draw(Canvas canvas) {
            super.draw(canvas);

            int canvasWidth = canvas.getWidth();
            int canvasHeight = canvas.getHeight();

            if (mFloatBitmap != null) {
                canvas.drawBitmap(mFloatBitmap, 0, 0, paint);

                canvasWidth = mFloatBitmap.getWidth();
                canvasHeight = mFloatBitmap.getHeight();
            }

            Context context = getContext();
            if (dragModeShadowImage > 0) {
                NinePatchDrawable npd = (NinePatchDrawable)context.getResources().getDrawable(dragModeShadowImage);
                Rect npdBounds = new Rect(0, -mListView.getDividerHeight(), canvasWidth, canvasHeight);
                npd.setBounds(npdBounds);
                npd.draw(canvas);
            }

            int marginLeft = dpToPx(context, 10);
            int marginRight = dpToPx(context, 10);
            int marginTop = dpToPx(context, 10);
            int marginBottom = dpToPx(context, 15);

            paint.setARGB(255, 62, 87, 195);

            int x = marginLeft;
            int y = marginTop;
            int len = dpToPx(context, 4);
            int width = dpToPx(context, 1);
            int sep = dpToPx(context, 1);

            // top
            while (x < canvasWidth - marginRight) {
                canvas.drawRect(x, y, Math.min(x + len, canvasWidth - marginRight), y + width, paint);
                x += len + sep;
            }

            // left
            x = marginLeft;
            y = marginTop;
            while (y < canvasHeight - marginBottom) {
                canvas.drawRect(x, y, x + width, Math.min(y + len, canvasHeight - marginBottom), paint);
                y += len + sep;
            }

            // bottom
            x = marginLeft;
            y = canvasHeight - marginBottom - width;
            while (x < canvasWidth - marginRight) {
//                Log.d("Draw", "bottom " + x + "," + y + ", " + Math.min(x + len, canvas.getWidth() - marginRight) + "," + (y + width));
                canvas.drawRect(x, y, Math.min(x + len, canvasWidth - marginRight), y + width, paint);
                x += len + sep;
            }

            // right
            x = canvasWidth - marginRight - width;
            y = marginTop;
            while (y < canvasHeight - marginBottom) {
                canvas.drawRect(x, y, x + width, Math.min(y + len, canvasHeight - marginBottom), paint);
                y += len + sep;
            }
        }
    }

    /**
     * This does nothing
     */
    @Override
    public void onDragFloatView(View floatView, Point position, Point touch) {
        // do nothing
    }

    /**
     * Removes the Bitmap from the ImageView created in
     * onCreateFloatView() and tells the system to recycle it.
     */
    @Override
    public void onDestroyFloatView(View floatView) {
        ((ImageView) floatView).setImageDrawable(null);

        mFloatBitmap.recycle();
        mFloatBitmap = null;
    }

}

