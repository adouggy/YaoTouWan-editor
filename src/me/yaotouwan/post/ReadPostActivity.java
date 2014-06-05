package me.yaotouwan.post;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.QuoteSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.*;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.client.ClientProtocolException;
import ch.boye.httpclientandroidlib.client.HttpClient;
import ch.boye.httpclientandroidlib.client.methods.HttpGet;
import ch.boye.httpclientandroidlib.util.EntityUtils;
import me.yaotouwan.R;
import me.yaotouwan.android.util.MyConstants;
import me.yaotouwan.screenrecorder.YoukuUploader;
import me.yaotouwan.util.YTWHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jason on 14-3-18.
 */
public class ReadPostActivity extends BaseActivity {

    ListView postItemsListView;
    PostListViewDataSource adapter;
    String youkuPlayerHTML;
    boolean youkuFullscreen;
    WebView youkuWebView;
    ViewGroup youkuWebViewParent;
    int youkuWebViewIndex = -1;
    Fragment headerFragment;
    Fragment footerFragment;
    JSONArray sections;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.read_post);
        setupActionBar(R.string.post_title);

        Intent intent = getIntent();
        Uri postUri = intent.getData();

        postItemsListView = (ListView) findViewById(R.id.post_items);
        adapter = new PostListViewDataSource();
      

        View headerView = getLayoutInflater().inflate(R.layout.read_post_header, null);
        View footerView = getLayoutInflater().inflate(R.layout.read_post_footer, null);
        postItemsListView.addHeaderView(headerView);
        postItemsListView.addFooterView(footerView);
        
        postItemsListView.setAdapter(adapter);
        postItemsListView.setOnScrollListener(adapter);

