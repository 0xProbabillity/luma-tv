package dev.luma.tv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/** Small, feathered displacements restricted to open water in the fjord artwork. */
final class WaterMesh {
    static final int COLUMNS=64,ROWS=40;
    private final float[] points=new float[(COLUMNS+1)*(ROWS+1)*2];
    private final float[] weights=new float[(COLUMNS+1)*(ROWS+1)];
    private final float width,height,amplitude;
    WaterMesh(float width,float height,float unit) {
        this.width=width;this.height=height;amplitude=unit;
        for(int row=0;row<=ROWS;row++)for(int column=0;column<=COLUMNS;column++) {
            float x=(float)column/COLUMNS,y=(float)row/ROWS;
            // These softly bounded pools lie inside water, clear of ridges, islands and shore.
            float mask=Math.max(pool(x,y,.225f,.635f,.30f,.205f),pool(x,y,.43f,.535f,.13f,.07f));
            mask=Math.max(mask,pool(x,y,.65f,.565f,.145f,.033f));
            weights[row*(COLUMNS+1)+column]=mask;
        }
    }
    private float pool(float x,float y,float cx,float cy,float rx,float ry) {
        float dx=(x-cx)/rx,dy=(y-cy)/ry;
        float inside=Math.max(0,1-dx*dx-dy*dy);
        return inside*inside; // Zero slope at the boundary: no moving seams.
    }
    void draw(Canvas canvas,Bitmap bitmap,float seconds,Paint paint) {
        for(int row=0;row<=ROWS;row++) {
            float y=(float)row/ROWS;
            float horizontal=(float)(Math.sin(y*104+seconds*.64)*.65+Math.sin(y*53-seconds*.43)*.4);
            float fade=Math.min(1,seconds/3);
            for(int column=0;column<=COLUMNS;column++) {
                int index=row*(COLUMNS+1)+column;
                float x=(float)column/COLUMNS,weight=weights[index];
                points[index*2]=x*width+weight*amplitude*fade*horizontal;
                points[index*2+1]=y*height+weight*amplitude*fade*.32f*(float)Math.sin(x*34+y*71-seconds*.72);
            }
        }
        canvas.drawBitmapMesh(bitmap,COLUMNS,ROWS,points,0,null,0,paint);
    }
}
