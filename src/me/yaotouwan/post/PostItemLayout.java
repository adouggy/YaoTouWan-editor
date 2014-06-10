package me.yaotouwan.post;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import com.mobeta.android.dslv.DragSortListView;
import me.yaotouwan.R;
import me.yaotouwan.util.YTWHelper;

/**
 * Created by jason on 14-4-28.
 */
public class PostItemLayout extends RelativeLayout implements DragSortListView.SubViewLocater {

    protected boolean viewDidLayout;

    public PostItemLayout(Context context) {
        super(context);
    }

    public PostItemLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PostItemLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }


    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        final View dragHandle = findViewById(R.id.drag_handle);
        if (dragHandle.getVisibility() == VISIBLE) {
            final LayoutParams p = (LayoutParams) dragHandle.getLayoutParams();
            if (p.width != getWidth() || p.height != getHeight()) {
                p.width = getWidth();
                p.height = getHeight();
                dragHandle.post(new Runnable() {
                    @Override
                    public void run() {
                        dragHandle.setLayoutParams(p);
                    }
                });
            }
        }
    }

    @Override
    public View locateViewAtPosition(int x, int y) {
        if (findViewById(R.id.drag_handle).getVisibility() != VISIBLE) {
            return null;
        }
        View imageView = findViewById(R.id.post_item_preview);
        if (imageView != null && imageView.getVisibility() == VISIBLE) {
            int[] loc = new int[2];
            imageView.getLocationOnScreen(loc);
            Log.d("Post", "" + loc[1] + ", " + imageView.getHeight() + ", " + y);
            if ((loc[1] + imageView.getHeight()) > y) {
                Log.d("Post", "locate at image view");
                return imageView;
            }
        }
        Log.d("Post", "locate at text editor");
        return findViewById(R.id.post_item_text);
    }
}
