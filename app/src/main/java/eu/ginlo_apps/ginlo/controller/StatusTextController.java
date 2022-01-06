// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.controller;

import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.DaoMaster;
import eu.ginlo_apps.ginlo.greendao.DaoSession;
import eu.ginlo_apps.ginlo.greendao.StatusText;
import eu.ginlo_apps.ginlo.greendao.StatusTextDao;
import java.util.ArrayList;
import org.greenrobot.greendao.database.Database;

public class StatusTextController {
    private final StatusTextDao statusTextDao;

    public StatusTextController(SimsMeApplication application) {
        Database db = application.getDataBase();

        DaoMaster daoMaster = new DaoMaster(db);
        DaoSession daoSession = daoMaster.newSession();

        statusTextDao = daoSession.getStatusTextDao();
    }

    void saveStatusText(String text)
            throws LocalizedException {
        for (StatusText st : getAllStatusTexts()) {
            if (st.getText().equals(text)) {
                return;
            }
        }

        StatusText statusText = new StatusText();

        statusText.setText(text);
        synchronized (statusTextDao) {
            statusTextDao.insert(statusText);
        }
    }

    public ArrayList<StatusText> getAllStatusTexts() {
        synchronized (statusTextDao) {
            return (ArrayList<StatusText>) statusTextDao.loadAll();
        }
    }
}
