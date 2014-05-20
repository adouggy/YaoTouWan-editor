package me.yaotouwan.post;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.*;
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

    GridView listView;
    String photoPathListFilePath;
    List<String> photos;
    List<Integer> selectedPhotoIds;
    DataSource dataSource;
    TextView selectedCountLabel;
    int maxSelectionCount;
    boolean isVideo;
    int thumbnailSize;

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

        listView = (GridView) getRootViewGroup();
        int width = getWindowSize().x / 3 - dpToPx(1) * 2;
        listView.setColumnWidth(width);
        listView.setHorizontalSpacing(dpToPx(1));
        listView.setVerticalSpacing(dpToPx(1));
        thumbnailSize = width;

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

        final LinearLayout actionView = new LinearLayout(this);
        actionView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        actionView.setOrientation(LinearLayout.HORIZONTAL);
        actionView.setPadding(dpToPx(10), 0, dpToPx(10), 0);

        ImageView icon = new ImageView(this);
        icon.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        actionView.addView(icon);
        icon.setImageResource(R.drawable.post_action_bar_icon_done);

        TextView label = new TextView(this);
        label.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        actionView.addView(label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(18);
        label.setGravity(Gravity.CENTER_VERTICAL);
        selectedCountLabel = label;

        selectedCountLabel.setText(selectedPhotoIds.size() + "/" + maxSelectionCount);

        final MenuItem menuItem = menu.getItem(0);
        icon.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN){
                    actionView.setBackgroundColor(Color.parseColor("#55FFFFFF"));
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    onFinishClick(menuItem);
                    actionView.setBackgroundColor(Color.parseColor("#00000000"));
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    actionView.setBackgroundColor(Color.parseColor("#00000000"));
                }
                return true;
            }
        });
        label.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN){
                    actionView.setBackgroundColor(Color.parseColor("#55FFFFFF"));
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    onFinishClick(menuItem);
                    actionView.setBackgroundColor(Color.parseColor("#00000000"));
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    actionView.setBackgroundColor(Color.parseColor("#00000000"));
                }
                return true;
            }
        });

        menuItem.setActionView(actionView);

        return ret;
    }

    class DataSource extends BaseAdapter {

        LayoutInflater inflater;

        DataSource() {
            inflater = getLayoutInflater();
        }

        @Override
        public int getCount() {
            return photos.size();
        }

        @Override
        public String getItem(int position) {
            if (position >= photos.size())
                return null;
            return photos.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            View rowView = convertView != null ? convertView : inflater.inflate(R.layout.select_photo_item, null);
            assert rowView != null;

            final CachedImageButton previewImageButton = (CachedImageButton) rowView.findViewById(R.id.photo_preview);
            final ImageView checkbox = (ImageView) rowView.findViewById(R.id.check_box);

            setViewHeight(previewImageButton, listView.getColumnWidth());
            String path = getItem(position);
            if (isVideo) {
                previewImageButton.setImageWithVideoPath(path,
                        MediaStore.Video.Thumbnails.MINI_KIND, false, 0);
                showView(rowView.findViewById(R.id.video_flag));
            } else {
                previewImageButton.setImageWithPath(path, thumbnailSize, true, 0);
                hideView(rowView.findViewById(R.id.video_flag));
            }
            previewImageButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (selectedPhotoIds.contains(position)) {
                        selectedPhotoIds.remove(Integer.valueOf(position));
                        hideView(checkbox);
                    } else if (selectedPhotoIds.size() < maxSelectionCount) {
                        selectedPhotoIds.add(position);
                        showView(checkbox);
                    }
                    selectedCountLabel.setText(selectedPhotoIds.size() + "/" + maxSelectionCount);
                }
            });

            if (selectedPhotoIds.contains(position)) {
                showView(checkbox);
            } else {
                hideView(checkbox);
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