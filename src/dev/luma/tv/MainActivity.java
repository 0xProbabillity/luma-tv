package dev.luma.tv;

import android.app.*;
import android.animation.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** An offline, remote-first home screen. No network, analytics, or accessibility service. */
public final class MainActivity extends Activity {
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<App> apps = new ArrayList<>();
    private final ArrayList<View> tabs = new ArrayList<>();
    private final ArrayList<Tile> tiles = new ArrayList<>();
    private SharedPreferences prefs;
    private SonicFeedback sonic;
    private HouseWidgets houseWidgets;
    private FrameLayout root, content, hero;
    private final ArrayList<Animator> pageAnimations=new ArrayList<>();
    private View sceneryShade;
    private Wallpaper wallpaper;
    private TextView heroTitle, heroDescription, clock;
    private String heroKey="";
    private boolean holdGreeting;
    private ScrollView scroll;
    private LinearLayout rows;
    private String page = "home", selectedPackage = "com.google.android.youtube.tv";
    private JSONObject lastHome;
    private View sceneVeil;
    private TextView brand;
    private boolean sceneTransition;
    private boolean loaded, loading, started, homeGreeting=true;
    private float unit = 1f;
    private int lastDirectionX=1, lastDirectionY=0;
    private String heroPackage="";
    private final android.view.animation.Interpolator ease = new PathInterpolator(.22f,1f,.36f,1f);
    private final android.view.animation.Interpolator drift = new PathInterpolator(.2f,.72f,.25f,1f);
    private final android.view.animation.Interpolator spring = t -> {
        if(t>=1)return 1;
        double time=t*.42,frequency=25,damping=.72;
        double damped=frequency*Math.sqrt(1-damping*damping);
        return (float)(1-Math.exp(-damping*frequency*time)*(Math.cos(damped*time)+damping*frequency/damped*Math.sin(damped*time)));
    };
    private final Runnable clockTick = new Runnable() {
        public void run() {
            if (!started) return;
            if (clock != null) clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
            handler.postDelayed(this, 15000);
        }
    };
    private static final String[] FIRST = {"com.google.android.youtube.tv", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.disney.disneyplus", "com.spotify.tv.android", "com.google.android.youtube.tvmusic"};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("luma", MODE_PRIVATE);
        unit = getResources().getDisplayMetrics().widthPixels / 960f;
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        sonic=new SonicFeedback(this);
        createShell();
        discover();
    }

