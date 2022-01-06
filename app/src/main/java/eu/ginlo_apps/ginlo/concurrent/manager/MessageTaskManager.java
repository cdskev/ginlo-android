// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.concurrent.manager;

import eu.ginlo_apps.ginlo.concurrent.listener.ConcurrentTaskListener;
import eu.ginlo_apps.ginlo.concurrent.manager.TaskManager;
import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import eu.ginlo_apps.ginlo.concurrent.task.GetMessagesTask;
import eu.ginlo_apps.ginlo.concurrent.task.QueryDatabaseTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.message.contracts.QueryDatabaseListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import java.util.List;
import org.greenrobot.greendao.query.QueryBuilder;

public class MessageTaskManager
        extends TaskManager {

    public ConcurrentTask executeGetMessageTask(SimsMeApplication application,
                                                ConcurrentTaskListener listener,
                                                boolean useLazyMsgService,
                                                boolean useInBackground) {
        ConcurrentTask task = new GetMessagesTask(application, useLazyMsgService, useInBackground, false);

        task.addListener(listener);
        execute(task);

        return task;
    }

    public ConcurrentTask getMessageTask(SimsMeApplication application,
                                         ConcurrentTaskListener listener,
                                         boolean useLazyMsgService,
                                         boolean useInBackground,
                                         boolean onlyPrio1Msg) {
        ConcurrentTask task = new GetMessagesTask(application, useLazyMsgService, useInBackground, onlyPrio1Msg);

        task.addListener(listener);

        return task;
    }

    public <T> void executeQueryDatabaseTask(QueryBuilder<T> queryBuilder,
                                             final int mode,
                                             final QueryDatabaseListener resultListener) {
        ConcurrentTaskListener listener = new ConcurrentTaskListener() {
            @Override
            public void onStateChanged(final ConcurrentTask task,
                                       final int state) {
                if (state == ConcurrentTask.STATE_COMPLETE) {
                    switch (mode) {
                        case QueryDatabaseTask.MODE_LIST:
                            resultListener.onListResult((List<Message>) task.getResults()[0]);
                            break;
                        case QueryDatabaseTask.MODE_UNIQUE:
                            resultListener.onUniqueResult((Message) task.getResults()[0]);
                            break;
                        case QueryDatabaseTask.MODE_COUNT:
                            resultListener.onCount((long)task.getResults()[0]);
                            break;
                        default: {
                            LogUtil.w(this.getClass().getName(), LocalizedException.UNDEFINED_ARGUMENT);
                            break;
                        }
                    }
                }
            }
        };

        ConcurrentTask task = new QueryDatabaseTask<>(queryBuilder, mode);

        task.addListener(listener);
        execute(task);
    }
}
