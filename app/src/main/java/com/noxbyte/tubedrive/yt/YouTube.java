package com.noxbyte.tubedrive.yt;

import android.util.Log;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * Thin facade over NewPipe Extractor: search + stream resolution, no API keys.
 * YouTube changes its internals constantly, so this can break at any time;
 * bump the extractor version when playback breaks.
 */
public final class YouTube {
    private static final String TAG = "TubeDrive";
    private static volatile boolean inited = false;

    private YouTube() {}

    public static synchronized void init() {
        if (inited) return;
        NewPipe.init(new OkHttpDownloader(), new Localization("en", "GB"), new ContentCountry("GB"));
        inited = true;
    }

    public static List<Video> search(String query) throws Exception {
        init();
        SearchQueryHandler handler = ServiceList.YouTube.getSearchQHFactory().fromQuery(query);
        SearchInfo info = SearchInfo.getInfo(ServiceList.YouTube, handler);
        List<Video> out = new ArrayList<>();
        for (InfoItem item : info.getRelatedItems()) {
            if (item instanceof StreamInfoItem) {
                out.add(fromItem((StreamInfoItem) item));
            }
        }
        return out;
    }

    public static Video fromItem(StreamInfoItem it) {
        String url = it.getUrl();
        String thumb = "";
        if (it.getThumbnails() != null && !it.getThumbnails().isEmpty()) {
            thumb = it.getThumbnails().get(0).getUrl();
        }
        return new Video(extractVideoId(url), url, it.getName(), it.getUploaderName(),
                it.getDuration(), it.getViewCount(), thumb);
    }

    public static class Streams {
        public String title = "";
        public String uploader = "";
        public String thumbnailUrl = "";
        public long duration;
        public String audioUrl;
        public int audioBitrate;
        public String videoUrl;
        public int videoHeight;
    }

    /** Best audio stream (for playback) plus best muxed video stream (phone screen). */
    public static Streams resolve(String url) throws Exception {
        init();
        StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, url);
        Streams s = new Streams();
        s.title = info.getName();
        s.uploader = info.getUploaderName();
        s.duration = info.getDuration();
        if (info.getThumbnails() != null && !info.getThumbnails().isEmpty()) {
            s.thumbnailUrl = info.getThumbnails().get(0).getUrl();
        }
        for (AudioStream a : info.getAudioStreams()) {
            if (a.getAverageBitrate() > s.audioBitrate) {
                s.audioBitrate = a.getAverageBitrate();
                s.audioUrl = a.getContent();
            }
        }
        for (VideoStream v : info.getVideoStreams()) {
            if (v.isVideoOnly()) continue;
            if (s.videoUrl == null || v.getHeight() > s.videoHeight) {
                s.videoHeight = v.getHeight();
                s.videoUrl = v.getContent();
            }
        }
        if (s.audioUrl == null) {
            throw new IOException("No playable audio stream for " + url);
        }
        return s;
    }

    public static String extractVideoId(String url) {
        if (url == null) return "";
        int v = url.indexOf("v=");
        if (v >= 0) {
            String id = url.substring(v + 2);
            int amp = id.indexOf('&');
            return amp >= 0 ? id.substring(0, amp) : id;
        }
        int be = url.indexOf("youtu.be/");
        if (be >= 0) {
            String id = url.substring(be + 9);
            int q = id.indexOf('?');
            return q >= 0 ? id.substring(0, q) : id;
        }
        return url;
    }

    static class OkHttpDownloader extends Downloader {
        private final OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        @Override
        public Response execute(Request request) throws IOException, ReCaptchaException {
            okhttp3.Request.Builder rb = new okhttp3.Request.Builder().url(request.url());
            Map<String, List<String>> headers = request.headers();
            if (headers != null) {
                for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                    for (String value : e.getValue()) {
                        rb.header(e.getKey(), value);
                    }
                }
            }
            byte[] body = request.dataToSend();
            if (body != null && body.length > 0) {
                rb.post(okhttp3.RequestBody.create(body));
            }
            okhttp3.Response resp = client.newCall(rb.build()).execute();
            String text = resp.body() != null ? resp.body().string() : "";
            Map<String, List<String>> respHeaders = resp.headers().toMultimap();
            int code = resp.code();
            String message = resp.message();
            String url2 = resp.request().url().toString();
            resp.close();
            if (code == 429) {
                throw new ReCaptchaException("Rate limited by YouTube", url2);
            }
            return new Response(code, message, respHeaders, text, url2);
        }
    }
}
