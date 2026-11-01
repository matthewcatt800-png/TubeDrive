package com.noxbyte.tubedrive.yt;

public class Video {
    public final String id;
    public final String url;
    public final String title;
    public final String uploader;
    public final long durationSeconds;
    public final long viewCount;
    public final String thumbnailUrl;

    public Video(String id, String url, String title, String uploader,
                 long durationSeconds, long viewCount, String thumbnailUrl) {
        this.id = id;
        this.url = url;
        this.title = title;
        this.uploader = uploader == null ? "" : uploader;
        this.durationSeconds = durationSeconds;
        this.viewCount = viewCount;
        this.thumbnailUrl = thumbnailUrl == null ? "" : thumbnailUrl;
    }

    public String subtitle() {
        StringBuilder sb = new StringBuilder(uploader);
        if (durationSeconds > 0) {
            sb.append(" \u00b7 ").append(formatDuration(durationSeconds));
        }
        if (viewCount > 0) {
            sb.append(" \u00b7 ").append(shortCount(viewCount)).append(" views");
        }
        return sb.toString();
    }

    public static String formatDuration(long s) {
        if (s <= 0) return "";
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, sec);
        return String.format("%d:%02d", m, sec);
    }

    public static String shortCount(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.0fK", n / 1_000.0);
        return String.valueOf(n);
    }
}
