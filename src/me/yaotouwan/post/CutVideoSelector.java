package me.yaotouwan.post;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import me.yaotouwan.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jason on 14-4-22.
 */
public class CutVideoSelector extends RelativeLayout {
    public CutVideoSelector(Context context) {
        super(context);
        initLayout();
    }

    public CutVideoSelector(Context context, AttributeSet attrs) {
        super(context, attrs);
        initLayout();
    }

    public CutVideoSelector(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initLayout();
    }

    private void initLayout() {
        if (slicesGroup != null) {
            return;
        }

        assert getResources() != null;

        float screenScale = getResources().getDisplayMetrics().density;
        borderWidth = (int) (BORDER_WIDTH * screenScale);

        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        slicesGroup = new LinearLayout(getContext());
        slicesGroup.setLayoutParams(params);
        slicesGroup.setBackgroundColor(Color.parseColor("#00000000"));
        slicesGroup.setPadding(borderWidth, 0, borderWidth, 0);
        addView(slicesGroup);

        leftUnselectedOverlay = new View(getContext());
        params = new LayoutParams(0, 0);
        params.leftMargin = borderWidth;
        params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        leftUnselectedOverlay.setLayoutParams(params);
        leftUnselectedOverlay.setBackgroundColor(Color.parseColor("#90000000"));
        addView(leftUnselectedOverlay);

        border = new ImageView(getContext());
        border.setImageResource(R.drawable.cut_video_selector_border);
        params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        border.setLayoutParams(params);
        border.setScaleType(ImageView.ScaleType.FIT_XY);
        border.setBackgroundColor(Color.parseColor("#00000000"));
        addView(border);

        rightUnselectedOverlay = new View(getContext());
        params = new LayoutParams(0, 0);
        params.rightMargin = borderWidth;
        params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        rightUnselectedOverlay.setLayoutParams(params);
        rightUnselectedOverlay.setBackgroundColor(Color.parseColor("#90000000"));
        addView(rightUnselectedOverlay);

        handle = new ImageView(getContext());
        handle.setImageResource(R.drawable.cut_video_selector_handle);
        params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        handle.setLayoutParams(params);
        handle.setScaleType(ImageView.ScaleType.FIT_XY);
        handle.setBackgroundColor(Color.parseColor("#00000000"));
        addView(handle);

        sliceViews = new ArrayList<ImageView>();
    }

    private LinearLayout slicesGroup;
    private ImageView border;
    private ImageView handle;
    private View leftUnselectedOverlay;
    private View rightUnselectedOverlay;
    private int borderWidth;

    private List<ImageView> sliceViews;

    public int minSliceCount = 10;

    public void addPreview(Bitmap bitmap) {
        ImageView newSliceView = new ImageView(getContext());
        newSliceView.setImageBitmap(bitmap);
        sliceViews.add(newSliceView);
        slicesGroup.addView(newSliceView);

        reLayout();

        int sliceWidth = getWidth() / Math.max(sliceViews.size(), minSliceCount);
        int left = sliceWidth * sliceViews.size() - sliceWidth;
        int transX = slicesGroup.getWidth() - left;

        TranslateAnimation prepareyAnimation = new TranslateAnimation(transX, transX, 0, 0);
        prepareyAnimation.setFillAfter(true);
        newSliceView.startAnimation(prepareyAnimation);

        TranslateAnimation mAnimation = new TranslateAnimation(transX, 0, 0, 0);
        mAnimation.setDuration(1000 / minSliceCount);
        mAnimation.setFillAfter(true);
        mAnimation.setInterpolator(new LinearInterpolator());
        newSliceView.startAnimation(mAnimation);
    }

    public void reLayout() {
        int sliceWidth = getWidth() / Math.max(sliceViews.size(), minSliceCount);
        int sliceHeight = getHeight();

        for (ImageView sliceView : sliceViews) {
            LinearLayout.LayoutParams sliceParams =
                    new LinearLayout.LayoutParams(sliceWidth, sliceHeight);
            sliceView.setLayoutParams(sliceParams);
            sliceView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        }
    }

    private boolean draggingHandle;
    private boolean draggingStartProgress;
    private boolean draggingEndProgress;

//    private int draggingStartX;
    private int draggingLastTimeX;

    private static final int BORDER_WIDTH = 12;
    private static final int HANDLE_WIDTH = 9;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
//        Log.d("Cut", event.toString());

        assert getResources() != null;

        float screenScale = getResources().getDisplayMetrics().density;

