// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.context;

import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.ContactControllerBusiness;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.controller.PreferencesControllerBusiness;
import eu.ginlo_apps.ginlo.controller.message.GroupChatController;
import eu.ginlo_apps.ginlo.greendao.CompanyContactDao;
import eu.ginlo_apps.ginlo.util.fts.FtsDatabaseOpenHelper;

public class SimsMeApplicationBusiness extends SimsMeApplication {

    private CompanyContactDao mCompanyContactDao;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void initControllers() {
        super.initControllers();
    }

    @Override
    protected void initDaos() {
        super.initDaos();
        mCompanyContactDao = mDaoSession.getCompanyContactDao();
    }

    @Override
    public ContactController getContactController() {
        if (mContactController == null) {
            mContactController = new ContactControllerBusiness(this);
        }

        return mContactController;
    }

    @Override
    public GroupChatController getGroupChatController() {
        if (groupChatController == null) {
            groupChatController = new GroupChatController(this);
        }
        return groupChatController;
    }

    @Override
    public PreferencesController getPreferencesController() {
        if (preferencesController == null) {
            preferencesController = new PreferencesControllerBusiness(this);
        }

        return preferencesController;
    }

    public CompanyContactDao getCompanyContactDao() {
        return mCompanyContactDao;
    }

    @Override
    public void deleteAll() {
        FtsDatabaseOpenHelper.deleteFtsDatabase(this);
        super.deleteAll();
    }

    @Override
    public void safeDeleteAccount() {
        FtsDatabaseOpenHelper.deleteFtsDatabase(this);
        super.safeDeleteAccount();
    }
}
