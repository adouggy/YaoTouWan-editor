package me.yaotouwan.post;

import android.app.ActivityManager;
import android.content.*;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.QuoteSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.client.ClientProtocolException;
import ch.boye.httpclientandroidlib.client.HttpClient;
import ch.boye.httpclientandroidlib.client.methods.HttpGet;
import ch.boye.httpclientandroidlib.util.EntityUtils;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.mobeta.android.dslv.DragSortController;
import com.mobeta.android.dslv.DragSortListView;
import com.mobeta.android.dslv.SimpleDragSortCursorAdapter;
import me.yaotouwan.R;
import me.yaotouwan.screenrecorder.EditVideoActivity;
import me.yaotouwan.screenrecorder.SRecorderService;
import me.yaotouwan.screenrecorder.SelectGameActivity;
import me.yaotouwan.uicommon.ActionSheet;
import me.yaotouwan.uicommon.ActionSheetItem;
import me.yaotouwan.util.AppPackageHelper;
import me.yaotouwan.util.YTWHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.List;


/**
 * Created by jason on 14-3-18.
 */
public class PostActivity extends BaseActivity {

    private static final int INTENT_REQUEST_CODE_SELECT_PHOTO = 1;
    private static final int INTENT_REQUEST_CODE_SELECT_VODEO = 2;
    private static final int INTENT_REQUEST_CODE_RECORD_SCREEN = 3;
    private static final int INTENT_REQUEST_CODE_CUT_VIDEO = 4;

    private static final int MAX_IMAGE_WIDTH_FOR_UPLOAD = 1280;
    private static final long MAX_IMAGE_FILE_SIZE = 5 * 1000 * 1000;

    DragSortListView postItemsListView;
    PostListViewDataSource adapter;
    Uri croppedVideoUri;
    EditText titleEditor;
    boolean showKeyboard4EditingTitle;
    EditText contentEditor;
    ViewGroup toolbar;
    int editVideoAtPosition = -1;
    boolean finishButtonClicked; // 为防止键盘显示判断错误导致的点击无效
    List<AppPackageHelper.Game> gamesInstalled;
    Fragment headerFragment;
    Fragment footerFragment;
    Menu menuOnActionBar;
    boolean hasTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        IntentFilter intentFilter = new IntentFilter(UploadPostMediaService.ACTION_POST_MEDIA_PROGRESS);
        intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
        try {
            intentFilter.addDataType("application/json");
        } catch (IntentFilter.MalformedMimeTypeException e) {
            e.printStackTrace();
        }

        setContentView(R.layout.post);
        setupActionBar(R.string.post_title);

        Intent intent = getIntent();
        Uri postUri = intent.getData();

        toolbar = (ViewGroup) findViewById(R.id.post_toolbar);
        postItemsListView = (DragSortListView) findViewById(R.id.post_items);
        ((DragSortController)postItemsListView.mFloatViewManager).dragModeShadowImage
                = R.drawable.post_item_drag_mode_bg;

        menuResId = R.menu.post_actions;
        View titleContent = getLayoutInflater().inflate(R.layout.post_title, null);
        postItemsListView.addHeaderView(titleContent);

        View footer = getLayoutInflater().inflate(R.layout.post_footer, null);
        postItemsListView.addFooterView(footer);
        showView(R.id.footer_sep);
        hideView(R.id.footer_readonly);

