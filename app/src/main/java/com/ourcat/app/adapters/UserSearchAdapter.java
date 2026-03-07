package com.ourcat.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ourcat.app.R;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UserSearchAdapter extends RecyclerView.Adapter<UserSearchAdapter.ViewHolder> {

    private final List<Map<String, Object>> items = new ArrayList<>();
    public interface OnUserClickListener {
        void onUserClick(long userId);
    }
    private OnUserClickListener onUserClickListener;

    public void setItems(List<Map<String, Object>> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public void setOnUserClickListener(OnUserClickListener l) {
        this.onUserClickListener = l;
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
        String avatarUrl = getStr(m, "avatarUrl");
        int role = getInt(m, "role");
        long userId = getLong(m, "id");

        holder.tvNickname.setText(nickname.isEmpty() ? username : nickname);
        holder.tvUsername.setText("@" + username);
        if (!avatarUrl.isEmpty()) {
            Glide.with(holder.itemView)
                    .load(avatarUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_default_avatar)
                    .into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_default_avatar);
        }

        if (role >= 3) {
            holder.tvRoleBadge.setText("管理员");
            holder.tvRoleBadge.setTextColor(0xFF4CAF50);
        } else if (role == 2) {
            holder.tvRoleBadge.setText("志愿者");
            holder.tvRoleBadge.setTextColor(0xFF2196F3);
        } else {
            holder.tvRoleBadge.setText("");
        }

        holder.itemView.setOnClickListener(v -> {
            if (onUserClickListener != null && userId > 0) {
                onUserClickListener.onUserClick(userId);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNickname, tvUsername, tvRoleBadge;
        ImageView ivAvatar;

        ViewHolder(View itemView) {
            super(itemView);
            tvNickname = itemView.findViewById(R.id.tv_nickname);
            tvUsername = itemView.findViewById(R.id.tv_username);
            tvRoleBadge = itemView.findViewById(R.id.tv_role_badge);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
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

    private static long getLong(Map<String, Object> m, String key) {
        Object o = m.get(key);
        if (o instanceof Number) return ((Number) o).longValue();
        try {
            return Long.parseLong(o != null ? o.toString() : "0");
        } catch (Exception e) {
            return 0L;
        }
    }
}
