package me.yaotouwan.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.util.Log;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.client.ClientProtocolException;
import ch.boye.httpclientandroidlib.client.HttpClient;
import ch.boye.httpclientandroidlib.client.methods.HttpPost;
import ch.boye.httpclientandroidlib.entity.StringEntity;
import ch.boye.httpclientandroidlib.util.EntityUtils;
import me.yaotouwan.post.HttpClientUtil;
import ch.boye.httpclientandroidlib.entity.BasicHttpEntity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jason on 14-3-27.
 */
public class AppPackageHelper {

    PackageManager packageManager;
    static final String URL_GAME_LIST_BY_PACKAGE = "http://115.28.156.104/j/game/list/by/package";

    public class Game {
        public String appname = "";
        public String pname = "";
        public Drawable icon;
    }

    public interface AppPackageHelperDelegate {
        public void onComplete(List<Game> packageInfos);
    }

    public void getPackages(final Context ctx, final AppPackageHelperDelegate delegate) {

        new AsyncTask<Integer, Integer, List<Game>>() {

            JSONObject apps;

            @Override
            protected List<Game> doInBackground(Integer... params) {
                ArrayList<String> pkgs = new ArrayList<String>();
                packageManager = ctx.getPackageManager();
                List<PackageInfo> packs = packageManager.getInstalledPackages(0);

                if (packs == null) return null;

                for(int i=0; i<packs.size(); i++) {
                    PackageInfo p = packs.get(i);
                    if (p.versionName == null) {
                        continue ;
                    }
                    if (p.applicationInfo == null) {
                        continue;
                    }
                    if (p.applicationInfo.packageName.startsWith("android")) {
                        continue;
                    }
                    if (p.applicationInfo.packageName.startsWith("com.android")) {
                        continue;
                    }
                    if (p.applicationInfo.packageName.startsWith("com.google.android")) {
                        continue;
                    }
                    pkgs.add(p.applicationInfo.packageName);
                }
                if (pkgs.size() == 0) return null;

                apps = new JSONObject();
                try {
                    apps.put("apps", new JSONArray(pkgs));
                } catch (JSONException e) {
                    return null;
                }

                HttpClient httpclient = HttpClientUtil.createInstance();
                HttpPost httpPost = new HttpPost(URL_GAME_LIST_BY_PACKAGE);
                httpPost.setHeader("Content-Type", "application/json");
                try {
                    StringEntity entity = new StringEntity(apps.toString());
                    httpPost.setEntity(entity);
                    HttpResponse response = httpclient.execute(httpPost);
                    if (response != null) {
                        String responseContent = EntityUtils.toString(response.getEntity());
                        List<Game> games = new ArrayList<Game>();
                        JSONArray jsonArray = new JSONArray(responseContent);
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject jobj = jsonArray.getJSONObject(i);
                            String url = jobj.getString("url");
                            // todo 需要用包名判断
                            String packName = url.substring("http://www.appchina.com/app/".length(), url.length()-1);

                            for (PackageInfo pack : packs) {
                                if (pack.applicationInfo == null) continue;
                                if (pack.applicationInfo.loadLabel(packageManager) == null) continue;
                                String appname = pack.applicationInfo.loadLabel(packageManager).toString();
                                String localPackName = pack.applicationInfo.packageName;
                                if (packName.equals(localPackName)) {
                                    Game game = new Game();
                                    game.appname = appname;
                                    game.icon = pack.applicationInfo.loadIcon(packageManager);
                                    game.pname = localPackName;
                                    games.add(game);
                                    break;
                                }
                            }
                        }
                        return games;
                    }
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                    return null;
                } catch (ClientProtocolException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(List<Game> games) {
                if (games != null)
                    delegate.onComplete(games);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
