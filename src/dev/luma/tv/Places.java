package dev.luma.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Where the weather is for. Automatic, the default, is the city of the TV's internet connection, the way a weather
 *  site finds it: no location permission, a city-level guess from a free IP lookup (GeoJS, else ipwho.is), kept for
 *  12 hours. The guess is trusted only if its time zone keeps the TV's time, so a VPN abroad does not move the
 *  weather; without one, and offline at first, it is the main city of the TV's time zone (Daylight). Settings →
 *  Weather location can pin any city instead, found with Open-Meteo's geocoding (free, no account or key). */
final class Places {
    static final long STALE=12*3600000L,RETRY=30*60000L;
    private static final String[] LOOKUPS={"https://get.geojs.io/v1/ip/geo.json","https://ipwho.is/"};

    static final class Place {
        final String name,detail,country;final double lat,lon;
        Place(String name,String detail,String country,double lat,double lon){this.name=name;this.detail=detail;this.country=country;this.lat=lat;this.lon=lon;}
        /** "Porto, Portugal": the name with its region and country, for lists. */
        String full(){return detail.isEmpty()?name:name+", "+detail;}
        boolean same(Place o){return o!=null&&Math.abs(lat-o.lat)<.01&&Math.abs(lon-o.lon)<.01;}
        String json(){try{return new JSONObject().put("name",name).put("detail",detail).put("country",country).put("lat",lat).put("lon",lon).toString();}catch(JSONException e){return "";}}
        static Place parse(String raw){
            if(raw==null||raw.isEmpty())return null;
            try{JSONObject o=new JSONObject(raw);return new Place(o.getString("name"),o.optString("detail"),o.optString("country"),o.getDouble("lat"),o.getDouble("lon"));}catch(JSONException e){return null;}
        }
    }

    /** The place the forecast is for, at once and without the network: the pinned city, else the last automatic
     *  one, else the time zone's city (null in an unknown time zone). */
    static Place current(Context context,SharedPreferences prefs){
        Place p=pinned(prefs);if(p==null)p=Place.parse(prefs.getString("place_auto",""));
        return p!=null?p:zone(context);
    }
    static Place pinned(SharedPreferences prefs){return Place.parse(prefs.getString("place_pinned",""));}
    static void pin(SharedPreferences prefs,Place place){prefs.edit().putString("place_pinned",place==null?"":place.json()).remove("weather_at").apply();}
    static Place zone(Context context){double[] p=Daylight.place(context);return p==null?null:new Place(Daylight.city(),"","",p[0],p[1]);}
    /** The automatic place is looked up again after 12 hours, or at once when the TV's time zone changes; after a
     *  failed lookup (offline), not for half an hour. */
    static boolean due(SharedPreferences prefs){
        long now=System.currentTimeMillis();
        return pinned(prefs)==null&&now-prefs.getLong("place_auto_failed",0)>RETRY
            &&(now-prefs.getLong("place_auto_at",0)>STALE||!TimeZone.getDefault().getID().equals(prefs.getString("place_auto_zone","")));
    }

    /** Refreshes the automatic place when it is due. Blocks: call it on WidgetBoard.IO. A failed lookup keeps the
     *  last place. */
    static void locate(SharedPreferences prefs){
        if(!due(prefs))return;
        for(String url:LOOKUPS){
            JSONObject o;try{o=new JSONObject(get(url));}catch(Exception e){continue;}
            if(o.has("success")&&!o.optBoolean("success"))continue;
            String city=o.optString("city").trim();double lat=o.optDouble("latitude"),lon=o.optDouble("longitude");
            if(city.isEmpty()||Double.isNaN(lat)||Double.isNaN(lon))continue;
            Object tz=o.opt("timezone");String zone=tz instanceof JSONObject?((JSONObject)tz).optString("id"):tz instanceof String?(String)tz:"";
            SharedPreferences.Editor e=prefs.edit().putLong("place_auto_at",System.currentTimeMillis()).putString("place_auto_zone",TimeZone.getDefault().getID());
            if(keepsTime(zone))e.putString("place_auto",new Place(city,join(city,o.optString("region"),o.optString("country")),o.optString("country_code"),lat,lon).json());
            else e.remove("place_auto"); // a VPN or a far-off exit: the time zone's city is the better guess
            e.remove("place_auto_failed").apply();return;
        }
        prefs.edit().putLong("place_auto_failed",System.currentTimeMillis()).apply();
    }
    /** Whether a time zone shows the same time as the TV's now (an unknown one is given the benefit of the doubt). */
    private static boolean keepsTime(String zone){
        if(zone==null||zone.isEmpty())return true;
        long now=System.currentTimeMillis();TimeZone z=TimeZone.getTimeZone(zone);
        return (!z.getID().equals("GMT")||zone.equals("GMT"))&&z.getOffset(now)==TimeZone.getDefault().getOffset(now);
    }

