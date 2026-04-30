package download.throttled.lvijfc.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.TextView;

import download.throttled.lvijfc.R;
import download.throttled.lvijfc.adapters.MessageAdapter;
import download.throttled.lvijfc.core.FluxerApp;
import download.throttled.lvijfc.core.FluxerGateway;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat view for one channel (guild channel or DM).
 *
 * Reply flow:
 *   Long-press any message → haptic tick → reply strip appears above compose bar
 *   showing "↵ Replying to username: preview".  Tapping × (or sending) clears it.
 *   The reply_to message_id is included in the POST payload when set.
 */
public class MessagesActivity extends BaseActivity {

    private ListView  list;
    private View      emptyView;
    private TextView  messageInput;
    private View      sendBtn;
    private View      backBtn;
    private View      spinner;
    private TextView  titleView;

    private View     mentionBanner;
    private TextView mentionBannerText;

    private View     replyStrip;
    private TextView replyStripText;
    private View     replyStripCancel;

    private MessageAdapter         adapter;
    private final List<JSONObject> messages = new ArrayList<>();

    private String  channelId;
    private String  channelName;
    private String  guildId;
    private boolean isDm;

    private boolean sending = false;

    // reply state

    private String replyToId      = null;   // snowflake of the message being replied to
    private String replyToPreview = null;   // "username: content preview" for the strip label

    // GATEWAY LISTENERS

    private final FluxerGateway.MessageListener gatewayListener = newMessage -> {
        String newId = newMessage.optString("id");
        if (!newId.isEmpty()) {
            for (JSONObject m : messages) {
                if (newId.equals(m.optString("id"))) return;
            }
        }
        messages.add(newMessage);
        adapter.notifyDataSetChanged();
    };

    private final FluxerGateway.MentionListener mentionListener = (message, isDirect) -> {
        String fromChannel = message.optString("channel_id", "");
        if (fromChannel.equals(channelId)) return;

        JSONObject author = message.optJSONObject("author");
        String authorName = author != null ? author.optString("username", "Someone") : "Someone";
        String preview    = message.optString("content", "");
        if (preview.length() > 60) preview = preview.substring(0, 60) + "…";

        String label = isDirect
                ? authorName + " mentioned you: " + preview
                : authorName + " pinged @everyone: " + preview;

        showMentionBanner(label);
    };

    // LIFECYCLE

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (api() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        channelId   = getIntent().getStringExtra("channel_id");
        channelName = getIntent().getStringExtra("channel_name");
        guildId     = getIntent().getStringExtra("guild_id");
        isDm        = "@me".equals(guildId) || guildId == null || guildId.isEmpty();

        setContentView(R.layout.activity_messages);

        list              = findViewById(R.id.list);
        emptyView         = findViewById(android.R.id.empty);
        messageInput      = findViewById(R.id.message_input);
        sendBtn           = findViewById(R.id.send_btn);
        backBtn           = findViewById(R.id.back_btn);
        spinner           = findViewById(R.id.spinner);
        titleView         = findViewById(R.id.title);
        mentionBanner     = findViewById(R.id.mention_banner);
        mentionBannerText = findViewById(R.id.mention_banner_text);
        replyStrip        = findViewById(R.id.reply_strip);
        replyStripText    = findViewById(R.id.reply_strip_text);
        replyStripCancel  = findViewById(R.id.reply_strip_cancel);

        titleView.setText(channelName != null
                ? (isDm ? channelName : "#" + channelName)
                : "Channel");

        list.setStackFromBottom(true);
        list.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);

        adapter = new MessageAdapter(this, messages);
        list.setAdapter(adapter);
        list.setEmptyView(emptyView);

        backBtn.setOnClickListener(v -> finish());
        sendBtn.setOnClickListener(v -> sendMessage());

        if (mentionBanner != null) {
            mentionBanner.setOnClickListener(v -> hideMentionBanner());
        }

        replyStripCancel.setOnClickListener(v -> clearReply());

        // long-press to reply
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            JSONObject msg = adapter.getItem(position);
            if (msg == null) return false;

