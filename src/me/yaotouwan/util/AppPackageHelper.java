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
        public String versionName = "";
        public int versionCode = 0;
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
                httpPost.setHeader("Accept", "application/json");
                httpPost.setHeader("Content-Type", "application/json");
                try {
                    StringEntity entity = new StringEntity(apps.toString());
                    httpPost.setEntity(entity);
                    HttpResponse response = httpclient.execute(httpPost);
                    if (response != null) {
                        InputStream is = response.getEntity().getContent();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                        StringBuilder sb = new StringBuilder();
                        while (reader.ready()) {
                            String line = reader.readLine().trim();
                            sb.append(line);
                        }
                        List<Game> games = new ArrayList<Game>();
                        JSONArray jsonArray = new JSONArray(sb.toString());
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject jobj = jsonArray.getJSONObject(i);
                            String title = jobj.getString("title");

                            for (PackageInfo pack : packs) {
                                String appname = pack.applicationInfo.loadLabel(packageManager).toString();
                                if (title.equals(appname)) {
                                    Game game = new Game();
                                    game.appname = title;
                                    game.icon = pack.applicationInfo.loadIcon(packageManager);
                                    game.pname = pack.applicationInfo.packageName;
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
        }.execute();
    }
}
