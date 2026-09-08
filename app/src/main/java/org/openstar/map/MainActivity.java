package org.openstar.map;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class MainActivity extends Activity {
    private Catalog catalog;
    private SkyView sky;
    private LinearLayout root,header,footer;
    private TextView status,selection,hourLabel;
    private SeekBar hourBar;
    private Button liveButton;
    private boolean hourTracking;
    private long hourAnchor;
    private final List<TextView> tinted=new ArrayList<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private double latitude=22.3193,longitude=114.1694;
    private long chosenTime;
    private ZoneId observerZone=ZoneOffset.UTC;
    private String observerZoneProblem;
    private boolean night,live;
    private String locationName="Hong Kong · reference location";
    private boolean configured;
    private String lastFailure;
    private int calculationGeneration;
    private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private DeviceLocation deviceLocation;
    private EditText initialMoment,initialLat,initialLon;
    private TextView initialLocationStatus,initialZoneStatus;
    private boolean initialTimeEdited,changingInitialTime;
    private boolean changingInitial,initialEdited,defaultAttempted,locationWanted;
    private Bundle restoredInputs;
    private final Runnable ticker=new Runnable(){public void run(){if(live){if(chosenTime==0&&!hourTracking&&lastFailure==null)refresh();handler.postDelayed(this,30000);}}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        android.content.SharedPreferences prefs=SkyPreferences.get(this);
        latitude=Double.longBitsToDouble(prefs.getLong("lat",Double.doubleToLongBits(latitude)));
        longitude=Double.longBitsToDouble(prefs.getLong("lon",Double.doubleToLongBits(longitude)));
        locationName=prefs.getString("place",locationName);night=prefs.getBoolean("night",false);
        setObserverZone(latitude,longitude);
        if(state!=null){initialTimeEdited=state.getBoolean("initialTimeEdited");chosenTime=state.getLong("time");configured=state.getBoolean("configured");restoredInputs=state;initialEdited=state.getBoolean("initialEdited");locationWanted=state.getBoolean("locationWanted");defaultAttempted=state.getBoolean("defaultAttempted");}
        deviceLocation=new DeviceLocation(this,new DeviceLocation.Listener(){
            @Override public void onStatus(String message){if(initialLocationStatus!=null&&locationWanted)initialLocationStatus.setText(message);}
            @Override public void onLocation(Location fix,boolean cached){
                if(!locationWanted||configured||initialEdited)return;
                latitude=fix.getLatitude();longitude=fix.getLongitude();locationName=cached?"Recent device location":"Device location";
                fillInitialCoordinates();initialLocationStatus.setText("Default: "+locationName+(fix.hasAccuracy()?String.format(Locale.US," · ±%.0f m",fix.getAccuracy()):""));
                locationWanted=false;
            }
        });
        try{catalog=new Catalog(this);}catch(Exception ex){new AlertDialog.Builder(this).setTitle("Cannot load star catalogue").setMessage(ex.getMessage()).setPositiveButton("Close",(d,w)->finish()).setCancelable(false).show();return;}
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),0,dp(16),0);
        if(Build.VERSION.SDK_INT>=30)root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(dp(16)+bars.left,bars.top,dp(16)+bars.right,bars.bottom);return insets;});
        // API 26–29 use the legacy inset accessors.
        if(Build.VERSION.SDK_INT<30)root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(16)+insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),dp(16)+insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        setContentView(root);
        header=column();root.addView(header);
        TextView eyebrow=label("O P E N S T A R   /   O F F L I N E",10);eyebrow.setPadding(0,dp(14),0,dp(5));header.addView(eyebrow);
        LinearLayout titleRow=row();header.addView(titleRow);
        TextView title=label("Your sky, tonight.",27);title.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));titleRow.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        titleRow.addView(button("•••",v->options(),"Map options and licenses"),new LinearLayout.LayoutParams(dp(52),dp(48)));
        status=label("",11);status.setPadding(0,0,0,dp(8));status.setOnClickListener(v->{if(lastFailure!=null)showCalculationError();});header.addView(status);
        sky=new SkyView(this,catalog);sky.onSelected=this::select;
        sky.showLines=prefs.getBoolean("lines",true);sky.showLabels=prefs.getBoolean("labels",true);sky.magnitudeLimit=prefs.getFloat("magnitude",5.5f);
        root.addView(sky,new LinearLayout.LayoutParams(-1,0,1));
        footer=column();root.addView(footer);
        selection=label("Explore the sky.\nDrag to look around · pinch to zoom · tap a star",13);selection.setMinHeight(dp(66));selection.setGravity(Gravity.CENTER_VERTICAL);selection.setPadding(dp(12),dp(7),dp(12),dp(7));footer.addView(selection);
        LinearLayout actions=row();footer.addView(actions);
        addButton(actions,"Find star",v->search());addButton(actions,"Location",v->location());addButton(actions,"Time",v->chooseDate());addButton(actions,"AR",v->openAr());
        addHourTimeline();
        TextView hint=label("TOUCH SKY  ·  DOUBLE TAP TO RESET VIEW",9);hint.setGravity(Gravity.CENTER);hint.setPadding(0,dp(6),0,dp(10));footer.addView(hint);
        if(getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE){
            eyebrow.setVisibility(View.GONE);title.setVisibility(View.GONE);
            titleRow.removeView(title);header.removeView(status);titleRow.addView(status,0,new LinearLayout.LayoutParams(0,-2,1));
            selection.setMinHeight(dp(44));selection.setTextSize(11);hint.setVisibility(View.GONE);
            root.removeView(sky);root.removeView(footer);
            LinearLayout landscape=row();root.addView(landscape,new LinearLayout.LayoutParams(-1,0,1));
            landscape.addView(sky,new LinearLayout.LayoutParams(0,-1,1));
            landscape.addView(footer,new LinearLayout.LayoutParams(dp(320),-2));
        }
        if(state!=null&&state.containsKey("selected")){int hip=state.getInt("selected");for(Catalog.Star s:catalog.stars)if(s.hip==hip)sky.selected=s;}
        sky.restoreState(state);applyColors();if(configured)refresh();else initialInputs();
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView label(String value,int sp){TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);tinted.add(t);return t;}
    private Button button(String text,View.OnClickListener click,String description){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(12);b.setPadding(dp(5),0,dp(5),0);b.setMinWidth(0);b.setMinimumWidth(0);b.setOnClickListener(click);if(description!=null)b.setContentDescription(description);tinted.add(b);return b;}
    private void addButton(LinearLayout row,String text,View.OnClickListener click){Button b=button(text,click,null);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.setMargins(dp(2),dp(3),dp(2),0);row.addView(b,p);}
    private void addHourTimeline(){
        LinearLayout timeline=row();timeline.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);timeline.setPadding(0,dp(8),0,0);footer.addView(timeline);
        LinearLayout slider=column();timeline.addView(slider,new LinearLayout.LayoutParams(0,-2,1));
        hourLabel=label("",11);hourLabel.setPadding(dp(12),0,0,0);slider.addView(hourLabel);
        hourBar=new SeekBar(this);hourBar.setMax(23);hourBar.setKeyProgressIncrement(1);hourBar.setPadding(dp(16),0,dp(16),0);
        hourBar.setContentDescription("Hour of selected date, from 00 to 23");slider.addView(hourBar,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout ticks=row();ticks.setPadding(dp(12),0,dp(12),0);slider.addView(ticks);
        for(int i=0;i<3;i++){TextView tick=label(new String[]{"00:00","12:00","23:00"}[i],9);tick.setGravity(i==0?Gravity.START:i==1?Gravity.CENTER:Gravity.END);ticks.addView(tick,new LinearLayout.LayoutParams(0,-2,1));}
        liveButton=button("Live",v->{hourTracking=false;chosenTime=0;refresh();},"Return to the current time and follow it live");
        LinearLayout.LayoutParams liveParams=new LinearLayout.LayoutParams(dp(72),dp(48));liveParams.setMargins(dp(8),0,dp(2),0);timeline.addView(liveButton,liveParams);
        hourBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onStartTrackingTouch(SeekBar bar){hourTracking=true;hourAnchor=time();}
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){
                if(!fromUser)return;
                try{chosenTime=hourTracking?HourTimeline.selectHour(hourAnchor,progress,observerZone):HourTimeline.selectHourForNavigation(time(),progress,observerZone);}
                catch(IllegalArgumentException e){toast("Choose a date from 2000 to 2050.");updateHourControls(time());return;}
                calculationGeneration++;updateHourControls(chosenTime);
                if(hourTracking)status.setText("Release the hour bar to update the map.");else refresh();
            }
            @Override public void onStopTrackingTouch(SeekBar bar){hourTracking=false;refresh();}
        });
        updateHourControls(time());
    }
    private void updateHourControls(long instant){
        if(hourBar==null)return;
        if(!hourTracking)hourBar.setProgress(HourTimeline.hour(instant,observerZone));
        String when=DateTimeFormatter.ofPattern("HH:mm z",Locale.getDefault()).withZone(observerZone).format(Instant.ofEpochMilli(instant));
        hourLabel.setText("Hour · "+when);
        if(Build.VERSION.SDK_INT>=30)hourBar.setStateDescription(when);
        liveButton.setText(chosenTime==0?"● Live":"Live");
        liveButton.setContentDescription(chosenTime==0?"Live time is on":"Return to the current time and follow it live");
    }
    private void applyColors(){
        int ink=night?Color.rgb(225,88,71):Color.rgb(190,231,222), bg=night?Color.rgb(12,3,3):Color.rgb(7,19,31);
        root.setBackgroundColor(bg);sky.night=night;
        for(TextView t:tinted){t.setTextColor(ink);if(t instanceof Button){GradientDrawable d=new GradientDrawable();d.setColor(night?Color.rgb(38,10,9):Color.rgb(18,42,53));d.setCornerRadius(dp(10));t.setBackground(d);}}
        android.content.res.ColorStateList sliderInk=android.content.res.ColorStateList.valueOf(ink);
        hourBar.setProgressTintList(sliderInk);hourBar.setThumbTintList(sliderInk);
        hourBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(night?Color.rgb(83,31,26):Color.rgb(48,79,89)));
        GradientDrawable d=new GradientDrawable();d.setColor(night?Color.rgb(28,8,7):Color.rgb(13,33,45));d.setCornerRadius(dp(12));selection.setBackground(d);sky.invalidate();
    }
    private long time(){return chosenTime==0?System.currentTimeMillis():chosenTime;}
    private void openAr(){
        if(!configured)return;
        if(lastFailure!=null){showCalculationError();return;}
        startActivity(new Intent(this,ArActivity.class).putExtra("latitude",latitude).putExtra("longitude",longitude)
            .putExtra("time",chosenTime).putExtra("magnitude",sky.magnitudeLimit).putExtra("lines",sky.showLines)
            .putExtra("labels",sky.showLabels).putExtra("night",night).putExtra("zone",observerZone.getId()).putExtra("zoneProblem",observerZoneProblem));
    }
    private void refresh(){if(sky==null||!configured||isDestroyed())return;
        long instant=time();updateHourControls(instant);double lat=latitude,lon=longitude;int generation=++calculationGeneration;
        lastFailure=null;status.setText("Calculating with Skyfield…");
        worker.execute(()->{try{double[][] positions=catalog.calculate(lat,lon,instant);runOnUiThread(()->{
            if(isDestroyed()||generation!=calculationGeneration)return;
            catalog.apply(positions);renderStatus(instant);
        });}catch(Exception e){android.util.Log.e("OpenStar", "Sky calculation failed", e);runOnUiThread(()->{if(!isDestroyed()&&generation==calculationGeneration){
            long pageSize=0;try{pageSize=android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE);}catch(Exception ignored){}
            lastFailure="OpenStar 1.1.6 · Android API "+Build.VERSION.SDK_INT+" · page size "+pageSize+"\n\n"+android.util.Log.getStackTraceString(e);
            status.setText("Map could not load. Tap here for error details.");showCalculationError();
        }});}});
    }
    private void showCalculationError(){
        if(lastFailure==null)return;
        final String details=lastFailure;
        TextView text=new TextView(this);text.setTextSize(12);text.setPadding(dp(18),dp(8),dp(18),dp(12));text.setTextIsSelectable(true);
        text.setText("The map calculation failed. Copy the details below to report the problem.\n\n"+details);
        ScrollView scroll=new ScrollView(this);scroll.addView(text);
        new AlertDialog.Builder(this).setTitle(details.contains("ImportError")?"Python library could not load":"Map calculation failed").setView(scroll)
            .setPositiveButton("Retry",(d,w)->refresh()).setNegativeButton("Close",null)
            .setNeutralButton("Copy details",(d,w)->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("OpenStar error",details));toast("Error details copied.");}).show();
    }
    private void renderStatus(long instant){
        String when=DateTimeFormatter.ofPattern("EEE d MMM yyyy · HH:mm z",Locale.getDefault()).withZone(observerZone).format(Instant.ofEpochMilli(instant));
        status.setText((chosenTime==0?"LIVE  ·  ":"PREVIEW  ·  ")+when+" · "+zoneDescription()+"\n"+locationName+String.format(Locale.US,"  |  %.2f°, %.2f°",latitude,longitude));
        if(sky.selected!=null)select(sky.selected);sky.invalidate();
    }
    private void initialInputs(){
        LinearLayout form=column();form.setPadding(dp(20),0,dp(20),0);
        TextView help=new TextView(this);help.setText("Time zone is set from the coordinates. Automatic time follows that zone; an explicitly entered UTC offset is respected. Longitude is positive east.");form.addView(help);
        EditText moment=new EditText(this),lat=new EditText(this),lon=new EditText(this);initialMoment=moment;initialLat=lat;initialLon=lon;
        moment.setSingleLine();moment.setHint("2026-09-08T21:00:00+08:00");moment.setText(Instant.now().atZone(observerZone).toOffsetDateTime().withNano(0).toString());
        moment.setContentDescription("Observation date and time with UTC offset");form.addView(moment);
        lat.setHint("Latitude −90 to 90");lat.setContentDescription("Latitude in degrees");lon.setHint("Longitude −180 to 180");lon.setContentDescription("Longitude in degrees, east positive");
        for(EditText field:new EditText[]{lat,lon}){field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);form.addView(field);}
        lat.setText(Double.toString(latitude));lon.setText(Double.toString(longitude));
        if(restoredInputs!=null){moment.setText(restoredInputs.getString("inputTime",moment.getText().toString()));lat.setText(restoredInputs.getString("inputLat",lat.getText().toString()));lon.setText(restoredInputs.getString("inputLon",lon.getText().toString()));}
        initialZoneStatus=new TextView(this);initialZoneStatus.setTextSize(12);initialZoneStatus.setPadding(0,dp(6),0,dp(6));form.addView(initialZoneStatus);
        moment.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){if(!changingInitialTime)initialTimeEdited=true;updateInitialZoneLabel();}public void afterTextChanged(Editable e){}});
        updateInitialZone();
        initialLocationStatus=new TextView(this);initialLocationStatus.setTextSize(12);initialLocationStatus.setPadding(0,dp(6),0,dp(6));initialLocationStatus.setText("Starting point: "+locationName+". Choose a map point or use My location.");form.addView(initialLocationStatus);
        TextWatcher editListener=new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(!changingInitial){initialEdited=true;locationWanted=false;deviceLocation.stop();locationName="Manual coordinates";initialLocationStatus.setText("Manual coordinates. Your edits take priority.");updateInitialZone();}}public void afterTextChanged(Editable e){}};
        lat.addTextChangedListener(editListener);lon.addTextChangedListener(editListener);
        LinearLayout mapActions=row();form.addView(mapActions);addButton(mapActions,"Choose on map",v->openLocationMap());addButton(mapActions,"My location",v->requestDefaultLocation(true));
        ScrollView inputScroll=new ScrollView(this);inputScroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Generate your star map").setView(inputScroll).setPositiveButton("Generate map",null).setNegativeButton("Close app",(d,w)->finish()).setCancelable(false).create();dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{java.time.OffsetDateTime input=java.time.OffsetDateTime.parse(moment.getText().toString().trim());int year=input.toInstant().atZone(ZoneOffset.UTC).getYear();if(year<2000||year>2050)throw new java.time.DateTimeException("range");chosenTime=input.toInstant().toEpochMilli();}
            catch(java.time.DateTimeException e){moment.setError("Enter ISO time with UTC offset; years 2000–2050.");return;}
            try{double a=Double.parseDouble(lat.getText().toString().replace(',','.')),b=Double.parseDouble(lon.getText().toString().replace(',','.'));if(!Double.isFinite(a)||!Double.isFinite(b)||Math.abs(a)>90||Math.abs(b)>180)throw new NumberFormatException();latitude=a;longitude=b;}
            catch(NumberFormatException e){lat.setError("Latitude −90…90; longitude −180…180.");return;}
            setObserverZone(latitude,longitude);locationWanted=false;deviceLocation.stop();configured=true;save();dialog.dismiss();refresh();
        });
    }
    private void setObserverZone(double lat,double lon){
        try{observerZone=ObserverTimeZone.resolve(this,lat,lon);observerZoneProblem=null;}
        catch(IllegalArgumentException|IllegalStateException error){observerZone=ZoneOffset.UTC;observerZoneProblem="Location time zone unavailable; using UTC";android.util.Log.w("OpenStar","Location time zone lookup unavailable",error);}
    }
    private String zoneDescription(){return observerZoneProblem==null?observerZone.getId():observerZoneProblem;}
    private void updateInitialZone(){
        if(configured||initialLat==null||initialLon==null)return;
        try{
            double lat=Double.parseDouble(initialLat.getText().toString().replace(',','.')),lon=Double.parseDouble(initialLon.getText().toString().replace(',','.'));
            if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new IllegalArgumentException();
            setObserverZone(lat,lon);
            if(!initialTimeEdited&&initialMoment!=null){
                try{Instant instant=OffsetDateTime.parse(initialMoment.getText().toString().trim()).toInstant();changingInitialTime=true;initialMoment.setText(instant.atZone(observerZone).toOffsetDateTime().withNano(0).toString());}
                catch(java.time.DateTimeException ignored){}finally{changingInitialTime=false;}
            }
            updateInitialZoneLabel();
        }catch(IllegalArgumentException ignored){if(initialZoneStatus!=null)initialZoneStatus.setText("Enter valid coordinates to set the time zone.");}
    }
    private void updateInitialZoneLabel(){
        if(initialZoneStatus==null)return;
        try{double lat=Double.parseDouble(initialLat.getText().toString().replace(',','.')),lon=Double.parseDouble(initialLon.getText().toString().replace(',','.'));if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new NumberFormatException();}
        catch(NumberFormatException ignored){initialZoneStatus.setText("Enter valid coordinates to set the time zone.");return;}
        Instant instant=Instant.now();try{instant=OffsetDateTime.parse(initialMoment.getText().toString().trim()).toInstant();}catch(java.time.DateTimeException ignored){}
        initialZoneStatus.setText("Location time zone · "+zoneDescription()+" · UTC"+observerZone.getRules().getOffset(instant).getId().replace("Z","+00:00"));
    }
    private void select(Catalog.Star star){sky.selected=star;selection.setText(star.title()+"  /  "+star.constellation+String.format(Locale.US,"\n%s · altitude %.1f° · azimuth %.1f° · mag %.1f",star.altitude>=0?"Above horizon":"Below horizon",star.altitude,star.azimuth,star.magnitude));sky.invalidate();}
    private void search(){
        LinearLayout body=column();body.setPadding(dp(16),0,dp(16),0);
        EditText query=new EditText(this);query.setSingleLine();query.setHint("Name, HIP number or constellation (Ori)");query.setTextSize(14);body.addView(query);
        ListView list=new ListView(this);body.addView(list,new LinearLayout.LayoutParams(-1,dp(320)));
        List<Catalog.Star> matches=new ArrayList<>();ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,new ArrayList<>());list.setAdapter(adapter);
        Runnable filter=()->{String q=query.getText().toString().trim().toLowerCase(Locale.ROOT);matches.clear();adapter.clear();for(Catalog.Star s:catalog.stars){
            if((q.isEmpty()?!s.name.isEmpty():(s.title()+" "+s.constellation+" "+s.hip).toLowerCase(Locale.ROOT).contains(q))){matches.add(s);adapter.add(s.title()+" · "+s.constellation+String.format(Locale.US," · %+.0f°",s.altitude));if(matches.size()==120)break;}}
            adapter.notifyDataSetChanged();};
        query.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){filter.run();}public void afterTextChanged(Editable e){}});filter.run();
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Find a star").setView(body).setNegativeButton("Close",null).create();
        list.setOnItemClickListener((p,v,i,id)->{Catalog.Star s=matches.get(i);sky.focus(s);select(s);dialog.dismiss();if(s.altitude<0)toast("Below the horizon at the selected time and location.");});dialog.show();
    }
    private void location(){openLocationMap();}
    private void fillInitialCoordinates(){
        if(initialLat==null)return;
        changingInitial=true;initialLat.setText(Double.toString(latitude));initialLon.setText(Double.toString(longitude));changingInitial=false;updateInitialZone();
    }
    private void openLocationMap(){
        locationWanted=false;deviceLocation.stop();
        double lat=latitude,lon=longitude;
        if(!configured&&initialLat!=null){try{double a=Double.parseDouble(initialLat.getText().toString().replace(',','.')),b=Double.parseDouble(initialLon.getText().toString().replace(',','.'));if(Double.isFinite(a)&&Double.isFinite(b)&&Math.abs(a)<=90&&Math.abs(b)<=180){lat=a;lon=b;}}catch(NumberFormatException ignored){}}
        startActivityForResult(new Intent(this,LocationPickerActivity.class).putExtra("latitude",lat).putExtra("longitude",lon).putExtra("place",locationName).putExtra("night",night),31);
    }
    @Override protected void onActivityResult(int code,int result,Intent data){
        super.onActivityResult(code,result,data);
        if(code!=31||result!=RESULT_OK||data==null)return;
        double lat=data.getDoubleExtra("latitude",Double.NaN),lon=data.getDoubleExtra("longitude",Double.NaN);
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)return;
        latitude=lat;longitude=lon;setObserverZone(latitude,longitude);locationName=data.getStringExtra("place");if(locationName==null)locationName="Selected on map";
        locationWanted=false;deviceLocation.stop();initialEdited=true;fillInitialCoordinates();save();
        if(configured)refresh();else if(initialLocationStatus!=null)initialLocationStatus.setText(locationName+". Ready to generate your sky.");
    }
    private void requestDefaultLocation(boolean explicit){
        if(configured||initialLat==null)return;
        if(explicit)initialEdited=false;
        if(initialEdited)return;
        locationWanted=true;defaultAttempted=true;
        if(DeviceLocation.hasPermission(this)){deviceLocation.start();return;}
        if(explicit||!getPreferences(MODE_PRIVATE).getBoolean("locationPermissionAsked",false)){
            getPreferences(MODE_PRIVATE).edit().putBoolean("locationPermissionAsked",true).apply();
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},12);
        }else{locationWanted=false;initialLocationStatus.setText("Choose on map, enter coordinates, or tap My location to allow location access.");}
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==12&&locationWanted&&!configured&&!initialEdited){
            if(DeviceLocation.hasPermission(this)){if(live)deviceLocation.start();}
            else{locationWanted=false;initialLocationStatus.setText("Location permission declined. Choose a map point or enter coordinates.");}
        }
    }
    private void chooseDate(){
        ZonedDateTime current=Instant.ofEpochMilli(time()).atZone(observerZone);
        DatePickerDialog dialog=new DatePickerDialog(this,(p,y,m,d)->new TimePickerDialog(this,(v,h,min)->{
            Instant selectedInstant=ZonedDateTime.ofLocal(LocalDate.of(y,m+1,d).atTime(h,min),observerZone,current.getOffset()).toInstant();
            int utcYear=selectedInstant.atZone(ZoneOffset.UTC).getYear();if(utcYear<2000||utcYear>2050){toast("Choose a date from 2000 to 2050.");return;}chosenTime=selectedInstant.toEpochMilli();refresh();
        },current.getHour(),current.getMinute(),true).show(),current.getYear(),current.getMonthValue()-1,current.getDayOfMonth());
        dialog.setTitle("Date · "+observerZone.getId());dialog.getDatePicker().setMinDate(Instant.parse("2000-01-02T00:00:00Z").toEpochMilli());dialog.getDatePicker().setMaxDate(Instant.parse("2050-12-30T00:00:00Z").toEpochMilli());dialog.show();
    }
    private void options(){new AlertDialog.Builder(this).setTitle("Your sky, your way").setItems(new String[]{night?"Turn off red night mode":"Red night mode",sky.showLines?"Hide constellation lines":"Show constellation lines",sky.showLabels?"Hide star names":"Show star names","Star density","Reset map","About & open-source licenses"},(d,i)->{
        switch(i){case 0:night=!night;applyColors();break;case 1:sky.showLines=!sky.showLines;break;case 2:sky.showLabels=!sky.showLabels;break;case 3:density();break;case 4:sky.reset();break;case 5:about();break;}save();sky.invalidate();}).show();}
    private void density(){new AlertDialog.Builder(this).setTitle("Faintest stars to display").setSingleChoiceItems(new String[]{"City · magnitude 3","Suburbs · magnitude 4.5","Dark sky · magnitude 5.5","All catalogue stars · magnitude 6.5"},sky.magnitudeLimit<4?0:sky.magnitudeLimit<5?1:sky.magnitudeLimit<6?2:3,(d,i)->{sky.magnitudeLimit=new double[]{3,4.5,5.5,6.5}[i];save();sky.invalidate();d.dismiss();}).setNegativeButton("Cancel",null).show();}
    private void about(){AboutOpenStar.show(this,catalog==null?0:catalog.stars.size());}
    private void save(){if(sky==null)return;SkyPreferences.get(this).edit().putLong("lat",Double.doubleToLongBits(latitude)).putLong("lon",Double.doubleToLongBits(longitude)).putString("place",locationName).putBoolean("night",night).putBoolean("lines",sky.showLines).putBoolean("labels",sky.showLabels).putFloat("magnitude",(float)sky.magnitudeLimit).apply();}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    @Override protected void onResume(){super.onResume();if(sky!=null){android.content.SharedPreferences prefs=SkyPreferences.get(this);night=prefs.getBoolean("night",night);sky.showLines=prefs.getBoolean("lines",sky.showLines);sky.showLabels=prefs.getBoolean("labels",sky.showLabels);sky.magnitudeLimit=prefs.getFloat("magnitude",(float)sky.magnitudeLimit);applyColors();}live=true;ticker.run();if(!configured&&!initialEdited&&initialLat!=null){if(locationWanted&&DeviceLocation.hasPermission(this))deviceLocation.start();else if(!defaultAttempted)requestDefaultLocation(false);}}
    @Override protected void onPause(){super.onPause();live=false;handler.removeCallbacks(ticker);if(deviceLocation!=null)deviceLocation.stop();save();}
    @Override protected void onStop(){super.onStop();if(deviceLocation!=null)deviceLocation.stop();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putBoolean("initialTimeEdited",initialTimeEdited);state.putLong("time",chosenTime);state.putBoolean("configured",configured);state.putBoolean("initialEdited",initialEdited);state.putBoolean("defaultAttempted",defaultAttempted);state.putBoolean("locationWanted",locationWanted);if(initialMoment!=null){state.putString("inputTime",initialMoment.getText().toString());state.putString("inputLat",initialLat.getText().toString());state.putString("inputLon",initialLon.getText().toString());}if(sky!=null){sky.saveState(state);if(sky.selected!=null)state.putInt("selected",sky.selected.hip);}}
    @Override protected void onDestroy(){if(deviceLocation!=null)deviceLocation.stop();calculationGeneration++;worker.shutdownNow();super.onDestroy();}
}

