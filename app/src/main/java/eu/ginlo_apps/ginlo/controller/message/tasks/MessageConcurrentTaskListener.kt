// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller.message.tasks

import eu.ginlo_apps.ginlo.concurrent.listener.ConcurrentTaskListener
import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask
import eu.ginlo_apps.ginlo.controller.GinloAppLifecycle
import eu.ginlo_apps.ginlo.controller.GinloAppLifecycleImpl
import eu.ginlo_apps.ginlo.controller.LoginController
import eu.ginlo_apps.ginlo.controller.PreferencesController
import eu.ginlo_apps.ginlo.controller.message.MessageController
import eu.ginlo_apps.ginlo.greendao.Message

class MessageConcurrentTaskListener(
    private val messageController: MessageController,
    private val nextListener: ConcurrentTaskListener?,
    private val informOnMessageReceivedListener: Boolean,
    private val ginloAppLifecycle: GinloAppLifecycle,
    private val loginController: LoginController,
    private val preferencesController: PreferencesController
) : ConcurrentTaskListener() {

    override fun onStateChanged(
        task: ConcurrentTask,
        state: Int
    ) {
        val results = task.results

        if (state != ConcurrentTask.STATE_COMPLETE || results == null || results.isEmpty()) {
            nextListener?.onStateChanged(task, state)
            return
        }

        val newMessageFlags = results[0] as Int

        if (newMessageFlags <= -1) {
            nextListener?.onStateChanged(task, state)
            return
        }

        handleMessageFlags(results, newMessageFlags)


        nextListener?.onStateChanged(task, state)
    }

    private fun handleMessageFlags(results: Array<Any>, newMessageFlags: Int) {
        if (ginloAppLifecycle.isAppInForeground && loginController.isLoggedIn) {
            if (informOnMessageReceivedListener && results.size > 1) {
                val messages = results[2] as? List<Message>
                if (messages != null)
                    messageController.notifyMessageReceivedListeners(messages)
            }
            messageController.handleNewMessageFlag(newMessageFlags)
        } else {
            var newFlag = newMessageFlags
            val savedFlag = preferencesController.newMessagesFlags
            if (savedFlag > -1) {
                newFlag = newFlag or savedFlag
            }
            preferencesController.newMessagesFlags = newFlag
        }
    }
}
