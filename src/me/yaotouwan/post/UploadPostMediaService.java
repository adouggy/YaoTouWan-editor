package me.yaotouwan.post;

import android.app.Service;
import android.content.*;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.client.ClientProtocolException;
import ch.boye.httpclientandroidlib.client.HttpClient;
import ch.boye.httpclientandroidlib.client.methods.HttpPost;
import ch.boye.httpclientandroidlib.entity.mime.HttpMultipartMode;
import ch.boye.httpclientandroidlib.entity.mime.MultipartEntityBuilder;
import ch.boye.httpclientandroidlib.entity.mime.content.InputStreamBody;
import me.yaotouwan.screenrecorder.YoukuUploader;
import me.yaotouwan.util.YTWHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;

/**
 * Created by jason on 14-5-8.
 */
public class UploadPostMediaService extends Service {

    Uri postJSONFileUri;
    boolean mediaUpdated;

    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mMessageReceiver);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        registerReceiver(mMessageReceiver,
                new IntentFilter("post_media_updated"));

        if (intent != null) {
            postJSONFileUri = intent.getData();
            uploadMedia();
        }

        return super.onStartCommand(intent, flags, startId);
    }

    void uploadMedia() {
        assert postJSONFileUri != null;
        mediaUpdated = true;

        new AsyncTask<Integer, Float, Boolean>() {
            @Override
            protected Boolean doInBackground(Integer... params) {
                while (mediaUpdated) {
                    mediaUpdated = false;
                    try {
                        String JSON = YTWHelper.readTextContent(postJSONFileUri);
                        if (JSON == null) return false;
                        JSONObject post = new JSONObject(JSON);
                        JSONArray sections = post.getJSONArray("sections");
                        int totalMediaCount = 0;
                        for (int i=0; i<sections.length(); i++) {
                            JSONObject section = (JSONObject) sections.get(i);
                            if (section.has("image_src") || section.has("video_src")) {
                                totalMediaCount ++;
                            }
                        }
                        JSONObject urlMap = new JSONObject();
                        if (post.has("url_map")) {
                            urlMap = post.getJSONObject("url_map");
                        }
                        int uploadedMediaCount = 0;
                        for (int i=0; i<sections.length(); i++) {
                            JSONObject section = (JSONObject) sections.get(i);
                            boolean didUpload = false;
                            if (section.has("image_src")) {
                                String imagePath = section.getString("image_src");
                                String imagePathHash = YTWHelper.md5(imagePath);
                                if (!urlMap.has(imagePathHash)) {
                                    String imageUrl = uploadImage(imagePath);
                                    if (imageUrl != null) {
                                        urlMap.put(imagePathHash, imageUrl);
                                        didUpload = true;
                                    }
                                }
                                uploadedMediaCount ++;
                            }
                            if (section.has("video_src")) {
                                String videoPath = section.getString("video_src");
                                String videoPathHash = YTWHelper.md5(videoPath);
                                if (!urlMap.has(videoPathHash)) {
                                    String gameName = null;
                                    if (section.has("game_name")) {
                                        gameName = section.getString("game_name");
                                    }
                                    String videoId = uploadVideo(videoPath, gameName);
                                    if (videoId != null) {
                                        urlMap.put(videoPathHash, videoId);
                                        didUpload = true;
                                    }
                                }
                                uploadedMediaCount ++;
                            }
                            if (didUpload) {
                                post.put("url_map", urlMap);
                                if (uploadedMediaCount < totalMediaCount) {
                                    publishProgress(uploadedMediaCount * 1.0f / totalMediaCount);
                                }
                                YTWHelper.writeTextContentToFile(post.toString(),
                                        postJSONFileUri.getPath());
                            }
                            if (mediaUpdated) {
                                break;
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                publishProgress(1.0f);
                return true;
            }

            @Override
            protected void onProgressUpdate(Float... values) {
                float progress = values[0];
                broadcastProgress(values[0]);
                if (progress >= 1)
                    stopSelf();
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    void broadcastProgress(float progress) {
        sendBroadcast(new Intent()
                .setAction("upload_post_media_progress")
                .setDataAndType(postJSONFileUri, "application/json")
                .addCategory(Intent.CATEGORY_DEFAULT)
                .putExtra("progress", progress));
        Log.d(TAG, "send progress broadcast: " + postJSONFileUri + " -> " + progress);
    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (postJSONFileUri.getPath().equals(intent.getData().getPath())) {
                mediaUpdated = true;
            }
        }
    };

    String uploadVideo(String videoPath, String game) {
        if (HttpClientUtil.checkConnection(this)) {
            YoukuUploader uploader = new YoukuUploader();
            try {
                return uploader.uploadVideoFile(videoPath,
                        nameVideoTitleByGameName(game), nameVideoTagsByGameName(game));
            } catch (YoukuUploader.UploadException e) {
                Intent intent = new Intent("upload_post_media_error");
                intent.setData(postJSONFileUri);
                intent.putExtra("error_name", "youku_upload_error");
                intent.putExtra("error_code", e.code);
                intent.putExtra("error_desc", e.description);
                intent.putExtra("video_path", videoPath);
                intent.putExtra("game_name", game);
                sendBroadcast(intent);
            }
        }
        return null;
    }

    String username = null;
    String nameVideoTitleByGameName(String gameName) {
        if (gameName == null)
            gameName = "";
        if (username == null) {
            SharedPreferences pref = getSharedPreferences("yaotouwan_user_info", Context.MODE_APPEND);
            username = pref.getString("userName", "");
        }
        return "摇头玩用户" + username + "录制" + gameName + "游戏视频";
    }

    String nameVideoTagsByGameName(String gameName) {
        String tags = "摇头玩,游戏视频";
        if (gameName != null) {
            tags += "," + gameName;
        }
        return tags;
    }

    static final String TAG = "UploadPostMediaService";
    static final String IMAGE_ADD_URL = "http://115.28.156.104/image/add";
    static final String IMAGE_URL_PREFIX = "yaotouwan://";

    class FileStreamBody extends InputStreamBody {

        File inFile;

        public FileStreamBody(File inFile, String filename) throws FileNotFoundException {
            super(new FileInputStream(inFile), filename);
            this.inFile = inFile;
        }

        @Override
        public long getContentLength() {
            return inFile.length();
        }
    }

    private String uploadImage(String imageFilePath) {
        if (!HttpClientUtil.checkConnection(this))
            return null;
        HttpClient httpclient = HttpClientUtil.createInstance();
        HttpPost httppost = new HttpPost(IMAGE_ADD_URL);
        MultipartEntityBuilder mpEntity = MultipartEntityBuilder.create();
        mpEntity.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        try {
            mpEntity.addPart("image",
                    new FileStreamBody(new File(imageFilePath), "temp.jpg"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }

        httppost.setEntity(mpEntity.build());
        try {
            HttpResponse response = httpclient.execute(httppost);
            if (response != null) {
                InputStream is = response.getEntity().getContent();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                while (reader.ready()) {
                    String line = reader.readLine();
                    if (line != null) {
                        line = line.trim();
                        if (line.startsWith("<h1>")) {
                            String imageId = line.substring(9, 32+9);
                            return IMAGE_URL_PREFIX + imageId;
                        }
                    }
                }
                return null;
            }
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}
