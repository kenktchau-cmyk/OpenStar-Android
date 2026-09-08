package org.openstar.map;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/** Local-hour selection for the map's 24-hour timeline. */
public final class HourTimeline {
    private HourTimeline() {}

    /**
     * Select an exact local hour on the anchor's local date.
     * A daylight-saving overlap keeps the anchor's offset when possible; a gap
     * advances by the gap length. The ephemeris supports UTC years 2000-2050.
     */
    public static long selectHour(long anchorMillis, int hour, ZoneId zone) {
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("Hour must be between 0 and 23.");
        }
        ZonedDateTime anchor = Instant.ofEpochMilli(anchorMillis).atZone(zone);
        LocalDateTime selected = anchor.toLocalDate().atTime(hour, 0);
        Instant result = ZonedDateTime.ofLocal(selected, zone, anchor.getOffset()).toInstant();
        int utcYear = result.atZone(ZoneOffset.UTC).getYear();
        if (utcYear < 2000 || utcYear > 2050) {
            throw new IllegalArgumentException("Choose a time in UTC years 2000 through 2050.");
        }
        return result.toEpochMilli();
    }

    /**
     * Select an hour for keyboard/accessibility navigation. When moving left,
     * skip a daylight-saving gap which would otherwise return to the current
     * hour. A gap at midnight has no earlier hour on the selected local date.
     */
    public static long selectHourForNavigation(long anchorMillis, int hour, ZoneId zone) {
        long result = selectHour(anchorMillis, hour, zone);
        int anchorHour = hour(anchorMillis, zone);
        int candidate = hour;
        while (candidate > 0 && candidate < anchorHour && hour(result, zone) >= anchorHour) {
            result = selectHour(anchorMillis, --candidate, zone);
        }
        return result;
    }

    public static int hour(long instantMillis, ZoneId zone) {
        return Instant.ofEpochMilli(instantMillis).atZone(zone).getHour();
    }
}
