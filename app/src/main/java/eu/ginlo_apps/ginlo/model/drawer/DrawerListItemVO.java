// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model.drawer;

public class DrawerListItemVO {
    private final String mContentDescription;
    private final String mTitle;
    private final String mHint;
    private final Class mNextActivity;
    private final int mImageID;
    private final boolean mIsAbsent;

    public DrawerListItemVO(String title,
                            String hint,
                            Class nextActivity,
                            int imageID,
                            String contentDescription,
                            boolean isAbsent) {
        mTitle = title;
        mHint = hint;
        mNextActivity = nextActivity;
        mImageID = imageID;
        mContentDescription = contentDescription;
        mIsAbsent = isAbsent;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getHint() {
        return mHint;
    }

    public Class getNextActivity() {
        return mNextActivity;
    }

    public int getImage() {
        return mImageID;
    }

    public String getContentDescription() {
        return mContentDescription;
    }

    public boolean getIsAbsent() {
        return mIsAbsent;
    }
}
