package org.openstar.map;

/** Geometry regression checks; runs on a desktop JDK without Android or a camera. */
public final class ArProjectionTest {
    private static void near(double actual,double expected){if(Math.abs(actual-expected)>1e-5)throw new AssertionError(actual+" != "+expected);}
    public static void main(String[] args){
        // Upright phone: screen right=east, screen up=up, rear camera=north.
        float[] north={1,0,0, 0,0,-1, 0,1,0};
        double[] center=ArProjection.project(0,0,0,north,500,1000,800);
        near(center[0],500);near(center[1],400);
        double[] east=ArProjection.project(0,45,0,north,500,1000,800);
        near(east[0],1000);near(east[1],400);
        double[] up=ArProjection.project(45,0,0,north,500,1000,800);
        near(up[0],500);near(up[1],-100);
        if(ArProjection.project(0,180,0,north,500,1000,800)!=null)throw new AssertionError("Behind camera should be hidden");
        double[] corrected=ArProjection.project(0,12,12,north,500,1000,800);
        near(corrected[0],500);near(corrected[1],400);
        // Camera pointed straight up must have no azimuth singularity.
        float[] zenith={1,0,0, 0,-1,0, 0,0,-1};
        double[] overhead=ArProjection.project(90,123,0,zenith,500,1000,800);
        near(overhead[0],500);near(overhead[1],400);
        // Landscape screen basis: screen right=down and screen up=east.
        float[] landscape={0,1,0, 0,0,-1, -1,0,0};
        double[] landscapeEast=ArProjection.project(0,45,0,landscape,500,1000,800);
        near(landscapeEast[0],500);near(landscapeEast[1],-100);
        System.out.println("AR projection: 7 scenarios passed");
    }
}
