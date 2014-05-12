package me.yaotouwan.post;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.NetworkOnMainThreadException;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import ch.boye.httpclientandroidlib.HttpEntity;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.client.ClientProtocolException;
import ch.boye.httpclientandroidlib.client.HttpClient;
import ch.boye.httpclientandroidlib.client.methods.HttpPost;
import ch.boye.httpclientandroidlib.entity.ContentType;
import ch.boye.httpclientandroidlib.entity.mime.HttpMultipartMode;
import ch.boye.httpclientandroidlib.entity.mime.MultipartEntityBuilder;
import ch.boye.httpclientandroidlib.entity.mime.content.ByteArrayBody;
import ch.boye.httpclientandroidlib.entity.mime.content.ContentBody;
import ch.boye.httpclientandroidlib.entity.mime.content.InputStreamBody;
import ch.boye.httpclientandroidlib.impl.client.CloseableHttpClient;
import ch.boye.httpclientandroidlib.impl.client.HttpClients;
import ch.boye.httpclientandroidlib.message.BasicHeader;
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
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter("post_media_updated"));

        postJSONFileUri = intent.getData();
        uploadMedia();

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
                        JSONObject post = new JSONObject(JSON);
                        JSONArray sections = post.getJSONArray("sections");
                        int totalMediaCount = 0;
                        for (int i=0; i<sections.length(); i++) {
                            JSONObject section = (JSONObject) sections.get(i);
                            if (section.has("image_path") || section.has("video_path")) {
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
                            if (section.has("image_path")) {
                                String imagePath = section.getString("image_path");
                                String imagePathHash = YTWHelper.md5(imagePath);
                                if (!urlMap.has(imagePathHash)) {
                                    String imageUrl = uploadImage(imagePath);
                                    if (imageUrl != null) {
                                        urlMap.put(imagePathHash, imageUrl);
                                    }
                                }
                                uploadedMediaCount ++;
                            }
                            if (section.has("video_path")) {
                                String videoPath = section.getString("video_path");
                                String videoPathHash = YTWHelper.md5(videoPath);
                                if (!urlMap.has(videoPathHash)) {
                                    String imageUrl = uploadImage(videoPath);
                                    if (imageUrl != null) {
                                        urlMap.put(videoPathHash, imageUrl);
                                    }
                                }
                                uploadedMediaCount ++;
                            }
                            post.put("url_map", urlMap);
                            if (uploadedMediaCount < totalMediaCount) {
                                publishProgress(uploadedMediaCount * 1.0f / totalMediaCount);
                            }
                            YTWHelper.writeTextContentToFile(post.toString(),
                                    postJSONFileUri.getPath());
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
                broadcastProgress(values[0]);
            }
        }.execute();
    }

    void broadcastProgress(float progress) {
        Intent intent = new Intent("upload_post_media_progress");
        intent.setData(postJSONFileUri);
        intent.putExtra("progress", progress);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
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

    String uploadVideo(String videoPath) {
        return null;
    }

    static final String TAG = "UploadPostMediaService";
    static final String IMAGE_ADD_URL = "http://115.28.156.104/image/add";
    static final String IMAGE_URL_PREFIX = "http://115.28.156.104/image/";

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
                    String line = reader.readLine().trim();
                    if (line.startsWith("<h1>")) {
                        String imageId = line.substring(9, 32);
                        return IMAGE_URL_PREFIX + imageId;
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
