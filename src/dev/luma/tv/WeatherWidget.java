package dev.luma.tv;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.*;

/** Weather for the main city of the TV's time zone (the place Daylight uses for sunrise): no location permission.
 *  Forecasts come from Open-Meteo (open-meteo.com, free, no account or key); the request carries only the city's
 *  rounded coordinates. The last forecast is kept, so Home shows it at once and offline. OK switches between
 *  the next hours and the coming days. */
final class WeatherWidget extends Widget {
    static final long FRESH=30*60000L;
    static final float PAD=18,SLOTS_Y=114;
    final TextView city,temp,condition,range,error;
    final Glyph glyph;
    final LinearLayout slots;
    final View divider;
    boolean daily,loading,focused;
    JSONObject data;
    final Runnable tick=new Runnable(){public void run(){if(!active)return;load(false);postDelayed(this,FRESH);}};

    WeatherWidget(Context context,WidgetBoard.Host host){
        super(context,host,WidgetBoard.width("weather"));
        Glyph pin=new Glyph(context).kind("pin").color(ink(0xB3));addView(pin,pos(10,10,PAD,18));
        city=text(city(),13,ink(0xE6),"sans-serif-medium");addView(city,pos(150,16,PAD+15,15));
        temp=text("—",44,ink(0xFF),"sans-serif-light");temp.setLetterSpacing(-.03f);addView(temp,pos(140,50,PAD-3,36));
        glyph=new Glyph(context).kind("partly").color(ink(0xF2));addView(glyph,pos(34,34,w-PAD-34,14));
        condition=text("",12.5f,ink(0xD9),"sans-serif-medium");condition.setGravity(Gravity.RIGHT);addView(condition,pos(140,16,w-PAD-140,56));
        range=text("",12,ink(0x99),"sans-serif");range.setGravity(Gravity.RIGHT);addView(range,pos(140,15,w-PAD-140,76));
        divider=new View(context);divider.setBackgroundColor(ink(0x1F));addView(divider,pos(w-PAD*2,1,PAD,103));
        slots=new LinearLayout(context);addView(slots,pos(w-PAD*2,62,PAD,SLOTS_Y));
        error=text("",12.5f,ink(0x99),"sans-serif");error.setGravity(Gravity.CENTER);error.setVisibility(GONE);addView(error,pos(w-PAD*2,16,PAD,134));
        setFocusable(true);setFocusableInTouchMode(true);setSoundEffectsEnabled(false);setPivotX(px(w/2));setPivotY(px(WidgetBoard.ROW_H/2));
        setOnFocusChangeListener((v,f)->{focused=f;paintFocus();if(f){heroNow();host.step(0);}});
        setOnClickListener(v->{host.open();press(this,1.04f);if(data==null){load(true);return;}daily=!daily;fillSlots(true);heroNow();});
        try{String raw=host.prefs().getString("weather_json","");if(!raw.isEmpty()&&key().equals(host.prefs().getString("weather_key",""))){data=new JSONObject(raw);fill();}}catch(JSONException ignored){}
        if(data==null)placeholder();
    }
    @Override void setActive(boolean value){
        boolean was=active;super.setActive(value);removeCallbacks(tick);
        if(value&&!was)post(tick);
    }
    private void paintFocus(){
        GradientDrawable bg=round(focused?(light()?0xF2FFFFFF:0x38FFFFFF):(light()?0x8CFFFFFF:0x1FFFFFFF),22);
        bg.setStroke(Math.max(1,px(focused?1.2f:.8f)),focused?(light()?0x33000000:0xB3FFFFFF):(light()?0x14000000:0x1FFFFFFF));setBackground(bg);
        animate().cancel();float s=focused?1.04f:1;
        // A modest shadow: the card is large, and its shadow is drawn every frame.
        if(motion())animate().scaleX(s).scaleY(s).translationY(focused?-px(3):0).setDuration(focused?460:300).setInterpolator(focused?spring:ease).start();else{setScaleX(s);setScaleY(s);}
        setElevation(px(focused?10:0));
    }
    /** The time zone's main city, or Miami in the promo demo (Promo). */
    String city(){return Promo.on?Promo.CITY:Daylight.city();}
    double[] place(){return Promo.on?Promo.MIAMI:Daylight.place(getContext());}
    static boolean fahrenheit(android.content.SharedPreferences prefs){
        String unit=prefs.getString("temp_unit","auto");if(!unit.equals("auto"))return unit.equals("f");
        if(Promo.on)return true;
        // By the TV's time zone, not its language: many TVs outside the US run in US English.
        String zone=TimeZone.getDefault().getID();
        if(zone.startsWith("US/")||zone.equals("Pacific/Honolulu")||zone.startsWith("America/Indiana/")||zone.startsWith("America/Kentucky/")||zone.startsWith("America/North_Dakota/"))return true;
        return zone.startsWith("America/")&&Arrays.asList("New_York","Chicago","Denver","Los_Angeles","Phoenix","Anchorage","Adak","Boise","Detroit","Juneau","Menominee","Metlakatla","Nome","Sitka","Yakutat").contains(zone.substring(8));
    }
    private String key(){double[] p=place();return (p==null?"?":String.format(Locale.US,"%.2f,%.2f",p[0],p[1]))+(fahrenheit(host.prefs())?"F":"C");}
    void load(boolean force){
        long at=host.prefs().getLong("weather_at",0);
        if(loading||!force&&data!=null&&System.currentTimeMillis()-at<FRESH)return;
        final double[] place=place();if(place==null){showError("Set the TV's time zone to see the weather");return;}
        loading=true;final boolean f=fahrenheit(host.prefs());final String key=key();
        WidgetBoard.IO.execute(()->{
            JSONObject result=null;
            try{
                String url=String.format(Locale.US,"https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code,is_day"
                    +"&hourly=temperature_2m,weather_code,is_day&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=6&forecast_hours=6%s",
                    place[0],place[1],f?"&temperature_unit=fahrenheit":"");
                HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(10000);c.setRequestProperty("User-Agent","LumaTV");
                try(InputStream in=c.getInputStream();BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){StringBuilder sb=new StringBuilder();String line;while((line=r.readLine())!=null)sb.append(line);result=new JSONObject(sb.toString());}
                if(!result.has("current"))result=null;
            }catch(Exception ignored){}
            final JSONObject done=result;
            post(()->{loading=false;
                if(done!=null){host.prefs().edit().putString("weather_json",done.toString()).putLong("weather_at",System.currentTimeMillis()).putString("weather_key",key).apply();data=done;fill();if(focused)heroNow();}
                else if(data==null)showError("Weather is unavailable right now");});
        });
    }
    private void placeholder(){temp.setText("—");condition.setText("Loading…");range.setText("");slots.removeAllViews();}
    private void showError(String message){temp.setText("—");condition.setText("");range.setText("");slots.removeAllViews();error.setText(message);error.setVisibility(VISIBLE);}
    private String deg(double v){return Math.round(v)+"°";}
    private void fill(){
        try{
            error.setVisibility(GONE);
            JSONObject now=data.getJSONObject("current"),day=data.getJSONObject("daily");
            int code=now.optInt("weather_code");
            temp.setText(deg(now.getDouble("temperature_2m")));glyph.kind(Glyph.weather(code,now.optInt("is_day",1)==1));condition.setText(Glyph.weatherName(code));
            range.setText("H:"+deg(day.getJSONArray("temperature_2m_max").getDouble(0))+"   L:"+deg(day.getJSONArray("temperature_2m_min").getDouble(0)));
            fillSlots(false);
        }catch(JSONException e){showError("Weather is unavailable right now");}
    }
    /** Five columns: the next hours (Now first), or the coming days (Today first). */
    private void fillSlots(boolean animate){
        if(data==null)return;
        Runnable build=()->{
            slots.removeAllViews();
            try{
                JSONObject series=data.getJSONObject(daily?"daily":"hourly");JSONArray times=series.getJSONArray("time"),codes=series.getJSONArray("weather_code");
                SimpleDateFormat in=new SimpleDateFormat("yyyy-MM-dd",Locale.US),dayName=new SimpleDateFormat("EEE",Locale.ENGLISH);
                for(int i=0;i<Math.min(5,times.length());i++){
                    LinearLayout col=new LinearLayout(getContext());col.setOrientation(LinearLayout.VERTICAL);col.setGravity(Gravity.CENTER_HORIZONTAL);
                    String t=times.getString(i),label=daily?(i==0?"Today":dayName.format(in.parse(t))):(i==0?"Now":t.substring(11,13));
                    TextView l=text(label,11,ink(0x99),"sans-serif-medium");l.setGravity(Gravity.CENTER);col.addView(l,new LinearLayout.LayoutParams(-1,px(14)));
                    boolean isDay=daily||series.optJSONArray("is_day")==null||series.getJSONArray("is_day").optInt(i,1)==1;
                    Glyph g=new Glyph(getContext()).kind(Glyph.weather(codes.optInt(i),isDay)).color(ink(0xE6));LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(px(19),px(19));gp.topMargin=px(5);gp.bottomMargin=px(5);col.addView(g,gp);
                    TextView v;
                    if(daily){String hi=deg(series.getJSONArray("temperature_2m_max").getDouble(i)),lo=deg(series.getJSONArray("temperature_2m_min").getDouble(i));
                        SpannableString s=new SpannableString(hi+" "+lo);s.setSpan(new ForegroundColorSpan(ink(0x80)),hi.length()+1,s.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);v=text("",12,ink(0xF2),"sans-serif-medium");v.setText(s);}
                    else v=text(deg(series.getJSONArray("temperature_2m").getDouble(i)),12.5f,ink(0xF2),"sans-serif-medium");
                    v.setGravity(Gravity.CENTER);col.addView(v,new LinearLayout.LayoutParams(-1,px(16)));
                    slots.addView(col,new LinearLayout.LayoutParams(0,-1,1));
                }
            }catch(Exception ignored){}
        };
        if(!animate||!motion()){build.run();return;}
        slots.animate().cancel();slots.animate().alpha(0).translationY(px(3)).setDuration(110).withEndAction(()->{build.run();slots.setTranslationY(-px(3));slots.animate().alpha(1).translationY(0).setDuration(300).setInterpolator(ease).start();}).start();
    }
    private void heroNow(){
        if(data==null){host.hero("weather",city(),error.getVisibility()==VISIBLE?error.getText()+". OK tries again.":"Loading the forecast…");return;}
        try{JSONObject now=data.getJSONObject("current"),day=data.getJSONObject("daily");
            String title=city()+"  "+deg(now.getDouble("temperature_2m"));
            String detail=Glyph.weatherName(now.optInt("weather_code"))+". High "+deg(day.getJSONArray("temperature_2m_max").getDouble(0))+", low "+deg(day.getJSONArray("temperature_2m_min").getDouble(0))+" · OK shows "+(daily?"the next hours":"the next days");
            host.hero("weather:"+daily,title,detail);
        }catch(JSONException ignored){}
    }
}
