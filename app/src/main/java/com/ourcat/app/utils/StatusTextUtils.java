package com.ourcat.app.utils;

import java.util.Locale;

public final class StatusTextUtils {

    private StatusTextUtils() {
    }

    public static String formatRescueStatus(String status) {
        switch (normalize(status)) {
            case "created":
                return "待处理";
            case "in_progress":
                return "进行中";
            case "completed":
                return "已完成";
            case "resolved":
                return "已解决";
            default:
                return formatGenericStatus(status);
        }
    }

    public static String formatTaskStatus(String status) {
        switch (normalize(status)) {
            case "assigned":
                return "待执行";
            case "claimed":
                return "已领取";
            case "in_progress":
                return "进行中";
            case "done":
            case "completed":
                return "已完成";
            default:
                return formatGenericStatus(status);
        }
    }

    public static String formatGenericStatus(String status) {
        switch (normalize(status)) {
            case "created":
                return "已创建";
            case "in_progress":
                return "进行中";
            case "completed":
            case "done":
                return "已完成";
            case "assigned":
                return "已指派";
            case "claimed":
                return "已领取";
            case "pending":
            case "open":
                return "待处理";
            case "resolved":
                return "已解决";
            case "closed":
                return "已关闭";
            case "active":
                return "活跃";
            default:
                return status == null ? "" : status.trim();
        }
    }

    private static String normalize(String status) {
        return status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    }
}
