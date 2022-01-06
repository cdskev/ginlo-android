// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model;

import androidx.annotation.NonNull;
import eu.ginlo_apps.ginlo.greendao.Contact;

/**
 * Created by SGA on 21.09.2016.
 */

public class ContactMessageInfo extends Contact {
    private final Long mDateSent;
    private final Long mDateDownloaded;
    private final Long mDateRead;
    private final boolean mIsFirstElementOfType;

    public ContactMessageInfo(@NonNull final Contact contact, final long dateSent, final long dateDonwloaded, final long dateRead, final boolean isFirstElementOfType) {
        super(contact);
        mDateSent = dateSent;
        mDateDownloaded = dateDonwloaded;
        mDateRead = dateRead;
        mIsFirstElementOfType = isFirstElementOfType;
    }

    public long getDateSent() {
        return mDateSent;
    }

    public long getDateDownloaded() {
        return mDateDownloaded;
    }

    public long getDateRead() {
        return mDateRead;
    }

    public boolean getIstFirstElementOfType() {
        return mIsFirstElementOfType;
    }
}


