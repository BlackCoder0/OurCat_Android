package com.ourcat.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ourcat.app.R;

import java.util.ArrayList;
import java.util.Map;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.ViewHolder> {

    private final java.util.List<Map<String, Object>> items = new ArrayList<>();

    public void setItems(java.util.List<Map<String, Object>> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
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
        holder.author.setText(author != null ? author.toString() : "");
        holder.content.setText(content != null ? content.toString() : "");
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView author, content;

        ViewHolder(View itemView) {
            super(itemView);
            author = itemView.findViewById(R.id.item_comment_author);
            content = itemView.findViewById(R.id.item_comment_content);
        }
    }
}
