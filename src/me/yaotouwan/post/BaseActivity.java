package me.yaotouwan.post;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.*;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.RelativeLayout;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import me.yaotouwan.R;
import me.yaotouwan.util.YTWHelper;

/**
 * Created by jason on 14-3-18.
 */
public class BaseActivity extends SherlockActivity {

    protected int defaultAnimationDuration = 200;

    protected boolean isSoftKeyboardShown;

    protected int menuResId;
    protected Menu actionsMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitleColor(Color.WHITE);

        logd("Activity.onCreate with intent -> " + getIntent());
    }

    protected void setupActionBar(int title) {
        ActionBar bar = getActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setDisplayShowHomeEnabled(false);
            bar.setTitle(title);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(com.actionbarsherlock.view.Menu menu) {
        if (menuResId > 0) {
            MenuInflater inflater = getSupportMenuInflater();
            inflater.inflate(menuResId, menu);
        }
        actionsMenu = menu;
        return super.onCreateOptionsMenu(menu);
    }

    protected ViewGroup getRootViewGroup() {
        return (ViewGroup) findViewById(R.id.root_layout);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        logd("onActivityResult " + requestCode + " resultCode " + resultCode + " intent " + data);
    }

    public void logi(String msg) {
        Log.i("Yaotouwan_" + this.getLocalClassName().toString(), msg);
    }

    public void logd(String msg) {
        Log.d("Yaotouwan_" + this.getLocalClassName().toString(), msg);
    }

    public void loge(String msg) {
        Log.e("Yaotouwan_" + this.getLocalClassName().toString(), msg);
    }

    public void logw(String msg) {
        Log.w("Yaotouwan_" + this.getLocalClassName().toString(), msg);
    }

    public void pushActivity(Class<? extends Activity> activityClass) {
        Intent intent = new Intent(this, activityClass);
        startActivity(intent);
    }

    public void pushActivity(Class<? extends  Activity> activityClass, Uri data) {
        Intent intent = new Intent(this, activityClass);
        intent.setData(data);
        startActivity(intent);
    }

    public void hideSoftKeyboard() {
        View curFocusView = getCurrentFocus();
        if (curFocusView != null) {
            InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(curFocusView.getWindowToken(), 0);
        }
    }

    public void showSoftKeyboard(boolean force) {
        View curFocusView = getCurrentFocus();
        if (curFocusView != null) {
            InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (force)
                inputManager.showSoftInput(curFocusView, InputMethodManager.SHOW_FORCED);
            else
                inputManager.showSoftInput(curFocusView, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public void hideActionBar() {
        if (getActionBar() != null) {
            getActionBar().hide();
        }
    }

    protected boolean isActionBarShown() {
        if (getActionBar() != null) {
            return getActionBar().isShowing();
        }
        return false;
    }

    protected void toggleActionBar() {
        if (getActionBar() != null) {
            if (isActionBarShown()) {
                getActionBar().hide();
            } else {
                getActionBar().show();
            }
        }
    }

    public void showActionBar() {
        if (getActionBar() != null) {
            getActionBar().show();
        }
    }

    protected boolean isFullscreen() {
        return (getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) > 0;
    }

    protected void toggleFullscreen() {
        if (isFullscreen()) {
            exitFullscreen();
        } else {
            enterFullscreen();
        }
    }

    public void enterFullscreen() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    public void exitFullscreen() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
    }

    protected void hideSystemBar() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            findViewById(R.id.root_layout).setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            findViewById(R.id.root_layout).setSystemUiVisibility(View.STATUS_BAR_HIDDEN);
    }

    public int dpToPx(int dp) {
        return YTWHelper.dpToPx(this, dp);
    }

    public int pxToDp(int px) {
        final float scale = getResources().getDisplayMetrics().density;
        int dp = (int) (px / scale + 0.5f);
        return dp;
    }

    public float screenScale() {
        return getResources().getDisplayMetrics().density;
    }

    public void setViewSize(View view, int width, int height) {
        ViewGroup.LayoutParams p = view.getLayoutParams();
        if (p != null) {
            p.width = width;
            p.height = height;
        }
        view.setLayoutParams(p);
    }

    public void setViewWidth(View view, int width) {
        ViewGroup.LayoutParams p = view.getLayoutParams();
        if (p != null) {
            p.width = width;
        }
        view.setLayoutParams(p);
    }

    public void setViewHeight(View view, int height) {
        ViewGroup.LayoutParams p = view.getLayoutParams();
        if (p != null) {
            p.height = height;
        }
        view.setLayoutParams(p);
    }

    protected void showView(View view) {
        view.setVisibility(View.VISIBLE);
    }

    protected void showView(int id) {
        showView(findViewById(id));
    }

    protected void hideView(View view) {
        view.setVisibility(View.GONE);
    }

    protected void hideView(int id) {
        hideView(findViewById(id));
    }

    protected void animateToShow(int id) {
        animateToShow(findViewById(id));
    }

    protected void animateToShow(View view) {
        view.setVisibility(View.VISIBLE);
        AlphaAnimation alphaUp = new AlphaAnimation(0, 0);
        alphaUp.setFillAfter(true);
        view.startAnimation(alphaUp);

        Animation mAnimation = new AlphaAnimation(0, 1);
        mAnimation.setDuration(defaultAnimationDuration);
        mAnimation.setInterpolator(new LinearInterpolator());
        view.startAnimation(mAnimation);
    }

    protected void animateToShowIfNeed(final View view) {
        if (view.getVisibility() == View.GONE) {
            animateToShow(view);
        }
    }

    protected void animateToShowIfNeed(int id) {
        animateToShowIfNeed(findViewById(id));
    }

    protected void animateToHide(int id) {
        animateToHide(findViewById(id));
    }

    protected void animateToHide(final View view) {
        view.setVisibility(View.VISIBLE);

        AlphaAnimation alphaUp = new AlphaAnimation(1, 1);
        alphaUp.setFillAfter(true);
        view.startAnimation(alphaUp);

        Animation mAnimation = new AlphaAnimation(1, 0);
        mAnimation.setDuration(defaultAnimationDuration);
        mAnimation.setInterpolator(new LinearInterpolator());
        mAnimation.setAnimationListener(new Animation.AnimationListener() {
            public void onAnimationStart(Animation animation) {}
            public void onAnimationRepeat(Animation animation) {}
            public void onAnimationEnd(Animation animation) {
                view.setVisibility(View.GONE);
            }
        });
        view.startAnimation(mAnimation);
    }

    protected void animateToHideIfNeed(final View view) {
        if (view.getVisibility() == View.VISIBLE) {
            animateToHide(view);
        }
    }

    protected void animateToHideIfNeed(int id) {
        animateToHideIfNeed(findViewById(id));
    }

    public void aniamteToTranslate(final View view, final int fromX, final int toX, final int fromY, final int toY) {
        TranslateAnimation prepareyAnimation = new TranslateAnimation(fromX, fromX, fromY, fromY);
        prepareyAnimation.setFillAfter(true);
        view.startAnimation(prepareyAnimation);

        TranslateAnimation mAnimation = new TranslateAnimation(fromX, toX, fromY, toY);
        mAnimation.setDuration(defaultAnimationDuration);
        mAnimation.setFillAfter(true);
        mAnimation.setInterpolator(new LinearInterpolator());
        view.startAnimation(mAnimation);
    }

    protected void toggleEnable(int id) {
        View v = findViewById(id);
        if (v == null) return;
        v.setEnabled(!v.isEnabled());
    }

    protected void toggleVisible(int id) {
        toggleVisible(id, false);
    }

    protected void toggleVisible(int id, boolean invisible) {
        View v = findViewById(id);
        if (v == null) return;
        if (v.getVisibility() == View.VISIBLE) {
            if (invisible)
                v.setVisibility(View.INVISIBLE);
            else
                v.setVisibility(View.GONE);
        } else {
            v.setVisibility(View.VISIBLE);
        }
    }

    protected void animateToggleVisible(int id) {
        View v = findViewById(id);
        if (v == null) return;
        if (v.getVisibility() == View.VISIBLE) {
            animateToHide(id);
        } else {
            animateToShow(id);
        }
    }

    protected String getText(EditText editor) {
        if (editor == null) return null;
        if (editor.getText() == null) return null;
        return editor.getText().toString();
    }

    protected void onKeyboardShow() {
        isSoftKeyboardShown = true;
    }

    protected void onKeyboardHide() {
        isSoftKeyboardShown = false;
    }

    protected void listenKeyboard() {
        final View activityRootView = findViewById(R.id.root_layout);
        if (activityRootView != null) {
            ViewTreeObserver observer = activityRootView.getViewTreeObserver();
            if (observer != null) {
                observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    int lastHeight;
                    int lastWindowHeight;

                    @Override
                    public void onGlobalLayout() {
                        View rootView = activityRootView.getRootView();
                        if (rootView != null) {
                            int height = activityRootView.getHeight();
                            int windowHeight = getWindowManager().getDefaultDisplay().getHeight();
                            if (lastHeight > 0) {
                                if (height < lastHeight) { // activity height smaller
                                    if (windowHeight == lastWindowHeight) { // window height not changed
                                        // that must be the soft keyboard cause it
                                        // keyboard shown
                                        onKeyboardShow();
                                    }
                                } else if (height > lastHeight) {
                                    if (windowHeight == lastWindowHeight) {
                                        onKeyboardHide();
                                    }
                                }
                            }

                            lastHeight = height;
                            lastWindowHeight = windowHeight;
                        }
                    }
                });
            }
        }
    }

    protected void setRootBackground(int colorID) {
        View rootLayout = findViewById(R.id.root_layout);
        if (rootLayout != null) {
            View rootView = rootLayout.getRootView();
            if (rootView != null) {
                rootView.setBackgroundColor(colorID);
            }
        }
    } 
    protected int marginLeft(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) {
            if (params instanceof RelativeLayout.LayoutParams) {
                RelativeLayout.LayoutParams rParms = (RelativeLayout.LayoutParams) params;
                return rParms.leftMargin;
            }
        }
        return 0;
    }
    protected int marginRight(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) {
            if (params instanceof RelativeLayout.LayoutParams) {
                RelativeLayout.LayoutParams rParms = (RelativeLayout.LayoutParams) params;
                return rParms.rightMargin;
            }
        }
        return 0;
    }
    protected int marginTop(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) {
            if (params instanceof RelativeLayout.LayoutParams) {
                RelativeLayout.LayoutParams rParms = (RelativeLayout.LayoutParams) params;
                return rParms.topMargin;
            }
        }
        return 0;
    }
    protected int marginBottom(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) {
            if (params instanceof RelativeLayout.LayoutParams) {
                RelativeLayout.LayoutParams rParms = (RelativeLayout.LayoutParams) params;
                return rParms.bottomMargin;
            }
        }
        return 0;
    }

    protected void onViewSizeChanged() {

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        final ViewTreeObserver observer = getRootViewGroup().getViewTreeObserver();
        assert observer != null;
        observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                onViewSizeChanged();
                observer.removeGlobalOnLayoutListener(this);
            }
        });
    }

    protected void doTaskOnMainThread(Runnable runnable) {
        new Handler(getMainLooper()).post(runnable);
    }

    protected int getScreenOrientation() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int width = dm.widthPixels;
        int height = dm.heightPixels;
        int orientation;
        // if the device's natural orientation is portrait:
        if ((rotation == Surface.ROTATION_0
                || rotation == Surface.ROTATION_180) && height > width ||
                (rotation == Surface.ROTATION_90
                        || rotation == Surface.ROTATION_270) && width > height) {
            switch(rotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_180:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                case Surface.ROTATION_270:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                default:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
            }
        }
        // if the device's natural orientation is landscape or if the device
        // is square:
        else {
            switch(rotation) {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_180:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                case Surface.ROTATION_270:
                    orientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                default:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
            }
        }

        return orientation;
    }

    public static Point getWindowSize(Activity activity) {
        int width = activity.getWindowManager().getDefaultDisplay().getWidth();
        int height = activity.getWindowManager().getDefaultDisplay().getHeight();
        return new Point(width, height);
    }
}