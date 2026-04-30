package download.throttled.lvijfc.ui;

import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import download.throttled.lvijfc.core.FluxerApp;
import download.throttled.lvijfc.api.FluxerApi;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thin base that every Fluxer Activity extends.
 * Provides:
 *   – a single-thread executor for background API calls
 *   – a main-thread Handler for posting results back
 *   – convenience method bg() to run a task off the UI thread
 */
public abstract class BaseActivity extends AppCompatActivity {

    protected final ExecutorService executor = Executors.newSingleThreadExecutor();
    protected final Handler ui = new Handler(Looper.getMainLooper());

    // access the shared API instance

    protected FluxerApi api() {
        return FluxerApp.get(this).getApi();
    }

    // threading convenience

    /**
     * Run {@code task} on the background executor.
     * On success, run {@code onSuccess} on the UI thread.
     * On failure, show a toast and optionally run {@code onError}.
     */
    protected <T> void bg(
            ApiCall<T> task,
            UiCallback<T> onSuccess,
            Runnable onError
    ) {
        executor.execute(() -> {
            try {
                T result = task.call();
                ui.post(() -> onSuccess.accept(result));
            } catch (Exception e) {
                String msg = friendlyError(e);
                ui.post(() -> {
                    toast(msg);
                    if (onError != null) onError.run();
                });
            }
        });
    }

    /** Overload with no error callback. */
    protected <T> void bg(ApiCall<T> task, UiCallback<T> onSuccess) {
        bg(task, onSuccess, null);
    }

    protected void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private String friendlyError(Exception e) {
        if (e instanceof FluxerApi.ApiException) {
            int code = ((FluxerApi.ApiException) e).code;
            if (code == 401 || code == 403) return "Authentication error — check your token.";
            if (code == 404) return "Not found.";
            return "Server error (" + code + ")";
        }
        if (e instanceof java.net.SocketTimeoutException ||
                e instanceof java.io.IOException) {
            return "Connection problem — check your internet.";
        }
        return "Unexpected error: " + e.getMessage();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    // SAM INTERFACES

    public interface ApiCall<T> {
        T call() throws Exception;
    }

    public interface UiCallback<T> {
        void accept(T result);
    }
}