        titleEditor = (EditText) findViewById(R.id.post_title);
        titleEditor.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus && !isSoftKeyboardShown) {
                    showKeyboard4EditingTitle = true;
                }
            }
        });
        hasTitle = intent.getBooleanExtra("has_title", true);
        if (!hasTitle) {
            hideView(R.id.title_seperator5);
            hideView(R.id.title_seperator6);
            hideView(R.id.post_title_group);
        }

        contentEditor = (EditText) findViewById(R.id.content_editor);

        if (postUri != null) {
            draftFile = new File(postUri.getPath());
            String JSON = YTWHelper.readTextContent(draftFile.getAbsolutePath());
            loadDraftFromJSON(JSON);
        }
        prepareDraftFile();

        adapter = new PostListViewDataSource();

        postItemsListView.setAdapter(adapter);
        postItemsListView.setDragSortListener(adapter);
        postItemsListView.setOnScrollListener(adapter);

        listenKeyboard();

        preloadGameList();

        SRecorderService.rmIndicatorFile();
        new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] params) {
                YTWHelper.killAll(SRecorderService.BUILDIN_RECORDER_NAME, false);
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menuOnActionBar = menu;
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onBackPressed() {
        if (adapter.editingTextRow >= 0) {
            adapter.editingTextRow = -1;
            adapter.notifyDataSetChanged();
        } else {
            if (adapter.hasData()) {
                YTWHelper.confirm(this, getString(R.string.post_editor_giveup_edit), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                });
            } else {
                super.onBackPressed();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideSoftKeyboard();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (isSoftKeyboardShown) {
                hideSoftKeyboard();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    protected void onKeyboardHide() {
        super.onKeyboardHide();

        titleEditor.clearFocus();
        hideView(contentEditor);

        if (adapter.editingTextRow >= 0) {
            setToolbarMode(ToolbarMode_Normal);
            String text = contentEditor.getText().toString();
            adapter.updateText(adapter.editingTextRow, text);
            adapter.editingTextRow = -1;
            //把换行符拆成多个段落
            for (int i=0; i<adapter.getCount(); i++) {
                text = adapter.getText(i);
                if (text != null && text.contains("\n")) {
                    String[] lines = text.split("\\n");
                    int newLine = 0;
                    for (int j=0; j<lines.length; j++) {
                        String line = lines[j].trim();
                        if (line.length() > 0) {
                            if (newLine > 0) {
                                adapter.insertRow(i + newLine);
                            }
                            adapter.updateText(i + newLine, line);
                            newLine ++;
                        }
                    }
                    i += newLine;
                }
            }
        }

        adapter.notifyDataSetChanged();

        if (finishButtonClicked) {
            onFinishClick(null);
        } else {
            saveDraft();
        }
    }

    @Override
    protected void onKeyboardShow() {
        super.onKeyboardShow();
        showKeyboard4EditingTitle = false;
        setViewHeight(contentEditor, (int) toolbar.getY());
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == INTENT_REQUEST_CODE_SELECT_PHOTO) {
                if (data != null) {
                    final int count = data.getIntExtra("selected_photo_count", 0);
                    for (int i=0; i<count; i++) {
                        String path = data.getStringExtra("selected_photo_" + i);
                        Bitmap srcBitmap = YTWHelper.decodeBitmapFromPath(path, MAX_IMAGE_WIDTH_FOR_UPLOAD);
                        if (srcBitmap != null) {
                            String imagePathForUpload = saveImage(srcBitmap);
                            long jpgFileSize = new File(imagePathForUpload).length();
                            if (jpgFileSize > MAX_IMAGE_FILE_SIZE) {
                                srcBitmap = YTWHelper.decodeBitmapFromPath(path,
                                        (int) (MAX_IMAGE_WIDTH_FOR_UPLOAD * MAX_IMAGE_WIDTH_FOR_UPLOAD / jpgFileSize * 0.9));
                                if (srcBitmap != null) {
                                    imagePathForUpload = saveImage(srcBitmap);
                                    jpgFileSize = new File(imagePathForUpload).length();
                                    if (jpgFileSize > MAX_IMAGE_FILE_SIZE) {
                                        Toast.makeText(PostActivity.this, "图片太大，无法使用", Toast.LENGTH_LONG).show();
                                        continue;
                                    }
                                }
                            }
                            if (canAppend()) {
                                adapter.appendRow();
                            }
                            adapter.updateImage(adapter.getCount() - 1,
                                    imagePathForUpload, srcBitmap.getWidth(), srcBitmap.getHeight());
                        }
                    }
                    adapter.notifyDataSetChanged();
                    postItemsListView.post(new Runnable() {
                        @Override
                        public void run() {
                            if (adapter.getCount() - count >= 0) {
                                postItemsListView.setSelection(adapter.getCount() - count + 1);
                            }
                        }
                    });
                }
            } else if (requestCode == INTENT_REQUEST_CODE_SELECT_VODEO) {
                if (data != null) {
                    final int count = data.getIntExtra("selected_video_count", 0);
                    for (int i=0; i<count; i++) {
                        String videoPath = data.getStringExtra("selected_video_" + i);
                        int width = data.getIntExtra("selected_video_width_" + i, 0);
                        int height = data.getIntExtra("selected_video_height_" + i, 0);
                        if (canAppend()) {
                            adapter.appendRow();
                        }
                        adapter.updateVideo(adapter.getCount() - 1, videoPath, null, width, height);
                    }
                    adapter.notifyDataSetChanged();
                    postItemsListView.post(new Runnable() {
                        @Override
                        public void run() {
                            if (adapter.getCount() - count >= 0) {
                                postItemsListView.setSelection(adapter.getCount() - count + 1);
                            }
                        }
                    });
                }
            } else if (requestCode == INTENT_REQUEST_CODE_RECORD_SCREEN) {
                if (data != null && data.getData() != null) {
                    croppedVideoUri = data.getData();
                    if (canAppend()) {
                        adapter.appendRow();
                    }
                    String videoPath = croppedVideoUri.getPath();
                    String gameName = null;
                    if (data.hasExtra("game_name")) {
                        gameName = data.getStringExtra("game_name");
                    }
                    int width = data.getIntExtra("video_width", 0);
                    int height = data.getIntExtra("video_height", 0);
                    adapter.updateVideo(adapter.getCount() - 1, videoPath, gameName, width, height);
                    adapter.notifyDataSetChanged();
                    postItemsListView.post(new Runnable() {
                        @Override
                        public void run() {
                            if (adapter.getCount() - 1 >= 0) {
                                postItemsListView.setSelection(adapter.getCount() - 1);
                            }
                        }
                    });
                }
            } else if (requestCode == INTENT_REQUEST_CODE_CUT_VIDEO) {
                if (data != null && data.getData() != null) {
                    croppedVideoUri = data.getData();
                    String videoPath = croppedVideoUri.getPath();
                    adapter.updateVideo(editVideoAtPosition, videoPath, null, 0, 0);
                    adapter.notifyDataSetChanged();
                    editVideoAtPosition = -1;
                }
            }
        }
    }

    JSONObject draft;
    File draftFile;
    File draftDir;
    void saveDraft() {
        if (adapter == null) return;
        try {
            if (draftFile != null) {
                String JSON = YTWHelper.readTextContent(Uri.parse(draftFile.getAbsolutePath()));
                if (JSON != null)
                    draft = new JSONObject(JSON);
            }
            if (draft == null) {
                draft = new JSONObject();
            }

            if (hasTitle) {
                assert titleEditor != null && titleEditor.getText() != null;
                String title = titleEditor.getText().toString();
                draft.put("title", title);
            }

            JSONArray sections = new JSONArray();

            for (int i=0; i<adapter.getCount(); i++) {
                JSONObject section = new JSONObject();

                boolean hasData = false;

                String text = adapter.getText(i);
                if (text != null) {
                    section.put("text", text);
                    hasData = true;
                }

                String imagePath = adapter.getImagePath(i);
                if (imagePath != null) {
                    section.put("image_src", imagePath);
                    hasData = true;
                }

                String videoPath = adapter.getVideoPath(i);
                if (videoPath != null) {
                    section.put("video_src", videoPath);
                    hasData = true;
                }

                String gameName = adapter.getGameName(i);
                if (gameName != null) {
                    section.put("game_name", gameName);
                }

                Point mediaSize = adapter.getMediaSize(i);
                if (mediaSize != null) {
                    section.put("media_width", mediaSize.x);
                    section.put("media_height", mediaSize.y);
                }

                Integer textStyle = adapter.getTextStyle(i);
                if (textStyle != null && textStyle != 0) {
                    section.put("text_style", textStyle);
                }

                if (hasData)
                    sections.put(section);
            }

            draft.put("sections", sections);

            YTWHelper.writeTextContentToFile(draft.toString(), draftFile);

            uploadMedia();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    void prepareDraftFile() {
        if (draftFile == null) {
            File drafsDir = new File(YTWHelper.dataRootDirectory(0), "Posts");
            if (!drafsDir.exists()) {
                logd("mkdirs " + drafsDir.mkdirs());
            }
            draftFile = new File(drafsDir, YTWHelper.generateRandomFilename("json"));
        }
    }

    void loadPost(final Uri postUri) {
        if (postUri == null) {
            Toast.makeText(this, "No Uri passed", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (!HttpClientUtil.checkConnection(this)) {
            Toast.makeText(this, "没有网络，无法查看文章", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        showProgressDialog();
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void[] params) {
                try {
                    HttpClient httpclient = HttpClientUtil.createInstance();
                    HttpGet httpGet = new HttpGet(postUri.toString());
                    HttpResponse response = httpclient.execute(httpGet);
                    if (response != null) {
                        String content = EntityUtils.toString(response.getEntity());
                        return content;
                    }
                } catch (ClientProtocolException e) {
                } catch (IOException e) {
                }
                return null;
            }

            @Override
            protected void onPostExecute(String content) {
                try {
                    if (content != null) {
                        if (headerFragment != null) {
                            ((PostHeader) headerFragment).setContent(content);
                        }
                        if ((footerFragment != null)) {
                            ((PostFooter) footerFragment).setPostUrl(postUri.toString());
                        }
                        JSONObject jsonObject = new JSONObject(content);
                        if (jsonObject != null) {
                            if (jsonObject.has("content")) {
                                JSONObject contentObj = jsonObject.getJSONObject("content");
                                if (contentObj.has("text")) {
                                    String text = contentObj.getString("text");
                                    try {
                                        JSONArray sections = new JSONArray(text);
                                        if (sections != null) {
                                            loadSections(sections);
                                        }
                                    } catch (JSONException e) {
                                        try {
                                            JSONObject section = new JSONObject();
                                            section.put("text", text);
                                            JSONArray sections = new JSONArray();
                                            sections.put(section);
                                            loadSections(sections);
                                        } catch (JSONException e1) {
                                            e1.printStackTrace();
                                        }
                                    }
                                }
                            }
                        }
                    }
                    hideProgressDialog();
                } catch (JSONException e) {

                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    void loadDraftFromJSON(String JSON) {
        try {
            if (JSON == null) return;
            draft = new JSONObject(JSON);
            if (draft.has("title")) {
                String title = draft.getString("title");
                SpannableString styledText = new SpannableString(title);
                styledText.setSpan(new RelativeSizeSpan(1.2f), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                titleEditor.setText(styledText);
            }
            adapter.removeAll();
            JSONArray sections = draft.getJSONArray("sections");
            loadSections(sections);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    void loadSections(JSONArray sections) {
        try {
            for (int i=0; i<sections.length(); i++) {
                adapter.appendRow();
                JSONObject section = (JSONObject) sections.get(i);
                int width = 0, height = 0;
                if (section.has("media_width") && section.has("media_height")) {
                    width = section.getInt("media_width");
                    height = section.getInt("media_height");
                }
                if (section.has("image_src")) {
                    String imagePath = section.getString("image_src");
                    adapter.updateImage(i, imagePath, width, height);
                }
                if (section.has("video_src")) {
                    String videoPath = section.getString("video_src");
                    String gameName = null;
                    if (section.has("game_name"))
                        gameName = section.getString("game_name");
                    adapter.updateVideo(i, videoPath, gameName, width, height);
                }
                if (section.has("text")) {
                    String text = section.getString("text");
                    adapter.updateText(i, text);
                }
                if (section.has("text_style")) {
                    int textStyle = section.getInt("text_style");
                    adapter.updateTextStyle(i, textStyle);
                }
            }
            adapter.notifyDataSetChanged();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static List<Uri> listDrafts() {
        File drafsDir = new File(YTWHelper.dataRootDirectory(0), "Posts");
        String[] files = drafsDir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                return filename.endsWith(".json");
            }
        });
        List<Uri> uris = new ArrayList<Uri>(files.length);
        for (String file : files) {
            uris.add(Uri.parse(file));
        }
        return uris;
    }

    public static void deleteDraft(Uri draftUri) {
        String draftPath = draftUri.getPath();
        String draftMediaDirPath = draftPath.substring(0, draftPath.length() - 5);
        File draftMediaDir = new File(draftMediaDirPath);
        YTWHelper.delete(draftMediaDir);
        File draftFie = new File(draftPath);
        YTWHelper.delete(draftFie);
    }

    public static boolean isPostContentContainMedia(Uri postJSONFileUri) {
        try {
            String JSON = YTWHelper.readTextContent(postJSONFileUri);
            JSONObject post = new JSONObject(JSON);
            JSONArray sections = post.getJSONArray("sections");
            for (int i=0; i<sections.length(); i++) {
                JSONObject section = (JSONObject) sections.get(i);
                if (section.has("image_src")) {
                    return true;
                }
                if (section.has("video_src")) {
                    return true;
                }
            }
        } catch (JSONException e) {
        }
        return false;
    }

    // read file as json string, used for sending to api, stored in db.
    // image url will be online image url, like yaotouwan://<image_id>
    // video url will be youku video id, like youku://<video_id>
    public static String readPostContentForSend(Uri postJSONFileUri) {
        try {
            String JSON = YTWHelper.readTextContent(postJSONFileUri);
            JSONObject post = new JSONObject(JSON);
            JSONObject urlMap = new JSONObject();
            if (post.has("url_map")) {
                urlMap = post.getJSONObject("url_map");
            }
            JSONArray sections = post.getJSONArray("sections");
            for (int i=0; i<sections.length(); i++) {
                JSONObject section = (JSONObject) sections.get(i);
                if (section.has("image_src")) {
                    String imagePath = section.getString("image_src");
                    String imagePathHash = YTWHelper.md5(imagePath);
                    if (urlMap.has(imagePathHash)) {
                        section.put("image_src", urlMap.get(imagePathHash));
                    } else {
                        section.put("image_src", imagePath);
                    }
                }
                if (section.has("video_src")) {
                    String videoPath = section.getString("video_src");
                    String videoPathHash = YTWHelper.md5(videoPath);
                    if (urlMap.has(videoPathHash)) {
                        section.put("video_src", urlMap.get(videoPathHash));
                        String thumbnailPathHash = videoPathHash + "_thumbnail";
                        if (urlMap.has(thumbnailPathHash)) {
                            section.put("video_thumbnail_src", urlMap.get(thumbnailPathHash));
                        }
                    } else {
                        section.put("video_src", videoPath);
                    }
                }
            }
            post.remove("url_map");
            return post.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void onClickBoldButton(View v) {
        adapter.toggleTextStyle(adapter.editingTextRow,
                PostListViewDataSource.TEXT_STYLE_BOLD);
        adapter.applyStyle(contentEditor,
                contentEditor.getText().toString(),
                adapter.getTextStyle(adapter.editingTextRow));
        setToolbarMode(ToolbarMode_Edit);
    }

    public void onClickItalyButton(View v) {
        adapter.toggleTextStyle(adapter.editingTextRow,
                PostListViewDataSource.TEXT_STYLE_ITALIC);
        adapter.applyStyle(contentEditor,
                contentEditor.getText().toString(),
                adapter.getTextStyle(adapter.editingTextRow));
        setToolbarMode(ToolbarMode_Edit);
    }

    public void onClickQuoteButton(View v) {
        adapter.toggleTextStyle(adapter.editingTextRow,
                PostListViewDataSource.TEXT_STYLE_QUOTE);
        adapter.applyStyle(contentEditor,
                contentEditor.getText().toString(),
                adapter.getTextStyle(adapter.editingTextRow));
        setToolbarMode(ToolbarMode_Edit);
    }

    public void onClickHeadButton(View v) {
        adapter.toggleTextStyle(adapter.editingTextRow,
                PostListViewDataSource.TEXT_STYLE_HEAD);
        adapter.applyStyle(contentEditor,
                contentEditor.getText().toString(),
                adapter.getTextStyle(adapter.editingTextRow));
        setToolbarMode(ToolbarMode_Edit);
    }

    public void onFinishClick(MenuItem menuItem) {
        if (adapter.editingTextRow >= 0) {
            hideSoftKeyboard();
            return;
        }

        if (hasTitle && (titleEditor.getText() == null || titleEditor.getText().length() <= 0)) {
            Toast.makeText(this, R.string.post_title_required, Toast.LENGTH_LONG).show();
            return;
        }

        if (!finishButtonClicked && isSoftKeyboardShown) {
            hideSoftKeyboard();
            finishButtonClicked = true;
            return;
        }
        finishButtonClicked = false;

        saveDraft();

        setResult(RESULT_OK, new Intent().setData(Uri.parse(draftFile.getAbsolutePath())));
        finish();
    }

    private void uploadMedia() {
//        if (true) return;
        if (adapter.hasData()) {
            if (isUploadingMedia()) {
                Intent intent = new Intent(UploadPostMediaService.ACTION_POST_MEDIA_UPDATED);
                intent.setData(Uri.parse(draftFile.getAbsolutePath()));
                sendBroadcast(intent);
            } else {
                Intent intent = new Intent(this, UploadPostMediaService.class);
                intent.setData(Uri.parse(draftFile.getAbsolutePath()));
                startService(intent);
            }
        }
    }

    private boolean isUploadingMedia() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (UploadPostMediaService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    String saveImage(Bitmap srcBitmap) {
        String dstFile = YTWHelper.prepareFilePathForImageSaveWithDraftUri(
                Uri.parse(draftFile.getAbsolutePath()));
        OutputStream os = null;
        try {
            os = new BufferedOutputStream(new FileOutputStream(dstFile));
            if (srcBitmap.compress(Bitmap.CompressFormat.JPEG, 92, os)) {
                return dstFile;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    String[] getColumnNames() {
        return new String[]{"_id", "text", "image_src", "video_src", "text_style"};
    }

    public void onClickAppendTextButton(View view) {
        if (canAppend()) {
            adapter.appendRow();
        }
        adapter.editingTextRow = adapter.getCount() - 1;
        adapter.notifyDataSetChanged();
        postItemsListView.setSelectionFromTop(adapter.editingTextRow + 1, 100);
        adapter.click(adapter.editingTextRow, null);
    }

    boolean canAppend() {
        if (adapter.getCount() <= 0)
            return true;
        return adapter.hasDataAtRow(adapter.getCount() - 1);
    }

    public void onClickAppendPhotoButton(View view) {
        Intent intent = new Intent(this, PhotoAlbum.class);
        startActivityForResult(intent, INTENT_REQUEST_CODE_SELECT_PHOTO);
    }

    public void onClickAppendVideoButton(View view) {
        List<ActionSheetItem> items = new ArrayList<ActionSheetItem>();
        items.add(new ActionSheetItem(getString(R.string.post_select_section_type_title_record_game),
                new ActionSheetItem.ActionSheetItemOnClickListener() {
                    @Override
                    public void onClick() {
                        if (!YTWHelper.isFBCanRW() && !YTWHelper.chmodFB()) {
                            Toast.makeText(PostActivity.this, R.string.root_permission_needed, Toast.LENGTH_LONG).show();
                            return;
                        }
                        YTWHelper.stopQihoo();
                        // todo move method to better place
                        SRecorderService.rmIndicatorFile();
                        SelectGameActivity.preLoadGames = gamesInstalled;
                        Intent intent = new Intent(PostActivity.this, SelectGameActivity.class);
                        intent.setData(Uri.parse(draftFile.getAbsolutePath()));
                        startActivityForResult(intent, INTENT_REQUEST_CODE_RECORD_SCREEN);
                    }
                }));
        items.add(new ActionSheetItem(getString(R.string.post_select_section_type_title_select_video),
                new ActionSheetItem.ActionSheetItemOnClickListener() {
                    @Override
                    public void onClick() {
                        Intent intent = new Intent(PostActivity.this, PhotoAlbum.class);
                        intent.putExtra("video", true);
                        startActivityForResult(intent, INTENT_REQUEST_CODE_SELECT_VODEO);
                    }
                }
        ));
        ActionSheet.showWithItems(this, items);
    }

    void preloadGameList() {
        new AppPackageHelper().loadGames(this, new AppPackageHelper.AppPackageHelperDelegate() {
            @Override
            public void onComplete(List<AppPackageHelper.Game> games) {
                gamesInstalled = games;
            }
        });
    }

    private static final int ToolbarMode_Normal = 1;
    private static final int ToolbarMode_Delete = 2;
    private static final int ToolbarMode_Edit = 3;
    void setToolbarMode(int toolbarMode) {
        if (toolbarMode == ToolbarMode_Delete) {
            hideView(R.id.post_toolbar_button_append_text);
            hideView(R.id.post_toolbar_button_append_image);
            hideView(R.id.post_toolbar_button_append_video);

            showView(R.id.post_toolbar_button_delete);

            hideView(R.id.post_toolbar_button_style_head);
            hideView(R.id.post_toolbar_button_style_bold);
            hideView(R.id.post_toolbar_button_style_italy);
            hideView(R.id.post_toolbar_button_style_quote);
        } else
        if (toolbarMode == ToolbarMode_Normal) {
            showView(R.id.post_toolbar_button_append_text);
            showView(R.id.post_toolbar_button_append_image);
            showView(R.id.post_toolbar_button_append_video);

            hideView(R.id.post_toolbar_button_delete);

            hideView(R.id.post_toolbar_button_style_head);
            hideView(R.id.post_toolbar_button_style_bold);
            hideView(R.id.post_toolbar_button_style_italy);
            hideView(R.id.post_toolbar_button_style_quote);
        } else
        if (toolbarMode == ToolbarMode_Edit) {
            hideView(R.id.post_toolbar_button_append_text);
            hideView(R.id.post_toolbar_button_append_image);
            hideView(R.id.post_toolbar_button_append_video);

            hideView(R.id.post_toolbar_button_delete);

            showView(R.id.post_toolbar_button_style_head);
            showView(R.id.post_toolbar_button_style_bold);
            showView(R.id.post_toolbar_button_style_italy);
            showView(R.id.post_toolbar_button_style_quote);

            findViewById(R.id.post_toolbar_button_style_head).setSelected(false);
            findViewById(R.id.post_toolbar_button_style_bold).setSelected(false);
            findViewById(R.id.post_toolbar_button_style_italy).setSelected(false);
            findViewById(R.id.post_toolbar_button_style_quote).setSelected(false);

            if (adapter.editingTextRow >= 0) {
                int style = adapter.getTextStyle(adapter.editingTextRow);
                switch (style) {
                    case PostListViewDataSource.TEXT_STYLE_HEAD:
                        findViewById(R.id.post_toolbar_button_style_head).setSelected(true);
                        break;
                    case PostListViewDataSource.TEXT_STYLE_BOLD:
                        findViewById(R.id.post_toolbar_button_style_bold).setSelected(true);
                        break;
                    case PostListViewDataSource.TEXT_STYLE_ITALIC:
                        findViewById(R.id.post_toolbar_button_style_italy).setSelected(true);
                        break;
                    case PostListViewDataSource.TEXT_STYLE_QUOTE:
                        findViewById(R.id.post_toolbar_button_style_quote).setSelected(true);
                        break;
                    default:
                        break;
                }
            }
        }
    }

    private class PostListViewDataSource
            extends SimpleDragSortCursorAdapter
            implements AbsListView.OnScrollListener {

        PostCursor cursor;
        static final int id_col_idx = 0;
        static final int text_col_idx = 1;
        static final int image_path_col_idx = 2;
        static final int video_path_col_idx = 3;
        static final int text_style_col_idx = 4;
        static final int game_name_col_idx = 5;
        static final int media_width_col_idx = 6;
        static final int media_height_col_idx = 7;

        static final int TEXT_STYLE_HEAD = 1;
        static final int TEXT_STYLE_BOLD = 2;
        static final int TEXT_STYLE_ITALIC = 3;
        static final int TEXT_STYLE_QUOTE = 4;

        boolean animateToShow;
        int draggingRow;
        int editingTextRow = -1;
        boolean deleting;

        public PostListViewDataSource() {
            super(PostActivity.this, R.layout.post_item, null,
                    getColumnNames(),
                    new int[]{R.id.post_item_text_readonly, R.id.post_item_image}, 0);

            cursor = new PostCursor(getColumnNames());
            appendRow();
            changeCursor(cursor);
        }

        // 如果最后一行内容为空，则添加失败并返回false
        void appendRow() {
            cursor.addRow(0, null, null, null, 0, null, 0, 0);
        }

        void insertRow(int pos) {
            cursor.insertRow(pos, 0, null, null, null, 0, null, 0, 0);
        }

        void updateImage(int pos, String imagePath, int width, int height) {
            cursor.updateRowAtColumn(pos, image_path_col_idx, imagePath);
            if (width > 0 && height > 0)
                updateMediaSize(pos, width, height);
        }

        void updateText(int pos, String text) {
            cursor.updateRowAtColumn(pos, text_col_idx, text);
        }

        void updateVideo(int pos, String videoPath, String gameName, int width, int height) {
            cursor.updateRowAtColumn(pos, video_path_col_idx, videoPath);
            if (gameName != null)
                cursor.updateRowAtColumn(pos, game_name_col_idx, gameName);
            if (width > 0 && height > 0)
                updateMediaSize(pos, width, height);
        }

        void updateTextStyle(int pos, int style) {
            cursor.updateRowAtColumn(pos, text_style_col_idx, style);
        }

        private void updateMediaSize(int pos, int width, int height) {
            cursor.updateRowAtColumn(pos, media_width_col_idx, width);
            cursor.updateRowAtColumn(pos, media_height_col_idx, height);
        }

        boolean toggleTextStyle(int pos, int style) {
            Integer oldStyle = (Integer) getTextStyle(pos);
            if (oldStyle == style) {
                cursor.updateRowAtColumn(pos, text_style_col_idx, 0);
                return false;
            } else {
                cursor.updateRowAtColumn(pos, text_style_col_idx, style);
                return true;
            }
        }

        void applyStyle(View view, String text, int style) {
            if (text == null) {
                if (view instanceof TextView) {
                    ((TextView) view).setText(text);
                } else if (view instanceof EditText) {
                    ((EditText) view).setText(text);
                }
                return;
            }

            SpannableString styledText = new SpannableString(text);

            styledText.setSpan(new TypefaceSpan("serif"), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (style == TEXT_STYLE_HEAD) {
                styledText.setSpan(new RelativeSizeSpan(1.3f), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (style == TEXT_STYLE_BOLD) {
                styledText.setSpan(new StyleSpan(Typeface.BOLD), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                styledText.setSpan(new TypefaceSpan("monospace"), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (style == TEXT_STYLE_ITALIC) {
                styledText.setSpan(new StyleSpan(Typeface.ITALIC), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                styledText.setSpan(new TypefaceSpan("monospace"), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (style == TEXT_STYLE_QUOTE) {
                styledText.setSpan(new QuoteSpan(Color.GRAY), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            if (view instanceof TextView) {
                ((TextView) view).setText(styledText);
                ((TextView) view).setMovementMethod(LinkMovementMethod.getInstance());
            } else if (view instanceof EditText) {
                ((EditText) view).setText(styledText);
                ((TextView) view).setMovementMethod(LinkMovementMethod.getInstance());
            }
        }

        void removeRow(int pos) {
            cursor.removeRow(pos);
        }

        void removeAll() {
            while (cursor.getCount() > 0) {
                cursor.removeRow(0);
            }
        }

        boolean hasData() {
            for (int i=0; i<cursor.getCount(); i++) {
                if (hasDataAtRow(i)) return true;
            }
            return false;
        }

        boolean hasDataAtRow(int row) {
            if (!cursor.isNull(row, text_col_idx)
                    && getText(row).length() > 0) return true;
            if (!cursor.isNull(row, image_path_col_idx)) return true;
            if (!cursor.isNull(row, video_path_col_idx)) return true;
            return false;
        }

        @Override
        public void notifyDataSetChanged() {
            saveDraft();
            super.notifyDataSetChanged();
        }

        String getText(int row) {
            return (String) cursor.getValue(row, text_col_idx);
        }

        String getImagePath(int row) {
            return (String) cursor.getValue(row, image_path_col_idx);
        }

        String getVideoPath(int row) {
            return (String) cursor.getValue(row, video_path_col_idx);
        }

        Integer getTextStyle(int row) {
            return (Integer) cursor.getValue(row, text_style_col_idx);
        }

        String getGameName(int row) {
            return (String) cursor.getValue(row, game_name_col_idx);
        }

        Point getMediaSize(int row) {
            int width = (Integer) (cursor.getValue(row, media_width_col_idx));
            int height = (Integer) (cursor.getValue(row, media_height_col_idx));
            if (width > 0 && height > 0)
                return new Point(width, height);
            return null;
        }

        @Override
        public void bindView(final View rowView, Context context, Cursor cursor) {
            if (showKeyboard4EditingTitle) {
                return;
            }
            final ReadText textView = (ReadText) rowView.findViewById(R.id.post_item_text_readonly);
            final int position = cursor.getPosition();
            final String text = cursor.getString(text_col_idx);
            final View dragHandle = rowView.findViewById(R.id.drag_handle);
            final String imagePath = cursor.getString(image_path_col_idx);
            final String videoPath = cursor.getString(video_path_col_idx);
            ImageButton playBtn = (ImageButton) rowView.findViewById(R.id.post_item_video_play_btn);
            playBtn.setEnabled(false);
            final CachedImageButton previewImageView = (CachedImageButton) rowView.findViewById(R.id.post_item_image);
            previewImageView.setEnabled(false);
            final ViewGroup previewGroup = (ViewGroup) rowView.findViewById(R.id.post_item_preview);

            if (imagePath != null) {
                showView(previewGroup);
                hideView(playBtn);
                previewImageView.setBackgroundColor(Color.WHITE);
                previewImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                previewImageView.setImageWithPath(imagePath,
                        postItemsListView.getWidth(), scrolling, 0);
                Point imgSize = getMediaSize(position);
                if (imgSize != null) {
                    if (imgSize.x > imgSize.y * 2) {
                        imgSize.x = imgSize.y * 2;
                    } else if (imgSize.y > imgSize.x) {
                        imgSize.y = imgSize.x;
                    }
                    setViewHeight(previewImageView,
                            postItemsListView.getWidth() * imgSize.y / imgSize.x);
                }
            } else if (videoPath != null) {
                showView(previewGroup);
                showView(playBtn);

                previewImageView.setBackgroundColor(Color.BLACK);
                previewImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                previewImageView.setImageWithVideoPath(videoPath,
                        MediaStore.Video.Thumbnails.FULL_SCREEN_KIND, scrolling, 0);
                setViewHeight(previewImageView, postItemsListView.getWidth() * 3 / 4);
            } else {
                hideView(previewGroup);
            }

            int minLines = 0;
            if (imagePath != null) {
                textView.setHint(R.string.post_image_desc_hint);
            } else if (videoPath != null) {
                textView.setHint(R.string.post_video_desc_hint);
            } else {
                if (getCount() == 1) {
                    minLines = 5;
                }
                textView.setHint(R.string.post_section_hint);
            }
            textView.setMinLines(minLines);
            final Integer style = (Integer) getTextStyle(position);
            if (text != null && text.length() > 300) {
                String sum = text.substring(0, 200)
                        + " … " + text.substring(text.length()-100, text.length());
                applyStyle(textView, sum, style);
            } else {
                applyStyle(textView, text, style);
            }
            textView.post(new Runnable() {
                @Override
                public void run() {
                    int height = textView.getMeasuredHeight();
                    if (previewGroup.getVisibility() == View.VISIBLE) {
                        height += previewImageView.getHeight() + dpToPx(10);
                    }
                    if (height > 0) {
                        setViewHeight(dragHandle, height);
                    }
                }
            });
        }

        @Override
        public void startDrag(int position) {
            draggingRow = position;
            setToolbarMode(ToolbarMode_Delete);
            hideSoftKeyboard();
        }

        @Override
        public void dragMove(int x, int y) {
            int[] loc = new int[2];
            findViewById(R.id.post_toolbar_button_delete).getLocationInWindow(loc);
            int deleteButtonY = loc[1];
            if (getActionBar() != null) {
                deleteButtonY -= getActionBar().getHeight() + dpToPx(20);
            }
            deleting = y > deleteButtonY;
            postItemsListView.pauseSort = deleting;
            ImageButton imageButton = (ImageButton) findViewById(R.id.post_toolbar_button_delete);
            if (deleting) {
                imageButton.setImageResource(R.drawable.post_tool_bar_icon_delete_active);
            } else {
                imageButton.setImageResource(R.drawable.post_tool_bar_icon_delete);
            }
        }

        @Override
        public void drop(int from, int to) {
            setToolbarMode(ToolbarMode_Normal);
            if (deleting) {
                YTWHelper.confirm(PostActivity.this, getString(R.string.post_item_delete_row), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        removeRow(draggingRow);
                        draggingRow = -1;
                        deleting = false;
                        postItemsListView.pauseSort = false;
                        if (adapter.getCount() == 0) {
                            adapter.appendRow();
                        }
                        notifyDataSetChanged();
                    }
                });
            } else {
                adapter.cursor.move(from, to);
                adapter.notifyDataSetChanged();
                draggingRow = -1;
            }
        }

        @Override
        public void click(final int position, View targetView) {
            if (position < 0) return;
            logd("click " + position + ", target " + targetView);
            if (targetView != null && targetView.getId() == R.id.post_item_preview) { // click on image
                String imagePath = getImagePath(position);
                String videoPath = getVideoPath(position);
                if (imagePath != null) {
                    ImageView imageView = (ImageView) targetView.findViewById(R.id.post_item_image);
                    PreviewImageActivity.placeHolder = imageView.getDrawable();
                    pushActivity(PreviewImageActivity.class, Uri.parse(imagePath));
                } else if (videoPath != null) {
                    editVideoAtPosition = position;
                    Intent intent = new Intent(PostActivity.this, EditVideoActivity.class);
                    intent.setData(Uri.parse(videoPath));
                    intent.putExtra("draft_path", draftFile.getAbsolutePath());
                    startActivityForResult(intent, INTENT_REQUEST_CODE_CUT_VIDEO);
                }
            } else {
                showView(contentEditor);
                String text = getText(position);
                applyStyle(contentEditor, text, getTextStyle(position));
                contentEditor.setSelection(text != null ? text.length() : 0);
                contentEditor.requestFocus();
                showSoftKeyboard(true);
                editingTextRow = position;
                setToolbarMode(ToolbarMode_Edit);
            }
        }

        boolean scrolling;

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            scrolling = scrollState != SCROLL_STATE_IDLE;
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        }
    }

    public interface PostHeader {
        public void setContent(String content);
    }

    public interface PostFooter {
        public void setPostUrl(String postUrl);
    }
}
