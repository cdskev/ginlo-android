// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.concurrent.manager;

import eu.ginlo_apps.ginlo.concurrent.listener.ConcurrentTaskListener;
import eu.ginlo_apps.ginlo.concurrent.manager.TaskManager;
import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import eu.ginlo_apps.ginlo.concurrent.task.ConvertToChatItemVOTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.message.ChatController;
import eu.ginlo_apps.ginlo.greendao.Message;
import java.util.List;

public class ChatTaskManager
        extends TaskManager {

    public void executeConvertToChatItemVOTask(String chatGuid,
                                               ChatController chatController,
                                               SimsMeApplication application,
                                               List<Message> messages,
                                               ConcurrentTaskListener listener,
                                               boolean onlyShowTimedMessages) {
        ConcurrentTask task = new ConvertToChatItemVOTask(chatGuid, chatController, application, messages, onlyShowTimedMessages);

        task.addListener(listener);
        execute(task);
    }
}
