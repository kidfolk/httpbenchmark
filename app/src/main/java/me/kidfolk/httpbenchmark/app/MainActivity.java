package me.kidfolk.httpbenchmark.app;

import com.squareup.okhttp.HttpResponseCache;
import com.squareup.okhttp.OkHttpClient;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity implements View.OnClickListener {

    private TextView mLabel;

    private Button mButton;

//    private static final String[] DATAS = {"http://192.168.123.108:8910/rfc792.html",
//            "http://192.168.123.108:8910/rfc793.html", "http://192.168.123.108:8910/rfc826.html",
//            "http://192.168.123.108:8910/rfc1058.html", "http://192.168.123.108:8910/rfc1067
// .html",
//            "http://192.168.123.108:8910/rfc1149.html", "http://192.168.123.108:8910/rfc1918
// .html",
//            "http://192.168.123.108:8910/rfc1964.html",
//            "http://192.168.123.108:8910/rfc2119.html", "http://192.168.123.108:8910/rfc2616
// .txt"};
//
//    private static final String URL = "http://192.168.123.108:8910/rfc2616.txt";

    private static ExecutorService mOkHttpThreadPoolExecutor;

    private static ExecutorService mHUCThreadPoolExecutor;

    private static LinkedBlockingQueue<Runnable> okHttpQueue;

    private static LinkedBlockingQueue<Runnable> hucQueue;

    private static LinkedBlockingQueue<Long> okResult;

    private static LinkedBlockingQueue<Long> hucResult;

    private static Thread checkThread = new Thread(new Runnable() {
        public static final String TAG = "CheckThread";

        private boolean okHasFinished = false;

        private boolean hucHasFinished = false;

        @Override
        public void run() {
            while (!okHasFinished || !hucHasFinished) {
                if (okResult.size() == Data.URLS.length && !okHasFinished) {
                    okHasFinished = true;
                    log("ok finished!");
                }

                if (hucQueue.isEmpty() && !hucHasFinished) {
                    hucHasFinished = true;
                    log("huc finished!");
                }

            }
        }
    });

    private static Handler mainThreadHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            Log.d("CheckThread", msg.getData().getString("message"));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mLabel = (TextView) findViewById(R.id.okhttp_label);
        mButton = (Button) findViewById(R.id.okhttp_button);

        okHttpQueue = new LinkedBlockingQueue<>();
        hucQueue = new LinkedBlockingQueue<>();

        okResult = new LinkedBlockingQueue<>();
        hucResult = new LinkedBlockingQueue<>();

        mOkHttpThreadPoolExecutor = new ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS,
                okHttpQueue);
        mHUCThreadPoolExecutor = new ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS,
                hucQueue);

        mButton.setOnClickListener(this);
    }

    public static Proxy createProxy() {
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress("192.168.123.108",
                8888));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.okhttp_button:
                for (int i = 0; i < Data.URLS.length; i++) {
                    mOkHttpThreadPoolExecutor.submit(new OkHttpRunnable(getExternalCacheDir(),
                            Data.URLS[i]));
                    mHUCThreadPoolExecutor
                            .submit(new HUCRunnable(getExternalCacheDir(), Data.URLS[i]));
                }

                checkThread.start();
                break;
        }
    }

    private static class OkHttpRunnable implements Runnable {

        private final String url;

        private final OkHttpClient okHttpClient;

        private OkHttpRunnable(File cacheDir, String url) {
            this.url = url;
            okHttpClient = new OkHttpClient();
//            okHttpClient.setProxy(createProxy());
            try {
                okHttpClient.setResponseCache(new HttpResponseCache(new File(cacheDir, "okhttp"),
                        10L * 1024 * 1024));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                //warm up
                for (int i = 0; i < 10; i++) {
                    get(url);
                }
                long start = System.currentTimeMillis();
                for (int i = 0; i < 1000; i++) {
                    get(url);
                }
                long duration = (System.currentTimeMillis() - start) / 1000l;
                log(url + "-OkHttp: " + duration);
                okResult.add(duration);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public String get(String url) throws IOException {
            HttpURLConnection connection = okHttpClient.open(new java.net.URL(url));
            return printResponse(connection.getInputStream());
        }
    }

    private static void log(String log) {
        Bundle args = new Bundle();
        args.putString("message", log);
        Message msg = mainThreadHandler.obtainMessage();
        msg.setData(args);
        mainThreadHandler.sendMessage(msg);
    }

    private static class HUCRunnable implements Runnable {

        private final String url;

        private HUCRunnable(File cacheDir, String url) {
            this.url = url;
            try {
                android.net.http.HttpResponseCache.install(new File(cacheDir, "huc"), 10L * 1024
                        * 1024);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < 10; i++) {
                    hucGet(url);
                }
                long start = System.currentTimeMillis();
                for (int i = 0; i < 1000; i++) {
                    hucGet(url);
                }
                long duration = (System.currentTimeMillis() - start) / 1000l;
                log(url + "-huc: " + duration);
                hucResult.add(duration);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public String hucGet(String url) throws IOException {
            HttpURLConnection connection = (HttpURLConnection) new URL(url)
                    .openConnection(/**createProxy()*/);
            return printResponse(connection.getInputStream());
        }

    }

    public static String printResponse(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        for (String line; (line = reader.readLine()) != null; ) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

}
