package com.noxbyte.tubedrive.car;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public final class Recents {
    private static final String PREFS = "tubedrive";
    private static final String KEY = "recent_searches";

    private Recents() {}

    public static List<String> get(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String joined = p.getString(KEY, "");
        List<String> out = new ArrayList<>();
        if (!joined.isEmpty()) {
            for (String s : joined.split("\u0001")) {
                if (!s.isEmpty()) out.add(s);
            }
        }
        return out;
    }

    public static void add(Context ctx, String q) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<String> list = new ArrayList<>();
        list.add(q);
        for (String s : get(ctx)) {
            if (!s.equals(q) && list.size() < 5) list.add(s);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append('\u0001');
            sb.append(list.get(i));
        }
        p.edit().putString(KEY, sb.toString()).apply();
    }
}
