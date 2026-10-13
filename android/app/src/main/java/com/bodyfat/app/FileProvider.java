package com.bodyfat.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

public class FileProvider extends ContentProvider {
    public static final String AUTHORITY = "com.bodyfat.app.fileprovider";
    private static final String[] COLUMNS = { "_display_name", "_size" };

    @Override public boolean onCreate() { return true; }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        File f = fileFor(uri);
        if (f == null) return null;
        MatrixCursor c = new MatrixCursor(COLUMNS);
        c.addRow(new Object[]{ f.getName(), f.length() });
        return c;
    }

    @Override public String getType(Uri uri) { return "image/jpeg"; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = fileFor(uri);
        if (f == null) throw new FileNotFoundException("no file");
        int m = ParcelFileDescriptor.MODE_READ_ONLY;
        if ("rw".equals(mode) || "rwt".equals(mode)) m = ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE;
        else if ("w".equals(mode) || "wt".equals(mode) || "wa".equals(mode)) m = ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE;
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        return ParcelFileDescriptor.open(f, m);
    }

    private File fileFor(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("..")) return null;
        File dir = new File(getContext().getFilesDir(), "photos");
        return new File(dir, name);
    }
}
