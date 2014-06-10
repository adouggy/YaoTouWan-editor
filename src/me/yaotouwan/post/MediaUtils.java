package me.yaotouwan.post;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import me.yaotouwan.util.YTWHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by jason on 14-6-10.
 */
public class MediaUtils {

    public static String cachePathForVideoThumbnail(Context context, String srcPath, int sizeKind) {
        File photoCacheDir = new File(context.getCacheDir(), "select_photo");
        File cacheFile = new File(photoCacheDir, YTWHelper.md5(srcPath) + "_" + sizeKind);
        if (cacheFile.exists()) {
            return cacheFile.getAbsolutePath();
        } else {
            loadVideoThumbnail(context, srcPath, sizeKind);
            if (cacheFile.exists()) {
                return cacheFile.getAbsolutePath();
            }
            return null;
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
}