            JSONObject auth = msg.optJSONObject("author");
            String username = auth != null ? auth.optString("username", "?") : "?";
            String content  = msg.optString("content", "");
            if (content.isEmpty()) content = "📎 attachment";
            if (content.length() > 60) content = content.substring(0, 60) + "…";

            replyToId      = msg.optString("id");
            replyToPreview = username + ": " + content;

            replyStripText.setText("↵ Replying to " + replyToPreview);
            replyStrip.setVisibility(View.VISIBLE);

            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            messageInput.requestFocus();

            return true;   // consume the long-press
        });

        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); return true; }
            return false;
        });

        messageInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                sendBtn.setAlpha(s.length() > 0 ? 1f : 0.4f);
            }
        });
        sendBtn.setAlpha(0.4f);

        loadMessages(true);
    }

    @Override
    protected void onResume() {
        super.onResume();

        FluxerGateway gw = FluxerApp.get(this).getGateway();
        if (gw != null) {
            gw.setListener(channelId, gatewayListener);
            gw.setMentionListener(mentionListener);
            gw.reconnectIfNeeded();

            if (!isDm) {
                gw.subscribeToChannel(guildId, channelId);
            }
        }

        if (adapter != null) {
            loadMessages(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FluxerGateway gw = FluxerApp.get(this).getGateway();
        if (gw != null) {
            gw.setListener(null, null);
            gw.setMentionListener(null);
        }
    }

    // reply helpers

    private void clearReply() {
        replyToId      = null;
        replyToPreview = null;
        replyStrip.setVisibility(View.GONE);
    }

    // MENTION BANNER

    private static final long BANNER_AUTO_HIDE_MS = 5_000;
    private final Runnable hideBannerRunnable = this::hideMentionBanner;

    private void showMentionBanner(String text) {
        if (mentionBanner == null) return;
        mentionBannerText.setText(text);
        mentionBanner.setVisibility(View.VISIBLE);
        mentionBanner.animate().alpha(1f).setDuration(200).start();
        ui.removeCallbacks(hideBannerRunnable);
        ui.postDelayed(hideBannerRunnable, BANNER_AUTO_HIDE_MS);
    }

    private void hideMentionBanner() {
        if (mentionBanner == null) return;
        mentionBanner.animate().alpha(0f).setDuration(200).withEndAction(
                () -> mentionBanner.setVisibility(View.GONE)
        ).start();
        ui.removeCallbacks(hideBannerRunnable);
    }

    // DATA

    private void loadMessages(boolean showSpinner) {
        if (showSpinner) setSpinner(true);
        bg(() -> api().getMessages(channelId), raw -> {
            messages.clear();
            for (int i = 0; i < raw.length(); i++) {
                try { messages.add(raw.getJSONObject(i)); } catch (Exception ignored) {}
            }
            adapter.notifyDataSetChanged();
            if (showSpinner) setSpinner(false);
        }, () -> {
            if (showSpinner) setSpinner(false);
        });
    }

    private void sendMessage() {
        String content = messageInput.getText().toString().trim();
        if (TextUtils.isEmpty(content) || sending) return;

        sending = true;
        sendBtn.setEnabled(false);
        messageInput.setEnabled(false);

        // Capture reply id now; clear UI immediately so it feels responsive.
        final String sendingReplyTo = replyToId;
        clearReply();

        bg(() -> {
            api().sendMessage(channelId, content, sendingReplyTo);
            return api().getMessages(channelId);
        }, raw -> {
            messageInput.setText("");
            messages.clear();
            for (int i = 0; i < raw.length(); i++) {
                try { messages.add(raw.getJSONObject(i)); } catch (Exception ignored) {}
            }
            adapter.notifyDataSetChanged();
            sending = false;
            sendBtn.setEnabled(true);
            messageInput.setEnabled(true);
        }, () -> {
            sending = false;
            sendBtn.setEnabled(true);
            messageInput.setEnabled(true);
        });
    }

    // UI

    private void setSpinner(boolean on) {
        spinner.setVisibility(on ? View.VISIBLE : View.GONE);
    }
}
