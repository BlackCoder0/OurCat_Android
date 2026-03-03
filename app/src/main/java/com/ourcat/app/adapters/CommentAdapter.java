package com.ourcat.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.ourcat.app.R;

import java.util.ArrayList;
import java.util.Map;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.ViewHolder> {

    public interface OnDeleteListener {
        void onDelete(long commentId);
    }

    private final java.util.List<Map<String, Object>> items = new ArrayList<>();
    private long currentUserId;
    private int currentUserRole;
    private OnDeleteListener deleteListener;

    public void setItems(java.util.List<Map<String, Object>> list) {
        items.clear();
        if (list != null)
            items.addAll(list);
        notifyDataSetChanged();
    }

    public void setCurrentUserId(long userId) {
        this.currentUserId = userId;
    }

    public void setCurrentUserRole(int role) {
        this.currentUserRole = role;
    }

    public void setOnDeleteListener(OnDeleteListener listener) {
        this.deleteListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> m = items.get(position);
        Object author = m.get("authorName");
        Object content = m.get("content");
        Object avatarUrl = m.get("authorAvatar");
        holder.author.setText(author != null ? author.toString() : "");
        holder.content.setText(content != null ? content.toString() : "");

        if (avatarUrl != null && !avatarUrl.toString().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(avatarUrl.toString())
                    .circleCrop()
                    .placeholder(R.drawable.ic_default_avatar)
                    .into(holder.avatar);
        } else {
            holder.avatar.setImageResource(R.drawable.ic_default_avatar);
        }

        long commentUserId = m.get("userId") instanceof Number ? ((Number) m.get("userId")).longValue() : 0;
        long commentId = m.get("id") instanceof Number ? ((Number) m.get("id")).longValue() : 0;

        View.OnClickListener profileListener = v -> {
            if (commentUserId != 0) {
                android.os.Bundle args = new android.os.Bundle();
                args.putLong("userId", commentUserId);
                androidx.navigation.Navigation.findNavController(v).navigate(R.id.user_profile, args);
            }
        };
        holder.avatar.setOnClickListener(profileListener);
        holder.author.setOnClickListener(profileListener);

        boolean isOwnComment = currentUserId != 0 && currentUserId == commentUserId;
        boolean canManage = currentUserRole >= 2;

        if (isOwnComment || canManage) {
            holder.btnMore.setVisibility(View.VISIBLE);
            holder.btnMore.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(v.getContext(), v);
                popup.getMenu().add("删除");
                popup.setOnMenuItemClickListener(item -> {
                    if ("删除".equals(item.getTitle()) && deleteListener != null) {
                        deleteListener.onDelete(commentId);
                    }
                    return true;
                });
                popup.show();
            });
        } else {
            holder.btnMore.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView author, content;
        ImageView btnMore, avatar;

        ViewHolder(View itemView) {
            super(itemView);
            author = itemView.findViewById(R.id.item_comment_author);
            content = itemView.findViewById(R.id.item_comment_content);
            btnMore = itemView.findViewById(R.id.btn_comment_more);
            avatar = itemView.findViewById(R.id.item_comment_avatar);
        }
    }
}
