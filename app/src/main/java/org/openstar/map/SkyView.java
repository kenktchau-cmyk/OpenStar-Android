package org.openstar.map;

import android.content.Context;
import android.graphics.*;
import android.os.Build;
import android.os.Bundle;
import android.view.*;
import java.util.*;
import java.util.function.Consumer;

/** The AR sky projection driven by touch, with no camera or orientation sensors. */
public final class SkyView extends View {
    private final Catalog catalog;
    private final TouchSkyCamera camera=new TouchSkyCamera();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestures;
    private final Map<Catalog.Star,PointF> points=new HashMap<>();
    private final List<RectF> labels=new ArrayList<>();
    private float[] rotation;
    private double focalPixels;
    private boolean multitouch;
    public boolean night,showLines=true,showLabels=true;
    public double magnitudeLimit=5.5;
    public Catalog.Star selected;
    public Consumer<Catalog.Star> onSelected;

    public SkyView(Context context,Catalog catalog){
        super(context);this.catalog=catalog;setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        setClickable(true);setFocusable(true);
        setContentDescription("Touch sky view. Drag with one finger to look around, pinch with two fingers to zoom, double tap to reset. Tap a star or use Find star. Camera and phone motion are not used.");
        scaleDetector=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector detector){camera.scale(detector.getScaleFactor());viewChanged();return true;}
        });
        // Double tap has one meaning: reset. Zoom is a two-finger gesture.
        scaleDetector.setQuickScaleEnabled(false);scaleDetector.setStylusScaleEnabled(false);
        gestures=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent event){return true;}
            @Override public boolean onScroll(MotionEvent a,MotionEvent b,float dx,float dy){
                if(!multitouch&&!scaleDetector.isInProgress()){camera.drag(dx,dy,getWidth(),getHeight());viewChanged();}return true;
            }
            @Override public boolean onDoubleTap(MotionEvent event){if(!multitouch)reset();return true;}
            @Override public boolean onSingleTapConfirmed(MotionEvent event){
                if(multitouch)return true;
                Catalog.Star closest=null;double distance=dp(24);
                for(Map.Entry<Catalog.Star,PointF> entry:points.entrySet()){
                    PointF p=entry.getValue();double d=Math.hypot(p.x-event.getX(),p.y-event.getY());
                    if(d<distance){distance=d;closest=entry.getKey();}
                }
                if(closest!=null){selected=closest;performClick();}return true;
            }
        });
        updateAccessibility();
    }
    private float dp(float value){return value*getResources().getDisplayMetrics().density;}
    private int ink(){return night?0xFFFF796B:0xFFC5FFF0;}
    public void reset(){camera.reset();viewChanged();}
    public void focus(Catalog.Star star){selected=star;camera.setView(star.azimuth,Math.max(0,star.altitude),Math.max(1,camera.zoom()));viewChanged();}
    public void saveState(Bundle state){state.putDouble("skyAzimuth",camera.azimuth());state.putDouble("skyAltitude",camera.altitude());state.putDouble("skyZoom",camera.zoom());}
    public void restoreState(Bundle state){if(state!=null&&state.containsKey("skyAzimuth")){camera.setView(state.getDouble("skyAzimuth"),state.getDouble("skyAltitude"),state.getDouble("skyZoom",1));viewChanged();}}
    private void viewChanged(){updateAccessibility();postInvalidateOnAnimation();}
    private void updateAccessibility(){
        if(Build.VERSION.SDK_INT>=30)setStateDescription(String.format(Locale.US,"Facing %s %.1f degrees; altitude %.1f degrees; zoom %.2f",direction(),camera.azimuth(),camera.altitude(),camera.zoom()));
    }
    private String direction(){return new String[]{"N","NE","E","SE","S","SW","W","NW"}[(int)Math.floor((camera.azimuth()+22.5)/45)%8];}
    private PointF position(double altitude,double azimuth){
        double[] p=ArProjection.project(altitude,azimuth,0,rotation,focalPixels,getWidth(),getHeight());
        return p==null?null:new PointF((float)p[0],(float)p[1]);
    }
    private PointF position(Catalog.Star star){return position(star.altitude,star.azimuth);}

    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);points.clear();labels.clear();
        canvas.drawColor(night?0xFF0C0303:0xFF07131F);
        if(getWidth()==0||getHeight()==0)return;
        rotation=camera.rotation();focalPixels=camera.focalPixels(getWidth(),getHeight());
        float cx=getWidth()/2f,cy=getHeight()/2f;
        float horizon=cy+(float)(focalPixels*Math.tan(Math.toRadians(camera.altitude())));
        paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);paint.setColor(night?0xFF1C0806:0xFF0F292F);
        if(horizon<getHeight())canvas.drawRect(0,Math.max(0,horizon),getWidth(),getHeight(),paint);
        if(horizon>=0&&horizon<=getHeight()){
            paint.setColor(ink());paint.setAlpha(90);paint.setStrokeWidth(dp(1));canvas.drawLine(0,horizon,getWidth(),horizon,paint);
            paint.setTextSize(dp(11));
            for(int az=0;az<360;az+=45){PointF p=position(0,az);if(p!=null&&p.x>dp(18)&&p.x<getWidth()-dp(18))centered(canvas,new String[]{"N","NE","E","SE","S","SW","W","NW"}[az/45],p.x,Math.min(getHeight()-dp(40),p.y+dp(17)));}
        }
        if(showLines){
            paint.setColor(ink());paint.setAlpha(75);paint.setStrokeWidth(dp(1));
            for(Catalog.Star[] line:catalog.lines){
                if(line[0].altitude<0||line[1].altitude<0)continue;
                PointF a=position(line[0]),b=position(line[1]);
                if(a!=null&&b!=null&&Math.abs(a.x)<getWidth()*4&&Math.abs(b.x)<getWidth()*4&&Math.abs(a.y)<getHeight()*4&&Math.abs(b.y)<getHeight()*4)canvas.drawLine(a.x,a.y,b.x,b.y,paint);
            }
        }
        for(Catalog.Star star:catalog.stars){
            if(star.altitude<0||(star.magnitude>magnitudeLimit&&star!=selected))continue;
            PointF p=position(star);if(p==null||p.x<0||p.x>getWidth()||p.y<0||p.y>getHeight())continue;
            points.put(star,p);float size=dp((float)Math.max(.8,3-star.magnitude*.35));
            if(star.magnitude<2.5){paint.setColor(ink());paint.setAlpha(24);canvas.drawCircle(p.x,p.y,size*3,paint);}
            paint.setColor(night?ink():0xFFE0EEF9);paint.setAlpha(star.magnitude<3?255:175);canvas.drawCircle(p.x,p.y,size,paint);
            if(star==selected){paint.setColor(ink());paint.setAlpha(255);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.5f));canvas.drawCircle(p.x,p.y,dp(12),paint);paint.setStyle(Paint.Style.FILL);}
        }
        // Name the selected star first so it wins label collisions.
        if(selected!=null&&points.containsKey(selected))drawLabel(canvas,selected,true);
        if(showLabels)for(Catalog.Star star:catalog.stars)if(star!=selected&&!star.name.isEmpty()&&star.magnitude<(camera.zoom()>2?4:3)&&points.containsKey(star))drawLabel(canvas,star,false);
        paint.setColor(ink());paint.setAlpha(90);paint.setStrokeWidth(dp(1));
        canvas.drawLine(cx-dp(8),cy,cx+dp(8),cy,paint);canvas.drawLine(cx,cy-dp(8),cx,cy+dp(8),paint);
        paint.setTextSize(dp(11));paint.setAlpha(220);
        String heading=String.format(Locale.US,"%s  %03.0f°  ·  %+.0f° altitude",direction(),camera.azimuth(),camera.altitude());
        pill(canvas,heading,dp(12),dp(22));
        paint.setTextSize(dp(10));
        String detail=String.format(Locale.US,"%.1f×  ·  %d stars in view",camera.zoom(),points.size());
        if(horizon<0)detail="Below horizon · drag down to look up";
        pill(canvas,detail,dp(12),getHeight()-dp(14));paint.setAlpha(255);
    }
    private void pill(Canvas canvas,String text,float x,float y){
        paint.setColor(night?0xFF0C0303:0xFF07131F);paint.setAlpha(225);
        canvas.drawRoundRect(x-dp(5),y+paint.ascent()-dp(4),x+paint.measureText(text)+dp(5),y+paint.descent()+dp(4),dp(4),dp(4),paint);
        paint.setColor(ink());paint.setAlpha(220);canvas.drawText(text,x,y,paint);
    }
    private void drawLabel(Canvas canvas,Catalog.Star star,boolean forced){
        PointF p=points.get(star);paint.setTextSize(dp(12));String name=star.title();
        float x=Math.max(dp(4),Math.min(getWidth()-paint.measureText(name)-dp(4),p.x+dp(9))),y=Math.max(dp(45),Math.min(getHeight()-dp(36),p.y-dp(9)));
        RectF rect=new RectF(x-dp(3),y+paint.ascent()-dp(2),x+paint.measureText(name)+dp(3),y+paint.descent()+dp(2));
        if(!forced)for(RectF prior:labels)if(RectF.intersects(prior,rect))return;
        labels.add(rect);paint.setColor(night?0xFF0C0303:0xFF07131F);paint.setAlpha(220);canvas.drawRoundRect(rect,dp(3),dp(3),paint);
        paint.setColor(ink());paint.setAlpha(255);canvas.drawText(name,x,y,paint);
    }
    private void centered(Canvas canvas,String text,float x,float y){canvas.drawText(text,x-paint.measureText(text)/2,y,paint);}
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN)multitouch=false;
        if(event.getPointerCount()>1)multitouch=true;
        scaleDetector.onTouchEvent(event);gestures.onTouchEvent(event);return true;
    }
    @Override public boolean performClick(){super.performClick();if(onSelected!=null&&selected!=null)onSelected.accept(selected);invalidate();return true;}
}
