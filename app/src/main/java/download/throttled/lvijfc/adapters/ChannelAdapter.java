package download.throttled.lvijfc.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import download.throttled.lvijfc.R;

import org.json.JSONObject;

import java.util.List;

public class ChannelAdapter extends ArrayAdapter<JSONObject> {

    private final LayoutInflater inflater;

    public ChannelAdapter(Context ctx, List<JSONObject> items) {
        super(ctx, 0, items);
        inflater = LayoutInflater.from(ctx);
    }

    public void replace(List<JSONObject> items) {
        clear();
        addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder vh;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_channel, parent, false);
            vh = new ViewHolder(convertView);
            convertView.setTag(vh);
        } else {
            vh = (ViewHolder) convertView.getTag();
        }

        JSONObject ch = getItem(position);
        if (ch != null) {
            int type = ch.optInt("type", 0);
            // KISS-style: type indicator as a prefix character, no icons needed
            String prefix = (type == 5) ? "📣 " : "# ";
            vh.name.setText(prefix + ch.optString("name", "—"));
            String topic = ch.optString("topic", "");
            if (!topic.isEmpty()) {
                vh.topic.setText(topic);
                vh.topic.setVisibility(View.VISIBLE);
            } else {
                vh.topic.setVisibility(View.GONE);
            }
        }
        return convertView;
    }

    static class ViewHolder {
        final TextView name;
        final TextView topic;
        ViewHolder(View v) {
            name  = v.findViewById(R.id.item_name);
            topic = v.findViewById(R.id.item_meta);
        }
    }
}
