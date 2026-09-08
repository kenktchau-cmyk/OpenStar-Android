package org.openstar.map;

import android.Manifest;
import android.app.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.*;
import android.util.Size;
import android.util.SizeF;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/** Camera and compass sky overlay. Does not photograph, record, or upload camera frames. */
public final class ArActivity extends Activity implements SensorEventListener, SurfaceHolder.Callback {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService calculator=Executors.newSingleThreadExecutor();
    private SensorManager sensors;
    private Sensor rotationSensor, accelerometer, magnetometer;
    private final float[] rotation=new float[9], displayRotation=new float[9];
    private float[] gravity, magnetic;
    private boolean resumed, oriented, surfaceReady, opening, ready, calculating, cameraEnabled;
    private int cameraGeneration, accuracy=SensorManager.SENSOR_STATUS_UNRELIABLE;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private SurfaceView preview;
    private Overlay overlay;
    private FrameLayout frame;
    private LinearLayout controls;
    private TextView state, when, selected;
    private Switch cameraToggle;
    private Button optionsButton;
    private Catalog catalog;
    private CameraCharacteristics characteristics;
    private Size previewSize;
    private String cameraId, cameraProblem, dataProblem;
    private double latitude, longitude, declination, headingOffset, magnitudeLimit;
    private double focalPixels;
    private long chosenTime, lastStatus;
    private ZoneId observerZone;
    private String observerZoneProblem;
    private boolean showLines, showLabels, night;
    private Catalog.Star selectedStar;
    private final Runnable refreshSky=new Runnable(){public void run(){
        if(!resumed)return;
        if(chosenTime==0)calculate();
        main.postDelayed(this,30000);
    }};

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        latitude=getIntent().getDoubleExtra("latitude",0);longitude=getIntent().getDoubleExtra("longitude",0);
        String zone=getIntent().getStringExtra("zone");observerZoneProblem=getIntent().getStringExtra("zoneProblem");
        try{observerZone=zone==null?ObserverTimeZone.resolve(this,latitude,longitude):ZoneId.of(zone);}
        catch(IllegalArgumentException|IllegalStateException|java.time.DateTimeException error){observerZone=ZoneOffset.UTC;observerZoneProblem="Location time zone unavailable; using UTC";}
        chosenTime=saved==null?getIntent().getLongExtra("time",0):saved.getLong("time");
        headingOffset=saved==null?0:saved.getDouble("offset");
        // A fresh AR visit starts without the camera. Rotation keeps the user's choice.
        cameraEnabled=saved!=null&&saved.getBoolean("cameraEnabled")&&
            checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED;
        android.content.SharedPreferences prefs=SkyPreferences.get(this);
        magnitudeLimit=prefs.getFloat("magnitude",(float)getIntent().getDoubleExtra("magnitude",5.5));
        showLines=prefs.getBoolean("lines",getIntent().getBooleanExtra("lines",true));showLabels=prefs.getBoolean("labels",getIntent().getBooleanExtra("labels",true));
        night=prefs.getBoolean("night",getIntent().getBooleanExtra("night",false));
        declination=new GeomagneticField((float)latitude,(float)longitude,0,System.currentTimeMillis()).getDeclination();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        frame=new FrameLayout(this);frame.setBackgroundColor(Color.BLACK);setContentView(frame);
        preview=new SurfaceView(this);preview.setVisibility(View.INVISIBLE);frame.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        preview.getHolder().addCallback(this);
        overlay=new Overlay();frame.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(16),dp(12),dp(16),dp(10));controls.setBackgroundColor(0xBB07131F);
        frame.addView(controls,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);controls.addView(actions);
        cameraToggle=new Switch(this);cameraToggle.setTextSize(14);cameraToggle.setTextColor(night?0xFFFF796B:0xFFDDF5EF);
        cameraToggle.setMinHeight(dp(48));cameraToggle.setChecked(cameraEnabled);actions.addView(cameraToggle,new LinearLayout.LayoutParams(0,dp(48),1));
        cameraToggle.setOnCheckedChangeListener((button,enabled)->{if(enabled!=cameraEnabled)setCameraEnabled(enabled);});
        optionsButton=new Button(this);optionsButton.setText("•••");optionsButton.setAllCaps(false);optionsButton.setMinWidth(0);optionsButton.setMinimumWidth(0);
        optionsButton.setContentDescription("AR options and licenses");optionsButton.setOnClickListener(v->options());
        LinearLayout.LayoutParams menuParams=new LinearLayout.LayoutParams(dp(52),dp(48));menuParams.leftMargin=dp(12);actions.addView(optionsButton,menuParams);
        // Keep status available on demand without placing it over the sky.
        when=text(12);state=text(12);
        selected=text(13);selected.setPadding(dp(16),dp(12),dp(16),dp(12));selected.setBackgroundColor(0xBB07131F);
        selected.setVisibility(View.GONE);
        frame.addView(selected,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        frame.setOnApplyWindowInsetsListener((view,insets)->{
            int top=insets.getSystemWindowInsetTop(),bottom=insets.getSystemWindowInsetBottom();
            FrameLayout.LayoutParams a=(FrameLayout.LayoutParams)controls.getLayoutParams();a.topMargin=top;a.leftMargin=insets.getSystemWindowInsetLeft();a.rightMargin=insets.getSystemWindowInsetRight();controls.setLayoutParams(a);
            FrameLayout.LayoutParams b=(FrameLayout.LayoutParams)selected.getLayoutParams();b.bottomMargin=bottom;b.leftMargin=a.leftMargin;b.rightMargin=a.rightMargin;selected.setLayoutParams(b);
            return insets;
        });
        frame.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot)configurePreviewGeometry();});
        sensors=(SensorManager)getSystemService(SENSOR_SERVICE);
        rotationSensor=sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if(rotationSensor==null)rotationSensor=sensors.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
        accelerometer=sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer=sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        try{catalog=new Catalog(this);}catch(Exception e){dataProblem="Star catalogue could not load: "+e.getMessage();}
        if(saved!=null&&catalog!=null&&saved.containsKey("selected"))for(Catalog.Star star:catalog.stars)if(star.hip==saved.getInt("selected"))selectedStar=star;
        applyColors();updateCameraControl();updateStatus();
        if(saved==null)Toast.makeText(this,"Point the back of your phone at the sky. Tap a star to identify it.",Toast.LENGTH_LONG).show();
    }

    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(int size){TextView v=new TextView(this);v.setTextColor(night?0xFFFF796B:0xFFDDF5EF);v.setTextSize(size);return v;}
    private void applyColors(){
        int ink=night?0xFFFF796B:0xFFDDF5EF, panel=night?0xDD1C0807:0xBB07131F;
        frame.setBackgroundColor(night?0xFF0C0303:Color.BLACK);controls.setBackgroundColor(panel);selected.setBackgroundColor(panel);
        for(TextView view:new TextView[]{cameraToggle,optionsButton,when,state,selected})view.setTextColor(ink);
        optionsButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(night?0xFF32120E:0xFF142E38));
        cameraToggle.setThumbTintList(android.content.res.ColorStateList.valueOf(ink));
        cameraToggle.setTrackTintList(android.content.res.ColorStateList.valueOf(night?0xFF823D31:0xFF537B78));
        overlay.invalidate();
    }

    private void options(){new AlertDialog.Builder(this).setTitle("Your sky, your way").setItems(new String[]{
        night?"Turn off red night mode":"Red night mode",showLines?"Hide constellation lines":"Show constellation lines",
        showLabels?"Hide star names":"Show star names","Star density","Reset alignment","About & open-source licenses",
        "Live now","Calibrate","AR details","Back to map"},(dialog,item)->{
            switch(item){
                case 0:night=!night;applyColors();saveDisplayOptions();break;
                case 1:showLines=!showLines;saveDisplayOptions();break;
                case 2:showLabels=!showLabels;saveDisplayOptions();break;
                case 3:density();break;
                case 4:headingOffset=0;selectedStar=null;updateSelection();updateStatus();break;
                case 5:AboutOpenStar.show(this,catalog==null?0:catalog.stars.size());break;
                case 6:chosenTime=0;calculate();break;
                case 7:calibrate();break;
                case 8:details();break;
                case 9:finish();break;
            }
            overlay.invalidate();
        }).show();}
    private void details(){updateStatus();new AlertDialog.Builder(this).setTitle("AR details")
        .setMessage(when.getText()+"\n\n"+state.getText()).setPositiveButton("Done",null).show();}
    private void density(){new AlertDialog.Builder(this).setTitle("Faintest stars to display").setSingleChoiceItems(
        new String[]{"City · magnitude 3","Suburbs · magnitude 4.5","Dark sky · magnitude 5.5","All catalogue stars · magnitude 6.5"},
        magnitudeLimit<4?0:magnitudeLimit<5?1:magnitudeLimit<6?2:3,(dialog,item)->{
            magnitudeLimit=new double[]{3,4.5,5.5,6.5}[item];saveDisplayOptions();overlay.invalidate();dialog.dismiss();
        }).setNegativeButton("Cancel",null).show();}
    private void saveDisplayOptions(){SkyPreferences.get(this).edit().putBoolean("night",night).putBoolean("lines",showLines).putBoolean("labels",showLabels).putFloat("magnitude",(float)magnitudeLimit).apply();}

    private void calculate(){
        if(catalog==null||calculating)return;
        calculating=true;dataProblem=null;ready=false;updateStatus();
        long requestedTime=chosenTime;
        long instant=requestedTime==0?System.currentTimeMillis():requestedTime;
        calculator.execute(()->{
            try{double[][] result=catalog.calculate(latitude,longitude,instant);main.post(()->{
                if(isDestroyed())return;
                calculating=false;if(chosenTime!=requestedTime){calculate();return;}
                catalog.apply(result);ready=true;updateSelection();updateStatus();overlay.invalidate();
            });}catch(Exception e){android.util.Log.e("OpenStar","AR sky calculation failed",e);main.post(()->{
                if(isDestroyed())return;
                calculating=false;dataProblem="Sky calculation failed. Return to Map for error details.";updateStatus();
            });}
        });
    }

    private void findCamera(){
        try{CameraManager manager=(CameraManager)getSystemService(CAMERA_SERVICE);
            for(String id:manager.getCameraIdList()){
                CameraCharacteristics c=manager.getCameraCharacteristics(id);
                if(Objects.equals(c.get(CameraCharacteristics.LENS_FACING),CameraCharacteristics.LENS_FACING_BACK)){
                    StreamConfigurationMap map=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    Size[] sizes=map==null?null:map.getOutputSizes(SurfaceHolder.class);
                    if(sizes==null||sizes.length==0)continue;
                    cameraId=id;characteristics=c;
                    previewSize=Arrays.stream(sizes).filter(s->s.getWidth()<=1920&&s.getHeight()<=1080)
                        .max(Comparator.comparingLong(s->(long)s.getWidth()*s.getHeight())).orElse(sizes[0]);
                    preview.getHolder().setFixedSize(previewSize.getWidth(),previewSize.getHeight());return;
                }
            }
            cameraProblem="No rear camera is available. Compass sky view still works.";
        }catch(CameraAccessException|SecurityException e){cameraProblem="Camera unavailable. Compass sky view still works.";}
    }

    private void updateCameraControl(){
        cameraToggle.setChecked(cameraEnabled);
        cameraToggle.setText(cameraEnabled?"Camera on":"Camera off");
        cameraToggle.setContentDescription("Camera on or off");
        overlay.invalidate();
    }
    private void setCameraEnabled(boolean enabled){
        cameraEnabled=enabled;cameraProblem=null;
        if(!enabled){closeCamera();preview.setVisibility(View.INVISIBLE);}
        else if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.CAMERA},20);
        }else startCameraPreview();
        updateCameraControl();updateStatus();
    }
    private void startCameraPreview(){
        if(!cameraEnabled)return;
        if(cameraId==null)findCamera();
        if(cameraId==null){failCamera(cameraProblem);return;}
        preview.setVisibility(View.VISIBLE);configurePreviewGeometry();openCamera();
    }

    private int displayDegrees(){return getWindowManager().getDefaultDisplay().getRotation()*90;}
    private void configurePreviewGeometry(){
        if(frame.getWidth()==0||frame.getHeight()==0)return;
        if(previewSize==null){
            // Camera-free pointing uses a 60-degree field of view along the long edge.
            focalPixels=Math.max(frame.getWidth(),frame.getHeight())/(2*Math.tan(Math.toRadians(30)));
            overlay.invalidate();return;
        }
        Integer orientation=characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        int relative=((orientation==null?90:orientation)+displayDegrees())%360;
        boolean swap=relative%180!=0;
        float bw=swap?previewSize.getHeight():previewSize.getWidth();
        float bh=swap?previewSize.getWidth():previewSize.getHeight();
        float scale=Math.max(frame.getWidth()/bw,frame.getHeight()/bh);
        // SurfaceView rotates the camera buffer; preserve its aspect ratio and crop centrally.
        preview.setScaleX(bw*scale/frame.getWidth());preview.setScaleY(bh*scale/frame.getHeight());
        SizeF physical=characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        float[] focal=characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        if(physical!=null&&focal!=null&&focal.length>0&&physical.getWidth()>0&&physical.getHeight()>0){
            double streamAspect=(double)previewSize.getWidth()/previewSize.getHeight();
            double sensorWidth=Math.min(physical.getWidth(),physical.getHeight()*streamAspect);
            focalPixels=focal[0]*previewSize.getWidth()/sensorWidth*scale;
        }else focalPixels=Math.max(frame.getWidth(),frame.getHeight())/(2*Math.tan(Math.toRadians(30)));
        overlay.invalidate();
    }

    private void openCamera(){
        if(!cameraEnabled||!resumed||!surfaceReady||opening||camera!=null||cameraId==null||
            checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return;
        opening=true;cameraProblem=null;int generation=++cameraGeneration;
        try{((CameraManager)getSystemService(CAMERA_SERVICE)).openCamera(cameraId,new CameraDevice.StateCallback(){
            @Override public void onOpened(CameraDevice device){
                if(!cameraEnabled||!resumed||generation!=cameraGeneration){device.close();return;}
                opening=false;camera=device;configurePreviewGeometry();
                try{Surface surface=preview.getHolder().getSurface();
                    CaptureRequest.Builder request=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    request.addTarget(surface);request.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
                    // Disable crop-changing stabilization so the overlay uses the same field of view.
                    request.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
                    request.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
                    device.createCaptureSession(Collections.singletonList(surface),new CameraCaptureSession.StateCallback(){
                        @Override public void onConfigured(CameraCaptureSession value){
                            if(!cameraEnabled||!resumed||generation!=cameraGeneration){value.close();return;}
                            session=value;
                            try{value.setRepeatingRequest(request.build(),null,main);updateStatus();}
                            catch(CameraAccessException|IllegalStateException e){failCamera("Camera preview could not start.");}
                        }
                        @Override public void onConfigureFailed(CameraCaptureSession value){if(generation==cameraGeneration)failCamera("Camera preview is unavailable. Return to Map and retry AR.");}
                    },main);
                }catch(CameraAccessException|IllegalArgumentException|IllegalStateException e){failCamera("Camera preview could not start. Return to Map and retry AR.");}
            }
            @Override public void onDisconnected(CameraDevice device){device.close();if(generation==cameraGeneration)failCamera("Camera disconnected. Return to Map and retry AR.");}
            @Override public void onError(CameraDevice device,int error){device.close();if(generation==cameraGeneration)failCamera("Camera unavailable ("+error+"). Return to Map and retry AR.");}
        },main);}catch(CameraAccessException|SecurityException|IllegalArgumentException e){failCamera("Camera access is unavailable. Return to Map and retry AR.");}
    }
    private void failCamera(String problem){cameraEnabled=false;closeCamera();preview.setVisibility(View.INVISIBLE);cameraProblem=problem;updateCameraControl();updateStatus();}
    private void closeCamera(){cameraGeneration++;opening=false;if(session!=null){session.close();session=null;}if(camera!=null){camera.close();camera=null;}}
    @Override public void surfaceCreated(SurfaceHolder holder){surfaceReady=true;openCamera();}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){configurePreviewGeometry();openCamera();}
    @Override public void surfaceDestroyed(SurfaceHolder holder){surfaceReady=false;closeCamera();}
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==20&&cameraEnabled){
            if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCameraPreview();
            else failCamera("Camera permission was declined. Compass sky view still works.");
            updateCameraControl();updateStatus();
        }
    }

    @Override protected void onResume(){super.onResume();resumed=true;oriented=false;
        if(rotationSensor!=null)sensors.registerListener(this,rotationSensor,SensorManager.SENSOR_DELAY_GAME);
        else if(accelerometer!=null&&magnetometer!=null){sensors.registerListener(this,accelerometer,SensorManager.SENSOR_DELAY_GAME);sensors.registerListener(this,magnetometer,SensorManager.SENSOR_DELAY_GAME);}
        if(cameraEnabled){
            if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCameraPreview();
            else setCameraEnabled(false);
        }
        calculate();main.postDelayed(refreshSky,30000);
    }
    @Override protected void onPause(){resumed=false;main.removeCallbacks(refreshSky);sensors.unregisterListener(this);closeCamera();super.onPause();}
    @Override protected void onDestroy(){calculator.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putLong("time",chosenTime);state.putDouble("offset",headingOffset);state.putBoolean("cameraEnabled",cameraEnabled);if(selectedStar!=null)state.putInt("selected",selectedStar.hip);super.onSaveInstanceState(state);}
    @Override public void onAccuracyChanged(Sensor sensor,int value){if(sensor==rotationSensor||sensor.getType()==Sensor.TYPE_MAGNETIC_FIELD){accuracy=value;updateStatus();}}
    @Override public void onSensorChanged(SensorEvent event){
        if(event.sensor==rotationSensor){accuracy=event.accuracy;SensorManager.getRotationMatrixFromVector(rotation,event.values);}
        else{
            if(event.sensor.getType()==Sensor.TYPE_ACCELEROMETER)gravity=filtered(gravity,event.values);
            if(event.sensor.getType()==Sensor.TYPE_MAGNETIC_FIELD)magnetic=filtered(magnetic,event.values);
            if(gravity==null||magnetic==null||!SensorManager.getRotationMatrix(rotation,null,gravity,magnetic))return;
        }
        int display=getWindowManager().getDefaultDisplay().getRotation();
        int x=SensorManager.AXIS_X,y=SensorManager.AXIS_Y;
        if(display==Surface.ROTATION_90){x=SensorManager.AXIS_Y;y=SensorManager.AXIS_MINUS_X;}
        else if(display==Surface.ROTATION_180){x=SensorManager.AXIS_MINUS_X;y=SensorManager.AXIS_MINUS_Y;}
        else if(display==Surface.ROTATION_270){x=SensorManager.AXIS_MINUS_Y;y=SensorManager.AXIS_X;}
        SensorManager.remapCoordinateSystem(rotation,x,y,displayRotation);oriented=true;overlay.postInvalidateOnAnimation();
        if(SystemClock.uptimeMillis()-lastStatus>250){lastStatus=SystemClock.uptimeMillis();updateStatus();}
    }
    private static float[] filtered(float[] prior,float[] next){if(prior==null)return next.clone();for(int i=0;i<3;i++)prior[i]+=.15f*(next[i]-prior[i]);return prior;}

    private void updateStatus(){
        if(state==null)return;
        long instant=chosenTime==0?System.currentTimeMillis():chosenTime;
        when.setText((chosenTime==0?"LIVE AR · ":"TIME PREVIEW · ")+DateTimeFormatter.ofPattern("d MMM yyyy HH:mm z").withZone(observerZone).format(Instant.ofEpochMilli(instant))+" · "+(observerZoneProblem==null?observerZone.getId():observerZoneProblem)+
            String.format(Locale.US,"\n%.4f°, %.4f° · selected observer location",latitude,longitude));
        String message;
        if(dataProblem!=null)message=dataProblem;
        else if(rotationSensor==null&&(accelerometer==null||magnetometer==null))message="This device has no compass orientation sensor. The regular Map still works.";
        else if(!oriented)message="Waiting for compass orientation…";
        else if(!ready)message="Calculating the sky with Skyfield…";
        else{
            double az=(Math.toDegrees(Math.atan2(-displayRotation[2],-displayRotation[5]))+declination+headingOffset+360)%360;
            double alt=Math.toDegrees(Math.asin(Math.max(-1,Math.min(1,-displayRotation[8]))));
            message=String.format(Locale.US,"Facing %.0f° true · altitude %+.0f°",az,alt);
            if(accuracy<=SensorManager.SENSOR_STATUS_ACCURACY_LOW)message+="\nCompass accuracy low · move phone in a figure eight.";
            else message+="\nCompass alignment is approximate · Calibrate if labels drift.";
        }
        String mode=cameraEnabled?(session==null?"Starting camera…":"Camera on · live background"):"Camera off · compass sky view";
        state.setText(mode+"\n"+message+(cameraProblem==null?"":"\n"+cameraProblem));
    }

    private void calibrate(){
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(8),dp(20),dp(8));
        TextView help=text(14);help.setText("Move the phone in a figure eight, away from magnets or metal. Point at a known bright star. If needed, adjust the heading below until its marker lines up.\n\nThis is a compass overlay; it does not detect stars in the camera image.");body.addView(help);
        TextView value=text(14);body.addView(value);SeekBar slider=new SeekBar(this);slider.setMax(120);slider.setProgress((int)Math.round(headingOffset*2)+60);body.addView(slider);
        Runnable update=()->value.setText(String.format(Locale.US,"Heading adjustment: %+.1f°",headingOffset));update.run();
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int p,boolean user){headingOffset=(p-60)/2.0;update.run();overlay.invalidate();updateStatus();}});
        new AlertDialog.Builder(this).setTitle("Align the sky overlay").setView(body).setPositiveButton("Done",null).setNeutralButton("Reset",(d,w)->{headingOffset=0;overlay.invalidate();updateStatus();}).show();
    }

    private void updateSelection(){
        if(selectedStar==null){selected.setVisibility(View.GONE);return;}
        selected.setVisibility(View.VISIBLE);
        Catalog.Star s=selectedStar;
        selected.setText(s.title()+" · "+s.constellation+String.format(Locale.US,
            "\nAltitude %.1f° · azimuth %.1f° · mag %.1f%s",s.altitude,s.azimuth,s.magnitude,
            s.altitude<0?" · below horizon":""));
    }

    private final class Overlay extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Map<Catalog.Star,PointF> points=new HashMap<>();
        Overlay(){super(ArActivity.this);setClickable(true);setContentDescription("Pointing star view, with an optional camera background. Point the phone's back at the sky; tap a star marker. Return to Map and Find star for a searchable list.");}
        private PointF position(Catalog.Star s){double[] p=ArProjection.project(s.altitude,s.azimuth,declination+headingOffset,displayRotation,focalPixels,getWidth(),getHeight());return p==null?null:new PointF((float)p[0],(float)p[1]);}
        @Override protected void onDraw(Canvas canvas){
            points.clear();if(!ready||!oriented||focalPixels<=0)return;
            int ink=night?0xFFFF796B:0xFFC5FFF0;
            paint.setColor(ink);paint.setStrokeWidth(dp(1));paint.setAlpha(100);
            float cx=getWidth()/2f,cy=getHeight()/2f;
            canvas.drawLine(cx-dp(9),cy,cx+dp(9),cy,paint);canvas.drawLine(cx,cy-dp(9),cx,cy+dp(9),paint);
            if(showLines)for(Catalog.Star[] line:catalog.lines){
                if(line[0].altitude<0||line[1].altitude<0)continue;
                PointF a=position(line[0]),b=position(line[1]);
                if(a!=null&&b!=null&&Math.abs(a.x)<getWidth()*4&&Math.abs(b.x)<getWidth()*4&&Math.abs(a.y)<getHeight()*4&&Math.abs(b.y)<getHeight()*4)canvas.drawLine(a.x,a.y,b.x,b.y,paint);
            }
            List<RectF> labels=new ArrayList<>();
            for(Catalog.Star star:catalog.stars){
                if(star.altitude<0||(star.magnitude>magnitudeLimit&&star!=selectedStar))continue;
                PointF p=position(star);if(p==null||p.x<0||p.y<0||p.x>getWidth()||p.y>getHeight())continue;
                points.put(star,p);paint.setColor(ink);paint.setAlpha(255);
                paint.setShadowLayer(dp(3),0,0,Color.BLACK);canvas.drawCircle(p.x,p.y,dp((float)Math.max(1,3-star.magnitude*.35)),paint);paint.clearShadowLayer();
                if(star==selectedStar){paint.setStyle(Paint.Style.STROKE);canvas.drawCircle(p.x,p.y,dp(12),paint);paint.setStyle(Paint.Style.FILL);}
                if((showLabels&&!star.name.isEmpty()&&star.magnitude<3)||star==selectedStar){
                    String label=star.title();paint.setTextSize(dp(12));float x=Math.max(dp(4),Math.min(getWidth()-paint.measureText(label)-dp(5),p.x+dp(9))),y=Math.max(dp(16),p.y-dp(9));
                    RectF rect=new RectF(x-dp(3),y+paint.ascent()-dp(2),x+paint.measureText(label)+dp(3),y+paint.descent()+dp(2));
                    boolean collision=false;for(RectF prior:labels)if(RectF.intersects(prior,rect)){collision=true;break;}
                    if(!collision||star==selectedStar){labels.add(rect);paint.setColor(night?0xCC1C0807:0xCC07131F);canvas.drawRoundRect(rect,dp(3),dp(3),paint);paint.setColor(ink);canvas.drawText(label,x,y,paint);}
                }
            }
        }
        @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()==MotionEvent.ACTION_UP){
            double distance=dp(28);Catalog.Star nearest=null;
            for(Map.Entry<Catalog.Star,PointF> entry:points.entrySet()){double d=Math.hypot(entry.getValue().x-e.getX(),entry.getValue().y-e.getY());if(d<distance){distance=d;nearest=entry.getKey();}}
            if(nearest!=null){selectedStar=nearest;updateSelection();invalidate();}performClick();
        }return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
