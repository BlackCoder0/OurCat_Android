package com.ourcat.app.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.text.style.ReplacementSpan;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
        chip.setText("@" + name);
        chip.setChipIconResource(R.drawable.cat);
        chip.setChipIconTintResource(R.color.primary);
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

    private static class MentionBadgeSpan extends ReplacementSpan {
        private final Drawable icon;
        private final String label;
        private final int bgColor;
        private final int textColor;
        private final float hPadding;
        private final float vPadding;
        private final float iconGap;

        MentionBadgeSpan(Context context, String label) {
            this.label = label;
            this.icon = context.getDrawable(R.drawable.cat);
            if (this.icon != null) {
                this.icon.setTint(context.getResources().getColor(R.color.primary, null));
            }
            this.bgColor = context.getResources().getColor(R.color.secondary, null);
            this.textColor = context.getResources().getColor(R.color.primary, null);
            float density = context.getResources().getDisplayMetrics().density;
            this.hPadding = 6f * density;
            this.vPadding = 2f * density;
            this.iconGap = 4f * density;
        }

        @Override
        public int getSize(@NonNull android.graphics.Paint paint, CharSequence text, int start, int end,
                android.graphics.Paint.FontMetricsInt fm) {
            android.graphics.Paint.FontMetrics metrics = paint.getFontMetrics();
            float textHeight = metrics.descent - metrics.ascent;
            float iconWidth = icon != null ? (textHeight * 0.95f + iconGap) : 0f;
            float textWidth = paint.measureText(label);
            return (int) Math.ceil(hPadding + iconWidth + textWidth + hPadding);
        }

        @Override
        public void draw(@NonNull android.graphics.Canvas canvas, CharSequence text, int start, int end, float x,
                int top, int y, int bottom, @NonNull android.graphics.Paint paint) {
            android.graphics.Paint.FontMetrics metrics = paint.getFontMetrics();
            float textTop = y + metrics.ascent;
            float textBottom = y + metrics.descent;
            float textHeight = textBottom - textTop;
            float iconSize = icon != null ? textHeight * 0.95f : 0f;
            float textWidth = paint.measureText(label);
            float contentWidth = (icon != null ? (iconSize + iconGap) : 0f) + textWidth;
            float rectTop = textTop - vPadding;
            float rectBottom = textBottom + vPadding;
            float rectRight = x + hPadding + contentWidth + hPadding;

            int oldColor = paint.getColor();
            paint.setColor(bgColor);
            canvas.drawRect(x, rectTop, rectRight, rectBottom, paint);

            float cursorX = x + hPadding;
            if (icon != null) {
                float iconTop = textTop + (textHeight - iconSize) / 2f;
                icon.setBounds((int) cursorX, (int) iconTop, (int) (cursorX + iconSize), (int) (iconTop + iconSize));
                icon.draw(canvas);
                cursorX += iconSize + iconGap;
            }

            paint.setColor(textColor);
            canvas.drawText(label, cursorX, y, paint);
            paint.setColor(oldColor);
        }
    }

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

            int spanStart = ssb.length();
            String display = "@" + name;
            ssb.append(display);
            int spanEnd = ssb.length();
            ssb.append(" ");

            ssb.setSpan(new MentionBadgeSpan(context, display), spanStart, spanEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            ssb.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    if (listener != null)
                        listener.onCatClick(catId);
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    ds.setUnderlineText(false);
                }
            }, spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            lastEnd = matcher.end();
        }
        ssb.append(text.substring(lastEnd));
        return ssb;
    }
}
