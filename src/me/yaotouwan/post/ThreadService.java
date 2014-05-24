package me.yaotouwan.post;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

/**
 * Created by jason on 14-5-24.
 */
public class ThreadService extends Service {
    public IBinder onBind(Intent intent) {
        return null;
    }


    boolean isRunning;
    void testThread() {
        new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] params) {
                while (isRunning) {
                    Log.d("Thread", "is running");
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                    }
                }
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onStart(Intent intent, int startId) {
        isRunning = true;
        testThread();
        new Handler(getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                isRunning = false;
            }
        }, 3000);
        super.onStart(intent, startId);
    }
}
