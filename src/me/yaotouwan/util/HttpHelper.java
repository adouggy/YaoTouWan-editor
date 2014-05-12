package me.yaotouwan.util;

import java.io.IOException;
import java.util.List;

//import me.yaotouwan.android.keeper.UserInfoKeeper;

import android.os.AsyncTask;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Log;

public enum HttpHelper {
	INSTANCE;

	public static final String TAG = "HttpHelper";

	private Context mContext;
	public void setContext(Context ctx){
		mContext = ctx;
	}
	
	HttpHelper() {

	}

	public HttpResponse get(String url) {
//		Log.d(TAG, "get:" + url);
		HttpClient httpclient = new DefaultHttpClient();
		
		HttpGet get = new HttpGet(url);
		HttpResponse response = null;
		try {
//			get.setHeader("Cookie", UserInfoKeeper.read(mContext).getSession().getSession());
//			Header[] headers = get.getHeaders("Cookie");
//			for(Header h : headers){
//				Log.i(TAG, h.getName() + ": " + h.getValue());
//			}
			response = httpclient.execute(get);
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return response;
	}

    public HttpResponse postData(String url, byte[] data) {
//        Log.d(TAG, "post:" + url);
        HttpClient httpclient = new DefaultHttpClient();
        HttpPost post = new HttpPost(url);
        HttpResponse response = null;
        try {

            ByteArrayEntity entity = new ByteArrayEntity(data);
            post.setEntity(entity);

            response = httpclient.execute(post);
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return response;
    }

    public interface HttpHelperDelegate {
        public void onComplete(String responseContent, Exception exception);
    }

    public JSONObject postJSON(final String url, final JSONObject jsonObject, final HttpHelperDelegate delegate) {
        if (delegate != null) {

            new AsyncTask<Integer, Integer, Boolean>() {

                String content;

                @Override
                protected Boolean doInBackground(Integer... params) {
                    HttpResponse response = post(url, jsonObject.toString());
                    HttpEntity entity = response.getEntity();
                    try {
                        content = EntityUtils.toString(entity);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    return null;
                }

                @Override
                protected void onPostExecute(Boolean aBoolean) {
                    delegate.onComplete(content, null);
                }

            }.execute();

            return null;
        } else {
            HttpResponse response = post(url, jsonObject.toString());
            HttpEntity entity = response.getEntity();
            String content = EntityUtils.getContentCharSet(entity);
            try {
                return new JSONObject(content);
            } catch (JSONException e) {
                return null;
            }
        }
    }

	public HttpResponse post(String url, String body) {
//		Log.d(TAG, "post:" + url);
		HttpClient httpclient = new DefaultHttpClient();
		HttpPost post = new HttpPost(url);
		HttpResponse response = null;
		try {
//			Log.i(TAG, "post body:" + body);
			StringEntity entity = new StringEntity(body, HTTP.UTF_8);

			post.setHeader("Accept", "application/json");
			post.setHeader("Content-Type", "application/json");
//			post.setHeader("Cookie", UserInfoKeeper.read(mContext).getSession().getSession());
			post.setEntity(entity);

			response = httpclient.execute(post);
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
        }
        return response;
	}

	private String getCookie(DefaultHttpClient httpClient) {
		List<Cookie> cookies = httpClient.getCookieStore().getCookies();
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < cookies.size(); i++) {
			Cookie cookie = cookies.get(i);
			String cookieName = cookie.getName();
			String cookieValue = cookie.getValue();
			if (!TextUtils.isEmpty(cookieName) && !TextUtils.isEmpty(cookieValue)) {
				sb.append(cookieName + "=");
				sb.append(cookieValue + ";");
			}
		}

		return sb.toString();
	}

    public HttpResponse uploadData(String url, byte[] data) {
        HttpClient httpclient = new DefaultHttpClient();
        httpclient.getParams().setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);

        HttpPost httppost = new HttpPost(url);

        ByteArrayEntity byteArrayEntity = new ByteArrayEntity(data);
        httppost.setEntity(byteArrayEntity);

        HttpResponse response = null;
        try {
            response = httpclient.execute(httppost);
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // httpclient.getConnectionManager().shutdown();
        return response;
    }

	public HttpResponse uploadImage(byte[] bArr) {
		HttpClient httpclient = new DefaultHttpClient();
		httpclient.getParams().setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);

		HttpPost httppost = new HttpPost("");

		// this need httpmime.jar
		MultipartEntity mpEntity = new MultipartEntity();
		ContentBody cbFile = new ByteArrayBody(bArr, "temp.jpg");
		mpEntity.addPart("file", cbFile);

		httppost.setEntity(mpEntity);

		HttpResponse response = null;
		try {
			response = httpclient.execute(httppost);
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		// httpclient.getConnectionManager().shutdown();
		return response;
	}

	public HttpResponse uploadPortrait(byte[] bArr) {
		HttpClient httpclient = new DefaultHttpClient();
		httpclient.getParams().setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);

		HttpPost httppost = new HttpPost("");

		// this need httpmime.jar
		MultipartEntity mpEntity = new MultipartEntity();
		ContentBody cbFile = new ByteArrayBody(bArr, "tempPortrait.jpg");
		mpEntity.addPart("file", cbFile);

		httppost.setEntity(mpEntity);

		HttpResponse response = null;
		try {
			response = httpclient.execute(httppost);
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		// httpclient.getConnectionManager().shutdown();
		return response;
	}

	public Bitmap downloadBitmap(String url) {
		HttpUriRequest request = new HttpGet(url.toString());
		HttpClient httpClient = new DefaultHttpClient();
		HttpResponse response = null;
		try {
			response = httpClient.execute(request);
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (response != null) {
			StatusLine statusLine = response.getStatusLine();
			int statusCode = statusLine.getStatusCode();
			if (statusCode == 200) {
				HttpEntity entity = response.getEntity();
				byte[] bytes = null;
				try {
					bytes = EntityUtils.toByteArray(entity);
				} catch (IOException e) {
					e.printStackTrace();
				}

				if (bytes == null)
					return null;

				Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
				return bitmap;
			}
		}

		return null;
	}
}
