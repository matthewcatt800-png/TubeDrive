package com.noxbyte.tubedrive.car;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.car.app.model.ListTemplate;

public class BrowseScreen extends Screen {
    public BrowseScreen(@NonNull CarContext context) {
        super(context);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        ItemList.Builder items = new ItemList.Builder();
        items.addItem(new Row.Builder()
                .setTitle("Search YouTube")
                .setOnClickListener(() -> getScreenManager().push(new SearchScreen(getCarContext())))
                .build());
        items.addItem(new Row.Builder()
                .setTitle("Trending")
                .setOnClickListener(() -> getScreenManager().push(new ResultsScreen(getCarContext(), "trending")))
                .build());
        for (String q : Recents.get(getCarContext())) {
            final String query = q;
            items.addItem(new Row.Builder()
                    .setTitle(query)
                    .setOnClickListener(() -> getScreenManager().push(new ResultsScreen(getCarContext(), query)))
                    .build());
        }
        return new ListTemplate.Builder()
                .setSingleList(items.build())
                .setTitle("TubeDrive")
                .build();
    }
}
