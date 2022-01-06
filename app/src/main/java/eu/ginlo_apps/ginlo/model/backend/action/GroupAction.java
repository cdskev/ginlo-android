// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.model.backend.action;

import eu.ginlo_apps.ginlo.model.backend.action.Action;

/**
 * Created by Florian on 08.12.16.
 */

public class GroupAction extends Action {

    private String mGroupGuid;

    private String mSenderGuid;

    private String mNickName;

    private String[] mGuids;

    /**
     * cons
     */
    GroupAction() {
        super();
    }

    /**
     * @return
     */
    public String getGroupGuid() {
        return mGroupGuid;
    }

    /**
     * @param mGroupGuid
     */
    public void setGroupGuid(String mGroupGuid) {
        this.mGroupGuid = mGroupGuid;
    }

    /**
     * @return
     */
    public String getSenderGuid() {
        return mSenderGuid;
    }

    /**
     * @param mSenderGuid
     */
    public void setSenderGuid(String mSenderGuid) {
        this.mSenderGuid = mSenderGuid;
    }

    /**
     * @return
     */
    public String getNickName() {
        return mNickName;
    }

    /**
     * @param mNickName
     */
    public void setNickName(String mNickName) {
        this.mNickName = mNickName;
    }

    /**
     * @return
     */
    public String[] getGuids() {
        if (mGuids != null) {
            return mGuids.clone();
        } else {
            return null;
        }
    }

    /**
     * @param guids
     */
    public void setGuids(String[] guids) {
        if (guids != null) {
            this.mGuids = guids.clone();
        }
    }
}
