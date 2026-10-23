package dev.luma.tv;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Vector glyphs for the Home widgets, drawn on a 24-unit grid in the style of SceneIcon: weather, transport,
 *  connectivity and settings. Drawn once per state change; no bitmaps. */
final class Glyph extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path(),cloud=new Path();
    private final RectF r=new RectF();
    private String kind="sun";
    Glyph(Context context){super(context);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);}
    Glyph kind(String value){if(value==null)value="sun";if(!value.equals(kind)){kind=value;invalidate();}return this;}
    Glyph color(int color){if(paint.getColor()!=color){paint.setColor(color);invalidate();}return this;}
    @Override public boolean hasOverlappingRendering(){return false;}

    /** WMO weather code (Open-Meteo) to a glyph. */
    static String weather(int code,boolean day){
        if(code==0)return day?"sun":"moon";
        if(code<=2)return day?"partly":"partly-night";
        if(code==3)return "cloud";
        if(code==45||code==48)return "fog";
        if(code>=51&&code<=57)return "drizzle";
        if(code>=71&&code<=77||code==85||code==86)return "snow";
        if(code>=95)return "storm";
        return "rain";
    }
    static String weatherName(int code){
        if(code==0)return "Clear";if(code==1)return "Mostly clear";if(code==2)return "Partly cloudy";if(code==3)return "Cloudy";
        if(code==45||code==48)return "Fog";if(code>=51&&code<=57)return "Drizzle";if(code>=61&&code<=65)return "Rain";if(code==66||code==67)return "Freezing rain";
        if(code>=71&&code<=77)return "Snow";if(code>=80&&code<=82)return "Showers";if(code==85||code==86)return "Snow showers";if(code>=95)return "Thunderstorm";
        return "—";
    }

    /** A cloud outline: two puffs over a rounded base, centred at (x, y), k = scale of the 24-unit grid. */
    private void cloudPath(float x,float y,float u){
        cloud.reset();Path puff=new Path();
        r.set(x-9*u,y-1*u,x+9*u,y+5.5f*u);cloud.addRoundRect(r,3.2f*u,3.2f*u,Path.Direction.CW);
        puff.addCircle(x-3.6f*u,y-0.8f*u,4.4f*u,Path.Direction.CW);cloud.op(puff,Path.Op.UNION);
        puff.reset();puff.addCircle(x+2.8f*u,y-2.6f*u,5.4f*u,Path.Direction.CW);cloud.op(puff,Path.Op.UNION);
    }
    private void sun(Canvas c,float x,float y,float u,float k){
        c.drawCircle(x,y,4.2f*u*k,paint);
        for(int i=0;i<8;i++){double a=i*Math.PI/4;float dx=(float)Math.cos(a),dy=(float)Math.sin(a);c.drawLine(x+dx*7.2f*u*k,y+dy*7.2f*u*k,x+dx*9.6f*u*k,y+dy*9.6f*u*k,paint);}
    }
    private void moon(Canvas c,float x,float y,float u,float k){
        path.reset();path.addCircle(x-0.5f*u*k,y+0.5f*u*k,8*u*k,Path.Direction.CW);
        Path cut=new Path();cut.addCircle(x+4.2f*u*k,y-3.6f*u*k,6.6f*u*k,Path.Direction.CW);path.op(cut,Path.Op.DIFFERENCE);c.drawPath(path,paint);
    }
    private void triangle(Canvas c,float x,float y,float h,boolean right){
        path.reset();float d=right?1:-1;path.moveTo(x-d*h*.45f,y-h*.55f);path.lineTo(x+d*h*.55f,y);path.lineTo(x-d*h*.45f,y+h*.55f);path.close();c.drawPath(path,paint);
    }

    @Override protected void onDraw(Canvas c){
        float s=Math.min(getWidth(),getHeight()),cx=getWidth()/2f,cy=getHeight()/2f,u=s/24f;
        paint.setStrokeWidth(1.7f*u);paint.setStyle(Paint.Style.STROKE);path.reset();
        switch(kind){
            case "sun":sun(c,cx,cy,u,1);break;
            case "moon":moon(c,cx,cy,u,1);break;
            case "partly":case "partly-night":{
                cloudPath(cx+1.6f*u,cy+3.2f*u,u*.82f);
                c.save();c.clipOutPath(cloud);
                if(kind.equals("partly"))sun(c,cx-3.6f*u,cy-3.4f*u,u,.72f);else moon(c,cx-3.4f*u,cy-3.6f*u,u,.62f);
                c.restore();c.drawPath(cloud,paint);break;}
            case "cloud":cloudPath(cx,cy+1*u,u);c.drawPath(cloud,paint);break;
            case "fog":
                cloudPath(cx,cy-3*u,u*.8f);c.drawPath(cloud,paint);
                c.drawLine(cx-8*u,cy+6.5f*u,cx+6*u,cy+6.5f*u,paint);c.drawLine(cx-5*u,cy+9.5f*u,cx+8*u,cy+9.5f*u,paint);break;
            case "drizzle":case "rain":case "snow":case "storm":{
                cloudPath(cx,cy-3.2f*u,u*.85f);c.drawPath(cloud,paint);
                if(kind.equals("storm")){path.moveTo(cx+1*u,cy+3.4f*u);path.lineTo(cx-2*u,cy+7.6f*u);path.lineTo(cx+1.6f*u,cy+7.6f*u);path.lineTo(cx-1*u,cy+11.4f*u);c.drawPath(path,paint);break;}
                for(int i=-1;i<=1;i++){float x=cx+i*4.6f*u;
                    if(kind.equals("snow")){paint.setStyle(Paint.Style.FILL);c.drawCircle(x,cy+6.6f*u+(i==0?2.2f*u:0),1.2f*u,paint);paint.setStyle(Paint.Style.STROKE);}
                    else if(kind.equals("drizzle"))c.drawLine(x,cy+5.4f*u,x-0.8f*u,cy+7.4f*u,paint);
                    else c.drawLine(x+1*u,cy+4.6f*u,x-1.6f*u,cy+10*u,paint);}
                break;}
            case "wifi":case "offline":
                for(int i=1;i<=3;i++){float rad=i*3.4f*u;r.set(cx-rad,cy+5*u-rad,cx+rad,cy+5*u+rad);c.drawArc(r,-135,90,false,paint);}
                paint.setStyle(Paint.Style.FILL);c.drawCircle(cx,cy+5*u,1.5f*u,paint);paint.setStyle(Paint.Style.STROKE);
                if(kind.equals("offline"))c.drawLine(cx-8*u,cy-7*u,cx+8*u,cy+9*u,paint);break;
            case "ethernet":
                r.set(cx-6*u,cy-6*u,cx+6*u,cy+4*u);c.drawRoundRect(r,1.6f*u,1.6f*u,paint);
                c.drawLine(cx-2.5f*u,cy+4*u,cx-2.5f*u,cy+8*u,paint);c.drawLine(cx+2.5f*u,cy+4*u,cx+2.5f*u,cy+8*u,paint);
                c.drawLine(cx-3*u,cy-6*u,cx-3*u,cy-3*u,paint);c.drawLine(cx,cy-6*u,cx,cy-3*u,paint);c.drawLine(cx+3*u,cy-6*u,cx+3*u,cy-3*u,paint);break;
            case "tv":
                r.set(cx-10*u,cy-7*u,cx+10*u,cy+5.5f*u);c.drawRoundRect(r,2.2f*u,2.2f*u,paint);
                c.drawLine(cx-4.5f*u,cy+9*u,cx+4.5f*u,cy+9*u,paint);break;
            case "remote":
                r.set(cx-4.2f*u,cy-10*u,cx+4.2f*u,cy+10*u);c.drawRoundRect(r,3.2f*u,3.2f*u,paint);
                c.drawCircle(cx,cy-4.6f*u,2.2f*u,paint);
                paint.setStyle(Paint.Style.FILL);c.drawCircle(cx,cy+1.8f*u,1.1f*u,paint);c.drawCircle(cx,cy+5.6f*u,1.1f*u,paint);break;
            case "gear":
                c.drawCircle(cx,cy,3*u,paint);c.drawCircle(cx,cy,7*u,paint);
                paint.setStrokeWidth(2.6f*u);paint.setStrokeCap(Paint.Cap.BUTT);
                for(int i=0;i<8;i++){double a=i*Math.PI/4+Math.PI/8;float dx=(float)Math.cos(a),dy=(float)Math.sin(a);c.drawLine(cx+dx*7*u,cy+dy*7*u,cx+dx*9.6f*u,cy+dy*9.6f*u,paint);}
                paint.setStrokeCap(Paint.Cap.ROUND);break;
            case "home":
                path.moveTo(cx-9*u,cy-0.5f*u);path.lineTo(cx,cy-8.5f*u);path.lineTo(cx+9*u,cy-0.5f*u);
                path.moveTo(cx-6.5f*u,cy-2.5f*u);path.lineTo(cx-6.5f*u,cy+8*u);path.lineTo(cx+6.5f*u,cy+8*u);path.lineTo(cx+6.5f*u,cy-2.5f*u);
                c.drawPath(path,paint);break;
            case "note":
                c.drawLine(cx-3*u,cy+6*u,cx-3*u,cy-8*u,paint);c.drawLine(cx+7*u,cy+3.5f*u,cx+7*u,cy-10*u,paint);
                paint.setStrokeWidth(2.4f*u);c.drawLine(cx-3*u,cy-8*u,cx+7*u,cy-10.4f*u,paint);
                paint.setStyle(Paint.Style.FILL);c.drawCircle(cx-5.6f*u,cy+6.4f*u,3*u,paint);c.drawCircle(cx+4.4f*u,cy+3.9f*u,3*u,paint);break;
            case "pin":
                paint.setStyle(Paint.Style.FILL);path.moveTo(cx+8*u,cy-8*u);path.lineTo(cx+2*u,cy+9*u);path.lineTo(cx-0.6f*u,cy+0.6f*u);path.lineTo(cx-9*u,cy-2*u);path.close();c.drawPath(path,paint);break;
            case "play":paint.setStyle(Paint.Style.FILL);triangle(c,cx+1*u,cy,17*u,true);break;
            case "pause":paint.setStyle(Paint.Style.FILL);r.set(cx-6.5f*u,cy-8*u,cx-2.2f*u,cy+8*u);c.drawRoundRect(r,1.2f*u,1.2f*u,paint);r.set(cx+2.2f*u,cy-8*u,cx+6.5f*u,cy+8*u);c.drawRoundRect(r,1.2f*u,1.2f*u,paint);break;
            case "next":case "previous":{
                paint.setStyle(Paint.Style.FILL);boolean next=kind.equals("next");float d=next?1:-1;
                triangle(c,cx-d*3.6f*u,cy,12*u,next);triangle(c,cx+d*3.4f*u,cy,12*u,next);
                r.set(cx+d*8.4f*u-1.1f*u,cy-6.6f*u,cx+d*8.4f*u+1.1f*u,cy+6.6f*u);c.drawRoundRect(r,u,u,paint);break;}
            case "hdmi":
                path.moveTo(cx-10*u,cy-4.5f*u);path.lineTo(cx+10*u,cy-4.5f*u);path.lineTo(cx+10*u,cy+0.5f*u);path.lineTo(cx+6.5f*u,cy+4.5f*u);
                path.lineTo(cx-6.5f*u,cy+4.5f*u);path.lineTo(cx-10*u,cy+0.5f*u);path.close();c.drawPath(path,paint);
                c.drawLine(cx-5.5f*u,cy-1*u,cx+5.5f*u,cy-1*u,paint);break;
            case "antenna":
                c.drawLine(cx,cy-2*u,cx-7*u,cy-10*u,paint);c.drawLine(cx,cy-2*u,cx+7*u,cy-10*u,paint);
                r.set(cx-9*u,cy-2*u,cx+9*u,cy+9*u);c.drawRoundRect(r,2*u,2*u,paint);
                paint.setStyle(Paint.Style.FILL);c.drawCircle(cx+5*u,cy+3.5f*u,1.2f*u,paint);break;
            case "gamepad":
                path.moveTo(cx-6*u,cy-5*u);path.lineTo(cx+6*u,cy-5*u);path.cubicTo(cx+10*u,cy-5*u,cx+11*u,cy+2*u,cx+10.5f*u,cy+5*u);
                path.cubicTo(cx+10*u,cy+8*u,cx+7*u,cy+8.5f*u,cx+5*u,cy+5.5f*u);path.lineTo(cx+3.5f*u,cy+3.5f*u);path.lineTo(cx-3.5f*u,cy+3.5f*u);
                path.lineTo(cx-5*u,cy+5.5f*u);path.cubicTo(cx-7*u,cy+8.5f*u,cx-10*u,cy+8*u,cx-10.5f*u,cy+5*u);path.cubicTo(cx-11*u,cy+2*u,cx-10*u,cy-5*u,cx-6*u,cy-5*u);
                path.close();c.drawPath(path,paint);
                c.drawLine(cx-7*u,cy-0.5f*u,cx-3*u,cy-0.5f*u,paint);c.drawLine(cx-5*u,cy-2.5f*u,cx-5*u,cy+1.5f*u,paint);
                paint.setStyle(Paint.Style.FILL);c.drawCircle(cx+4.5f*u,cy-1.8f*u,1.1f*u,paint);c.drawCircle(cx+6.8f*u,cy+0.6f*u,1.1f*u,paint);break;
            case "box":
                r.set(cx-10*u,cy-3.5f*u,cx+10*u,cy+4.5f*u);c.drawRoundRect(r,2.4f*u,2.4f*u,paint);
                paint.setStyle(Paint.Style.FILL);c.drawCircle(cx-6*u,cy+0.5f*u,1.1f*u,paint);break;
            case "switch":
                r.set(cx-10.5f*u,cy-7*u,cx-5.5f*u,cy+7*u);c.drawRoundRect(r,2.5f*u,2.5f*u,paint);
                r.set(cx+5.5f*u,cy-7*u,cx+10.5f*u,cy+7*u);c.drawRoundRect(r,2.5f*u,2.5f*u,paint);
                r.set(cx-5.5f*u,cy-6*u,cx+5.5f*u,cy+6*u);c.drawRoundRect(r,1*u,1*u,paint);
                paint.setStyle(Paint.Style.FILL);c.drawCircle(cx-8*u,cy-3*u,1.1f*u,paint);c.drawCircle(cx+8*u,cy+2.5f*u,1.1f*u,paint);break;
            case "speaker":
                r.set(cx-11*u,cy-3*u,cx+11*u,cy+3.5f*u);c.drawRoundRect(r,3*u,3*u,paint);
                paint.setStyle(Paint.Style.FILL);for(int i=-2;i<=2;i++)c.drawCircle(cx+i*3.6f*u,cy+0.25f*u,0.9f*u,paint);break;
            case "disc":
                c.drawCircle(cx,cy,9*u,paint);c.drawCircle(cx,cy,2.4f*u,paint);
                r.set(cx-5.8f*u,cy-5.8f*u,cx+5.8f*u,cy+5.8f*u);c.drawArc(r,200,60,false,paint);break;
            case "lock":
                r.set(cx-6.5f*u,cy-1.5f*u,cx+6.5f*u,cy+8.5f*u);c.drawRoundRect(r,2*u,2*u,paint);
                r.set(cx-4*u,cy-9*u,cx+4*u,cy+1*u);c.drawArc(r,180,180,false,paint);c.drawLine(cx-4*u,cy-5*u,cx-4*u,cy-1.5f*u,paint);c.drawLine(cx+4*u,cy-5*u,cx+4*u,cy-1.5f*u,paint);break;
            default:sun(c,cx,cy,u,1);
        }
    }
}
