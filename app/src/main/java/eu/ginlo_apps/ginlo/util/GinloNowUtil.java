package eu.ginlo_apps.ginlo.util;

import eu.ginlo_apps.ginlo.activity.register.IntroBaseActivity;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.log.LogUtil;

public class GinloNowUtil {

    public final static String GINLO_NOW_INVITATION = "IntroBaseActivity.GinloNowInvitation";

    private SimsMeApplication mApplication;
    private PreferencesController mPreferencesController;
    private String ginloNowInvitationString;

    public GinloNowUtil() {
        mApplication = SimsMeApplication.getInstance();
        mPreferencesController = mApplication.getPreferencesController();
    }

    /**
     *
     * @return
     */
    public boolean haveGinloNowInvitation() {
        getGinloNowInvitationString();
        return !StringUtil.isNullOrEmpty(ginloNowInvitationString);
    }

    public String getGinloNowInvitationString() {
        this.ginloNowInvitationString = mPreferencesController.getSharedPreferences().getString(GINLO_NOW_INVITATION, "");
        return ginloNowInvitationString;
    }

    public void setGinloNowInvitationString(String ginloNowInvitationString) {
        mPreferencesController.getSharedPreferences().edit().putString(GINLO_NOW_INVITATION, ginloNowInvitationString).apply();
        this.ginloNowInvitationString = ginloNowInvitationString;
    }

    public void resetGinloNowInvitation() {
        mPreferencesController.getSharedPreferences().edit().remove(GINLO_NOW_INVITATION).apply();
        this.ginloNowInvitationString = "";
    }
}
