package com.ourcat.app.utils;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.ourcat.app.R;

public final class LoadingDialogHelper {

    private LoadingDialogHelper() {
    }

    public static Dialog create(@NonNull Context context, @NonNull String text) {
        Dialog dialog = new Dialog(context);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        if (dialog.getWindow() != null) {
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_loading, null);
        TextView titleView = view.findViewById(R.id.loading_title);
        ImageView gifView = view.findViewById(R.id.loading_gif);

        titleView.setText(text);

        // 加载 GIF 素材
        Glide.with(context)
                .load(R.drawable.loading_df)
                .into(gifView);

        dialog.setContentView(view);
        return dialog;
    }

    /**
     * 更新加载对话框的副标题小字
     */
    public static void updateInfo(@NonNull Dialog dialog, String info) {
        if (dialog.isShowing()) {
            TextView infoView = dialog.findViewById(R.id.loading_info);
            if (infoView != null) {
                infoView.setText(info);
            }
        }
    }
}
