package dev.luma.tv;

import android.content.Context;
import android.graphics.*;
import org.json.*;

/** QA and promo films: am start -n dev.luma.tv/.MainActivity --es luma.demo promo|off fills the widgets with
 *  made-up content, not saved: the weather in Miami, three Up Next videos whose pictures are Luma's own CC0
 *  wallpapers, a song with drawn artwork, and consoles on HDMI ports (also on a stick, which has none).
 *  A film can restyle it without a build: adb push a promo.json (and its pictures) to the app's external files dir,
 *  {"song","artist","length","start" (ms),"art" (a file next to it),"videos":[{"title","line","detail","art","progress"}|null,…]}. */
final class Promo {
    static volatile boolean on;
    /** Widget order for the film: Up Next and Weather, then Inputs with Now Playing, then Control. Smart Home is left
     *  out: on a real TV it would show the owner's own home. */
    static final String[] ORDER={"upnext","weather","inputs","music","control"};
    static final double[] MIAMI={25.77,-80.19};
    static final String CITY="Miami";
    /** {title, line, detail, wallpaper, progress} */
    static final Object[][] BUILT_IN={
        {"Under the Milky Way","18 min left · YouTube","A night under the stars · 18 min left in YouTube",R.drawable.wp_milkyway,.62f},
        {"Calm Sea, 4K","Next episode · YouTube","Waves at sunrise · Next episode in YouTube",R.drawable.wp_sea,.0f},
        {"First Light","6 min left · YouTube","Dawn over the mountains · 6 min left in YouTube",R.drawable.wp_dawn,.86f},
    };
    static Object[][] VIDEOS=BUILT_IN;
    static final String SOURCE="YouTube Music";
    static String SONG="Golden Hour",ARTIST="Nova Lane",ART=null;
    static long SONG_LENGTH=412000,SONG_START=38000;
    static long since=0,offset=SONG_START;static boolean playing=true;
    /** The song's clock runs on the animation clock, so a film recorded in slow motion (animator scale) plays it at speed. */
    static long position(){float k=Math.max(1f,android.animation.ValueAnimator.getDurationScale());long p=offset+(playing?(long)((android.os.SystemClock.elapsedRealtime()-since)/k):0);return Math.min(SONG_LENGTH,p);}
    static void toggle(){offset=position();since=android.os.SystemClock.elapsedRealtime();playing=!playing;}
    static void start(Context context){restyle(context);since=android.os.SystemClock.elapsedRealtime();offset=SONG_START;playing=true;}

    /** promo.json in the external files dir, if there is one; otherwise the built-in content. */
    private static void restyle(Context context){
        SONG="Golden Hour";ARTIST="Nova Lane";ART=null;SONG_LENGTH=412000;SONG_START=38000;VIDEOS=BUILT_IN;
        java.io.File dir=context.getExternalFilesDir(null),f=dir==null?null:new java.io.File(dir,"promo.json");
        if(f==null||!f.isFile())return;
        try{
            JSONObject o=new JSONObject(new String(java.nio.file.Files.readAllBytes(f.toPath()),"UTF-8"));
            SONG=o.optString("song",SONG);ARTIST=o.optString("artist",ARTIST);
            SONG_LENGTH=o.optLong("length",SONG_LENGTH);SONG_START=o.optLong("start",SONG_START);
            if(o.has("art"))ART=new java.io.File(dir,o.getString("art")).getPath();
            JSONArray v=o.optJSONArray("videos");if(v==null)return;
            Object[][] out=new Object[BUILT_IN.length][];
            for(int i=0;i<out.length;i++){
                Object[] b=BUILT_IN[i];JSONObject x=v.optJSONObject(i);
                if(x==null){out[i]=b;continue;}
                Object art=x.has("art")?"file://"+new java.io.File(dir,x.getString("art")).getPath():b[3];
                out[i]=new Object[]{x.optString("title",(String)b[0]),x.optString("line",(String)b[1]),x.optString("detail",(String)b[2]),art,(float)x.optDouble("progress",(Float)b[4])};
            }
            VIDEOS=out;
        }catch(Exception e){android.util.Log.w("Luma","promo.json",e);}
    }

    /** Square cover art for the song: a warm dusk gradient with a low sun and its reflection. */
    static Bitmap artwork(int size){
        if(ART!=null){Bitmap a=BitmapFactory.decodeFile(ART);if(a!=null)return Bitmap.createScaledBitmap(a,size,size,true);}
        Bitmap b=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0,0,0,size,new int[]{0xFF2B1B5A,0xFFB1416B,0xFFF59E5B,0xFFFFD08A},new float[]{0,.45f,.72f,1},Shader.TileMode.CLAMP));
        c.drawRect(0,0,size,size,p);p.setShader(null);
        p.setColor(0xFFFFF1D6);c.drawCircle(size*.5f,size*.66f,size*.17f,p);
        p.setColor(0xFF3A1F4F);c.drawRect(0,size*.7f,size,size,p);
        p.setColor(0x66FFE2B0);for(int i=0;i<5;i++){float y=size*(.74f+i*.045f),w=size*(.22f-i*.035f);c.drawRoundRect(size*.5f-w,y,size*.5f+w,y+size*.012f,size*.006f,size*.006f,p);}
        return b;
    }
}
