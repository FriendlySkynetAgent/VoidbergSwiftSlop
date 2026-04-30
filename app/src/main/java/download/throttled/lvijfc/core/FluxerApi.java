package download.throttled.lvijfc.api;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Thin wrapper around the Fluxer REST API.
 * All methods block — call from a background thread.
 *
 *   GET  /users/@me                          → {username, ...}
 *   GET  /users/@me/guilds                   → [{id, name, icon, ...}]
 *   GET  /users/@me/channels                 → [{id, type, recipients:[...], last_message_id}]
 *                                              type 1 = DM, type 3 = group DM
 *   GET  /guilds/{id}/channels               → [{id, name, type, position}]
 *                                              type 0 = text, 5 = announcement
 *   GET  /channels/{id}/messages?limit=50    → [{id, content, author:{username}, timestamp}]
 *   POST /channels/{id}/messages             body: {content, message_reference?}
 */
public class FluxerApi {

    private static final String BASE = "https://api.fluxer.app/v1";
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http;
    private final String token;

    public FluxerApi(String token) {
        this.token = token;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    // HELPERS

    private Request.Builder authed(String url) {
        return new Request.Builder()
                .url(url)
                .header("Authorization", token)
                .header("Content-Type", "application/json");
    }

    /** GET → JSONObject. Throws ApiException on non-2xx. */
    private JSONObject getObj(String path) throws IOException, JSONException, ApiException {
        Request req = authed(BASE + path).get().build();
        try (Response r = http.newCall(req).execute()) {
            String body = requireBody(r);
            if (!r.isSuccessful()) throw new ApiException(r.code(), body);
            return new JSONObject(body);
        }
    }

    /** GET → JSONArray. Throws ApiException on non-2xx. */
    private JSONArray getArr(String path) throws IOException, JSONException, ApiException {
        Request req = authed(BASE + path).get().build();
        try (Response r = http.newCall(req).execute()) {
            String body = requireBody(r);
            if (!r.isSuccessful()) throw new ApiException(r.code(), body);
            return new JSONArray(body);
        }
    }

    private String requireBody(Response r) throws IOException {
        if (r.body() == null) throw new IOException("Empty response body");
        return r.body().string();
    }

    // AUTH CHECK

    /** Returns the current user object, or throws if the token is invalid. */
    public JSONObject getMe() throws IOException, JSONException, ApiException {
        return getObj("/users/@me");
    }

    // GUILDS

    /** Guilds the authed user belongs to. */
    public JSONArray getMyGuilds() throws IOException, JSONException, ApiException {
        return getArr("/users/@me/guilds");
    }

    // DIRECT MESSAGES

    /**
     * DM and group-DM channels for the authed user.
     * Each object has: id, type (1=DM, 3=group), recipients:[{id,username,...}],
     * last_message_id, name (group DMs only).
     */
    public JSONArray getPrivateChannels() throws IOException, JSONException, ApiException {
        return getArr("/users/@me/channels");
    }

    // CHANNELS

    /** All channels in a guild (type 0 = text, 5 = announcement). */
    public JSONArray getChannels(String guildId) throws IOException, JSONException, ApiException {
        return getArr("/guilds/" + guildId + "/channels");
    }

    // MESSAGES

    /** Last 50 messages, oldest-first. */
    public JSONArray getMessages(String channelId) throws IOException, JSONException, ApiException {
        HttpUrl url = HttpUrl.parse(BASE + "/channels/" + channelId + "/messages").newBuilder()
                .addQueryParameter("limit", "50")
                .build();
        Request req = authed(url.toString()).get().build();
        try (Response r = http.newCall(req).execute()) {
            String body = requireBody(r);
            if (!r.isSuccessful()) throw new ApiException(r.code(), body);
            // API returns newest-first; reverse to oldest-first for the list
            JSONArray raw = new JSONArray(body);
            JSONArray out = new JSONArray();
            for (int i = raw.length() - 1; i >= 0; i--) out.put(raw.get(i));
            return out;
        }
    }

    /**
     * Post a message to a channel.
     *
     * @param channelId  target channel
     * @param content    message text
     * @param replyToId  snowflake of the message being replied to, or null for a normal message
     */
    public void sendMessage(String channelId, String content, String replyToId)
            throws IOException, JSONException, ApiException {
        JSONObject body = new JSONObject().put("content", content);
        if (replyToId != null && !replyToId.isEmpty()) {
            body.put("message_reference",
                    new JSONObject().put("message_id", replyToId));
        }
        RequestBody rb = RequestBody.create(body.toString(), JSON_TYPE);
        Request req = authed(BASE + "/channels/" + channelId + "/messages").post(rb).build();
        try (Response r = http.newCall(req).execute()) {
            if (!r.isSuccessful()) throw new ApiException(r.code(), requireBody(r));
        }
    }

    /** Convenience overload for normal (non-reply) messages. */
    public void sendMessage(String channelId, String content)
            throws IOException, JSONException, ApiException {
        sendMessage(channelId, content, null);
    }

    // ERRORS

    public static class ApiException extends Exception {
        public final int code;
        public ApiException(int code, String message) {
            super("HTTP " + code + ": " + message);
            this.code = code;
        }
    }
}
