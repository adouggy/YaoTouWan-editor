package me.yaotouwan.post;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
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
    ListView listView;
    boolean isVideo;
    ProgressDialog dialog;

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

        listView = (ListView) findViewById(R.id.root_layout);

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

                File albumListFile = new File(getCacheDir(), albumListFilePath());
                BufferedReader reader = null;
                try {
                    reader = new BufferedReader(new FileReader(albumListFile));
                    String path;
                    while ((path = reader.readLine()) != null) {
                        String title = null;
                        if (new File(path).getName().toLowerCase().equals("yaotouwan"))
                            title = "摇头玩";
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

                File[] files = rootDir.listFiles();
                if (files == null) return false;

                for (File file : files) {
                    String title = null;
                    if (file.getName().toLowerCase().equals("yaotouwan"))
                        title = "摇头玩";
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
                    album.name = dir.getName();
                    if (title != null)
                        album.name = title;
                    album.path = dir.getAbsolutePath();
                    album.savePhotoList();
                    dataSource.albums.add(album);
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
                    for (Album album : albums) {
                        writer.write(album.path + "\n");
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
        int countInRow;

        DataSource(List<Album> albums) {
            this.albums = albums;
            inflater = getLayoutInflater();
            checkCountInRow();
        }

        @Override
        public void notifyDataSetChanged() {
            checkCountInRow();
            super.notifyDataSetChanged();
        }

        private void checkCountInRow() {
            int orientation = getScreenOrientation();
            if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    || orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT) {
                countInRow = 2;
            } else {
                countInRow = 3;
            }
        }

        @Override
        public int getCount() {
            return (albums.size() + countInRow - 1) / countInRow;
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

        void setupAlbum(ViewGroup group, final Album album) {
            if (album == null) {
                hideView(group);
                return;
            }
            showView(group);
            int width = getRootViewGroup().getWidth() / countInRow - dpToPx(1);
            setViewSize(group, width, width);
            CachedImageButton previewImageButton = (CachedImageButton) group.findViewById(R.id.album_preview);
            TextView titleLabel = (TextView) group.findViewById(R.id.album_title);
            TextView countLabel = (TextView) group.findViewById(R.id.album_count);
            if (isVideo)
                previewImageButton.setImageWithVideoPath(album.lastMediaPath,
                        MediaStore.Video.Thumbnails.FULL_SCREEN_KIND, false, 0);
            else
                previewImageButton.setImageWithPath(album.lastMediaPath, width, false, 0);
            titleLabel.setText(album.name);
            countLabel.setText(album.count + (isVideo ? "个" : "张"));
            previewImageButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(PhotoAlbum.this, SelectPhoto.class);
                    intent.setData(Uri.parse(album.mediaPathListFilePath));
                    intent.putExtra("photo_count", album.count);
                    intent.putExtra("video", isVideo);
                    startActivityForResult(intent, INTENT_REQUEST_CODE_SELECT_PHOTO);
                }
            });
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View rowView = convertView != null ? convertView :
                    inflater.inflate(R.layout.photo_album_item, null);

            final Album albumLeft = getItem(position * countInRow);
            ViewGroup leftAlbumGroup = (ViewGroup) rowView.findViewById(R.id.album_left);
            setupAlbum(leftAlbumGroup, albumLeft);

            if (countInRow == 2) {
                ViewGroup albumGroup = (ViewGroup) rowView.findViewById(R.id.album_center);
                hideView(albumGroup);

                final Album albumRight = getItem(position * countInRow + 1);
                albumGroup = (ViewGroup) rowView.findViewById(R.id.album_right);
                setupAlbum(albumGroup, albumRight);
            } else if (countInRow == 3) {
                final Album albumCenter = getItem(position * countInRow + 1);
                ViewGroup albumGroup = (ViewGroup) rowView.findViewById(R.id.album_center);
                setupAlbum(albumGroup, albumCenter);
                showView(albumGroup);

                final Album albumRight = getItem(position * countInRow + 2);
                albumGroup = (ViewGroup) rowView.findViewById(R.id.album_right);
                setupAlbum(albumGroup, albumRight);
            }

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