    @Override protected void onResume() {
        super.onResume();
        started = true;
        resetSceneTransition();
        handler.removeCallbacks(clockTick);
        handler.post(clockTick);
        if(loaded && page.equals("home")){selectedPackage="com.google.android.youtube.tv";showGreeting();root.post(()->{holdGreeting=true;focusFirst();});}
        if (loaded) discover();
        wallpaper.syncAmbient();
        if(houseWidgets!=null)houseWidgets.setActive(true);
    }
    @Override protected void onPause() {
        super.onPause(); if(houseWidgets!=null)houseWidgets.setActive(false); started = false; handler.removeCallbacks(clockTick);
        for(Tile tile:tiles) tile.movement.snap();
        wallpaper.pauseAmbient();wallpaper.rest();
    }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused);if(wallpaper!=null)wallpaper.syncAmbient(); }
    @Override protected void onDestroy() { if(wallpaper!=null){wallpaper.pauseAmbient();wallpaper.surfaceThread.quitSafely();}super.onDestroy(); handler.removeCallbacksAndMessages(null); loader.shutdownNow(); if(sonic!=null)sonic.release(); }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasCategory(Intent.CATEGORY_HOME)){selectedPackage="com.google.android.youtube.tv";showPage("home", true);}
    }

    @Override public void dump(String prefix,FileDescriptor fd,PrintWriter writer,String[] args) {
        super.dump(prefix,fd,writer,args);
        if(wallpaper!=null)writer.println(prefix+"Luma ambient: running="+wallpaper.ambientRunning+" ticks="+wallpaper.ambientTicks+" seconds="+wallpaper.ambientSeconds+" style="+wallpaper.requestedStyle+" page="+page);
    }
    private int px(float value) { return Math.round(value * unit); }
    private boolean motion() { return prefs.getBoolean("motion", true) && ValueAnimator.areAnimatorsEnabled(); }
    private void sound() { if (prefs.getBoolean("sound", true)) sonic.open(); }
    private void navigationSound(int index) { if(prefs.getBoolean("sound",true))sonic.step((index%6)/5f,lastDirectionX+lastDirectionY); }
    private TextView text(String value, float size, int color, String family) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextColor(color);
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(size));
        view.setTypeface(Typeface.create(family, Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        return view;
    }
    private FrameLayout.LayoutParams pos(float w, float h, float x, float y) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w < 0 ? (int)w : px(w), h < 0 ? (int)h : px(h));
        lp.leftMargin = px(x); lp.topMargin = px(y); return lp;
    }
    private GradientDrawable shape(int color, float radius) {
        GradientDrawable gd = new GradientDrawable(); gd.setColor(color); gd.setCornerRadius(px(radius)); return gd;
    }

    private void createShell() {
        root = new FrameLayout(this); root.setClipChildren(false); root.setClipToPadding(false);
        wallpaper = new Wallpaper(); root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));
        sceneryShade=new View(this);sceneryShade.setBackgroundColor(0x70080C14);sceneryShade.setAlpha(0);
        root.addView(sceneryShade,new FrameLayout.LayoutParams(-1,-1));
        content = new FrameLayout(this); content.setClipChildren(false); root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        brand = text("luma", 23, Color.WHITE, "sans-serif-medium");
        brand.setLetterSpacing(-.045f); root.addView(brand, pos(120, 32, 48, 29));
        LinearLayout navigation = new LinearLayout(this); navigation.setGravity(Gravity.CENTER);
        String[] titles = {"Home", "Apps", "Settings"};
        String[] pages = {"home", "apps", "settings"};
        for (int i=0;i<titles.length;i++) {
            final int index=i;
            TextView tab = text(titles[i], 13, 0xB3FFFFFF, "sans-serif-medium");
            tab.setPadding(px(17), px(9), px(17), px(9)); tab.setGravity(Gravity.CENTER);
            tab.setFocusable(true); tab.setFocusableInTouchMode(true); tab.setSoundEffectsEnabled(false);
            tab.setOnClickListener(v -> { sound(); showPage(pages[index], true); });
            // One pill slides between tabs; leaving the bar it settles, translucent, on the current page.
            tab.setOnFocusChangeListener((v, focus) -> { if(focus) moveTabIndicator(true); else tab.post(()->{ if(focusedTab()<0) moveTabIndicator(true); }); });
            tab.setOnKeyListener((v,key,e) -> {
                if (e.getAction()==KeyEvent.ACTION_DOWN && key==KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (!page.equals(pages[index])) showPage(pages[index], true); else if(index==0&&houseWidgets!=null)houseWidgets.focusFirstScene();else focusFirst();
                    return true;
                }
                return false;
            });
            tabs.add(tab); navigation.addView(tab, new LinearLayout.LayoutParams(-2, px(36)));
        }
        FrameLayout bar = new FrameLayout(this); bar.setClipChildren(false);
        tabIndicatorShape = shape(0x24FFFFFF, 18); tabIndicator = new View(this); tabIndicator.setBackground(tabIndicatorShape);
        bar.addView(tabIndicator, new FrameLayout.LayoutParams(0, px(36), Gravity.CENTER_VERTICAL));
        bar.addView(navigation, new FrameLayout.LayoutParams(-2, -1));
        bar.setPadding(px(4),px(2),px(4),px(2)); bar.setBackground(shape(0x1FFFFFFF,22));
        root.addView(bar, pos(-2, 40, 0, 26));
        bar.post(()->{ bar.setTranslationX((getResources().getDisplayMetrics().widthPixels-bar.getWidth())/2f); moveTabIndicator(false); });
        clock = text("", 17, 0xF2FFFFFF, "sans-serif-medium"); clock.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        root.addView(clock, pos(130, 36, 782, 28));
        setContentView(root);
        showPage("home", false);
    }

    private void discover() {
        if (loading) return;
        loading=true;
        loader.execute(() -> {
            ArrayList<App> found = new ArrayList<>();
            HashSet<String> seen = new HashSet<>();
            PackageManager pm = getPackageManager();
            for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
                for (ResolveInfo info : pm.queryIntentActivities(query, 0)) {
                    String pkg=info.activityInfo.packageName;
                    if (pkg.equals(getPackageName()) || Arrays.asList(STOCK_HOMES).contains(pkg) || !seen.add(pkg)) continue;
                    if (pm.getLaunchIntentForPackage(pkg)==null && pm.getLeanbackLaunchIntentForPackage(pkg)==null) continue;
                    try {
                        App app = new App(); app.pkg=pkg;
                        app.label=info.loadLabel(pm).toString();
                        app.icon=bitmap(info.loadIcon(pm), 120, 120);
                        app.color=colorFor(pkg);
                        app.description=descriptionFor(pkg);
                        app.tag=tagFor(pkg);
                        Drawable banner=info.activityInfo.applicationInfo.loadBanner(pm);
                        if (banner!=null) app.banner=bitmap(banner, 320, 180);
                        found.add(app);
                    } catch (RuntimeException ignored) { /* A package can disappear during an update. */ }
                }
            }
            Collections.sort(found, (a,b)->a.label.compareToIgnoreCase(b.label));
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                loading=false;
                boolean changed = !loaded || found.size()!=apps.size();
                if (!changed) for (int i=0;i<found.size();i++) if (!found.get(i).pkg.equals(apps.get(i).pkg)) { changed=true; break; }
                if (changed) { apps.clear(); apps.addAll(found); loaded=true; showPage(page, true); }
            });
        });
    }

    private Bitmap bitmap(Drawable drawable, int w, int h) {
        Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bitmap); drawable.setBounds(0,0,w,h); drawable.draw(c); return bitmap;
    }
    private ArrayList<String> order() {
        ArrayList<String> ids=new ArrayList<>();
        String saved=prefs.getString("order", "");
        if (!saved.isEmpty()) ids.addAll(Arrays.asList(saved.split(",")));
        for (String pkg:FIRST) if (!ids.contains(pkg)) ids.add(pkg);
        for (App app:apps) if (!ids.contains(app.pkg)) ids.add(app.pkg);
        return ids;
    }
    private ArrayList<App> visibleApps(boolean all) {
        ArrayList<App> result=new ArrayList<>();
        Set<String> hidden=prefs.getStringSet("hidden", Collections.emptySet());
        for (String id:order()) for (App app:apps) if (app.pkg.equals(id) && (all || !hidden.contains(id))) result.add(app);
        return result;
    }

    private void showPage(String next, boolean focus) {
        String previous=page;
        boolean transition=loaded && !previous.equals(next) && motion();
        for(Animator animation:pageAnimations)animation.cancel();pageAnimations.clear();
        for(Tile tile:tiles) tile.movement.stop();
        content.animate().cancel();content.setAlpha(1);content.setTranslationX(0);
        if(houseWidgets!=null&&houseWidgets.current!=null)lastHome=houseWidgets.current;
        content.removeAllViews();houseWidgets=null;content.setTag(null);
        page=next;homeGreeting=page.equals("home");heroPackage=homeGreeting?"@greeting":"";tiles.clear();
        clock.setVisibility(prefs.getBoolean("clock", true) ? View.VISIBLE : View.GONE);
        moveTabIndicator(transition);
        wallpaper.update();wallpaper.rest();wallpaper.syncAmbient();
        sceneryShade.animate().cancel();
        sceneryShade.setAlpha(page.equals("settings")?1:0);
        if (page.equals("settings")) { settings(focus); animatePage(previous,transition); return; }
        boolean home=page.equals("home");
        hero=new FrameLayout(this){@Override public boolean hasOverlappingRendering(){return false;}};
        heroTitle=text(home ? welcome() : "All your apps", home?40:44, Color.WHITE, "sans-serif-medium");
        heroTitle.setLetterSpacing(-.03f); heroTitle.setSingleLine(true); heroTitle.setEllipsize(android.text.TextUtils.TruncateAt.END); heroTitle.setShadowLayer(px(10),0,px(1),0x55000000);
        hero.addView(heroTitle,pos(home?440:860,52,0,0));
        heroDescription=text(home ? GREETING_LINE : "Everything installed on this TV.", 15, 0xD9FFFFFF, "sans-serif");
        heroDescription.setSingleLine(true); heroDescription.setEllipsize(android.text.TextUtils.TruncateAt.END); heroDescription.setShadowLayer(px(8),0,px(1),0x55000000); hero.addView(heroDescription,pos(home?440:860,22,1,56));
        content.addView(hero,pos(864,84,48,home?84:100));
        heroKey=home?"@greeting":"";
        if(home){
            houseWidgets=new HouseWidgets(this,unit,new HouseWidgets.Callbacks(){
                public void up(){tabs.get(0).requestFocus();}
                public void down(float x){focusNearestTile(x);}
                public void open(){try{startActivity(new Intent().setComponent(new ComponentName("dev.housecontrol.tv","dev.housecontrol.tv.MainActivity")));overridePendingTransition(0,0);}catch(Exception e){Toast.makeText(MainActivity.this,"Install House Control to connect your home.",Toast.LENGTH_SHORT).show();}}
                public void focusHome(String title,String detail){holdGreeting=false;homeGreeting=false;setHero("@home:"+title,title,detail);navigationSound(2);}
                public void activate(){sound();}
                public boolean sceneApplied(String id){return MainActivity.this.sceneApplied(id);}
                public boolean motion(){return MainActivity.this.motion();}
                public String sceneDetail(String id,String name){return MainActivity.this.sceneDetail(id);}
            });
            content.addView(houseWidgets,pos(864,164,48,178));
        }
        scroll=new ScrollView(this); scroll.setFillViewport(false); scroll.setClipChildren(false); scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false); scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rows=new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL); rows.setClipChildren(false); rows.setClipToPadding(false);
        rows.setPadding(px(48),px(10),px(48),px(30));
        scroll.addView(rows,new ScrollView.LayoutParams(-1,-2));
        content.addView(scroll,pos(960, home ? 180 : 330,0,home ? 358 : 200));
        ArrayList<App> list=visibleApps(!home);
        if (!loaded) {
            rows.addView(text("Finding your apps…",15,0xBBFFFFFF,"sans-serif"));
        } else if (list.isEmpty()) {
            rows.addView(text("Your home is empty. Find your apps in the Apps tab.",15,0xBBFFFFFF,"sans-serif"));
        } else {
            int count=home ? Math.min(6,list.size()) : list.size();
            for (int start=0;start<count;start+=6) {
                if (home && start==0) {
                    TextView heading=text("Favorites",13,0xE6FFFFFF,"sans-serif-medium");
                    LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,px(26)); rows.addView(heading,hp);
                }
                LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false); row.setClipToPadding(false); row.setGravity(Gravity.TOP);
                for (int i=start;i<Math.min(start+6,count);i++) {
                    App app=list.get(i); Tile tile=new Tile(app,i);
                    LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(px(132),px(108));
                    if (i>start) tp.leftMargin=px(14.4f);
                    row.addView(tile,tp); tiles.add(tile);
                }
                rows.addView(row,new LinearLayout.LayoutParams(-1,px(112)));
            }
        }
        if (focus) root.post(()->{holdGreeting=page.equals("home");focusFirst();});
        animatePage(previous,transition);
    }

    private void animatePage(String previous,boolean transition) {
        if(!transition)return;
        int before=previous.equals("home")?0:previous.equals("apps")?1:2;
        int after=page.equals("home")?0:page.equals("apps")?1:2;
        float direction=after>=before?1:-1;
        // Animate real element bounds rather than blending two full-screen textures.
        // This preserves the soft page entrance within a small TV chip's fill-rate budget.
        ArrayList<View> entering=new ArrayList<>();
        for(int i=0;i<content.getChildCount();i++)if(!(content.getChildAt(i) instanceof ScrollView)&&!"scroll-frame".equals(content.getChildAt(i).getTag()))entering.add(content.getChildAt(i));
        if(page.equals("settings")) {
            Object tag=content.getTag();if(tag instanceof ArrayList)for(Object row:(ArrayList<?>)tag)if(row instanceof View)entering.add((View)row);
        } else {
            for(int i=0;i<rows.getChildCount();i++)if(rows.getChildAt(i) instanceof TextView)entering.add(rows.getChildAt(i));
            entering.addAll(tiles);
        }
        for(int i=0;i<entering.size();i++) {
            View view=entering.get(i);float offset=px(16)*direction;
            view.setAlpha(0);view.setTranslationX(offset);
            ObjectAnimator fade=ObjectAnimator.ofFloat(view,View.ALPHA,0,1);fade.setDuration(270);
            ObjectAnimator slide=ObjectAnimator.ofFloat(view,View.TRANSLATION_X,offset,0);slide.setDuration(340);
            AnimatorSet set=new AnimatorSet();set.playTogether(fade,slide);set.setInterpolator(ease);set.setStartDelay(Math.min(i,5)*10);
            pageAnimations.add(set);set.start();
        }
    }

    private void focusFirst() {
        for (Tile tile:tiles) if (tile.app.pkg.equals(selectedPackage)) { tile.requestFocus(); return; }
        if (!tiles.isEmpty()) tiles.get(0).requestFocus();
    }
    private static final String GREETING_LINE="Make yourself at home.";
    /** Set at build time: LUMA_GREETING or .local/greeting.txt (see build.sh). */
    private String welcome(){return getString(R.string.greeting);}
    private String greeting(){int h=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);return h<5?"Good night":h<12?"Good morning":h<18?"Good afternoon":"Good evening";}
    private String today(){return new SimpleDateFormat("EEEE, MMMM d",Locale.ENGLISH).format(new Date());}
    private void focusNearestTile(float x){
        Tile best=null;float d=Float.MAX_VALUE;int[] l=new int[2];
        for(Tile t:tiles){if(t.index>=6)break;t.getLocationOnScreen(l);float e=Math.abs(l[0]+t.getWidth()/2f-x);if(e<d){d=e;best=t;}}
        if(best!=null){lastDirectionY=1;lastDirectionX=0;best.requestFocus();}
    }
    private String sceneDetail(String id){
        String app=sceneApp(id),opens=app.isEmpty()?"":" · Opens "+appLabel(app);
        switch(id){
            case "bright":return "Every light, full brightness"+opens;
            case "cozy":return "Warm, low light for the evening"+opens;
            case "movie":return "Lights down, warm glow behind the TV"+opens;
            case "night":return "Only the bedside lamps"+opens;
            case "away":return "Everything off while you are out"+opens;
            default:return "Set the mood in one press"+opens;
        }
    }
    private void showGreeting() {
        homeGreeting=true;
        if(heroTitle==null || !page.equals("home"))return;
        setHero("@greeting",welcome(),GREETING_LINE);
    }
    /** Cross-fades the hero to a new title and line. */
    private void setHero(String key,String title,String detail){
        if(heroTitle==null||key.equals(heroKey))return;heroKey=key;
        final TextView t=heroTitle,d=heroDescription;t.animate().cancel();d.animate().cancel();
        if(!motion()){t.setText(title);d.setText(detail);t.setAlpha(1);d.setAlpha(1);t.setTranslationY(0);d.setTranslationY(0);return;}
        t.animate().alpha(0).translationY(-px(4)).setDuration(90).withEndAction(()->{
            if(heroTitle!=t||!heroKey.equals(key))return;
            t.setText(title);d.setText(detail);t.setTranslationY(px(8));d.setTranslationY(px(5));d.setAlpha(0);
            t.animate().alpha(1).translationY(0).setDuration(300).setInterpolator(ease).start();
            d.animate().alpha(1).translationY(0).setDuration(360).setStartDelay(30).setInterpolator(ease).start();
        }).start();
        d.animate().alpha(0).setDuration(90).start();
    }
    private void selectApp(App app) {
        selectedPackage=app.pkg;
        if(holdGreeting){holdGreeting=false;return;}
        if(heroTitle==null || !page.equals("home")) return;
        homeGreeting=false;setHero(app.pkg,app.label,app.description);
    }

    private void launch(App app) {
        try {
            Intent intent=getPackageManager().getLeanbackLaunchIntentForPackage(app.pkg);
            if (intent==null) intent=getPackageManager().getLaunchIntentForPackage(app.pkg);
            if (intent==null) throw new ActivityNotFoundException();
            sound(); startActivity(intent);overridePendingTransition(0,0);
        } catch (RuntimeException error) { Toast.makeText(this,"Could not open this app. It may be updating.",Toast.LENGTH_SHORT).show(); discover(); }
    }

    private String sceneApp(String scene){try{JSONObject saved=new JSONObject(prefs.getString("scene_app_packages","{}"));if(saved.has(scene))return saved.optString(scene);}catch(Exception ignored){}return scene.equals("movie")?"com.netflix.ninja":"";}
    private String appLabel(String pkg){if(pkg.isEmpty())return "None";for(App app:apps)if(app.pkg.equals(pkg))return app.label;return pkg;}
    /* ---------- overlays: menus, notices and the wallpaper picker (no stock dialogs) ---------- */
    private View nav(){return (View)tabs.get(0).getParent().getParent();}
    private View tabIndicator;private GradientDrawable tabIndicatorShape;private ValueAnimator tabMove;private int tabColor=0x24FFFFFF;
    private int focusedTab(){for(int i=0;i<tabs.size();i++)if(tabs.get(i).hasFocus())return i;return -1;}
    private int pageTab(){return page.equals("apps")?1:page.equals("settings")?2:0;}
    /** Slides the tab pill under the focused tab (white), or the current page's tab (glass) when focus is elsewhere. */
    private void moveTabIndicator(boolean animate){
        if(tabIndicator==null||tabs.isEmpty())return;
        int focused=focusedTab(),index=focused>=0?focused:pageTab();View target=tabs.get(index),row=(View)target.getParent();
        if(target.getWidth()==0){target.post(()->moveTabIndicator(false));return;}
        final float fromX=tabIndicator.getTranslationX(),toX=row.getLeft()+target.getLeft()-((View)row.getParent()).getPaddingLeft();
        final int fromW=tabIndicator.getLayoutParams().width,toW=target.getWidth(),fromColor=tabColor,toColor=focused>=0?0xF5FFFFFF:0x24FFFFFF;
        final int[] fromText=new int[tabs.size()],toText=new int[tabs.size()];
        for(int i=0;i<tabs.size();i++){fromText[i]=((TextView)tabs.get(i)).getCurrentTextColor();toText[i]=i==focused?0xFF17202B:i==pageTab()?Color.WHITE:0xB3FFFFFF;}
        if(tabMove!=null)tabMove.cancel();
        ArgbEvaluator argb=new ArgbEvaluator();
        ValueAnimator.AnimatorUpdateListener step=a->{float f=(float)a.getAnimatedValue();
            tabIndicator.setTranslationX(fromX+(toX-fromX)*f);ViewGroup.LayoutParams lp=tabIndicator.getLayoutParams();lp.width=Math.round(fromW+(toW-fromW)*f);tabIndicator.setLayoutParams(lp);
            tabColor=(int)argb.evaluate(f,fromColor,toColor);tabIndicatorShape.setColor(tabColor);
            float t=Math.max(0,Math.min(1,(f-.12f)/.3f)); // text flips quickly, so it never matches the pill mid-way
            for(int i=0;i<tabs.size();i++)((TextView)tabs.get(i)).setTextColor((int)argb.evaluate(t,fromText[i],toText[i]));};
        tabMove=ValueAnimator.ofFloat(0,1);tabMove.addUpdateListener(step);
        if(!animate||!motion()||fromW==0){tabMove.setDuration(0);}else{tabMove.setDuration(280);tabMove.setInterpolator(ease);}
        tabMove.start();
    }
    private FrameLayout overlay;private View overlayReturn;private Runnable overlayCancel;
    private void closeOverlay(boolean cancelled){
        if(overlay==null)return;final FrameLayout o=overlay;overlay=null;Runnable c=overlayCancel;overlayCancel=null;if(cancelled&&c!=null)c.run();
        trapFocus(false);
        // A fade's end action is skipped when the animation is cancelled, which left the picker stuck over Home:
        // the closing overlay takes no focus, and is removed on a timer, not by the animation.
        o.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);o.animate().cancel();
        if(motion()){o.animate().alpha(0).setDuration(180).start();root.postDelayed(()->root.removeView(o),200);}else root.removeView(o);
        if(overlayReturn!=null){View r=overlayReturn;overlayReturn=null;root.post(r::requestFocus);}
    }
    private void trapFocus(boolean on){int mode=on?ViewGroup.FOCUS_BLOCK_DESCENDANTS:ViewGroup.FOCUS_BEFORE_DESCENDANTS;content.setDescendantFocusability(mode);((ViewGroup)nav()).setDescendantFocusability(mode);}
    private FrameLayout openOverlay(int scrim){
        // One overlay replacing another (About → Support) keeps the original place to return to.
        View back=overlay!=null?overlayReturn:getCurrentFocus();overlayReturn=null;closeOverlay(false);overlayReturn=back;
        trapFocus(true); // the page and tabs behind a dialog take no focus while it is open
        overlay=new FrameLayout(this);overlay.setBackgroundColor(scrim);overlay.setClipChildren(false);overlay.setTranslationZ(px(120));
        root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        if(motion()){overlay.setAlpha(0);overlay.animate().alpha(1).setDuration(220).setInterpolator(ease).start();}
        return overlay;
    }
    private TextView menuRow(LinearLayout list,String label,boolean checked,Runnable action){
        TextView row=text(label,15,Color.WHITE,"sans-serif-medium");row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(px(18),0,px(18),0);
        row.setSingleLine(true);row.setEllipsize(android.text.TextUtils.TruncateAt.END);row.setFocusable(true);row.setFocusableInTouchMode(true);row.setSoundEffectsEnabled(false);
        if(checked)row.setCompoundDrawablePadding(px(8));
        Runnable paint=()->{boolean f=row.hasFocus();row.setBackground(f?shape(0xF7FFFFFF,12):null);row.setTextColor(f?0xFF111827:Color.WHITE);row.setText((checked?"✓  ":"")+label);};
        paint.run();
        row.setOnFocusChangeListener((v,f)->{paint.run();row.animate().cancel();if(motion())row.animate().scaleX(f?1.02f:1).scaleY(f?1.02f:1).setDuration(240).setInterpolator(ease).start();if(f)navigationSound(list.indexOfChild(row));});
        row.setOnClickListener(v->{sound();action.run();});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,px(42));lp.bottomMargin=px(4);list.addView(row,lp);return row;
    }
    /** A centred glass menu. pick receives the chosen index. */
    private void menu(String title,String[] items,int checked,java.util.function.IntConsumer pick){
        FrameLayout o=openOverlay(0xB3060910);
        FrameLayout panel=new FrameLayout(this);panel.setBackground(shape(0xF5161B26,24));panel.setElevation(px(24));
        int rows=Math.min(items.length,7);float h=74+rows*46+18;
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(px(400),px(h));lp.gravity=Gravity.CENTER;o.addView(panel,lp);
        TextView t=text(title,20,Color.WHITE,"sans-serif-medium");t.setSingleLine(true);t.setEllipsize(android.text.TextUtils.TruncateAt.END);panel.addView(t,pos(352,28,24,24));
        ScrollView sv=new ScrollView(this);sv.setVerticalScrollBarEnabled(false);sv.setClipToPadding(false);sv.setPadding(px(10),0,px(10),0);
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(px(4),px(4),px(4),px(10));list.setClipToPadding(false);list.setClipChildren(false);sv.addView(list);
        panel.addView(sv,pos(400,h-66,0,62));
        View first=null;
        for(int i=0;i<items.length;i++){final int index=i;View r=menuRow(list,items[i],i==checked,()->{closeOverlay(false);pick.accept(index);});if(i==Math.max(0,checked)||first==null&&i==0)first=r;}
        if(motion()){panel.setScaleX(.96f);panel.setScaleY(.96f);panel.animate().scaleX(1).scaleY(1).setDuration(360).setInterpolator(spring).start();}
        final View focus=first;root.post(()->{if(focus!=null)focus.requestFocus();});
    }
    private void notice(String title,String message){
        FrameLayout o=openOverlay(0xB3060910);
        FrameLayout panel=new FrameLayout(this);panel.setBackground(shape(0xF5161B26,24));panel.setElevation(px(24));
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(px(440),px(260));lp.gravity=Gravity.CENTER;o.addView(panel,lp);
        panel.addView(text(title,22,Color.WHITE,"sans-serif-medium"),pos(392,30,24,26));
        TextView body=text(message,14,0xC7FFFFFF,"sans-serif");body.setLineSpacing(px(5),1);panel.addView(body,pos(392,140,24,66));
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);panel.addView(list,pos(140,42,286,200));
        View done=menuRow(list,"Done",false,()->closeOverlay(false));((TextView)done).setGravity(Gravity.CENTER);root.post(done::requestFocus);
    }
    /** Full-screen wallpaper picker: moving between thumbnails previews each wallpaper live. */
    private void wallpaperPicker(){
        final int saved=prefs.getInt("wallpaper",0);
        FrameLayout o=openOverlay(Color.TRANSPARENT);
        overlayCancel=()->{wallpaper.show(saved);content.animate().alpha(1).setDuration(260).start();nav().animate().alpha(1).setDuration(260).start();sceneryShade.animate().alpha(1).setDuration(260).start();};
        content.animate().alpha(0).setDuration(240).start();nav().animate().alpha(0).setDuration(240).start();sceneryShade.animate().alpha(0).setDuration(300).start();
        View fade=new View(this);fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00060910,0x99060910,0xE6060910}));o.addView(fade,pos(960,260,0,280));
        TextView name=text(WALLPAPERS[saved],34,Color.WHITE,"sans-serif-medium");name.setLetterSpacing(-.02f);name.setShadowLayer(px(10),0,px(1),0x66000000);o.addView(name,pos(600,44,48,338));
        TextView hint=text("OK to choose  ·  Back to keep the current one",13,0xB3FFFFFF,"sans-serif");o.addView(hint,pos(600,20,49,386));
        LinearLayout row=new LinearLayout(this);row.setClipChildren(false);row.setClipToPadding(false);o.addView(row,pos(864,80,48,430));
        ArrayList<View> cells=new ArrayList<>();
        for(int i=0;i<WALLPAPERS.length;i++){
            final int index=i;
            FrameLayout cell=new FrameLayout(this);cell.setFocusable(true);cell.setFocusableInTouchMode(true);cell.setSoundEffectsEnabled(false);cell.setClipToOutline(true);
            cell.setBackground(shape(0xFF101521,12));
            ImageView img=new ImageView(this);img.setScaleType(ImageView.ScaleType.CENTER_CROP);img.setImageResource(THUMBS[i]);cell.addView(img,new FrameLayout.LayoutParams(-1,-1));
            GradientDrawable ring=shape(Color.TRANSPARENT,12);ring.setStroke(px(1.5f),i==saved?0xCCFFFFFF:0x33FFFFFF);cell.setForeground(ring);
            cell.setOnFocusChangeListener((v,f)->{
                GradientDrawable r=shape(Color.TRANSPARENT,12);r.setStroke(px(f?2:1.5f),f?Color.WHITE:index==saved?0xCCFFFFFF:0x33FFFFFF);cell.setForeground(r);cell.setElevation(px(f?18:2));
                cell.animate().cancel();float s=f?1.16f:1;if(motion())cell.animate().scaleX(s).scaleY(s).translationY(f?-px(4):0).setDuration(420).setInterpolator(spring).start();else{cell.setScaleX(s);cell.setScaleY(s);}
                if(f){name.setText(WALLPAPERS[index]);wallpaper.show(index);navigationSound(index);}
            });
            cell.setOnClickListener(v->{sound();prefs.edit().putInt("wallpaper",index).apply();overlayCancel=null;closeOverlay(false);
                content.animate().alpha(1).setDuration(260).start();nav().animate().alpha(1).setDuration(260).start();sceneryShade.animate().alpha(1).setDuration(260).start();showPage("settings",false);controlsFocus(0);});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(px(112),px(63));if(i>0)lp.leftMargin=px(12);row.addView(cell,lp);cells.add(cell);
        }
        root.post(()->cells.get(saved).requestFocus());
    }

    /** Non-comment lines of a res/raw text file, split on "|". */
    private ArrayList<String[]> rawLines(int id){
        ArrayList<String[]> out=new ArrayList<>();
        try(java.io.BufferedReader r=new java.io.BufferedReader(new java.io.InputStreamReader(getResources().openRawResource(id),java.nio.charset.StandardCharsets.UTF_8))){
            String line;while((line=r.readLine())!=null){line=line.trim();if(!line.isEmpty()&&!line.startsWith("#"))out.add(line.split("\\s*\\|\\s*",-1));}
        }catch(java.io.IOException ignored){}
        return out;
    }
    private String about(String key){for(String[] l:rawLines(R.raw.about))if(l[0].startsWith(key+"="))return l[0].substring(key.length()+1);return "";}
    private String version(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(PackageManager.NameNotFoundException e){return "";}}
    private ImageView qr(String name){ImageView img=new ImageView(this);img.setScaleType(ImageView.ScaleType.FIT_CENTER);img.setBackground(shape(Color.WHITE,12));img.setPadding(px(5),px(5),px(5),px(5));
        int id=getResources().getIdentifier(name,"drawable",getPackageName());if(id!=0)img.setImageResource(id);return img;}
    /** Credits, links and a QR code to the source. */
    private void aboutScreen(){
        FrameLayout o=openOverlay(0xB3060910);
        FrameLayout panel=new FrameLayout(this);panel.setBackground(shape(0xF5161B26,24));panel.setElevation(px(24));
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(px(600),px(330));lp.gravity=Gravity.CENTER;o.addView(panel,lp);
        panel.addView(text("Luma TV",26,Color.WHITE,"sans-serif-medium"),pos(380,34,26,24));
        panel.addView(text("Version "+version(),13,0x99FFFFFF,"sans-serif"),pos(380,18,27,62));
        TextView body=text("A calm home screen for Android TV. No ads, no sponsored rows, no tracking: your settings stay on this TV. Hold OK on an app to organise your Home.",13,0xC7FFFFFF,"sans-serif");body.setLineSpacing(px(4),1);panel.addView(body,pos(380,66,27,94));
        panel.addView(text("Made by "+about("author"),15,Color.WHITE,"sans-serif-medium"),pos(380,22,27,172));
        String links=about("x").replaceFirst("^https?://","")+"\n"+about("github").replaceFirst("^https?://","");
        TextView link=text(links,12,0xB3FFFFFF,"sans-serif");link.setLineSpacing(px(4),1);panel.addView(link,pos(380,40,27,198));
        panel.addView(qr("qr_source"),pos(150,150,426,28));
        TextView caption=text("Source code",12,0x99FFFFFF,"sans-serif");caption.setGravity(Gravity.CENTER);panel.addView(caption,pos(150,18,426,184));
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.HORIZONTAL);panel.addView(list,pos(400,42,26,262));
        LinearLayout a=new LinearLayout(this),b=new LinearLayout(this);a.setOrientation(LinearLayout.VERTICAL);b.setOrientation(LinearLayout.VERTICAL);
        list.addView(a,new LinearLayout.LayoutParams(px(200),-1));LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(px(110),-1);gap.leftMargin=px(10);list.addView(b,gap);
        menuRow(a,"Support the author",false,this::supportScreen);View done=menuRow(b,"Done",false,()->closeOverlay(false));((TextView)done).setGravity(Gravity.CENTER);
        root.post(done::requestFocus);
    }
    /** Crypto tips: a QR code per coin from res/raw/support.txt (drawn by tools/support-qr.sh). */
    private void supportScreen(){
        FrameLayout o=openOverlay(0xF2060910);
        TextView title=text("Support the author",30,Color.WHITE,"sans-serif-medium");title.setLetterSpacing(-.02f);o.addView(title,pos(700,40,48,38));
        TextView body=text("Luma is free and has no ads. If it earned a place on your TV, a tip in crypto helps it grow. Scan a code with your phone's wallet, and send each coin only over the network named on its card.",13,0xC7FFFFFF,"sans-serif");
        body.setLineSpacing(px(4),1);o.addView(body,pos(760,44,48,84));
        ArrayList<String[]> coins=rawLines(R.raw.support);int n=Math.max(1,Math.min(4,coins.size()));float w=(864-(n-1)*16)/(float)n;
        for(int i=0;i<Math.min(4,coins.size());i++){String[] c=coins.get(i);
            FrameLayout card=new FrameLayout(this);card.setBackground(shape(0x1AFFFFFF,18));o.addView(card,pos(w,322,48+i*(w+16),146));
            TextView name=text(c[0],14,Color.WHITE,"sans-serif-medium");name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(name,pos(w-28,20,14,14));
            TextView note=text(c.length>1?c[1]:"",10.5f,0x99FFFFFF,"sans-serif");note.setMaxLines(2);card.addView(note,pos(w-28,30,14,37));
            float side=Math.min(w-40,170);card.addView(qr("qr_"+i),pos(side,side,(w-side)/2,74));
            TextView address=text(c.length>2?c[2]:"",10,0xD9FFFFFF,"monospace");address.setGravity(Gravity.CENTER);address.setLineSpacing(px(2),1);card.addView(address,pos(w-24,58,12,82+side));
        }
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);o.addView(list,pos(140,42,410,482));
        View done=menuRow(list,"Done",false,()->closeOverlay(false));((TextView)done).setGravity(Gravity.CENTER);root.post(done::requestFocus);
    }
    // Stock launchers that luma.sh may have switched off to let Luma be the home screen.
    static final String[] STOCK_HOMES={"com.google.android.tvlauncher","com.google.android.apps.tv.launcherx","com.google.android.leanbacklauncher"};
    private Intent homeIntent(){return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);}
    private boolean lumaIsHome(){ResolveInfo r=getPackageManager().resolveActivity(homeIntent(),PackageManager.MATCH_DEFAULT_ONLY);return r!=null&&r.activityInfo!=null&&getPackageName().equals(r.activityInfo.packageName);}
    private void homeScreen(){
        if(!lumaIsHome()){notice("Home screen","To make Luma your home screen, run  ./luma.sh home  on a computer with this TV on the same Wi-Fi. Some TVs also offer it in Settings → Apps → Default apps.");return;}
        menu("Home screen",new String[]{"Keep Luma","Return to the TV's home screen"},0,choice->{if(choice==1)restoreHome();});
    }
    /** Hands Home back to the TV's own launcher. A launcher switched off over ADB can only be switched on over ADB. */
    private void restoreHome(){
        PackageManager pm=getPackageManager();boolean off=false;
        for(String pkg:STOCK_HOMES)try{int state=pm.getApplicationEnabledSetting(pkg);off|=state==PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER||state==PackageManager.COMPONENT_ENABLED_STATE_DISABLED;}catch(IllegalArgumentException notInstalled){}
        ResolveInfo other=null;
        for(ResolveInfo r:pm.queryIntentActivities(homeIntent(),0)){String pkg=r.activityInfo.packageName;if(pkg.equals(getPackageName())||pkg.equals("com.android.tv.settings")||pkg.contains("setupwraith")||pkg.contains("provision"))continue;if(other==null||r.priority>other.priority)other=r;}
        if(off||other==null){notice("Return to the TV's home screen","Your TV's own home screen was switched off from a computer so that Luma could replace it. Only a computer can switch it back on: run  ./luma.sh restore  in the Luma folder, with this TV on the same Wi-Fi.");return;}
        pm.clearPackagePreferredActivities(getPackageName());
        startActivity(homeIntent().setComponent(new ComponentName(other.activityInfo.packageName,other.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    private void sceneApps(){
        JSONArray scenes=lastHome==null?null:lastHome.optJSONObject("plan").optJSONArray("scenes");
        if(scenes==null||scenes.length()==0){notice("Scene apps","Open Home once so Luma can load your scenes from House Control.");return;}
        ArrayList<String> ids=new ArrayList<>(),names=new ArrayList<>();for(int i=0;i<scenes.length();i++){JSONObject scene=scenes.optJSONObject(i);ids.add(scene.optString("id"));names.add(scene.optString("name")+"   →   "+appLabel(sceneApp(scene.optString("id"))));}
        menu("Open an app after a scene",names.toArray(new String[0]),-1,index->{
            ArrayList<String> labels=new ArrayList<>();labels.add("None — stay on Home");int current=0;String chosen=sceneApp(ids.get(index));
            for(int i=0;i<apps.size();i++){labels.add(apps.get(i).label);if(apps.get(i).pkg.equals(chosen))current=i+1;}
            menu("After "+scenes.optJSONObject(index).optString("name"),labels.toArray(new String[0]),current,choice->{
                try{JSONObject saved=new JSONObject(prefs.getString("scene_app_packages","{}"));saved.put(ids.get(index),choice==0?"":apps.get(choice-1).pkg);prefs.edit().putString("scene_app_packages",saved.toString()).apply();}catch(Exception ignored){}
                root.post(this::sceneApps);
            });
        });
    }
    /** true when the scene opens an app: the 3D card becomes the screen and flies into the modelled TV. */
    private boolean sceneApplied(String id){
        String pkg=sceneApp(id);if(pkg.isEmpty()||sceneTransition)return false;App target=null;for(App app:apps)if(app.pkg.equals(pkg))target=app;
        if(target==null){Toast.makeText(this,"The scene app is not installed.",Toast.LENGTH_SHORT).show();return false;}
        final App selected=target;if(!motion()||houseWidgets==null){launch(selected);return false;}
        sceneTransition=true;content.setTranslationZ(px(40));
        sceneVeil=new View(this);sceneVeil.setBackgroundColor(Color.BLACK);sceneVeil.setAlpha(0);sceneVeil.setFocusable(true);sceneVeil.setFocusableInTouchMode(true);sceneVeil.setOnKeyListener((v,k,e)->true);sceneVeil.setTranslationZ(px(200));root.addView(sceneVeil,new FrameLayout.LayoutParams(-1,-1));sceneVeil.requestFocus();
        final int flight=1500;
        wallpaper.pauseAmbient();
        hero.animate().alpha(0).translationY(-px(6)).setDuration(360).setInterpolator(ease).withLayer().start();
        for(Tile t:tiles)t.animate().alpha(0).translationY(px(14)).setDuration(420).setInterpolator(ease).withLayer().start();
        nav().animate().alpha(0).setDuration(320).withLayer().start();
        if(clock!=null)clock.animate().alpha(0).setDuration(320).start();
        brand.animate().alpha(0).setDuration(320).start();
        houseWidgets.fadeControls(260);houseWidgets.portalToTV(flight);
        sceneVeil.animate().alpha(1).setStartDelay(flight-420).setDuration(420).setInterpolator(new PathInterpolator(.5f,0f,1f,1f)).start();
        // Delays follow the system animation scale, like the animations themselves.
        handler.postDelayed(()->{if(sceneTransition&&started)launch(selected);},Math.round((flight+40)*Math.max(.01f,ValueAnimator.getDurationScale())));
        return true;
    }
    private void resetSceneTransition(){
        sceneTransition=false;if(sceneVeil!=null){sceneVeil.animate().cancel();root.removeView(sceneVeil);sceneVeil=null;}if(content!=null)content.setTranslationZ(0);if(hero!=null){hero.animate().cancel();hero.setAlpha(1);hero.setTranslationY(0);}nav().animate().cancel();nav().setAlpha(1);if(clock!=null){clock.animate().cancel();clock.setAlpha(1);}if(brand!=null){brand.animate().cancel();brand.setAlpha(1);}if(sceneryShade!=null){sceneryShade.animate().cancel();sceneryShade.setAlpha(page.equals("settings")?1:0);}for(Tile t:tiles){t.animate().cancel();t.setAlpha(1);t.setTranslationY(0);}if(houseWidgets!=null)houseWidgets.resetPortal();
    }

    private void appMenu(App app) {
        Set<String> hidden=new HashSet<>(prefs.getStringSet("hidden",Collections.emptySet()));
        String[] choices={"Open", "Move to first position", "Move left", "Move right", hidden.contains(app.pkg) ? "Show on Home" : "Hide from Home", "App info"};
        menu(app.label,choices,-1,index-> {
            if (index==0) { launch(app); return; }
            ArrayList<String> ids=order(); int current=ids.indexOf(app.pkg);
            if (index==1) { ids.remove(app.pkg); ids.add(0,app.pkg); hidden.remove(app.pkg); }
            if (index==2 && current>0) Collections.swap(ids,current,current-1);
            if (index==3 && current<ids.size()-1) Collections.swap(ids,current,current+1);
            if (index==4) { if (hidden.contains(app.pkg)) hidden.remove(app.pkg); else hidden.add(app.pkg); }
            if (index==5) { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+app.pkg))); return; }
            prefs.edit().putString("order",String.join(",",ids)).putStringSet("hidden",hidden).apply();
            selectedPackage=app.pkg;showPage(page,true);
        });
    }

    /* ---------- Settings ---------- */
    private ImageView previewImage;private TextView previewTitle,previewBody;
    private void preview(int thumb,String title,String body){
        if(previewImage==null)return;
        if(thumb!=0){previewImage.setImageResource(thumb);previewImage.setAlpha(1f);}else previewImage.setAlpha(.35f);
        previewTitle.setText(title);previewBody.setText(body);
    }
    private void settings(boolean focus) {
        TextView title=text("Settings",40,Color.WHITE,"sans-serif-medium"); title.setLetterSpacing(-.03f);
        content.addView(title,pos(450,52,48,84));
        ScrollView sv=new ScrollView(this); sv.setVerticalScrollBarEnabled(false); sv.setClipToPadding(false); sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        // A view is clipped to its bounds by its parent: the page lets focus effects overflow, so the list gets its own
        // clipping frame (with padding room for the focus lift) and rows scroll away under a soft edge, not over the title.
        sv.setVerticalFadingEdgeEnabled(true); sv.setFadingEdgeLength(px(28));
        LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(px(8),px(4),px(8),px(40)); list.setClipChildren(false); list.setClipToPadding(false);
        sv.setPadding(px(14),px(8),px(14),0);sv.addView(list);
        FrameLayout frame=new FrameLayout(this); frame.setTag("scroll-frame"); frame.addView(sv,new FrameLayout.LayoutParams(-1,-1)); content.addView(frame,pos(520,400,26,138));
        // live preview on the right
        FrameLayout card=new FrameLayout(this);card.setBackground(shape(0x1FFFFFFF,22));card.setClipToOutline(true);
        previewImage=new ImageView(this);previewImage.setScaleType(ImageView.ScaleType.CENTER_CROP);card.addView(previewImage,pos(352,198,0,0));
        previewTitle=text("",19,Color.WHITE,"sans-serif-medium");card.addView(previewTitle,pos(320,26,18,214));
        previewBody=text("",13,0xC2FFFFFF,"sans-serif");previewBody.setLineSpacing(px(4),1);card.addView(previewBody,pos(316,80,18,246));
        content.addView(card,pos(352,330,560,150));
        int wp=prefs.getInt("wallpaper",0);
        ArrayList<View> controls=new ArrayList<>();
        caption(list,"Appearance",true);
        controls.add(option(list,"Wallpaper",()->WALLPAPERS[prefs.getInt("wallpaper",0)],null,this::wallpaperPicker,
            ()->preview(THUMBS[prefs.getInt("wallpaper",0)],WALLPAPERS[prefs.getInt("wallpaper",0)],"Seven wallpapers, from the fjord to the Milky Way. Preview each one live.")));
        controls.add(option(list,"Clock",null,"clock",()->{},()->preview(0,"Clock","Shows the time in the top-right corner of every page.")));
        caption(list,"Motion and sound",false);
        controls.add(option(list,"Fluid motion",null,"motion",()->{},()->preview(0,"Fluid motion","Springy focus, depth and the 3D flight into your TV. Turn off for a calmer, simpler screen.")));
        controls.add(option(list,"Living wallpaper",null,"ambient",()->wallpaper.syncAmbient(),()->preview(THUMBS[prefs.getInt("wallpaper",0)],"Living wallpaper","Clouds drift and water moves very slowly. Pauses whenever an app is open.")));
        controls.add(option(list,"Navigation sounds",null,"sound",()->{},()->preview(0,"Navigation sounds","Soft, quiet feedback as you move and open apps.")));
        caption(list,"Home",false);
        controls.add(option(list,"Scene apps",()->"Choose",null,this::sceneApps,()->preview(0,"Scene apps","Open an app after a scene — Movie can start Netflix as the lights go down.")));
        controls.add(option(list,"Home screen",()->lumaIsHome()?"Luma":"TV default",null,this::homeScreen,
            ()->preview(0,"Home screen",lumaIsHome()?"Luma opens when you press Home. Choose to return to your TV's own home screen.":"Your TV's own home screen opens when you press Home.")));
        caption(list,"System",false);
        controls.add(option(list,"Device settings",()->"",null,()->startActivity(new Intent(Settings.ACTION_SETTINGS)),()->preview(0,"Device settings","Network, picture, sound and accounts.")));
        controls.add(option(list,"Support the author",()->"",null,this::supportScreen,()->preview(0,"Support the author","Luma is free and has no ads. A tip in crypto helps it grow.")));
        controls.add(option(list,"About Luma",()->"",null,this::aboutScreen,()->preview(0,"About Luma","Version "+version()+" · made by "+about("author"))));
        content.setTag(controls);
        preview(THUMBS[wp],WALLPAPERS[wp],"Seven wallpapers, from the fjord to the Milky Way. Preview each one live.");
        if (focus) root.post(()->controls.get(0).requestFocus());
    }
    private void caption(LinearLayout list,String label,boolean first){
        TextView c=text(label,13,0x99FFFFFF,"sans-serif-medium");c.setPadding(px(10),0,0,0);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,px(20));lp.topMargin=px(first?0:12);lp.bottomMargin=px(6);list.addView(c,lp);
    }
    @SuppressWarnings("unchecked") private void controlsFocus(int i) { root.post(()-> { Object tag=content.getTag(); if(tag instanceof ArrayList) ((ArrayList<View>)tag).get(i).requestFocus(); }); }
    /** A settings row: either a value with a chevron, or an Apple-style switch bound to a preference. */
    private View option(LinearLayout parent,String title,java.util.function.Supplier<String> value,String toggle,Runnable action,Runnable onFocus) {
        FrameLayout row=new FrameLayout(this);row.setFocusable(true);row.setFocusableInTouchMode(true);row.setSoundEffectsEnabled(false);
        TextView name=text(title,15,Color.WHITE,"sans-serif-medium");row.addView(name,pos(300,20,18,12));
        TextView detail=value==null?null:text(value.get()+(value.get().equals("")?"›":"   ›"),14,0x99FFFFFF,"sans-serif");
        if(detail!=null){detail.setGravity(Gravity.RIGHT);row.addView(detail,pos(200,20,258,12));}
        final View track=new View(this),knob=new View(this);
        if(toggle!=null){row.addView(track,pos(40,24,418,9));row.addView(knob,pos(20,20,420,11));}
        Runnable paint=()->{boolean f=row.hasFocus();row.setBackground(shape(f?0xF7FFFFFF:0x14FFFFFF,14));name.setTextColor(f?0xFF111827:Color.WHITE);
            if(detail!=null)detail.setTextColor(f?0xFF5B6472:0x99FFFFFF);
            if(toggle!=null){boolean on=toggle.equals("motion")?motion():prefs.getBoolean(toggle,true);track.setBackground(shape(on?0xFF34C759:f?0x33111827:0x33FFFFFF,12));knob.setBackground(shape(Color.WHITE,10));knob.setElevation(px(1.5f));
                float x=on?px(16):0;knob.animate().cancel();if(motion())knob.animate().translationX(x).setDuration(260).setInterpolator(spring).start();else knob.setTranslationX(x);}};
        paint.run();
        row.setOnFocusChangeListener((v,f)->{paint.run();row.animate().cancel();if(motion())row.animate().scaleX(f?1.02f:1).scaleY(f?1.02f:1).setDuration(300).setInterpolator(spring).start();else{row.setScaleX(1);row.setScaleY(1);}row.setElevation(px(f?10:0));if(f){onFocus.run();navigationSound(parent.indexOfChild(row));}});
        row.setOnClickListener(v->{sound();
            if(toggle!=null){boolean on=toggle.equals("motion")?motion():prefs.getBoolean(toggle,true);prefs.edit().putBoolean(toggle,!on).apply();
                if(toggle.equals("clock"))clock.setVisibility(!on?View.VISIBLE:View.GONE);paint.run();}
            action.run();});
        row.setContentDescription(title);
        row.setOnKeyListener((v,key,e)-> {if(e.getAction()==KeyEvent.ACTION_DOWN && key==KeyEvent.KEYCODE_DPAD_UP && parent.getChildAt(1)==row) {tabs.get(2).requestFocus();return true;}return false;});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,px(44));lp.bottomMargin=px(6);parent.addView(row,lp);return row;
    }
    @Override public void onBackPressed() {
        if (overlay!=null) { closeOverlay(true); return; }
        if (!page.equals("home")) showPage("home",true);
        else if (scroll!=null && scroll.getScrollY()>0) {scroll.smoothScrollTo(0,0);if(!tiles.isEmpty())tiles.get(0).requestFocus();}
        else tabs.get(0).requestFocus();
    }

    private final class Tile extends FrameLayout {
        final App app;
        final Art art;
        final FrameLayout face;
        final Shine light;
        final GradientDrawable rim;
        final TextView label;
        final int index;
        final TileMotion movement;
        Tile(App item,int number) {
            super(MainActivity.this);app=item;index=number;
            setClipChildren(false);setClipToPadding(false);setFocusable(true);setFocusableInTouchMode(true);setSoundEffectsEnabled(false);
            setContentDescription(app.label+". OK: open. Hold OK: organize app.");
            face=new FrameLayout(MainActivity.this);GradientDrawable faceBg=shape(app.color,16);faceBg.setStroke(Math.max(1,px(.6f)),0x1FFFFFFF);face.setBackground(faceBg);face.setClipToOutline(true);face.setElevation(px(2));face.setCameraDistance(px(900));
            art=new Art(app);face.addView(art,new FrameLayout.LayoutParams(-1,-1));
            light=new Shine();light.setAlpha(0);face.addView(light,new FrameLayout.LayoutParams(-1,-1));
            rim=shape(Color.TRANSPARENT,16);rim.setStroke(px(1.2f),0xF0FFFFFF);rim.setAlpha(0);face.setForeground(rim);
            addView(face,pos(132,76,0,0));
            label=text(app.label,13,Color.WHITE,"sans-serif-medium");label.setGravity(Gravity.CENTER);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setAlpha(0);label.setShadowLayer(px(6),0,px(1),0x66000000);
            addView(label,pos(160,22,-14,88));
            movement=new TileMotion(this);
            setOnFocusChangeListener((v,f)-> {
                movement.focus(f);
                face.setElevation(px(f?14:2));rim.setAlpha(f?210:0);
                label.animate().cancel();if(motion())label.animate().alpha(f?1:0).translationY(f?0:-px(3)).setDuration(f?260:140).setInterpolator(ease).start();else label.setAlpha(f?1:0);
                setTranslationZ(px(f?20:0));
                if(f) {
                    selectApp(app);wallpaper.follow(index);
                    if(index<6) scroll.post(()->scroll.smoothScrollTo(0,0));
                    else requestRectangleOnScreen(new Rect(-px(12),-px(14),getWidth()+px(12),getHeight()+px(5)),false);
                }
            });
            setOnClickListener(v->launch(app));
            setOnLongClickListener(v->{sound();appMenu(app);return true;});
            setOnKeyListener((v,key,e)-> {
                if(key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER) {
                    movement.press(e.getAction()==KeyEvent.ACTION_DOWN);return false;
                }
                if(e.getAction()!=KeyEvent.ACTION_DOWN) return false;
                if(key==KeyEvent.KEYCODE_DPAD_UP && index<6){if(page.equals("home")&&houseWidgets!=null){int[] l=new int[2];getLocationOnScreen(l);houseWidgets.focusNearest(l[0]+getWidth()/2f);}else tabs.get(1).requestFocus();return true;}
                int next=-1;
                if(key==KeyEvent.KEYCODE_DPAD_LEFT && index%6>0) next=index-1;
                if(key==KeyEvent.KEYCODE_DPAD_RIGHT && index%6<5 && index+1<tiles.size()) next=index+1;
                if(key==KeyEvent.KEYCODE_DPAD_DOWN && index+6<tiles.size()) next=index+6;
                if(key==KeyEvent.KEYCODE_DPAD_UP && index>=6) next=index-6;
                if(next>=0){if(e.getRepeatCount()==0 || e.getRepeatCount()%2==0){homeGreeting=false;lastDirectionX=Integer.compare(next%6,index%6);lastDirectionY=Integer.compare(next/6,index/6);navigationSound(next);tiles.get(next).requestFocus();}return true;}
                return key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT || key==KeyEvent.KEYCODE_DPAD_DOWN;
            });
        }
        @Override protected void onDetachedFromWindow(){movement.stop();super.onDetachedFromWindow();}
    }

    /** Native property animations keep the static card artwork cached on the GPU. */
    private final class TileMotion {
        final Tile tile;
        boolean focused,pressed;
        TileMotion(Tile tile){this.tile=tile;}
        void focus(boolean value) {
            focused=value;pressed=false;
            if(value && motion()) {
                tile.face.animate().cancel();
                tile.face.setRotationY(-lastDirectionX*4.5f);tile.face.setRotationX(lastDirectionY*3f);
            }
            start();
            if(motion()) {
                tile.art.animate().cancel();
                float depthX=focused?px((tile.index%6-2.5f)*.7f):0;
                float depthY=focused?-px(1):0;
                if(focused){tile.art.setTranslationX(depthX-px(lastDirectionX*4.5f));tile.art.setTranslationY(depthY+px(lastDirectionY*3));tile.light.setTranslationX(px(lastDirectionX*12));}
                tile.art.animate().translationX(depthX).translationY(depthY).setDuration(520).setInterpolator(drift).start();
                tile.light.animate().translationX(focused?-depthX*2:0).alpha(focused?.6f:0).setDuration(580).setInterpolator(drift).start();
            }
        }
        void press(boolean down){pressed=down;start();}
        void start(){
            if(!motion()){snap();return;}
            tile.face.animate().cancel();tile.label.animate().cancel();tile.light.animate().cancel();
            float scale=(focused?1.11f:1)-(pressed?.03f:0);
            tile.face.animate().scaleX(scale).scaleY(scale).translationY(focused?-px(3):0).rotationX(0).rotationY(0).setDuration(pressed?65:420).setInterpolator(pressed?ease:spring).withLayer().start();
            tile.label.animate().scaleX(focused?1.025f:1).scaleY(focused?1.025f:1).setDuration(260).setInterpolator(ease).start();
            tile.light.animate().alpha(focused?.6f:0).setDuration(200).setInterpolator(ease).start();
        }
        void stop(){tile.face.animate().cancel();tile.art.animate().cancel();tile.label.animate().cancel();tile.light.animate().cancel();}
        void snap(){
            stop();pressed=false;
            float scale=focused?1.11f:1;
            tile.face.setScaleX(scale);tile.face.setScaleY(scale);tile.face.setTranslationY(focused?-px(3):0);tile.face.setRotationX(0);tile.face.setRotationY(0);
            tile.art.setTranslationX(0);tile.art.setTranslationY(0);tile.light.setTranslationX(0);tile.light.setAlpha(focused?.6f:0);
            tile.label.setScaleX(focused?1.025f:1);tile.label.setScaleY(focused?1.025f:1);
        }
    }

    private final class Art extends View {
        final App app; final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        final RectF bounds=new RectF();Shader finish;
        Art(App app) {super(MainActivity.this);this.app=app;}
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
            finish=new LinearGradient(0,0,0,h,new int[]{0x20FFFFFF,0x00FFFFFF,0x14000000},new float[]{0,.46f,1},Shader.TileMode.CLAMP);
        }
        @Override protected void onDraw(Canvas c) {
            float w=getWidth(),h=getHeight();
            bounds.set(0,0,w,h);
            p.setColor(app.color);p.setShader(null);c.drawRoundRect(bounds,px(16),px(16),p);
            String pkg=app.pkg;
            if(pkg.equals("com.google.android.youtube.tv")) {
                float cx=w/2,cy=h/2;
                p.setColor(0xFFFF0033);bounds.set(cx-px(25),cy-px(17),cx+px(25),cy+px(17));c.drawRoundRect(bounds,px(10),px(10),p);
                p.setColor(Color.WHITE);Path path=new Path();path.moveTo(cx-px(5),cy-px(9));path.lineTo(cx+px(10),cy);path.lineTo(cx-px(5),cy+px(9));path.close();c.drawPath(path,p);
            } else if(pkg.equals("com.netflix.ninja")) {
                p.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));p.setTextSize(px(54));p.setColor(0xFFE50914);p.setTextAlign(Paint.Align.CENTER);c.drawText("N",w/2,h/2+px(19),p);
            } else if(pkg.equals("com.soundcloud.android")) {
                drawIcon(c,app.icon,48);
            } else if(pkg.equals("com.ted.android.tv")) {
                p.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));p.setTextSize(px(33));p.setTextAlign(Paint.Align.CENTER);p.setColor(0xFFFF302B);c.drawText("TED",w/2,h/2+px(12),p);
            } else if(pkg.equals("stream.tv.online")) {
                float cx=w/2,cy=h/2-px(2);
                p.setColor(Color.WHITE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(px(2.5f));
                bounds.set(cx-px(27),cy-px(18),cx+px(27),cy+px(18));c.drawRoundRect(bounds,px(6),px(6),p);
                c.drawLine(cx,cy+px(18),cx,cy+px(25),p);c.drawLine(cx-px(10),cy+px(25),cx+px(10),cy+px(25),p);
                p.setStyle(Paint.Style.FILL);Path play=new Path();play.moveTo(cx-px(5),cy-px(9));play.lineTo(cx+px(10),cy);play.lineTo(cx-px(5),cy+px(9));play.close();c.drawPath(play,p);
            } else if(pkg.equals("com.phlox.tvwebbrowser")) {
                float cx=w/2,cy=h/2,r=px(26);
                p.setColor(0xF4FFFFFF);c.drawCircle(cx,cy,r,p);
                p.setColor(0xFFB4C6D6);p.setStrokeWidth(px(1));
                for(int tick=0;tick<24;tick++) {
                    double a=tick*Math.PI/12;float inner=r-px(tick%3==0?6:3);
                    c.drawLine(cx+(float)Math.cos(a)*inner,cy+(float)Math.sin(a)*inner,cx+(float)Math.cos(a)*(r-px(2)),cy+(float)Math.sin(a)*(r-px(2)),p);
                }
                c.save();c.rotate(38,cx,cy);
                Path needle=new Path();needle.moveTo(cx,cy-px(21));needle.lineTo(cx+px(5),cy);needle.lineTo(cx-px(5),cy);needle.close();p.setColor(0xFFE65E62);c.drawPath(needle,p);
                needle.reset();needle.moveTo(cx,cy+px(21));needle.lineTo(cx+px(5),cy);needle.lineTo(cx-px(5),cy);needle.close();p.setColor(0xFF42677F);c.drawPath(needle,p);c.restore();
            } else if(pkg.equals("com.google.android.vending") || pkg.equals("com.android.vending")) {
                drawIcon(c,app.icon,47);
            } else if(app.banner!=null) {
                float ratio=Math.min(w/app.banner.getWidth(),h/app.banner.getHeight());
                float bw=app.banner.getWidth()*ratio,bh=app.banner.getHeight()*ratio;
                bounds.set((w-bw)/2,(h-bh)/2,(w+bw)/2,(h+bh)/2);p.setAlpha(255);c.drawBitmap(app.banner,null,bounds,p);
            } else drawIcon(c,app.icon,48);
            p.setShader(finish);
            bounds.set(0,0,w,h);c.drawRoundRect(bounds,px(16),px(16),p);p.setShader(null);
        }
        private void drawIcon(Canvas c,Bitmap icon,float size) {float s=px(size);bounds.set((getWidth()-s)/2,(getHeight()-s)/2,(getWidth()+s)/2,(getHeight()+s)/2);p.setAlpha(255);c.drawBitmap(icon,null,bounds,p);}
    }

    /** A separate compositor layer keeps the icon display list static during focus. */
    private final class Shine extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Shine(){super(MainActivity.this);}
        @Override public boolean hasOverlappingRendering(){return false;}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){
            paint.setShader(new LinearGradient(-w*.4f,0,w*.95f,h,new int[]{0x00FFFFFF,0x40FFFFFF,0x00FFFFFF},new float[]{0,.5f,1},Shader.TileMode.CLAMP));
        }
        @Override protected void onDraw(Canvas canvas){canvas.drawRect(0,0,getWidth(),getHeight(),paint);}
    }

    /** Independent compositor surfaces: distant clouds drift behind the cut-out landscape. */
    private final class Wallpaper extends FrameLayout {
        final HandlerThread surfaceThread=new HandlerThread("Luma scenery",android.os.Process.THREAD_PRIORITY_BACKGROUND);
        final Handler surfaceHandler;
        final SceneLayer sky, terrain;
        final FrameLayout skyTrack;
        volatile boolean ambientRunning;
        volatile float ambientSeconds,cloudX,cloudY;
        long lastAmbientTick,ambientTicks;
        private final Runnable ambientTick=new Runnable(){public void run(){
            if(!ambientEligible()){syncAmbient();return;}
            long now=SystemClock.uptimeMillis();
            ambientSeconds+=Math.min(.25f,(now-lastAmbientTick)/1000f);lastAmbientTick=now;
            cloudX=px(14)*(float)Math.sin(ambientSeconds*Math.PI*2/112);
            cloudY=px(.65f)*(float)Math.sin(ambientSeconds*Math.PI*2/83);
            if(blend>=1){sky.drawSurface();terrain.drawSurface();ambientTicks++;}
            handler.postDelayed(this,100);
        }};
        Bitmap skySource, terrainSource;
        int requestedStyle=-1;
        volatile float blend=1;
        ValueAnimator sceneTransition;
        Wallpaper() {
            super(MainActivity.this);
            surfaceThread.start();surfaceHandler=new Handler(surfaceThread.getLooper());
            setClipChildren(false);setClipToPadding(false);
            sky=new SceneLayer(false);terrain=new SceneLayer(true);
            skyTrack=new FrameLayout(MainActivity.this);skyTrack.setClipChildren(false);skyTrack.setClipToPadding(false);
            skyTrack.addView(sky,new FrameLayout.LayoutParams(-1,-1));
            int margin=px(30);
            int width=getResources().getDisplayMetrics().widthPixels+margin*2;
            int height=getResources().getDisplayMetrics().heightPixels+margin*2;
            FrameLayout.LayoutParams back=new FrameLayout.LayoutParams(width,px(220)+margin);
            back.leftMargin=-margin;back.topMargin=-margin;addView(skyTrack,back);
            FrameLayout.LayoutParams front=new FrameLayout.LayoutParams(width,height);
            front.leftMargin=-margin;front.topMargin=-margin;addView(terrain,front);
            update();
        }
        void follow(int index) {
            skyTrack.animate().cancel();terrain.animate().cancel();
            float position=(index%6-2.5f)/2.5f;
            float arc=1-position*position;
            if(motion()) {
                skyTrack.animate().translationX(-position*px(requestedStyle>=3?11:5)).translationY(px(arc*(requestedStyle>=3?2.2f:.7f))).setDuration(1000).setInterpolator(drift).start();
                terrain.animate().translationX(-position*px(22)).translationY(px(arc*3-Math.min(index/6,2)*2)).setDuration(680).setInterpolator(drift).start();
            } else rest();
        }
        void rest(){
            skyTrack.animate().cancel();terrain.animate().cancel();
            skyTrack.setTranslationX(0);skyTrack.setTranslationY(0);terrain.setTranslationX(0);terrain.setTranslationY(0);
        }
        private boolean ambientEligible() {
            return started && hasWindowFocus() && !page.equals("settings") && requestedStyle!=2
                && motion() && prefs.getBoolean("ambient",true) && sky.scene!=null && terrain.scene!=null;
        }
        void syncAmbient() {
            if(ambientEligible()) {
                if(!ambientRunning){ambientRunning=true;lastAmbientTick=SystemClock.uptimeMillis();handler.postDelayed(ambientTick,100);}
            } else {
                pauseAmbient();
                if(!motion() || !prefs.getBoolean("ambient",true) || requestedStyle==2) {
                    cloudX=0;cloudY=0;sky.drawSurface();terrain.drawSurface();
                }
            }
        }
        void pauseAmbient(){ambientRunning=false;handler.removeCallbacks(ambientTick);}
        private Bitmap compose(Bitmap source,int style,boolean foreground,int width,int height) {
            Bitmap result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            Canvas c=new Canvas(result);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            if(!foreground || style==2)c.drawColor(0xFF080C14);
            if(source!=null && style!=2) {
                // Shared destination bounds keep the horizon registered; only the terrain has alpha.
                // Normalize almost-opaque generated terrain pixels, preserving antialiased skyline edges.
                // Otherwise a moving sky faintly shines through the mountain interiors.
                if(foreground)paint.setColorFilter(new ColorMatrixColorFilter(new float[]{1,0,0,0,0, 0,1,0,0,0, 0,0,1,0,0, 0,0,0,1.04f,0}));
                c.drawBitmap(source,null,new RectF(0,0,width,height),paint);paint.setColorFilter(null);
                if(foreground)paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
                if(style==1){paint.setColor(0x504B3159);c.drawRect(0,0,width,height,paint);paint.setAlpha(255);}
            }
            if(foreground)paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
            paint.setShader(new LinearGradient(0,0,width*.88f,0,new int[]{0xA0080D18,0x30080D18,0x00080D18},new float[]{0,.5f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,width,height,paint);
            paint.setShader(new LinearGradient(0,0,0,height,new int[]{0x40080C14,0x08080C14,0x88080C14,0xE8080C14},new float[]{0,.3f,.68f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,width,height,paint);
            return result;
        }
        void update(){show(prefs.getInt("wallpaper",0));}
        /** Shows a wallpaper without saving it (the picker previews live). */
        void show(final int style) {
            if(requestedStyle==style)return;
            requestedStyle=style;
            final int width=getResources().getDisplayMetrics().widthPixels;
            final int height=getResources().getDisplayMetrics().heightPixels;
            loader.execute(()-> {
                Bitmap back,front;
                if(style>=3) {
                    // Photographic and rendered wallpapers: one full-height layer, no cut-out terrain.
                    BitmapFactory.Options full=new BitmapFactory.Options();full.inPreferredConfig=Bitmap.Config.ARGB_8888;
                    Bitmap photo=BitmapFactory.decodeResource(getResources(),PHOTOS[Math.min(PHOTOS.length-1,style-3)],full);
                    back=compose(photo,style,false,width,height);photo.recycle();
                    front=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);
                } else {
                    if(skySource==null) {
                        BitmapFactory.Options rgb=new BitmapFactory.Options();rgb.inPreferredConfig=Bitmap.Config.RGB_565;
                        skySource=BitmapFactory.decodeResource(getResources(),R.drawable.fjord_sky,rgb);
                        terrainSource=BitmapFactory.decodeResource(getResources(),R.drawable.fjord_terrain);
                    }
                    back=compose(skySource,style,false,width,height);
                    front=compose(terrainSource,style,true,width,height);
                }
                runOnUiThread(()->{
                    if(isDestroyed() || requestedStyle!=style){back.recycle();front.recycle();return;}
                    FrameLayout.LayoutParams track=(FrameLayout.LayoutParams)skyTrack.getLayoutParams();int depth=style>=3?getResources().getDisplayMetrics().heightPixels+px(60):px(220)+px(30);
                    if(track.height!=depth){track.height=depth;skyTrack.setLayoutParams(track);}
                    if(sceneTransition!=null)sceneTransition.cancel();
                    sky.previous=sky.scene;terrain.previous=terrain.scene;sky.scene=back;terrain.scene=front;
                    if(sky.previous!=null && motion()) {
                        sceneTransition=ValueAnimator.ofFloat(0,1);sceneTransition.setDuration(420);sceneTransition.setInterpolator(ease);
                        sceneTransition.addUpdateListener(a->{blend=(float)a.getAnimatedValue();sky.drawSurface();terrain.drawSurface();});
                        sceneTransition.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){sky.previous=null;terrain.previous=null;blend=1;sky.drawSurface();terrain.drawSurface();}});
                        sceneTransition.start();
                    } else {sky.previous=null;terrain.previous=null;blend=1;sky.drawSurface();terrain.drawSurface();}
                    syncAmbient();
                });
            });
        }
        private final class SceneLayer extends SurfaceView implements SurfaceHolder.Callback {
            final boolean foreground;
            volatile Bitmap scene,previous;
            final java.util.concurrent.atomic.AtomicBoolean drawPending=new java.util.concurrent.atomic.AtomicBoolean();
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            final RectF bounds=new RectF();
            WaterMesh water;
            int waterWidth,waterHeight;
            SceneLayer(boolean front) {
                super(MainActivity.this);foreground=front;
                if(front){setZOrderMediaOverlay(true);getHolder().setFormat(PixelFormat.TRANSLUCENT);}
                getHolder().addCallback(this);
            }
            @Override public void surfaceCreated(SurfaceHolder holder){drawSurface();}
            @Override public void surfaceChanged(SurfaceHolder holder,int format,int w,int h){drawSurface();}
            @Override public void surfaceDestroyed(SurfaceHolder holder){animate().cancel();}
            void drawSurface() {
                if(!drawPending.compareAndSet(false,true))return;
                surfaceHandler.post(()->{try{drawSurfaceNow();}finally{drawPending.set(false);}});
            }
            void drawSurfaceNow() {
                if(!getHolder().getSurface().isValid())return;
                Canvas c=null;
                try {
                    c=getHolder().lockHardwareCanvas();if(c==null)return;
                    c.drawColor(foreground && scene!=null?Color.TRANSPARENT:0xFF080C14,PorterDuff.Mode.SRC);
                    if(!foreground)c.translate(cloudX,cloudY);
                    // Sky uses a shallow surface, but the same virtual full-scene crop as terrain.
                    bounds.set(0,0,c.getWidth(),getResources().getDisplayMetrics().heightPixels+px(60));
                    if(previous!=null && blend<1) {
                        paint.setAlpha(Math.round(255*(1-blend)));c.drawBitmap(previous,null,bounds,paint);
                        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));paint.setAlpha(Math.round(255*blend));
                    }
                    if(scene!=null) {
                        if(foreground && ambientRunning && previous==null && blend>=1 && requestedStyle<2) {
                            if(water==null || waterWidth!=c.getWidth() || waterHeight!=(int)bounds.height()) {
                                waterWidth=c.getWidth();waterHeight=(int)bounds.height();water=new WaterMesh(waterWidth,waterHeight,unit);
                            }
                            water.draw(c,scene,ambientSeconds,paint);
                        } else c.drawBitmap(scene,null,bounds,paint);
                    }
                    paint.setXfermode(null);paint.setAlpha(255);
                } catch(IllegalStateException|IllegalArgumentException ignored){/* Surface may disappear while the TV sleeps. */}
                finally{if(c!=null)try{getHolder().unlockCanvasAndPost(c);}catch(RuntimeException ignored){}}
            }
        }
    }
    static final String[] WALLPAPERS={"Fjord","Dusk","Graphite","Milky Way","Calm Sea","Dawn","Silk"};
    static final int[] PHOTOS={R.drawable.wp_milkyway,R.drawable.wp_sea,R.drawable.wp_dawn,R.drawable.wp_silk};
    static final int[] THUMBS={R.drawable.thumb_0,R.drawable.thumb_1,R.drawable.thumb_2,R.drawable.thumb_3,R.drawable.thumb_4,R.drawable.thumb_5,R.drawable.thumb_6};
    private static final class App {String pkg,label,description,tag;Bitmap icon,banner;int color;}
    private int colorFor(String pkg) {
        if(pkg.contains("youtube.tv"))return 0xFFF6F5F5;
        if(pkg.contains("netflix"))return 0xFF141414;
        if(pkg.contains("soundcloud"))return 0xFFFF6900;
        if(pkg.contains("ted.android"))return 0xFFF5F5F5;
        if(pkg.contains("phlox"))return 0xFF304B6C;
        if(pkg.contains("stream.tv"))return 0xFF265EAF;
        if(pkg.contains("tvmusic"))return 0xFF26131B;
        if(pkg.contains("amazon"))return 0xFF142C3E;
        return 0xFF293341;
    }
    private String tagFor(String pkg) {
        if(pkg.contains("youtube.tv"))return "WATCH YOUR WAY";
        if(pkg.contains("netflix"))return "TIME FOR A GREAT STORY";
        if(pkg.contains("soundcloud")||pkg.contains("tvmusic"))return "THE SOUND OF YOUR EVENING";
        if(pkg.contains("ted.android"))return "IDEAS WORTH EXPLORING";
        if(pkg.contains("phlox"))return "A WINDOW TO YOUR WORLD";
        return "YOUR SPACE";
    }
    private String descriptionFor(String pkg) {
        if(pkg.contains("youtube.tv"))return "Favorite creators. New discoveries. A bigger screen.";
        if(pkg.contains("netflix"))return "Great stories. Even better evenings.";
        if(pkg.contains("soundcloud"))return "Find your sound. Set the mood.";
        if(pkg.contains("tvmusic"))return "The artists you love. The mood you are in.";
        if(pkg.contains("ted.android"))return "A new perspective can change everything.";
        if(pkg.contains("phlox"))return "The web, from the comfort of your sofa.";
        if(pkg.contains("disney"))return "Stories for every age, all in one place.";
        if(pkg.contains("spotify"))return "Your music and podcasts, on the big speakers.";
        if(pkg.contains("amazon"))return "Movies and series for your next great evening.";
        return "Your apps. Always within reach.";
    }
}
