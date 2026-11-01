package com.noxbyte.tubedrive;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.common.util.concurrent.MoreExecutors;
import com.noxbyte.tubedrive.playback.PlaybackService;
import com.noxbyte.tubedrive.yt.Video;
import com.noxbyte.tubedrive.yt.YouTube;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Phone/tablet companion UI (Material 3). Browsing, search, audio control. */
public class MainActivity extends AppCompatActivity {
    private final List<Video> results = new ArrayList<>();
    private final Map<String, Bitmap> thumbs = new ConcurrentHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private VideoAdapter adapter;
    private TextInputEditText searchInput;
    private TextView statusText;
    private TextView miniTitle;
    private MaterialButton miniPlay;
    private MediaController controller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        YouTube.init();

        searchInput = findViewById(R.id.search_input);
        statusText = findViewById(R.id.status_text);
        miniTitle = findViewById(R.id.mini_title);
        miniPlay = findViewById(R.id.mini_play);

        RecyclerView recycler = findViewById(R.id.results);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new VideoAdapter();
        recycler.setAdapter(adapter);

        findViewById(R.id.search_button).setOnClickListener(v -> runSearch());
        miniPlay.setOnClickListener(v -> {
            if (controller != null) {
                if (controller.isPlaying()) controller.pause(); else controller.play();
            }
        });

        connectController();
        runSearch("trending");
    }

    private void runSearch() {
        String q = searchInput.getText() == null ? "" : searchInput.getText().toString().trim();
        if (q.isEmpty()) return;
        runSearch(q);
    }

    private void runSearch(String q) {
        statusText.setText("Searching\u2026");
        io.execute(() -> {
            try {
                List<Video> r = YouTube.search(q);
                runOnUiThread(() -> {
                    results.clear();
                    results.addAll(r);
                    adapter.notifyDataSetChanged();
                    statusText.setText(results.size() + " results");
                });
            } catch (Exception e) {
                runOnUiThread(() -> statusText.setText("Search failed: " + e.getMessage()));
            }
        });
    }

    private void connectController() {
        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        com.google.common.util.concurrent.ListenableFuture<MediaController> future =
                new MediaController.Builder(this, token).buildAsync();
        future.addListener(() -> {
            try {
                controller = future.get();
                controller.addListener(new Player.Listener() {
                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        runOnUiThread(() -> miniPlay.setText(isPlaying ? "Pause" : "Play"));
                    }

                    @Override
                    public void onMediaItemTransition(@androidx.annotation.Nullable MediaItem item, int reason) {
                        runOnUiThread(() -> {
                            if (item != null && item.mediaMetadata != null) {
                                miniTitle.setText(item.mediaMetadata.title != null
                                        ? item.mediaMetadata.title : "Playing");
                            }
                        });
                    }
                });
            } catch (Exception ignored) {
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    protected void onDestroy() {
        if (controller != null) {
            controller.release();
            controller = null;
        }
        super.onDestroy();
    }

    private void play(Video v) {
        if (controller == null) return;
        MediaMetadata md = new MediaMetadata.Builder().setTitle(v.title).setArtist(v.uploader).build();
        MediaItem item = new MediaItem.Builder().setMediaId(v.url).setMediaMetadata(md).build();
        controller.setMediaItem(item);
        controller.prepare();
        controller.play();
        miniTitle.setText(v.title);
    }

    private class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.Holder> {
        class Holder extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final TextView title;
            final TextView subtitle;

            Holder(@NonNull View itemView) {
                super(itemView);
                thumb = itemView.findViewById(R.id.thumb);
                title = itemView.findViewById(R.id.title);
                subtitle = itemView.findViewById(R.id.subtitle);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_video, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Video v = results.get(position);
            h.title.setText(v.title);
            h.subtitle.setText(v.subtitle());
            Bitmap cached = thumbs.get(v.thumbnailUrl);
            if (cached != null) {
                h.thumb.setImageBitmap(cached);
            } else {
                h.thumb.setImageBitmap(null);
                io.execute(() -> {
                    Bitmap b = fetchBitmap(v.thumbnailUrl);
                    if (b != null) {
                        thumbs.put(v.thumbnailUrl, b);
                        runOnUiThread(() -> {
                            int pos = h.getBindingAdapterPosition();
                            if (pos != RecyclerView.NO_POSITION && results.get(pos).id.equals(v.id)) {
                                h.thumb.setImageBitmap(b);
                            }
                        });
                    }
                });
            }
            h.itemView.setOnClickListener(x -> play(v));
        }

        @Override
        public int getItemCount() {
            return results.size();
        }
    }

    private static Bitmap fetchBitmap(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            try (InputStream in = conn.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }
}
