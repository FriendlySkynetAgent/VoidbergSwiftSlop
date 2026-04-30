package download.throttled.lvijfc.core;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Fluxer WebSocket gateway client.
 *
 * Listeners:
 *   MessageListener  — called when a MESSAGE_CREATE arrives for the channel
 *                      currently open in MessagesActivity.
 *   MentionListener  — called for every MESSAGE_CREATE that mentions the
 *                      logged-in user (direct mention, @everyone, @here),
 *                      regardless of which channel is being watched.
 *                      This is the hook UnifiedPush notifications will
 *                      replace / augment later.
 */
public class FluxerGateway {

    private static final String TAG         = "FluxerGateway";
    private static final String GATEWAY_URL = "wss://gateway.fluxer.app/?v=1&encoding=json";

    private static final int OP_DISPATCH      = 0;
    private static final int OP_HEARTBEAT     = 1;
    private static final int OP_IDENTIFY      = 2;
    private static final int OP_HELLO         = 10;
    private static final int OP_HEARTBEAT_ACK = 11;

    private static final long[] RECONNECT_DELAYS_MS = {1_000, 2_000, 4_000, 8_000, 16_000, 30_000};

    // LISTENER INTERFACES

    public interface MessageListener {
        /** Called on the main thread when a MESSAGE_CREATE arrives for the watched channel. */
        void onNewMessage(JSONObject message);
    }

    public interface MentionListener {
        /**
         * Called on the main thread when any MESSAGE_CREATE mentions the
         * logged-in user (direct <@id>, @everyone, or @here).
         *
         * @param message  the full message object
         * @param isDirect true when the user is mentioned by id (not just @everyone/@here)
         */
        void onMention(JSONObject message, boolean isDirect);
    }

    // STATE

    private final OkHttpClient http = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build();

    private final Handler       ui               = new Handler(Looper.getMainLooper());
    private final AtomicBoolean intentionalClose = new AtomicBoolean(false);

    private WebSocket ws;
    private String    token;
    private String    myUserId;        // set in connect(); used for mention detection
    private String    sessionId;
    private int       lastSeq          = -1;
    private int       reconnectAttempt = 0;

    private String          watchedChannelId;
    private MessageListener messageListener;
    private MentionListener mentionListener;

    private String pendingGuildId;
    private String pendingChannelId;
    private String prevGuildId;
    private String prevChannelId;

    // PUBLIC API

    /**
     * @param myUserId the logged-in user's snowflake ID (used for mention detection).
     *                 Pass empty string if not yet available; detection will still
     *                 work for @everyone/@here.
     */
    public void connect(String token, String myUserId) {
        this.token    = token;
        this.myUserId = myUserId != null ? myUserId : "";
        intentionalClose.set(false);
        reconnectAttempt = 0;
        openSocket();
    }

    /** Convenience overload kept for callers that don't have the id yet. */
    public void connect(String token) {
        connect(token, "");
    }

    /** Update the stored user-id after login completes (so connect() can be called early). */
    public void setMyUserId(String id) {
        this.myUserId = id != null ? id : "";
    }

    public void reconnectIfNeeded() {
        if (ws != null || intentionalClose.get()) return;
        Log.i(TAG, "reconnectIfNeeded: socket is gone — reconnecting");
        reconnectAttempt = 0;
        openSocket();
    }

    /** Register a listener for the channel currently open on screen. Pass null to clear. */
    public void setListener(String channelId, MessageListener l) {
        this.watchedChannelId = channelId;
        this.messageListener  = l;
    }

    /**
     * Register a global mention listener.
     * Typically set once in MessagesActivity (or a future service).
     * Pass null to clear.
     */
    public void setMentionListener(MentionListener l) {
        this.mentionListener = l;
    }

    public void subscribeToChannel(String guildId, String channelId) {
        pendingGuildId   = guildId;
        pendingChannelId = channelId;
        if (ws == null) {
            Log.d(TAG, "subscribeToChannel: socket not ready, will replay on READY");
            return;
        }
        sendSubscription(guildId, channelId);
    }

    public void disconnect() {
        intentionalClose.set(true);
        stopHeartbeat();
        if (ws != null) {
            ws.close(1000, "logout");
            ws = null;
        }
    }

    public boolean isConnected() { return ws != null; }

    // SOCKET LIFECYCLE

    private void openSocket() {
        Request req = new Request.Builder().url(GATEWAY_URL).build();
        ws = http.newWebSocket(req, new GatewayListener());
        Log.d(TAG, "Connecting to " + GATEWAY_URL);
    }

