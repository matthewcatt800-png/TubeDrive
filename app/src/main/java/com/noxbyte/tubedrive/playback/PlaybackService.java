package com.noxbyte.tubedrive.playback;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.MediaSession;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.noxbyte.tubedrive.MainActivity;
import com.noxbyte.tubedrive.yt.Video;
import com.noxbyte.tubedrive.yt.YouTube;

import java.util.List;
import java.util.concurrent.Executors;

/**
 * Media3 MediaLibraryService: the piece Android Auto talks to for the browse
 * tree, search and audio playback. Content comes from NewPipe Extractor.
 */
public class PlaybackService extends MediaLibraryService {
    private static final String ROOT = "root";
    public static final String BROWSE_SEARCH = "search";
    public static final String BROWSE_TRENDING = "trending";
    private static final String RESULTS_PREFIX = "results:";

    private volatile String lastSearchQuery = "";

    private MediaLibrarySession session;
    private ExoPlayer player;
    private ListeningExecutorService io;

    @Override
    public void onCreate() {
        super.onCreate();
        YouTube.init();
        io = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(2));
        player = new ExoPlayer.Builder(this)
                .setAudioAttributes(AudioAttributes.DEFAULT, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build();
        Intent openApp = new Intent(this, MainActivity.class);
        int flags = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent pi = PendingIntent.getActivity(this, 0, openApp, flags);
        session = new MediaLibrarySession.Builder(this, player, new LibraryCallback())
                .setSessionActivity(pi)
                .build();
    }

    @Override
    public MediaLibrarySession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    @Override
    public void onDestroy() {
        if (session != null) session.release();
        if (player != null) player.release();
        if (io != null) io.shutdown();
        super.onDestroy();
    }

    private static MediaItem browseItem(String id, String title) {
        MediaMetadata md = new MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build();
        return new MediaItem.Builder().setMediaId(id).setMediaMetadata(md).build();
    }

    private static MediaItem playableItem(Video v) {
        MediaMetadata.Builder md = new MediaMetadata.Builder()
                .setTitle(v.title)
                .setArtist(v.uploader)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC);
        if (!v.thumbnailUrl.isEmpty()) {
            md.setArtworkUri(Uri.parse(v.thumbnailUrl));
        }
        return new MediaItem.Builder().setMediaId(v.url).setMediaMetadata(md.build()).build();
    }

    private static List<MediaItem> recentSearchItems() {
        ImmutableList.Builder<MediaItem> b = ImmutableList.builder();
        for (String q : recentQueries()) {
            b.add(browseItem(RESULTS_PREFIX + q, q));
        }
        return b.build();
    }

    private static List<String> recentQueries() {
        List<String> defaults = new java.util.ArrayList<>();
        defaults.add("trending");
        defaults.add("train cab ride");
        defaults.add("lofi");
        return defaults;
    }

    private ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> searchResults(String query) {
        return io.submit(() -> {
            try {
                ImmutableList.Builder<MediaItem> b = ImmutableList.builder();
                for (Video v : YouTube.search(query)) {
                    b.add(playableItem(v));
                }
                return LibraryResult.ofItemList(b.build(), null);
            } catch (Exception e) {
                return LibraryResult.ofError(LibraryResult.RESULT_ERROR_IO);
            }
        });
    }

    private class LibraryCallback implements MediaLibrarySession.Callback {

        @Override
        public ListenableFuture<LibraryResult<MediaItem>> onGetLibraryRoot(
                MediaLibrarySession session, MediaSession.ControllerInfo browser,
                @Nullable LibraryParams params) {
            return Futures.immediateFuture(LibraryResult.ofItem(browseItem(ROOT, "TubeDrive"), params));
        }

        @Override
        public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildren(
                MediaLibrarySession session, MediaSession.ControllerInfo browser, String parentId,
                int page, int pageSize, @Nullable LibraryParams params) {
            if (ROOT.equals(parentId)) {
                return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(
                        browseItem(BROWSE_SEARCH, "Search YouTube"),
                        browseItem(BROWSE_TRENDING, "Trending")), params));
            }
            if (BROWSE_SEARCH.equals(parentId)) {
                // A live search already run? Return its results, else recent searches.
                String q = lastSearchQuery;
                if (!q.isEmpty()) {
                    return searchResults(q);
                }
                return Futures.immediateFuture(LibraryResult.ofItemList(
                        ImmutableList.copyOf(recentSearchItems()), params));
            }
            if (BROWSE_TRENDING.equals(parentId) || parentId.startsWith(RESULTS_PREFIX)) {
                String q = BROWSE_TRENDING.equals(parentId) ? "trending" : parentId.substring(RESULTS_PREFIX.length());
                return searchResults(q);
            }
            return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
        }

        @Override
        public ListenableFuture<LibraryResult<Void>> onSearch(
                MediaLibrarySession session, MediaSession.ControllerInfo browser, String query,
                @Nullable LibraryParams params) {
            lastSearchQuery = query;
            return Futures.immediateFuture(LibraryResult.ofVoid());
        }

        @Override
        public ListenableFuture<List<MediaItem>> onAddMediaItems(
                MediaSession session, MediaSession.ControllerInfo controller, List<MediaItem> mediaItems) {
            return io.submit(() -> {
                ImmutableList.Builder<MediaItem> out = ImmutableList.builder();
                for (MediaItem mi : mediaItems) {
                    try {
                        YouTube.Streams s = YouTube.resolve(mi.mediaId);
                        MediaMetadata.Builder md = new MediaMetadata.Builder()
                                .setTitle(s.title)
                                .setArtist(s.uploader);
                        if (!s.thumbnailUrl.isEmpty()) {
                            md.setArtworkUri(Uri.parse(s.thumbnailUrl));
                        }
                        out.add(mi.buildUpon().setUri(Uri.parse(s.audioUrl)).setMediaMetadata(md.build()).build());
                    } catch (Exception e) {
                        // skip unresolvable items
                    }
                }
                return (List<MediaItem>) out.build();
            });
        }
    }
}
