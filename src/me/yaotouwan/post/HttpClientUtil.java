package me.yaotouwan.post;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import ch.boye.httpclientandroidlib.client.HttpClient;
import ch.boye.httpclientandroidlib.impl.client.CloseableHttpClient;
import ch.boye.httpclientandroidlib.impl.client.HttpClients;

/**
 * Created by jason on 14-5-9.
 */
public enum HttpClientUtil {INSTANCE;

    private static final int	DEFAULT_CONNECTION_TIMEOUT	= 10 * 1000;
    private static final int	SOKET_TIMEOUT				= 3 * 10 * 1000;

    public static HttpClient createInstance() {
        return HttpClients.createDefault();
    }

    public static boolean checkConnection(Context ctx) {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnectedOrConnecting();
    }
}
