package dev.luma.tv;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;

/** Tiny, preloaded earcons: no audio focus, network, music interruption or idle work. */
final class SonicFeedback {
    private final SoundPool pool;
    private int step, open;
    private static final float[] VARIATIONS={-.008f,.006f,0,.010f};
    private boolean stepReady, openReady, released;
    private long lastStep;
    private int variation;
    SonicFeedback(Context context) {
        pool=new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        pool.setOnLoadCompleteListener((p,id,status)-> {
            if(status==0){if(id==step)stepReady=true;if(id==open)openReady=true;}
        });
        step=pool.load(context,R.raw.soft_step,1);
        open=pool.load(context,R.raw.soft_open,1);
    }
    void step(float position,int direction) {
        long now=SystemClock.uptimeMillis();
        if(released || !stepReady || now-lastStep<55)return;
        float level=now-lastStep<135?.57f:.76f;
        lastStep=now;
        // A restrained stereo position and tiny timbre variation prevent a harsh machine-gun effect.
        float pan=(Math.max(0,Math.min(1,position))-.5f)*.22f;
        float rate=1+direction*.012f+VARIATIONS[variation++%VARIATIONS.length];
        pool.play(step,level*(1-Math.max(0,pan)),level*(1+Math.min(0,pan)),1,0,rate);
    }
    void open(){if(!released && openReady)pool.play(open,.67f,.67f,2,0,1);}
    void release(){released=true;pool.release();}
}
