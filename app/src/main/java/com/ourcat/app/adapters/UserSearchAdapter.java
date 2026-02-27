package com.ourcat.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ourcat.app.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UserSearchAdapter extends RecyclerView.Adapter<UserSearchAdapter.ViewHolder> {

    private final List<Map<String, Object>> items = new ArrayList<>();

    public void setItems(List<Map<String, Object>> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_search, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> m = items.get(position);
        String nickname = getStr(m, "nickname");
        String username = getStr(m, "username");
        int role = getInt(m, "role");

        holder.tvNickname.setText(nickname.isEmpty() ? username : nickname);
        holder.tvUsername.setText("@" + username);

        if (role >= 3) {
            holder.tvRoleBadge.setText("管理员");
            holder.tvRoleBadge.setTextColor(0xFF4CAF50);
        } else if (role == 2) {
            holder.tvRoleBadge.setText("志愿者");
            holder.tvRoleBadge.setTextColor(0xFF2196F3);
        } else {
            holder.tvRoleBadge.setText("");
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNickname, tvUsername, tvRoleBadge;

        ViewHolder(View itemView) {
            super(itemView);
            tvNickname = itemView.findViewById(R.id.tv_nickname);
            tvUsername = itemView.findViewById(R.id.tv_username);
            tvRoleBadge = itemView.findViewById(R.id.tv_role_badge);
        }
    }

    private static String getStr(Map<String, Object> m, String key) {
        Object o = m.get(key);
        return o != null ? o.toString() : "";
    }

    private static int getInt(Map<String, Object> m, String key) {
        Object o = m.get(key);
        return o instanceof Number ? ((Number) o).intValue() : 0;
    }
}
