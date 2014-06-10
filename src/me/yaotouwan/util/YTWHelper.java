package me.yaotouwan.util;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
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

    public static boolean isFBCanRW() {
        try {
            File fbFile = new File("/dev/graphics/fb0");
            return fbFile.canRead() && fbFile.canWrite();
        } catch (Exception exception) {
            return false;
        }
    }

    public static boolean chmodFB() {
        try {
            File fbFile = new File("/dev/graphics/fb0");
            if (!fbFile.canRead() || !fbFile.canWrite()) {
                return Root.INSTANCE.runCMD(true, "chmod 666 /dev/graphics/fb0").success();
            } else {
                return true;
            }
        } catch (Exception exception) {
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
            File rd = new File(sdcard, "YaoTouWan");
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

    public static String generateRandomFilename(String ext) {
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS");
        return df.format(new Date()) + "." + ext;
    }

    public static String prepareFilePathForImageSaveWithDraftUri(Uri draftUri)  {
        String draftPath = draftUri.getPath();
        String draftMediaDirPath = draftPath.substring(0, draftPath.length() - 5);
        File draftMediaDir = new File(draftMediaDirPath);
        if (!draftMediaDir.exists()) {
            draftMediaDir.mkdirs();
        }
        return new File(draftMediaDir, generateRandomFilename("jpg")).getAbsolutePath();
    }

    public static String generateFilePathForVideoSaveWithDraftUri(Uri draftUri)  {
        String draftPath = draftUri.getPath();
        String draftMediaDirPath = draftPath.substring(0, draftPath.length() - 5);
        File draftMediaDir = new File(draftMediaDirPath);
        if (!draftMediaDir.exists()) {
            draftMediaDir.mkdirs();
        }
        return new File(draftMediaDir, generateRandomFilename("mp4")).getAbsolutePath();
    }

    public static String prepareFilePathForVideoSaveWithDraftUri(Uri draftUri)  {
        String draftPath = draftUri.getPath();
        String draftMediaDirPath = draftPath.substring(0, draftPath.length() - 5);
        File draftMediaDir = new File(draftMediaDirPath);
        if (!draftMediaDir.exists()) {
            draftMediaDir.mkdirs();
        }
        String[] files = draftMediaDir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                return filename.endsWith(".mp4");
            }
        });
        if (files.length > 0) {
            for (String file : files) {
                if (file.contains("-0.mp4")) {
                    return new File(draftMediaDir, file.replace("-0", "")).getAbsolutePath();
                } else if (file.contains("-a.mp4")) {
                    return new File(draftMediaDir, file.replace("-a", "")).getAbsolutePath();
                }
            }
        }
        if (files.length > 0) {
            return new File(draftMediaDir, files[0]).getAbsolutePath();
        }
        return new File(draftMediaDir, generateRandomFilename("mp4")).getAbsolutePath();
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
                                    int propertyValue) {
        SharedPreferences preferences =
                context.getSharedPreferences("YTWPreferences", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(propertyName, propertyValue);
        editor.commit();
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

    public static int getIntProperty(final Context context,
                                             String propertyName) {
        SharedPreferences preferences =
                context.getSharedPreferences("YTWPreferences", Context.MODE_PRIVATE);
        return preferences.getInt(propertyName, 0);
    }

    public static boolean getBooleanProperty(final Context context,
                                      String propertyName) {
        SharedPreferences preferences =
                context.getSharedPreferences("YTWPreferences", Context.MODE_PRIVATE);
        return preferences.getBoolean(propertyName, false);
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

    public static String readAssertsTextContent(Context context, String filename) {
        InputStream is = null;
        try {
            is = context.getResources().getAssets().open(filename);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            while (reader.ready())
                sb.append(reader.readLine());
            return sb.toString();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
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
//            e.printStackTrace();
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

    public static void delete(File file) {

        if (file.isDirectory()) {
            if (file.list().length == 0) {
                file.delete();
            } else {
                String files[] = file.list();
                for (String temp : files) {
                    File fileDelete = new File(file, temp);
                    delete(fileDelete);
                }
                if(file.list().length==0){
                    file.delete();
                }
            }
        } else {
            file.delete();
        }
    }

    public static String postsDir() {
        return new File(dataRootDirectory(0), "Posts").getAbsolutePath();
    }
}
