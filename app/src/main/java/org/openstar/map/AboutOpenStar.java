package org.openstar.map;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.ScrollView;
import android.widget.TextView;

/** The same app information and full notices from either sky mode. */
public final class AboutOpenStar {
    private AboutOpenStar() {}
    private static int dp(Activity activity,int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    public static void show(Activity activity,int starCount){String notice;try(java.io.InputStream stream=activity.getAssets().open("NOTICE.txt");java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream()){byte[] buffer=new byte[4096];int n;while((n=stream.read(buffer))!=-1)bytes.write(buffer,0,n);notice=new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception e){notice="See the source distribution for licenses.";}
        TextView text=new TextView(activity);text.setPadding(dp(activity,20),dp(activity,8),dp(activity,20),dp(activity,16));text.setText("OpenStar 1.1.6\n\nAn offline, open-source Android star atlas powered by the shared Python StarMapGenerator and Skyfield.\n\n"+starCount+" catalogue stars. Constellation guides: Orion, Ursa Major, Cassiopeia, Cygnus, Leo, Scorpius, Crux and the Summer Triangle.\n\nNormal mode is a perspective sky view like AR. Drag to look around; pinch to zoom; double tap to face north again. Camera and phone orientation do not control normal mode. Dates use the time zone of your selected observing coordinates, including daylight-saving changes.\n\nSkyfield apparent star positions using bundled JPL DE421 data. No atmospheric refraction, stellar proper motion, terrain, daylight or weather model. Above the horizon does not guarantee visibility. No Sun, Moon or planets in this version.\n\nAR follows compass orientation with the camera switched on or off, with approximate alignment and manual heading calibration. Camera frames are previewed only; no photos or videos are saved.\n\nNo accounts, ads or analytics. Sky calculations stay offline. The location picker loads OpenStreetMap street tiles over the internet; tile requests reveal the viewed area and your IP address to OpenStreetMap. A bundled world overview works offline. Device location is optional, used once in the foreground and stored on this device. Night mode reddens both sky views; system dialogs retain the Android theme.\n\n"+notice);
        ScrollView scroll=new ScrollView(activity);scroll.addView(text);new AlertDialog.Builder(activity).setTitle("About OpenStar").setView(scroll).setPositiveButton("Done",null).show();}
}
