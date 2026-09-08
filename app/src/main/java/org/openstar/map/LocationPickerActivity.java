package org.openstar.map;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/** Geographic selection is a draft until the user chooses Use this location. */
public final class LocationPickerActivity extends Activity {
    private static final String PAGE="https://appassets.androidplatform.net/map/index.html";
    private double latitude,longitude,accuracy;
    private int zoom=11;
    private String place;
    private boolean night,pageReady,locationWanted,foreground,closed;
    private WebView web;
    private TextView coordinates,status;
    private DeviceLocation locator;
    private LinearLayout root;
    private int ink,bg,panel;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        latitude=getIntent().getDoubleExtra("latitude",22.3193);longitude=getIntent().getDoubleExtra("longitude",114.1694);
        place=getIntent().getStringExtra("place");if(place==null)place="Selected location";
        night=getIntent().getBooleanExtra("night",false);
        if(state!=null){latitude=state.getDouble("latitude");longitude=state.getDouble("longitude");accuracy=state.getDouble("accuracy");place=state.getString("place",place);zoom=state.getInt("zoom",11);locationWanted=state.getBoolean("locationWanted");}
        if(!valid(latitude,longitude)){latitude=22.3193;longitude=114.1694;place="Hong Kong · reference location";}
        ink=night?Color.rgb(225,88,71):Color.rgb(190,231,222);bg=night?Color.rgb(12,3,3):Color.rgb(7,19,31);panel=night?Color.rgb(38,10,9):Color.rgb(18,42,53);
        root=column();root.setBackgroundColor(bg);root.setPadding(dp(12),0,dp(12),0);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(dp(12)+b.left,b.top,dp(12)+b.right,b.bottom);}
            else v.setPadding(dp(12)+insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),dp(12)+insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout title=row();root.addView(title);
        title.addView(label("Choose location",23),new LinearLayout.LayoutParams(0,dp(50),1));
        title.addView(button("Cancel",v->finish()),new LinearLayout.LayoutParams(dp(74),dp(48)));
        TextView hint=label("Tap the map or drag the pin · pinch to zoom",12);hint.setPadding(0,0,0,dp(8));root.addView(hint);
        FrameLayout mapFrame=new FrameLayout(this);root.addView(mapFrame,new LinearLayout.LayoutParams(-1,0,1));
        coordinates=label("",14);coordinates.setPadding(dp(4),dp(8),0,dp(4));root.addView(coordinates);
        status=label("Loading map…",11);status.setMinHeight(dp(30));root.addView(status);
        LinearLayout actions=row();root.addView(actions);addButton(actions,"My location",v->myLocation());addButton(actions,"Enter coordinates",v->manualCoordinates());
        Button use=button("Use this location",v->{locationWanted=false;locator.stop();setResult(RESULT_OK,new Intent().putExtra("latitude",latitude).putExtra("longitude",longitude).putExtra("place",place));finish();});
        LinearLayout.LayoutParams useParams=new LinearLayout.LayoutParams(-1,dp(50));useParams.setMargins(0,dp(5),0,dp(8));root.addView(use,useParams);
        locator=new DeviceLocation(this,new DeviceLocation.Listener(){
            @Override public void onStatus(String message){if(!closed&&locationWanted)status.setText(message);}
            @Override public void onLocation(Location fix,boolean cached){
                if(closed||!foreground||!locationWanted)return;
                locationWanted=false;latitude=fix.getLatitude();longitude=fix.getLongitude();accuracy=fix.hasAccuracy()?fix.getAccuracy():0;
                place=cached?"Recent device location":"Device location";zoom=accuracy>1000?11:15;showCoordinates();positionMap();
                status.setText(accuracy>0?String.format(Locale.US,"Device location · accuracy about %.0f m",accuracy):"Device location selected.");
            }
        });
        showCoordinates();
        if(getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE){
            root.removeView(mapFrame);root.removeView(coordinates);root.removeView(status);root.removeView(actions);root.removeView(use);
            LinearLayout body=row(),controls=column();root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
            body.addView(mapFrame,new LinearLayout.LayoutParams(0,-1,1));body.addView(controls,new LinearLayout.LayoutParams(dp(320),-2));controls.setPadding(dp(10),0,0,0);
            controls.addView(coordinates);controls.addView(status);controls.addView(actions);controls.addView(use,useParams);
        }
        try{
            web=new WebView(this);mapFrame.addView(web,new FrameLayout.LayoutParams(-1,-1));
            WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
            settings.setGeolocationEnabled(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setSupportMultipleWindows(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            settings.setUserAgentString(settings.getUserAgentString()+" OpenStar/1.1.3 (org.openstar.map)");
            CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
            web.setBackgroundColor(Color.rgb(17,38,52));web.addJavascriptInterface(new MapBridge(),"OpenStarNative");
            web.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                    Uri uri=request.getUrl();String host=uri.getHost(),path=uri.getPath();
                    if(!"https".equals(uri.getScheme())||!"GET".equals(request.getMethod())||(uri.getPort()!=-1&&uri.getPort()!=443))return blocked();
                    if("appassets.androidplatform.net".equals(host)&&path!=null&&path.startsWith("/map/")&&!path.contains("..")){
                        try{return new WebResourceResponse(mime(path),"UTF-8",getAssets().open(path.substring(1)));}catch(IOException e){return blocked();}
                    }
                    if("tile.openstreetmap.org".equals(host)&&path!=null&&path.matches("/\\d{1,2}/\\d+/\\d+\\.png")&&uri.getQuery()==null&&!request.isForMainFrame())return null;
                    return blocked();
                }
                @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                    if(PAGE.equals(request.getUrl().toString()))return false;
                    Uri uri=request.getUrl();String host=uri.getHost();
                    if(request.isForMainFrame()&&request.hasGesture()&&"https".equals(uri.getScheme())&&Arrays.asList("www.openstreetmap.org","leafletjs.com","www.naturalearthdata.com").contains(host)){
                        try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(ActivityNotFoundException e){status.setText("No browser available to open the map attribution.");}
                    }
                    return true;
                }
                @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())status.setText("Map unavailable. Use My location or Enter coordinates.");}
                @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){
                    pageReady=false;mapFrame.removeView(view);view.destroy();web=null;status.setText("Map stopped. You can still enter coordinates or use My location.");return true;
                }
            });
            web.loadUrl(PAGE);
        }catch(RuntimeException e){
            mapFrame.removeAllViews();mapFrame.addView(label("Map viewer unavailable.\nUse My location or Enter coordinates below.",16));web=null;
            status.setText("Install or update Android System WebView to display the map.");
        }
    }
    private static boolean valid(double lat,double lon){return Double.isFinite(lat)&&Double.isFinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180;}
    private static WebResourceResponse blocked(){return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
    private static String mime(String path){if(path.endsWith(".js"))return "application/javascript";if(path.endsWith(".css"))return "text/css";if(path.endsWith(".json")||path.endsWith(".geojson"))return "application/json";if(path.endsWith(".png"))return "image/png";if(path.endsWith(".svg"))return "image/svg+xml";return "text/html";}
    public final class MapBridge {
        @JavascriptInterface public void ready(){runOnUiThread(()->{if(!closed&&web!=null){pageReady=true;positionMap();}});}
        @JavascriptInterface public void selected(double lat,double lon){runOnUiThread(()->{
            if(closed||!foreground||!valid(lat,lon))return;
            locationWanted=false;locator.stop();latitude=lat;longitude=lon;accuracy=0;place="Selected on map";showCoordinates();status.setText("Map point selected. Tap Use this location to apply.");
        });}
        @JavascriptInterface public void mapStatus(String message){runOnUiThread(()->{if(!closed&&!locationWanted&&message!=null)status.setText(message.length()>200?message.substring(0,200):message);});}
    }
    private void positionMap(){if(pageReady&&web!=null)web.evaluateJavascript(String.format(Locale.US,"window.OpenStarMap.setPosition(%.9f,%.9f,%.1f,%d)",latitude,longitude,accuracy,zoom),null);}
    private void showCoordinates(){coordinates.setText(String.format(Locale.US,"Latitude %.5f°   Longitude %.5f°\n%s",latitude,longitude,place));}
    private void myLocation(){
        locationWanted=true;
        if(DeviceLocation.hasPermission(this))locator.start();
        else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},12);
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==12&&locationWanted){if(DeviceLocation.hasPermission(this)){if(foreground)locator.start();}else{locationWanted=false;status.setText("Location permission declined. Tap the map or enter coordinates.");}}
    }
    private void manualCoordinates(){
        locationWanted=false;locator.stop();LinearLayout form=column();form.setPadding(dp(20),0,dp(20),0);
        EditText lat=new EditText(this),lon=new EditText(this);lat.setHint("Latitude −90 to 90");lon.setHint("Longitude −180 to 180");
        lat.setContentDescription("Latitude in degrees");lon.setContentDescription("Longitude in degrees, east positive");
        for(EditText f:new EditText[]{lat,lon}){f.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);form.addView(f);}
        lat.setText(Double.toString(latitude));lon.setText(Double.toString(longitude));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Coordinates in degrees").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Show on map",null).create();d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{
            double a=Double.parseDouble(lat.getText().toString().replace(',','.')),b=Double.parseDouble(lon.getText().toString().replace(',','.'));if(!valid(a,b))throw new NumberFormatException();
            latitude=a;longitude=b;accuracy=0;place="Manual coordinates";showCoordinates();positionMap();status.setText("Coordinates selected. Tap Use this location to apply.");d.dismiss();
        }catch(NumberFormatException e){lat.setError("Latitude −90…90; longitude −180…180.");}});
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView label(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(ink);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private Button button(String text,View.OnClickListener click){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(12);b.setTextColor(ink);b.setPadding(dp(4),0,dp(4),0);b.setMinWidth(0);b.setMinimumWidth(0);b.setOnClickListener(click);GradientDrawable shape=new GradientDrawable();shape.setColor(panel);shape.setCornerRadius(dp(10));b.setBackground(shape);return b;}
    private void addButton(LinearLayout row,String title,View.OnClickListener click){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.setMargins(dp(2),0,dp(2),0);row.addView(button(title,click),p);}
    @Override protected void onResume(){super.onResume();foreground=true;if(web!=null)web.onResume();if(locationWanted&&DeviceLocation.hasPermission(this))locator.start();}
    @Override protected void onPause(){super.onPause();foreground=false;if(locator!=null)locator.stop();if(web!=null)web.onPause();}
    @Override protected void onSaveInstanceState(Bundle s){super.onSaveInstanceState(s);s.putDouble("latitude",latitude);s.putDouble("longitude",longitude);s.putDouble("accuracy",accuracy);s.putString("place",place);s.putInt("zoom",zoom);s.putBoolean("locationWanted",locationWanted);}
    @Override protected void onDestroy(){closed=true;if(locator!=null)locator.stop();if(web!=null){web.removeJavascriptInterface("OpenStarNative");((android.view.ViewGroup)web.getParent()).removeView(web);web.destroy();web=null;}super.onDestroy();}
}
