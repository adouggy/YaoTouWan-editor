package me.yaotouwan.post;

import android.app.ActivityManager;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.QuoteSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.*;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.client.ClientProtocolException;
import ch.boye.httpclientandroidlib.client.HttpClient;
import ch.boye.httpclientandroidlib.client.methods.HttpGet;
import ch.boye.httpclientandroidlib.util.EntityUtils;
import com.actionbarsherlock.view.MenuItem;
import com.mobeta.android.dslv.DragSortController;
import com.mobeta.android.dslv.DragSortListView;
import com.mobeta.android.dslv.SimpleDragSortCursorAdapter;
import me.yaotouwan.R;
import me.yaotouwan.android.util.MyConstants;
import me.yaotouwan.screenrecorder.EditVideoActivity;
import me.yaotouwan.screenrecorder.Root;
import me.yaotouwan.screenrecorder.SelectGameActivity;
import me.yaotouwan.screenrecorder.YoukuUploader;
import me.yaotouwan.uicommon.ActionSheet;
import me.yaotouwan.uicommon.ActionSheetItem;
import me.yaotouwan.util.AppPackageHelper;
import me.yaotouwan.util.StreamHelper;
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
    ViewGroup toolbar;
    View editPopMenu;
    int editVideoAtPosition = -1;
    boolean finishButtonClicked; // 为防止键盘显示判断错误导致的点击无效
    List<AppPackageHelper.Game> gamesInstalled;
    boolean appendedText;
    boolean scrollingToEditor;
    String youkuPlayerHTML;
    boolean youkuFullscreen;
    WebView youkuWebView;
    ViewGroup youkuWebViewParent;
    int youkuWebViewIndex = -1;
    Fragment headerFragment;
    Fragment footerFragment;
    boolean titleEditorIsOnFocus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        IntentFilter intentFilter = new IntentFilter("upload_post_media_progress");
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
                logd("has focus title " + hasFocus);
                if (hasFocus) {
                    titleEditorIsOnFocus = hasFocus;
                    postItemsListView.blockLayoutRequests();
                }
            }
        });

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
    }

    private void stopQihoo() {
        Root.CommandResult result = Root.INSTANCE.runCMD(false, "ps");
        if (result.success()) {
            String out = result.stdout;
            String[] lines = out.split("\n");
            for (int i=0; i<lines.length; i++) {
                String line = lines[i];
//                logd(line);
                if (line.contains("com.qihoo")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length > 1) {
                        String pidStr = parts[1];
                        try {
                            int aPid = Integer.parseInt(pidStr);
                            Root.INSTANCE.runCMD(true, "kill -9 " + aPid);
                        } catch (NumberFormatException e) {
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (youkuFullscreen) {
            youkuFullscreen = false;
            getRootViewGroup().removeView(youkuWebView);
            youkuWebViewParent.addView(youkuWebView, youkuWebViewIndex);
            youkuWebView = null;
            youkuWebViewParent = null;
            youkuWebViewIndex = -1;
            exitFullscreen();
            showActionBar();
            return;
        }
        if (adapter.editingTextRow >= 0) {
            adapter.editingTextRow = -1;
            adapter.notifyDataSetChanged();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (adapter.webViews != null) {
            for (WebView webView : adapter.webViews) {
                webView.loadUrl("about:blank");
            }
        }
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
        if (adapter.webViews != null) {
            for (WebView webView : adapter.webViews) {
                webView.loadData("about:blank", "text/html", "UTF-8");
            }
        }
    }

    protected void onKeyboardHide() {
        super.onKeyboardHide();

        titleEditor.clearFocus();

        if (adapter.editingTextRow >= 0) {
            adapter.editingTextRow = -1;
            //把换行符拆成多个段落
            for (int i=0; i<adapter.getCount(); i++) {
                String text = adapter.getText(i);
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
        postItemsListView.unBlockLayoutRequests();
        titleEditorIsOnFocus = false;

        if (finishButtonClicked) {
            onFinishClick(null);
        } else {
            saveDraft();
        }
    }

    @Override
    protected void onKeyboardShow() {
        super.onKeyboardShow();

        if (adapter.editingTextRow >= 0) {
            adapter.notifyDataSetChanged();
            scrollingToEditor = false;
            postItemsListView.setSelectionFromTop(adapter.editingTextRow + 1, 100);
        }
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

            assert titleEditor != null && titleEditor.getText() != null;
            String title = titleEditor.getText().toString();
            draft.put("title", title);

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

    public void onFinishClick(MenuItem menuItem) {
        if (titleEditor.getVisibility() == View.VISIBLE
                && titleEditor.getText() == null || titleEditor.getText().length() <= 0) {
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
                Intent intent = new Intent("post_media_updated");
                intent.setData(Uri.parse(draftFile.getAbsolutePath()));
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
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

    public void onAppendTextClick(View view) {
        if (canAppend()) {
            adapter.appendRow();
        }
        appendedText = true;
        adapter.editingTextRow = adapter.getCount() - 1;
        adapter.notifyDataSetChanged();
        postItemsListView.setSelectionFromTop(adapter.editingTextRow + 1, 100);
    }

    boolean canAppend() {
        if (adapter.getCount() <= 0)
            return true;
        return adapter.hasDataAtRow(adapter.getCount() - 1);
    }

    public void onAppendPhotoClick(View view) {
        Intent intent = new Intent(this, PhotoAlbum.class);
        startActivityForResult(intent, INTENT_REQUEST_CODE_SELECT_PHOTO);
    }

    public void onAppendVideoClick(View view) {
        List<ActionSheetItem> items = new ArrayList<ActionSheetItem>();
        items.add(new ActionSheetItem(getString(R.string.post_select_section_type_title_record_game),
                new ActionSheetItem.ActionSheetItemOnClickListener() {
                    @Override
                    public void onClick() {
                        if (!YTWHelper.isFBCanRW() && !YTWHelper.chmodFB()) {
                            Toast.makeText(PostActivity.this, R.string.root_permission_needed, Toast.LENGTH_LONG).show();
                            return;
                        }
                        stopQihoo();
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

    void setToolbarDeleteMode(boolean deleteMode) {
        if (deleteMode) {
            hideView(R.id.post_toolbar_button_append_text);
            hideView(R.id.post_toolbar_button_append_image);
            hideView(R.id.post_toolbar_button_append_video);
            showView(R.id.post_toolbar_button_delete);
        } else {
            showView(R.id.post_toolbar_button_append_text);
            showView(R.id.post_toolbar_button_append_image);
            showView(R.id.post_toolbar_button_append_video);
            hideView(R.id.post_toolbar_button_delete);
        }
    }

    class JavaScriptInterface {
        @JavascriptInterface
        public void onVideoStart(final int position) {
            doTaskOnMainThread(new Runnable() {
                @Override
                public void run() {
                    View rowView = postItemsListView.getItemViewAtRow(position);
                    if (rowView == null) return;
                    youkuWebView = (WebView) rowView.findViewById(R.id.video_webview);
                    if (youkuWebView == null) return;
                    youkuWebViewParent = (ViewGroup) youkuWebView.getParent();
                    if (youkuWebViewParent == null) return;
                    youkuWebViewIndex = youkuWebViewParent.indexOfChild(youkuWebView);
                    youkuWebViewParent.removeView(youkuWebView);
                    setViewSize(youkuWebView, ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT);
                    getRootViewGroup().addView(youkuWebView);
                    hideActionBar();
                    enterFullscreen();
                    youkuFullscreen = true;
                }
            });
        }
    }

    private class PostListViewDataSource
            extends SimpleDragSortCursorAdapter
            implements AbsListView.OnScrollListener, TextWatcher {

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

        boolean editModeLastTime;
        boolean animateToShow;
        int draggingRow;
        int editingTextRow = -1;
        View editTargetView;
        boolean deleting;
        List<WebView> webViews;

        public PostListViewDataSource() {
            super(PostActivity.this, R.layout.post_item, null,
                    getColumnNames(),
                    new int[]{R.id.post_item_text, R.id.post_item_image}, 0);

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
            animateToShow = (postItemsListView.editMode != editModeLastTime);
            editModeLastTime = postItemsListView.editMode;
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
            final TextEditor textEditor = (TextEditor) rowView.findViewById(R.id.post_item_text);

            final ReadText textView = (ReadText) rowView.findViewById(R.id.post_item_text_readonly);

            final int position = cursor.getPosition();
            if (titleEditorIsOnFocus) return;

            final String text = cursor.getString(text_col_idx);

            final View dragHandle = rowView.findViewById(R.id.drag_handle);
            showView(dragHandle);

            final String imagePath = cursor.getString(image_path_col_idx);
            final String videoPath = cursor.getString(video_path_col_idx);

            ImageButton playBtn = (ImageButton) rowView.findViewById(R.id.post_item_video_play_btn);
            playBtn.setEnabled(false);

            final CachedImageButton previewImageView = (CachedImageButton) rowView.findViewById(R.id.post_item_image);
            previewImageView.setEnabled(false);

            ViewGroup previewGroup = (ViewGroup) rowView.findViewById(R.id.post_item_preview);
            final WebView webView = (WebView) rowView.findViewById(R.id.video_webview);
            if (webView == null) {
                return;
            }
            if (webViews == null || !webViews.contains(webView)) {
                if (webViews == null) {
                    webViews = new ArrayList<WebView>(3);
                }
                webViews.add(webView);
                webView.getSettings().setJavaScriptEnabled(true);
                webView.getSettings().setAppCacheEnabled(true);
                webView.addJavascriptInterface(new JavaScriptInterface(), "Android");
            }
            if (postItemsListView.editingPosition == position) {
                // todo deprecated
                textView.setBackgroundDrawable(new EditModeBGDrawable(PostActivity.this, 0));
            } else {
                textView.setBackgroundColor(Color.WHITE);
            }

            if (imagePath != null) {
                showView(previewGroup);
                hideView(playBtn);
                hideView(webView);

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
                if ("youku".equals(Uri.parse(videoPath).getScheme())) {
                    hideView(previewGroup);
                    showView(webView);
                    if (youkuPlayerHTML == null) {
                        youkuPlayerHTML = YTWHelper
                                .readAssertsTextContent(PostActivity.this, "youku_player.html");
                    }
                    String videoTag = youkuPlayerHTML;
                    String videoID = videoPath.replace("youku://", "");
                    videoTag = videoTag.replace("{youku_video_id}", videoID);
                    videoTag = videoTag.replace("{youku_client_id}", YoukuUploader.CLIENT_ID);
                    videoTag = videoTag.replace("{position}", position + "");
                    webView.loadData(videoTag, "text/html", "UTF-8");
                    setViewHeight(webView, postItemsListView.getWidth() * 3 / 4);
                } else {
                    showView(previewGroup);
                    showView(playBtn);
                    hideView(webView);

                    previewImageView.setBackgroundColor(Color.BLACK);
                    previewImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    previewImageView.setImageWithVideoPath(videoPath,
                            MediaStore.Video.Thumbnails.FULL_SCREEN_KIND, scrolling, 0);
                    setViewHeight(previewImageView, postItemsListView.getWidth() * 3 / 4);
                }
            } else {
                hideView(previewGroup);
                hideView(webView);
            }

            int minLines = 0;
            if (imagePath != null) {
                textEditor.setHint(R.string.post_image_desc_hint);
                textView.setHint(R.string.post_image_desc_hint);
            } else if (videoPath != null) {
                textEditor.setHint(R.string.post_video_desc_hint);
                textView.setHint(R.string.post_video_desc_hint);
            } else {
                if (getCount() == 1) {
                    minLines = 5;
                }
                textEditor.setHint(R.string.post_section_hint);
                textView.setHint(R.string.post_section_hint);
            }
            textEditor.setMinLines(minLines);
            textView.setMinLines(minLines);

            if (editingTextRow == position) {
                hideView(dragHandle);
                if (!isSoftKeyboardShown) {
                    textEditor.setFocuable(true);
                    textEditor.requestFocus();
                    showSoftKeyboard(true);
                }
            } else if (editingTextRow >= 0) {
                showView(dragHandle);
                textEditor.removeTextChangedListener(this);
                textEditor.setFocuable(false);
            } else {
                showView(dragHandle);
                textEditor.removeTextChangedListener(this);
            }
            if (editingTextRow == position) {
                showView(textEditor);
                hideView(textView);
            } else {
                hideView(textEditor);
                showView(textView);
            }
            textEditor.removeTextChangedListener(adapter);
            final Integer style = (Integer) getTextStyle(position);
            if (editingTextRow == position) {
                applyStyle(textEditor, text, style);
            } else {
                if (text != null && text.length() > 300) {
                    String sum = text.substring(0, 200)
                            + " … " + text.substring(text.length()-100, text.length());
                    applyStyle(textView, sum, style);
                } else {
                    applyStyle(textView, text, style);
                }
            }
            if (editingTextRow == position) {
                textEditor.addTextChangedListener(adapter);
                textEditor.setSelection(text != null ? text.length() : 0);
            }

            if (editingTextRow == position) {
                textEditor.post(new Runnable() {
                    @Override
                    public void run() {
                        int height = textEditor.getMeasuredHeight();
                        if (height > 0) {
                            setViewHeight(dragHandle, height);
                        }
                    }
                });
            } else {
                textView.post(new Runnable() {
                    @Override
                    public void run() {
                        int height = textView.getMeasuredHeight();
                        if (height > 0) {
                            setViewHeight(dragHandle, height);
                        }
                    }
                });
            }
        }

        @Override
        public void startDrag(int position) {
            if (postItemsListView.editingPosition >= 0) {
                for (int i=0; i<postItemsListView.getChildCount(); i++) {
                    View rowView = postItemsListView.getChildAt(i);
                    if (rowView != null) {
                        View textView = rowView.findViewById(R.id.post_item_text_readonly);
                        if (textView != null) {
                            textView.setBackgroundColor(Color.WHITE);
                        }
                    }
                }
                if (editPopMenu != null && editPopMenu.getParent() != null) {
                    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                    wm.removeView(editPopMenu);
                }
                postItemsListView.editingPosition = -1;
                postItemsListView.editMode = false;
                editTargetView = null;
            }
            draggingRow = position;
            setToolbarDeleteMode(true);
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
            setToolbarDeleteMode(false);
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
            if (editingTextRow >= 0) {
                hideSoftKeyboard();
                return;
            }
            if (targetView != null && targetView.getId() == R.id.post_item_preview) { // click on image
                String imagePath = getImagePath(position);
                String videoPath = getVideoPath(position);
                if (imagePath != null || videoPath != null) {
                    postItemsListView.editMode = false;
                    postItemsListView.editingPosition = -1;
                    editTargetView = null;
                }
                if (imagePath != null) {
                    ImageView imageView = (ImageView) targetView.findViewById(R.id.post_item_image);
                    PreviewImageActivity.placeHolder = imageView.getDrawable();
                    pushActivity(PreviewImageActivity.class, Uri.parse(imagePath));
                } else if (videoPath != null) {
                    editVideoAtPosition = position;
                    Intent intent = new Intent(PostActivity.this, EditVideoActivity.class);
                    intent.setData(Uri.parse(videoPath));
                    intent.putExtra("draft_path", draftFile.getAbsolutePath());
                    intent.putExtra("readonly", false);
                    startActivityForResult(intent, INTENT_REQUEST_CODE_CUT_VIDEO);
                }
            } else if (targetView == null) {
                int pos = position + postItemsListView.getHeaderViewsCount() - postItemsListView.getFirstVisiblePosition();
                View rowView = postItemsListView.getChildAt(pos);
                if (rowView == null) return;
            } else if (postItemsListView.editingPosition == position) {
                postItemsListView.editingPosition = -1;
                postItemsListView.editMode = false;
                editTargetView = null;
                if (editPopMenu != null && editPopMenu.getParent() != null) {
                    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                    wm.removeView(editPopMenu);
                }
            } else {
                if (adapter.getCount() == 1) {
                    doubleClick(position, targetView);
                    return;
                }

                postItemsListView.makeFloatViewForEditRow(position);

                WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                WindowManager.LayoutParams wlp = new WindowManager.LayoutParams();
                wlp.gravity = Gravity.TOP;
                wlp.width = dpToPx(230);
                wlp.height = dpToPx(65);
                wlp.format = PixelFormat.RGBA_8888;
                wlp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

                int[] loc = new int[2];
                targetView.getLocationInWindow(loc);
                wlp.y = loc[1] - dpToPx(80);

                if (editPopMenu == null) {
                    editPopMenu = getLayoutInflater().inflate(R.layout.post_item_edit_pop_menu, null);
                } else if (editPopMenu.getParent() != null) {
                    wm.removeView(editPopMenu);
                }
                wm.addView(editPopMenu, wlp);

                final View editBtn = (View) editPopMenu.findViewById(R.id.edit_btn).getParent();
                final View headBtn = (View) editPopMenu.findViewById(R.id.head_btn).getParent();
                final View boldBtn = (View) editPopMenu.findViewById(R.id.bold_btn).getParent();
                final View italicBtn = (View) editPopMenu.findViewById(R.id.italic_btn).getParent();
                final View quoteBtn = (View) editPopMenu.findViewById(R.id.quote_btn).getParent();
                assert editBtn != null;
                assert headBtn != null;
                assert boldBtn != null;
                assert italicBtn != null;
                assert quoteBtn != null;

                Integer style = (Integer) getTextStyle(position);
                headBtn.setSelected(style == TEXT_STYLE_HEAD);
                boldBtn.setSelected(style == TEXT_STYLE_BOLD);
                italicBtn.setSelected(style == TEXT_STYLE_ITALIC);
                quoteBtn.setSelected(style == TEXT_STYLE_QUOTE);

                View.OnClickListener listener = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (v == editBtn) {
                            doEditTextOnPosition(position);
                        } else {
                            headBtn.setSelected(false);
                            boldBtn.setSelected(false);
                            italicBtn.setSelected(false);
                            quoteBtn.setSelected(false);
                            if (v == headBtn) {
                                v.setSelected(toggleTextStyle(position, TEXT_STYLE_HEAD));
                            } else if (v == boldBtn) {
                                v.setSelected(toggleTextStyle(position, TEXT_STYLE_BOLD));
                            } else if (v == italicBtn) {
                                v.setSelected(toggleTextStyle(position, TEXT_STYLE_ITALIC));
                            } else if (v == quoteBtn) {
                                v.setSelected(toggleTextStyle(position, TEXT_STYLE_QUOTE));
                            }
                            postItemsListView.editingPosition = -1;
                            postItemsListView.editMode = false;
                            editTargetView = null;
                            if (editPopMenu != null && editPopMenu.getParent() != null) {
                                WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                                wm.removeView(editPopMenu);
                            }
                            notifyDataSetChanged();
                        }
                    }
                };

                editBtn.setOnClickListener(listener);
                headBtn.setOnClickListener(listener);
                boldBtn.setOnClickListener(listener);
                italicBtn.setOnClickListener(listener);
                quoteBtn.setOnClickListener(listener);

                postItemsListView.editingPosition = position;
                postItemsListView.editMode = true;

                editTargetView = targetView;
            }
            notifyDataSetChanged();
        }


        @Override
        public void doubleClick(final int position, View targetView) {
            if (targetView != null && targetView.getId() == R.id.post_item_preview) { // double click on image
                String imagePath = getImagePath(position);
                String videoPath = getVideoPath(position);
                if (imagePath != null) {
                    ImageView imageView = (ImageView) targetView.findViewById(R.id.post_item_image);
                    PreviewImageActivity.placeHolder = imageView.getDrawable();
                    pushActivity(PreviewImageActivity.class, Uri.parse(imagePath));
                } if (videoPath != null) {
                    startActivity(new Intent(PostActivity.this, EditVideoActivity.class)
                            .setData(Uri.parse(videoPath)).putExtra("readonly", false));
                }
            } else { // double click on text
                doEditTextOnPosition(position);
            }
        }

        void doEditTextOnPosition(int position) {
            if (editTargetView != null) {
                editTargetView.setBackgroundColor(Color.WHITE);
                editTargetView = null;
            }
            if (postItemsListView.editMode) {
                postItemsListView.editingPosition = -1;
                postItemsListView.editMode = false;
            }
            if (editPopMenu != null && editPopMenu.getParent() != null) {
                WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                wm.removeView(editPopMenu);
            }
            editingTextRow = position;
            View rowView = postItemsListView.getItemViewAtRow(position);
            if (rowView != null) {
                hideView(rowView.findViewById(R.id.drag_handle));
                postItemsListView.blockLayoutRequests();
                adapter.notifyDataSetChanged();
            }
        }

        boolean scrolling;

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            scrolling = scrollState != SCROLL_STATE_IDLE;

            if (editPopMenu == null || editTargetView == null || postItemsListView.editingPosition < 0) return;

            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (editPopMenu.getParent() != null)
                wm.removeView(editPopMenu);

            if (scrollState == SCROLL_STATE_IDLE) {
                // 不在列表中
                if (postItemsListView.getFirstVisiblePosition() - postItemsListView.getHeaderViewsCount() > postItemsListView.editingPosition) {
                    return;
                }
                if (postItemsListView.getLastVisiblePosition() - postItemsListView.getHeaderViewsCount() < postItemsListView.editingPosition) {
                    return;
                }

                View item = postItemsListView.getChildAt(postItemsListView.editingPosition + postItemsListView.getHeaderViewsCount() - postItemsListView.getFirstVisiblePosition());
                if (item == null) return;
                editTargetView = item.findViewById(R.id.post_item_text);

                WindowManager.LayoutParams params = (WindowManager.LayoutParams) editPopMenu.getLayoutParams();
                assert params != null;
                int[] loc = new int[2];
                editTargetView.getLocationInWindow(loc);

                int listViewLoc = getActionBar() == null ? 0 : getActionBar().getHeight();

                if (loc[1] + editTargetView.getHeight() < listViewLoc + dpToPx(80) + dpToPx(30)) {
                    return;
                }

                params.y = loc[1] - dpToPx(80);

                if (params.y < listViewLoc) {
                    params.y = listViewLoc;
                }

                Point size = new Point();
                wm.getDefaultDisplay().getSize(size);
                if (params.y > size.y - findViewById(R.id.post_toolbar).getHeight() - dpToPx(30)) {
                    return;
                }

                wm.addView(editPopMenu, params);
            }
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (editingTextRow >= 0) {
                String text = s.toString();
                if (text != null && text.length() > 0) {
                    updateText(editingTextRow, s.toString());
                } else {
                    updateText(editingTextRow, null);
                }
            }
        }
    }

    public interface PostHeader {
        public void setContent(String content);
    }

    public interface PostFooter {
        public void setPostUrl(String postUrl);
    }
}
