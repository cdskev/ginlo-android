// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.services

import android.content.Intent
import androidx.core.app.JobIntentService
import eu.ginlo_apps.ginlo.context.SimsMeApplication
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.greendao.MessageDao
import eu.ginlo_apps.ginlo.log.LogUtil
import org.greenrobot.greendao.query.WhereCondition

class LoadPendingTimedMessagesTask : JobIntentService() {
    override fun onHandleWork(intent: Intent) {
        val messageController = SimsMeApplication.getInstance().messageController

        try {
            val timedMessageGuids = messageController.getTimedMessagesGuids()

            if (timedMessageGuids.size <= 0) {
                return
            }

            val queryBuilder = messageController.dao.queryBuilder()

            when {
                timedMessageGuids.size == 1 -> queryBuilder.where(MessageDao.Properties.Guid.eq(timedMessageGuids[0]))
                timedMessageGuids.size == 2 -> queryBuilder.whereOr(
                    MessageDao.Properties.Guid.eq(timedMessageGuids[0]),
                    MessageDao.Properties.Guid.eq(timedMessageGuids[1])
                )
                timedMessageGuids.size > 2 -> {
                    val moreConditions = mutableListOf<WhereCondition>()

                    for (i in 2 until timedMessageGuids.size) {
                        moreConditions.add(MessageDao.Properties.Guid.eq(timedMessageGuids[i]))
                    }

                    queryBuilder.whereOr(
                        MessageDao.Properties.Guid.eq(timedMessageGuids[0]),
                        MessageDao.Properties.Guid.eq(timedMessageGuids[1]),
                        *moreConditions.toTypedArray()
                    )
                }
            }

            val localMessages = queryBuilder.build().forCurrentThread().list()
            val messagesToLoad = mutableListOf<String>()
            for (guid in timedMessageGuids) {
                val localMessage = localMessages.firstOrNull { it.guid == guid }

                if (localMessage?.data == null) {
                    messagesToLoad.add(guid)
                }
            }

            if (messagesToLoad.size > 0) {
                messageController.loadTimedMessages(messagesToLoad)
            }
        } catch (e: LocalizedException) {
            LogUtil.w(
                LoadPendingTimedMessagesTask::class.java.simpleName,
                "Something wrong when executing LoadPendingTimedMessagesTask",
                e
            )
        }
    }

    companion object {
        fun start() {
            val intent = Intent(SimsMeApplication.getInstance(), LoadPendingTimedMessagesTask::class.java)
            enqueueWork(SimsMeApplication.getInstance(), LoadPendingTimedMessagesTask::class.java, 1, intent)
        }
    }
}