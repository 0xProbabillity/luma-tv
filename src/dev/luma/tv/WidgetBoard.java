package dev.luma.tv;

import android.app.Activity;
import android.content.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.view.animation.PathInterpolator;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Home pages. The widgets chosen in Settings flow, in order, into pages of one row each (864 design units wide);
 *  the Smart Home widget (House Control's scenes and 3D home) is a page of its own, drawn by HouseWidgets beneath
 *  this board. The favourites stay below every page. Left and Right past the last control of a page turn the page;
 *  focus moves inside a page with the platform's FocusFinder, limited to that page. */
final class WidgetBoard extends FrameLayout {
    static final float W=960,H=392,X=48,ROW_Y=190,ROW_H=186,ROW_W=864,GAP=16,DOTS_Y=380;
    /** Every widget, in the default order. */
    static final String[] IDS={"house","upnext","weather","music","control"};
    /** Widgets do their network and disk work here, away from the launcher's own loader (wallpapers, apps). */
    static final ExecutorService IO=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"Luma widgets");t.setDaemon(true);t.setPriority(Thread.MIN_PRIORITY+1);return t;});

    interface Host {
        float unit();boolean light();boolean motion();
        void step(int index);void open();
        void hero(String key,String title,String detail);
        View up();View down(float screenX);
        /** The page changed to (house=true) or away from the Smart Home page; direction is +1 (right) or -1. */
        void pageChanged(boolean house,int direction);
        View houseEntry(int direction);
        void nudgeHouse(int direction);
        MainActivity.App app(String pkg);View art(String pkg);
        void launch(String pkg);void start(Intent intent);
        SharedPreferences prefs();Activity activity();
    }
    static String title(String id){switch(id){case "house":return "Smart Home";case "upnext":return "Up Next";case "weather":return "Weather";case "music":return "Now Playing";default:return "Control";}}
    static String glyph(String id){switch(id){case "house":return "home";case "upnext":return "tv";case "weather":return "partly";case "music":return "note";default:return "gear";}}
    static String about(String id){switch(id){
        case "house":return "Scenes and your live 3D home, from House Control TV.";
        case "upnext":return "Pick up shows and videos where you left off, and your recent apps.";
        case "weather":return "Now, the next hours and the coming days where you are.";
        case "music":return "What's playing on this TV, with play, pause and skip.";
        default:return "Wi-Fi, picture and sound, accessories and settings in one place.";}}
    static float width(String id){switch(id){case "upnext":return 560;case "weather":return 288;default:return 424;}}

    /** All widgets in the saved order (new ones appended), and which of them are on. */
    static ArrayList<String> order(SharedPreferences prefs){
        ArrayList<String> ids=new ArrayList<>();for(String id:prefs.getString("widget_order","").split(","))if(Arrays.asList(IDS).contains(id)&&!ids.contains(id))ids.add(id);
        for(String id:IDS)if(!ids.contains(id))ids.add(id);return ids;
    }
    static boolean on(SharedPreferences prefs,String id){return !Arrays.asList(prefs.getString("widget_off","").split(",")).contains(id);}
    static void save(SharedPreferences prefs,List<String> order,Set<String> off){prefs.edit().putString("widget_order",String.join(",",order)).putString("widget_off",String.join(",",off)).apply();}
    /** Pages of widget ids: the house alone, the others packed left to right into rows of ROW_W. */
    static ArrayList<ArrayList<String>> pages(SharedPreferences prefs,boolean house){
        ArrayList<ArrayList<String>> pages=new ArrayList<>();ArrayList<String> row=null;float used=0;
        for(String id:order(prefs)){
            if(!on(prefs,id))continue;
            if(id.equals("house")){if(!house)continue;pages.add(new ArrayList<>(Collections.singletonList(id)));row=null;continue;}
            float w=width(id);
            if(row==null||used+GAP+w>ROW_W){row=new ArrayList<>();pages.add(row);used=w;}else used+=GAP+w;
            row.add(id);
        }
        return pages;
    }

    final Host host;
    final ArrayList<ArrayList<String>> layout;
    final ArrayList<FrameLayout> pageViews=new ArrayList<>();
    final ArrayList<ArrayList<Widget>> widgets=new ArrayList<>();
    final LinearLayout dots;
    final PathInterpolator ease=new PathInterpolator(.22f,1f,.36f,1f);
    int index;boolean active;

    WidgetBoard(Context context,Host host,ArrayList<ArrayList<String>> layout,int start){
        super(context);this.host=host;this.layout=layout;setClipChildren(false);setClipToPadding(false);
        index=Math.max(0,Math.min(layout.size()-1,start));
        for(int p=0;p<layout.size();p++){
            FrameLayout page=new FrameLayout(context);page.setClipChildren(false);page.setClipToPadding(false);
            ArrayList<Widget> list=new ArrayList<>();float x=X;
            for(String id:layout.get(p)){
                if(id.equals("house"))continue;
                Widget w=create(id);if(w==null)continue;
                page.addView(w,lp(width(id),ROW_H,x,ROW_Y));list.add(w);x+=width(id)+GAP;
            }
            page.setVisibility(p==index?VISIBLE:INVISIBLE);
            addView(page,new LayoutParams(-1,-1));pageViews.add(page);widgets.add(list);
        }
        dots=new LinearLayout(context);dots.setGravity(Gravity.CENTER);
        LayoutParams dp=new LayoutParams(px(200),px(8));dp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;dp.topMargin=px(DOTS_Y);addView(dots,dp);
        for(int p=0;p<layout.size();p++){View dot=new View(context);LinearLayout.LayoutParams l=new LinearLayout.LayoutParams(px(6),px(6));l.leftMargin=l.rightMargin=px(3);dots.addView(dot,l);}
        dots.setVisibility(layout.size()>1?VISIBLE:GONE);paintDots(false);
    }
    private Widget create(String id){
        Context c=getContext();
        switch(id){
            case "upnext":return new UpNextWidget(c,host);
            case "weather":return new WeatherWidget(c,host);
            case "music":return new NowPlayingWidget(c,host);
            case "control":return new ControlWidget(c,host);
            default:return null;
        }
    }
    int px(float v){return Math.round(v*host.unit());}
    LayoutParams lp(float w,float h,float x,float y){LayoutParams p=new LayoutParams(px(w),px(h));p.leftMargin=px(x);p.topMargin=px(y);return p;}
    boolean onHouse(){return !layout.isEmpty()&&layout.get(index).contains("house");}
    boolean hasHouse(){for(ArrayList<String> p:layout)if(p.contains("house"))return true;return false;}
    int pageCount(){return layout.size();}

    void setActive(boolean value){active=value;for(int p=0;p<widgets.size();p++)for(Widget w:widgets.get(p))w.setActive(value&&p==index);}
    void refresh(){for(Widget w:widgets.get(index))w.refresh();}
    @Override protected void onDetachedFromWindow(){for(ArrayList<Widget> list:widgets)for(Widget w:list)w.setActive(false);super.onDetachedFromWindow();}

    /** The current page's dot is a short pill; the others are small, quiet dots. */
    private void paintDots(boolean animate){
        int on=host.light()?0xD91D1D1F:0xE6FFFFFF,off=host.light()?0x401D1D1F:0x47FFFFFF;
        for(int i=0;i<dots.getChildCount();i++){
            View dot=dots.getChildAt(i);GradientDrawable d=new GradientDrawable();d.setCornerRadius(px(3));d.setColor(i==index?on:off);dot.setBackground(d);
            LinearLayout.LayoutParams l=(LinearLayout.LayoutParams)dot.getLayoutParams();int target=px(i==index?16:6);
            if(l.width==target)continue;
            if(animate&&host.motion()){final int from=l.width;android.animation.ValueAnimator a=android.animation.ValueAnimator.ofFloat(0,1);a.setDuration(320);a.setInterpolator(ease);
                a.addUpdateListener(v->{l.width=Math.round(from+(target-from)*(float)v.getAnimatedValue());dot.setLayoutParams(l);});a.start();}
            else{l.width=target;dot.setLayoutParams(l);}
        }
    }

    /** Focus stays inside the current page; past its edges it turns the page, goes up to the tabs or down to the apps. */
    @Override public View focusSearch(View focused,int direction){
        if(focused==null||index>=pageViews.size()||!isDescendant(pageViews.get(index),focused))return super.focusSearch(focused,direction);
        View next=FocusFinder.getInstance().findNextFocus(pageViews.get(index),focused,direction);
        if(next!=null)return next;
        switch(direction){
            case FOCUS_LEFT:return flip(-1,focused);
            case FOCUS_RIGHT:return flip(1,focused);
            case FOCUS_UP:return host.up();
            case FOCUS_DOWN:{View down=host.down(centerX(focused));return down!=null?down:focused;}
            default:return super.focusSearch(focused,direction);
        }
    }
    static boolean isDescendant(ViewGroup parent,View v){for(ViewParent p=v.getParent();p!=null;p=p.getParent())if(p==parent)return true;return false;}
    static float centerX(View v){int[] l=new int[2];v.getLocationOnScreen(l);return l[0]+v.getWidth()/2f;}
    static float centerY(View v){int[] l=new int[2];v.getLocationOnScreen(l);return l[1]+v.getHeight()/2f;}

    /** Turns to the next (+1) or previous (-1) page and returns the control to focus there: the one nearest the
     *  edge it was entered from, at about the same height. At the first or last page the row nudges and stays. */
    View flip(int direction,View from){
        int next=index+direction;
        if(next<0||next>=layout.size()){nudge(direction);return from;}
        float y=from==null?0:centerY(from);
        show(next,direction);
        if(onHouse())return host.houseEntry(direction);
        View target=edge(direction>0,y);return target!=null?target:from;
    }
    /** Switches pages with a short slide; the outgoing page stops taking focus at once. */
    void show(int next,int direction){
        if(next==index)return;
        FrameLayout out=pageViews.get(index),in=pageViews.get(next);
        for(Widget w:widgets.get(index))w.setActive(false);
        index=next;
        out.animate().cancel();in.animate().cancel();
        in.setVisibility(VISIBLE);
        // Each page slides as one cached layer: fading a group of glass cards without one re-blends them every frame.
        if(host.motion()){
            out.animate().translationX(-direction*px(56)).alpha(0).setDuration(240).setInterpolator(ease).withLayer().withEndAction(()->{if(pageViews.indexOf(out)!=index){out.setVisibility(INVISIBLE);out.setTranslationX(0);out.setAlpha(1);}}).start();
            in.setTranslationX(direction*px(72));in.setAlpha(0);in.animate().translationX(0).alpha(1).setDuration(460).setStartDelay(40).setInterpolator(ease).withLayer().start();
        } else {out.setVisibility(INVISIBLE);in.setTranslationX(0);in.setAlpha(1);}
        // Widgets start their work once the page has landed, so binder calls and decoding stay out of the slide.
        final int shown=index;postDelayed(()->{if(index==shown)for(Widget w:widgets.get(shown))w.setActive(active);},host.motion()?480:0);
        paintDots(true);
        host.pageChanged(onHouse(),direction);
    }
    /** Jumps to a page (Back from a later page returns to the first one). */
    void showPage(int page){if(page!=index&&page>=0&&page<layout.size())show(page,page>index?1:-1);}
    private void nudge(int direction){
        if(onHouse()){host.nudgeHouse(direction);return;}
        if(!host.motion())return;View page=pageViews.get(index);page.animate().cancel();
        page.animate().translationX(direction*px(10)).setDuration(110).setInterpolator(ease).withEndAction(()->page.animate().translationX(0).setDuration(380).setInterpolator(new android.view.animation.OvershootInterpolator(1.4f)).start()).start();
    }
    private ArrayList<View> focusables(){ArrayList<View> list=new ArrayList<>();if(index<pageViews.size())pageViews.get(index).addFocusables(list,FOCUS_FORWARD);return list;}
    /** The leftmost (or rightmost) control of the page, preferring the row nearest y. */
    View edge(boolean leftmost,float y){
        View best=null;float score=Float.MAX_VALUE;
        for(View v:focusables()){float x=centerX(v),s=(leftmost?x:-x)+Math.abs(centerY(v)-y)*.6f;if(s<score){score=s;best=v;}}
        return best;
    }
    View first(){if(onHouse())return host.houseEntry(1);ArrayList<View> list=focusables();View best=null;for(View v:list)if(best==null||centerY(v)<centerY(best)-2||Math.abs(centerY(v)-centerY(best))<=2&&centerX(v)<centerX(best))best=v;return best;}
    /** Up from a favourite: the lowest control nearest above it. */
    View nearest(float x){
        View best=null;float score=Float.MAX_VALUE;
        for(View v:focusables()){float s=Math.abs(centerX(v)-x)-centerY(v)*.35f;if(s<score){score=s;best=v;}}
        return best;
    }
}
