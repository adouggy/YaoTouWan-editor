package me.yaotouwan.util;

import java.io.*;

/**
 * Created by jason on 14-4-3.
 */
public class StreamHelper {

    public static BufferedReader reader(InputStream is) {
        return new BufferedReader(new InputStreamReader(is));
    }

    public static BufferedWriter writer(OutputStream os) {
        return new BufferedWriter(new OutputStreamWriter(os));
    }
}
