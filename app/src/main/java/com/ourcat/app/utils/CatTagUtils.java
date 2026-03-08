package com.ourcat.app.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.chip.ChipDrawable;
import com.ourcat.app.R;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 猫咪档案引用工具类
 * 处理 [cat:id:name] 格式的标记，转换为 ChipDrawable 或 ClickableSpan
 */
public class CatTagUtils {

    // 匹配格式：[cat:123:咪咪]
    // Group 1: id
    // Group 2: name
    private static final Pattern CAT_TAG_PATTERN = Pattern.compile("\\[cat:(\\d+):([^\\]]+)\\]");

    public interface OnCatClickListener {
        void onCatClick(long catId);
    }

    /**
     * 在 EditText 光标处插入猫咪引用 Chip
     */
    public static void insertCatTag(Context context, EditText editText, Map<String, Object> cat) {
        if (context == null || editText == null || cat == null)
            return;

        long id = ((Number) cat.get("id")).longValue();
        String name = (String) cat.get("name");
        if (name == null || name.isEmpty())
            name = "未命名";

        String tagText = String.format("[cat:%d:%s]", id, name);

        // 创建 ChipDrawable
        ChipDrawable chip = ChipDrawable.createFromAttributes(context, null, 0,
                com.google.android.material.R.style.Widget_MaterialComponents_Chip_Action);
        chip.setText("🐱 " + name);
        chip.setBounds(0, 0, chip.getIntrinsicWidth(), chip.getIntrinsicHeight());

        // 创建包含 ImageSpan 的 SpannableString
        SpannableStringBuilder ssb = new SpannableStringBuilder(tagText);
        ImageSpan span = new ImageSpan(chip);
        ssb.setSpan(span, 0, tagText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        // 插入到光标位置
        int start = Math.max(editText.getSelectionStart(), 0);
        int end = Math.max(editText.getSelectionEnd(), 0);
        editText.getText().replace(Math.min(start, end), Math.max(start, end), ssb);
        // 插入后加个空格方便继续输入
        editText.getText().insert(editText.getSelectionEnd(), " ");
    }

    /**
     * 解析文本中的猫咪引用，并在 TextView 中显示为可点击的 Span
     */
    public static void parseAndBind(Context context, TextView textView, String text, OnCatClickListener listener) {
        if (textView == null || text == null)
            return;

        SpannableStringBuilder ssb = parseToClickable(context, text, listener);
        textView.setText(ssb);
        textView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
    }

    private static class CatTagSpan extends android.text.style.ReplacementSpan {
        private final String text;
        private final int backgroundColor;
        private final int textColor;
        private final float radius;
        private final float padding;
        private final long catId;
        private final OnCatClickListener listener;

        public CatTagSpan(Context context, String name, long catId, OnCatClickListener listener) {
            this.text = "🐱 " + name;
            this.catId = catId;
            this.listener = listener;
            this.backgroundColor = context.getResources().getColor(R.color.secondary, null); // 使用 secondary 作为浅色背景
            this.textColor = context.getResources().getColor(R.color.primary, null);
            this.radius = 16f; // px
            this.padding = 16f; // px
        }

        @Override
        public int getSize(@NonNull android.graphics.Paint paint, CharSequence text, int start, int end,
                @Nullable android.graphics.Paint.FontMetricsInt fm) {
            return (int) (paint.measureText(this.text) + padding * 2);
        }

        @Override
        public void draw(@NonNull android.graphics.Canvas canvas, CharSequence text, int start, int end, float x,
                int top, int y, int bottom, @NonNull android.graphics.Paint paint) {
            // 绘制背景
            android.graphics.RectF rect = new android.graphics.RectF(x, top, x + getSize(paint, text, start, end, null),
                    bottom);
            paint.setColor(backgroundColor);
            // 稍微缩小一点高度
            float height = bottom - top;
            float margin = height * 0.15f;
            rect.top += margin;
            rect.bottom -= margin;
            canvas.drawRoundRect(rect, radius, radius, paint);

            // 绘制文字
            paint.setColor(textColor);
            canvas.drawText(this.text, x + padding, y, paint);
        }

        // ReplacementSpan 默认不支持点击事件，需要配合 LinkMovementMethod 和特殊的 Touch 处理
        // 如果要支持点击，通常建议直接用 ClickableSpan + BackgroundColorSpan 组合，或者用 ChipDrawable
        // (ImageSpan)
    }

    // 为了稳健且支持点击，我们改用 ChipDrawable 的 ImageSpan 方案，
    // 但 ImageSpan 的点击事件处理比较麻烦（LinkMovementMethod 不直接支持 ImageSpan 点击）。
    // 最简单的方案：将 [cat:id:name] 替换为 "🐱 name" 文本，然后在这个文本范围内应用 ClickableSpan。

    /**
     * 简单版：解析并替换为可点击文本
     */
    public static SpannableStringBuilder parseToClickable(Context context, String text, OnCatClickListener listener) {
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        Matcher matcher = CAT_TAG_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            ssb.append(text.substring(lastEnd, matcher.start()));

            String idStr = matcher.group(1);
            String name = matcher.group(2);
            long catId = Long.parseLong(idStr);

            String display = "🐱 " + name + " "; // 加个空格防止粘连
            int spanStart = ssb.length();
            ssb.append(display);
            int spanEnd = ssb.length();

            // 样式
            ssb.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    if (listener != null)
                        listener.onCatClick(catId);
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    ds.setColor(context.getResources().getColor(R.color.primary, null));
                    ds.setUnderlineText(false);
                    ds.setFakeBoldText(true);
                    ds.bgColor = Color.parseColor("#E3F2FD"); // 浅蓝色背景
                }
            }, spanStart, spanEnd - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); // -1 是为了不包含空格

            lastEnd = matcher.end();
        }
        ssb.append(text.substring(lastEnd));
        return ssb;
    }
}
