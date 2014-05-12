package me.yaotouwan.util;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.TranslateAnimation;
import me.yaotouwan.R;
import me.yaotouwan.screenrecorder.Root;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by jason on 14-3-24.
 */
public class YTWHelper {

    public static boolean runRootCommand(String command) {
        Process process = null;
        DataOutputStream os = null;
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
            Log.d("Helper", "su process completed");
        } catch (Exception e) {
            return false;
        } finally {
            try {
                if (os != null) {
                    os.close();
                }
                process.destroy();
            } catch (Exception e) {
            }
        }
        return true;
    }

    public static boolean checkFBPermission() {
        if (new Root().isDeviceRooted()) {
            try {
                File fbFile = new File("/dev/graphics/fb0");
                if (!fbFile.canRead() || !fbFile.canWrite()) {
                    return runRootCommand("chmod 666 /dev/graphics/fb0");
                } else {
                    return true;
                }
            } catch (Exception exception) {
                return false;
            }
        } else {
            return false;
        }
    }

    public static boolean hasBuildinScreenRecorder() {
        return new File("/system/bin/screenrecord").exists();
    }

    public static String screenrecordScriptPath() {
        return new File(dataRootDirectory(0), "screenrecord.sh").getAbsolutePath();
    }

    public static String dataRootDirectory(int extNum) {
        File sdcard = Environment.getExternalStorageDirectory();
        if (!sdcard.exists()) {
            sdcard = new File("/sdcard");
        }
        if (sdcard.exists()) {
            File rd = new File("/sdcard/YaoTouWan");
            if (extNum > 0) {
                rd = new File(rd.getAbsolutePath() + extNum);
            }
            if (!rd.exists()) {
                if (rd.mkdirs()) {
                    return rd.getAbsolutePath();
                }
            } else if (rd.isDirectory()) {
                return rd.getAbsolutePath();
            } else {
                return dataRootDirectory(extNum+1);
            }
        } else {
            // todo no /sdcard/
            return null;
        }
        return sdcard.getAbsolutePath();
    }

    public static String videoDirectory() {
        return dataRootDirectory(0);
//        String rd = dataRootDirectory(0);
//        if (rd != null) {
//            File vd = new File(rd + "/video");
//            if (!vd.exists()) {
//                vd.mkdirs();
//            }
//            return vd.getAbsolutePath();
//        }
//        return null;
    }

    public static String picturesDirectory() {
        return dataRootDirectory(0);
//        String rd = dataRootDirectory(0);
//        if (rd != null) {
//            File vd = new File(rd + "/pictures");
//            if (!vd.exists()) {
//                vd.mkdirs();
//            }
//            return vd.getAbsolutePath();
//        }
//        return null;
    }

    public static String generateRandomFilename(String ext) {
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS");
        return df.format(new Date()) + "." + ext;
    }

    public static String prepareFilePathForImageSave()  {
        String dirPath = YTWHelper.picturesDirectory();
        if (dirPath == null) return null;
        return new File(dirPath, generateRandomFilename("jpg")).getAbsolutePath();
    }

    public static String prepareFilePathForVideoSave()  {
        String dirPath = YTWHelper.videoDirectory();
        if (dirPath == null) return null;
        return new File(dirPath, generateRandomFilename("mp4")).getAbsolutePath();
    }

    public static String getRealPathFromURI(Uri content_uri, ContentResolver contentResolver) {
        Cursor cursor = null;
        try {
            String column = MediaStore.Images.ImageColumns.DATA;
            String [] proj = { column };
            cursor = contentResolver.query(content_uri,
                    proj, // Which columns to return
                    null, // WHERE clause; which rows to return (all rows)
                    null, // WHERE clause selection arguments (none)
                    null); // Order-by clause (ascending by name)
            int column_index = cursor.getColumnIndexOrThrow(column);
            if (!cursor.moveToFirst())
                return null;
            return cursor.getString(column_index);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public static String getCameraRootPath(ContentResolver contentResolver) {
        if (new File("/sdcard/DCIM/Camera").exists())
            return "/sdcard/DCIM/Camera";

        String demoImagePath = getRealPathFromURI(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentResolver);
        File parent = new File(demoImagePath).getParentFile();
        if (!parent.exists()) {
            parent = new File(correctFilePath(parent.getAbsolutePath()));
            if (!parent.exists()) {
                if (parent.mkdirs()) {
                    return parent.getAbsolutePath();
                }
            }
        }
        return parent.getAbsolutePath();
    }

    public static String prepareImagePathForCamera(ContentResolver contentResolver) {
        String dirPath = YTWHelper.getCameraRootPath(contentResolver);
        if (dirPath == null) return null;
        return new File(dirPath, generateRandomFilename("jpg")).getAbsolutePath();
    }

    public static String prepareVideoPathForCamera(ContentResolver contentResolver) {
        String dirPath = YTWHelper.getCameraRootPath(contentResolver);
        if (dirPath == null) return null;
        return new File(dirPath, generateRandomFilename("mp4")).getAbsolutePath();
    }

    public static String correctFilePath(String path) {
        if (!new File(path).exists()) {
            // todo 目錄矯正需要更通用一些
            return path.replace("/storage/emulated/0/", "/sdcard/");
        }
        return path;
    }

    public static void alert(Context context, String message) {
        AlertDialog.Builder db = new AlertDialog.Builder(context);
        db.setTitle(message);
        db.setNegativeButton(R.string.confirm, null);
        db.show();
    }

    public static AlertDialog confirm(final Context context,
                                      final String title,
                                      final DialogInterface.OnClickListener task) {
        AlertDialog.Builder db = new AlertDialog.Builder(context);
        db.setTitle(title);
        db.setNegativeButton(R.string.confirm, task);
        db.setPositiveButton(R.string.cancel, null);
        return db.show();
    }

    // return true if alerted
    public static boolean alertWithNeverAgain(final Context context,
                                           final String propertyName,
                                           final String title,
                                           final DialogInterface.OnClickListener task) {
        SharedPreferences preferences = context.getSharedPreferences("YTWPreferences", Context.MODE_PRIVATE);
        if (!preferences.getBoolean(propertyName, false)) {
            AlertDialog.Builder db = new AlertDialog.Builder(context);
            db.setTitle(title);
            db.setNegativeButton(R.string.confirm, task);
            db.setPositiveButton(R.string.never_alert_again,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            SharedPreferences preferences = context.getSharedPreferences("YTWPreferences", Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putBoolean(propertyName, true);
                            editor.commit();

                            task.onClick(dialog, which);
                        }
                    });
            db.show();
            return true;
        } else {
            task.onClick(null, 0);
            return false;
        }
    }

    public static boolean isLowMemory(Context context) {
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) context.getSystemService(context.ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(memoryInfo);
        return memoryInfo.lowMemory;
    }

    public static int findRecorderPid(String pname) {
        BufferedReader pbr = null;
        int pid = 0;
        try {
            Process p = Runtime.getRuntime().exec("ps | grep " + pname);
            pbr = StreamHelper.reader(p.getInputStream());
            while (true) {
                String line = pbr.readLine();
                if (line == null) return 0;
                if (line.startsWith("USER")) continue;
                Log.d(YTWHelper.class.getSimpleName(), line);
                String[] parts = line.split("\\s+");
                if (parts.length > 1) {
                    String pidStr = parts[1];
                    try {
                        pid = Integer.parseInt(pidStr);
                        if (pid > 0) {
                            Log.d(YTWHelper.class.getSimpleName(), "found ss pid " + pid);
                            break;
                        }
                    } catch (NumberFormatException e) {
                        continue;
                    }
                }
            }
            p.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            if (pbr != null) {
                try {
                    pbr.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return pid;
    }

    public static boolean isBuildinScreenRecorderRunning() {
        return findRecorderPid("screenrecord") > 0;
    }

    public static void saveProperty(final Context context,
                             String propertyName,
                             boolean propertyValue) {
        SharedPreferences preferences =
                context.getSharedPreferences("YTWPreferences", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(propertyName, propertyValue);
        editor.commit();
    }

    public static boolean getBooleanProperty(final Context context,
                                      String propertyName) {
        SharedPreferences preferences =
                context.getSharedPreferences("YTWPreferences", Context.MODE_PRIVATE);
        return preferences.getBoolean(propertyName, false);
    }

    public static String saveImageAsCopy(String srcPath) {
        InputStream in = null;
        OutputStream out = null;
        try {
            String dstPath = YTWHelper.prepareFilePathForImageSave();
            in = new FileInputStream(srcPath);
            out = new FileOutputStream(dstPath);

            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            return dstPath;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (in != null)
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public static int dpToPx(Context context, int dp) {
        final float scale = context.getResources().getDisplayMetrics().density;
        int px = (int) (dp * scale + 0.5f);
        return px;
    }

    public static Point restrictSizeInSize(Point size, Point inSize) {
        if (size.x * inSize.y > inSize.x * size.y) {
            int width = inSize.x;
            int height = (int) (size.y * 1.0 / size.x * inSize.x);
            return new Point(width, height);
        } else if (size.y * inSize.x > inSize.y * size.x) {
            int height = inSize.y;
            int width = (int) (size.x * 1.0 / size.y * inSize.y);
            return new Point(width, height);
        } else {
            return inSize;
        }
    }

    public static Bitmap rotateBitmap(Bitmap source, float angle)
    {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    public static Bitmap decodeBitmapFromPath(String path, int reqWidth, int reqHeight) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, options);
    }

    public static Point getImageSize(String path) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        return new Point(options.outWidth, options.outHeight);
    }

    static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            if (width > height) {
                inSampleSize = Math.round((float) height / (float) reqHeight);
            } else {
                inSampleSize = Math.round((float) width / (float) reqWidth);
            }
        }
        return inSampleSize;
    }

    public static Bitmap decodeBitmapFromPath(String path, int reqWidth) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, options);
    }

    static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth) {
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (width > reqWidth) {
            inSampleSize = Math.round((float) width / (float) reqWidth);
        }
        return inSampleSize;
    }

    public static String md5(final String s) {
        final String MD5 = "MD5";
        try {
            // Create MD5 Hash
            MessageDigest digest = MessageDigest.getInstance(MD5);
            digest.update(s.getBytes());
            byte messageDigest[] = digest.digest();

            // Create Hex String
            StringBuilder hexString = new StringBuilder();
            for (byte aMessageDigest : messageDigest) {
                String h = Integer.toHexString(0xFF & aMessageDigest);
                while (h.length() < 2)
                    h = "0" + h;
                hexString.append(h);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static String readTextContent(Uri fileUri) {
        return readTextContent(fileUri.getPath());
    }

    public static String readTextContent(String filePath) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(filePath));
            StringBuilder sb = new StringBuilder();
            while (reader.ready())
                sb.append(reader.readLine());
            return sb.toString();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public static boolean writeTextContentToFile(String content, File dstFile) {
        return writeTextContentToFile(content, dstFile.getAbsolutePath());
    }

    public static boolean writeTextContentToFile(String content, String dstPath) {
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(dstPath));
            writer.write(content);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }
}
