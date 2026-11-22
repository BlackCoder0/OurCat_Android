package com.ourcat.app.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class ServerAddress {
    private ServerAddress() { }

    public static String normalize(String value) {
        try {
            URI uri = new URI(value == null ? "" : value.trim());
            String scheme = uri.getScheme();
            int port = uri.getPort();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !(uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath()))
                    || port == 0 || port < -1 || port > 65535
                    || uri.getRawAuthority().endsWith(":")) {
                throw new URISyntaxException(value == null ? "" : value, "Expected a server root URL");
            }
            return new URI(scheme.toLowerCase(Locale.ROOT), null,
                    uri.getHost().toLowerCase(Locale.ROOT), port, "/", null, null).toString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("请输入后端根地址，例如 http://192.168.1.20:8080/，不要包含账号或 API 路径");
        }
    }
}
