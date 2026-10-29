package com.madushas.shangos;

import android.content.*;
import android.net.*;
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
    private Tile tile(String kind,String title,int index,int col,int row,Runnable action){Tile t=new Tile(kind,title,tileW,tileH,index,action);addView(t,pos(tileW,tileH,PAD+col*(tileW+GAP),PAD+row*(tileH+GAP)));return t;}
    private void open(String action){try{host.start(new Intent(action));}catch(RuntimeException e){host.start(new Intent(Settings.ACTION_SETTINGS));}}
    @Override void setActive(boolean value){boolean was=active;super.setActive(value);removeCallbacks(tick);if(value&&!was)post(tick);}
    @Override void refresh(){update();}

    void update(){
        Context c=getContext();
        try{
            ConnectivityManager cm=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities caps=cm.getNetworkCapabilities(cm.getActiveNetwork());
            if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)){network.glyph.kind("ethernet");network.name.setText("Ethernet");network.set("Connected","Connected by cable. OK opens network settings.",true);}
            else if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){
                // Signal strength comes from the connection itself (API 29+): no Wi-Fi permission, and no network name (reading
                // the name needs the location permission, which this app does not ask for).
                int rssi=caps.getSignalStrength();
                String signal=rssi==Integer.MIN_VALUE?"":rssi>=-55?"Excellent":rssi>=-67?"Good":rssi>=-78?"Fair":"Weak";
                boolean online=caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                network.glyph.kind("wifi");network.name.setText("Wi-Fi");
                network.set(!online?"No internet":signal.isEmpty()?"Connected":signal+" signal","Connected."+(signal.isEmpty()?"":" "+signal+" signal")+(online?"":", no internet")+". OK opens Wi-Fi settings.",true);
            } else {network.glyph.kind("offline");network.name.setText("Wi-Fi");network.set("Not connected","The TV is offline. OK opens Wi-Fi settings to connect.",false);}
        }catch(RuntimeException e){network.set("Network settings","OK opens network settings.",false);}
        try{
            Display.Mode mode=host.activity().getDisplay().getMode();
            int h=Math.min(mode.getPhysicalHeight(),mode.getPhysicalWidth());String res=h>=2160?"4K":h>=1440?"1440p":h>=1080?"1080p":h>=720?"720p":h+"p";
            String hz=Math.round(mode.getRefreshRate())+" Hz";
            picture.set(res+" · "+hz,"Output "+res+" at "+hz+". OK opens display and sound settings: resolution, HDR, audio output and volume.",false);
        }catch(RuntimeException e){picture.set("Display and audio","OK opens display and sound settings.",false);}
    }
}
