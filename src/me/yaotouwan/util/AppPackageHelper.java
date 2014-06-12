package me.yaotouwan.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.client.HttpClient;
import ch.boye.httpclientandroidlib.client.methods.HttpPost;
import ch.boye.httpclientandroidlib.entity.StringEntity;
import ch.boye.httpclientandroidlib.util.EntityUtils;
import me.yaotouwan.R;
import me.yaotouwan.post.HttpClientUtil;
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

    public void loadGames(final Context ctx, final AppPackageHelperDelegate delegate) {

        new AsyncTask<Integer, Integer, List<Game>>() {

            JSONObject apps;

            @Override
            protected List<Game> doInBackground(Integer... params) {
                ArrayList<String> pkgs = new ArrayList<String>();
                packageManager = ctx.getPackageManager();
                List<PackageInfo> packs = packageManager.getInstalledPackages(0);

                if (packs == null) return null;

                List<String> packagePrefixesShouldIgnore = new ArrayList<String>(3);
                packagePrefixesShouldIgnore.add("android");
                packagePrefixesShouldIgnore.add("com.android");
                packagePrefixesShouldIgnore.add("com.google.android");
                packagePrefixesShouldIgnore.add("com.qihoo");

                for(int i=0; i<packs.size(); i++) {
                    PackageInfo p = packs.get(i);
                    if (p.versionName == null) {
                        continue ;
                    }
                    if (p.applicationInfo == null) {
                        continue;
                    }
                    boolean shouldIgnore = false;
                    for (String packPrefix : packagePrefixesShouldIgnore) {
                        if (p.applicationInfo.packageName.startsWith(packPrefix)) {
                            shouldIgnore = true;
                            break;
                        }
                    }
                    if (shouldIgnore)
                        continue;
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
                        JSONArray jsonArray = new JSONArray(responseContent);
                        List<Game> games = new ArrayList<Game>(jsonArray.length()+1);
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject jobj = jsonArray.getJSONObject(i);
                            String url = jobj.getString("url");
                            // todo 需要用包名判断
                            String packName = url.substring("http://www.appchina.com/app/".length(), url.length()-1);

                            for (PackageInfo pack : packs) {
                                if (pack.applicationInfo == null) continue;
                                String appname = null;
                                try {
                                    if (pack.applicationInfo.loadLabel(packageManager) == null) continue;
                                    appname = pack.applicationInfo.loadLabel(packageManager).toString();
                                } catch (Resources.NotFoundException e) {
                                    continue;
                                }
                                String localPackName = pack.applicationInfo.packageName;
                                if (packName.equals(localPackName)) {
                                    Game game = new Game();
                                    game.appname = appname;
                                    try {
                                        game.icon = pack.applicationInfo.loadIcon(packageManager);
                                    } catch (Resources.NotFoundException e) {
                                        continue;
                                    }
                                    game.pname = localPackName;
                                    games.add(game);
                                    break;
                                }
                            }
                        }
                        Game game = new Game();
                        game.appname = ctx.getString(R.string.select_game_other_btn);
                        game.icon = ctx.getResources().getDrawable(R.drawable.ic_other_game);
                        games.add(game);

                        // save to disk
                        File cacheFile = new File(ctx.getCacheDir(), "games.json");
                        JSONArray gamesJSONArray = new JSONArray();
                        for (Game gameObj : games) {
                            JSONObject gameJSONObject = new JSONObject();
                            gameJSONObject.put("appname", gameObj.appname);
                            if (gameObj.pname != null)
                                gameJSONObject.put("pname", gameObj.pname);
                        }
                        YTWHelper.writeTextContentToFile(gamesJSONArray.toString(), cacheFile);

                        return games;
                    }
                    return null;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(List<Game> games) {
                delegate.onComplete(games);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void loadCachedGames(final Context ctx, final AppPackageHelperDelegate delegate) {
        new AsyncTask<Void, Void, List<Game>>() {

            @Override
            protected List<Game> doInBackground(Void... params) {
                File cacheFile = new File(ctx.getCacheDir(), "games.json");
                String cacheFileContent = YTWHelper.readTextContent(cacheFile.getAbsolutePath());
                if (cacheFileContent != null) {
                    try {
                        JSONArray gamesJSONArray = new JSONArray(cacheFileContent);
                        PackageManager packageManager = ctx.getPackageManager();
                        List<Game> games = new ArrayList<Game>(gamesJSONArray.length());
                        for (int i=0; i<gamesJSONArray.length(); i++) {
                            JSONObject gameJSONObject = gamesJSONArray.getJSONObject(i);
                            if (gameJSONObject.has("appname")) {
                                String appname = (String) gameJSONObject.get("appname");
                                Game game = new Game();
                                game.appname = appname;
                                if (gameJSONObject.has("pname")) {
                                    String pname = (String) gameJSONObject.get("pname");
                                    game.pname = pname;
                                    try {
                                        PackageInfo pack = packageManager.getPackageInfo(pname, 0);
                                        game.icon = pack.applicationInfo.loadIcon(packageManager);
                                    } catch (PackageManager.NameNotFoundException e) {
                                        continue;
                                    }
                                } else {
                                    game.icon = ctx.getResources().getDrawable(R.drawable.ic_other_game);
                                }
                                games.add(game);
                            }
                        }
                        return games;
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(List<Game> games) {
                delegate.onComplete(games);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
