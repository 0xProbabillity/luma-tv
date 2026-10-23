package dev.luma.tv;

/** Checks Devices.identify against the names real devices announce over HDMI-CEC. Run from the repository root:
 *    javac -d build/test src/dev/luma/tv/Devices.java tools/DevicesTest.java && java -cp build/test dev.luma.tv.DevicesTest */
public final class DevicesTest {
    static int failures;
    static void expect(String osd,int vendor,int type,String name,String glyph){
        Devices.Device d=Devices.identify(osd,vendor,type);
        boolean ok=d.name.equals(name)&&d.glyph.equals(glyph);
        if(!ok)failures++;
        System.out.println((ok?"ok    ":"FAIL  ")+'"'+osd+"\" → "+d.name+" ("+d.glyph+")"+(ok?"":"   expected "+name+" ("+glyph+")"));
    }
    public static void main(String[] args){
        int P=Devices.PLAYBACK,A=Devices.AUDIO,R=Devices.RECORDER,T=Devices.TUNER;
        expect("PS5",Devices.SONY,P,"PlayStation 5","gamepad");
        expect("PS4",Devices.SONY,P,"PlayStation 4","gamepad");
        expect("PlayStation 4",Devices.SONY,P,"PlayStation 4","gamepad");
        expect("PS3",Devices.SONY,P,"PlayStation 3","gamepad");
        expect("Xbox Series X",-1,P,"Xbox Series X","gamepad");
        expect("XboxSeriesS",-1,P,"Xbox Series S","gamepad");
        expect("Xbox One",-1,P,"Xbox One","gamepad");
        expect("NintendoSwitch",-1,P,"Nintendo Switch","switch");
        expect("Switch",-1,P,"Nintendo Switch","switch");
        expect("Steam Deck",-1,P,"Steam Deck","gamepad");
        expect("SHIELD",-1,P,"NVIDIA SHIELD","box");
        expect("Apple TV",Devices.APPLE,P,"Apple TV","box");
        expect("",Devices.APPLE,P,"Apple TV","box");
        expect("Chromecast",Devices.GOOGLE,P,"Chromecast","box");
        expect("Google TV",Devices.GOOGLE,P,"Google TV Streamer","box");
        expect("Fire TV",-1,P,"Fire TV","box");
        expect("AFTMM",-1,P,"Fire TV","box");
        expect("Roku Ultra",-1,P,"Roku Ultra","box");
        expect("Mi TV Stick",-1,P,"Mi TV Stick","box");
        expect("HT-A5000",Devices.SONY,A,"HT-A5000","speaker");
        expect("Sonos Arc",-1,A,"Sonos Arc","speaker");
        expect("BDP-S6700",Devices.SONY,P,"BDP-S6700","disc");
        expect("DIGITAL_RECEIVER",-1,T,"Digital Receiver","speaker");
        expect("",-1,R,"Set-top box","box");
        expect("",-1,-1,"HDMI device","hdmi");
        expect("MyGadget",-1,P,"My Gadget","box");
        System.out.println(failures==0?"\nAll passed.":"\n"+failures+" failed.");
        System.exit(failures==0?0:1);
    }
}
