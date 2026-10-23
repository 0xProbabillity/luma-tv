package dev.luma.tv;

import android.content.*;
import android.graphics.Color;
import android.net.*;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** Control: the TV's connection, picture and sound, accessories and settings as four tiles, in the spirit of
 *  Control Center. Each shows its state and opens the matching page of the TV's own Settings. */
final class ControlWidget extends Widget {
    static final float PAD=18,GAP=12;
    final float tileW=(w-PAD*2-GAP)/2,tileH=(WidgetBoard.ROW_H-PAD*2-GAP)/2;
    final Tile network,picture,accessories,settings;
    final Runnable tick=new Runnable(){public void run(){if(!active)return;update();postDelayed(this,15000);}};

    final class Tile extends FrameLayout {
        final View disc;final Glyph glyph;final TextView name,state;boolean focused,on;String detail="";
        Tile(String kind,String title,int index,Runnable action){
            super(ControlWidget.this.getContext());setClipChildren(false);
            disc=new View(getContext());addView(disc,pos(36,36,12,(tileH-36)/2));
            glyph=new Glyph(getContext()).kind(kind);addView(glyph,pos(20,20,20,(tileH-20)/2));
            name=text(title,13.5f,ink(0xF2),"sans-serif-medium");addView(name,pos(tileW-62,17,58,tileH/2-17));
            state=text("",11.5f,ink(0x99),"sans-serif");addView(state,pos(tileW-62,15,58,tileH/2+3));
            setPivotX(px(tileW/2));setPivotY(px(tileH/2));
            control(this,index,1.06f,f->{focused=f;paint();},()->new String[]{"control:"+title,title,detail},action);
        }
        void paint(){
            setBackground(round(focused?focusFill():restFill(),20));
            int discFill=on?0xFF0A84FF:focused?(light()?0x33FFFFFF:0x1A000000):(light()?0x14000000:0x24FFFFFF);
            disc.setBackground(oval(discFill));
            glyph.color(on?Color.WHITE:focused?focusInk():ink(0xE6));
            name.setTextColor(focused?focusInk():ink(0xF2));state.setTextColor(focused?(focusInk()&0x00FFFFFF|0xA6000000):ink(0x99));
        }
        void set(String status,String hero,boolean active){if(!status.contentEquals(state.getText()))state.setText(status);detail=hero;if(active!=on){on=active;paint();}}
    }

    ControlWidget(Context context,WidgetBoard.Host host){
        super(context,host,WidgetBoard.width("control"));
        network=tile("wifi","Wi-Fi",0,0,0,()->open(Settings.ACTION_WIFI_SETTINGS));
        picture=tile("tv","Picture & Sound",1,1,0,()->open(Settings.ACTION_SOUND_SETTINGS));
        accessories=tile("remote","Accessories",2,0,1,()->open("com.google.android.intent.action.CONNECT_INPUT"));
        settings=tile("gear","Settings",3,1,1,()->open(Settings.ACTION_SETTINGS));
        accessories.set("Remotes, speakers, gamepads","Pair a remote, Bluetooth speaker, headphones or a gamepad.",false);
        settings.set("Apps, storage and more","All of the TV's settings: apps, storage, date, language and more.",false);
        update();
    }
    private Tile tile(String kind,String title,int index,int col,int row,Runnable action){Tile t=new Tile(kind,title,index,action);addView(t,pos(tileW,tileH,PAD+col*(tileW+GAP),PAD+row*(tileH+GAP)));return t;}
    private void open(String action){try{host.start(new Intent(action));}catch(RuntimeException e){host.start(new Intent(Settings.ACTION_SETTINGS));}}
    @Override void setActive(boolean value){boolean was=active;super.setActive(value);removeCallbacks(tick);if(value&&!was)post(tick);}
    @Override void refresh(){update();}

    @SuppressWarnings("deprecation")
    void update(){
        Context c=getContext();
        try{
            ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities caps=cm.getNetworkCapabilities(cm.getActiveNetwork());
            if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)){network.glyph.kind("ethernet");network.name.setText("Ethernet");network.set("Connected","Connected by cable. OK opens network settings.",true);}
            else if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){
                WifiInfo info=((WifiManager)c.getApplicationContext().getSystemService(Context.WIFI_SERVICE)).getConnectionInfo();
                String ssid=info==null?null:info.getSSID();if(ssid!=null){ssid=ssid.replace("\"","");if(ssid.startsWith("<")||ssid.isEmpty())ssid=null;}
                int level=info==null?0:WifiManager.calculateSignalLevel(info.getRssi(),4);String signal=new String[]{"Weak","Fair","Good","Excellent"}[Math.max(0,Math.min(3,level))];
                boolean online=caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                network.glyph.kind("wifi");network.name.setText("Wi-Fi");
                network.set(ssid!=null?ssid:online?signal+" signal":"No internet",(ssid!=null?"Connected to "+ssid+". ":"Connected. ")+signal+" signal"+(online?"":", no internet")+". OK opens Wi-Fi settings.",true);
            } else {network.glyph.kind("offline");network.name.setText("Wi-Fi");network.set("Not connected","The TV is offline. OK opens Wi-Fi settings to connect.",false);}
        }catch(RuntimeException e){network.set("Network settings","OK opens network settings.",false);}
        try{
            Display.Mode mode=((android.app.Activity)host.activity()).getWindowManager().getDefaultDisplay().getMode();
            int h=Math.min(mode.getPhysicalHeight(),mode.getPhysicalWidth());String res=h>=2160?"4K":h>=1440?"1440p":h>=1080?"1080p":h>=720?"720p":h+"p";
            String hz=Math.round(mode.getRefreshRate())+" Hz";
            picture.set(res+" · "+hz,"Output "+res+" at "+hz+". OK opens display and sound settings: resolution, HDR, audio output and volume.",false);
        }catch(RuntimeException e){picture.set("Display and audio","OK opens display and sound settings.",false);}
    }
}
