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

public class MyCommentAdapter extends RecyclerView.Adapter<MyCommentAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onClick(Map<String, Object> item);
    }

    private final List<Map<String, Object>> items = new ArrayList<>();
    private OnItemClickListener listener;

    public void setItems(List<Map<String, Object>> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_my_comment, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> m = items.get(position);
        String source = getStr(m, "source");
        String postTitle = getStr(m, "postTitle");
        String content = getStr(m, "content");
        String time = getStr(m, "createdAt");

        if ("square".equals(source)) {
            holder.tvPostTitle.setText(postTitle.isEmpty() ? "广场广播" : "广场: " + postTitle);
        } else {
            holder.tvPostTitle.setText(postTitle.isEmpty() ? "帖子" : "回复: " + postTitle);
        }
        holder.tvContent.setText(content);
        holder.tvTime.setText(time);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(m);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvPostTitle, tvContent, tvTime;

        ViewHolder(View itemView) {
            super(itemView);
            tvPostTitle = itemView.findViewById(R.id.tv_post_title);
            tvContent = itemView.findViewById(R.id.tv_comment_content);
            tvTime = itemView.findViewById(R.id.tv_time);
        }
    }

    private static String getStr(Map<String, Object> m, String key) {
        Object o = m.get(key);
        return o != null ? o.toString() : "";
    }
}
