package org.openstar.map;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Calendar and daylight-saving regression checks; runs without Android. */
public final class HourTimelineTest {
    private static int scenarios;

    private static long millis(String instant) { return Instant.parse(instant).toEpochMilli(); }

    private static void selection(String anchor, int hour, ZoneId zone, String expected) {
        long actual = HourTimeline.selectHour(millis(anchor), hour, zone);
        if (actual != millis(expected)) {
            throw new AssertionError(Instant.ofEpochMilli(actual) + " != " + expected);
        }
        scenarios++;
    }

    private static void rejected(String anchor, int hour, ZoneId zone) {
        try {
            HourTimeline.selectHour(millis(anchor), hour, zone);
        } catch (IllegalArgumentException expected) {
            scenarios++;
            return;
        }
        throw new AssertionError("Expected rejection for " + anchor + ", hour " + hour);
    }

    private static void navigation(String anchor, int hour, ZoneId zone, String expected) {
        long actual = HourTimeline.selectHourForNavigation(millis(anchor), hour, zone);
        if (actual != millis(expected)) {
            throw new AssertionError(Instant.ofEpochMilli(actual) + " != " + expected);
        }
        scenarios++;
    }

    public static void main(String[] args) {
        ZoneId hongKong = ZoneId.of("Asia/Hong_Kong");
        ZoneId newYork = ZoneId.of("America/New_York");

        // A normal selection clears minutes, seconds and milliseconds.
        selection("2026-09-08T13:27:45.123Z", 18, hongKong, "2026-09-08T10:00:00Z");
        // Midnight and late evening stay on the local calendar date.
        selection("2026-09-08T13:27:45Z", 0, hongKong, "2026-09-07T16:00:00Z");
        selection("2026-09-07T16:27:45Z", 23, hongKong, "2026-09-08T15:00:00Z");
        if (HourTimeline.hour(millis("2026-09-07T16:27:45Z"), hongKong) != 0) {
            throw new AssertionError("hour() must use the local date and hour");
        }
        scenarios++;

        // 02:00 does not exist on this spring-forward date: resolve to 03:00.
        selection("2026-03-08T05:30:00Z", 2, newYork, "2026-03-08T07:00:00Z");
        // The repeated 01:00 keeps either the earlier or later anchor offset.
        selection("2026-11-01T04:30:00Z", 1, newYork, "2026-11-01T05:00:00Z");
        selection("2026-11-01T07:30:00Z", 1, newYork, "2026-11-01T06:00:00Z");

        selection("2000-01-01T12:00:00Z", 0, ZoneOffset.UTC, "2000-01-01T00:00:00Z");
        selection("2050-12-31T12:00:00Z", 23, ZoneOffset.UTC, "2050-12-31T23:00:00Z");
        // The UTC bound takes priority even if the selected local year is valid.
        rejected("2000-01-01T00:00:00Z", 0, hongKong);
        rejected("2050-12-31T12:00:00Z", 23, newYork);
        // Conversely, a local year outside the range may still yield valid UTC.
        selection("2050-12-31T23:00:00Z", 0, hongKong, "2050-12-31T16:00:00Z");
        selection("2000-01-01T00:00:00Z", 23, newYork, "2000-01-01T04:00:00Z");
        rejected("2026-09-08T00:00:00Z", -1, hongKong);
        rejected("2026-09-08T00:00:00Z", 24, hongKong);

        // A keyboard decrement from 03:00 must cross the missing 02:00 hour.
        navigation("2026-03-08T07:00:00Z", 2, newYork, "2026-03-08T06:00:00Z");
        navigation("2026-03-08T07:45:00Z", 2, newYork, "2026-03-08T06:00:00Z");
        // Moving forward keeps ordinary gap normalization to 03:00.
        navigation("2026-03-08T06:00:00Z", 2, newYork, "2026-03-08T07:00:00Z");
        // Navigation also preserves the preferred offset in an overlap.
        navigation("2026-11-01T07:30:00Z", 1, newYork, "2026-11-01T06:00:00Z");
        // A half-hour gap can still reach an earlier hour: 03:00 -> 02:30.
        ZoneId lordHowe = ZoneId.of("Australia/Lord_Howe");
        navigation("2026-10-03T16:00:00Z", 2, lordHowe, "2026-10-03T15:30:00Z");
        navigation("2026-10-03T14:30:00Z", 2, lordHowe, "2026-10-03T15:30:00Z");

        System.out.println("Hour timeline: " + scenarios + " scenarios passed");
    }
}
