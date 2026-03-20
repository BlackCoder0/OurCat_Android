package com.ourcat.app.utils;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.ourcat.app.R;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

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
        LinearLayout titleContainer = view.findViewById(R.id.loading_title_container);
        ImageView gifView = view.findViewById(R.id.loading_gif);

        List<Animator> animators = new ArrayList<>();
        List<TextView> titleCharViews = new ArrayList<>();
        boolean[] cancelled = new boolean[] { false };
        Runnable[] startNext = new Runnable[1];
        String title = text + "...";
        if (titleContainer != null) {
            titleContainer.removeAllViews();
            float density = context.getResources().getDisplayMetrics().density;
            float amplitudePx = 6f * density;
            long durationMs = 260L;
            long gapMs = 30L;
            for (int i = 0; i < title.length(); i++) {
                TextView tv = new TextView(context);
                tv.setText(String.valueOf(title.charAt(i)));
                tv.setTextColor(context.getResources().getColor(R.color.primary, context.getTheme()));
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
                titleContainer.addView(tv);
                titleCharViews.add(tv);
            }

            if (!titleCharViews.isEmpty()) {
                int count = titleCharViews.size();
                ObjectAnimator[] seqAnimators = new ObjectAnimator[count];
                int[] nextIndex = new int[] { 0 };
                startNext[0] = () -> {
                    if (cancelled[0]) {
                        return;
                    }
                    for (TextView t : titleCharViews) {
                        t.setTranslationY(0f);
                    }
                    seqAnimators[nextIndex[0]].start();
                };
                for (int i = 0; i < count; i++) {
                    TextView tv = titleCharViews.get(i);
                    ObjectAnimator animator = ObjectAnimator.ofFloat(tv, View.TRANSLATION_Y, 0f, -amplitudePx, 0f);
                    animator.setDuration(durationMs);
                    animator.setInterpolator(new AccelerateDecelerateInterpolator());
                    int next = (i + 1) % count;
                    animator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (cancelled[0]) {
                                return;
                            }
                            nextIndex[0] = next;
                            titleContainer.postDelayed(startNext[0], gapMs);
                        }
                    });
                    seqAnimators[i] = animator;
                    animators.add(animator);
                }
                titleContainer.post(startNext[0]);
            }
        }

        // 加载 GIF 素材
        Glide.with(context)
                .load(R.drawable.loading_df)
                .into(gifView);

        dialog.setContentView(view);
        dialog.setOnDismissListener(d -> {
            cancelled[0] = true;
            if (titleContainer != null && startNext[0] != null) {
                titleContainer.removeCallbacks(startNext[0]);
            }
            for (Animator a : animators) {
                a.cancel();
            }
        });
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
