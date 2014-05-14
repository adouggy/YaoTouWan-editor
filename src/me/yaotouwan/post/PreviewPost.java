package me.yaotouwan.post;

import android.app.Activity;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;
import me.yaotouwan.R;
import me.yaotouwan.screenrecorder.YoukuUploader;
import me.yaotouwan.util.YTWHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by jason on 14-5-14.
 */
public class PreviewPost extends BaseActivity {
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.preview_post);

        Uri postUri = getIntent().getData();
        String HTML = readPostContentAsHTML(postUri);
        if (HTML != null) {
            logd(HTML);
//            WebView webView = (WebView) findViewById(R.id.webview);
//            webView.getSettings().setJavaScriptEnabled(true);
//            webView.loadDataWithBaseURL("", HTML, "text/html", "UTF-8", "");
        }
    }

    public String readPostContentAsHTML(Uri postJSONFileUri) {
        try {
            String JSON = YTWHelper.readTextContent(postJSONFileUri);
            JSONObject post = new JSONObject(JSON);
            String title = "";
            if (post.has("title")) {
                title = post.getString("title");
            }
            StringBuilder sb = new StringBuilder();
            sb.append("<h>" + title + "</h>\n");
            JSONArray sections = post.getJSONArray("sections");
            for (int i=0; i<sections.length(); i++) {
                sb.append("<p>\n");
                JSONObject section = (JSONObject) sections.get(i);
                boolean hasMedia = false;
                if (section.has("image_src")) {
                    String imgTag = "<img src='{image_src}' " +
                            "style='width={image_width}px;height={image_height}px;' />";
                    String imagePath = section.getString("image_src");
                    Uri imageUri = Uri.parse(imagePath);
                    if (imageUri.getScheme() == null
                        || "file".equals(imageUri.getScheme())) {
                        imgTag = imgTag.replace("{image_src}", "file://" + imagePath);
                    } else if ("yaotouwan".equals(imageUri.getScheme())) {
                        imagePath = imagePath.replace("yaotouwan://", "http://115.28.156.104/image/");
                        imgTag = imgTag.replace("{image_src}", imagePath);
                    }
                    int width = 0, height = 0;
                    if (section.has("media_width")) {
                        width = section.getInt("media_width");
                    }
                    if (section.has("media_height")) {
                        height = section.getInt("media_height");
                    }
                    if (width > 0 && height > 0) {
                        Point winSize = getWindowSize();
                        height = winSize.x * height / width;
                        width = winSize.x;
                        if (width > height * 2) {
                            height = width / 2;
                        } else if (height > width * 2) {
                            height = width * 2;
                        }
                        imgTag = imgTag.replace("{image_width}", width + "");
                        imgTag = imgTag.replace("{image_height}", height + "");
                    }
                    sb.append(imgTag);
                    hasMedia = true;
                }
                if (section.has("video_src")) {
                    String videoPath = section.getString("video_src");
                    Uri videoUri = Uri.parse(videoPath);
                    String videoTag = null;
                    if (videoUri.getScheme() == null
                        || "file".equals(videoUri.getScheme())) {
                        videoTag = "<video src='{video_src}' " +
                                "style='width:{video_width}px;height:{video_height}px;' />";
                        videoTag = videoTag.replace("{video_src}", "file://" + videoPath);
                    } else if ("youku".equals(videoUri.getScheme())) {
                        videoTag =
                                "<div id='youkuplayer_{youku_video_id}' " +
                                        "style='width:{video_width}px;height:{video_height}px;'></div>\n" +
                                        "<script type='text/javascript' src='http://player.youku.com/jsapi'>\n" +
                                        "player = new YKU.Player('youkuplayer_{youku_video_id}',{\n" +
                                        "styleid: '0',\n" +
                                        "client_id: '{youku_client_id}',\n" +
                                        "vid: '{youku_video_id}'\n" +
                                        "});\n" +
                                        "</script>";
                        String videoID = videoPath.replace("yaotouwan://", "");
                        videoTag = videoTag.replaceAll("\\{youku_video_id\\}", videoID);
                        videoTag = videoTag.replace("{youku_client_id}", YoukuUploader.CLIENT_ID);
                    } else {
                        continue;
                    }

                    int width = 0, height = 0;
                    if (section.has("media_width")) {
                        width = section.getInt("media_width");
                    }
                    if (section.has("media_height")) {
                        height = section.getInt("media_height");
                    }
                    if (width > 0 && height > 0) {
                        Point winSize = getWindowSize();
                        height = winSize.x * height / width;
                        width = winSize.x;
                        if (width > height * 2) {
                            height = width / 2;
                        } else if (height > width * 2) {
                            height = width * 2;
                        }
                        videoTag = videoTag.replace("{video_width}", width + "");
                        videoTag = videoTag.replace("{video_height}", height + "");
                    }
                    sb.append(videoTag);
                    hasMedia = true;
                }

                if (section.has("text")) {
                    String text = section.getString("text");
                    int textStyle = 0;
                    if (section.has("text_style")) {
                        textStyle = section.getInt("text_style");
                    }
                    if (!hasMedia && textStyle == 0) {
                        sb.append(text);
                    } else if (textStyle == 1) {
                        sb.append("<h>" + text + "</h>");
                    } else if (textStyle == 2) {
                        sb.append("<b>" + text + "</b>");
                    } else if (textStyle == 3) {
                        sb.append("<i>" + text + "</i>");
                    } else if (textStyle == 4) {
                        sb.append("<blockquote>" + text + "</blockquote>");
                    }
                }
                sb.append("\n</p>\n");
            }
            return sb.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }
}