package me.yaotouwan.screenrecorder;

import android.util.Log;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.client.ClientProtocolException;
import ch.boye.httpclientandroidlib.client.entity.UrlEncodedFormEntity;
import ch.boye.httpclientandroidlib.client.methods.HttpGet;
import ch.boye.httpclientandroidlib.client.methods.HttpPost;
import ch.boye.httpclientandroidlib.entity.ByteArrayEntity;
import ch.boye.httpclientandroidlib.entity.StringEntity;
import ch.boye.httpclientandroidlib.impl.client.HttpClients;
import ch.boye.httpclientandroidlib.util.EntityUtils;
import me.yaotouwan.util.YTWHelper;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.CRC32;

/**
 * Created by jason on 14-3-11.
 */
public class YoukuUploader {

    private static final String ACCESS_TOKEN_URL = "https://openapi.youku.com/v2/oauth2/token";
    private static final String UPLOAD_TOKEN_URL = "https://openapi.youku.com/v2/uploads/create.json";
    private static final String UPLOAD_COMMIT_URL = "https://openapi.youku.com/v2/uploads/commit.json";
    private static final String VERSION_UPDATE_URL = "http://open.youku.com/sdk/version_update";

    public static final String CLIENT_ID = "2780bc1482be4fd7"; // Youku OpenAPI client_id
    public static final String CLIENT_SECRET = "763deedf075ea67cb2c9c111b80ef779"; //Youku OpenAPI client_secret
    public static final String ACCESS_TOKEN = "c8bed2af457c4d388b60b114095711bd";
    public static final String REFRESH_TOKEN = "85a2a457e8c924bba2a664af8f5dda12";
    // https://openapi.youku.com/v2/oauth2/authorize?client_id=2780bc1482be4fd7&response_type=code&redirect_uri=http://yaotouwan.me
    // http://yaotouwan.me/?code=559fdbd0b3dd9379b9e7deff541b73ba&state=
    // http://yaotouwan.me/?code=c8bed2af457c4d388b60b114095711bd&state=
    public static final String REDIRECT_URI = "http://yaotouwan.me";

    private String refreshFilePath;
    private String client_id;
    private String client_secret;
    private String access_token;
    private String upload_token;
    private String upload_server_ip;
    private String refresh_token;

