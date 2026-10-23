package dev.luma.tv;

import java.util.Locale;

/** Recognises what is plugged into an HDMI port from what the device tells the TV over HDMI-CEC: its on-screen
 *  name (OSD name, at most 14 characters, e.g. "PS5" or "NintendoSwitch"), its vendor ID (an IEEE OUI) and its
 *  CEC device type. Plain Java with no Android classes, so tools/DevicesTest.java checks it on a computer. */
final class Devices {
    /** CEC device types (HDMI-CEC 1.4, table 11). */
    static final int TV=0,RECORDER=1,TUNER=3,PLAYBACK=4,AUDIO=5;
    /** CEC vendor IDs, as listed by libCEC. */
    static final int SONY=0x080046,APPLE=0x0010FA,GOOGLE=0x001A11,PANASONIC=0x008045,PHILIPS=0x00903E,LG=0x00E091,
        SAMSUNG=0x0000F0,YAMAHA=0x00A0DE,DENON=0x0005CD,ONKYO=0x0009B0,PIONEER=0x00E036,HARMAN=0x9C645E;

    static final class Device {
        final String name,glyph;final int color;
        Device(String name,String glyph,int color){this.name=name;this.glyph=glyph;this.color=color;}
    }
    private static final int BLUE=0xFF0A84FF;

    /** osd: the name the device announces (may be empty); vendor: its vendor ID, or -1 when unknown; type: its CEC
     *  device type, or -1. Never null: an unknown device keeps its own name with an HDMI glyph. */
    static Device identify(String osd,int vendor,int type){
        String raw=osd==null?"":osd.trim(),n=raw.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]","");
        // Consoles first: their names are unambiguous and they deserve their own colours.
        if(n.matches("ps5|playstation5.*"))return new Device("PlayStation 5","gamepad",0xFF0070D1);
        if(n.matches("ps4|playstation4.*"))return new Device("PlayStation 4","gamepad",0xFF0070D1);
        if(n.matches("ps3|playstation3.*"))return new Device("PlayStation 3","gamepad",0xFF0070D1);
        if(n.startsWith("playstation")||n.equals("ps")||vendor==SONY&&type==PLAYBACK&&n.startsWith("ps"))return new Device("PlayStation","gamepad",0xFF0070D1);
        if(n.startsWith("xbox")){
            String model=n.contains("seriesx")?"Xbox Series X":n.contains("seriess")?"Xbox Series S":n.contains("series")?"Xbox Series X|S":n.contains("one")?"Xbox One":"Xbox";
            return new Device(model,"gamepad",0xFF107C10);
        }
        if(n.contains("nintendo")||n.equals("switch")||n.equals("switch2"))return new Device(n.contains("2")?"Nintendo Switch 2":"Nintendo Switch","switch",0xFFE60012);
        if(n.contains("steamdeck"))return new Device("Steam Deck","gamepad",0xFF1A9FFF);
        if(n.contains("shield"))return new Device("NVIDIA SHIELD","box",0xFF76B900);
        // Streaming players.
        if(n.contains("appletv")||vendor==APPLE&&type==PLAYBACK)return new Device("Apple TV","box",0xFF3A3A3C);
        if(n.contains("chromecast")||n.equals("googletv")||n.contains("googletvstreamer"))return new Device(n.contains("chromecast")?"Chromecast":"Google TV Streamer","box",0xFF4285F4);
        if(n.startsWith("firetv")||n.startsWith("aft")&&n.length()<=8||n.contains("amazonfire"))return new Device("Fire TV","box",0xFFFF9900);
        if(n.startsWith("roku"))return new Device(pretty(raw),"box",0xFF6F1AB1);
        if(n.contains("mitvstick")||n.contains("mibox")||n.contains("xiaomitv"))return new Device(pretty(raw),"box",0xFFFF6900);
        // Sound: a soundbar or an AV receiver announces itself as an audio system.
        if(type==AUDIO||n.contains("soundbar")||n.matches(".*(receiver|avr|sonos|bose|harman).*"))return new Device(raw.isEmpty()?"Sound system":pretty(raw),"speaker",BLUE);
        if(n.matches(".*(bluray|bdplayer|bdp\\d*|dvd).*"))return new Device(raw.isEmpty()?"Blu-ray player":pretty(raw),"disc",BLUE);
        if(n.matches(".*(pc|laptop|macbook|computer|desktop).*"))return new Device(pretty(raw),"tv",BLUE);
        if(type==TUNER||type==RECORDER)return new Device(raw.isEmpty()?"Set-top box":pretty(raw),"box",BLUE);
        return new Device(raw.isEmpty()?"HDMI device":pretty(raw),type==PLAYBACK?"box":"hdmi",BLUE);
    }
    /** "NintendoSwitch" → "Nintendo Switch", "DIGITAL_RECEIVER" → "Digital Receiver"; names with spaces stay. */
    static String pretty(String raw){
        String s=raw.replace('_',' ').trim();
        if(!s.contains(" "))s=s.replaceAll("([a-z])([A-Z])","$1 $2");
        if(s.equals(s.toUpperCase(Locale.ENGLISH))&&s.length()>4&&!s.matches(".*\\d.*")){ // model numbers (HT-A5000) stay as they are
            StringBuilder b=new StringBuilder();for(String w:s.toLowerCase(Locale.ENGLISH).split(" "))b.append(b.length()>0?" ":"").append(w.isEmpty()?w:Character.toUpperCase(w.charAt(0))+w.substring(1));s=b.toString();}
        return s;
    }
}
