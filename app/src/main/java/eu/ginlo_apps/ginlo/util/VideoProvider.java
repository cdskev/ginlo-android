// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.util;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import eu.ginlo_apps.ginlo.BuildConfig;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class VideoProvider
        extends ContentProvider {

    public static final Uri CONTENT_URI_BASE = Uri.parse("content://" + BuildConfig.APPLICATION_ID
            + ".util.VideoProvider.files");

    private static final String VIDEO_MIME_TYPE = "video/mp4";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(final Uri uri) {
        return VIDEO_MIME_TYPE;
    }

    /**
     * @throws FileNotFoundException [!EXC_DESCRIPTION!]
     */
    @Override
    public ParcelFileDescriptor openFile(final Uri uri,
                                         final String mode)
            throws FileNotFoundException {
        File f = new File(uri.getPath());

        if (f.exists()) {
            return (ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY));
        }

        throw new FileNotFoundException(uri.getPath());
    }

    /**
     * @throws UnsupportedOperationException [!EXC_DESCRIPTION!]
     */
    @Override
    public int delete(final Uri uri,
                      final String selection,
                      final String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    /**
     * @throws UnsupportedOperationException [!EXC_DESCRIPTION!]
     */
    @Override
    public Uri insert(final Uri uri,
                      final ContentValues values) {
        throw new UnsupportedOperationException();
    }

    /**
     * @throws UnsupportedOperationException [!EXC_DESCRIPTION!]
     */
    @Override
    public Cursor query(final Uri uri,
                        final String[] projection,
                        final String selection,
                        final String[] selectionArgs,
                        final String sortOrder) {
        return null;
    }

    /**
     * @throws UnsupportedOperationException [!EXC_DESCRIPTION!]
     */
    @Override
    public int update(final Uri uri,
                      final ContentValues values,
                      final String selection,
                      final String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
