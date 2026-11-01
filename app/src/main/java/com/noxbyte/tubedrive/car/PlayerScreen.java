package com.noxbyte.tubedrive.car;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ParkedOnlyOnClickListener;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.car.app.model.ListTemplate;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;

import com.noxbyte.tubedrive.yt.Video;

/**
 * In-car player. Audio always plays through PlaybackService. The video
 * action is wrapped in ParkedOnlyOnClickListener: the host only fires it
 * when the car is parked, which is exactly the parked-only video rule.
 */
public class PlayerScreen extends Screen {
    private final Video video;
    private boolean playing = false;
    private boolean videoRequested = false;

    public PlayerScreen(@NonNull CarContext context, @NonNull Video video) {
        super(context);
        this.video = video;
        PlaybackController.connect(context, c -> c.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                playing = isPlaying;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                        invalidate();
                    }
                });
            }
        }));
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        ItemList.Builder items = new ItemList.Builder();
        items.addItem(new Row.Builder().setTitle(video.uploader).addText("Audio playback").build());

        if (videoRequested) {
            items.addItem(new Row.Builder()
                    .setTitle("Video: on (parked)")
                    .addText("Renders on a video-capable head unit. Locks again once you drive off.")
                    .build());
        } else {
            items.addItem(new Row.Builder()
                    .setTitle("Turn on video")
                    .addText("Available only when parked. Audio keeps playing meanwhile.")
                    .setOnClickListener(ParkedOnlyOnClickListener.create(() -> videoRequested = true))
                    .build());
        }

        ActionStrip.Builder strip = new ActionStrip.Builder()
                .addAction(new Action.Builder()
                        .setTitle("Prev")
                        .setOnClickListener(() -> PlaybackController.connect(getCarContext(),
                                MediaController::seekToPrevious))
                        .build())
                .addAction(new Action.Builder()
                        .setTitle(playing ? "Pause" : "Play")
                        .setOnClickListener(() -> PlaybackController.connect(getCarContext(),
                                PlaybackController::toggle))
                        .build())
                .addAction(new Action.Builder()
                        .setTitle("Next")
                        .setOnClickListener(() -> PlaybackController.connect(getCarContext(),
                                MediaController::seekToNext))
                        .build());

        return new ListTemplate.Builder()
                .setSingleList(items.build())
                .setActionStrip(strip.build())
                .setTitle(video.title)
                .setHeaderAction(Action.BACK)
                .build();
    }
}
