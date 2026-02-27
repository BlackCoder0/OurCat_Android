package com.ourcat.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ourcat.app.R;
import com.ourcat.app.models.PostItem;

import java.util.ArrayList;
import java.util.List;

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(PostItem item);
    }

    private final List<PostItem> items = new ArrayList<>();
    private OnItemClickListener listener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<PostItem> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_post, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PostItem item = items.get(position);

        String titleText = item.getTitle();
        if (item.isPinned()) titleText = "[置顶] " + titleText;
        holder.title.setText(titleText);

        String author = item.getAuthorName();
        holder.author.setText(author != null && !author.isEmpty() ? author : "匿名");

        holder.likes.setText("👍 " + item.getLikes());
        holder.commentsHint.setText("👎 " + item.getDislikes());

        String firstImageUrl = getFirstImageUrl(item.getImages());
        if (firstImageUrl != null) {
            holder.image.setVisibility(View.VISIBLE);
            Glide.with(holder.itemView.getContext())
                    .load(firstImageUrl)
                    .centerCrop()
                    .into(holder.image);
        } else {
            holder.image.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(item);
        });
    }

    private String getFirstImageUrl(String imagesJson) {
        if (imagesJson == null || imagesJson.isEmpty()) return null;
        try {
            List<String> urls = new Gson().fromJson(imagesJson, new TypeToken<List<String>>() {}.getType());
            if (urls != null && !urls.isEmpty()) return urls.get(0);
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title, author, likes, commentsHint;
        ImageView image;

        ViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.item_post_title);
            author = itemView.findViewById(R.id.item_post_author);
            likes = itemView.findViewById(R.id.item_post_likes);
            commentsHint = itemView.findViewById(R.id.item_post_comments_hint);
            image = itemView.findViewById(R.id.item_post_image);
        }
    }
}