    /** Cities matching a name, most populous first. Blocks: call it on WidgetBoard.IO. */
    static ArrayList<Place> search(String query) throws IOException,JSONException {
        ArrayList<Place> out=new ArrayList<>();final String typed=plain(query);
        for(String language:languages(query)){
            ArrayList<Place> found=new ArrayList<>();boolean named=false;
            JSONArray rows=new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=10&format=json&language="+language+"&name="+URLEncoder.encode(query.trim(),"UTF-8"))).optJSONArray("results");
            for(int i=0;rows!=null&&i<rows.length();i++){
                JSONObject r=rows.getJSONObject(i);String name=r.optString("name");if(name.isEmpty()||!r.has("latitude"))continue;
                // Towns and cities only: no peaks, airports, dams, city districts or abandoned places.
                String kind=r.optString("feature_code");if(!kind.isEmpty()&&(!kind.startsWith("PPL")||Arrays.asList("PPLX","PPLH","PPLQ","PPLW","PPLCH").contains(kind)))continue;
                Place p=new Place(name,join(name,r.optString("admin1"),r.optString("country")),r.optString("country_code"),r.getDouble("latitude"),r.getDouble("longitude"));
                boolean dup=false;for(Place q:found)if(q.full().equals(p.full()))dup=true;
                if(!dup){found.add(p);named|=plain(name).startsWith(typed);}
            }
            // A language that finds only other names ("Минск" in English finds Minskoye) leaves the next one a try.
            if(out.isEmpty()||named)out=found;
            if(named)break;
        }
        // Places found by another of their names (Chicago finds Willard, Ohio) after those called what was typed.
        Collections.sort(out,(x,y)->Boolean.compare(!plain(x.name).startsWith(typed),!plain(y.name).startsWith(typed)));
        return out;
    }
    /** Lower case without accents: "Malmö" and "malmo" match. */
    private static String plain(String s){return java.text.Normalizer.normalize(s.trim(),java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);}
    /** Open-Meteo finds names in the language it is asked for: English first; then, for a name in another alphabet,
     *  the TV's language and the languages written in that alphabet ("Берлин" is found in Russian, "Київ" in
     *  Ukrainian). The cities then come back in that language. */
    private static Set<String> languages(String query){
        LinkedHashSet<String> out=new LinkedHashSet<>();out.add("en");
        Character.UnicodeScript script=null;
        for(int i=0;i<query.length()&&script==null;i=query.offsetByCodePoints(i,1)){
            int c=query.codePointAt(i);if(!Character.isLetter(c))continue;
            Character.UnicodeScript s=Character.UnicodeScript.of(c);if(s!=Character.UnicodeScript.LATIN)script=s;
        }
        if(script==null)return out;
        out.add(Locale.getDefault().getLanguage());
        switch(script){
            case CYRILLIC:out.addAll(Arrays.asList("ru","uk","bg","sr","be","kk","mk"));break;
            case GREEK:out.add("el");break;
            case ARABIC:out.addAll(Arrays.asList("ar","fa","ur"));break;
            case HEBREW:out.add("he");break;
            case HAN:out.addAll(Arrays.asList("zh","ja"));break;
            case HIRAGANA:case KATAKANA:out.add("ja");break;
            case HANGUL:out.add("ko");break;
            case THAI:out.add("th");break;
            case GEORGIAN:out.add("ka");break;
            case ARMENIAN:out.add("hy");break;
            case DEVANAGARI:out.addAll(Arrays.asList("hi","mr","ne"));break;
            default:break;
        }
        return out;
    }
    /** "Region, Country", leaving out parts that repeat the city or each other. */
    private static String join(String city,String region,String country){
        StringBuilder b=new StringBuilder();
        for(String part:new String[]{region,country}){part=part==null?"":part.trim();if(part.isEmpty()||part.equalsIgnoreCase(city)||b.toString().equalsIgnoreCase(part))continue;if(b.length()>0)b.append(", ");b.append(part);}
        return b.toString();
    }
    static String get(String url) throws IOException {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(10000);c.setRequestProperty("User-Agent","LumaTV");
        try(InputStream in=c.getInputStream();BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){StringBuilder sb=new StringBuilder();String line;while((line=r.readLine())!=null)sb.append(line);return sb.toString();}
        finally{c.disconnect();}
    }
}
