package download.throttled.lvijfc.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import download.throttled.lvijfc.core.FluxerApp;
import download.throttled.lvijfc.R;
import download.throttled.lvijfc.api.FluxerApi;

public class LoginActivity extends BaseActivity {

    private TextView tokenInput;
    private View     connectBtn;
    private View     spinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("fluxer", MODE_PRIVATE);
        if (prefs.contains("token")) {
            go(HomeActivity.class);
            return;
        }

        setContentView(R.layout.activity_login);

        tokenInput = findViewById(R.id.token_input);
        connectBtn = findViewById(R.id.connect_btn);
        spinner    = findViewById(R.id.spinner);

        connectBtn.setOnClickListener(v -> attemptLogin());

        tokenInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                    actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });
    }

    private void attemptLogin() {
        String token = tokenInput.getText().toString().trim();
        if (TextUtils.isEmpty(token)) {
            toast("Paste your token first.");
            return;
        }

        setLoading(true);

        FluxerApi tmpApi = new FluxerApi(token);
        bg(tmpApi::getMe, me -> {
            String username = me.optString("username", "");
            String userId   = me.optString("id", "");

            getSharedPreferences("fluxer", MODE_PRIVATE).edit()
                    .putString("token",    token)
                    .putString("username", username)
                    .putString("user_id",  userId)
                    .apply();

            FluxerApp.get(this).initApi(token);
            go(HomeActivity.class);

        }, () -> setLoading(false));
    }

    private void setLoading(boolean on) {
        connectBtn.setVisibility(on ? View.GONE    : View.VISIBLE);
        spinner.setVisibility   (on ? View.VISIBLE : View.GONE);
        tokenInput.setEnabled(!on);
    }

    private void go(Class<?> cls) {
        startActivity(new Intent(this, cls));
        finish();
    }
}
