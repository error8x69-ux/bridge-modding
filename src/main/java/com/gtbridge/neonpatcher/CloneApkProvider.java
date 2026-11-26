package com.gtbridge.neonpatcher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

public final class CloneApkProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) {
        return apk(uri) == null ? null : BridgeRepository.APK;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        File file = apk(uri);
        if (file == null || !"r".equals(mode)) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] args, String order) {
        File file = apk(uri);
        if (file == null) return null;
        String[] columns = projection == null
            ? new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
            : projection;
        var cursor = new MatrixCursor(columns, 1);
        if (file.isFile()) {
            Object[] row = new Object[columns.length];
            for (int index = 0; index < columns.length; index++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[index])) row[index] = file.getName();
                if (OpenableColumns.SIZE.equals(columns[index])) row[index] = file.length();
            }
            cursor.addRow(row);
        }
        return cursor;
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override public int delete(Uri uri, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }

    private File apk(Uri uri) {
        String path = uri.getPath();
        if (("/" + BridgeRepository.CLONE_FILE).equals(path)) {
            return new File(getContext().getCacheDir(), BridgeRepository.CLONE_FILE);
        }
        if (("/" + BridgeRepository.UPDATE_FILE).equals(path)) {
            return new File(getContext().getCacheDir(), BridgeRepository.UPDATE_FILE);
        }
        return null;
    }
}
