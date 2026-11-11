package dev.madusha.shangos;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * The door for "Your photo". Only a computer connected over adb can use it:
 * {@code adb shell content write --uri content://dev.madusha.shangos.photo/wallpaper < photo.jpg}
 * (that is what {@code shangos.sh wallpaper} runs). The manifest protects it with the DUMP
 * permission, which the adb shell holds and apps cannot get, so no installed app can change your
 * wallpaper. Write-only: nothing can read the photo back through here.
 */
public final class PhotoProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!mode.contains("w") || mode.contains("r"))
            throw new FileNotFoundException("write only");
        Context context = getContext();
        if (context == null) throw new FileNotFoundException();
        try {
            return ParcelFileDescriptor.open(
                    PhotoStore.incoming(context),
                    ParcelFileDescriptor.MODE_WRITE_ONLY
                            | ParcelFileDescriptor.MODE_CREATE
                            | ParcelFileDescriptor.MODE_TRUNCATE,
                    new Handler(Looper.getMainLooper()),
                    e -> WidgetBoard.IO.execute(() -> PhotoStore.accept(context)));
        } catch (IOException e) {
            throw new FileNotFoundException("could not open");
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        Context context = getContext();
        if (context == null) return 0;
        PhotoStore.remove(context);
        return 1;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] p, String s, String[] a, String o) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
