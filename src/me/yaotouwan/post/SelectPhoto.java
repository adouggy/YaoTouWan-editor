package me.yaotouwan.post;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import me.yaotouwan.R;
import me.yaotouwan.util.YTWHelper;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by jason on 14-5-6.
 */
public class SelectPhoto extends BaseActivity {

    ListView listView;
    String photoPathListFilePath;
    List<String> photos;
    List<Integer> selectedPhotoIds;
    DataSource dataSource;
    TextView selectedCountLabel;
    int maxSelectionCount;
    boolean isVideo;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.select_photo);

        menuResId = R.menu.select_photo_actions;

        if (getIntent().getBooleanExtra("video", false)) {
            setupActionBar(R.string.select_video_title);
            isVideo = true;
        } else {
            setupActionBar(R.string.select_photo_title);
        }

        listView = (ListView) getRootViewGroup();

        loadContent();
    }

    void loadContent() {
        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                photoPathListFilePath = getIntent().getData().getPath();
                photos = new ArrayList<String>(getIntent().getIntExtra("photo_count", 16));
                BufferedReader reader = null;
                try {
                    reader = new BufferedReader(new FileReader(photoPathListFilePath));
                    String path = null;
                    while ((path = reader.readLine()) != null) {
                        photos.add(path);
                    }
                    Collections.reverse(photos);
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

                selectedPhotoIds = new ArrayList<Integer>(maxSelectionCount);

                dataSource = new DataSource();

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                listView.setAdapter(dataSource);
                super.onPostExecute(aVoid);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean ret = super.onCreateOptionsMenu(menu);

        maxSelectionCount = 8;

        LinearLayout actionView = new LinearLayout(this);
        actionView.setOrientation(LinearLayout.HORIZONTAL);
        actionView.setPadding(0, 0, dpToPx(16), 0);

        ImageView icon = new ImageView(this);
        icon.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        actionView.addView(icon);
        icon.setImageResource(R.drawable.post_action_bar_icon_done);

        TextView label = new TextView(this);
        label.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        actionView.addView(label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(18);
        selectedCountLabel = label;

        selectedCountLabel.setText(selectedPhotoIds.size() + "/" + maxSelectionCount);

        final MenuItem menuItem = menu.getItem(0);
        actionView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onFinishClick(menuItem);
            }
        });

        menuItem.setActionView(actionView);

        return ret;
    }

    class DataSource extends BaseAdapter {

        LayoutInflater inflater;
        int countInRow;

        DataSource() {
            inflater = getLayoutInflater();
            checkCountInRow();
        }

        @Override
        public int getCount() {
            return (photos.size() + countInRow - 1) / countInRow;
        }

        @Override
        public String getItem(int position) {
            if (position >= photos.size())
                return null;
            return photos.get(position);
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
                countInRow = 3;
            } else {
                countInRow = 4;
            }
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        void setupPhoto(ViewGroup group, final int pos) {
            final CachedImageButton previewImageButton = (CachedImageButton) group.findViewById(R.id.photo_preview);
            final ImageView checkbox = (ImageView) group.findViewById(R.id.check_box);

            int width = (getRootViewGroup().getWidth() - dpToPx(2)) / countInRow;
            setViewSize(group, width, width);

            String path = getItem(pos);
            if (path == null) {
                hideView(group);
            } else {
                showView(group);
                if (isVideo) {
                    previewImageButton.setImageWithVideoPath(path,
                            MediaStore.Video.Thumbnails.MINI_KIND, false, 0);
                    showView(group.findViewById(R.id.video_flag));
                } else {
                    previewImageButton.setImageWithPath(path,
                            width * 2 / 3, true, CachedImageButton.DEFAULT_DELAY);
                    hideView(group.findViewById(R.id.video_flag));
                }
                previewImageButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (selectedPhotoIds.contains(pos)) {
                            selectedPhotoIds.remove(Integer.valueOf(pos));
                            hideView(checkbox);
                        } else if (selectedPhotoIds.size() < maxSelectionCount) {
                            selectedPhotoIds.add(pos);
                            showView(checkbox);
                        }
                        selectedCountLabel.setText(selectedPhotoIds.size() + "/" + maxSelectionCount);
                    }
                });

                if (selectedPhotoIds.contains(pos)) {
                    showView(checkbox);
                } else {
                    hideView(checkbox);
                }
            }
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            View rowView = convertView != null ? convertView : inflater.inflate(R.layout.select_photo_item, null);
            assert rowView != null;

            ViewGroup group = (ViewGroup) rowView.findViewById(R.id.photo1);
            setupPhoto(group, position * countInRow);

            group = (ViewGroup) rowView.findViewById(R.id.photo2);
            setupPhoto(group, position * countInRow + 1);

            group = (ViewGroup) rowView.findViewById(R.id.photo3);
            setupPhoto(group, position * countInRow + 2);

            group = (ViewGroup) rowView.findViewById(R.id.photo4);
            if (countInRow == 4) {
                showView(group);
                setupPhoto(group, position * countInRow + 3);
            } else {
                hideView(group);
            }


            return rowView;
        }
    }

    public void onFinishClick(MenuItem item) {
        if (selectedPhotoIds.size() > 0) {
            Intent intent = new Intent();
            intent.putExtra(isVideo ? "selected_video_count" : "selected_photo_count", selectedPhotoIds.size());
            for (int i=0; i<selectedPhotoIds.size(); i++) {
                Integer pos = selectedPhotoIds.get(i);
                String path = photos.get(pos);
                intent.putExtra((isVideo ? "selected_video_" : "selected_photo_") + i, path);
                if (isVideo) {
                    Bitmap thumbnail = CachedImageButton.loadVideoThumbnail(this,
                            path, MediaStore.Video.Thumbnails.MINI_KIND);
                    if (thumbnail != null) {
                        intent.putExtra("selected_video_width_" + i, thumbnail.getWidth());
                        intent.putExtra("selected_video_height_" + i, thumbnail.getHeight());
                    }
                }
            }
            setResult(RESULT_OK, intent);
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        if (selectedPhotoIds.size() > 0) {
            YTWHelper.confirm(this, getString(R.string.select_photo_giveup), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    selectedPhotoIds.clear();
                    onBackPressed();
                }
            });
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onViewSizeChanged() {
        dataSource.notifyDataSetChanged();
        super.onViewSizeChanged();
    }
}