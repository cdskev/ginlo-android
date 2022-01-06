// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.concurrent.manager;

import eu.ginlo_apps.ginlo.concurrent.listener.ConcurrentTaskListener;
import eu.ginlo_apps.ginlo.concurrent.manager.TaskManager;
import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import eu.ginlo_apps.ginlo.concurrent.task.DownloadImageTask;
import eu.ginlo_apps.ginlo.concurrent.task.HttpBaseTask;
import eu.ginlo_apps.ginlo.concurrent.task.HttpSingleTask;
import eu.ginlo_apps.ginlo.model.param.HttpPostParams;
import java.security.KeyStore;

public class HttpTaskManager
        extends TaskManager {
    public void executeHttpPostTask(KeyStore keyStore,
                                    HttpPostParams httpPostParams,
                                    String username,
                                    String password,
                                    String requestGuid,
                                    ConcurrentTaskListener listener,
                                    HttpBaseTask.OnConnectionDataUpdatedListener onConnectionDataUpdatedListener,
                                    int connectionTimeout) {
        HttpSingleTask task = new HttpSingleTask(keyStore, httpPostParams, username, password, requestGuid, onConnectionDataUpdatedListener);

        if (connectionTimeout > -1) {
            task.setConnectionTimeout(connectionTimeout);
        }

        task.addListener(listener);
        execute(task);
    }

    public void executeDownloadImageTask(String urlString,
                                         ConcurrentTaskListener listener) {
        ConcurrentTask task = new DownloadImageTask(urlString);

        task.addListener(listener);
        execute(task);
    }
}