    private JSONObject jsonObjectOfResponse(String responseContent) throws UploadException {
        try {
            JSONObject result = new JSONObject(responseContent);
            if (result.has("error")) {
                JSONObject error =  result.getJSONObject("error");
                throw new UploadException(error.getString("description"), error.getInt(("code")));
            }
            return result;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String buildQuery(Map<String, String> parameters) {
        if (parameters == null) return null;
        String paramString = "";
        Set<String> keys = parameters.keySet();
        int i = 0;
        for(String key : keys) {
            String val = parameters.get(key).toString();
            try {
                val = URLEncoder.encode(val, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            if (i++ > 0) {
                paramString += "&";
            }
            paramString += key + "=" + val;
        }
        return paramString;
    }

    private JSONObject doPOST(String url, Map<String, String> parameters) throws UploadException {
        Log.i("Youku", "POST " + url);
        String paramString = buildQuery(parameters);
        Log.i("Youku", "POST body " + paramString);

        try {
            HttpPost httpPost = new HttpPost(url);
            StringEntity entity = new StringEntity(paramString);
            httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded");
            httpPost.setEntity(entity);
            HttpResponse response = HttpClients.createDefault().execute(httpPost);
            String responseContent = EntityUtils.toString(response.getEntity());
            Log.i("Youku", "Response " + responseContent);
            JSONObject result = new JSONObject(responseContent);
            if (result.has("error")) {
                JSONObject error = result.getJSONObject("error");
                throw new UploadException(error.getString("description"), error.getInt(("code")));
            }
            return result;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }


    private JSONObject doGET(String url, Map<String, String> parameters) throws UploadException {
        Log.i("Youku", "GET " + url);
        String paramString = buildQuery(parameters);
        if (paramString != null && paramString.length() > 0) {
            url += "?" + paramString;
        }

        try {
            HttpResponse response = HttpClients.createDefault().execute(new HttpGet(url));
            String responseContent = EntityUtils.toString(response.getEntity());
            Log.i("Youku", "Response " + responseContent);
            JSONObject result = new JSONObject(responseContent);
//            if (result.has("error")) {
//                JSONObject error =  result.getJSONObject("error");
//                throw new UploadException(error.getString("description"), error.getInt(("code")));
//            }
            return result;
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    private JSONObject getUploadToken(Map<String, String> uploadInfo) throws UploadException {
        Map<String, String> parameters = new HashMap<String, String>(uploadInfo);
        parameters.put("client_id", this.client_id);
        parameters.put("access_token", this.access_token);
        return doGET(UPLOAD_TOKEN_URL, parameters);
    }

    private JSONObject uploadCreate(String filename) throws UploadException {
        File file = new File(filename);
        long filesize = file.length();
        String extension = "";
        int i = filename.lastIndexOf('.');
        if (i > 0) {
            extension = filename.substring(i+1);
        }
        String url = "http://" + this.upload_server_ip + "/gupload/create_file";
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("upload_token", this.upload_token);
        parameters.put("file_size", filesize + "");
        parameters.put("slice_length", "1024");
        parameters.put("ext", extension);

        return doPOST(url, parameters);
    }

    private JSONObject createSlice() throws UploadException {
        String url = "http://" + this.upload_server_ip + "/gupload/new_slice?upload_token=" + this.upload_token;
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("upload_token", this.upload_token);

        return doGET(url, null);
    }

    private JSONObject uploadSlice(int slice_task_id, int offset, int length, String filename) throws UploadException {
        String url = "http://" + this.upload_server_ip + "/gupload/upload_slice";
        byte[] data = readVideoFile(filename, offset, length);
        url += "?upload_token=" + this.upload_token;
        url += "&slice_task_id=" + slice_task_id;
        url += "&offset=" + offset;
        url += "&length=" + length;
        CRC32 crc32 = new CRC32();
        crc32.update(data);
        long crc32Value = crc32.getValue();
        url += "&crc=" + Dechexer.decToHex((int) crc32Value);
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("MD5");
            byte[] md5Value = md.digest(data);
            String hexValue = this.bin2hex(md5Value);
            url += "&hash=" + hexValue;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
        Log.i("Youku", "POST with data " + url);

        try {
            HttpPost httpPost = new HttpPost(url);
            ByteArrayEntity dataEntity = new ByteArrayEntity(data);
            httpPost.setEntity(dataEntity);
            HttpResponse response = HttpClients.createDefault().execute(httpPost);
            String responseContent = EntityUtils.toString(response.getEntity());
            return jsonObjectOfResponse(responseContent);
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private JSONObject commit(String uploadServerIp) throws UploadException {
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("access_token", this.access_token);
        parameters.put("client_id", this.client_id);
        parameters.put("upload_token", this.upload_token);
        parameters.put("upload_server_ip", uploadServerIp);
        return doGET(UPLOAD_COMMIT_URL, parameters);
    }

    private JSONObject versionUpdate() throws UploadException {
        String verion = "13120910";
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("client_id", this.client_id);
        parameters.put("version", verion);
        parameters.put("type", "php");
        return doPOST(VERSION_UPDATE_URL, parameters);
    }

    private JSONObject check() throws UploadException {
        String url = "http://" + this.upload_server_ip  + "/gupload/check?upload_token=" + this.upload_token;
        return doGET(url, null);
    }

    private void getAccessToken() throws UploadException, JSONException {
        HashMap<String, String> parameters = new HashMap<String, String>();
        parameters.put("client_id", this.client_id);
        parameters.put("client_secret", this.client_secret);
        parameters.put("grant_type", "authorization_code");
        parameters.put("code", this.access_token);
        parameters.put("redirect_uri", REDIRECT_URI);
        JSONObject acessTokenResult = doPOST(ACCESS_TOKEN_URL, parameters);
        if (acessTokenResult != null && acessTokenResult.has("access_token") && acessTokenResult.has("refresh_token")) {
            this.access_token = acessTokenResult.getString("access_token");
            this.refresh_token = acessTokenResult.getString("refresh_token");
            writeRefreshFile(refreshFilePath, acessTokenResult);
        }
    }

    private void refreshToken() throws UploadException, JSONException {
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("client_id", this.client_id);
        parameters.put("client_secret", this.client_secret);
        parameters.put("grant_type", "refresh_token");
        parameters.put("refresh_token", this.refresh_token);
        JSONObject refreshResult = doPOST(ACCESS_TOKEN_URL, parameters);
        if (refreshResult != null && refreshResult.has("access_token") && refreshResult.has("refresh_token")) {
            this.access_token = refreshResult.getString("access_token");
            this.refresh_token = refreshResult.getString("refresh_token");
            writeRefreshFile(refreshFilePath, refreshResult);
        }
    }

    private void readRefreshFile() {
        BufferedInputStream bis = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(refreshFilePath));
            byte[] bytes = new byte[1024];
            int len = bis.read(bytes);
            String refreshContent = new String(bytes, 0, len);
            JSONObject refreshJSONObject = new JSONObject(refreshContent);
            this.access_token = refreshJSONObject.getString("access_token");
//            if (this.access_token == null) this.access_token = "";
            this.refresh_token = refreshJSONObject.getString("refresh_token");
//            if (this.refresh_token == null) this.refresh_token = "";
        } catch (FileNotFoundException e) {
//            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        } finally {
            if (bis != null) try {
                bis.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void writeRefreshFile(String refresh_file, JSONObject refresh_json_result) {
        BufferedOutputStream bos = null;
        try {
            bos = new BufferedOutputStream(new FileOutputStream(new File(refresh_file)));
            bos.write(refresh_json_result.toString().getBytes());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (bos != null) try {
                bos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String upload(Map<String, String> uploadInfo) throws UploadException {

//        this.access_token = params.get("access_token");
//        this.refresh_token = params.get("refresh_token");

        if (refreshFilePath == null) {
            String dataRootDir = YTWHelper.dataRootDirectory(0);
            refreshFilePath = new File(dataRootDir, "youku.token.txt").getAbsolutePath();
        }

        readRefreshFile();

//        versionUpdate();

        try {
            JSONObject uploadResult = null;
            if (this.refresh_token == null) { // 缓存中没有refresh_token，就获取
                getAccessToken();
            }
            if (this.refresh_token != null) {
                uploadResult = getUploadToken(uploadInfo); // 拿到了refresh_token，就创建上传
                JSONObject error = uploadResult.has("error") ? uploadResult.getJSONObject("error") : null;
                if (error != null && error.getInt("code") == 1009) { // 创建失败，原因是refresh_token过期
                    this.refresh_token = null;
                    refreshToken();
                    if (this.refresh_token != null) { // 重新拿到了refresh_token，再次创建上传
                        uploadResult = getUploadToken(uploadInfo);
                    } else {
                        Log.d("Youku", "upload failed");
                        return null;
                    }
                } else if (error != null) { // 其他原因创建失败，不可恢复
                    Log.d("Youku", "upload failed");
                    return null;
                }
            }

            if (uploadResult == null || !uploadResult.has("upload_token")) {
                Log.d("Youku", "upload failed");
                return null;
            }
            this.upload_token = uploadResult.getString("upload_token");
            String filename = uploadInfo.get("file_name");
            this.upload_server_ip = uploadResult.getString("upload_server_uri");//getHost(uploadResult.getString("upload_server_uri"));

            uploadCreate(filename);
            Log.i("Youku", "Uploading start!");
            boolean finish = false;
            int transferred = 0;

            JSONObject sliceResult = createSlice();
            int slice_id = sliceResult.getInt("slice_task_id");
            int offset = sliceResult.getInt("offset");
            int length = sliceResult.getInt("length");
            String uploadServerIp = "";

            do {
                JSONObject uploadSlice = uploadSlice(slice_id, offset, length, filename);
                slice_id = uploadSlice.getInt("slice_task_id");
                offset = uploadSlice.getInt("offset");
                length = uploadSlice.getInt("length");
                transferred = Math.round(uploadSlice.getInt("transferred")
                        / Integer.parseInt(uploadInfo.get("file_size")) * 100);
                if (slice_id == 0) {
                    do {
                        JSONObject checkResult = check();
                        if (checkResult.has("status")) {
                            finish = checkResult.getBoolean("finished");
                            if (checkResult.getInt("status") == 1) {
                                uploadServerIp = checkResult.getString("upload_server_ip");
                                transferred = 100;
                                break;
                            }
                        } else if (checkResult.getInt("status") == 2 || checkResult.getInt("status") == 3) {
                            transferred = checkResult.getInt("confirmed_percent");
                        }
                    } while (true);
                }
                Log.i("Youku", "Upload progress:" + transferred);
            } while (!finish);
            JSONObject commitResult = commit(uploadServerIp);
            Log.i("Youku", "Uploading success");
            String videoId = "";
            if (commitResult.has("video_id")) {
                videoId = commitResult.getString("video_id");
                Log.i("Youku", "videoid: " + videoId);
                return "youku://" + videoId;
            }
            return null;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }



    private byte[] readVideoFile(String filename, int offset, int length) {
        BufferedInputStream bis = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(filename));
            byte[] bytes = new byte[length];
            bis.skip(offset);
            int len = bis.read(bytes, 0, length);
            if (len == length) {
                return bytes;
            } else if (len <= 0) {
                return null;
            } else {
                byte[] buf = new byte[len];
                System.arraycopy(buf, 0, bytes, 0, len);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (bis != null)
                try {
                    bis.close();
                } catch (IOException ignore) {

                }
        }
        return null;
    }

    public class UploadException extends Throwable {
        public String description;
        public int code;
        public UploadException(String description, int anInt) {
            this.description = description;
            this.code = anInt;
        }

        public String toString() {
            return "Youku Upload Exception, " + description + " code " + code;
        }
    }

    private static class Dechexer {
        private static final int sizeOfIntInHalfBytes = 8;
        private static final int numberOfBitsInAHalfByte = 4;
        private static final int halfByte = 0x0F;
        private static final char[] hexDigits = {
                '0', '1', '2', '3', '4', '5', '6', '7',
                '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
        };

        public static String decToHex(int dec) {
            StringBuilder hexBuilder = new StringBuilder(sizeOfIntInHalfBytes);
            hexBuilder.setLength(sizeOfIntInHalfBytes);
            for (int i = sizeOfIntInHalfBytes - 1; i >= 0; --i)
            {
                int j = dec & halfByte;
                hexBuilder.setCharAt(i, hexDigits[j]);
                dec >>= numberOfBitsInAHalfByte;
            }
            return hexBuilder.toString();
        }
    }

    static String bin2hex(byte[] data) {
        return String.format("%0" + (data.length * 2) + 'x', new BigInteger(1, data));
    }

    public String uploadVideoFile(String filepath, String title, String tags) throws UploadException {
        client_id = CLIENT_ID;
        client_secret = CLIENT_SECRET;
        this.access_token = ACCESS_TOKEN;
        this.refresh_token = REFRESH_TOKEN;

        Map<String, String> uploadInfo = new HashMap<String, String>();
        String md5 = new MD5Checksum(filepath).getMD5Checksum();
        uploadInfo.put("file_md5", md5);
        uploadInfo.put("file_size", new File(filepath).length() + "");
        uploadInfo.put("file_name", filepath);
        String tail = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date());
        title = title + "_" + tail;
        uploadInfo.put("title", title);
        Log.d("Youku", "upload with title " + title + ", md5 " + md5);
//        uploadInfo.put("tags", tags);
        uploadInfo.put("tags", "游戏");
        uploadInfo.put("category", "游戏");

        return upload(uploadInfo);
    }

    public class MD5Checksum {

        private String filename;
        public MD5Checksum(String filename) {
            this.filename = filename;
        }

        // see this How-to for a faster way to convert
        // a byte array to a HEX string
        public String getMD5Checksum() {
            try {
                InputStream fis = new FileInputStream(filename);

                byte[] buffer = new byte[1024];
                MessageDigest complete = MessageDigest.getInstance("MD5");
                int numRead;

                do {
                    numRead = fis.read(buffer);
                    if (numRead > 0) {
                        complete.update(buffer, 0, numRead);
                    }
                } while (numRead != -1);

                fis.close();
                byte[] b = complete.digest();

                String result = "";

                for (int i = 0; i < b.length; i++) {
                    result += Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
                }
                return result;
            } catch (Exception e) {
                return null;
            }
        }
    }
}
