package me.yaotouwan.post;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import me.yaotouwan.util.YTWHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Timer;

/**
 * Created by jason on 14-5-7.
 */
public class CachedImageButton extends ImageButton {
    public CachedImageButton(Context context) {
        super(context);
    }

    public CachedImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CachedImageButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public static final int DEFAULT_DELAY = 500;
    private String imagePath;
    public void setImageWithPath(final String newImagePath, final int width, boolean async, int delay) {
        if (videoPath == null && newImagePath.equals(imagePath))
            return;
        videoPath = null;
        if (newImagePath.equals(imagePath)) return;
        imagePath = newImagePath;
        setImageBitmap(null);
        if (async) {
            final String oldImagePath = newImagePath;
            if (delay > 0) {
                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (oldImagePath.equals(imagePath)) {
                            new LoadBitmapTask().execute(width);
                        }
                    }
                }, delay);
            } else {
                post(new Runnable() {
                    @Override
                    public void run() {
                        if (oldImagePath.equals(imagePath)) {
                            new LoadBitmapTask().execute(width);
                        }
                    }
                });
            }
        } else {
            setImageBitmap(loadBitmap(imagePath, width));
        }
    }

    private String videoPath;
    public void setImageWithVideoPath(final String newVideoPath, final int sizeKind, boolean async, int delay) {
        if (imagePath == null && newVideoPath.equals(videoPath))
         return;
        imagePath = null;
        videoPath = newVideoPath;
        setImageBitmap(null);
        if (async) {
            final String oldVideoPath = newVideoPath;
            if (delay > 0) {
                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (oldVideoPath.equals(videoPath)) {
                            new LoadVideoThumbnailTask().execute(sizeKind);
                        }
                    }
                }, delay);
            } else {
                post(new Runnable() {
                    @Override
                    public void run() {
                        if (oldVideoPath.equals(videoPath)) {
                            new LoadVideoThumbnailTask().execute(sizeKind);
                        }
                    }
                });
            }
        } else {
            setImageBitmap(loadVideoThumbnail(videoPath, sizeKind));
        }
    }

    class LoadBitmapTask extends AsyncTask<Integer, Integer, Bitmap> {

        private String loadedImagePath;

        @Override
        protected Bitmap doInBackground(Integer... params) {
            loadedImagePath = imagePath;
            int width = params[0];
            return loadBitmap(imagePath, width);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (imagePath != null && imagePath.equals(loadedImagePath)) {
                AlphaAnimation alphaUp = new AlphaAnimation(0, 0);
                alphaUp.setFillAfter(true);
                startAnimation(alphaUp);

                setImageBitmap(bitmap);

                Animation mAnimation = new AlphaAnimation(0, 1);
                mAnimation.setDuration(250);
                mAnimation.setInterpolator(new LinearInterpolator());
                startAnimation(mAnimation);
            } else {
                bitmap.recycle();
            }
        }
    }

    public static Bitmap loadBitmap(Context context, String srcPath, int width) {
        File photoCacheDir = new File(context.getCacheDir(), "select_photo");
        if (!photoCacheDir.exists()) {
            if (!photoCacheDir.mkdir())
                return YTWHelper.decodeBitmapFromPath(srcPath, width);
        }
        File cacheFile = new File(photoCacheDir, YTWHelper.md5(srcPath) + "_" + width);
        if (cacheFile.exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
            if (bitmap == null) {
                cacheFile.delete();
                return YTWHelper.decodeBitmapFromPath(srcPath, width);
            }
            return bitmap;
        } else {
            Bitmap bitmap = YTWHelper.decodeBitmapFromPath(srcPath, width);
            if (bitmap == null) {
                return null;
            }
            FileOutputStream cacheStream = null;
            try {
                cacheStream = new FileOutputStream(cacheFile);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, cacheStream);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } finally {
                if (cacheStream != null) {
                    try {
                        cacheStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            return bitmap;
        }
    }

    Bitmap loadBitmap(String srcPath, int width) {
        return loadBitmap(getContext(), srcPath, width);
    }

    class LoadVideoThumbnailTask extends AsyncTask<Integer, Integer, Bitmap> {

        private String loadedVideoPath;

        @Override
        protected Bitmap doInBackground(Integer... params) {
            loadedVideoPath = videoPath;
            int sizeKind = params[0];
            return loadVideoThumbnail(videoPath, sizeKind);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (videoPath.equals(loadedVideoPath)) {
                AlphaAnimation alphaUp = new AlphaAnimation(0, 0);
                alphaUp.setFillAfter(true);
                startAnimation(alphaUp);

                setImageBitmap(bitmap);

                Animation mAnimation = new AlphaAnimation(0, 1);
                mAnimation.setDuration(250);
                mAnimation.setInterpolator(new LinearInterpolator());
                startAnimation(mAnimation);
            } else {
                bitmap.recycle();
            }
        }
    }

    public static Bitmap loadVideoThumbnail(Context context, String srcPath, int sizeKind) {
        File photoCacheDir = new File(context.getCacheDir(), "select_photo");
        if (!photoCacheDir.exists()) {
            if (!photoCacheDir.mkdir())
                return ThumbnailUtils.createVideoThumbnail(srcPath, sizeKind);
        }
        File cacheFile = new File(photoCacheDir, YTWHelper.md5(srcPath) + "_" + sizeKind);
        if (cacheFile.exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
            if (bitmap == null) {
                cacheFile.delete();
                return ThumbnailUtils.createVideoThumbnail(srcPath, sizeKind);
            }
            return bitmap;
        } else {
            Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(srcPath, sizeKind);
            if (bitmap == null) {
                return null;
            }
            FileOutputStream cacheStream = null;
            try {
                cacheStream = new FileOutputStream(cacheFile);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, cacheStream);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } finally {
                if (cacheStream != null) {
                    try {
                        cacheStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            return bitmap;
        }
    }

    Bitmap loadVideoThumbnail(String srcPath, int width) {
        return loadVideoThumbnail(getContext(), srcPath, width);
    }
}
