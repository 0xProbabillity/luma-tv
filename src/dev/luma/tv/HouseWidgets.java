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
 *  Layout (design units, the page's own coordinates): a row of round scene buttons under the greeting, and the live
 *  3D home floating on the wallpaper at the right, with no card around it. A quiet status line sits under the model.
 *  The WebGL view is never drawn on Home: it renders off screen and the page shows a picture of it, refreshed with a
 *  cross-fade when the lights change. A transparent WebView composited over the wallpaper made every focus
 *  animation pay for blending a screen-sized layer; a picture costs one textured quad. */
final class HouseWidgets extends FrameLayout {
    static final float W=960,PAGE_H=392,SCENE_X=48,SCENE_Y=212,SCENE_W=70,SCENE_H=86,SCENE_D=58,SCENE_GAP=6,
        CARD_X=444,CARD_Y=66,CARD_W=496,H=310,STATUS_Y=372;
    final Activity activity;final float scale;
    final Handler handler=new Handler(Looper.getMainLooper());
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    final ArrayList<SceneTile> scenes=new ArrayList<>();
    final FrameLayout card;
    final LinearLayout row,statusRow;
    final TextView status,openHint;
    final View dot,cover,backdrop,ground;
    final WebView model;
    final ImageView still,after;
    final PathInterpolator ease=new PathInterpolator(.22f,1f,.36f,1f);
    final android.view.animation.Interpolator spring=t->{if(t>=1)return 1;double time=t*.42,f=24,d=.7,w=f*Math.sqrt(1-d*d);return (float)(1-Math.exp(-d*f*time)*(Math.cos(w*time)+d*f/w*Math.sin(w*time)));};
    /** The last picture of the model by day [1] and by night [0], kept across page rebuilds: Home shows the house at
     *  once while WebGL starts again, also after sunset once a night picture exists. */
    static final Bitmap[] lastPicture=new Bitmap[2];static float lastTvX=.5f,lastTvY=.5f;
    volatile String snapshot="{}";
    volatile boolean daylight;
    IBinder binder;boolean bound=false,attached=false,active=true,commandBusy=false,loaded=false,fetching=false,portal=false,shown=false,live=false;
    String revision="",title="Your home",pictured="";JSONObject current;
    long warningUntil=0;
    final Callbacks callbacks;
    interface Callbacks{void up();void down(float screenX);void open();void focusHome(String title,String detail);void activate();boolean sceneApplied(String id);boolean motion();String sceneDetail(String id,String name);boolean light();}
    final Runnable refresh=new Runnable(){public void run(){if(!attached||!active)return;fetch();handler.postDelayed(this,2000);}};
    final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder service){binder=service;setRemoteActive(active);fetch();if(!loaded){loaded=true;model.loadUrl("http://127.0.0.1:8788/widget.html");}}
        public void onServiceDisconnected(ComponentName name){binder=null;setStatus("Open House Control to reconnect",false);}
        public void onBindingDied(ComponentName name){binder=null;setStatus("Open House Control to reconnect",false);}
    };

    HouseWidgets(Activity activity,float scale,Callbacks callbacks){
        super(activity);this.activity=activity;this.scale=scale;this.callbacks=callbacks;daylight=callbacks.light();setClipChildren(false);setClipToPadding(false);
        row=new LinearLayout(activity);row.setClipChildren(false);row.setClipToPadding(false);addView(row,pos(5*SCENE_W+4*SCENE_GAP,SCENE_H,SCENE_X-(SCENE_W-SCENE_D)/2,SCENE_Y));

        // A soft contact shadow grounds the floating model; it is a static drawable, not part of the 3D render.
        ground=new View(activity);GradientDrawable shadow=new GradientDrawable();shadow.setGradientType(GradientDrawable.RADIAL_GRADIENT);shadow.setGradientRadius(px(CARD_W*.34f));
        shadow.setColors(new int[]{callbacks.light()?0x30000000:0x73000000,0x00000000});ground.setBackground(shadow);ground.setScaleY(.24f);
        addView(ground,pos(CARD_W*.8f,CARD_W*.8f,CARD_X+CARD_W*.1f,CARD_Y+H*.72f-CARD_W*.4f));

        card=new FrameLayout(activity);card.setClipChildren(true);addView(card,pos(CARD_W,H,CARD_X,CARD_Y));
        backdrop=new View(activity);backdrop.setBackgroundColor(0xFF0B0B0D);backdrop.setAlpha(0);card.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));
        model=new WebView(activity);model.setBackgroundColor(Color.TRANSPARENT);model.setFocusable(false);model.setFocusableInTouchMode(false);
        model.setAlpha(0); // an off-screen renderer: the pictures in still/after are what Home shows
        WebSettings settings=model.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(false);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setSupportZoom(false);
        model.addJavascriptInterface(new Object(){
            @JavascriptInterface public String home(){return snapshot;}
            @JavascriptInterface public double renderAspect(){return CARD_W/H;}
            @JavascriptInterface public double resolution(){return widgetResolution();}
            @JavascriptInterface public boolean daylight(){return daylight;}
            @JavascriptInterface public void ready(){handler.post(HouseWidgets.this::modelReady);}
        },"HouseWidget");
        model.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){String url=request.getUrl().toString();if(url.startsWith("http://127.0.0.1:8788/"))return null;return new WebResourceResponse("text/plain","utf-8",new java.io.ByteArrayInputStream(new byte[0]));}
        });
        // The viewport stays screen-sized so the portal can fill the screen without reflowing;
        // while it is small the renderer draws at its own resolution (see widget.js).
        card.addView(model,new FrameLayout.LayoutParams(screenW(),screenH()));
        model.setPivotX(0);model.setPivotY(0);resetModelTransform();
        still=new ImageView(activity);still.setScaleType(ImageView.ScaleType.FIT_XY);still.setVisibility(View.GONE);card.addView(still,new FrameLayout.LayoutParams(-1,-1));
        after=new ImageView(activity);after.setScaleType(ImageView.ScaleType.FIT_XY);after.setVisibility(View.GONE);card.addView(after,new FrameLayout.LayoutParams(-1,-1));
        cover=new View(activity);cover.setFocusable(true);cover.setFocusableInTouchMode(true);cover.setSoundEffectsEnabled(false);cover.setContentDescription("Open your live 3D home");card.addView(cover,new FrameLayout.LayoutParams(-1,-1));

        statusRow=new LinearLayout(activity);statusRow.setGravity(Gravity.CENTER);
        dot=new View(activity);dot.setBackground(round(0xFF34C759,3));LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(px(6),px(6));dp.rightMargin=px(8);statusRow.addView(dot,dp);
        status=text("Connecting to House Control…",12.5f,ink(0xB8));status.setSingleLine(true);statusRow.addView(status,new LinearLayout.LayoutParams(-2,-2));
        addView(statusRow,pos(CARD_W,20,CARD_X,STATUS_Y));
        openHint=text("Open House Control",12.5f,callbacks.light()?Color.WHITE:0xFF1D1D1F);openHint.setGravity(Gravity.CENTER);openHint.setPadding(px(14),0,px(14),0);
        openHint.setBackground(round(callbacks.light()?0xFF1D1D1F:0xF7FFFFFF,13));openHint.setAlpha(0);
        LayoutParams hp=new LayoutParams(-2,px(26));hp.gravity=Gravity.TOP|Gravity.LEFT;addView(openHint,hp);
        openHint.post(()->{openHint.setTranslationX(px(CARD_X+CARD_W/2)-openHint.getWidth()/2f);openHint.setTranslationY(px(STATUS_Y+10)-openHint.getHeight()/2f);});

        card.setPivotX(px(CARD_W/2));card.setPivotY(px(H*.62f));
        Bitmap kept=lastPicture[callbacks.light()?1:0];
        if(kept!=null){shown=true;still.setImageBitmap(kept);still.setVisibility(View.VISIBLE);tvX=lastTvX;tvY=lastTvY;}
        cover.setOnClickListener(v->{callbacks.activate();callbacks.open();});
        cover.setOnFocusChangeListener((v,focus)->{
            if(focus)callbacks.focusHome(title,status.getText().toString());
            card.animate().cancel();ground.animate().cancel();openHint.animate().cancel();statusRow.animate().cancel();
            float s=focus?1.06f:1;
            if(callbacks.motion()){
                card.animate().scaleX(s).scaleY(s).translationY(focus?-px(4):0).setDuration(focus?520:360).setInterpolator(focus?spring:ease).start();
                ground.animate().scaleX(focus?1.08f:1).alpha(focus?.75f:1).setDuration(420).setInterpolator(ease).start();
                openHint.animate().alpha(focus?1:0).setDuration(focus?260:160).setStartDelay(focus?60:0).start();
                statusRow.animate().alpha(focus?0:1).setDuration(focus?140:260).setStartDelay(focus?0:80).start();
            } else {card.setScaleX(s);card.setScaleY(s);openHint.setAlpha(focus?1:0);statusRow.setAlpha(focus?0:1);}
        });
        cover.setOnKeyListener((v,key,event)->{if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;if(key==KeyEvent.KEYCODE_DPAD_DOWN){callbacks.down(centerX(card));return true;}if(key==KeyEvent.KEYCODE_DPAD_UP){callbacks.up();return true;}if(key==KeyEvent.KEYCODE_DPAD_LEFT){focusLastScene();return true;}return key==KeyEvent.KEYCODE_DPAD_RIGHT;});
        renderScenes(null);
    }
    int px(float x){return Math.round(x*scale);}
    int ink(int alpha){return (alpha<<24)|(callbacks.light()?0x1D1D1F:0xFFFFFF);}
    int screenW(){return activity.getResources().getDisplayMetrics().widthPixels;}
    int screenH(){return activity.getResources().getDisplayMetrics().heightPixels;}
    // One buffer size for the model and the flight: no reallocation (and no stall) when the portal starts.
    double widgetResolution(){return Math.min(1.0,Math.max(.6,(CARD_W*scale*1.15)/screenW()));}
    LayoutParams pos(float w,float h,float x,float y){LayoutParams p=new LayoutParams(px(w),px(h));p.leftMargin=px(x);p.topMargin=px(y);return p;}
    TextView text(String value,float size,int color){TextView t=new TextView(activity);t.setText(value);t.setTextColor(color);t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(size));t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));t.setIncludeFontPadding(false);return t;}
    GradientDrawable round(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(px(radius));return d;}

    /** A model was built (first load or a new plan): its first picture fades in, with no empty box or loading text. */
    void modelReady(){
        if(!attached)return;
        capture(true,b->{
            if(b==null||portal)return;
            if(shown){crossfade(b,500);return;}
            shown=true;still.setImageBitmap(b);still.setVisibility(View.VISIBLE);still.animate().cancel();
            if(callbacks.motion()){still.setAlpha(0f);still.setTranslationY(px(10));still.animate().alpha(1).translationY(0).setDuration(700).setInterpolator(ease).start();}
            else still.setAlpha(1f);
        });
    }
    /** The lights changed: a new picture of the settled model replaces the old one. */
    final Runnable repaint=()->{if(!attached||!shown||portal||commandBusy)return;capture(true,b->{if(b!=null&&!portal&&!commandBusy)crossfade(b,600);});};
    void crossfade(Bitmap b,int ms){
        after.animate().cancel();after.setImageBitmap(b);after.setAlpha(0f);after.setVisibility(View.VISIBLE);
        after.animate().alpha(1).setDuration(callbacks.motion()?ms:0).setInterpolator(ease).withEndAction(()->{still.setImageBitmap(b);after.setVisibility(View.GONE);}).start();
    }

    /** One scene: a round glass button with a glyph and its name underneath; white when focused, warm when active. */
    final class SceneTile extends FrameLayout {
        final String id,name,icon;final View disc;final SceneIcon glyph;final TextView label;boolean selected,focused;
        SceneTile(String id,String name,String icon){
            super(activity);this.id=id;this.name=name;this.icon=icon;setFocusable(true);setFocusableInTouchMode(true);setSoundEffectsEnabled(false);setClipChildren(false);
            setContentDescription(name+" scene");
            float inset=(SCENE_W-SCENE_D)/2;
            disc=new View(activity);addView(disc,pos(SCENE_D,SCENE_D,inset,0));
            glyph=new SceneIcon(activity);glyph.setKind(icon);addView(glyph,pos(26,26,(SCENE_W-26)/2,(SCENE_D-26)/2));
            label=text(name,12.5f,ink(0xE6));label.setGravity(Gravity.CENTER);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);addView(label,pos(SCENE_W,16,0,SCENE_D+10));
            setPivotX(px(SCENE_W/2));setPivotY(px(SCENE_D/2));
            paint();
            setOnFocusChangeListener((v,f)->{focused=f;paint();if(f)callbacks.focusHome(name,callbacks.sceneDetail(id,name));
                animate().cancel();float s=f?1.14f:1;if(callbacks.motion())animate().scaleX(s).scaleY(s).setDuration(f?460:300).setInterpolator(f?spring:ease).start();else{setScaleX(s);setScaleY(s);}
                // A raised view draws over its flat siblings: the glyph rises with the disc to stay on top of it.
                disc.setElevation(px(f?12:0));glyph.setElevation(px(f?12.5f:0));});
            setOnClickListener(v->{callbacks.activate();press();runScene(id,name);});
        }
        void press(){if(!callbacks.motion())return;animate().cancel();animate().scaleX(1.04f).scaleY(1.04f).setDuration(70).setInterpolator(ease).withEndAction(()->animate().scaleX(focused?1.14f:1).scaleY(focused?1.14f:1).setDuration(420).setInterpolator(spring).start()).start();}
        void paint(){
            boolean light=callbacks.light();
            int fill=focused?(light?0xFF1D1D1F:0xFAFFFFFF):selected?(light?0xFFFFE6BF:0x3DFFC46B):(light?0x8CFFFFFF:0x24FFFFFF);
            GradientDrawable bg=new GradientDrawable();bg.setShape(GradientDrawable.OVAL);bg.setColor(fill);
            if(!focused)bg.setStroke(Math.max(1,px(.8f)),selected?(light?0x66D08A2E:0x8CFFC46B):(light?0x14000000:0x1FFFFFFF));
            disc.setBackground(bg);
            int glyphInk=focused?(light?Color.WHITE:0xFF1D1D1F):selected?(light?0xFFB0640A:0xFFFFC46B):ink(0xF2);glyph.setColor(glyphInk);
            label.setTextColor(selected&&!focused?(light?0xFFA35A06:0xFFFFD08A):ink(focused?0xFF:0xD9));
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
    void setStatus(String value,boolean ok){if(!value.contentEquals(status.getText()))status.setText(value);if(ok!=live||dot.getBackground()==null){live=ok;dot.setBackground(round(ok?0xFF34C759:0xFFF0A94A,3));}}
    void update(JSONObject home){
        if(home.has("error")){setStatus(home.optString("error"),false);return;}current=home;
        String name=home.optJSONObject("plan")==null?"":home.optJSONObject("plan").optString("name");
        title=home.optBoolean("demo")?(name.isEmpty()?"Demo home":name):"Your home";
        JSONObject state=home.optJSONObject("state"),lights=state==null?null:state.optJSONObject("lights");int on=0,total=lights==null?0:lights.length();if(lights!=null){Iterator<String> keys=lights.keys();while(keys.hasNext())if(lights.optJSONObject(keys.next()).optBoolean("on"))on++;}
        boolean ready=home.optBoolean("controlReady");String lit=on==0?"All lights off":on+" of "+total+" lights on";
        if(System.currentTimeMillis()<warningUntil)setStatus("Some devices did not respond",false);
        else if(!ready)setStatus("Connect Smart Life in House Control",false);
        else if(!home.optString("cloudError").isEmpty())setStatus(lit+" · Reconnecting",false);
        else if(home.optLong("lastPoll")==0)setStatus("Updating from Smart Life…",true);
        else setStatus(home.optBoolean("demo")?lit+" · Demo":lit,true);
        if(!revision.equals(home.optString("revision"))){revision=home.optString("revision");renderScenes(home.optJSONObject("plan"));}
        String selected=state==null?"":state.optString("scene");for(SceneTile t:scenes){boolean s=t.id.equals(selected);if(s!=t.selected){t.selected=s;t.paint();}}
        model.evaluateJavascript("window.widgetRefresh && window.widgetRefresh()",null);
        // Polls change timestamps every few seconds; only a change the picture shows (lights, ACs) repaints it.
        String look=String.valueOf(state);if(!look.equals(pictured)){pictured=look;handler.removeCallbacks(repaint);handler.postDelayed(repaint,250);}
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
        final boolean demo=current.optBoolean("demo");worker.execute(()->{try{JSONObject result=transact(demo?4:2,id);handler.post(()->{if(!attached)return;commandBusy=false;if(result.has("error")){warningUntil=System.currentTimeMillis()+15000;setStatus("Some devices did not respond",false);Toast.makeText(activity,result.optString("error"),Toast.LENGTH_LONG).show();}else{warningUntil=0;JSONObject home=result.optJSONObject("home");if(home!=null){snapshot=home.toString();update(home);}handler.removeCallbacks(repaint);reveal(()->callbacks.sceneApplied(id));}for(SceneTile t:scenes)t.paint();if(result.has("error"))handler.post(repaint);fetch();});}catch(Exception error){handler.post(()->{commandBusy=false;for(SceneTile t:scenes)t.paint();if(attached)Toast.makeText(activity,"Could not apply scene. Open House Control.",Toast.LENGTH_LONG).show();});}});
    }
    float tvX=.5f,tvY=.5f;
    /** Reads the model as a picture (with transparency). settled=true jumps light fades to their end first. */
    void capture(boolean settled,java.util.function.Consumer<Bitmap> done){
        model.evaluateJavascript("window.widgetSnapshot && window.widgetSnapshot("+settled+")",value->{
            if(value==null||value.length()<40){done.accept(null);return;}
            worker.execute(()->{Bitmap bitmap=null;try{
                JSONObject o=new JSONObject(new JSONTokener(value).nextValue().toString());
                String data=o.optString("image");byte[] bytes=android.util.Base64.decode(data.substring(data.indexOf(',')+1),android.util.Base64.DEFAULT);
                bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);JSONObject tv=o.optJSONObject("tv");
                if(tv!=null){tvX=(float)tv.optDouble("x",.5);tvY=(float)tv.optDouble("y",.5);}
            }catch(Exception ignored){}final Bitmap result=bitmap;
            handler.post(()->{if(attached&&result!=null){lastPicture[callbacks.light()?1:0]=result;lastTvX=tvX;lastTvY=tvY;}done.accept(attached?result:null);});});
        });
    }
    /** Shows the scene's light state with a soft cross-fade; done once it has landed. */
    void reveal(Runnable done){
        if(!shown){done.run();return;}
        capture(true,b->{if(b==null){done.run();return;}
            after.animate().cancel();after.setImageBitmap(b);after.setAlpha(0f);after.setVisibility(View.VISIBLE);
            after.animate().alpha(1).setDuration(callbacks.motion()?650:0).setInterpolator(ease).withEndAction(()->{still.setImageBitmap(b);after.setVisibility(View.GONE);done.run();}).start();});
    }
    /** The model's frame becomes the screen and pushes in toward the modelled TV; a dark room fades in behind it. */
    void portalToTV(int duration){
        portal=true;
        card.animate().cancel();card.setScaleX(1);card.setScaleY(1);card.setTranslationY(0);
        int[] location=new int[2];card.getLocationOnScreen(location);setTranslationZ(px(60));card.setPivotX(0);card.setPivotY(0);
        openHint.animate().alpha(0).setDuration(160).start();statusRow.animate().alpha(0).setDuration(160).start();ground.animate().alpha(0).setDuration(240).start();
        PathInterpolator glide=new PathInterpolator(.6f,0f,.2f,1f);
        float sx=screenW()/(float)card.getWidth(),sy=screenH()/(float)card.getHeight();
        backdrop.animate().alpha(1).setDuration(Math.round(duration*.45f)).setInterpolator(ease).start();
        still.setPivotX(tvX*card.getWidth());still.setPivotY(tvY*card.getHeight());
        still.animate().scaleX(2.4f).scaleY(2.4f).setDuration(duration).setInterpolator(new PathInterpolator(.5f,0f,.3f,1f)).withLayer().start();
        card.animate().translationX(-location[0]).translationY(-location[1]).scaleX(sx).scaleY(sy).setDuration(duration).setInterpolator(glide).withLayer().start();
    }
    /** Scenes and status step back while the model takes the screen. */
    void fadeControls(int ms){for(View v:new View[]{row,statusRow})v.animate().alpha(0).setDuration(ms).withLayer().start();}
    void resetModelTransform(){model.animate().cancel();model.setScaleX(px(CARD_W)/(float)screenW());model.setScaleY(px(H)/(float)screenH());model.setTranslationY(0);}
    void resetPortal(){
        portal=false;still.animate().cancel();still.setScaleX(1);still.setScaleY(1);still.setAlpha(1f);still.setTranslationY(0);after.setVisibility(View.GONE);
        for(View v:new View[]{row,statusRow,ground}){v.animate().cancel();v.setAlpha(1);}
        backdrop.animate().cancel();backdrop.setAlpha(0);
        card.animate().cancel();card.setScaleX(1);card.setScaleY(1);card.setTranslationX(0);card.setTranslationY(0);card.setPivotX(px(CARD_W/2));card.setPivotY(px(H*.62f));
        setTranslationZ(0);resetModelTransform();openHint.setAlpha(0);
        if(cover.hasFocus()){card.setScaleX(1.06f);card.setScaleY(1.06f);card.setTranslationY(-px(4));openHint.setAlpha(1);statusRow.setAlpha(0);}
        model.evaluateJavascript("window.widgetResetCamera && window.widgetResetCamera()",null);
    }
    void focusFirstScene(){if(!scenes.isEmpty())scenes.get(0).requestFocus();else cover.requestFocus();}
    void focusLastScene(){if(!scenes.isEmpty())scenes.get(scenes.size()-1).requestFocus();}
    float centerX(View v){int[] l=new int[2];v.getLocationOnScreen(l);return l[0]+v.getWidth()/2f;}
    /** Up from a favourite: the scene or model nearest above it. */
    void focusNearest(float x){View best=cover;float d=Math.abs(centerX(card)-x);for(SceneTile t:scenes){float e=Math.abs(centerX(t)-x);if(e<d){d=e;best=t;}}best.requestFocus();}
    void focusScene(int index){if(scenes.isEmpty()){cover.requestFocus();return;}if(index>=scenes.size())cover.requestFocus();else scenes.get(Math.max(0,index)).requestFocus();}
}
