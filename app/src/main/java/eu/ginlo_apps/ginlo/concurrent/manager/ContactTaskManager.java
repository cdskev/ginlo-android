// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.concurrent.manager;

import eu.ginlo_apps.ginlo.concurrent.listener.ConcurrentTaskListener;
import eu.ginlo_apps.ginlo.concurrent.manager.TaskManager;
import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import eu.ginlo_apps.ginlo.concurrent.task.SyncAllContactsTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;

public class ContactTaskManager
        extends TaskManager {

    public ConcurrentTask executeSyncAllContactsTask(SimsMeApplication context,
                                                     ConcurrentTaskListener listener, boolean mergeOldContacts, final boolean hasPhonebookPermission) {
        ConcurrentTask task = new SyncAllContactsTask(context, mergeOldContacts, hasPhonebookPermission);

        task.addListener(listener);
        execute(task);
        return task;
    }


}
