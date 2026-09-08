package org.openstar.map;

import android.content.Context;
import android.content.SharedPreferences;

/** Shared settings for normal and AR sky views, retaining the original preference file. */
public final class SkyPreferences {
    private SkyPreferences() { }

    /** Uses the legacy MainActivity.getPreferences() file; no migration is needed. */
    public static SharedPreferences get(Context context) {
        // Activity.getPreferences uses getLocalClassName(), here "MainActivity".
        return context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE);
    }
}
