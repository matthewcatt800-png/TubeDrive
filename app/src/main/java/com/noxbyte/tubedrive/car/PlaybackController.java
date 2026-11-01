package com.noxbyte.tubedrive.car;

import android.content.ComponentName;

import androidx.annotation.Nullable;
import androidx.car.app.CarContext;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.MoreExecutors;
import com.noxbyte.tubedrive.playback.PlaybackService;

/** Shared connection to PlaybackService for the car screens. */
public final class PlaybackController {
    @Nullable
    private static MediaController controller;

    private PlaybackController() {}

    public interface Ready { void onReady(MediaController c); }

    public static void connect(CarContext ctx, Ready ready) {
        if (controller != null) {
            ready.onReady(controller);
            return;
        }
        SessionToken token = new SessionToken(ctx, new ComponentName(ctx, PlaybackService.class));
        com.google.common.util.concurrent.ListenableFuture<MediaController> future =
                new MediaController.Builder(ctx, token).buildAsync();
        future.addListener(() -> {
            try {
                controller = future.get();
                ready.onReady(controller);
            } catch (Exception ignored) {
            }
        }, MoreExecutors.directExecutor());
    }

    public static void play(MediaController c, String url, String title, String uploader) {
        androidx.media3.common.MediaMetadata md = new androidx.media3.common.MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(uploader)
                .build();
        MediaItem item = new MediaItem.Builder().setMediaId(url).setMediaMetadata(md).build();
        c.setMediaItem(item);
        c.prepare();
        c.play();
    }

    public static void toggle(MediaController c) {
        if (c.isPlaying()) c.pause(); else c.play();
    }

    public static void release() {
        if (controller != null) {
            controller.release();
            controller = null;
        }
    }
}
