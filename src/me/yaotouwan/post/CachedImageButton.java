package me.yaotouwan.post;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import me.yaotouwan.android.util.UniversalImageLoaderUtil;
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
    public boolean setImageWithPath(final String newImagePath, final int width, boolean async) {
        return setImageWithPath(newImagePath, width, async, 0);
    }
    public boolean setImageWithPath(final String newImagePath, final int width, boolean async, int delay) {
        if (newImagePath == null) return false;
        if (videoPath == null && newImagePath.equals(imagePath))
            return false;
        videoPath = null;
        if (newImagePath.equals(imagePath)) return false;
        imagePath = newImagePath;
        setImageBitmap(null);
        if ("http".equals(Uri.parse(imagePath).getScheme())
                || "yaotouwan".equals(Uri.parse(imagePath).getScheme())) {
            if (async) {
                UniversalImageLoaderUtil.INSTANCE.load(imagePath, this);
            } else {
                UniversalImageLoaderUtil.INSTANCE.loadNoFading(imagePath, this);
            }
        } else {
            if (async) {
                if (delay > 0) {
                    postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (newImagePath.equals(imagePath)) {
                                new LoadBitmapTask().executeOnExecutor(
                                        AsyncTask.THREAD_POOL_EXECUTOR, width);
                            } else {
                                Log.d("Cache", "ignored image");
                            }
                        }
                    }, delay);
                } else {
                    new LoadBitmapTask().executeOnExecutor(
                            AsyncTask.THREAD_POOL_EXECUTOR, width);
                }
            } else {
                setImageBitmap(YTWHelper.decodeBitmapFromPath(imagePath, width));
            }
        }
        return true;
    }

    private String videoPath;
    public boolean setImageWithVideoPath(final String newVideoPath, final int sizeKind, boolean async, int delay) {
        if (newVideoPath == null) return false;
        if (imagePath == null && newVideoPath.equals(videoPath))
         return false;
        imagePath = null;
        videoPath = newVideoPath;
        setImageBitmap(null);
        if (async) {
            if (delay > 0) {
                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (newVideoPath.equals(videoPath)) {
                            new LoadVideoThumbnailTask().executeOnExecutor(
                                    AsyncTask.THREAD_POOL_EXECUTOR, sizeKind);
                        }
                    }
                }, delay);
            } else {
                new LoadVideoThumbnailTask().executeOnExecutor(
                        AsyncTask.THREAD_POOL_EXECUTOR, sizeKind);
            }
        } else {
            setImageBitmap(loadVideoThumbnail(videoPath, sizeKind));
        }
        return true;
    }

    class LoadBitmapTask extends AsyncTask<Integer, Integer, Bitmap> {

        private String loadedImagePath;

        @Override
        protected Bitmap doInBackground(Integer... params) {
            loadedImagePath = imagePath;
            int width = params[0];
            return YTWHelper.decodeBitmapFromPath(imagePath, width);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (imagePath != null && imagePath.equals(loadedImagePath)) {
//                AlphaAnimation alphaUp = new AlphaAnimation(0, 0);
//                alphaUp.setFillAfter(true);
//                startAnimation(alphaUp);

                setImageBitmap(bitmap);

//                Animation mAnimation = new AlphaAnimation(0, 1);
//                mAnimation.setDuration(250);
//                mAnimation.setInterpolator(new LinearInterpolator());
//                startAnimation(mAnimation);
            } else if (bitmap != null) {
                bitmap.recycle();
            }
        }
    }

//    public static Bitmap loadBitmap(Context context, String srcPath, int width) {
//        File photoCacheDir = new File(context.getCacheDir(), "select_photo");
//        if (!photoCacheDir.exists()) {
//            if (!photoCacheDir.mkdir())
//                return YTWHelper.decodeBitmapFromPath(srcPath, width);
//        }
//        File cacheFile = new File(photoCacheDir, YTWHelper.md5(srcPath) + "_" + width);
//        if (cacheFile.exists()) {
//            Bitmap bitmap = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
//            if (bitmap == null) {
//                cacheFile.delete();
//                return YTWHelper.decodeBitmapFromPath(srcPath, width);
//            }
//            return bitmap;
//        } else {
//            Bitmap bitmap = YTWHelper.decodeBitmapFromPath(srcPath, width);
//            if (bitmap == null) {
//                return null;
//            }
//            FileOutputStream cacheStream = null;
//            try {
//                cacheStream = new FileOutputStream(cacheFile);
//                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, cacheStream);
//            } catch (FileNotFoundException e) {
//                e.printStackTrace();
//            } finally {
//                if (cacheStream != null) {
//                    try {
//                        cacheStream.close();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//            return bitmap;
//        }
//    }

//    Bitmap loadBitmap(String srcPath, int width) {
//        return YTWHelper.decodeBitmapFromPath(srcPath, width);
//        return loadBitmap(getContext(), srcPath, width);
//    }

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
            if (videoPath != null && videoPath.equals(loadedVideoPath)) {
//                AlphaAnimation alphaUp = new AlphaAnimation(0, 0);
//                alphaUp.setFillAfter(true);
//                startAnimation(alphaUp);

                setImageBitmap(bitmap);

//                Animation mAnimation = new AlphaAnimation(0, 1);
//                mAnimation.setDuration(250);
//                mAnimation.setInterpolator(new LinearInterpolator());
//                startAnimation(mAnimation);
            } else if (bitmap != null) {
                bitmap.recycle();
            }
        }
    }



    Bitmap loadVideoThumbnail(String srcPath, int sizeKind) {
        return MediaUtils.loadVideoThumbnail(getContext(), srcPath, sizeKind);
    }
}