    private void scheduleReconnect() {
        if (intentionalClose.get()) return;
        long delay = RECONNECT_DELAYS_MS[Math.min(reconnectAttempt, RECONNECT_DELAYS_MS.length - 1)];
        reconnectAttempt++;
        Log.d(TAG, "Reconnecting in " + delay + " ms (attempt " + reconnectAttempt + ")");
        ui.postDelayed(this::openSocket, delay);
    }

    // HEARTBEAT

    private long    heartbeatInterval;
    private boolean ackReceived = true;

    private final Runnable heartbeatRunnable = new Runnable() {
        @Override public void run() {
            if (ws == null) return;
            if (!ackReceived) {
                Log.w(TAG, "Missed heartbeat ACK, reconnecting");
                ws.cancel();
                return;
            }
            ackReceived = false;
            sendJson(heartbeatPayload());
            ui.postDelayed(this, heartbeatInterval);
        }
    };

    private void startHeartbeat(long intervalMs) {
        heartbeatInterval = intervalMs;
        ackReceived = true;
        ui.removeCallbacks(heartbeatRunnable);
        long jitter = (long) (intervalMs * Math.random());
        ui.postDelayed(heartbeatRunnable, jitter);
    }

    private void stopHeartbeat() {
        ui.removeCallbacks(heartbeatRunnable);
    }

    // SEND HELPERS

    private void sendJson(JSONObject payload) {
        if (ws != null) ws.send(payload.toString());
    }

    private JSONObject heartbeatPayload() {
        try {
            JSONObject p = new JSONObject();
            p.put("op", OP_HEARTBEAT);
            p.put("d", lastSeq == -1 ? JSONObject.NULL : lastSeq);
            return p;
        } catch (Exception e) { return new JSONObject(); }
    }

    private JSONObject identifyPayload() {
        try {
            JSONObject props = new JSONObject();
            props.put("os",                  "Android");
            props.put("os_version",          "");
            props.put("browser",             "fluxer-android");
            props.put("browser_version",     "");
            props.put("device",              "android");
            props.put("system_locale",       "en-US");
            props.put("locale",              "en-US");
            props.put("user_agent",          "fluxer-android");
            props.put("build_timestamp",     "0");
            props.put("build_sha",           "");
            props.put("build_number",        0);
            props.put("desktop_app_version", JSONObject.NULL);
            props.put("desktop_app_channel", JSONObject.NULL);
            props.put("desktop_arch",        "");
            props.put("desktop_os",          "");
            props.put("latitude",            JSONObject.NULL);
            props.put("longitude",           JSONObject.NULL);

            JSONObject presence = new JSONObject();
            presence.put("status",        "online");
            presence.put("afk",           false);
            presence.put("mobile",        true);
            presence.put("custom_status", JSONObject.NULL);

            JSONObject d = new JSONObject();
            d.put("token",      token);
            d.put("properties", props);
            d.put("presence",   presence);
            d.put("flags",      2);

            JSONObject p = new JSONObject();
            p.put("op", OP_IDENTIFY);
            p.put("d",  d);
            return p;
        } catch (Exception e) { return new JSONObject(); }
    }

    // SUBSCRIPTION HELPERS

    private void sendSubscription(String guildId, String channelId) {
        try {
            org.json.JSONArray pair = new org.json.JSONArray();
            pair.put(0); pair.put(99);
            org.json.JSONArray range = new org.json.JSONArray();
            range.put(pair);

            JSONObject step1Sub = new JSONObject();
            step1Sub.put("active", true);
            step1Sub.put("sync",   true);
            sendOp14(guildId, step1Sub);

            if (prevGuildId != null && prevChannelId != null) {
                JSONObject emptyChannels = new JSONObject();
                emptyChannels.put(prevChannelId, new org.json.JSONArray());
                JSONObject unsubSub = new JSONObject();
                unsubSub.put("member_list_channels", emptyChannels);
                sendOp14(prevGuildId, unsubSub);
            }

            JSONObject memberListChannels = new JSONObject();
            memberListChannels.put(channelId, range);
            JSONObject step3Sub = new JSONObject();
            step3Sub.put("active",               true);
            step3Sub.put("sync",                 true);
            step3Sub.put("member_list_channels", memberListChannels);
            sendOp14(guildId, step3Sub);

            Log.d(TAG, "Subscribed to guild=" + guildId + " channel=" + channelId);
            prevGuildId   = guildId;
            prevChannelId = channelId;
        } catch (Exception e) {
            Log.e(TAG, "sendSubscription error: " + e.getMessage());
        }
    }

