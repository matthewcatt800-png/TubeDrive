package com.noxbyte.tubedrive.car;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.SearchTemplate;
import androidx.car.app.model.Template;

public class SearchScreen extends Screen {
    public SearchScreen(@NonNull CarContext context) {
        super(context);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        return new SearchTemplate.Builder(new SearchTemplate.SearchCallback() {
            @Override
            public void onSearchSubmitted(@NonNull String searchTerm) {
                Recents.add(getCarContext(), searchTerm);
                getScreenManager().push(new ResultsScreen(getCarContext(), searchTerm));
            }
        })
        .setSearchHint("Search YouTube")
        .setHeaderAction(Action.BACK)
        .setShowKeyboardByDefault(true)
        .build();
    }
}
