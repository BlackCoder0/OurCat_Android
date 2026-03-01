package com.ourcat.app.adapters;

import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.ourcat.app.utils.ImageUtils;

import java.util.ArrayList;
import java.util.List;

public class ImagePagerAdapter extends RecyclerView.Adapter<ImagePagerAdapter.ViewHolder> {

    private final List<String> urls = new ArrayList<>();

    public void setUrls(List<String> list) {
        urls.clear();
        if (list != null) urls.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ImageView iv = new ImageView(parent.getContext());
        iv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return new ViewHolder(iv);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String url = urls.get(position);
        Glide.with(holder.itemView.getContext())
                .load(url)
                .into((ImageView) holder.itemView);

        holder.itemView.setOnLongClickListener(v -> {
            new AlertDialog.Builder(v.getContext())
                    .setItems(new String[]{"保存图片"}, (dialog, which) -> {
                        if (which == 0) {
                            ImageUtils.saveImage(v.getContext(), url);
                        }
                    })
                    .show();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return urls.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ViewHolder(View v) { super(v); }
    }
}