        int x = (int) event.getX();
        LayoutParams borderLayoutParams = (LayoutParams) border.getLayoutParams();
        assert borderLayoutParams != null;

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (x < borderWidth + borderLayoutParams.leftMargin
                && x > borderLayoutParams.leftMargin) {
                draggingStartProgress = true;
                draggingHandle = false;
                draggingEndProgress = false;
                border.setImageResource(R.drawable.cut_video_selector_border_selected);
                handle.setVisibility(INVISIBLE);
            } else if (x > getWidth() - borderWidth - borderLayoutParams.rightMargin
                       && x < getWidth() - borderLayoutParams.rightMargin) {
                draggingStartProgress = false;
                draggingHandle = false;
                draggingEndProgress = true;
                border.setImageResource(R.drawable.cut_video_selector_border_selected);
                handle.setVisibility(INVISIBLE);
            } else {
                LayoutParams handleParams = (LayoutParams) handle.getLayoutParams();
                assert handleParams != null;
                if (handle.getVisibility() == VISIBLE
                    && x > handleParams.leftMargin + borderWidth
                    && x < handleParams.leftMargin + borderWidth + HANDLE_WIDTH * screenScale) {

                    draggingStartProgress = false;
                    draggingHandle = true;
                    draggingEndProgress = false;
                }
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (draggingStartProgress) {
                int marginLeft = borderLayoutParams.leftMargin + x - draggingLastTimeX;
                if (marginLeft < 0)
                    marginLeft = 0;

                double previewProgress = marginLeft * 1.0 / (getWidth() - 2 * borderWidth);

                boolean overLine = minInterval > 0 && endProgress() - previewProgress < minInterval;
                if (overLine) {
                    previewProgress = endProgress() - minInterval;
                    marginLeft = (int) (previewProgress * (getWidth() - 2 * borderWidth));
                }

                borderLayoutParams.setMargins(marginLeft, 0, borderLayoutParams.rightMargin, 0);
                border.setLayoutParams(borderLayoutParams);

                LayoutParams leftOverlayParams = (LayoutParams) leftUnselectedOverlay.getLayoutParams();
                assert leftOverlayParams != null;
                leftOverlayParams.width = marginLeft - borderWidth;
                leftOverlayParams.height = getHeight();
                leftUnselectedOverlay.setLayoutParams(leftOverlayParams);

                if (onValueChangedListener != null) {
                    if (previewProgress > 0 && previewProgress < 1) {
                        onValueChangedListener.onChangePreviewProgress(previewProgress);
                    }
                }

                if (overLine) {
                    return false;
                }
            } else if (draggingHandle) {
                LayoutParams handleLayoutParams = (LayoutParams) handle.getLayoutParams();
                assert handleLayoutParams != null;
                int marginLeft = handleLayoutParams.leftMargin + x - draggingLastTimeX;
                if (marginLeft < 0)
                    marginLeft = 0;
                handleLayoutParams.setMargins(marginLeft, 0, 0, 0);
                handle.setLayoutParams(handleLayoutParams);

                if (onValueChangedListener != null) {
                    double previewProgress = (marginLeft * 1.0 + handle.getWidth() / 2 - borderWidth) / (getWidth() - 2 * borderWidth);
                    if (previewProgress > 0 && previewProgress < 1) {
                        onValueChangedListener.onChangePreviewProgress(previewProgress);
                    }
                }
            } else if (draggingEndProgress) {
                int marginRight = borderLayoutParams.rightMargin + draggingLastTimeX - x;
                if (marginRight < 0)
                    marginRight = 0;

                double previewProgress = 1 - marginRight * 1.0 / (getWidth() - 2 * borderWidth);
                boolean overLine = minInterval > 0 && previewProgress - startProgress() < minInterval;
                if (overLine) {
                    previewProgress = minInterval + startProgress();
                    marginRight = (int) ((1 - previewProgress) * (getWidth() - 2 * borderWidth));
                }

                borderLayoutParams.setMargins(borderLayoutParams.leftMargin, 0, marginRight, 0);
                border.setLayoutParams(borderLayoutParams);

                LayoutParams rightOverlayParams = (LayoutParams) rightUnselectedOverlay.getLayoutParams();
                assert rightOverlayParams != null;
                rightOverlayParams.width = marginRight - borderWidth;
                rightOverlayParams.height = getHeight();
                rightUnselectedOverlay.setLayoutParams(rightOverlayParams);

                if (onValueChangedListener != null) {
                    if (previewProgress > 0 && previewProgress < 1) {
                        onValueChangedListener.onChangePreviewProgress(previewProgress);
                    }
                }

                if (overLine) {
                    return false;
                }
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP
                || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (draggingStartProgress) {
                onValueChangedListener.onStopChangingStartProgress();
            } else if (draggingHandle) {
                onValueChangedListener.onStopChangingPreviewProgress();
            } else if (draggingEndProgress) {
                onValueChangedListener.onStopChangingEndProgress();
            }
            draggingStartProgress = false;
            draggingHandle = false;
            draggingEndProgress = false;
            draggingLastTimeX = 0;
            if (borderLayoutParams.leftMargin == 0 && borderLayoutParams.rightMargin == 0) {
                border.setImageResource(R.drawable.cut_video_selector_border);
                handle.setVisibility(VISIBLE);
            }
        }

        draggingLastTimeX = x;

        return true;
    }

    public double startProgress() {
        LayoutParams borderLayoutParams = (LayoutParams) border.getLayoutParams();
        assert borderLayoutParams != null;
        return borderLayoutParams.leftMargin * 1.0 / (getWidth() - 2 * borderWidth);
    }

    public double endProgress() {
        LayoutParams borderLayoutParams = (LayoutParams) border.getLayoutParams();
        assert borderLayoutParams != null;
        return 1 - borderLayoutParams.rightMargin * 1.0 / (getWidth() - 2 * borderWidth);
    }

    public OnValueChangedListener onValueChangedListener;
    public double minInterval;

    public static interface OnValueChangedListener {
        public void onChangePreviewProgress(double previewProgress);
        public void onStopChangingPreviewProgress();
        public void onStopChangingStartProgress();
        public void onStopChangingEndProgress();
    }



    public void setProgress(double progress) {
        handle.setVisibility(VISIBLE);
        LayoutParams handleLayoutParams = (LayoutParams) handle.getLayoutParams();
        assert handleLayoutParams != null;
        int marginLeft = (int) (progress * (getWidth() - 2 * borderWidth) + borderWidth - handle.getWidth() / 2);
        if (marginLeft < 0)
            marginLeft = 0;
        handleLayoutParams.setMargins(marginLeft, 0, 0, 0);
        handle.setLayoutParams(handleLayoutParams);
    }
}
