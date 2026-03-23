package com.ourcat.app.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class DisplayTimeUtils {

    private static final String[] ISO_PATTERNS = new String[] {
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm"
    };

    private static final SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);

    private DisplayTimeUtils() {
    }

    public static String getRelativeTime(String rawTime) {
        long timestamp = parseToMillis(rawTime);
        if (timestamp <= 0L) {
            return sanitizeRawTime(rawTime);
        }

        long diff = System.currentTimeMillis() - timestamp;
        if (diff <= 0L) {
            return "刚刚";
        }

        long seconds = diff / 1000L;
        if (seconds < 60L) {
            return "刚刚";
        }

        long minutes = seconds / 60L;
        if (minutes < 60L) {
            return minutes + "分钟前";
        }

        long hours = minutes / 60L;
        if (hours < 24L) {
            return hours + "小时前";
        }

        return formatDateTime(rawTime);
    }

    public static String formatDateTime(String rawTime) {
        long timestamp = parseToMillis(rawTime);
        if (timestamp <= 0L) {
            return sanitizeRawTime(rawTime);
        }
        return format(new Date(timestamp), DATE_TIME_FORMAT);
    }

    public static String formatDate(String rawTime) {
        long timestamp = parseToMillis(rawTime);
        if (timestamp <= 0L) {
            String text = sanitizeRawTime(rawTime);
            return text.length() >= 10 ? text.substring(0, 10) : text;
        }
        return format(new Date(timestamp), DATE_FORMAT);
    }

    public static long parseToMillis(String rawTime) {
        if (rawTime == null) {
            return 0L;
        }

        String text = rawTime.trim();
        if (text.isEmpty()) {
            return 0L;
        }

        if (text.matches("^\\d{13}$")) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }

        if (text.matches("^\\d{10}$")) {
            try {
                return Long.parseLong(text) * 1000L;
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }

        for (String pattern : ISO_PATTERNS) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = format.parse(text);
                if (date != null) {
                    return date.getTime();
                }
            } catch (ParseException ignored) {
            }
        }

        return 0L;
    }

    private static String sanitizeRawTime(String rawTime) {
        if (rawTime == null) {
            return "";
        }

        String text = rawTime.trim();
        if (text.isEmpty()) {
            return "";
        }

        if (text.endsWith("Z")) {
            text = text.substring(0, text.length() - 1);
        }

        if (text.length() >= 16 && text.charAt(10) == 'T') {
            return text.substring(0, 10) + " " + text.substring(11, 16);
        }

        return text;
    }

    private static synchronized String format(Date date, SimpleDateFormat formatter) {
        return formatter.format(date);
    }
}
