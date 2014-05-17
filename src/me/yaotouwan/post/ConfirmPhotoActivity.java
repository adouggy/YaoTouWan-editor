package me.yaotouwan.post;

import android.app.ActionBar;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import com.actionbarsherlock.view.MenuItem;
import me.yaotouwan.R;
import me.yaotouwan.util.YTWHelper;
import uk.co.senab.photoview.PhotoViewAttacher;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by jason on 14-4-1.
 */
public class ConfirmPhotoActivity extends BaseActivity {

    public static final int RESULT_RETAKE = 1;
    public static final int RESULT_RESELECT = 2;
    private static final int INTENT_REQUEST_CODE_EDIT_PHOTO = 1;
    private static final int INTENT_REQUEST_CODE_EDIT_PHOTO_CUSTOM = 2;
    private Uri photoUri;
    private Uri editedPhotoUri;
    private String photoPath;
    private String editedPhotoPath;
    private boolean isFromCamera;

    private PhotoViewAttacher mAttacher;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.confirm_photo);

        menuResId = R.menu.confirm_image_actions;

        isFromCamera = getIntent().getBooleanExtra("is_from_camera", false);
        ActionBar bar = getActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setDisplayShowHomeEnabled(false);
            if (isFromCamera)
                bar.setTitle(R.string.confirm_photo_retake);
            else
                bar.setTitle(R.string.confirm_photo_reselect);
        }

        setRootBackground(R.color.black);

        photoUri = getIntent().getData();
        photoPath = parseUriForImageStorage(photoUri).getPath();
        uk.co.senab.photoview.PhotoView mImageView = (uk.co.senab.photoview.PhotoView) findViewById(R.id.confirm_photo_preview);
        BitmapDrawable photoDrawable = new BitmapDrawable(getResources(), photoPath);
        mImageView.setImageDrawable(photoDrawable);
        mAttacher = new PhotoViewAttacher(mImageView);
        mAttacher.setOnPhotoTapListener(new PhotoViewAttacher.OnPhotoTapListener() {
            @Override
            public void onPhotoTap(View view, float x, float y) {
                toggleActionBar();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onClickRetake(item);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void previewImageOnClick(View view) {
//        toggleVisible(R.id.confirm_photo_control_bar, true);
        toggleActionBar();
        toggleFullscreen();
    }

    void doEdit(Uri uri, String path) {
        Intent editPhotoIntent = new Intent(Intent.ACTION_EDIT);
        editPhotoIntent.setDataAndType(uri, "image/*");
        editPhotoIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooserIntent = Intent.createChooser(editPhotoIntent, null);
        startActivityForResult(chooserIntent, INTENT_REQUEST_CODE_EDIT_PHOTO);
    }

    public void onClickRetake(MenuItem item) {
        Intent intent = new Intent();
        setResult(isFromCamera ? RESULT_RETAKE : RESULT_RESELECT, intent);
        finish();
    }

    public void onClickConfirm(MenuItem item) {
        Intent intent = new Intent();
        intent.setData(photoUri);
        setResult(RESULT_OK, intent);
        finish();
    }

    public void onClickEdit(MenuItem item) {
        if (isFromCamera) {
            doEdit(photoUri, photoPath);
        } else {
            // 準備一張圖片拷貝，否則原圖無法編輯後保存
            editedPhotoPath = copyImage(parseUriForImageStorage(photoUri).getPath());
            String fid = new SimpleDateFormat("yyyy_MMddHHmmss_SSS").format(new Date());
            String fileName = "Yaotouwan_" + fid + ".jpg";
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.TITLE, fileName);
            values.put(MediaStore.Images.Media.DESCRIPTION,"Image edited by yaotouwan");
            values.put(MediaStore.Images.Media.DATA, editedPhotoPath);
            editedPhotoUri = getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            doEdit(editedPhotoUri, editedPhotoPath);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == INTENT_REQUEST_CODE_EDIT_PHOTO) {
            if (data != null) {
                Intent intent = new Intent();
                intent.setData(data.getData());
                setResult(RESULT_OK, intent);
                finish();
            } else if (!isFromCamera) {
                String photoPath = parseUriForImageStorage(editedPhotoUri).getPath();
                new File(photoPath).delete();
                getContentResolver().delete(editedPhotoUri, null, null);
            }
        } else if (requestCode == INTENT_REQUEST_CODE_EDIT_PHOTO_CUSTOM) {
            if (data != null) {
                Intent intent = new Intent();
                intent.setData(data.getData());
                setResult(RESULT_OK, intent);
                finish();
            } else if (!isFromCamera) {
                new File(editedPhotoPath).delete();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Need to call clean-up
        mAttacher.cleanup();
    }

    Uri parseUriForImageStorage(Uri uri) {
        return parseUriForMediaStorage(uri, false);
    }

    Uri parseUriForMediaStorage(Uri uri, boolean isVideo) {
        String column = isVideo ? MediaStore.Video.VideoColumns.DATA
                : MediaStore.Images.ImageColumns.DATA;
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    uri,
                    new String[]{column},
                    null, null, null);
            if (cursor != null) {
                int idx = cursor.getColumnIndex(column);
                if (cursor.moveToFirst()) {
                    String fileSrc = cursor.getString(idx);
                    if (fileSrc != null)
                        return Uri.parse(fileSrc);
                }
                String uriString = uri.getPath();
                String exp = isVideo ? "video:(\\d+)$" : "image:(\\d+)$";
                Pattern p = Pattern.compile(exp);
                Matcher m = p.matcher(uriString);
                while (m.find()) {
                    // demo: content://com.android.providers.media.documents/document/video%3A256
                    String mediaID = m.group(1);
                    if (mediaID != null) {
                        Uri baseUri = isVideo ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                        Uri standardUri = Uri.withAppendedPath(baseUri, mediaID);
                        return parseUriForMediaStorage(standardUri, isVideo);
                    }
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    String copyImage(String srcPath) {
        // 这个类都不需要了，还没删，所以先把这行无法编译的代码注视掉
//        try {
//            String dstPath = YTWHelper.prepareFilePathForImageSave();
//            InputStream in = new FileInputStream(srcPath);
//            OutputStream out = new FileOutputStream(dstPath);
//
//            byte[] buf = new byte[4096];
//            int len;
//            while ((len = in.read(buf)) > 0) {
//                out.write(buf, 0, len);
//            }
//            in.close();
//            out.close();
//            return dstPath;
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        return null;
    }
}