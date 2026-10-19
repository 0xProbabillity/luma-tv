package dev.luma.tv;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Vector scene glyphs (sun, sofa, film, moon, door), drawn once per state change. */
final class SceneIcon extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final RectF r=new RectF();
    private String kind="sun";
    SceneIcon(Context context){super(context);paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);}
    void setKind(String value){kind=value==null?"sun":value;invalidate();}
    void setColor(int color){if(paint.getColor()!=color){paint.setColor(color);invalidate();}}
    @Override public boolean hasOverlappingRendering(){return false;}
    @Override protected void onDraw(Canvas c){
        float s=Math.min(getWidth(),getHeight()),cx=getWidth()/2f,cy=getHeight()/2f,u=s/24f;
        paint.setStrokeWidth(1.7f*u);paint.setStyle(Paint.Style.STROKE);path.reset();
        switch(kind){
            case "sofa":
                r.set(cx-7*u,cy-6*u,cx+7*u,cy+1*u);c.drawRoundRect(r,2.2f*u,2.2f*u,paint);
                r.set(cx-10*u,cy-1.5f*u,cx+10*u,cy+5.5f*u);c.drawRoundRect(r,2.4f*u,2.4f*u,paint);
                c.drawLine(cx-8*u,cy+5.5f*u,cx-8*u,cy+8*u,paint);c.drawLine(cx+8*u,cy+5.5f*u,cx+8*u,cy+8*u,paint);break;
            case "film":
                r.set(cx-9*u,cy-7*u,cx+9*u,cy+7*u);c.drawRoundRect(r,2.4f*u,2.4f*u,paint);
                c.drawLine(cx-4.5f*u,cy-7*u,cx-4.5f*u,cy+7*u,paint);c.drawLine(cx+4.5f*u,cy-7*u,cx+4.5f*u,cy+7*u,paint);
                c.drawLine(cx-9*u,cy,cx-4.5f*u,cy,paint);c.drawLine(cx+4.5f*u,cy,cx+9*u,cy,paint);
                c.drawLine(cx-9*u,cy-3.5f*u,cx-4.5f*u,cy-3.5f*u,paint);c.drawLine(cx+4.5f*u,cy-3.5f*u,cx+9*u,cy-3.5f*u,paint);
                c.drawLine(cx-9*u,cy+3.5f*u,cx-4.5f*u,cy+3.5f*u,paint);c.drawLine(cx+4.5f*u,cy+3.5f*u,cx+9*u,cy+3.5f*u,paint);break;
            case "moon":
                path.addCircle(cx-0.5f*u,cy+0.5f*u,8*u,Path.Direction.CW);
                Path cut=new Path();cut.addCircle(cx+4.2f*u,cy-3.6f*u,6.6f*u,Path.Direction.CW);path.op(cut,Path.Op.DIFFERENCE);
                c.drawPath(path,paint);
                paint.setStyle(Paint.Style.FILL);c.drawCircle(cx+7.5f*u,cy+3*u,1.1f*u,paint);break;
            case "door":
                r.set(cx-6*u,cy-9*u,cx+6*u,cy+9*u);c.drawRoundRect(r,1.6f*u,1.6f*u,paint);
                c.drawLine(cx-9.5f*u,cy+9*u,cx+9.5f*u,cy+9*u,paint);
                paint.setStyle(Paint.Style.FILL);c.drawCircle(cx+3*u,cy+0.8f*u,1.2f*u,paint);break;
            case "home":
                path.moveTo(cx-9*u,cy-0.5f*u);path.lineTo(cx,cy-8.5f*u);path.lineTo(cx+9*u,cy-0.5f*u);
                path.moveTo(cx-6.5f*u,cy-2.5f*u);path.lineTo(cx-6.5f*u,cy+8*u);path.lineTo(cx+6.5f*u,cy+8*u);path.lineTo(cx+6.5f*u,cy-2.5f*u);
                c.drawPath(path,paint);break;
            default: // sun
                c.drawCircle(cx,cy,4.4f*u,paint);
                for(int i=0;i<8;i++){double a=i*Math.PI/4;float x1=(float)Math.cos(a),y1=(float)Math.sin(a);c.drawLine(cx+x1*7.6f*u,cy+y1*7.6f*u,cx+x1*10*u,cy+y1*10*u,paint);}
        }
    }
}
