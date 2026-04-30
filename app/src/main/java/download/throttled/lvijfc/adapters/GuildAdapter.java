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

public class GuildAdapter extends ArrayAdapter<JSONObject> {

    private final LayoutInflater inflater;

    public GuildAdapter(Context ctx, List<JSONObject> items) {
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
            convertView = inflater.inflate(R.layout.item_guild, parent, false);
            vh = new ViewHolder(convertView);
            convertView.setTag(vh);
        } else {
            vh = (ViewHolder) convertView.getTag();
        }

        JSONObject guild = getItem(position);
        if (guild != null) {
            vh.name.setText(guild.optString("name", "—"));
            // Show member count or description if present
            String desc = guild.optString("description", "");
            int memberCount = guild.optInt("approximate_member_count", -1);
            if (memberCount > 0) {
                vh.meta.setText(memberCount + " members");
                vh.meta.setVisibility(View.VISIBLE);
            } else if (!desc.isEmpty()) {
                vh.meta.setText(desc);
                vh.meta.setVisibility(View.VISIBLE);
            } else {
                vh.meta.setVisibility(View.GONE);
            }
        }
        return convertView;
    }

    static class ViewHolder {
        final TextView name;
        final TextView meta;
        ViewHolder(View v) {
            name = v.findViewById(R.id.item_name);
            meta = v.findViewById(R.id.item_meta);
        }
    }
}