//        FragmentTransaction fragmentTransaction =
//                getSupportFragmentManager().beginTransaction();
//        String headerFragmentClassName = getIntent()
//                .getStringExtra("read_post_header_fragment_class_name");
//        String footerFragmentClassName = getIntent()
//                .getStringExtra("read_post_footer_fragment_class_name");
//        try {
//            headerFragment =
//                    (Fragment) Class.forName(headerFragmentClassName).newInstance();
//            footerFragment =
//                    (Fragment) Class.forName(footerFragmentClassName).newInstance();
//            fragmentTransaction.replace(R.id.read_post_header, headerFragment);
//            fragmentTransaction.replace(R.id.read_post_footer, footerFragment);
//        } catch (InstantiationException e) {
//        } catch (IllegalAccessException e) {
//        } catch (ClassNotFoundException e) {
//        }
//        fragmentTransaction.commit();

        loadPost(postUri);
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
        super.onBackPressed();
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
        if (adapter.webViews != null) {
            for (WebView webView : adapter.webViews) {
                webView.loadData("about:blank", "text/html", "UTF-8");
            }
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
                                        sections = new JSONArray(text);
                                        adapter.notifyDataSetChanged();
                                    } catch (JSONException e) {
                                        try {
                                            JSONObject section = new JSONObject();
                                            section.put("text", text);
                                            sections = new JSONArray();
                                            sections.put(section);
                                            adapter.notifyDataSetChanged();
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

    void loadSections(JSONArray sections) {
        this.sections = sections;
    }

    class JavaScriptInterface {
        @JavascriptInterface
        public void onVideoStart(final int position) {
            doTaskOnMainThread(new Runnable() {
                @Override
                public void run() {
                    View rowView = postItemsListView.getChildAt(position + postItemsListView.getHeaderViewsCount() - postItemsListView.getFirstVisiblePosition());
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

    private class PostListViewDataSource extends BaseAdapter implements AbsListView.OnScrollListener {

        List<WebView> webViews;
        LayoutInflater inflater;

        static final int TEXT_STYLE_HEAD = 1;
        static final int TEXT_STYLE_BOLD = 2;
        static final int TEXT_STYLE_ITALIC = 3;
        static final int TEXT_STYLE_QUOTE = 4;

        public PostListViewDataSource() {
            super();
        }

        @Override
        public int getCount() {
            if (sections == null) return 0;
            return sections.length();
        }

        @Override
        public JSONObject getItem(int position) {
            try {
                return sections.getJSONObject(position);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        void applyStyle(TextView textView, String text, int style) {
            if (text == null) {
                textView.setText(text);
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

            textView.setText(styledText);
            textView.setMovementMethod(LinkMovementMethod.getInstance());
        }

        String getText(int row) {
            try {
                return getItem(row).has("text") ? getItem(row).getString("text") : null;
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        String getImagePath(int row) {
            try {
                return getItem(row).has("image_src") ? getItem(row).getString("image_src") : null;
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        String getVideoPath(int row) {
            try {
                return getItem(row).has("video_src") ? getItem(row).getString("video_src") : null;
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        Integer getTextStyle(int row) {
            try {
                return getItem(row).has("text_style") ? getItem(row).getInt("text_style") : 0;
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        Point getMediaSize(int row) {
            try {
                int width = getItem(row).has("media_width") ? getItem(row).getInt("media_width") : 0;
                int height = getItem(row).has("media_height") ? getItem(row).getInt("media_height") : 0;
                if (width > 0 && height > 0)
                    return new Point(width, height);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (inflater == null)
                inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            final View rowView;
            if (convertView == null) {
                rowView = inflater.inflate(R.layout.read_post_item, parent, false);
            } else {
                rowView = convertView;
            }
            final TextView textView = (TextView) rowView.findViewById(R.id.post_item_text_readonly);
            final String text = getText(position);
            final String imagePath = getImagePath(position);
            final String videoPath = getVideoPath(position);

            final CachedImageButton previewImageView = (CachedImageButton) rowView.findViewById(R.id.post_item_image);

            final WebView webView = (WebView) rowView.findViewById(R.id.video_webview);
            if (webView == null) {
                return convertView;
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

            if (imagePath != null) {
                showView(previewImageView);
                hideView(webView);

                if ("yaotouwan".equals(Uri.parse(imagePath).getScheme())) {
                    String imageUrlString = MyConstants.IMAGE_GET_URL + "/" + Uri.parse(imagePath).getHost();
                    previewImageView.setImageWithPath(imageUrlString,
                            postItemsListView.getWidth(), scrolling, 0);
                }
                Point imgSize = getMediaSize(position);
                if (imgSize != null) {
                    if (imgSize.x > imgSize.y * 2) {
                        imgSize.x = imgSize.y * 2;
                    } else if (imgSize.y > imgSize.x * 2) {
                        imgSize.y = imgSize.x * 2;
                    }
                    setViewHeight(previewImageView,
                            postItemsListView.getWidth() * imgSize.y / imgSize.x);
                }
                previewImageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String imagePath = getImagePath(position);
                        String videoPath = getVideoPath(position);
                        if (imagePath != null) {
                            PreviewImageActivity.placeHolder = previewImageView.getDrawable();
                            pushActivity(PreviewImageActivity.class, Uri.parse(imagePath));
                        }
                    }
                });
            } else if (videoPath != null) {
                hideView(previewImageView);
                showView(webView);

                if ("youku".equals(Uri.parse(videoPath).getScheme())) {

                    if (youkuPlayerHTML == null) {
                        youkuPlayerHTML = YTWHelper
                                .readAssertsTextContent(ReadPostActivity.this, "youku_player.html");
                    }
                    String videoTag = youkuPlayerHTML;
                    String videoID = videoPath.replace("youku://", "");
                    videoTag = videoTag.replace("{youku_video_id}", videoID);
                    videoTag = videoTag.replace("{youku_client_id}", YoukuUploader.CLIENT_ID);
                    videoTag = videoTag.replace("{position}", position + "");
                    webView.loadData(videoTag, "text/html", "UTF-8");
                    setViewHeight(webView, postItemsListView.getWidth() * 3 / 4);
                }
            } else {
                hideView(previewImageView);
                hideView(webView);
            }

            textView.setMinLines(0);

            final Integer style = (Integer) getTextStyle(position);
            applyStyle(textView, text, style);
            if (text == null || text.length() == 0) {
                hideView(textView);
            }

            if (imagePath != null || videoPath != null) {
                textView.setTextSize(14);
            } else {
                textView.setTextSize(16);
            }

            return rowView;
        }

        boolean scrolling;

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            scrolling = scrollState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE;
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