    private void sendOp14(String guildId, JSONObject guildSub) throws Exception {
        JSONObject subscriptions = new JSONObject();
        subscriptions.put(guildId, guildSub);
        JSONObject d = new JSONObject();
        d.put("subscriptions", subscriptions);
        JSONObject payload = new JSONObject();
        payload.put("op", 14);
        payload.put("d", d);
        sendJson(payload);
    }

    // MENTION DETECTION

    /**
     * Returns true if this message mentions the logged-in user directly
     * (i.e. their id appears in the mentions array).
     */
    private boolean isDirectMention(JSONObject data) {
        if (myUserId.isEmpty()) return false;
        JSONArray mentions = data.optJSONArray("mentions");
        if (mentions == null) return false;
        for (int i = 0; i < mentions.length(); i++) {
            try {
                JSONObject u = mentions.getJSONObject(i);
                if (myUserId.equals(u.optString("id"))) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    /**
     * Returns true if the message content contains @everyone or @here,
     * and the server flagged it (mention_everyone field).
     */
    private boolean isEveryoneMention(JSONObject data) {
        return data.optBoolean("mention_everyone", false);
    }

    // MESSAGE HANDLING

    private void handleDispatch(String eventName, JSONObject data) {
        Log.d(TAG, "Dispatch: " + eventName);
        switch (eventName) {
            case "READY":
                sessionId        = data.optString("session_id");
                reconnectAttempt = 0;
                Log.i(TAG, "Gateway READY, session=" + sessionId);
                if (pendingGuildId != null && pendingChannelId != null) {
                    prevGuildId   = null;
                    prevChannelId = null;
                    Log.d(TAG, "Replaying subscription after READY");
                    sendSubscription(pendingGuildId, pendingChannelId);
                }
                break;

            case "MESSAGE_CREATE": {
                String channelId = data.optString("channel_id");
                boolean direct   = isDirectMention(data);
                boolean everyone = isEveryoneMention(data);

                Log.d(TAG, "MESSAGE_CREATE channel=" + channelId
                        + " watched=" + watchedChannelId
                        + " direct=" + direct
                        + " everyone=" + everyone
                        + " mentionListener=" + (mentionListener != null)
                        + " myUserId=" + myUserId
                        + " mentions=" + data.optJSONArray("mentions")
                        + " mention_everyone=" + data.optBoolean("mention_everyone", false));

                // ── per-channel listener (MessagesActivity) ──
                if (channelId.equals(watchedChannelId) && messageListener != null) {
                    final JSONObject msg = data;
                    ui.post(() -> messageListener.onNewMessage(msg));
                }

                // ── global mention listener ──────────────────
                if (mentionListener != null && (direct || everyone)) {
                    final JSONObject msg = data;
                    ui.post(() -> mentionListener.onMention(msg, direct));
                }
                break;
            }

            default:
                Log.d(TAG, "Dispatch ignored: " + eventName);
                break;
        }
    }

    // WebSocketListener

    private class GatewayListener extends WebSocketListener {

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            Log.d(TAG, "WebSocket opened");
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                JSONObject payload = new JSONObject(text);
                int op  = payload.optInt("op", -1);
                int seq = payload.optInt("s",  -1);
                if (seq != -1) lastSeq = seq;

                switch (op) {
                    case OP_HELLO: {
                        long interval = payload.getJSONObject("d")
                                               .getLong("heartbeat_interval");
                        ui.post(() -> startHeartbeat(interval));
                        sendJson(identifyPayload());
                        break;
                    }
                    case OP_DISPATCH: {
                        String t = payload.optString("t");
                        JSONObject d = payload.optJSONObject("d");
                        if (d != null) handleDispatch(t, d);
                        break;
                    }
                    case OP_HEARTBEAT:
                        sendJson(heartbeatPayload());
                        break;
                    case OP_HEARTBEAT_ACK:
                        ackReceived = true;
                        break;
                    default:
                        Log.d(TAG, "Unhandled op=" + op);
                }
            } catch (Exception e) {
                Log.e(TAG, "Parse error: " + e.getMessage());
            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            Log.i(TAG, "Gateway closing: " + code + " " + reason);
            webSocket.close(1000, null);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            Log.i(TAG, "Gateway closed: " + code);
            ws = null;
            stopHeartbeat();
            if (!intentionalClose.get()) scheduleReconnect();
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            Log.w(TAG, "Gateway failure: " + t.getMessage());
            ws = null;
            stopHeartbeat();
            if (!intentionalClose.get()) scheduleReconnect();
        }
    }
}
