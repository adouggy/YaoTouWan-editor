package me.yaotouwan.post;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.actionbarsherlock.view.MenuItem;
import me.yaotouwan.R;
import me.yaotouwan.util.YTWHelper;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jason on 14-5-6.
 */
public class PhotoAlbum extends BaseActivity {

    DataSource dataSource;
    GridView listView;
    boolean isVideo;
    ProgressDialog dialog;
    int thumbnailSize;

    final static int INTENT_REQUEST_CODE_SELECT_PHOTO = 1;
    final static int INTENT_REQUEST_CODE_TAKE_PHOTO = 2;
    final static int INTENT_REQUEST_CODE_RECORD_VIDEO = 3;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.photo_album);

        menuResId = R.menu.photo_album_actions;

        if (getIntent().getBooleanExtra("video", false)) {
            setupActionBar(R.string.video_album_title);
            isVideo = true;
        } else {
            setupActionBar(R.string.photo_album_title);
        }

        listView = (GridView) findViewById(R.id.root_layout);
        int width = getWindowSize().x / 3 - dpToPx(1) * 2;
        listView.setColumnWidth(width);
        listView.setHorizontalSpacing(dpToPx(1));
        listView.setVerticalSpacing(dpToPx(1));
        thumbnailSize = width;

        loadContent();
    }

    private void loadContent() {
        dialog = new ProgressDialog(this);
        dialog.setMessage(getString(R.string.please_wait));
        dialog.setCancelable(false);
        dialog.show();

        new AsyncTask<Integer, Integer, Boolean>() {

            List<Album> albums = new ArrayList<Album>(2);

            @Override
            protected Boolean doInBackground(Integer... params) {
                dataSource = new DataSource(albums);

                // 遍历常用目录
                File rootDir = Environment.getExternalStorageDirectory();
                File dir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DCIM);
                loadPhotoInDir(dir, "相机交卷", false);

                dir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES);
                loadPhotoInDir(dir, "我的相册", false);

                dir = new File(rootDir, "Pictures/Screenshots");
                loadPhotoInDir(dir, "屏幕截图", false);

                dir = new File(rootDir, "我的相机");
                loadPhotoInDir(dir, "我的相机", false);

                publishProgress(1);

                // 遍历上次发现过的目录
                logd(getExternalCacheDir().getAbsolutePath());
                File albumListFile = new File(getCacheDir(), albumListFilePath());
                BufferedReader reader = null;
                try {
                    reader = new BufferedReader(new FileReader(albumListFile));
                    String path;
                    while ((path = reader.readLine()) != null) {
                        String title = null;
                        if (new File(path).getName().toLowerCase().equals(Consts.DATA_ROOT_DIR.toLowerCase()))
                            title = getString(R.string.app_name);
                        loadPhotoInDir(new File(path), title, true);
                    }
                } catch (FileNotFoundException e) {
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

                // 遍历其他目录
                File[] files = rootDir.listFiles();
                if (files == null) return false;

                for (File file : files) {
                    String title = null;
                    if (file.getName().toLowerCase().equals(Consts.DATA_ROOT_DIR))
                        title = getString(R.string.app_name);
                    loadPhotoInDir(file, title, true);
                }

                return true;
            }

            void loadPhotoInDir(File dir, String title, boolean updateProgress) {
                if (!dir.exists()) return;
                if (!dir.isDirectory()) return;
                if (dir.isHidden()) return;
                if (dir.getName().equals("Android")) return;

                boolean existed = false;
                for (Album alb : dataSource.albums) {
                    if (alb.path.equals(dir.getAbsolutePath())) {
                        existed = true;
                        break;
                    }
                }
                if (existed) return;

                Album album = readPhotosAtDir(dir, null);
                if (album != null) {
                    logd(dir.getAbsolutePath());
                    album.name = dir.getName();
                    if (title != null)
                        album.name = title;
                    album.path = dir.getAbsolutePath();
                    album.savePhotoList();
                    synchronized (dataSource.albums) {
                        dataSource.albums.add(album);
                    }
                    if (updateProgress)
                        publishProgress(1);
                }
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                if (listView.getAdapter() != dataSource) {
                    listView.setAdapter(dataSource);
                    dialog.dismiss();
                } else {
                    dataSource.notifyDataSetChanged();
                }

                File albumListFile = new File(getCacheDir(), albumListFilePath());
                BufferedWriter writer = null;
                try {
                    writer = new BufferedWriter(new FileWriter(albumListFile));
                    synchronized (albums) {
                        for (Album album : albums) {
                            writer.write(album.path + "\n");
                        }
                    }
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
            }

            String albumListFilePath() {
                return isVideo ? "video_albums" : "photo_albums";
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    boolean isMediaFile(File file) {
        if (isVideo) {
            return file.getPath().toLowerCase().endsWith(".mp4")
                    || file.getParent().toLowerCase().endsWith(".3gp");
        } else {
            return file.getPath().toLowerCase().endsWith(".jpg")
                    || file.getParent().toLowerCase().endsWith(".jpeg")
                    || file.getPath().toLowerCase().endsWith(".png");
        }
    }

    private Album readPhotosAtDir(File dir, Album album) {
        File[] files = dir.listFiles();
        if (files == null) return album;

        for (File file : files) {
            if (file.isHidden()) continue;
            if (file.isDirectory()) {
                if (file.getAbsolutePath().equals(YTWHelper.postsDir()))
                    continue;
                boolean existed = false;
                if (dataSource != null) {
                    for (Album alb : dataSource.albums) {
                        if (alb.path.equals(file.getAbsolutePath())) {
                            existed = true;
                            break;
                        }
                    }
                }
                if (existed) continue;

                album = readPhotosAtDir(file, album);
            } else {
                if (isMediaFile(file)) {
                    if (album == null) {
                        album = new Album();
                    }
                    album.count ++;
                    album.mediaPathList.add(file.getAbsolutePath());
                    if (album.lastMediaPath == null
                            || file.lastModified() > new File(album.lastMediaPath).lastModified()) {
                        album.lastMediaPath = file.getAbsolutePath();
                    }
                }
            }
        }
        return album;
    }

    class Album {
        String name;
        String path;
        int count;
        String lastMediaPath;
        List<String> mediaPathList = new ArrayList<String>();
        String mediaPathListFilePath; // 临时存储媒体文件列表的文本文件

        void savePhotoList() {
            File cacheDir = getCacheDir();
            BufferedWriter writer = null;
            try {
                File photoPathListFile = new File(cacheDir, YTWHelper.md5(path));
                writer = new BufferedWriter(new FileWriter(photoPathListFile));
                for (String path : mediaPathList) {
                    writer.write(path + "\n");
                }
                writer.flush();
                mediaPathListFilePath = photoPathListFile.getAbsolutePath();
                mediaPathList = null;
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
        }
    }

    class DataSource extends BaseAdapter {

        List<Album> albums;
        LayoutInflater inflater;

        DataSource(List<Album> albums) {
            this.albums = albums;
            inflater = getLayoutInflater();
        }

        @Override
        public int getCount() {
            return albums.size();
        }

        @Override
        public Album getItem(int position) {
            if (albums.size() <= position)
                return null;
            return albums.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View rowView = convertView != null ? convertView :
                                inflater.inflate(R.layout.photo_album_item, null);
            final Album album = getItem(position);

            CachedImageButton previewImageButton = (CachedImageButton)
                    rowView.findViewById(R.id.album_preview);
            final ImageButton previewImageCover = (ImageButton)
                    rowView.findViewById(R.id.album_preview_cover);
            setViewHeight(previewImageButton, thumbnailSize);
            setViewHeight(previewImageCover, thumbnailSize);
            final TextView titleLabel = (TextView) rowView.findViewById(R.id.album_title);
            final TextView countLabel = (TextView) rowView.findViewById(R.id.album_count);
            if (isVideo)
                previewImageButton.setImageWithVideoPath(album.lastMediaPath,
                        MediaStore.Video.Thumbnails.FULL_SCREEN_KIND, false, 0);
            else
                previewImageButton.setImageWithPath(album.lastMediaPath, thumbnailSize, false, 0);
            titleLabel.setText(album.name);
            countLabel.setText(album.count + (isVideo ? "个" : "张"));
            final View labelGroup = rowView.findViewById(R.id.album_info_group);
            labelGroup.post(new Runnable() {
                @Override
                public void run() {
                    int titleLabelWidth = titleLabel.getMeasuredWidth();
                    int countLabelWidth = countLabel.getMeasuredWidth();
                    if (titleLabelWidth + countLabelWidth >
                            thumbnailSize - labelGroup.getPaddingLeft() - labelGroup.getPaddingRight()) {
                        titleLabel.setWidth(thumbnailSize - labelGroup.getPaddingLeft() - labelGroup.getPaddingRight()
                                - countLabelWidth);
                    }
                }
            });
            previewImageCover.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        previewImageCover.setBackgroundColor(Color.parseColor("#55FFFFFF"));
                    } else if (event.getAction() == MotionEvent.ACTION_UP
                            || event.getAction() == MotionEvent.ACTION_CANCEL) {
                        previewImageCover.setBackgroundColor(Color.parseColor("#00000000"));
                    }

                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        Intent intent = new Intent(PhotoAlbum.this, SelectPhoto.class);
                        intent.setData(Uri.parse(album.mediaPathListFilePath));
                        intent.putExtra("photo_count", album.count);
                        intent.putExtra("video", isVideo);
                        startActivityForResult(intent, INTENT_REQUEST_CODE_SELECT_PHOTO);
                    }
                    return true;
                }
            });

            return rowView;
        }
    }

    Uri sourceImageUri;
    Uri sourceVideoUri;
    public void onCameraClick(MenuItem item) {
        if (isVideo) {
            Intent intent = new Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE);
            startActivityForResult(Intent.createChooser(intent, "拍摄视频"), INTENT_REQUEST_CODE_RECORD_VIDEO);
        } else {
            Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            startActivityForResult(Intent.createChooser(intent, "拍摄照片"), INTENT_REQUEST_CODE_TAKE_PHOTO);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == INTENT_REQUEST_CODE_SELECT_PHOTO) {
            if (resultCode == RESULT_OK) {
                setResult(RESULT_OK, data);
                finish();
            }
        } else if (requestCode == INTENT_REQUEST_CODE_TAKE_PHOTO) {
            if (resultCode == RESULT_OK) {
                Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        new String[]{MediaStore.Images.ImageColumns.DATA}, null, null, MediaStore.Images.ImageColumns._ID + " DESC");
                if (cursor.moveToFirst()) {
                    String takePicturePath = cursor.getString(0);
                    Intent intent = new Intent();
                    intent.putExtra("selected_photo_count", 1);
                    intent.putExtra("selected_photo_0", takePicturePath);
                    setResult(RESULT_OK, intent);
                    finish();
                }
            }
        } else if (requestCode == INTENT_REQUEST_CODE_RECORD_VIDEO) {
            if (resultCode == RESULT_OK) {
                Cursor cursor = getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        new String[]{MediaStore.Video.VideoColumns.DATA}, null, null, MediaStore.Video.VideoColumns._ID + " DESC");
                if (cursor.moveToFirst()) {
                    String recordVideoPath = cursor.getString(0);
                    Intent intent = new Intent();
                    intent.putExtra("selected_video_count", 1);
                    intent.putExtra("selected_video_0", recordVideoPath);
                    setResult(RESULT_OK, intent);
                    finish();
                }
            }
        }
    }

    @Override
    protected void onViewSizeChanged() {
        dataSource.notifyDataSetChanged();
        super.onViewSizeChanged();
    }
}