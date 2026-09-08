package org.openstar.map;

/** Pinhole projection of true-north horizontal coordinates onto the rear camera. */
public final class ArProjection {
    private ArProjection() {}

    public static double[] deviceVector(double altitude, double azimuth, double declination,
                                         float[] deviceToMagneticWorld) {
        double alt=Math.toRadians(altitude), az=Math.toRadians(azimuth-declination);
        double east=Math.cos(alt)*Math.sin(az), north=Math.cos(alt)*Math.cos(az), up=Math.sin(alt);
        float[] r=deviceToMagneticWorld;
        // R maps device axes to east, magnetic north, up. Its inverse is its transpose.
        return new double[]{r[0]*east+r[3]*north+r[6]*up,
                            r[1]*east+r[4]*north+r[7]*up,
                            r[2]*east+r[5]*north+r[8]*up};
    }

    public static double[] project(double altitude, double azimuth, double declination,
                                    float[] rotation, double focalPixels, int width, int height) {
        double[] v=deviceVector(altitude,azimuth,declination,rotation);
        double depth=-v[2]; // Rear camera looks along device -Z.
        if(depth<=0.01)return null;
        return new double[]{width*.5+focalPixels*v[0]/depth,
                            height*.5-focalPixels*v[1]/depth};
    }
}
