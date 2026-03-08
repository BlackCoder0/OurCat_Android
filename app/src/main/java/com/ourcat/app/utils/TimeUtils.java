package com.ourcat.app.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class TimeUtils {

    private static final SimpleDateFormat ISO_FORMAT;
    private static final SimpleDateFormat DATE_FORMAT;

    static {
        ISO_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
        ISO_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC")); // 假设后端返回 UTC 时间

        DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    }

    /**
     * 将 ISO 8601 时间字符串转换为“多久前”格式
     */
    public static String getRelativeTime(String isoTime) {
        if (isoTime == null || isoTime.isEmpty())
            return "";

        try {
            Date date = ISO_FORMAT.parse(isoTime);
            if (date == null)
                return "";

            // 加上 8 小时时区偏移 (如果后端是 UTC，而本地是 UTC+8)
            // 或者直接用 System.currentTimeMillis() 比较
            // 这里简单处理：假设 date 解析后已经是 Date 对象，与当前时间比较
            // 注意：SimpleDateFormat 解析时如果未指定时区，默认用本地时区，所以上面 setTimeZone("UTC") 很关键

            long time = date.getTime();
            long now = System.currentTimeMillis();
            long diff = now - time;

            if (diff < 0)
                return "刚刚";

            long seconds = diff / 1000;
            if (seconds < 60)
                return "刚刚";

            long minutes = seconds / 60;
            if (minutes < 60)
                return minutes + "分钟前";

            long hours = minutes / 60;
            if (hours < 24)
                return hours + "小时前";

            return DATE_FORMAT.format(date);

        } catch (ParseException e) {
            return "";
        }
    }
}
