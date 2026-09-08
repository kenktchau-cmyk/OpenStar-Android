package org.openstar.map;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;

/** A foreground, one-shot location request. Call start/stop on the main thread. */
public final class DeviceLocation {
    public interface Listener {
        void onStatus(String message);
        void onLocation(Location location, boolean cached);
    }

    private static final long MAX_CACHE_AGE_MS = 120_000;
    private static final long TIMEOUT_MS = 20_000;
    private final Activity activity;
    private final Listener listener;
    private final LocationManager manager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String> subscribedProviders = new ArrayList<>();
    private long generation;
    private boolean active;
    private LocationListener platformListener;
    private Runnable timeout;

    public DeviceLocation(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        manager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
    }

    public static boolean hasPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** The caller handles permission requests, and stops this helper when leaving the screen. */
    @SuppressLint("MissingPermission")
    public void start() {
        stop();
        final long request = generation;
        final long startedMillis = SystemClock.elapsedRealtime();
        active = true;
        if (!hasPermission(activity)) {
            fail(request, "Location permission is off. Choose a place on the map or enter coordinates.");
            return;
        }
        if (manager == null) {
            fail(request, "Device location is unavailable. Choose a place on the map or enter coordinates.");
            return;
        }

        List<String> enabled = new ArrayList<>();
        boolean supported = false;
        try {
            List<String> available = manager.getAllProviders();
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (available.contains(provider)) {
                    supported = true;
                    if (manager.isProviderEnabled(provider)) enabled.add(provider);
                }
            }
        } catch (SecurityException e) {
            fail(request, "Location permission is unavailable. Choose a place on the map or enter coordinates.");
            return;
        }
        if (enabled.isEmpty()) {
            fail(request, supported
                    ? "Device location is off. Turn it on in Settings, or choose a place on the map."
                    : "No location provider is available. Choose a place on the map or enter coordinates.");
            return;
        }

        Location best = null;
        long bestAge = Long.MAX_VALUE;
        for (String provider : enabled) {
            try {
                Location candidate = manager.getLastKnownLocation(provider);
                long age = ageMillis(candidate);
                if (age <= MAX_CACHE_AGE_MS && (best == null || age < bestAge)) {
                    best = candidate;
                    bestAge = age;
                }
            } catch (SecurityException | IllegalArgumentException ignored) {
                // Coarse permission can still use network location on older Android versions.
            }
        }
        if (best != null) {
            succeed(request, best, true);
            return;
        }

        platformListener = new LocationListener() {
            @Override public void onLocationChanged(Location location) {
                if (!isCurrent(request)) return;
                long age = ageMillis(location);
                // Do not mistake a provider's delayed historical result for a new fix.
                long requestAge = SystemClock.elapsedRealtime() - startedMillis;
                if (age <= MAX_CACHE_AGE_MS && age <= requestAge + 2_000) {
                    succeed(request, location, false);
                }
            }

            @Override public void onProviderDisabled(String provider) {
                if (!isCurrent(request)) return;
                for (String subscribed : subscribedProviders) {
                    try {
                        if (manager.isProviderEnabled(subscribed)) return;
                    } catch (SecurityException | IllegalArgumentException ignored) { }
                }
                fail(request, "Device location is off. Turn it on in Settings, or choose a place on the map.");
            }

            @Override public void onProviderEnabled(String provider) { }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
        };
        boolean permissionFailure = false;
        for (String provider : enabled) {
            try {
                manager.requestLocationUpdates(provider, 0L, 0f, platformListener, Looper.getMainLooper());
                subscribedProviders.add(provider);
            } catch (SecurityException e) {
                permissionFailure = true;
            } catch (IllegalArgumentException ignored) {
                // A provider can disappear between discovery and registration.
            }
        }
        if (subscribedProviders.isEmpty()) {
            fail(request, permissionFailure
                    ? "Location is unavailable with this permission. Choose a place on the map or enter coordinates."
                    : "No location provider is available. Choose a place on the map or enter coordinates.");
            return;
        }
        timeout = () -> fail(request,
                "No location fix yet. Try again with a clearer view of the sky, or choose a place on the map.");
        handler.postDelayed(timeout, TIMEOUT_MS);
        listener.onStatus("Finding your location… You can also choose a place on the map.");
    }

    /** Cancels updates and invalidates any queued callbacks, without changing the caller's UI. */
    public void stop() {
        generation++;
        active = false;
        if (timeout != null) {
            handler.removeCallbacks(timeout);
            timeout = null;
        }
        if (manager != null && platformListener != null) {
            try {
                manager.removeUpdates(platformListener);
            } catch (SecurityException | IllegalArgumentException ignored) { }
        }
        platformListener = null;
        subscribedProviders.clear();
    }

    private boolean isCurrent(long request) {
        return active && generation == request;
    }

    private void succeed(long request, Location location, boolean cached) {
        if (!isCurrent(request)) return;
        Location result = new Location(location);
        stop();
        listener.onLocation(result, cached);
    }

    private void fail(long request, String message) {
        if (!isCurrent(request)) return;
        stop();
        listener.onStatus(message);
    }

    /** Prefer the monotonic clock; use wall time only if elapsed time was not supplied. */
    private static long ageMillis(Location location) {
        if (location == null || !Double.isFinite(location.getLatitude())
                || !Double.isFinite(location.getLongitude())
                || Math.abs(location.getLatitude()) > 90 || Math.abs(location.getLongitude()) > 180) {
            return Long.MAX_VALUE;
        }
        long elapsedNanos = location.getElapsedRealtimeNanos();
        if (elapsedNanos < 0) return Long.MAX_VALUE;
        if (elapsedNanos > 0) {
            long now = SystemClock.elapsedRealtimeNanos();
            if (elapsedNanos > now) return Long.MAX_VALUE;
            return (now - elapsedNanos) / 1_000_000;
        }
        long timestamp = location.getTime();
        long now = System.currentTimeMillis();
        if (timestamp <= 0 || timestamp > now) return Long.MAX_VALUE;
        return now - timestamp;
    }
}
