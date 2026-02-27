package com.ourcat.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.ourcat.app.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UserManageAdapter extends RecyclerView.Adapter<UserManageAdapter.ViewHolder> {

    public interface OnRoleToggleListener {
        void onToggle(long userId, int currentRole);
    }

    private final List<Map<String, Object>> users = new ArrayList<>();
    private OnRoleToggleListener listener;

    public void setListener(OnRoleToggleListener listener) {
        this.listener = listener;
    }

    public void setUsers(List<Map<String, Object>> list) {
        users.clear();
        users.addAll(list);
        notifyDataSetChanged();
    }

    public void updateUser(int position, Map<String, Object> updated) {
        if (position >= 0 && position < users.size()) {
            users.set(position, updated);
            notifyItemChanged(position);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_manage, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> user = users.get(position);
        String username = getStr(user, "username");
        String nickname = getStr(user, "nickname");
        int role = getInt(user, "role");
        long id = getLong(user, "id");

        holder.tvUsername.setText(username);
        holder.tvNickname.setText(nickname.isEmpty() ? username : nickname);

        String roleText;
        int roleColor;
        if (role >= 3) {
            roleText = "管理员";
            roleColor = 0xFF4CAF50;
        } else if (role == 2) {
            roleText = "志愿者";
            roleColor = 0xFF2196F3;
        } else {
            roleText = "普通用户";
            roleColor = 0xFF757575;
        }
        holder.tvRole.setText(roleText);
        holder.tvRole.setTextColor(roleColor);

        if (role >= 3) {
            holder.btnToggle.setVisibility(View.GONE);
        } else {
            holder.btnToggle.setVisibility(View.VISIBLE);
            holder.btnToggle.setText(role == 2 ? "撤销志愿者" : "授予志愿者");
            holder.btnToggle.setOnClickListener(v -> {
                if (listener != null) listener.onToggle(id, role);
            });
        }
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvUsername, tvNickname, tvRole;
        MaterialButton btnToggle;

        ViewHolder(View itemView) {
            super(itemView);
            tvUsername = itemView.findViewById(R.id.tv_username);
            tvNickname = itemView.findViewById(R.id.tv_nickname);
            tvRole = itemView.findViewById(R.id.tv_user_role);
            btnToggle = itemView.findViewById(R.id.btn_toggle_role);
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
        return o instanceof Number ? ((Number) o).longValue() : 0;
    }
}
