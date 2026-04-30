package download.throttled.lvijfc.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import download.throttled.lvijfc.R;
import download.throttled.lvijfc.adapters.ChannelAdapter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified channel/DM list activity.
 *
 * Launch with Intent extra "mode":
 *   MODE_GUILD — shows text/announcement channels for a guild.
 *                Also requires "guild_id" and "guild_name" extras.
 *   MODE_DMS   — shows the user's DM and group-DM channels.
 *
 * Both modes share the activity_channels layout, ChannelAdapter, and
 * the filter/spinner/back-button wiring. Only the data source and the
 * extras forwarded to MessagesActivity differ.
 */
public class ChannelListActivity extends BaseActivity {

    public static final String MODE_GUILD = "guild";
    public static final String MODE_DMS   = "dms";

    private ListView     list;
    private View         emptyView;
    private TextView     filterInput;
    private View         clearBtn;
    private View         backBtn;
    private View         spinner;

    private ChannelAdapter   adapter;
    private List<JSONObject> allItems = new ArrayList<>();

    private String mode;
    private String guildId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (api() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        mode    = getIntent().getStringExtra("mode");
        guildId = getIntent().getStringExtra("guild_id");
        String guildName = getIntent().getStringExtra("guild_name");

        setContentView(R.layout.activity_channels);

        list        = findViewById(R.id.list);
        emptyView   = findViewById(android.R.id.empty);
        filterInput = findViewById(R.id.filter_input);
        clearBtn    = findViewById(R.id.clear_btn);
        backBtn     = findViewById(R.id.back_btn);
        spinner     = findViewById(R.id.spinner);

        TextView titleView = findViewById(R.id.title);
        titleView.setText(MODE_DMS.equals(mode) ? "Direct Messages"
                : guildName != null ? guildName : "Channels");

        adapter = new ChannelAdapter(this, new ArrayList<>());
        list.setAdapter(adapter);
        list.setEmptyView(emptyView);

        list.setOnItemClickListener((parent, view, pos, id) -> openItem(adapter.getItem(pos)));

        clearBtn.setOnClickListener(v -> filterInput.setText(""));
        backBtn.setOnClickListener(v -> finish());

        filterInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearBtn.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                applyFilter(s.toString());
            }
        });

        loadData();
    }

    // DATA

    private void loadData() {
        setSpinner(true);
        if (MODE_DMS.equals(mode)) loadDms();
        else loadChannels();
    }

    private void loadChannels() {
        bg(() -> api().getChannels(guildId), raw -> {
            allItems.clear();
            for (int i = 0; i < raw.length(); i++) {
                try {
                    JSONObject ch = raw.getJSONObject(i);
                    int type = ch.optInt("type", -1);
                    if (type == 0 || type == 5) allItems.add(ch);   // text + announcement
                } catch (Exception ignored) {}
            }
            allItems.sort((a, b) -> Integer.compare(
                    a.optInt("position", 0), b.optInt("position", 0)));
            applyFilter(filterInput.getText().toString());
            setSpinner(false);
        }, () -> setSpinner(false));
    }

    private void loadDms() {
        bg(api()::getPrivateChannels, raw -> {
            allItems.clear();
            for (int i = 0; i < raw.length(); i++) {
                try {
                    JSONObject ch = raw.getJSONObject(i);
                    int type = ch.optInt("type", -1);
                    if (type != 1 && type != 3) continue;   // 1=DM, 3=group DM

                    String displayName = buildDisplayName(ch, type);
                    ch.put("_display_name", displayName);
                    ch.put("name", displayName);   // ChannelAdapter reads "name"

                    if (type == 3) {
                        JSONArray recips = ch.optJSONArray("recipients");
                        int count = recips != null ? recips.length() : 0;
                        ch.put("topic", "Group DM · " + count + " members");
                    }

                    allItems.add(ch);
                } catch (Exception ignored) {}
            }

            allItems.sort((a, b) -> {
                String idA = a.optString("last_message_id", "0");
                String idB = b.optString("last_message_id", "0");
                if (idB.length() != idA.length()) return idB.length() - idA.length();
                return idB.compareTo(idA);
            });

            applyFilter(filterInput.getText().toString());
            setSpinner(false);
        }, () -> setSpinner(false));
    }

    /**
     * Human-readable name for a DM channel:
     *   type 1 → first recipient's username
     *   type 3 → server-provided name, or comma-joined recipient usernames
     */
    private String buildDisplayName(JSONObject ch, int type) {
        if (type == 3) {
            String name = ch.optString("name", "").trim();
            if (!name.isEmpty()) return name;
        }
        JSONArray recips = ch.optJSONArray("recipients");
        if (recips == null || recips.length() == 0) return ch.optString("id");
        if (type == 1) {
            try { return recips.getJSONObject(0).optString("username", "?"); }
            catch (Exception ignored) {}
        }
        List<String> names = new ArrayList<>();
        for (int i = 0; i < recips.length(); i++) {
            try { names.add(recips.getJSONObject(i).optString("username", "?")); }
            catch (Exception ignored) {}
        }
        return String.join(", ", names);
    }

    private void applyFilter(String q) {
        String lower = q.toLowerCase().trim();
        List<JSONObject> filtered = new ArrayList<>();
        for (JSONObject item : allItems) {
            String name = item.optString("name", "").toLowerCase();
            if (lower.isEmpty() || name.contains(lower)) filtered.add(item);
        }
        adapter.replace(filtered);
    }

    private void openItem(JSONObject item) {
        if (item == null) return;
        Intent i = new Intent(this, MessagesActivity.class);
        i.putExtra("channel_id", item.optString("id"));
        if (MODE_DMS.equals(mode)) {
            i.putExtra("channel_name", item.optString("_display_name", item.optString("id")));
            i.putExtra("guild_id", "@me");   // signals DM — no guild subscription
        } else {
            i.putExtra("channel_name", item.optString("name", item.optString("id")));
            i.putExtra("guild_id", guildId);
        }
        startActivity(i);
    }

    // UI

    private void setSpinner(boolean on) {
        spinner.setVisibility(on ? View.VISIBLE : View.GONE);
    }
}
