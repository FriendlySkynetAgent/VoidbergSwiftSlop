package download.throttled.lvijfc.core;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class ImageCache {

    private static final String TAG = "ImageCache";

    private static final Handler UI = new Handler(Looper.getMainLooper());

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 8)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    private ImageCache() {}

    public static void load(String url, int reqW, int reqH, Consumer<Bitmap> onLoad) {
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            Log.d(TAG, "cache hit: " + url);
            UI.post(() -> onLoad.accept(cached));
            return;
        }

        EXECUTOR.execute(() -> {
            try {
                Log.d(TAG, "fetching: " + url);
                Request req = new Request.Builder().url(url).build();
                try (Response resp = HTTP.newCall(req).execute()) {
                    Log.d(TAG, "response " + resp.code() + " for " + url);
                    if (!resp.isSuccessful() || resp.body() == null) {
                        Log.w(TAG, "failed: " + resp.code() + " " + url);
                        return;
                    }

                    byte[] bytes = resp.body().bytes();
                    Log.d(TAG, "got " + bytes.length + " bytes for " + url);

                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    if (reqW > 0 && reqH > 0) {
                        opts.inJustDecodeBounds = true;
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                        opts.inSampleSize = sampleSize(opts, reqW, reqH);
                        opts.inJustDecodeBounds = false;
                    }

                    Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
                    if (bmp == null) {
                        Log.w(TAG, "BitmapFactory returned null for " + url);
                        return;
                    }

                    Log.d(TAG, "decoded " + bmp.getWidth() + "x" + bmp.getHeight() + " for " + url);
                    CACHE.put(url, bmp);
                    UI.post(() -> onLoad.accept(bmp));
                }
            } catch (Exception e) {
                Log.e(TAG, "error loading " + url + ": " + e.getMessage());
            }
        });
    }

    private static int sampleSize(BitmapFactory.Options opts, int reqW, int reqH) {
        int h = opts.outHeight;
        int w = opts.outWidth;
        int size = 1;
        if (h > reqH || w > reqW) {
            int hRatio = Math.round((float) h / reqH);
            int wRatio = Math.round((float) w / reqW);
            size = Math.min(hRatio, wRatio);
        }
        return Math.max(1, size);
    }
}
