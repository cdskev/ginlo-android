// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.concurrent.manager;

import eu.ginlo_apps.ginlo.concurrent.listener.ConcurrentTaskListener;
import eu.ginlo_apps.ginlo.concurrent.manager.TaskManager;
import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import eu.ginlo_apps.ginlo.concurrent.task.SyncAllContactsTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;

public class ContactTaskManager
        extends TaskManager {

    public ConcurrentTask executeSyncAllContactsTask(SimsMeApplication context,
                                                     ConcurrentTaskListener listener, boolean mergeOldContacts, final boolean hasPhonebookPermission, boolean onlyTenants) {
        ConcurrentTask task = new SyncAllContactsTask(context, mergeOldContacts, hasPhonebookPermission, onlyTenants);

        task.addListener(listener);
        execute(task);
        return task;
    }

    public ConcurrentTask executeSyncAllContactsTask(SimsMeApplication context,
                                                     ConcurrentTaskListener listener, boolean mergeOldContacts, final boolean hasPhonebookPermission) {
        return executeSyncAllContactsTask(context, listener, mergeOldContacts, hasPhonebookPermission, false);
    }
}
