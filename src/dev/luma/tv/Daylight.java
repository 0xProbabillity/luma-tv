package dev.luma.tv;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Sunrise and sunset at the main city of the TV's time zone (res/raw/zones.txt): no location permission,
 *  no network. Accurate to a few minutes, which is all a wallpaper needs. */
final class Daylight {
    private final double lat,lon;
    private final TimeZone zone;
    Daylight(Context context){
        zone=TimeZone.getDefault();
        double[] place=lookup(context,zone.getID());
        if(place==null)place=new double[]{45,zone.getRawOffset()/3600000.0*15}; // unknown zone: its offset's meridian
        lat=place[0];lon=place[1];
    }
    private static double[] lookup(Context context,String id){
        int res=context.getResources().getIdentifier("zones","raw",context.getPackageName());if(res==0)return null;
        try(BufferedReader r=new BufferedReader(new InputStreamReader(context.getResources().openRawResource(res),StandardCharsets.UTF_8))){
            String line;while((line=r.readLine())!=null){if(!line.startsWith(id+"|"))continue;String[] f=line.split("\\|");return new double[]{Double.parseDouble(f[1]),Double.parseDouble(f[2])};}
        }catch(IOException|RuntimeException ignored){}
        return null;
    }
    boolean isDay(long now){long[] t=times(now);return now>=t[0]&&now<t[1];}
    /** The next sunrise or sunset after now (or the next local midnight in a polar day or night). */
    long nextChange(long now){
        long[] t=times(now);if(now<t[0])return t[0];if(now<t[1])return t[1];
        long[] tomorrow=times(now+86400000L);return tomorrow[0]>now?tomorrow[0]:now+3600000L;
    }
    /** Today's {sunrise, sunset} in epoch ms. Polar night: both at noon. Polar day: the whole local day. */
    long[] times(long now){
        Calendar c=Calendar.getInstance(zone);c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY,12);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);
        long noon=c.getTimeInMillis();int n=c.get(Calendar.DAY_OF_YEAR);
        Calendar utc=Calendar.getInstance(TimeZone.getTimeZone("UTC"));utc.clear();utc.set(c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH));
        long midnight=utc.getTimeInMillis();
        double rise=event(n,true),set=event(n,false);
        if(Double.isNaN(rise)||Double.isNaN(set))return rise==Double.NEGATIVE_INFINITY?new long[]{noon-43200000L,noon+43200000L}:new long[]{noon,noon};
        return new long[]{near(midnight+(long)(rise*3600000),noon),near(midnight+(long)(set*3600000),noon)};
    }
    private static long near(long t,long noon){while(t-noon>43200000L)t-=86400000L;while(noon-t>43200000L)t+=86400000L;return t;}
    /** UTC hour of sunrise or sunset (the Almanac for Computers algorithm). NaN: polar night; -Infinity: polar day. */
    private double event(int day,boolean rising){
        double lngHour=lon/15,t=day+((rising?6:18)-lngHour)/24;
        double m=0.9856*t-3.289;
        double l=norm(m+1.916*sin(m)+0.020*sin(2*m)+282.634,360);
        double ra=norm(Math.toDegrees(Math.atan(0.91764*Math.tan(Math.toRadians(l)))),360);
        ra=(ra+Math.floor(l/90)*90-Math.floor(ra/90)*90)/15;
        double sinDec=0.39782*sin(l),cosDec=Math.cos(Math.asin(sinDec));
        double cosH=(Math.cos(Math.toRadians(90.833))-sinDec*sin(lat))/(cosDec*Math.cos(Math.toRadians(lat)));
        if(cosH>1)return Double.NaN;if(cosH<-1)return Double.NEGATIVE_INFINITY;
        double h=(rising?360-Math.toDegrees(Math.acos(cosH)):Math.toDegrees(Math.acos(cosH)))/15;
        return norm(h+ra-0.06571*t-6.622-lngHour,24);
    }
    private static double sin(double degrees){return Math.sin(Math.toRadians(degrees));}
    private static double norm(double v,double range){v%=range;return v<0?v+range:v;}
}
