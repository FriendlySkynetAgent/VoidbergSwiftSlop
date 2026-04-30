package download.throttled.lvijfc.adapters;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import download.throttled.lvijfc.R;
import download.throttled.lvijfc.core.ImageCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageAdapter extends BaseAdapter {

    private static final String TAG = "MessageAdapter";

    private static final int TYPE_OTHER = 0;
    private static final int TYPE_MINE  = 1;

    private static final int REPLY_PREVIEW_MAX = 60;

    // <:name:id> and <a:name:id> custom emojis
    private static final Pattern EMOJI_PAT =
            Pattern.compile("<(a?):([^:>]+):(\\d+)>");

    // <@id> and <@!id> user mentions
    private static final Pattern USER_MENTION_PAT =
            Pattern.compile("<@!?(\\d+)>");

    // @everyone and @here broadcast pings
    private static final Pattern BROADCAST_PAT =
            Pattern.compile("@(everyone|here)");

    // Parses the UTC part of an ISO-8601 timestamp; re-formatted in device local time.
    private static final SimpleDateFormat TS_PARSE;
    private static final SimpleDateFormat TS_FORMAT;
    static {
        TS_PARSE = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        TS_PARSE.setTimeZone(TimeZone.getTimeZone("UTC"));
        TS_FORMAT = new SimpleDateFormat("HH:mm", Locale.getDefault());
        TS_FORMAT.setTimeZone(TimeZone.getDefault());
    }

    private final Context          ctx;
    private final LayoutInflater   inflater;
    private final List<JSONObject> items;
    private final String           myUsername;
    private final String           myUserId;
    private final int              emojiPx;
    private final int              mentionColor;

    public MessageAdapter(Context ctx, List<JSONObject> items) {
        this.ctx        = ctx;
        this.inflater   = LayoutInflater.from(ctx);
        this.items      = items;

        SharedPreferences prefs = ctx.getSharedPreferences("fluxer", Context.MODE_PRIVATE);
        this.myUsername   = prefs.getString("username", "");
        this.myUserId     = prefs.getString("user_id",  "");
        this.emojiPx      = dp(20);
        this.mentionColor = ctx.getResources().getColor(R.color.accent, null);
    }

    @Override public int getCount()            { return items.size(); }
    @Override public JSONObject getItem(int p) { return items.get(p); }
    @Override public long getItemId(int p)     { return p; }
    @Override public int getViewTypeCount()    { return 2; }

    @Override
    public int getItemViewType(int pos) {
        JSONObject msg  = getItem(pos);
        JSONObject auth = msg.optJSONObject("author");
        String author   = auth != null ? auth.optString("username", "") : "";
        return author.equals(myUsername) ? TYPE_MINE : TYPE_OTHER;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        int type = getItemViewType(position);
        ViewHolder vh;

        if (convertView == null) {
            int layout = (type == TYPE_MINE)
                    ? R.layout.item_message_mine
                    : R.layout.item_message_other;
            convertView = inflater.inflate(layout, parent, false);
            vh = new ViewHolder(convertView);
            convertView.setTag(vh);
        } else {
            vh = (ViewHolder) convertView.getTag();
        }

        JSONObject msg  = getItem(position);
        JSONObject auth = msg.optJSONObject("author");
        String author   = auth != null ? auth.optString("username", "?") : "?";

        vh.author.setText(author);

        String localTime = formatTimestamp(msg.optString("timestamp", ""));
        if (!localTime.isEmpty()) {
            vh.time.setText(localTime);
            vh.time.setVisibility(View.VISIBLE);
        } else {
            vh.time.setVisibility(View.GONE);
        }

        // REPLY INDICATOR
        JSONObject ref = msg.optJSONObject("referenced_message");
        if (ref != null) {
            JSONObject refAuthor = ref.optJSONObject("author");
            String refName = refAuthor != null
                    ? refAuthor.optString("username", "?") : "?";
            String refContent = ref.optString("content", "");
            if (refContent.length() > REPLY_PREVIEW_MAX) {
                refContent = refContent.substring(0, REPLY_PREVIEW_MAX) + "…";
            }
            if (refContent.isEmpty()) refContent = "📎 attachment";
            vh.replyIndicator.setText("↵ " + refName + ": " + refContent);
            vh.replyIndicator.setVisibility(View.VISIBLE);
        } else {
            vh.replyIndicator.setVisibility(View.GONE);
        }

        // Build mentions lookup: id → username
        JSONArray mentionedUsers = msg.optJSONArray("mentions");
        java.util.Map<String, String> mentionNames = new java.util.HashMap<>();
        if (mentionedUsers != null) {
            for (int i = 0; i < mentionedUsers.length(); i++) {
                try {
                    JSONObject u = mentionedUsers.getJSONObject(i);
                    mentionNames.put(u.optString("id"), u.optString("username", "unknown"));
                } catch (Exception ignored) {}
            }
        }

        bindContent(vh.content, msg.optString("content", ""), mentionNames);

        String msgId    = msg.optString("id", String.valueOf(position));
        String imageUrl = firstImageUrl(msg.optJSONArray("attachments"));

        if (imageUrl != null) {
            vh.image.setVisibility(View.VISIBLE);
            vh.image.setTag(msgId);
            ImageCache.load(imageUrl, dp(260), dp(200), bmp -> {
                if (msgId.equals(vh.image.getTag())) {
                    vh.image.setImageBitmap(bmp);
                }
            });
        } else {
            vh.image.setVisibility(View.GONE);
            vh.image.setImageDrawable(null);
            vh.image.setTag(null);
        }

        return convertView;
    }

    // TIME FORMATTING

    /**
     * Converts an ISO-8601 UTC timestamp (e.g. "2024-03-15T14:30:00.000000+00:00")
     * to a short HH:mm string in the device's local timezone.
     * Falls back to an empty string on any parse failure.
     */
    private static String formatTimestamp(String ts) {
        if (ts == null || ts.length() < 19) return "";
        try {
            // Slice to exactly 19 chars so fractional seconds and timezone
            // suffixes are safely ignored before handing off to TS_PARSE.
            Date date;
            synchronized (TS_PARSE) {
                date = TS_PARSE.parse(ts.substring(0, 19));
            }
            if (date == null) return "";
            synchronized (TS_FORMAT) {
                return TS_FORMAT.format(date);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not parse timestamp: " + ts);
            return "";
        }
    }

    // CONTENT BINDING

    private void bindContent(TextView tv, String content,
                             java.util.Map<String, String> mentionNames) {

        if (!content.contains("<") && !content.contains("@")) {
            tv.setText(content);
            tv.setTag(null);
            return;
        }

        tv.setTag(content);

        SpannableStringBuilder ssb = new SpannableStringBuilder(content);

        List<String[]> emojiHits = new ArrayList<>();
        Matcher em = EMOJI_PAT.matcher(content);
        while (em.find()) {
            emojiHits.add(new String[]{
                    em.group(1),
                    em.group(2),
                    em.group(3),
                    String.valueOf(em.start()),
                    String.valueOf(em.end())
            });
        }

        int offset = 0;
        for (String[] hit : emojiHits) {
            String name      = hit[1];
            String id        = hit[2];
            boolean animated = "a".equals(hit[0]);
            int start = Integer.parseInt(hit[3]) - offset;
            int end   = Integer.parseInt(hit[4]) - offset;

            String url = "https://fluxerusercontent.com/emojis/" + id
                    + ".webp?animated=" + animated + "&size=96&quality=lossless";

            String placeholder = ":" + name + ":";
            ssb.replace(start, end, placeholder);
            offset += (end - start) - placeholder.length();

            AsyncDrawable drawable = new AsyncDrawable(emojiPx);
            ssb.setSpan(
                    new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM),
                    start, start + placeholder.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            WeakReference<TextView> ref     = new WeakReference<>(tv);
            String                  captKey = content;
            ImageCache.load(url, emojiPx, emojiPx, bmp -> {
                TextView target = ref.get();
                if (target != null && captKey.equals(target.getTag())) {
                    drawable.setBitmap(bmp);
                    CharSequence current = target.getText();
                    target.setText(null);
                    target.setText(current);
                }
            });
        }

        applyMentionSpans(ssb, mentionNames);
        applyBroadcastSpans(ssb);

        tv.setText(ssb);
    }

    private void applyMentionSpans(SpannableStringBuilder ssb,
                                   java.util.Map<String, String> mentionNames) {
        String plain = ssb.toString();
        Matcher m = USER_MENTION_PAT.matcher(plain);
        List<int[]> ranges = new ArrayList<>();
        List<String> replacements = new ArrayList<>();
        while (m.find()) {
            String id   = m.group(1);
            String name = mentionNames.containsKey(id) ? mentionNames.get(id) : id;
            ranges.add(new int[]{m.start(), m.end()});
            replacements.add("@" + name);
        }
        for (int i = ranges.size() - 1; i >= 0; i--) {
            int start = ranges.get(i)[0];
            int end   = ranges.get(i)[1];
            String rep = replacements.get(i);
            ssb.replace(start, end, rep);
            ssb.setSpan(
                    new ForegroundColorSpan(mentionColor),
                    start, start + rep.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
    }

    private void applyBroadcastSpans(SpannableStringBuilder ssb) {
        String plain = ssb.toString();
        Matcher m = BROADCAST_PAT.matcher(plain);
        List<int[]> ranges = new ArrayList<>();
        while (m.find()) ranges.add(new int[]{m.start(), m.end()});
        for (int i = ranges.size() - 1; i >= 0; i--) {
            ssb.setSpan(
                    new ForegroundColorSpan(mentionColor),
                    ranges.get(i)[0], ranges.get(i)[1],
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
    }

    // ATTACHMENT HELPERS

    private String firstImageUrl(JSONArray attachments) {
        if (attachments == null) return null;
        for (int i = 0; i < attachments.length(); i++) {
            try {
                JSONObject a = attachments.getJSONObject(i);
                String ct    = a.optString("content_type", "");
                if (ct.startsWith("image/") || ct.isEmpty()) {
                    String url = a.optString("url", "");
                    if (!url.isEmpty()) return url;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    // ASYNC DRAWABLE

    static class AsyncDrawable extends ColorDrawable {

        private Bitmap bitmap;
        private final int size;

        AsyncDrawable(int sizePx) {
            super(Color.TRANSPARENT);
            this.size = sizePx;
            setBounds(0, 0, sizePx, sizePx);
        }

        void setBitmap(Bitmap bmp) { this.bitmap = bmp; }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (bitmap != null && !bitmap.isRecycled()) {
                canvas.drawBitmap(bitmap, null, getBounds(), null);
            }
        }

        @Override public int getIntrinsicWidth()  { return size; }
        @Override public int getIntrinsicHeight() { return size; }
    }

    // VIEW HOLDER

    static class ViewHolder {
        final TextView  author;
        final TextView  replyIndicator;
        final TextView  content;
        final TextView  time;
        final ImageView image;

        ViewHolder(View v) {
            author         = v.findViewById(R.id.msg_author);
            replyIndicator = v.findViewById(R.id.reply_indicator);
            content        = v.findViewById(R.id.msg_content);
            time           = v.findViewById(R.id.msg_time);
            image          = v.findViewById(R.id.msg_image);
        }
    }

    // HELPERS

    private int dp(float dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                ctx.getResources().getDisplayMetrics()));
    }
}
