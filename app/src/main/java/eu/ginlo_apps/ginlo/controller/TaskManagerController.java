// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.controller;

import eu.ginlo_apps.ginlo.concurrent.manager.ContactTaskManager;
import eu.ginlo_apps.ginlo.concurrent.manager.HttpTaskManager;
import eu.ginlo_apps.ginlo.concurrent.manager.KeyTaskManager;

public class TaskManagerController {
    private final HttpTaskManager httpTaskManager;

    private final KeyTaskManager keyTaskManager;

    private final ContactTaskManager contactTaskManager;

    public TaskManagerController() {
        httpTaskManager = new HttpTaskManager();
        keyTaskManager = new KeyTaskManager();
        contactTaskManager = new ContactTaskManager();
    }

    public void cancelAllTasks() {
        httpTaskManager.cancelAllTasks();
        keyTaskManager.cancelAllTasks();
        contactTaskManager.cancelAllTasks();
    }

    public HttpTaskManager getHttpTaskManager() {
        return httpTaskManager;
    }

    public KeyTaskManager getKeyTaskManager() {
        return keyTaskManager;
    }

    public ContactTaskManager getContactTaskManager() {
        return contactTaskManager;
    }
}
