package org.openstar.map;

/** An upright sky camera controlled by touch, independent of device sensors. */
public final class TouchSkyCamera {
    private static final double MIN_ZOOM = 0.5;
    private static final double MAX_ZOOM = 6.0;
    private static final double MAX_ALTITUDE = 89.5;
    private double azimuth;
    private double altitude;
    private double zoom;

    public TouchSkyCamera() { reset(); }

    public void reset() { azimuth = 0; altitude = 35; zoom = 1; }

    public void setView(double azimuth, double altitude, double zoom) {
        if (!Double.isFinite(azimuth) || !Double.isFinite(altitude) || !Double.isFinite(zoom)) return;
        this.azimuth = wrap(azimuth);
        this.altitude = clamp(altitude, -MAX_ALTITUDE, MAX_ALTITUDE);
        this.zoom = clamp(zoom, MIN_ZOOM, MAX_ZOOM);
    }

    public double azimuth() { return azimuth; }
    public double altitude() { return altitude; }
    public double zoom() { return zoom; }

    /** GestureDetector distances are previous minus current finger coordinates. */
    public void drag(float distanceX, float distanceY, int width, int height) {
        if (!Float.isFinite(distanceX) || !Float.isFinite(distanceY) || width <= 0 || height <= 0) return;
        double focal = focalPixels(width, height);
        // Account for horizontal circles narrowing toward the poles. The floor
        // avoids rapid heading spins near zenith/nadir; atan2 bounds large drags.
        double horizontalScale = Math.max(0.1, Math.cos(Math.toRadians(altitude)));
        double yaw = Math.toDegrees(Math.atan2(distanceX, focal * horizontalScale));
        double pitch = Math.toDegrees(Math.atan2(-distanceY, focal));
        setView(azimuth + yaw, altitude + pitch, zoom);
    }

    public void scale(double factor) {
        if (!Double.isFinite(factor) || factor <= 0) return;
        zoom = clamp(zoom * factor, MIN_ZOOM, MAX_ZOOM);
    }

    /** At 1x, the longer viewport edge has the camera-free AR field of view, 60 degrees. */
    public double focalPixels(int width, int height) {
        return Math.max(1, Math.max(width, height)) / (2 * Math.tan(Math.toRadians(30))) * zoom;
    }

    /** Row-major device-to-ENU matrix with right, up and negative-forward columns. */
    public float[] rotation() {
        double yaw = Math.toRadians(azimuth), pitch = Math.toRadians(altitude);
        double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
        return new float[]{
                (float) cy, (float) (-sp * sy), (float) (-cp * sy),
                (float) -sy, (float) (-sp * cy), (float) (-cp * cy),
                0, (float) cp, (float) -sp
        };
    }

    private static double wrap(double angle) {
        double wrapped = angle % 360;
        return wrapped < 0 ? wrapped + 360 : wrapped == 0 ? 0 : wrapped;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
