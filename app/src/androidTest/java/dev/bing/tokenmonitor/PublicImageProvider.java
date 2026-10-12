package dev.bing.tokenmonitor;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/** Standalone test-APK process: Android/Java only, one public fixture, read-only. */
public final class PublicImageProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "image/png"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode) || !("/sample".equals(uri.getPath()) || "/invalid".equals(uri.getPath()))) throw new FileNotFoundException("Unsupported public fixture");
        File file = new File(getContext().getCacheDir(), "/sample".equals(uri.getPath()) ? "public-background.png" : "invalid-background.png");
        try (FileOutputStream output = new FileOutputStream(file)) {
            if ("/sample".equals(uri.getPath())) {
                Bitmap bitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888);
                for (int y=0; y<200; y++) for (int x=0; x<400; x++) bitmap.setPixel(x,y,x<100 ? Color.RED : x>=300 ? Color.BLUE : Color.GREEN);
                try { if (!bitmap.compress(Bitmap.CompressFormat.PNG,100,output)) throw new IOException("Public PNG unavailable"); }
                finally { bitmap.recycle(); }
            } else output.write(new byte[]{1,2,3});
        } catch (IOException failure) { throw new FileNotFoundException("Public fixture unavailable"); }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] arguments,String order) { return null; }
    @Override public Uri insert(Uri uri,ContentValues values) { return null; }
    @Override public int delete(Uri uri,String selection,String[] arguments) { return 0; }
    @Override public int update(Uri uri,ContentValues values,String selection,String[] arguments) { return 0; }
}
