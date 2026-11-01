package com.noxbyte.tubedrive.car;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.MessageTemplate;

import com.noxbyte.tubedrive.yt.Video;
import com.noxbyte.tubedrive.yt.YouTube;

import java.util.ArrayList;
import java.util.List;

public class ResultsScreen extends Screen {
    private final String query;
    private final List<Video> results = new ArrayList<>();
    private volatile boolean loading = true;
    @Nullable
    private volatile String error;

    public ResultsScreen(@NonNull CarContext context, @NonNull String query) {
        super(context);
        this.query = query;
        new Thread(() -> {
            try {
                results.addAll(YouTube.search(query));
            } catch (Exception e) {
                error = e.getMessage() == null ? "Search failed" : e.getMessage();
            }
            loading = false;
            new Handler(Looper.getMainLooper()).post(this::invalidate);
        }).start();
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        if (loading) {
            return new MessageTemplate.Builder("Loading\u2026")
                    .setTitle(query)
                    .setHeaderAction(Action.BACK)
                    .build();
        }
        if (error != null || results.isEmpty()) {
            String msg = error != null ? error : "Nothing found";
            return new MessageTemplate.Builder(msg)
                    .setTitle(query)
                    .setHeaderAction(Action.BACK)
                    .build();
        }

        ItemList.Builder items = new ItemList.Builder();
        for (int i = 0; i < results.size(); i++) {
            final int index = i;
            Video v = results.get(i);
            Row.Builder row = new Row.Builder()
                    .setTitle(v.title)
                    .addText(v.subtitle());
            row.setOnClickListener(() -> {
                PlaybackController.connect(getCarContext(), c ->
                        PlaybackController.play(c, v.url, v.title, v.uploader));
                getScreenManager().push(new PlayerScreen(getCarContext(), v));
            });
            items.addItem(row.build());
        }
        return new ListTemplate.Builder()
                .setSingleList(items.build())
                .setTitle(query)
                .setHeaderAction(Action.BACK)
                .build();
    }
}
