package dev.luma.tv;

import android.graphics.*;

/** QA and promo films: am start -n dev.luma.tv/.MainActivity --es luma.demo promo|off fills the widgets with
 *  made-up content, not saved: the weather in Miami, three Up Next videos whose pictures are Luma's own CC0
 *  wallpapers, a song with drawn artwork, and consoles on HDMI ports (also on a stick, which has none). */
final class Promo {
    static volatile boolean on;
    /** Widget order for the film: Up Next and Weather, then Inputs with Now Playing, then Control. Smart Home is left
     *  out: on a real TV it would show the owner's own home. */
    static final String[] ORDER={"upnext","weather","inputs","music","control"};
    static final double[] MIAMI={25.77,-80.19};
    static final String CITY="Miami";
    /** {title, line, detail, wallpaper, progress} */
    static final Object[][] VIDEOS={
        {"Under the Milky Way","18 min left · YouTube","A night under the stars · 18 min left in YouTube",R.drawable.wp_milkyway,.62f},
        {"Calm Sea, 4K","Next episode · YouTube","Waves at sunrise · Next episode in YouTube",R.drawable.wp_sea,.0f},
        {"First Light","6 min left · YouTube","Dawn over the mountains · 6 min left in YouTube",R.drawable.wp_dawn,.86f},
    };
    static final String SONG="Golden Hour",ARTIST="Nova Lane",SOURCE="YouTube Music";
    static final long SONG_LENGTH=412000,SONG_START=38000;
    static long since=0,offset=SONG_START;static boolean playing=true;
    static long position(){long p=offset+(playing?android.os.SystemClock.elapsedRealtime()-since:0);return Math.min(SONG_LENGTH,p);}
    static void toggle(){offset=position();since=android.os.SystemClock.elapsedRealtime();playing=!playing;}
    static void start(){since=android.os.SystemClock.elapsedRealtime();offset=SONG_START;playing=true;}

    /** Square cover art for the song: a warm dusk gradient with a low sun and its reflection. */
    static Bitmap artwork(int size){
        Bitmap b=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0,0,0,size,new int[]{0xFF2B1B5A,0xFFB1416B,0xFFF59E5B,0xFFFFD08A},new float[]{0,.45f,.72f,1},Shader.TileMode.CLAMP));
        c.drawRect(0,0,size,size,p);p.setShader(null);
        p.setColor(0xFFFFF1D6);c.drawCircle(size*.5f,size*.66f,size*.17f,p);
        p.setColor(0xFF3A1F4F);c.drawRect(0,size*.7f,size,size,p);
        p.setColor(0x66FFE2B0);for(int i=0;i<5;i++){float y=size*(.74f+i*.045f),w=size*(.22f-i*.035f);c.drawRoundRect(size*.5f-w,y,size*.5f+w,y+size*.012f,size*.006f,size*.006f,p);}
        return b;
    }
}
