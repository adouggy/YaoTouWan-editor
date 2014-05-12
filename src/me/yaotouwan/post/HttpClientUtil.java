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
    private Context mContext;

    public void init(Context ctx){
        mContext = ctx;
    }

    public static HttpClient createInstance() {
        CloseableHttpClient client = HttpClients.createDefault();
        return client;
    }

    public boolean checkConnection(Context ctx) {
        final ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        if (networkInfo == null || !networkInfo.isConnectedOrConnecting()) {
            return Boolean.FALSE;
        }

        return Boolean.TRUE;
    }
}
