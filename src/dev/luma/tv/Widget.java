package dev.luma.tv;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.view.animation.PathInterpolator;
import android.widget.*;

/** A Home widget: a glass card in the widget row (design units, like the rest of Home). Subclasses add their own
 *  focusable items; WidgetBoard moves focus between them, across widgets and across pages. */
abstract class Widget extends FrameLayout {
    final WidgetBoard.Host host;
    final float w;
    final PathInterpolator ease=new PathInterpolator(.22f,1f,.36f,1f);
    final android.view.animation.Interpolator spring=t->{if(t>=1)return 1;double time=t*.42,f=24,d=.7,q=f*Math.sqrt(1-d*d);return (float)(1-Math.exp(-d*f*time)*(Math.cos(q*time)+d*f/q*Math.sin(q*time)));};
    boolean active;
    Widget(Context context,WidgetBoard.Host host,float width){
        super(context);this.host=host;w=width;setClipChildren(false);setClipToPadding(false);
        GradientDrawable bg=round(light()?0x8CFFFFFF:0x1FFFFFFF,22);bg.setStroke(Math.max(1,px(.8f)),light()?0x14000000:0x1FFFFFFF);setBackground(bg);
    }
    /** Starts or stops the widget's own work (listeners, timers); only the visible page is active. */
    void setActive(boolean value){active=value;}
    /** Home came back from an app: re-read anything that may have changed meanwhile. */
    void refresh(){}

    boolean light(){return host.light();}
    boolean motion(){return host.motion();}
    int px(float v){return Math.round(v*host.unit());}
    int ink(int alpha){return (alpha<<24)|(light()?0x1D1D1F:0xFFFFFF);}
    /** Colours of a focused control: a dark pill with white text on the pale wallpaper, a white pill elsewhere. */
    int focusFill(){return light()?0xFF1D1D1F:0xFAFFFFFF;}
    int focusInk(){return light()?Color.WHITE:0xFF1D1D1F;}
    int restFill(){return light()?0x99FFFFFF:0x1AFFFFFF;}
    LayoutParams pos(float width,float height,float x,float y){LayoutParams p=new LayoutParams(width<0?(int)width:px(width),height<0?(int)height:px(height));p.leftMargin=px(x);p.topMargin=px(y);return p;}
    TextView text(String value,float size,int color,String family){
        TextView t=new TextView(getContext());t.setText(value);t.setTextColor(color);t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(size));
        t.setTypeface(Typeface.create(family,Typeface.NORMAL));t.setIncludeFontPadding(false);t.setSingleLine(true);t.setEllipsize(android.text.TextUtils.TruncateAt.END);return t;
    }
    GradientDrawable round(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(px(radius));return d;}
    GradientDrawable oval(int color){GradientDrawable d=new GradientDrawable();d.setShape(GradientDrawable.OVAL);d.setColor(color);return d;}
    /** Makes a view a focusable control with the shared lift, sound and hero text. */
    void control(View v,int index,float scale,java.util.function.Consumer<Boolean> paint,java.util.function.Supplier<String[]> hero,Runnable click){
        v.setFocusable(true);v.setFocusableInTouchMode(true);v.setSoundEffectsEnabled(false);
        v.setOnFocusChangeListener((view,f)->{
            paint.accept(f);v.animate().cancel();float s=f?scale:1;
            if(motion())v.animate().scaleX(s).scaleY(s).setDuration(f?460:300).setInterpolator(f?spring:ease).start();else{v.setScaleX(s);v.setScaleY(s);}
            v.setElevation(px(f?12:0));
            if(f){String[] h=hero.get();host.hero(h[0],h[1],h[2]);host.step(index);}
        });
        v.setOnClickListener(view->{host.open();press(v,scale);click.run();});
        paint.accept(false);
    }
    /** A tile in the style of Control Center: a round glyph, a name and a status line; the glyph turns blue when on.
     *  Control and Inputs use it. */
    final class Tile extends FrameLayout {
        final View disc;final Glyph glyph;final TextView name,state;boolean focused,on;String key,detail="";int tint=0xFF0A84FF;
        Tile(String kind,String title,float tileW,float tileH,int index,Runnable action){
            super(Widget.this.getContext());setClipChildren(false);key=title;
            disc=new View(getContext());addView(disc,pos(36,36,12,(tileH-36)/2));
            glyph=new Glyph(getContext()).kind(kind);addView(glyph,pos(20,20,20,(tileH-20)/2));
            name=text(title,13.5f,ink(0xF2),"sans-serif-medium");addView(name,pos(tileW-62,17,58,tileH/2-17));
            state=text("",11.5f,ink(0x99),"sans-serif");addView(state,pos(tileW-62,15,58,tileH/2+3));
            setPivotX(px(tileW/2));setPivotY(px(tileH/2));
            control(this,index,1.06f,f->{focused=f;paint();},()->new String[]{"tile:"+key,name.getText().toString(),detail},action);
        }
        void paint(){
            setBackground(round(focused?focusFill():restFill(),20));
            int discFill=on?tint:focused?(light()?0x33FFFFFF:0x1A000000):(light()?0x14000000:0x24FFFFFF);
            disc.setBackground(oval(discFill));
            glyph.color(on?Color.WHITE:focused?focusInk():ink(0xE6));
            name.setTextColor(focused?focusInk():ink(0xF2));state.setTextColor(focused?(focusInk()&0x00FFFFFF|0xA6000000):ink(0x99));
        }
        void set(String status,String hero,boolean active){if(!status.contentEquals(state.getText()))state.setText(status);detail=hero;if(active!=on){on=active;paint();}}
    }
    void press(View v,float scale){
        if(!motion())return;v.animate().cancel();
        v.animate().scaleX(scale*.96f).scaleY(scale*.96f).setDuration(70).setInterpolator(ease).withEndAction(()->v.animate().scaleX(v.hasFocus()?scale:1).scaleY(v.hasFocus()?scale:1).setDuration(420).setInterpolator(spring).start()).start();
    }
}
