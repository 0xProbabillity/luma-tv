package dev.luma.tv;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.view.animation.PathInterpolator;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Local, certificate-paired home widgets. Reads no credentials and calls no cloud API.
 *  Layout (design units): five scene tiles on the left, the live 3D home on the right. */
final class HouseWidgets extends FrameLayout {
    static final float W=864,H=258,CARD_TOP=-94,SCENE_W=80,SCENE_H=94,SCENE_GAP=8,CARD_X=464,CARD_W=400;
    final Activity activity;final float scale;
    final Handler handler=new Handler(Looper.getMainLooper());
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    final ArrayList<SceneTile> scenes=new ArrayList<>();
    final FrameLayout card;
    final LinearLayout row;
    final TextView status,modelTitle,openHint;
    final View dot,cover;
    final WebView model;
    final ImageView still,after;
    final PathInterpolator ease=new PathInterpolator(.22f,1f,.36f,1f);
    final android.view.animation.Interpolator spring=t->{if(t>=1)return 1;double time=t*.42,f=24,d=.7,w=f*Math.sqrt(1-d*d);return (float)(1-Math.exp(-d*f*time)*(Math.cos(w*time)+d*f/w*Math.sin(w*time)));};
    volatile String snapshot="{}";
    IBinder binder;boolean bound=false,attached=false,active=true,commandBusy=false,loaded=false,fetching=false,portal=false;
    String revision="";JSONObject current;
    long warningUntil=0;
    final Callbacks callbacks;
    interface Callbacks{void up();void down(float screenX);void open();void focusHome(String title,String detail);void activate();boolean sceneApplied(String id);boolean motion();String sceneDetail(String id,String name);}
    final Runnable refresh=new Runnable(){public void run(){if(!attached||!active)return;fetch();handler.postDelayed(this,2000);}};
    final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder service){binder=service;setRemoteActive(active);fetch();if(!loaded){loaded=true;model.loadUrl("http://127.0.0.1:8788/widget.html");}}
        public void onServiceDisconnected(ComponentName name){binder=null;setStatus("Open House Control to reconnect",false);}
        public void onBindingDied(ComponentName name){binder=null;setStatus("Open House Control to reconnect",false);}
    };

    HouseWidgets(Activity activity,float scale,Callbacks callbacks){
        super(activity);this.activity=activity;this.scale=scale;this.callbacks=callbacks;setClipChildren(false);setClipToPadding(false);
        row=new LinearLayout(activity);row.setClipChildren(false);row.setClipToPadding(false);addView(row,pos(CARD_X-16,SCENE_H,0,26));
        TextView heading=text("Scenes",13,0xE6FFFFFF);addView(heading,pos(300,20,2,0));
        dot=new View(activity);dot.setBackground(round(0xFF34D17A,4));addView(dot,pos(6,6,3,140));
        status=text("Connecting to House Control…",12,0xB3FFFFFF);status.setSingleLine(true);addView(status,pos(CARD_X-40,18,15,134));

        card=new FrameLayout(activity);card.setBackground(cardBackground(false));card.setClipToOutline(true);card.setElevation(px(6));addView(card,pos(CARD_W,H,CARD_X,CARD_TOP));
        model=new WebView(activity);model.setBackgroundColor(0xFF0B111D);model.setFocusable(false);model.setFocusableInTouchMode(false);
        WebSettings settings=model.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(false);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setSupportZoom(false);
        model.addJavascriptInterface(new Object(){@JavascriptInterface public String home(){return snapshot;}@JavascriptInterface public double renderAspect(){return CARD_W/H;}@JavascriptInterface public double resolution(){return widgetResolution();}},"HouseWidget");
        model.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){String url=request.getUrl().toString();if(url.startsWith("http://127.0.0.1:8788/"))return null;return new WebResourceResponse("text/plain","utf-8",new java.io.ByteArrayInputStream(new byte[0]));}
        });
        // The viewport stays screen-sized so the portal can fill the screen without reflowing;
        // while it is a card the renderer draws at card resolution (see widget.js).
        card.addView(model,new FrameLayout.LayoutParams(screenW(),screenH()));
        model.setPivotX(0);model.setPivotY(0);resetModelTransform();
        still=new ImageView(activity);still.setScaleType(ImageView.ScaleType.FIT_XY);still.setVisibility(View.GONE);card.addView(still,new FrameLayout.LayoutParams(-1,-1));
        after=new ImageView(activity);after.setScaleType(ImageView.ScaleType.FIT_XY);after.setVisibility(View.GONE);card.addView(after,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(activity);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x99070B14,0x00070B14,0x00070B14,0x8C070B14}));card.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        modelTitle=text("Your home",15,Color.WHITE);card.addView(modelTitle,pos(300,22,18,15));
        openHint=text("Open",11,0xFF0B111D);openHint.setGravity(Gravity.CENTER);openHint.setBackground(round(0xF5FFFFFF,11));openHint.setAlpha(0);card.addView(openHint,pos(52,22,CARD_W-70,14));
        cover=new View(activity);cover.setFocusable(true);cover.setFocusableInTouchMode(true);cover.setSoundEffectsEnabled(false);cover.setContentDescription("Open your live 3D home");card.addView(cover,new FrameLayout.LayoutParams(-1,-1));
        cover.setOnClickListener(v->{callbacks.activate();callbacks.open();});
        cover.setOnFocusChangeListener((v,focus)->{
            if(focus)callbacks.focusHome(modelTitle.getText().toString(),status.getText().toString());
            card.setBackground(cardBackground(focus));card.setElevation(px(focus?18:6));
            card.animate().cancel();openHint.animate().cancel();
            float s=focus?1.035f:1;
            if(callbacks.motion()){card.animate().scaleX(s).scaleY(s).setDuration(420).setInterpolator(spring).start();openHint.animate().alpha(focus?1:0).setDuration(220).start();}
            else{card.setScaleX(s);card.setScaleY(s);openHint.setAlpha(focus?1:0);}
        });
        cover.setOnKeyListener((v,key,event)->{if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;if(key==KeyEvent.KEYCODE_DPAD_DOWN){callbacks.down(centerX(card));return true;}if(key==KeyEvent.KEYCODE_DPAD_UP){callbacks.up();return true;}if(key==KeyEvent.KEYCODE_DPAD_LEFT){focusLastScene();return true;}return key==KeyEvent.KEYCODE_DPAD_RIGHT;});
        renderScenes(null);
    }
    int px(float x){return Math.round(x*scale);}
    int screenW(){return activity.getResources().getDisplayMetrics().widthPixels;}
    int screenH(){return activity.getResources().getDisplayMetrics().heightPixels;}
    // One buffer size for the card and the flight: no reallocation (and no stall) when the portal starts.
    double widgetResolution(){return Math.min(1.0,Math.max(.6,(CARD_W*scale*1.15)/screenW()));}
    LayoutParams pos(float w,float h,float x,float y){LayoutParams p=new LayoutParams(px(w),px(h));p.leftMargin=px(x);p.topMargin=px(y);return p;}
    TextView text(String value,float size,int color){TextView t=new TextView(activity);t.setText(value);t.setTextColor(color);t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(size));t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));t.setIncludeFontPadding(false);return t;}
    GradientDrawable round(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(px(radius));return d;}
    GradientDrawable cardBackground(boolean focus){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xFF101A2B,0xFF0B111D});d.setCornerRadius(px(20));d.setStroke(Math.max(1,px(focus?1.6f:.8f)),focus?0xF2FFFFFF:0x26FFFFFF);return d;}

    /** One scene: a glass tile with a vector glyph; white when focused, warm when active. */
    final class SceneTile extends FrameLayout {
        final String id,name,icon;final SceneIcon glyph;final TextView label;final View active;boolean selected,focused;
        SceneTile(String id,String name,String icon){
            super(activity);this.id=id;this.name=name;this.icon=icon;setFocusable(true);setFocusableInTouchMode(true);setSoundEffectsEnabled(false);setClipChildren(false);
            setContentDescription(name+" scene");
            glyph=new SceneIcon(activity);glyph.setKind(icon);addView(glyph,pos(27,27,(SCENE_W-27)/2,19));
            label=text(name,12,0xF2FFFFFF);label.setGravity(Gravity.CENTER);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);addView(label,pos(SCENE_W-8,16,4,58));
            active=new View(activity);active.setBackground(round(0xFFFFC46B,2.5f));active.setAlpha(0);addView(active,pos(5,5,(SCENE_W-5)/2,81));
            paint();
            setOnFocusChangeListener((v,f)->{focused=f;paint();if(f)callbacks.focusHome(name,callbacks.sceneDetail(id,name));
                animate().cancel();float s=f?1.09f:1;if(callbacks.motion())animate().scaleX(s).scaleY(s).translationY(f?-px(2):0).setDuration(f?440:300).setInterpolator(f?spring:ease).start();else{setScaleX(s);setScaleY(s);}
                setElevation(px(f?16:0));});
            setOnClickListener(v->{callbacks.activate();press();runScene(id,name);});
        }
        void press(){if(!callbacks.motion())return;animate().cancel();animate().scaleX(1.02f).scaleY(1.02f).setDuration(70).setInterpolator(ease).withEndAction(()->animate().scaleX(focused?1.09f:1).scaleY(focused?1.09f:1).setDuration(420).setInterpolator(spring).start()).start();}
        void paint(){
            GradientDrawable bg=round(focused?0xFAFFFFFF:selected?0x38FFFFFF:0x1FFFFFFF,18);if(!focused)bg.setStroke(Math.max(1,px(.8f)),selected?0x66FFD596:0x24FFFFFF);setBackground(bg);
            int ink=focused?0xFF111827:selected?0xFFFFD08A:0xF2FFFFFF;glyph.setColor(ink);label.setTextColor(focused?0xFF111827:0xF2FFFFFF);
            active.animate().cancel();active.animate().alpha(selected?1:0).setDuration(240).start();
            setAlpha(commandBusy&&!focused?.55f:1);
        }
    }

    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;Intent intent=new Intent().setComponent(new ComponentName("dev.housecontrol.tv","dev.housecontrol.tv.HomeService"));try{bound=activity.bindService(intent,connection,Context.BIND_AUTO_CREATE);}catch(Exception ignored){}handler.post(refresh);}
    @Override protected void onDetachedFromWindow(){attached=false;handler.removeCallbacksAndMessages(null);if(bound){try{activity.unbindService(connection);}catch(Exception ignored){}bound=false;}binder=null;model.removeJavascriptInterface("HouseWidget");model.destroy();worker.shutdownNow();super.onDetachedFromWindow();}
    void setActive(boolean value){active=value;setRemoteActive(value);handler.removeCallbacks(refresh);if(value&&attached){model.onResume();model.evaluateJavascript("window.widgetRunning && window.widgetRunning(true)",null);handler.post(refresh);}else{model.evaluateJavascript("window.widgetRunning && window.widgetRunning(false)",null);model.onPause();}}
    void setRemoteActive(boolean value){if(binder==null||worker.isShutdown())return;worker.execute(()->{IBinder target=binder;if(target==null)return;Parcel in=Parcel.obtain(),out=Parcel.obtain();try{in.writeInt(value?1:0);target.transact(3,in,out,0);out.readException();}catch(Exception ignored){}finally{in.recycle();out.recycle();}});}
    JSONObject transact(int code,String scene)throws Exception{
        IBinder target=binder;if(target==null)throw new IllegalStateException("Open House Control to connect your home.");Parcel in=Parcel.obtain(),out=Parcel.obtain();try{if(scene!=null)in.writeString(scene);if(!target.transact(code,in,out,0))throw new IllegalStateException("Update House Control for home widgets.");out.readException();return new JSONObject(out.readString());}finally{in.recycle();out.recycle();}
    }
    void fetch(){if(fetching||binder==null||commandBusy)return;fetching=true;worker.execute(()->{try{JSONObject home=transact(1,null);String raw=home.toString();handler.post(()->{if(!attached)return;boolean changed=!raw.equals(snapshot);snapshot=raw;if(changed)update(home);});}catch(Exception error){handler.post(()->{if(attached)setStatus("Open House Control to reconnect",false);});}finally{fetching=false;}});}
    void setStatus(String value,boolean live){if(!value.contentEquals(status.getText()))status.setText(value);dot.setBackground(round(live?0xFF34D17A:0xFFE0A84E,4));}
    void update(JSONObject home){
        if(home.has("error")){setStatus(home.optString("error"),false);return;}current=home;
        String name=home.optJSONObject("plan")==null?"":home.optJSONObject("plan").optString("name");
        modelTitle.setText(home.optBoolean("demo")?(name.isEmpty()?"Demo home":name):"Your home");
        JSONObject state=home.optJSONObject("state"),lights=state==null?null:state.optJSONObject("lights");int on=0,total=lights==null?0:lights.length();if(lights!=null){Iterator<String> keys=lights.keys();while(keys.hasNext())if(lights.optJSONObject(keys.next()).optBoolean("on"))on++;}
        boolean ready=home.optBoolean("controlReady");String lit=on==0?"All lights off":on+" of "+total+" lights on";
        if(System.currentTimeMillis()<warningUntil)setStatus("Some devices did not respond",false);
        else if(!ready)setStatus("Connect Smart Life in House Control",false);
        else if(!home.optString("cloudError").isEmpty())setStatus(lit+" · Reconnecting",false);
        else if(home.optLong("lastPoll")==0)setStatus("Updating from Smart Life…",false);
        else setStatus(home.optBoolean("demo")?lit+" · Demo":lit,true);
        if(!revision.equals(home.optString("revision"))){revision=home.optString("revision");renderScenes(home.optJSONObject("plan"));}
        String selected=state==null?"":state.optString("scene");for(SceneTile t:scenes){boolean s=t.id.equals(selected);if(s!=t.selected){t.selected=s;t.paint();}}
        model.evaluateJavascript("window.widgetRefresh && window.widgetRefresh()",null);
    }
    void renderScenes(JSONObject plan){
        boolean hadFocus=false;for(SceneTile t:scenes)hadFocus|=t.hasFocus();
        row.removeAllViews();scenes.clear();JSONArray list=plan==null?null:plan.optJSONArray("scenes");int count=list==null?0:Math.min(5,list.length());
        if(count==0){for(String[] s:new String[][]{{"bright","Bright","sun"},{"cozy","Cozy","sofa"},{"movie","Movie","film"},{"night","Night","moon"},{"away","Away","door"}})addScene(s[0],s[1],s[2]);}
        else for(int i=0;i<count;i++){JSONObject s=list.optJSONObject(i);addScene(s.optString("id"),s.optString("name",s.optString("id")),s.optString("icon","sun"));}
        for(int i=0;i<scenes.size();i++){final int index=i;SceneTile t=scenes.get(i);t.setOnKeyListener((v,key,event)->{if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
            if(key==KeyEvent.KEYCODE_DPAD_UP){callbacks.up();return true;}
            if(key==KeyEvent.KEYCODE_DPAD_DOWN){callbacks.down(centerX(t));return true;}
            if(key==KeyEvent.KEYCODE_DPAD_LEFT){if(index>0){scenes.get(index-1).requestFocus();return true;}return true;}
            if(key==KeyEvent.KEYCODE_DPAD_RIGHT){if(index+1<scenes.size())scenes.get(index+1).requestFocus();else cover.requestFocus();return true;}
            return false;});}
        if(hadFocus&&!scenes.isEmpty())scenes.get(0).requestFocus();
    }
    void addScene(String id,String name,String icon){SceneTile t=new SceneTile(id,name,icon);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(px(SCENE_W),px(SCENE_H));if(!scenes.isEmpty())lp.leftMargin=px(SCENE_GAP);row.addView(t,lp);scenes.add(t);}
    void runScene(String id,String name){if(commandBusy)return;if(current==null||!current.optBoolean("controlReady")){callbacks.open();return;}commandBusy=true;for(SceneTile t:scenes)t.paint();setStatus("Setting "+name+"…",true);
        final boolean demo=current.optBoolean("demo");freeze(()->worker.execute(()->{try{JSONObject result=transact(demo?4:2,id);handler.post(()->{if(!attached)return;commandBusy=false;if(result.has("error")){warningUntil=System.currentTimeMillis()+15000;setStatus("Some devices did not respond",false);Toast.makeText(activity,result.optString("error"),Toast.LENGTH_LONG).show();}else{warningUntil=0;JSONObject home=result.optJSONObject("home");if(home!=null){snapshot=home.toString();update(home);}reveal(()->{if(!callbacks.sceneApplied(id))unfreeze();});}for(SceneTile t:scenes)t.paint();if(result.has("error"))unfreeze();fetch();});}catch(Exception error){handler.post(()->{commandBusy=false;unfreeze();for(SceneTile t:scenes)t.paint();if(attached)Toast.makeText(activity,"Could not apply scene. Open House Control.",Toast.LENGTH_LONG).show();});}}));
    }
    float tvX=.5f,tvY=.5f;
    /** Reads the model as a picture. settled=true jumps light fades to their end first. */
    void capture(boolean settled,java.util.function.Consumer<Bitmap> done){
        model.evaluateJavascript("window.widgetSnapshot && window.widgetSnapshot("+settled+")",value->{
            if(value==null||value.length()<40){done.accept(null);return;}
            worker.execute(()->{Bitmap bitmap=null;try{
                JSONObject o=new JSONObject(new JSONTokener(value).nextValue().toString());
                String data=o.optString("image");byte[] bytes=android.util.Base64.decode(data.substring(data.indexOf(',')+1),android.util.Base64.DEFAULT);
                bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);JSONObject tv=o.optJSONObject("tv");
                if(tv!=null){tvX=(float)tv.optDouble("x",.5);tvY=(float)tv.optDouble("y",.5);}
            }catch(Exception ignored){}final Bitmap result=bitmap;handler.post(()->done.accept(attached?result:null));});
        });
    }
    /** Freezes the model as it is now: the scene change is then a cheap picture cross-fade, not WebGL. */
    void freeze(Runnable done){
        capture(false,b->{if(b!=null){still.setImageBitmap(b);still.setAlpha(1f);still.setVisibility(View.VISIBLE);model.evaluateJavascript("window.widgetRunning && window.widgetRunning(false)",null);}done.run();});
    }
    /** Shows the new light state with a soft cross-fade; done once it has landed. */
    void reveal(Runnable done){
        if(still.getVisibility()!=View.VISIBLE){done.run();return;}
        capture(true,b->{if(b==null){unfreeze();done.run();return;}
            after.setImageBitmap(b);after.setAlpha(0f);after.setVisibility(View.VISIBLE);
            after.animate().alpha(1).setDuration(callbacks.motion()?650:0).setInterpolator(ease).withEndAction(()->{still.setImageBitmap(b);after.setVisibility(View.GONE);done.run();}).start();});
    }
    void unfreeze(){
        if(still.getVisibility()!=View.VISIBLE||portal)return;
        model.evaluateJavascript("window.widgetRunning && window.widgetRunning("+active+")",null);
        still.animate().cancel();still.animate().alpha(0).setDuration(180).withEndAction(()->{still.setVisibility(View.GONE);still.setImageDrawable(null);still.setAlpha(1f);}).start();
    }
    /** The card becomes the screen and pushes in toward the modelled TV. */
    void portalToTV(int duration){
        portal=true;
        int[] location=new int[2];card.getLocationOnScreen(location);setTranslationZ(px(60));card.setPivotX(0);card.setPivotY(0);
        modelTitle.animate().alpha(0).setDuration(160).start();openHint.animate().alpha(0).setDuration(160).start();
        PathInterpolator glide=new PathInterpolator(.6f,0f,.2f,1f);
        float sx=screenW()/(float)card.getWidth(),sy=screenH()/(float)card.getHeight();
        if(still.getVisibility()==View.VISIBLE){
            still.setPivotX(tvX*card.getWidth());still.setPivotY(tvY*card.getHeight());
            still.animate().scaleX(2.4f).scaleY(2.4f).setDuration(duration).setInterpolator(new PathInterpolator(.5f,0f,.3f,1f)).withLayer().start();
        } else {
            model.evaluateJavascript("window.widgetPortal && window.widgetPortal("+duration+")",null);
            model.animate().translationY(0).scaleX(px(CARD_W)/(float)screenW()).scaleY(px(H)/(float)screenH()).setDuration(duration).setInterpolator(glide).start();
        }
        card.setBackground(round(0xFF0B111D,20));
        card.animate().translationX(-location[0]).translationY(-location[1]).scaleX(sx).scaleY(sy).setDuration(duration).setInterpolator(glide).withLayer().start();
    }
    /** Scenes and status step back while the card takes the screen. */
    void fadeControls(int ms){for(View v:new View[]{row,status,dot,getChildAt(1)})v.animate().alpha(0).setDuration(ms).withLayer().start();}
    void resetModelTransform(){model.animate().cancel();model.setScaleX(px(CARD_W)/(float)screenW());model.setScaleY(px(H)/(float)screenH());model.setTranslationY(0);}
    void resetPortal(){portal=false;still.animate().cancel();still.setScaleX(1);still.setScaleY(1);still.setVisibility(View.GONE);still.setImageDrawable(null);after.setVisibility(View.GONE);model.setVisibility(View.VISIBLE);model.evaluateJavascript("window.widgetRunning && window.widgetRunning("+active+")",null);for(View v:new View[]{row,status,dot,getChildAt(1)}){v.animate().cancel();v.setAlpha(1);}card.animate().cancel();card.setScaleX(1);card.setScaleY(1);card.setTranslationX(0);card.setTranslationY(0);card.setBackground(cardBackground(cover.hasFocus()));setTranslationZ(0);resetModelTransform();modelTitle.animate().cancel();modelTitle.setAlpha(1);openHint.setAlpha(0);model.evaluateJavascript("window.widgetResetCamera && window.widgetResetCamera()",null);}
    void focusFirstScene(){if(!scenes.isEmpty())scenes.get(0).requestFocus();else cover.requestFocus();}
    void focusLastScene(){if(!scenes.isEmpty())scenes.get(scenes.size()-1).requestFocus();}
    float centerX(View v){int[] l=new int[2];v.getLocationOnScreen(l);return l[0]+v.getWidth()/2f;}
    /** Up from a favourite: the scene or card nearest above it. */
    void focusNearest(float x){View best=cover;float d=Math.abs(centerX(card)-x);for(SceneTile t:scenes){float e=Math.abs(centerX(t)-x);if(e<d){d=e;best=t;}}best.requestFocus();}
    void focusScene(int index){if(scenes.isEmpty()){cover.requestFocus();return;}if(index>=scenes.size())cover.requestFocus();else scenes.get(Math.max(0,index)).requestFocus();}
}
