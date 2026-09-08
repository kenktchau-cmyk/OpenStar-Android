package org.openstar.map;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.ZoneId;

/** Offline observing-location time zone. Runtime offset/DST rules come from Android. */
public final class ObserverTimeZone {
    private static volatile TimeZoneBoundaryLookup lookup;
    private ObserverTimeZone() { }

    /** Resolves precise bundled boundaries. Call on a worker for first or border
     * lookups. Invalid/unsupported zones throw IllegalArgumentException; missing
     * or corrupt bundled assets throw IllegalStateException. No silent guess.
     */
    public static ZoneId resolve(Context context, double latitude, double longitude) {
        TimeZoneBoundaryLookup.validate(latitude, longitude);
        try {
            TimeZoneBoundaryLookup current = lookup;
            if (current == null) {
                synchronized (ObserverTimeZone.class) {
                    current = lookup;
                    if (current == null) {
                        AssetManager assets = context.getApplicationContext().getAssets();
                        current = new TimeZoneBoundaryLookup(name -> assets.open("timezones/" + name, AssetManager.ACCESS_STREAMING));
                        lookup = current;
                    }
                }
            }
            String id = current.lookup(latitude, longitude);
            try { return ZoneId.of(id); }
            catch (DateTimeException unavailable) {
                // Exact IANA renames, not geographically nearby substitutes.
                String alias = legacyAlias(id);
                if (alias != null) {
                    try { return ZoneId.of(alias); }
                    catch (DateTimeException ignored) { }
                }
                throw new IllegalArgumentException("This Android version has no time zone rules for " + id, unavailable);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Bundled location time zone data could not be read", failure);
        }
    }

    private static String legacyAlias(String id) {
        switch (id) {
            case "Europe/Kyiv": return "Europe/Kiev";
            case "America/Nuuk": return "America/Godthab";
            case "Pacific/Kanton": return "Pacific/Enderbury";
            case "Asia/Kolkata": return "Asia/Calcutta";
            case "Asia/Kathmandu": return "Asia/Katmandu";
            case "Asia/Yangon": return "Asia/Rangoon";
            case "Asia/Ho_Chi_Minh": return "Asia/Saigon";
            default: return null;
        }
    }
}
