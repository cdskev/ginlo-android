// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.concurrent.manager;

import eu.ginlo_apps.ginlo.concurrent.listener.ConcurrentTaskListener;
import eu.ginlo_apps.ginlo.concurrent.manager.TaskManager;
import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import eu.ginlo_apps.ginlo.concurrent.task.CreateNotificationTask;
import eu.ginlo_apps.ginlo.concurrent.task.RefreshChatOverviewTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import java.util.List;

public class ChatOverviewTaskManager extends TaskManager {

    public ConcurrentTask executeRefreshChatOverviewTask(SimsMeApplication application,
                                                         ConcurrentTaskListener listener,
                                                         List<String> chatsToRefresh,
                                                         boolean refresh) {
        ConcurrentTask task = new RefreshChatOverviewTask(application, chatsToRefresh, refresh);

        task.addListener(listener);
        execute(task);

        return task;
    }

    public void executeCreateNotificationChatTask(SimsMeApplication context) {
        ConcurrentTask task = new CreateNotificationTask(context);

        execute(task);
    }
}
