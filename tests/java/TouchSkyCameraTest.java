package org.openstar.map;

/** Touch camera geometry and gesture regression checks; needs only a desktop JDK. */
public final class TouchSkyCameraTest {
    private static int scenarios;
    private static final int WIDTH = 1000, HEIGHT = 1600;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void near(double actual, double expected, double tolerance) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance,
                actual + " != " + expected);
    }

    private static double[] project(TouchSkyCamera camera, double altitude, double azimuth) {
        return ArProjection.project(altitude, azimuth, 0, camera.rotation(),
                camera.focalPixels(WIDTH, HEIGHT), WIDTH, HEIGHT);
    }

    // Construct a direction tilted from the view centre toward its right/up basis.
    private static double[] offsetDirection(TouchSkyCamera camera, int basisColumn, double angle) {
        float[] r = camera.rotation();
        double c = Math.cos(Math.toRadians(angle)), s = Math.sin(Math.toRadians(angle));
        double east = -r[2] * c + r[basisColumn] * s;
        double north = -r[5] * c + r[3 + basisColumn] * s;
        double up = -r[8] * c + r[6 + basisColumn] * s;
        return new double[]{Math.toDegrees(Math.atan2(up, Math.hypot(east, north))),
                Math.toDegrees(Math.atan2(east, north))};
    }

    private static void orientation(double azimuth, double altitude) {
        TouchSkyCamera camera = new TouchSkyCamera();
        camera.setView(azimuth, altitude, 1);
        double[] centre = project(camera, altitude, azimuth);
        check(centre != null, "View centre hidden");
        near(centre[0], WIDTH / 2.0, 0.001);
        near(centre[1], HEIGHT / 2.0, 0.001);

        double[] right = offsetDirection(camera, 0, 12);
        double[] rightScreen = project(camera, right[0], right[1]);
        near(rightScreen[0], WIDTH / 2.0 + camera.focalPixels(WIDTH, HEIGHT) * Math.tan(Math.toRadians(12)), 0.001);
        near(rightScreen[1], HEIGHT / 2.0, 0.001);
        double[] up = offsetDirection(camera, 1, 12);
        double[] upScreen = project(camera, up[0], up[1]);
        near(upScreen[0], WIDTH / 2.0, 0.001);
        near(upScreen[1], HEIGHT / 2.0 - camera.focalPixels(WIDTH, HEIGHT) * Math.tan(Math.toRadians(12)), 0.001);
        check(project(camera, -altitude, azimuth + 180) == null, "Behind-view star should be hidden");

        float[] r = camera.rotation();
        for (int first = 0; first < 3; first++) {
            for (int second = 0; second < 3; second++) {
                double dot = r[first] * r[second] + r[3 + first] * r[3 + second] + r[6 + first] * r[6 + second];
                near(dot, first == second ? 1 : 0, 0.000001);
            }
        }
        scenarios++;
    }

    private static void gestureDirections(double azimuth, double altitude) {
        TouchSkyCamera camera = new TouchSkyCamera();
        camera.setView(azimuth, altitude, 1);
        camera.drag(50, 0, WIDTH, HEIGHT); // Finger moved left.
        double[] screen = project(camera, altitude, azimuth);
        check(screen != null && screen[0] < WIDTH / 2.0, "Star should follow a left drag");
        camera.setView(azimuth, altitude, 1);
        camera.drag(-50, 0, WIDTH, HEIGHT);
        screen = project(camera, altitude, azimuth);
        check(screen != null && screen[0] > WIDTH / 2.0, "Star should follow a right drag");
        camera.setView(azimuth, altitude, 1);
        camera.drag(0, -50, WIDTH, HEIGHT); // Finger moved down; view tilts upward.
        screen = project(camera, altitude, azimuth);
        check(camera.altitude() > altitude && screen != null && screen[1] > HEIGHT / 2.0,
                "Star should follow a downward drag");
        camera.setView(azimuth, altitude, 1);
        camera.drag(0, 50, WIDTH, HEIGHT);
        screen = project(camera, altitude, azimuth);
        check(camera.altitude() < altitude && screen != null && screen[1] < HEIGHT / 2.0,
                "Star should follow an upward drag");
        scenarios++;
    }

    public static void main(String[] args) {
        TouchSkyCamera camera = new TouchSkyCamera();
        near(camera.azimuth(), 0, 0); near(camera.altitude(), 35, 0); near(camera.zoom(), 1, 0);
        near(camera.focalPixels(1000, 1600), 1600 / (2 * Math.tan(Math.PI / 6)), 0.000001);
        near(camera.focalPixels(1600, 1000), camera.focalPixels(1000, 1600), 0);
        scenarios++;

        for (double yaw : new double[]{0, 90, 180, 270, 359}) {
            for (double pitch : new double[]{-89.5, -60, 0, 35, 80, 89.5}) orientation(yaw, pitch);
            for (double pitch : new double[]{-75, -30, 0, 35, 75}) gestureDirections(yaw, pitch);
        }

        camera.setView(-721, 200, 100); near(camera.azimuth(), 359, 0); near(camera.altitude(), 89.5, 0); near(camera.zoom(), 6, 0);
        camera.setView(1081, -200, -1); near(camera.azimuth(), 1, 0); near(camera.altitude(), -89.5, 0); near(camera.zoom(), .5, 0);
        camera.setView(359, 0, 1); camera.drag(100, 0, WIDTH, HEIGHT);
        check(camera.azimuth() >= 0 && camera.azimuth() < 90, "Heading must wrap through north");
        camera.setView(0, 89.4, 1); camera.drag(0, -Float.MAX_VALUE, WIDTH, HEIGHT); near(camera.altitude(), 89.5, 0);
        camera.setView(0, -89.4, 1); camera.drag(0, Float.MAX_VALUE, WIDTH, HEIGHT); near(camera.altitude(), -89.5, 0);
        scenarios++;

        camera.reset(); camera.scale(2); near(camera.zoom(), 2, 0);
        camera.scale(Double.MAX_VALUE); near(camera.zoom(), 6, 0);
        camera.scale(Double.MIN_VALUE); near(camera.zoom(), .5, 0);
        camera.reset(); camera.drag(50, 0, WIDTH, HEIGHT); double normalYaw = camera.azimuth();
        camera.reset(); camera.scale(2); camera.drag(50, 0, WIDTH, HEIGHT);
        check(camera.azimuth() < normalYaw, "Zoomed-in panning should turn through a smaller angle");
        scenarios++;

        camera.setView(123, 45, 2);
        camera.setView(Double.NaN, 0, 1); camera.setView(0, Double.POSITIVE_INFINITY, 1);
        camera.setView(0, 0, Double.NaN);
        camera.drag(Float.NaN, 0, WIDTH, HEIGHT); camera.drag(0, Float.NEGATIVE_INFINITY, WIDTH, HEIGHT);
        camera.drag(50, 50, 0, HEIGHT); camera.drag(50, 50, WIDTH, -1);
        camera.scale(Double.NaN); camera.scale(Double.POSITIVE_INFINITY); camera.scale(0); camera.scale(-1);
        near(camera.azimuth(), 123, 0); near(camera.altitude(), 45, 0); near(camera.zoom(), 2, 0);
        check(Double.isFinite(camera.focalPixels(0, 0)) && camera.focalPixels(0, 0) > 0, "Empty viewport must retain a finite focal length");
        camera.setView(Double.MAX_VALUE, 1, 1);
        check(camera.azimuth() >= 0 && camera.azimuth() < 360, "Large finite heading must wrap");
        float[] external = camera.rotation(); external[0] = Float.NaN;
        check(Float.isFinite(camera.rotation()[0]), "Returned matrix must not expose mutable camera state");
        camera.reset(); near(camera.azimuth(), 0, 0); near(camera.altitude(), 35, 0); near(camera.zoom(), 1, 0);
        scenarios++;
        System.out.println("Touch sky camera: " + scenarios + " scenarios passed");
    }
}
