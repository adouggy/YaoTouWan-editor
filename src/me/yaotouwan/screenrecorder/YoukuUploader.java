package me.yaotouwan.screenrecorder;

import android.util.Log;
import me.yaotouwan.util.HttpHelper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.math.BigInteger;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * Created by jason on 14-3-11.
 */
public class YoukuUploader {

    private static final String ACCESS_TOKEN_URL = "https://openapi.youku.com/v2/oauth2/token";
    private static final String UPLOAD_TOKEN_URL = "https://openapi.youku.com/v2/uploads/create.json";
    private static final String UPLOAD_COMMIT_URL = "https://openapi.youku.com/v2/uploads/commit.json";
    private static final String VERSION_UPDATE_URL = "http://open.youku.com/sdk/version_update";
    private static final String REFRESH_FILE = "/sdcard/yaotouwan/refresh.txt";

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
        HttpResponse response = HttpHelper.INSTANCE.post(url, paramString);
        try {
            String responseContent = EntityUtils.toString(response.getEntity());
            Log.i("Youku", "Response" + responseContent);
            JSONObject result = new JSONObject(responseContent);
            if (result.has("error")) {
                JSONObject error = result.getJSONObject("error");
                throw new UploadException(error.getString("description"), error.getInt(("code")));
            }
            return result;
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

        HttpResponse response = HttpHelper.INSTANCE.get(url);
        try {
            String responseContent = EntityUtils.toString(response.getEntity());
            Log.i("Youku", "Response " + responseContent);
            JSONObject result = new JSONObject(responseContent);
            if (result.has("error")) {
                JSONObject error =  result.getJSONObject("error");
                throw new UploadException(error.getString("description"), error.getInt(("code")));
            }
            return result;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    private JSONObject getAccessToken(Map<String, String> params) throws UploadException {
        HashMap<String, String> parameters = new HashMap<String, String>(params);
        parameters.put("client_id", this.client_id);
        parameters.put("client_secret", this.client_secret);
        parameters.put("grant_type", "password");

        return doPOST(ACCESS_TOKEN_URL, parameters);
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
        HttpResponse response = HttpHelper.INSTANCE.uploadData(url, data);
        HttpEntity entity = response.getEntity();
        String responseContent = null;
        try {
            responseContent = EntityUtils.toString(entity);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.i("Youku", "POST Response " + responseContent);
        return jsonObjectOfResponse(responseContent);
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

    private JSONObject refreshToken() throws UploadException {
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("client_id", this.client_id);
        parameters.put("client_secret", this.client_secret);
        parameters.put("grant_type", "refresh_token");
        parameters.put("refresh_token", this.refresh_token);
        return doPOST(ACCESS_TOKEN_URL, parameters);
    }

    private void readRefreshFile(String refresh_file) {
        BufferedInputStream bis = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(refresh_file));
            byte[] bytes = new byte[1024];
            int len = bis.read(bytes);
            String refreshContent = new String(bytes);
            JSONObject refreshJSONObject = new JSONObject(refreshContent);
            this.access_token = refreshJSONObject.getString("access_token");
            if (this.access_token == null) this.access_token = "";
            this.refresh_token = refreshJSONObject.getString("refresh_token");
            if (this.refresh_token == null) this.refresh_token = "";
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

    public void upload(boolean upload_process, Map<String, String> params, Map<String, String> uploadInfo) throws UploadException {

        this.access_token = params.get("access_token");
        this.refresh_token = params.get("refresh_token");
        readRefreshFile(REFRESH_FILE);

//        versionUpdate();

        try {
            JSONObject uploadResult = getUploadToken(uploadInfo);
            JSONObject error = uploadResult.has("error") ? uploadResult.getJSONObject("error") : null;

            if (error != null && error.getInt("code") != 1009) {
                JSONObject refreshResult = this.refreshToken();
                this.access_token = refreshResult.getString("access_token");
                this.refresh_token = refreshResult.getString("refresh_token");
                writeRefreshFile(REFRESH_FILE, refreshResult);
                uploadResult = getUploadToken(uploadInfo);
            }

            if (!uploadResult.has("upload_token")) {
                Log.d("Youku", "upload failed");
                return;
            }
            this.upload_token = uploadResult.getString("upload_token");
            String filename = uploadInfo.get("file_name");
            this.upload_server_ip = uploadResult.getString("upload_server_uri");//getHost(uploadResult.getString("upload_server_uri"));

            JSONObject uploadCreate = uploadCreate(filename);
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
                transferred = (int)Math.round(uploadSlice.getInt("transferred")
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
                if (upload_process) Log.i("Youku", "Upload progress:" + transferred);
            } while (!finish);
            if (finish) {
                JSONObject commitResult = commit(uploadServerIp);
                Log.i("Youku", "Uploading success");
                if (commitResult.getInt("video_id") > 0) {
                    Log.i("Youku", "videoid: " + commitResult.getInt("video_id"));
                }
            }

        } catch (JSONException e) {
        }

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

    private class UploadException extends Throwable {
        private String description;
        private int code;
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

    public void uploadVideoFile() {
        String filepath = "/sdcard/yaotouwan/screen.mp4";
        client_id = "2780bc1482be4fd7"; // Youku OpenAPI client_id
        client_secret = "763deedf075ea67cb2c9c111b80ef779"; //Youku OpenAPI client_secret
        Map<String, String> params = new HashMap<String, String>();
        params.put("access_token", "ae71097668c9397221aa7096a1e9fc44");
        params.put("refresh_token", "85a2a457e8c924bba2a664af8f5dda12");

        Map<String, String> uploadInfo = new HashMap<String, String>();
        String md5 = null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            InputStream is = new FileInputStream(filepath);
            DigestInputStream dis = new DigestInputStream(is, md);
            byte[] digest = md.digest();
//            md5 = new String(digest);
            md5 = "597d2b25005c17c25b538b7b00ad0158";
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        if (md5 != null) {
            uploadInfo.put("file_md5", md5);
        }

        uploadInfo.put("file_size", new File(filepath).length() + "");
        uploadInfo.put("file_name", filepath);
        uploadInfo.put("title", "jason_dev_test_file");
        uploadInfo.put("tags", "Flappy_Bird");

        boolean progress = true;
        try {
            upload(progress, params, uploadInfo);
        } catch (UploadException e) {
            e.printStackTrace();
        }
    }
}
