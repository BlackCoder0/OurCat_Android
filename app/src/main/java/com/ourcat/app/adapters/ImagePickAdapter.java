package com.ourcat.app.adapters;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.ourcat.app.R;

import java.util.ArrayList;
import java.util.List;

public class ImagePickAdapter extends RecyclerView.Adapter<ImagePickAdapter.ViewHolder> {

    public interface OnRemoveListener {
        void onRemove(int position);
    }

    private final List<Uri> uris = new ArrayList<>();
    private final List<String> existingUrls = new ArrayList<>();
    private OnRemoveListener removeListener;

    public void setOnRemoveListener(OnRemoveListener listener) {
        this.removeListener = listener;
    }

    public void addUri(Uri uri) {
        uris.add(uri);
        notifyItemInserted(existingUrls.size() + uris.size() - 1);
    }

    public void setExistingUrls(List<String> urls) {
        existingUrls.clear();
        if (urls != null) existingUrls.addAll(urls);
        notifyDataSetChanged();
    }

    public List<Uri> getNewUris() {
        return new ArrayList<>(uris);
    }

    public List<String> getExistingUrls() {
        return new ArrayList<>(existingUrls);
    }

    @Override
    public int getItemCount() {
        return existingUrls.size() + uris.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image_pick, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (position < existingUrls.size()) {
            Glide.with(holder.itemView).load(existingUrls.get(position)).centerCrop().into(holder.image);
        } else {
            Uri uri = uris.get(position - existingUrls.size());
            Glide.with(holder.itemView).load(uri).centerCrop().into(holder.image);
        }
        holder.remove.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos < 0) return;
            if (pos < existingUrls.size()) {
                existingUrls.remove(pos);
            } else {
                uris.remove(pos - existingUrls.size());
            }
            notifyItemRemoved(pos);
            if (removeListener != null) removeListener.onRemove(pos);
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView image, remove;
        ViewHolder(View v) {
            super(v);
            image = v.findViewById(R.id.iv_image);
            remove = v.findViewById(R.id.iv_remove);
        }
    }
}
