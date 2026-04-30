package download.throttled.lvijfc.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ListView;
import android.widget.TextView;

import download.throttled.lvijfc.core.FluxerApp;
import download.throttled.lvijfc.R;
import download.throttled.lvijfc.adapters.GuildAdapter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Guild list — home screen.
 *
 * Search filters the user's joined guilds locally (no extra network call).
 * The 💬 button in the header opens the DM list via ChannelListActivity.
 */
public class HomeActivity extends BaseActivity {

    private ListView  list;
    private View      emptyView;
    private TextView  searchInput;
    private View      clearBtn;
    private View      menuBtn;
    private View      dmBtn;
    private View      spinner;

    private GuildAdapter adapter;
    private JSONArray    myGuilds = new JSONArray();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (api() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_home);

        list        = findViewById(R.id.list);
        emptyView   = findViewById(android.R.id.empty);
        searchInput = findViewById(R.id.search_input);
        clearBtn    = findViewById(R.id.clear_btn);
        menuBtn     = findViewById(R.id.menu_btn);
        dmBtn       = findViewById(R.id.dm_btn);
        spinner     = findViewById(R.id.spinner);

        adapter = new GuildAdapter(this, new ArrayList<>());
        list.setAdapter(adapter);
        list.setEmptyView(emptyView);

        list.setOnItemClickListener((parent, view, pos, id) -> {
            JSONObject guild = adapter.getItem(pos);
            if (guild == null) return;
            Intent i = new Intent(this, ChannelListActivity.class);
            i.putExtra("mode",       ChannelListActivity.MODE_GUILD);
            i.putExtra("guild_id",   guild.optString("id"));
            i.putExtra("guild_name", guild.optString("name", guild.optString("id")));
            startActivity(i);
        });

        clearBtn.setOnClickListener(v -> searchInput.setText(""));
        menuBtn.setOnClickListener(v -> showMenu());
        if (dmBtn != null) dmBtn.setOnClickListener(v -> {
            Intent i = new Intent(this, ChannelListActivity.class);
            i.putExtra("mode", ChannelListActivity.MODE_DMS);
            startActivity(i);
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateClearButton();
                if (s.length() == 0) showMyGuilds();
                else applyFilter(s.toString());
            }
        });

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                applyFilter(searchInput.getText().toString());
                return true;
            }
            return false;
        });

        loadMyGuilds();
    }

    // DATA

    private void loadMyGuilds() {
        setSpinner(true);
        bg(api()::getMyGuilds, guilds -> {
            myGuilds = guilds;
            showMyGuilds();
            setSpinner(false);
        }, () -> setSpinner(false));
    }

    private void showMyGuilds() {
        populate(myGuilds);
    }

    private void applyFilter(String q) {
        String lower = q.toLowerCase().trim();
        JSONArray filtered = new JSONArray();
        for (int i = 0; i < myGuilds.length(); i++) {
            try {
                JSONObject g = myGuilds.getJSONObject(i);
                if (g.optString("name", "").toLowerCase().contains(lower)) filtered.put(g);
            } catch (Exception ignored) {}
        }
        populate(filtered);
    }

    private void populate(JSONArray arr) {
        List<JSONObject> items = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            try { items.add(arr.getJSONObject(i)); } catch (Exception ignored) {}
        }
        adapter.replace(items);
    }

    // UI

    private void updateClearButton() {
        boolean hasText = searchInput.getText().length() > 0;
        clearBtn.setVisibility(hasText ? View.VISIBLE : View.GONE);
        menuBtn.setVisibility (hasText ? View.GONE    : View.VISIBLE);
    }

    private void setSpinner(boolean on) {
        spinner.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    private void showMenu() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setItems(new CharSequence[]{"Log out"}, (dlg, which) -> logout())
                .show();
    }

    private void logout() {
        FluxerApp.get(this).logout();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
