// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.controller

import android.content.Context
import android.content.SharedPreferences
import android.os.AsyncTask
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.collection.ArrayMap
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.ginlo_apps.ginlo.BuildConfig
import eu.ginlo_apps.ginlo.billing.GinloPurchaseImpl
import eu.ginlo_apps.ginlo.concurrent.manager.SerialExecutor
import eu.ginlo_apps.ginlo.concurrent.task.AsyncHttpTask
import eu.ginlo_apps.ginlo.context.SimsMeApplication
import eu.ginlo_apps.ginlo.controller.contracts.AppLifecycleCallbacks
import eu.ginlo_apps.ginlo.controller.contracts.CreateRecoveryCodeListener
import eu.ginlo_apps.ginlo.exception.LocalizedException
import eu.ginlo_apps.ginlo.greendao.Account
import eu.ginlo_apps.ginlo.greendao.DaoMaster
import eu.ginlo_apps.ginlo.greendao.GreenDAOSecurityLayer
import eu.ginlo_apps.ginlo.greendao.Preference
import eu.ginlo_apps.ginlo.greendao.PreferenceDao
import eu.ginlo_apps.ginlo.log.LogUtil
import eu.ginlo_apps.ginlo.model.Mandant
import eu.ginlo_apps.ginlo.model.backend.BackendResponse
import eu.ginlo_apps.ginlo.service.BackendService
import eu.ginlo_apps.ginlo.service.IBackendService
import eu.ginlo_apps.ginlo.util.ConfigUtil
import eu.ginlo_apps.ginlo.util.DateUtil
import eu.ginlo_apps.ginlo.util.JsonUtil
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener
import eu.ginlo_apps.ginlo.util.RuntimeConfig
import eu.ginlo_apps.ginlo.util.SecurityUtil
import eu.ginlo_apps.ginlo.view.CameraView
import java.util.ArrayList
import java.util.Date
import java.util.HashMap
import java.util.concurrent.Executor

open class PreferencesController(val mApplication: SimsMeApplication) :
    LoginController.AppLockLifecycleCallbacks, AppLifecycleCallbacks {

    private val TAG = PreferencesController::class.java.simpleName

    companion object {
        private const val TAG = "PreferencesController"
        const val NOTIFICATION_SOUND_NO_SOUND = "noSound"
        const val BACKUP_INTERVAL_OFF = 0
        const val BACKUP_INTERVAL_DAILY = 1
        const val BACKUP_INTERVAL_WEEKLY = 2
        const val BACKUP_INTERVAL_MONTHLY = 3
        const val BACKUP_KEY_ROUNDS_ERROR = -1
        const val THEME_MODE_LIGHT = BuildConfig.DEFAULT_THEME_MODE
        const val THEME_MODE_DARK = "Dark"
        const val THEME_MODE_AUTO = "Auto"
        const val THEME_NAME_DEFAULT = BuildConfig.DEFAULT_THEME
        const val THEME_NAME_SMALL = "GinloDefaultSmall"
        const val THEME_NAME_LARGE = "GinloDefaultLarge"
    }

    private val NOTIFICATION_SOUND_DEFAULT = "default"
    private val SERVER_VERSIONS_TIME_STAMP = "serverVersionsTimeStamp"
    private val BACKUP_INTERVAL_DEFAULT = BACKUP_INTERVAL_WEEKLY
    private val PASSWORD_ENABLED_DEFAULT = true
    private val TYPE_PASSWORD_DEFAULT = Preference.TYPE_PASSWORD_COMPLEX
    private val APP_SETTINGS = "PreferencesController.appSettings"
    private val SENT_MESSAGE_COUNT_SETTING = "PreferencesController.sentMessageCount"
    private val SOUND_SINGLE_CHAT_ENABLED_SETTING = "PreferencesController.soundForSingleChatSetting"
    private val SOUND_SERVICE_CHAT_ENABLED_SETTING = "PreferencesController.soundForServiceChatSetting"
    private val SOUND_CHANNEL_CHAT_ENABLED_SETTING = "PreferencesController.soundForChannelChatSetting"
    private val SOUND_GROUP_CHAT_ENABLED_SETTING = "PreferencesController.soundForGroupChatSetting"
    private val NEW_MESSAGES_FLAGS = "PreferencesController.newMessageFlags"
    private val HAS_SHOW_OPEN_FILE_WARNING = "PreferencesController.hasShowOpenFileWarning"
    private val LATEST_BACKUP_FILE_SIZE = "PreferencesController.latestBackupFileSize"
    private val LATEST_BACKUP_DATE = "PreferencesController.latestBackupDate"
    private val SHOW_MSG_AFTER_BACKUP = "PreferencesController.showMsgAfterBackup"
    private val LATEST_BACKUP_FILE_PATH = "PreferencesController.latestBackupPath"
    private val LATEST_BACKUP_IS_IN_CLOUD = "PreferencesController.latestBackupIsInCloud"
    private val FIRST_BACKUP_AFTER_CREATE_ACCOUNT =
        "PreferencesController.fistBackupAfterCreateAccount"
    private val USE_INTERNAL_PDF_VIEWER = "PreferencesController.useInternalPdfViewer"
    private val ANIMATE_RICH_CONTENT = "PreferencesController.animateRichContent"
    private val ALWAYS_DOWNLOAD_RICH_CONTENT = "PreferencesController.alwaysDownloadRichContent"
    private val PLAY_MESSAGE_RECEIVED_SOUND = "PreferencesController.playMessageReceivedSound"
    private val PLAY_MESSAGE_SEND_SOUND = "PreferencesController.playMessageSendSound"
    private val PLAY_MESSAGE_SD_SOUND = "PreferencesController.playMessageSdSound"
    private val RECOVERY_TOKEN_PHONE = "PreferencesController.RecoverTokenPhone"
    private val RECOVERY_TOKEN_EMAIL = "PreferencesController.RecoverTokenEmail"
    private val NOTIFICATION_PREVIEW_ENABLED = "PreferencesController.NotificationPreviewEnabled"
    private val BIOMETRIC_AUTH_ENABLED = "PreferencesController.BiometricAuthEnabled"
    private val LAST_USED_GUIDS_COMPANY = "PreferencesController.LastUsedGuidsCompany"
    private val LAST_USED_GUIDS_DOMAIN = "PreferencesController.LastUsedGuidsDomain"
    private val LAST_USED_GUIDS_MAX_COUNT = 100
    private val NUMBER_OF_STARTED_CHATS_DEFAULT = 0
    private val CHECK_PASSWORD_AFTER_MINUTES_DEFAULT = 0
    private val DELETE_DATA_AFTER_TRIES_DEFAULT = false
    private val NUMBER_PASSWORD_TRIES_DEFAULT = 10
    private val SAVE_MEDIA_TO_GALLERY_DEFAULT = false
    private val NEW_MESSAGES_FLAGS_DEFAULT = -1
    private val NOTIFICATION_SINGLE_CHAT_ENABLED_DEFAULT = true
    private val NOTIFICATION_GROUP_CHAT_ENABLED_DEFAULT = true
    private val NOTIFICATION_SERVICE_CHAT_ENABLED_DEFAULT = true
    private val NOTIFICATION_CHANNEL_CHAT_ENABLED_DEFAULT = true
    private val SOUND_SERVICE_CHAT_ENABLED_DEFAULT = true
    private val SOUND_CHANNEL_CHAT_ENABLED_DEFAULT = true
    private val SOUND_SINGLE_CHAT_ENABLED_DEFAULT = true
    private val SOUND_GROUP_CHAT_ENABLED_DEFAULT = true
    private val LIST_REFRESH_RATE_DEFAULT = 30
    private val STREAM_REFRESH_RATE_DEFAULT = 10
    private val MAXIMUM_ROOM_MEMBERS_DEFAULT = 30
    private val SENT_MESSAGE_COUNT_DEFAULT: Long = 0
    private val SEND_PROFILE_NAME_DEFAULT = true
    private val SHOW_IN_APP_NOTIFICATIONS_DEFAULT = true
    private val IS_SEND_PROFILE_NAME_SET_DEFAULT = false
    private val LAZY_MSG_SERVICE_TIMEOUT_DEFAULT = 300
    private val USE_LAZY_MSG_SERVICE_DEFAULT = 1
    private val PUBLIC_ONLINE_STATE_DEFAULT = true
    private val SINGLE_CHATS_ENABLED = true
    private val GROUP_CHATS_ENABLED = true
    private val CHANNELS_ENABLED = true
    private val BACKUP_KEY_DEFAULT: String? = null
    private val IMAGE_QUALITY = 1  //medium
    private val GINLO_THEME_NAME = "PreferencesController.AppThemeName"
    private val GINLO_THEME_MODE = "PreferencesController.AppThemeMode"
    private val SCREENSHOTS_ALLOWED = "PreferencesController.screenshotsAllowed"
    private val USE_OSM = "PreferencesController.UseOsm"
    private val POLL_MESSAGES = "PreferencesController.pollMessages"
    private val USE_PLAY_SERVICES = "PreferencesController.usePlayServices"
    private val GINLO_ONGOING_SERVICE = "PreferencesController.ginloOngoingService"
    private val GINLO_ONGOING_SERVICE_NOTIFICATION = "PreferencesController.ginloOngoingServiceNotification"
    private val VIBRATION_FOR_SINGLECHATS = "PreferencesController.vibrationForSingle"
    private val VIBRATION_FOR_GROUPS = "PreferencesController.vibrationForGroups"
    private val VIBRATION_FOR_SERVICES = "PreferencesController.vibrationForServices"
    private val VIBRATION_FOR_CHANNELS = "PreferencesController.vibrationForChannels"
    private val VIDEO_QUALITY = CameraView.MODE_MEDIUM_QUALITY
    private val FETCH_IN_BACKGROUND = "PreferencesController.fetchInBackground"
    private val FETCH_IN_BACKGROUND_TOKEN = "PreferencesController.fetchInBackgroundToken"
    private val CONTACT_SYNC_DATE = "PreferencesController.contactSyncDate"
    private val NEXT_PURCHASE_CHECK_DATE = "PreferencesController.purchaseCheckDate"
    private val BACKGROUND_ACCESS_TOKEN_DATE = "PreferencesController.backgroundAccessTokenDate"
    private val SERVER_VERSIONS_SERIAL_EXECUTOR = SerialExecutor()
    private val CONFIRM_READ = "confirmRead"
    private val RECOVERY_CODE_ENABLED_DEFAULT = true
    private val ENABLE_RECOVERY_CODE = "enableRecoveryCode"
    private val SIMSE_ID_SHOWN_AT_REG = "PreferencesController.simseidshownatreg"
    private val HAS_OLD_CONTACTS_MERGED_DEFAULT = false
    private val HAS_OLD_CONTACTS_MERGED = "hasOldContactsMerged"
    private val LAST_PRIVATE_INDEX_SYNC_TIME_STAMP = "lastPrivateIndexTimeStamp"
    private val LAST_PRIVATE_COMPANY_SYNC_TIME_STAMP = "lastCompanyIndexTimeStamp"
    private val LAST_PRIVATE_COMPANY_FULL_CHECK_SYNC_TIME_STAMP =
        "lastCompanyIndexFullCheckTimeStamp"
    private val PRIVATE_INDEX_GUIDS_TO_LOAD = "privateIndexGuidsToLoad"
    private val HAS_SET_OWN_DEVICE_NAME = "hasSetOwnDeviceName"
    private val HAS_SET_OWN_DEVICE_NAME_DEFAULT = false
    private val MIGRATION_VERSION = "migrationVersion"

    private var preferenceDao: PreferenceDao
    private var sharedPreferences: SharedPreferences
    private var mNewServerVersion: MutableMap<String, String>?
    private var mServerChangedListeners: MutableMap<String, OnServerVersionChangedListener>
    private var mServerConfigTask: AsyncHttpTask<Map<String, String>>? = null
    private var mGenerateRecoveryCodeTask: GenerateRecoveryCodeTask? = null
    private var mStartGenerateRecoveryCodeTaskAgain: Boolean = false
    private var tenantCache: List<Mandant>? = null //Used to be called MANDANT_CACHE
    private var themeHasChanged: Boolean = false
    private var themeLocked: Boolean = false

    init {
        val db = mApplication.dataBase
        val daoMaster = DaoMaster(db)
        val daoSession = daoMaster.newSession()

        mServerChangedListeners = mutableMapOf<String, OnServerVersionChangedListener>()
        mNewServerVersion = mutableMapOf<String, String>()

        preferenceDao = daoSession.preferenceDao

        sharedPreferences = mApplication.getSharedPreferences(APP_SETTINGS, Context.MODE_PRIVATE)
        mApplication.loginController.registerAppLockLifecycleCallbacks(this)
        mApplication.appLifecycleController.registerAppLifecycleCallbacks(this)
    }

    private fun checkVersions(
        serverVersionKey: String,
        localVersions: JsonObject?,
        serverVersions: JsonObject?,
        newVersions: MutableMap<String, String>
    ) {
        if (serverVersions != null && JsonUtil.hasKey(serverVersionKey, serverVersions)) {
            var newVersion: String? = null
            val server = serverVersions.get(serverVersionKey).asString

            if (localVersions != null && JsonUtil.hasKey(serverVersionKey, localVersions)) {
                val local = localVersions.get(serverVersionKey).asString

                if (local != server) {
                    newVersion = server
                }
            } else {
                newVersion = server
            }

            if (newVersion != null) {
                newVersions[serverVersionKey] = newVersion
            }
        }
    }

    private fun getTenantListFromJsonArray(jsonArray: JsonArray): List<Mandant> =
        jsonArray.mapIndexedNotNull { idx, json ->
            val mandant = json.asJsonObject.get("Mandant") ?: return@mapIndexedNotNull null
            val tenantName = mandant.asJsonObject.get("ident")?.asString
            Mandant(
                tenantName,
                mandant.asJsonObject.get("label")?.asString,
                mandant.asJsonObject.get("salt")?.asString,
                if (RuntimeConfig.getMandant() == tenantName) 0 else idx + 1
            )
        }

    val preferences: Preference by lazy {
        var preference: Preference?

        synchronized(preferenceDao) {
            val queryBuilder = preferenceDao.queryBuilder()

            preference = queryBuilder.where(PreferenceDao.Properties.Id.eq(1)).unique()
        }

        if (preference == null && GreenDAOSecurityLayer.isInitiated()) {
            preference = getDefaultPreferences()
        }

        if (preference == null) {
            throw LocalizedException(LocalizedException.PREFERENCE_IS_NULL)
        }

        preference as Preference
    }

    private fun getDefaultPreferences(): Preference {
        try {
            LogUtil.i(TAG, "initPrefs")

            val preference = Preference()

            preference.setDefaultValues()

            synchronized(preferenceDao) {
                preferenceDao.insert(preference)
            }
            return preference
        } catch (e: LocalizedException) {
            throw RuntimeException("Init Prefs failed. ", e)
        }
    }

    private fun Preference.setDefaultValues() {
        clearEncryptedData()
        checkPasswordAfterMinutes = CHECK_PASSWORD_AFTER_MINUTES_DEFAULT
        passwordEnabled = PASSWORD_ENABLED_DEFAULT
        deleteDataAfterTries = DELETE_DATA_AFTER_TRIES_DEFAULT
        listRefreshRate = LIST_REFRESH_RATE_DEFAULT
        maximumRoomMembers = MAXIMUM_ROOM_MEMBERS_DEFAULT
        notificationForGroupChatEnabled = NOTIFICATION_GROUP_CHAT_ENABLED_DEFAULT
        notificationForSingleChatEnabled = NOTIFICATION_SINGLE_CHAT_ENABLED_DEFAULT
        numberOfPasswordTries = NUMBER_PASSWORD_TRIES_DEFAULT
        passwordType = TYPE_PASSWORD_DEFAULT
        saveMediaToGallery = SAVE_MEDIA_TO_GALLERY_DEFAULT
        setStreamRefreshRate(STREAM_REFRESH_RATE_DEFAULT)
        lazyMsgServiceTimeout = LAZY_MSG_SERVICE_TIMEOUT_DEFAULT
        useLazyMsgService = USE_LAZY_MSG_SERVICE_DEFAULT
        singleChatsEnabled = SINGLE_CHATS_ENABLED
        groupChatsEnabled = GROUP_CHATS_ENABLED
        channelsEnabled = CHANNELS_ENABLED
    }

    fun clearAll() {
        synchronized(preferenceDao) {
            preferenceDao.deleteAll()
        }

        try {
            preferences.setDefaultValues()
        } catch (e: LocalizedException) {
            LogUtil.i(TAG, "Failed to set default values for preferences after clearing it from database.")
        }
    }

    fun deleteSharedPreferences() {
        sharedPreferences.edit()?.clear()?.apply()
    }

    /**
     * Registriert den Listener für den angegebenen Key.
     * Wenn sich schon ein Listener für den Key registriert hat wird dieser durch den neuen ersetzt.
     *
     * @param serverVersionKey z.B. [eu.ginlo_apps.ginlo.util.ConfigUtil.SERVER_VERSION_GET_CHANNELS]
     * @param listener         zu informierender Listener
     */
    fun registerServerChangedListener(
        serverVersionKey: String,
        listener: OnServerVersionChangedListener
    ) {
        mServerChangedListeners[serverVersionKey] = listener
    }

    /**
     * serverVersionIsUpToDate
     *
     * @param serverVersionKey z.B. [eu.ginlo_apps.ginlo.util.ConfigUtil.SERVER_VERSION_GET_SERVICES]
     * @param serverVersion    neue Serverversion
     */
    @Synchronized
    fun serverVersionIsUpToDate(serverVersionKey: String, serverVersion: String) {
        try {
            val localVersionsString = preferences.serverVersions
            val localVersions: JsonObject
            localVersions = if (localVersionsString.isNullOrEmpty()) {
                JsonObject()
            } else {
                val parser = JsonParser()
                parser.parse(localVersionsString).asJsonObject
            }

            localVersions.addProperty(serverVersionKey, serverVersion)

            preferences.serverVersions = localVersions.toString()

            synchronized(preferenceDao) {
                preferenceDao.update(preferences)
            }

            if (SERVER_VERSIONS_SERIAL_EXECUTOR.size() < 1) {
                // We have to iterate over a COPY of the mNewServerVersion to avoid
                // ConcurrentModificationException. We should probably not store
                // newServerVersion as a member variable. But hey, it is what it is now
                // ¯\_(ツ)_/¯
                mNewServerVersion?.toMap()?.also { serverVersions ->
                    var allVersionsAreEqual = true

                    if (serverVersions.any()) {
                        for (key in serverVersions.keys) {
                            val localServerVersionJE = localVersions.get(key)

                            if (localServerVersionJE == null) {
                                allVersionsAreEqual = false
                                break
                            }

                            val localServerVersion = localServerVersionJE.asString

                            if (localServerVersion != serverVersions[key]) {
                                allVersionsAreEqual = false
                                break
                            }
                        }
                    }

                    if (allVersionsAreEqual) {
                        sharedPreferences.edit().putString(
                            SERVER_VERSIONS_TIME_STAMP,
                            DateUtil.getDateStringFromLocale()
                        ).apply()
                        mNewServerVersion = null
                    }
                }
            }
        } catch (e: LocalizedException) {
            LogUtil.w(TAG, e.message, e)
        }
    }

    fun loadServerConfigVersions(
        force: Boolean,
        genericActionListener: GenericActionListener<Void>?
    ) {
        try {
            if (mServerConfigTask != null) {
                return
            }

            val serverVersionTimeStamp = sharedPreferences.getString(SERVER_VERSIONS_TIME_STAMP, "")

            if (!force && !serverVersionTimeStamp.isNullOrEmpty() && !BuildConfig.DEBUG) {
                if (DateUtil.isSameDay(serverVersionTimeStamp)) {
                    return
                }
            }

            val callback = object : AsyncHttpTask.AsyncHttpCallback<Map<String, String>> {
                override fun asyncLoaderServerRequest(listener: IBackendService.OnBackendResponseListener) {
                    BackendService.withSyncConnection(mApplication)
                        .getConfigVersions(listener)
                }

                @Throws(LocalizedException::class)
                override fun asyncLoaderServerResponse(response: BackendResponse): Map<String, String>? {
                    val result = response.jsonObject ?: return null

                    val serverVersionKeys = ConfigUtil.getServerVersionKeys()

                    val newVersions = HashMap<String, String>(serverVersionKeys.size)

                    val localServerVersionsString = preferences.serverVersions
                    var localServerVersions: JsonObject? = null
                    if (!localServerVersionsString.isNullOrEmpty()) {
                        val parser = JsonParser()
                        localServerVersions = parser.parse(localServerVersionsString).asJsonObject
                    }

                    for (serverVersionKey in serverVersionKeys) {
                        checkVersions(serverVersionKey, localServerVersions, result, newVersions)
                    }

                    return newVersions
                }

                override fun asyncLoaderFinished(result: Map<String, String>?) {
                    if (result != null && result.isNotEmpty()) {

                        // local copy of the version while iterator so the carpet is not pulled out from underneath
                        // e.g. set to null.
                        val tempNewServerVersions = mNewServerVersion?.toMutableMap()
                            ?: HashMap(result.size)

                        //erst befuellen
                        for (serverVersionKey in result.keys) {
                            val newServerVersion = result[serverVersionKey] ?: continue
                            tempNewServerVersions[serverVersionKey] = newServerVersion
                        }

                        mNewServerVersion = tempNewServerVersions.toMutableMap()

                        //dann listener aufrufen.
                        //Grund: wenn auf gleichen Thread serverVersionIsUpToDate() aufgerufen wird, stimmt die Ueberpruefung in der Methode eventuell nicht
                        for ((key, versionValue) in tempNewServerVersions.entries) {
                            var listener: OnServerVersionChangedListener? = null

                            if (mServerChangedListeners.containsKey(key)) {
                                listener = mServerChangedListeners[key]
                            }

                            if (listener != null) {
                                listener.onServerVersionChanged(
                                    key,
                                    versionValue,
                                    SERVER_VERSIONS_SERIAL_EXECUTOR
                                )
                            } else if (key == ConfigUtil.SERVER_VERSION_GET_CONFIGURATION) {
                                onServerConfigurationChanged(versionValue)
                            } else if (key == ConfigUtil.SERVER_VERSION_GET_MANADANTEN) {
                                loadMandantenList(object : OnLoadTenantListener {
                                    override fun onLoadTenantFinished() {
                                        serverVersionIsUpToDate(
                                            ConfigUtil.SERVER_VERSION_GET_MANADANTEN,
                                            versionValue
                                        )
                                    }

                                    override fun onLoadTenantFailed() {
                                        LogUtil.e(TAG, "loadServerConfigVersions: Failed to load tenant")
                                    }
                                })
                            } else {
                                // THIS SHOULD NOT RUN!!! UNLESS someone added a new server version key.
                                LogUtil.w(TAG, "No listener for server version key: $key")
                                mNewServerVersion?.remove(key)
                            }
                        }
                    } else {
                        sharedPreferences.edit().putString(
                            SERVER_VERSIONS_TIME_STAMP,
                            DateUtil.getDateStringFromLocale()
                        ).apply()
                    }

                    mServerConfigTask = null
                    genericActionListener?.onSuccess(null)
                }

                override fun asyncLoaderFailed(errorMessage: String) {
                    mServerConfigTask = null
                    genericActionListener?.onFail(errorMessage, "")
                }
            }
            mServerConfigTask = AsyncHttpTask(callback)

            mServerConfigTask?.executeOnExecutor(SERVER_VERSIONS_SERIAL_EXECUTOR)
        } catch (e: LocalizedException) {
            LogUtil.e(TAG, e.message, e)
        }
    }

    private fun onServerConfigurationChanged(newServerVersion: String?) {
        try {
            val callback = object : AsyncHttpTask.AsyncHttpCallback<JsonObject> {
                override fun asyncLoaderServerRequest(listener: IBackendService.OnBackendResponseListener) {
                    BackendService.withSyncConnection(mApplication)
                        .getConfiguration(listener)
                }

                override fun asyncLoaderServerResponse(response: BackendResponse): JsonObject {
                    return response.jsonObject
                }

                override fun asyncLoaderFinished(result: JsonObject?) {
                    try {
                        if (result == null) {
                            return
                        }
                        if (result.has(Preference.MAXIMUM_ROOM_MEMBERS)) {
                            preferences.maximumRoomMembers =
                                result.get(Preference.MAXIMUM_ROOM_MEMBERS).asInt
                        }

                        if (result.has(Preference.LIST_REFRESH_RATE)) {
                            preferences.listRefreshRate =
                                result.get(Preference.LIST_REFRESH_RATE).asInt
                        }
                        if (result.has(Preference.STREAM_REFRESH_RATE)) {
                            preferences.setStreamRefreshRate(result.get(Preference.STREAM_REFRESH_RATE).asInt)
                        }
                        if (result.has(Preference.LAZY_MSG_SERVICE_TIMEOUT)) {
                            preferences.lazyMsgServiceTimeout =
                                result.get(Preference.LAZY_MSG_SERVICE_TIMEOUT).asInt
                        }

                        if (result.has(Preference.USE_LAZY_MSG_SERVICE)) {
                            preferences.useLazyMsgService =
                                result.get(Preference.USE_LAZY_MSG_SERVICE).asInt
                        }

                        if (result.has(Preference.PERSIST_MESSAGE_DAYS)) {
                            preferences.persistMessageDays =
                                result.get(Preference.PERSIST_MESSAGE_DAYS).asInt
                        }
                        if (result.has(Preference.DEVICE_MAX_CLIENTS)) {
                            preferences.deviceMaxClients =
                                result.get(Preference.DEVICE_MAX_CLIENTS).asInt
                        }
                        if (result.has(Preference.DEVICE_MAX_TEMP_CLIENTS)) {
                            preferences.setDeviceMaxTempClients(result.get(Preference.DEVICE_MAX_TEMP_CLIENTS).asInt)
                        }

                        synchronized(preferenceDao) {
                            preferenceDao.update(preferences)
                        }
                        if (newServerVersion is String) {
                            serverVersionIsUpToDate(
                                ConfigUtil.SERVER_VERSION_GET_CONFIGURATION,
                                newServerVersion
                            )
                        }
                    } catch (e: LocalizedException) {
                        LogUtil.e(TAG, e.message, e)
                    }
                }

                override fun asyncLoaderFailed(errorMessage: String) {
                    // We can schedule a new task on unlock
                }
            }
            AsyncHttpTask(callback).executeOnExecutor(SERVER_VERSIONS_SERIAL_EXECUTOR)
        } catch (e: LocalizedException) {
            LogUtil.e(TAG, e.message, e)
        }
    }

    @Throws(LocalizedException::class)
    fun incNumberOfStartedChats() {
        val oldValue = if (preferences.numberOfStartedChats != null)
            preferences.numberOfStartedChats
        else
            0
        preferences.numberOfStartedChats = oldValue + 1
        synchronized(preferenceDao) {
            preferenceDao.update(preferences)
        }
    }

    @Throws(LocalizedException::class)
    fun setMandantenJson(mandantenJson: String) {
        preferences.mandantenJson = mandantenJson
        synchronized(preferenceDao) {
            preferenceDao.update(preferences)
        }
    }

    fun getNumberOfStartedChats(): Int {
        return try {
            if (preferences.numberOfStartedChats == null) {
                NUMBER_OF_STARTED_CHATS_DEFAULT
            } else preferences.numberOfStartedChats
        } catch (e: LocalizedException) {
            LogUtil.e(TAG, e.message, e)
            NUMBER_OF_STARTED_CHATS_DEFAULT
        }
    }

    val checkPasswordAfterMinutes: Int
        get() {
            try {
                if (isLockApplicationDelayManaged()) {
                    return getLockApplicationDelayManagedValue()
                }
                return if (preferences.checkPasswordAfterMinutes == null) {
                    CHECK_PASSWORD_AFTER_MINUTES_DEFAULT
                } else preferences.checkPasswordAfterMinutes
            } catch (e: LocalizedException) {
                LogUtil.e(TAG, e.message, e)
                return CHECK_PASSWORD_AFTER_MINUTES_DEFAULT
            }
        }

    @Throws(LocalizedException::class)
    fun setCheckPasswordAfterMinutes(value: Int) {
        preferences.checkPasswordAfterMinutes = value
        synchronized(preferenceDao) {
            preferenceDao.update(preferences)
        }
    }

    fun getNumberOfPasswordTries(): Int {
        if (isDeleteDataAfterTriesManaged()) {
            return getDeleteDataAfterTriesManagedValue()
        }
        //val preferences: Preference
        try {
            if (preferences.numberOfPasswordTries == null) {
                return NUMBER_PASSWORD_TRIES_DEFAULT
            }
        } catch (e: LocalizedException) {
            LogUtil.e(TAG, e.message, e)
            return NUMBER_PASSWORD_TRIES_DEFAULT
        }

        return preferences.numberOfPasswordTries
    }

    @Throws(LocalizedException::class)
    fun setNumberOfPasswordTries(value: Int) {
        preferences.numberOfPasswordTries = value
        synchronized(preferenceDao) {
            preferenceDao.update(preferences)
        }
    }

    fun getSaveMediaToGallery(): Boolean {
        try {
            if (isSaveMediaToGalleryManaged()) {
                return isSaveMediaToGalleryManagedValue()
            }
            return if (preferences.saveMediaToGallery == null) {
                SAVE_MEDIA_TO_GALLERY_DEFAULT
            } else preferences.saveMediaToGallery
        } catch (e: LocalizedException) {
            LogUtil.e(TAG, e.message, e)
            return SAVE_MEDIA_TO_GALLERY_DEFAULT
        }
    }

    @Throws(LocalizedException::class)
    fun setSaveMediaToGallery(value: Boolean) {
        preferences.saveMediaToGallery = value
        synchronized(preferenceDao) {
            preferenceDao.update(preferences)
        }
    }

    @Throws(LocalizedException::class)
    fun getVideoQuality(): Int {
        return if (preferences.videoQuality == null) {
            VIDEO_QUALITY
        } else {
            preferences.videoQuality
        }
    }

    @Throws(LocalizedException::class)
    fun setVideoQuality(quality: Int) {
        preferences.videoQuality = quality
        synchronized(preferenceDao) {
            preferenceDao.update(preferences)
        }
    }

    var sentMessageCount: Long
        get() {
            return sharedPreferences.getLong(SENT_MESSAGE_COUNT_SETTING, SENT_MESSAGE_COUNT_DEFAULT)
        }
        set(value) {
            sharedPreferences.edit().putLong(SENT_MESSAGE_COUNT_SETTING, value).apply()
        }


    fun getHasShowOpenFileWarning(): Boolean {
        return sharedPreferences.getBoolean(HAS_SHOW_OPEN_FILE_WARNING, false)
    }

    fun setHasShowOpenFileWarning(value: Boolean) {
        sharedPreferences.edit().putBoolean(HAS_SHOW_OPEN_FILE_WARNING, value).apply()
    }

    fun getNotificationForSingleChatEnabled(): Boolean {
        return try {
            if (preferences.notificationForSingleChatEnabled == null) {
                NOTIFICATION_SINGLE_CHAT_ENABLED_DEFAULT
            } else preferences.notificationForSingleChatEnabled
        } catch (e: LocalizedException) {
            LogUtil.e(TAG, e.message, e)
            NOTIFICATION_SINGLE_CHAT_ENABLED_DEFAULT
        }
    }

    @Throws(LocalizedException::class)
    fun setNotificationForSingleChatEnabled(
        value: Boolean,
        onPreferenceChangedListener: OnPreferenceChangedListener?
    ) {
        val callback = object : AsyncHttpTask.AsyncHttpCallback<String> {
            override fun asyncLoaderServerRequest(listener: IBackendService.OnBackendResponseListener) {
                BackendService.withSyncConnection(mApplication)
                    .setNotification(value, listener)
            }

            override fun asyncLoaderServerResponse(response: BackendResponse): String {
                return ""
            }

            override fun asyncLoaderFinished(result: String) {
                try {
                    preferences.notificationForSingleChatEnabled = value
                    synchronized(preferenceDao) {
                        preferenceDao.update(preferences)
                    }
                    onPreferenceChangedListener?.onPreferenceChangedSuccess()
                } catch (e: LocalizedException) {
                    LogUtil.e(TAG, e.message, e)
                    onPreferenceChangedListener?.onPreferenceChangedFail()
                }
            }

            override fun asyncLoaderFailed(errorMessage: String) {
                onPreferenceChangedListener?.onPreferenceChangedFail()
            }
        }
        AsyncHttpTask(callback).executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR)
    }

    fun getNotificationForGroupChatEnabled(): Boolean {
        return try {
            if (preferences.notificationForGroupChatEnabled == null) {
                NOTIFICATION_GROUP_CHAT_ENABLED_DEFAULT
            } else preferences.notificationForGroupChatEnabled
        } catch (e: LocalizedException) {
            LogUtil.e(TAG, e.message, e)
            NOTIFICATION_GROUP_CHAT_ENABLED_DEFAULT
        }
    }

    @Throws(LocalizedException::class)
    fun setNotificationForGroupChatEnabled(
        value: Boolean,
        onPreferenceChangedListener: OnPreferenceChangedListener?
    ) {
        val callback = object : AsyncHttpTask.AsyncHttpCallback<String> {
            override fun asyncLoaderServerRequest(listener: IBackendService.OnBackendResponseListener) {
                BackendService.withSyncConnection(mApplication)
                    .setGroupNotification(value, listener)
            }

            override fun asyncLoaderServerResponse(response: BackendResponse): String {
                return ""
            }

            override fun asyncLoaderFinished(result: String) {
                try {
                    preferences.notificationForGroupChatEnabled = value
                    synchronized(preferenceDao) {
                        preferenceDao.update(preferences)
                    }
                    onPreferenceChangedListener?.onPreferenceChangedSuccess()
                } catch (e: LocalizedException) {
                    LogUtil.e(TAG, e.message, e)
                    onPreferenceChangedListener?.onPreferenceChangedFail()
                }
            }

            override fun asyncLoaderFailed(errorMessage: String) {
                onPreferenceChangedListener?.onPreferenceChangedFail()
            }
        }
        AsyncHttpTask(callback).executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR)
    }

    @Throws(LocalizedException::class)
    fun setNotificationForServiceChatEnabled(
        value: Boolean,
        onPreferenceChangedListener: OnPreferenceChangedListener?
    ) {
        val callback = object : AsyncHttpTask.AsyncHttpCallback<String> {
            override fun asyncLoaderServerRequest(listener: IBackendService.OnBackendResponseListener) {
                BackendService.withSyncConnection(mApplication)
                    .setServiceNotification(value, listener)
            }

            override fun asyncLoaderServerResponse(response: BackendResponse): String {
                return ""
            }

            override fun asyncLoaderFinished(result: String) {
                try {
                    preferences.notificationForServiceChatEnabled = value
                    synchronized(preferenceDao) {
                        preferenceDao.update(preferences)
                    }
                    onPreferenceChangedListener?.onPreferenceChangedSuccess()
                } catch (e: LocalizedException) {
                    LogUtil.e(TAG, e.message, e)
                    onPreferenceChangedListener?.onPreferenceChangedFail()
                }
            }

            override fun asyncLoaderFailed(errorMessage: String) {
                onPreferenceChangedListener?.onPreferenceChangedFail()
            }
        }
        AsyncHttpTask(callback).executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR)
    }

    @Throws(LocalizedException::class)
    fun getDisableConfirmRead() {

        val callback = object : AsyncHttpTask.AsyncHttpCallback<String> {

            override fun asyncLoaderServerRequest(listener: IBackendService.OnBackendResponseListener) {
                BackendService.withSyncConnection(mApplication)
                    .getAutoGeneratedMessages(listener)
            }

            override fun asyncLoaderServerResponse(response: BackendResponse): String? {
                return if (JsonUtil.hasKey(CONFIRM_READ, response.jsonObject)) {
                    response.jsonObject.get(CONFIRM_READ).asString
                } else {
                    null
                }
            }

            override fun asyncLoaderFinished(confirmRead: String?) {
                try {
                    if (confirmRead != null) {
                        preferences.confirmRead = "1" == confirmRead
                    }
                } catch (e: LocalizedException) {
                    LogUtil.e(TAG, e.message, e)
                }
            }

            override fun asyncLoaderFailed(errorMessage: String) {
            }
        }

        AsyncHttpTask(callback).executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR)
    }

    @Throws(LocalizedException::class)
    fun setDisableConfirmRead(
        value: Boolean,
        onPreferenceChangedListener: OnPreferenceChangedListener?
    ) {

        val callback = object : AsyncHttpTask.AsyncHttpCallback<String> {

            override fun asyncLoaderServerRequest(listener: IBackendService.OnBackendResponseListener) {
                val jsonObject = JsonObject()
                jsonObject.addProperty(CONFIRM_READ, if (value) "1" else "0")

                BackendService.withSyncConnection(mApplication)
                    .setAutoGeneratedMessages(jsonObject.toString(), listener)
            }

            override fun asyncLoaderServerResponse(response: BackendResponse): String? {
                return if (JsonUtil.hasKey(CONFIRM_READ, response.jsonObject)) {
                    response.jsonObject.get(CONFIRM_READ).asString
                } else {
                    null
                }
            }

            override fun asyncLoaderFinished(confirmRead: String?) {
                try {
                    if (confirmRead != null) {
                        preferences.confirmRead = "1" == confirmRead
                        onPreferenceChangedListener?.onPreferenceChangedSuccess()
                    } else {
                        onPreferenceChangedListener?.onPreferenceChangedFail()
                    }
                } catch (e: LocalizedException) {
                    LogUtil.e(TAG, e.message, e)
                    onPreferenceChangedListener?.onPreferenceChangedFail()
                }
            }

            override fun asyncLoaderFailed(errorMessage: String) {
                onPreferenceChangedListener?.onPreferenceChangedFail()
            }
        }
        AsyncHttpTask(callback).executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR)
    }

    @Throws(LocalizedException::class)
    fun getConfirmRead(): Boolean {
        return preferences.confirmRead
    }

    @Throws(LocalizedException::class)
    fun setNotificationForChannelChatEnabled(
        value: Boolean,
        onPreferenceChangedListener: OnPreferenceChangedListener?
    ) {
        val callback = object : AsyncHttpTask.AsyncHttpCallback<String> {
            override fun asyncLoaderServerRequest(listener: IBackendService.OnBackendResponseListener) {
                BackendService.withSyncConnection(mApplication)
                    .setChannelNotification(value, listener)
            }

            override fun asyncLoaderServerResponse(response: BackendResponse): String {
                return ""
            }

            override fun asyncLoaderFinished(result: String) {
                try {
                    preferences.notificationForChannelChatEnabled = value
                    synchronized(preferenceDao) {
                        preferenceDao.update(preferences)
                    }
                    onPreferenceChangedListener?.onPreferenceChangedSuccess()
                } catch (e: LocalizedException) {
                    LogUtil.e(TAG, e.message, e)
                    onPreferenceChangedListener?.onPreferenceChangedFail()
                }
            }

            override fun asyncLoaderFailed(errorMessage: String) {
                onPreferenceChangedListener?.onPreferenceChangedFail()
            }
        }
        AsyncHttpTask(callback).executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR)
    }

    var newMessagesFlags: Int
        get() {
            return sharedPreferences.getInt(NEW_MESSAGES_FLAGS, NEW_MESSAGES_FLAGS_DEFAULT)
        }
        set(value) {
            sharedPreferences.edit().putInt(NEW_MESSAGES_FLAGS, value).apply()
        }

    fun getNotificationForServiceChatEnabled(): Boolean {
        return try {
            if (preferences.notificationForServiceChatEnabled == null) {
                NOTIFICATION_SERVICE_CHAT_ENABLED_DEFAULT
            } else preferences.notificationForServiceChatEnabled
        } catch (e: LocalizedException) {
            LogUtil.e(TAG, e.message, e)
            NOTIFICATION_SERVICE_CHAT_ENABLED_DEFAULT
        }
    }

    fun getNotificationForChannelChatEnabled(): Boolean {
        return try {
            if (preferences.notificationForChannelChatEnabled == null) {
                NOTIFICATION_CHANNEL_CHAT_ENABLED_DEFAULT
            } else preferences.notificationForChannelChatEnabled
        } catch (e: LocalizedException) {
            LogUtil.e(TAG, e.message, e)
            NOTIFICATION_CHANNEL_CHAT_ENABLED_DEFAULT
        }
    }

    fun getSoundForServiceChatEnabled(): Boolean {
        return sharedPreferences.getBoolean(
            SOUND_SERVICE_CHAT_ENABLED_SETTING,
            SOUND_SERVICE_CHAT_ENABLED_DEFAULT
        )
    }

    @Throws(LocalizedException::class)
    fun setSoundForServiceChatEnabled(
        checked: Boolean,
        onPreferenceChangedListener: OnPreferenceChangedListener?
    ) {
        sharedPreferences.edit().putBoolean(SOUND_SERVICE_CHAT_ENABLED_SETTING, checked).apply()
        val callback = object : AsyncHttpTask.AsyncHttpCallback<String> {
            override fun asyncLoaderServerRequest(listener: IBackendService.OnBackendResponseListener) {
                BackendService.withSyncConnection(mApplication)
                    .setNotificationSound(getNotificationSoundSettingJsonString(), listener)
            }

            override fun asyncLoaderServerResponse(response: BackendResponse): String {
                return ""
            }

            override fun asyncLoaderFinished(result: String) {
                onPreferenceChangedListener?.onPreferenceChangedSuccess()
            }

            override fun asyncLoaderFailed(errorMessage: String) {
                sharedPreferences.edit().putBoolean(SOUND_SERVICE_CHAT_ENABLED_SETTING, !checked)
                    .apply()
                onPreferenceChangedListener?.onPreferenceChangedFail()
            }
        }
        AsyncHttpTask(callback).executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR)
    }

    @Throws(LocalizedException::class)
    fun setSoundForChannelChatEnabled(
        checked: Boolean,
        onPreferenceChangedListener: OnPreferenceChangedListener?
    ) {
        sharedPreferences.edit().putBoolean(SOUND_CHANNEL_CHAT_ENABLED_SETTING, checked).apply()
        val callback = object : AsyncHttpTask.AsyncHttpCallback<String> {
            override fun asyncLoaderServerRequest(listener: IBackendService.OnBackendResponseListener) {
                BackendService.withSyncConnection(mApplication)
                    .setNotificationSound(getNotificationSoundSettingJsonString(), listener)
            }

            override fun asyncLoaderServerResponse(response: BackendResponse): String {
                return ""
            }

            override fun asyncLoaderFinished(result: String) {
                onPreferenceChangedListener?.onPreferenceChangedSuccess()
            }

            override fun asyncLoaderFailed(errorMessage: String) {
                sharedPreferences.edit().putBoolean(SOUND_CHANNEL_CHAT_ENABLED_SETTING, !checked)
                    .apply()
                onPreferenceChangedListener?.onPreferenceChangedFail()
            }
        }
        AsyncHttpTask(callback).executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR)
    }

    @Throws(LocalizedException::class)
    fun setSoundForSingleChatEnabled(
        checked: Boolean,
        onPreferenceChangedListener: OnPreferenceChangedListener?
    ) {
        sharedPreferences.edit().putBoolean(SOUND_SINGLE_CHAT_ENABLED_SETTING, checked).apply()
        val callback = object : AsyncHttpTask.AsyncHttpCallback<String> {
            override fun asyncLoaderServerRequest(listener: IBackendService.OnBackendResponseListener) {
                BackendService.withSyncConnection(mApplication)
                    .setNotificationSound(getNotificationSoundSettingJsonString(), listener)
            }

            override fun asyncLoaderServerResponse(response: BackendResponse): String {
                return ""
            }

            override fun asyncLoaderFinished(result: String) {
                onPreferenceChangedListener?.onPreferenceChangedSuccess()
            }

            override fun asyncLoaderFailed(errorMessage: String) {
                sharedPreferences.edit().putBoolean(SOUND_SINGLE_CHAT_ENABLED_SETTING, !checked)
                    .apply()
                onPreferenceChangedListener?.onPreferenceChangedFail()
            }
        }
        AsyncHttpTask(callback).executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR)
    }

    @Throws(LocalizedException::class)
    fun setSoundForGroupChatEnabled(
        checked: Boolean,
        onPreferenceChangedListener: OnPreferenceChangedListener?
    ) {
        sharedPreferences.edit().putBoolean(SOUND_GROUP_CHAT_ENABLED_SETTING, checked).apply()
        val callback = object : AsyncHttpTask.AsyncHttpCallback<String> {
            override fun asyncLoaderServerRequest(listener: IBackendService.OnBackendResponseListener) {
                BackendService.withSyncConnection(mApplication)
                    .setNotificationSound(getNotificationSoundSettingJsonString(), listener)
            }

            override fun asyncLoaderServerResponse(response: BackendResponse): String {
                return ""
            }

            override fun asyncLoaderFinished(result: String) {
                onPreferenceChangedListener?.onPreferenceChangedSuccess()
            }

            override fun asyncLoaderFailed(errorMessage: String) {
                sharedPreferences.edit().putBoolean(SOUND_GROUP_CHAT_ENABLED_SETTING, !checked)
                    .apply()
                onPreferenceChangedListener?.onPreferenceChangedFail()
            }
        }
        AsyncHttpTask(callback).executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR)
    }

    private fun getNotificationSoundSettingJsonString(): String {
        val `object` = JsonObject()

        `object`.addProperty(
            "privateMessage",
            if (getSoundForSingleChatEnabled()) NOTIFICATION_SOUND_DEFAULT else NOTIFICATION_SOUND_NO_SOUND
        )
        `object`.addProperty(
            "groupMessage",
            if (getSoundForGroupChatEnabled()) NOTIFICATION_SOUND_DEFAULT else NOTIFICATION_SOUND_NO_SOUND
        )
        `object`.addProperty(
            "serviceMessage",
            if (getSoundForServiceChatEnabled()) NOTIFICATION_SOUND_DEFAULT else NOTIFICATION_SOUND_NO_SOUND
        )
        `object`.addProperty(
            "channelMessage",
            if (getSoundForChannelChatEnabled()) NOTIFICATION_SOUND_DEFAULT else NOTIFICATION_SOUND_NO_SOUND
        )

        return `object`.toString()
    }

    fun getSoundForSingleChatEnabled(): Boolean {
        return sharedPreferences.getBoolean(
            SOUND_SINGLE_CHAT_ENABLED_SETTING,
            SOUND_SINGLE_CHAT_ENABLED_DEFAULT
        )
    }

    fun getSoundForGroupChatEnabled(): Boolean {
        return sharedPreferences.getBoolean(
            SOUND_GROUP_CHAT_ENABLED_SETTING,
            SOUND_GROUP_CHAT_ENABLED_DEFAULT
        )
    }

    fun getSoundForChannelChatEnabled(): Boolean {
        return sharedPreferences.getBoolean(
            SOUND_CHANNEL_CHAT_ENABLED_SETTING,
            SOUND_CHANNEL_CHAT_ENABLED_DEFAULT
        )
    }

    fun getDeleteDataAfterTries(): Boolean {
        if (isDeleteDataAfterTriesManaged()) {
            return true
        }
        try {
            if (preferences.deleteDataAfterTries == null) {
                return DELETE_DATA_AFTER_TRIES_DEFAULT
            }
        } catch (e: LocalizedException) {
            LogUtil.e(TAG, e.message, e)
            return DELETE_DATA_AFTER_TRIES_DEFAULT
        }

        return preferences.deleteDataAfterTries
    }

    @Throws(LocalizedException::class)
    fun setDeleteDataAfterTries(value: Boolean) {
        preferences.deleteDataAfterTries = value
        synchronized(preferenceDao) {
            preferenceDao.update(preferences)
        }
    }

    fun getListRefreshRate(): Int {
        try {
            return if (preferences.listRefreshRate == null) {
                LIST_REFRESH_RATE_DEFAULT
            } else preferences.listRefreshRate
        } catch (e: LocalizedException) {
            LogUtil.e(TAG, e.message, e)
        }

        return LIST_REFRESH_RATE_DEFAULT
    }

    fun getLazyMsgServiceTimeout(): Int {
        try {
            return if (preferences.lazyMsgServiceTimeout == null) {
                LAZY_MSG_SERVICE_TIMEOUT_DEFAULT * 1000
            } else preferences.lazyMsgServiceTimeout
        } catch (le: LocalizedException) {
            LogUtil.e(TAG, le.message, le)
        }

        return LAZY_MSG_SERVICE_TIMEOUT_DEFAULT * 1000
    }

    fun useLazyMsgService(): Boolean {
        try {
            return preferences.useLazyMsgService == null || preferences.useLazyMsgService == USE_LAZY_MSG_SERVICE_DEFAULT
        } catch (le: LocalizedException) {
            LogUtil.e(TAG, le.message, le)
        }

        return true
    }

    @Throws(LocalizedException::class)
    fun getMandantenList(): List<Mandant> {
        if (tenantCache?.isNotEmpty() == true) {
            return ArrayList(tenantCache?: emptyList())
        }

        val mandantenJson = preferences.mandantenJson ?: return emptyList()

        val gson = Gson()
        val jsonArray = gson.fromJson(mandantenJson, JsonArray::class.java)

        if (jsonArray != null) {
            tenantCache = getTenantListFromJsonArray(jsonArray)
        }

        return ArrayList(tenantCache?: emptyList())
    }

    private fun loadMandantsFromBackend(listener: OnLoadTenantListener?) {
        try {
            val asyncTask = AsyncHttpTask(object : AsyncHttpTask.AsyncHttpCallback<List<Mandant>> {
                override fun asyncLoaderServerRequest(listener: IBackendService.OnBackendResponseListener) {
                    BackendService.withSyncConnection(mApplication)
                        .getTenants(listener)
                }

                @Throws(LocalizedException::class)
                override fun asyncLoaderServerResponse(response: BackendResponse): List<Mandant>? {
                    val responseArray = response.jsonArray
                    var result: List<Mandant>? = null
                    if (responseArray != null && responseArray.size() > 0) {
                        result = getTenantListFromJsonArray(responseArray)
                        setMandantenJson(responseArray.toString())
                    }
                    return result
                }

                override fun asyncLoaderFinished(result: List<Mandant>) {
                    listener?.onLoadTenantFinished()

                    tenantCache = ArrayList(result)
                }

                override fun asyncLoaderFailed(errorMessage: String) {
                    listener?.onLoadTenantFailed()
                }
            })
            asyncTask.executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR, null, null, null)
        } catch (e: LocalizedException) {
            LogUtil.e(TAG, e.message, e)
            listener?.onLoadTenantFailed()
        }
    }

    @Throws(LocalizedException::class)
    fun getMandantFromIdent(ident: String?): Mandant? {
        if (ident == null) {
            return null
        }
        val mandantCache = getMandantenList()
        for (mandant in mandantCache) {
            if (ident == mandant.ident) {
                return mandant
            }
        }
        return null
    }

    @Throws(LocalizedException::class)
    fun getMaximumRoomMembers(): Int? {
        return if (preferences.maximumRoomMembers == null) {
            MAXIMUM_ROOM_MEMBERS_DEFAULT
        } else preferences.maximumRoomMembers
    }

    fun getPasswordType(): Int? {
        try {
            if (preferences.passwordType == null) {
                return TYPE_PASSWORD_DEFAULT
            }
        } catch (e: LocalizedException) {
            LogUtil.e(TAG, e.message, e)
            return TYPE_PASSWORD_DEFAULT
        }

        return preferences.passwordType
    }

    @Throws(LocalizedException::class)
    fun setPasswordType(value: Int) {
        preferences.passwordType = value
        synchronized(preferenceDao) {
            preferenceDao.update(preferences)
        }
    }

    val passwordEnabled: Boolean?
        get() {
            try {
                if (isPasswordOnStartRequired()) {
                    if (preferences.passwordEnabled == null) {
                        return true
                    }
                    // MDM Settimg und Lokale einstellungen unterscheiden sich
                    mApplication.keyController.deleteNoPassKeyFile()
                    setPasswordEnabled(true)
                    return true
                }
                if (preferences.passwordEnabled == null) {
                    return PASSWORD_ENABLED_DEFAULT
                }
            } catch (e: LocalizedException) {
                LogUtil.e(TAG, e.message, e)
                return PASSWORD_ENABLED_DEFAULT
            }

            return preferences.passwordEnabled
        }

    /**
     * Speichert die Einstellung. Um die Umstellung des Keystores muss seperat durchgefuehrt werden.
     *
     * @param passwordEnabled value
     */
    @Throws(LocalizedException::class)
    fun setPasswordEnabled(passwordEnabled: Boolean?) {
        preferences.passwordEnabled = passwordEnabled
        synchronized(preferenceDao) {
            preferenceDao.update(preferences)
        }
    }

    /**
     * Speichert die Einstellung und sorgt fuer die Umstellung des Keystores.
     */
    @Throws(LocalizedException::class)
    fun setPasswordEnabled(
        password: String,
        passwordEnabled: Boolean?,
        onPreferenceChangedListener: OnPreferenceChangedListener?
    ) {
        preferences.passwordEnabled = passwordEnabled
        synchronized(preferenceDao) {
            preferenceDao.update(preferences)
        }

        val keysSavedListener = object : KeyController.OnKeysSavedListener {
            override fun onKeysSaveFailed() {
                preferences.passwordEnabled = !preferences.passwordEnabled
                synchronized(preferenceDao) {
                    preferenceDao.update(preferences)
                }
                onPreferenceChangedListener?.onPreferenceChangedFail()
            }

            override fun onKeysSaveComplete() {
                onPreferenceChangedListener?.onPreferenceChangedSuccess()
            }
        }
        if ((passwordEnabled) == false) {
            mApplication.keyController.saveKeys(password, keysSavedListener)
        } else {
            mApplication.keyController.deleteNoPassKeyFile()
            onPreferenceChangedListener?.onPreferenceChangedSuccess()
        }
    }

    fun getSharedPreferences(): SharedPreferences {
        return sharedPreferences
    }

    fun getPlayMessageSdSound(): Boolean {
        return sharedPreferences.getBoolean(PLAY_MESSAGE_SD_SOUND, true)
    }

    fun setPlayMessageSdSound(value: Boolean) {
        sharedPreferences.edit().putBoolean(PLAY_MESSAGE_SD_SOUND, value).apply()
    }

    fun getPlayMessageSendSound(): Boolean {
        return sharedPreferences.getBoolean(PLAY_MESSAGE_SEND_SOUND, true)
    }

    fun setPlayMessageSendSound(value: Boolean) {
        sharedPreferences.edit().putBoolean(PLAY_MESSAGE_SEND_SOUND, value).apply()
    }

    fun getPlayMessageReceivedSound(): Boolean {
        return sharedPreferences.getBoolean(PLAY_MESSAGE_RECEIVED_SOUND, true)
    }

    fun setPlayMessageReceivedSound(value: Boolean) {
        sharedPreferences.edit().putBoolean(PLAY_MESSAGE_RECEIVED_SOUND, value).apply()
    }

    fun getUseInternalPdfViewer(): Boolean {
        return sharedPreferences.getBoolean(USE_INTERNAL_PDF_VIEWER, true)
    }

    fun setUseInternalPdfViewer(value: Boolean) {
        sharedPreferences.edit().putBoolean(USE_INTERNAL_PDF_VIEWER, value).apply()
    }

    fun getAnimateRichContent(): Boolean {
        return sharedPreferences.getBoolean(ANIMATE_RICH_CONTENT, true)
    }

    fun setAnimateRichContent(value: Boolean) {
        sharedPreferences.edit().putBoolean(ANIMATE_RICH_CONTENT, value).apply()
    }

    fun getAlwaysDownloadRichContent(): Boolean {
        return sharedPreferences.getBoolean(ALWAYS_DOWNLOAD_RICH_CONTENT, true)
    }

    fun setAlwaysDownloadRichContent(value: Boolean) {
        sharedPreferences.edit().putBoolean(ALWAYS_DOWNLOAD_RICH_CONTENT, value).apply()
    }

    /**
     * @return aes key bytes as Base64 String OR Null
     */
    fun getBackupKey(): String? {
        return try {
            preferences.backupKey
        } catch (le: LocalizedException) {
            LogUtil.e(TAG, le.identifier, le)
            BACKUP_KEY_DEFAULT
        }
    }

    /**
     * getBackupKeySalt
     *
     * @return salt bytes as Base64 String OR null
     */
    @Throws(LocalizedException::class)
    fun getBackupKeySalt(): String {
        return preferences.backupKeySalt
    }

    /**
     * getBackupKeyRounds
     *
     * @return backup aes key rounds OR -1
     */
    fun getBackupKeyRounds(): Int {
        return try {
            val rounds = preferences.backupKeyRounds
            rounds ?: BACKUP_KEY_ROUNDS_ERROR
        } catch (e: LocalizedException) {
            LogUtil.e(TAG, e.message, e)
            BACKUP_KEY_ROUNDS_ERROR
        }
    }

    @Throws(LocalizedException::class)
    fun saveBackupKeyInfo(base64KeyValue: String?, base64Salt: String?, rounds: Int) {
        preferences.backupKey = base64KeyValue
        preferences.backupKeySalt = base64Salt
        preferences.backupKeyRounds = rounds

        synchronized(preferenceDao) {
            preferenceDao.update(preferences)
        }
    }

    var latestBackupDate: Long
        get() {
            return sharedPreferences.getLong(LATEST_BACKUP_DATE, -1)
        }
        set(value) {
            sharedPreferences.edit().putLong(LATEST_BACKUP_DATE, value).apply()
        }

    var latestBackupFileSize: Long
        get() {
            return sharedPreferences.getLong(LATEST_BACKUP_FILE_SIZE, -1)
        }
        set(value) {
            sharedPreferences.edit().putLong(LATEST_BACKUP_FILE_SIZE, value).apply()
        }


    var latestBackupPath: String?
        get() {
            return sharedPreferences.getString(LATEST_BACKUP_FILE_PATH, null)
        }
        set(value) {
            sharedPreferences.edit().putString(LATEST_BACKUP_FILE_PATH, value).apply()
        }

    fun getFirstBackupAfterCreateAccount(): Boolean {
        return sharedPreferences.getBoolean(FIRST_BACKUP_AFTER_CREATE_ACCOUNT, false)
    }

    fun setFirstBackupAfterCreateAccount() {
        sharedPreferences.edit().putBoolean(FIRST_BACKUP_AFTER_CREATE_ACCOUNT, true).apply()
    }

    fun getShowMessageAfterBackupProcess(): Boolean {
        return sharedPreferences.getBoolean(SHOW_MSG_AFTER_BACKUP, true)
    }

    fun setShowMessageAfterBackupProcess(value: Boolean) {
        sharedPreferences.edit().putBoolean(SHOW_MSG_AFTER_BACKUP, value).apply()
    }

    fun getSaveMediaInBackup(): Boolean {
        return try {
            val value = preferences.saveMediaInBackup
            value ?: false
        } catch (e: LocalizedException) {
            LogUtil.e(TAG, e.message, e)
            true
        }
    }

    @Throws(LocalizedException::class)
    fun setSaveMediaInBackup(value: Boolean) {
        preferences.saveMediaInBackup = value
        synchronized(preferenceDao) {
            preferenceDao.update(preferences)
        }
    }

    /**
     * getBackupInterval
     *
     * @return #BACKUP_INTERVAL_DAILY, #BACKUP_INTERVAL_WEEKLY or #BACKUP_INTERVAL_MONTHLY
     */
    @Throws(LocalizedException::class)
    fun getBackupInterval(): Int {
        val interval = preferences.backupInterval
        return interval ?: BACKUP_INTERVAL_DEFAULT
    }

    /**
     * save the backup interval settings
     *
     * @param interval use #BACKUP_INTERVAL_DAILY, #BACKUP_INTERVAL_WEEKLY or #BACKUP_INTERVAL_MONTHLY
     */
    @Throws(LocalizedException::class)
    fun setBackupInterval(interval: Int) {
        preferences.backupInterval = interval
        synchronized(preferenceDao) {
            preferenceDao.update(preferences)
        }
    }

    @Throws(LocalizedException::class)
    fun getBackupNetworkWifiOnly(): Boolean {
        val value = preferences.backupNetworkWifiOnly
        return value ?: true
    }

    @Throws(LocalizedException::class)
    fun setBackupNetworkWifiOnly(value: Boolean) {
        preferences.backupNetworkWifiOnly = value
        synchronized(preferenceDao) {
            preferenceDao.update(preferences)
        }
    }

    @Throws(LocalizedException::class)
    fun getImageQuality(): Int {
        return if (preferences.imageQuality == null) {
            IMAGE_QUALITY
        } else preferences.imageQuality
    }

    @Throws(LocalizedException::class)
    fun setImageQuality(value: Int) {
        preferences.imageQuality = value
        synchronized(preferenceDao) {
            preferenceDao.update(preferences)
        }
    }

    var automaticDownloadPicture: Int
        get() {
            return preferences.automaticDownloadPicture
        }
        set(value) {
            preferences.automaticDownloadPicture = value
            synchronized(preferenceDao) {
                preferenceDao.update(preferences)
            }
        }

/* @Throws(LocalizedException::class)
 fun setAutomaticDownloadPicture(value: Int) {
     val preferences = getPreferences()
     preferences.automaticDownloadPicture = value
     synchronized(preferenceDao) {
         preferenceDao.update(preferences)
     }
 }*/

    //@Throws(LocalizedException::class)
    var automaticDownloadVoice: Int
        get() {
            return preferences.automaticDownloadVoice
        }
        set(value) {
            preferences.automaticDownloadVoice = value
            synchronized(preferenceDao) {
                preferenceDao.update(preferences)
            }
        }

/*@Throws(LocalizedException::class)
fun setAutomaticDownloadVoice(value: Int) {
    val preferences = getPreferences()
    preferences.automaticDownloadVoice = value
    synchronized(preferenceDao) {
        preferenceDao.update(preferences)
    }
}*/

    //@Throws(LocalizedException::class)
    var automaticDownloadVideo: Int
        get() {
            return preferences.automaticDownloadVideo
        }
        set(value) {
            preferences.automaticDownloadVideo = value
            synchronized(preferenceDao) {
                preferenceDao.update(preferences)
            }
        }

/*@Throws(LocalizedException::class)
fun setAutomaticDownloadVideo(value: Int) {
    val preferences = getPreferences()
    preferences.automaticDownloadVideo = value
    synchronized(preferenceDao) {
        preferenceDao.update(preferences)
    }
}*/

    //@Throws(LocalizedException::class)
    var automaticDownloadFiles: Int
        get() {
            return preferences.automaticDownloadFiles
        }
        set(value) {
            preferences.automaticDownloadFiles = value
            synchronized(preferenceDao) {
                preferenceDao.update(preferences)
            }
        }

/*@Throws(LocalizedException::class)
fun setAutomaticDownloadFiles(value: Int) {
    val preferences = getPreferences()
    preferences.automaticDownloadFiles = value
    synchronized(preferenceDao) {
        preferenceDao.update(preferences)
    }
}*/

    @Throws(LocalizedException::class)
    fun getShowInAppNotifications(): Boolean {
        return if (preferences.showInappNotifications == null) {
            SHOW_IN_APP_NOTIFICATIONS_DEFAULT
        } else preferences.showInappNotifications
    }

    @Throws(LocalizedException::class)
    fun setShowInAppNotifications(value: Boolean) {
        preferences.showInappNotifications = value
        synchronized(preferenceDao) {
            preferenceDao.update(preferences)
        }
    }

    //@Throws(LocalizedException::class)
    var sendProfileName: Boolean
        get() {
            return if (preferences.sendProfileName == null) {
                SEND_PROFILE_NAME_DEFAULT
            } else preferences.sendProfileName
        }
        set(value) {
            preferences.sendProfileName = value
            synchronized(preferenceDao) {
                preferenceDao.update(preferences)
            }
        }

/*@Throws(LocalizedException::class)
fun setSendProfileName(value: Boolean) {
    val preferences = getPreferences()
    preferences.sendProfileName = value
    synchronized(preferenceDao) {
        preferenceDao.update(preferences)
    }
}*/

    fun getScreenshotsEnabled(): Boolean {
        return sharedPreferences.getBoolean(SCREENSHOTS_ALLOWED, BuildConfig.ALWAYS_ALLOW_SCREENSHOTS)
    }

    fun setScreenshotsEnabled(value: Boolean) {
        sharedPreferences.edit().putBoolean(SCREENSHOTS_ALLOWED, value).apply()
    }

    fun getOsmEnabled(): Boolean {
        return sharedPreferences.getBoolean(USE_OSM, BuildConfig.OSM_ENABLED_BY_DEFAULT)
    }

    fun setOsmEnabled(value: Boolean) {
        sharedPreferences.edit().putBoolean(USE_OSM, value).apply()
    }

    fun getPollingEnabled(): Boolean {
        return sharedPreferences.getBoolean(POLL_MESSAGES, BuildConfig.POLLING_ENABLED_BY_DEFAULT)
    }

    fun setPollingEnabled(value: Boolean) {
        sharedPreferences.edit().putBoolean(POLL_MESSAGES, value).apply()
    }

    fun getPlayServicesEnabled(): Boolean {
        return sharedPreferences.getBoolean(USE_PLAY_SERVICES, BuildConfig.USE_PLAY_SERVICES)
    }

    fun setPlayServicesEnabled(value: Boolean) {
        sharedPreferences.edit().putBoolean(USE_PLAY_SERVICES, value).apply()
    }

    /**
     * Contains the second part of a theme's name. The full style name must
     * be constructed with GINLO_THEME_NAME as received by getThemeName() prepended.
     * Example: GinloDefaultTheme + Light = GinloDefaultThemeLight (the full theme name)
     */
    fun getThemeMode(): String? {
        return sharedPreferences.getString(GINLO_THEME_MODE, THEME_MODE_LIGHT)
    }

    fun setThemeMode(value: String?) {
        if (!value.isNullOrEmpty()) {
            if(value != getThemeName()) {
                themeHasChanged = true
            }
            sharedPreferences.edit().putString(GINLO_THEME_MODE, value).apply()
        }
    }

    /**
     * Contains only the first part of a theme's name. The full style name must
     * be constructed with GINLO_THEME_MODE as received by getThemeMode() appended.
     * Example: GinloDefaultTheme + Light = GinloDefaultThemeLight (the full theme name)
     */
    fun getThemeName(): String? {
        return sharedPreferences.getString(GINLO_THEME_NAME, BuildConfig.DEFAULT_THEME)
    }

    fun setThemeName(value: String?) {
        if (!value.isNullOrEmpty()) {
            if(value != getThemeName()) {
                themeHasChanged = true
            }
            sharedPreferences.edit().putString(GINLO_THEME_NAME, value).apply()
        }
    }

    fun hasThemeChanged(): Boolean {
        return themeHasChanged
    }

    fun setThemeChanged(value: Boolean) {
        themeHasChanged = value
    }

    fun isThemeColorSettingLocked(): Boolean {
        return themeLocked
    }

    fun setThemeColorSettingLocked(value: Boolean) {
        themeLocked = value
    }

    fun getGinloOngoingServiceEnabled(): Boolean {
        return sharedPreferences.getBoolean(GINLO_ONGOING_SERVICE, false)
    }

    fun setGinloOngoingServiceEnabled(value: Boolean) {
        sharedPreferences.edit().putBoolean(GINLO_ONGOING_SERVICE, value).apply()
    }

    fun getVibrationForSingleChatsEnabled(): Boolean {
        return sharedPreferences.getBoolean(VIBRATION_FOR_SINGLECHATS, true)
    }

    fun setVibrationForSingleChatsEnabled(value: Boolean) {
        sharedPreferences.edit().putBoolean(VIBRATION_FOR_SINGLECHATS, value).apply()
    }

    fun getVibrationForGroupsEnabled(): Boolean {
        return sharedPreferences.getBoolean(VIBRATION_FOR_GROUPS, true)
    }

    fun setVibrationForGroupsEnabled(value: Boolean) {
        sharedPreferences.edit().putBoolean(VIBRATION_FOR_GROUPS, value).apply()
    }

    fun getVibrationForServicesEnabled(): Boolean {
        return sharedPreferences.getBoolean(VIBRATION_FOR_SERVICES, true)
    }

    fun setVibrationForServicesEnabled(value: Boolean) {
        sharedPreferences.edit().putBoolean(VIBRATION_FOR_SERVICES, value).apply()
    }

    fun getVibrationForChannelsEnabled(): Boolean {
        return sharedPreferences.getBoolean(VIBRATION_FOR_CHANNELS, true)
    }

    fun setVibrationForChannelsEnabled(value: Boolean) {
        sharedPreferences.edit().putBoolean(VIBRATION_FOR_CHANNELS, value).apply()
    }

    fun getRecoveryTokenPhone(): String? {
        return sharedPreferences.getString(RECOVERY_TOKEN_PHONE, null)
    }

    fun setRecoveryTokenPhone(value: String?) {
        if (!value.isNullOrEmpty()) {
            sharedPreferences.edit().putString(RECOVERY_TOKEN_PHONE, value).apply()
        } else {
            sharedPreferences.edit().remove(RECOVERY_TOKEN_PHONE).apply()
        }
    }

    fun getRecoveryTokenEmail(): String? {
        return sharedPreferences.getString(RECOVERY_TOKEN_EMAIL, null)
    }

    fun setRecoveryTokenEmail(value: String?) {
        if (!value.isNullOrEmpty()) {
            sharedPreferences.edit().putString(RECOVERY_TOKEN_EMAIL, value).apply()
        } else {
            sharedPreferences.edit().remove(RECOVERY_TOKEN_EMAIL).apply()
        }
    }

    fun getIsSendProfileNameSet(): Boolean {
        return try {
            if (preferences.isSendProfileNameSet == null) {
                IS_SEND_PROFILE_NAME_SET_DEFAULT
            } else preferences.isSendProfileNameSet
        } catch (e: LocalizedException) {
            LogUtil.e(TAG, e.message, e)
            IS_SEND_PROFILE_NAME_SET_DEFAULT
        }
    }

    @Throws(LocalizedException::class)
    fun setSendProfileNameSet() {
        preferences.setSendProfileNameSet()
        synchronized(preferenceDao) {
            preferenceDao.update(preferences)
        }
    }

    @Throws(LocalizedException::class)
    fun getRegConfirmCode(): String {
        return preferences.regConfirmCode
    }

    @Throws(LocalizedException::class)
    fun setRegConfirmCode(confirmCode: String) {
        preferences.regConfirmCode = confirmCode
        synchronized(preferenceDao) {
            preferenceDao.update(preferences)
        }
    }

    // MDM Einstellungen
    fun isExportEnabled(): Boolean {
        return if(RuntimeConfig.isB2c())
            true
        else
            RuntimeConfig.getClassUtil().getManagedConfigUtil(mApplication)?.canExportChat()
                    ?: false
    }

    fun isOpenInAllowed(): Boolean {
        return RuntimeConfig.getClassUtil().getManagedConfigUtil(mApplication)?.isOpenInAllowed
            ?: true
    }

    fun canSendMedia(): Boolean {
        return RuntimeConfig.getClassUtil().getManagedConfigUtil(mApplication)?.canSendMedia()
            ?: true
    }

    fun isPasswordOnStartRequired(): Boolean {
        return RuntimeConfig.getClassUtil().getManagedConfigUtil(mApplication)?.isPasswordOnStartRequired
            ?: false
    }

    private fun isSaveMediaToGalleryManaged(): Boolean {
        return RuntimeConfig.getClassUtil().getManagedConfigUtil(mApplication)?.isSaveMediaToGalleryManaged
            ?: SAVE_MEDIA_TO_GALLERY_DEFAULT
    }

    private fun isSaveMediaToGalleryManagedValue(): Boolean {
        return RuntimeConfig.getClassUtil().getManagedConfigUtil(mApplication)?.isSaveMediaToGalleryManagedValue
            ?: false
    }

    fun isDeleteDataAfterTriesManaged(): Boolean {
        return RuntimeConfig.getClassUtil().getManagedConfigUtil(mApplication)?.isDeleteDataAfterTriesManaged
            ?: false
    }

    private fun getDeleteDataAfterTriesManagedValue(): Int {
        return RuntimeConfig.getClassUtil().getManagedConfigUtil(mApplication)?.deleteDataAfterTriesManagedValue
            ?: 0
    }

    private fun isLockApplicationDelayManaged(): Boolean {
        return RuntimeConfig.getClassUtil().getManagedConfigUtil(mApplication)?.isLockApplicationDelayManaged
            ?: false
    }

    private fun getLockApplicationDelayManagedValue(): Int {
        return RuntimeConfig.getClassUtil().getManagedConfigUtil(mApplication)?.lockApplicationDelayManagedValue
            ?: 0
    }

    fun checkPassword(password: String): String? {
        return RuntimeConfig.getClassUtil().getManagedConfigUtil(mApplication)
            ?.checkPassword(password, true)
    }

    @Throws(LocalizedException::class)
    fun onPasswordChanged(password: String) {
        RuntimeConfig.getClassUtil().getManagedConfigUtil(mApplication)?.onPasswordChanged(password)
        setForceNeedToChangePassword(false)
        setHasSystemGeneratedPasword(false)

        val accountController = mApplication.accountController
        val account = accountController.account

        // recovery code erstellen, sofern nicht im cockpit deaktiviert
        if (!isRecoveryDisabled() && account != null) {
            // alte recovery tokens zuruecksetzen
            setRecoveryTokenEmail(null)
            setRecoveryTokenPhone(null)
            getSharedPreferences().edit()
                .putBoolean(AccountControllerBase.MC_RECOVERY_CODE_REQUESTED, false).apply()

            if (account.state >= Account.ACCOUNT_STATE_CONFIRMED) {
                account.setCustomBooleanAttribute(AccountController.RECOVERY_CODE_SET, false)
                accountController.updateAccoutDao()
                startGenerateRecoveryCodeTask()
            }
        }
    }

    private fun startGenerateRecoveryCodeTask() {
        if (mGenerateRecoveryCodeTask != null) {
            mStartGenerateRecoveryCodeTaskAgain = true
            return
        }
        mGenerateRecoveryCodeTask =
            GenerateRecoveryCodeTask(mApplication, object : GenericActionListener<Void> {
                override fun onSuccess(`object`: Void?) {
                    mGenerateRecoveryCodeTask = null
                    if (mStartGenerateRecoveryCodeTaskAgain) {
                        mStartGenerateRecoveryCodeTaskAgain = false
                        startGenerateRecoveryCodeTask()
                    }
                }

                override fun onFail(message: String, errorIdent: String) {
                    //wird nicht aufgerufen
                }
            })
        mGenerateRecoveryCodeTask?.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
    }

    fun checkRecoveryCodeToBeSet(force: Boolean) {
        try {
            if (isRecoveryDisabled() || !getRecoveryCodeEnabled()) {
                return
            }

            val accountController = mApplication.accountController
            val account = accountController.account
            val recoveryCodeSet =
                account.getCustomBooleanAttribute(AccountController.RECOVERY_CODE_SET)

            if (!recoveryCodeSet || force) {
                startGenerateRecoveryCodeTask()
            }
        } catch (e: LocalizedException) {
            LogUtil.e(TAG, e.message, e)
        }
    }

    @Throws(LocalizedException::class)
    fun needToChangePassword(password: String?, isPwChange: Boolean): Boolean {
        return if (getForceNeedToChangePassword()) {
            true
        } else RuntimeConfig.getClassUtil().getManagedConfigUtil(mApplication)?.needToChangePassword(
            password,
            isPwChange
        )
            ?: false
    }

    fun getForceNeedToChangePassword(): Boolean {
        //val preferences: Preference
        return try {
            preferences.forceNeedToChangePassword != null && preferences.forceNeedToChangePassword
        } catch (e: LocalizedException) {
            LogUtil.e(TAG, e.message, e)
            false
        }
    }

    @Throws(LocalizedException::class)
    fun setForceNeedToChangePassword(bForce: Boolean) {
        preferences.forceNeedToChangePassword = bForce
        synchronized(preferenceDao) {
            preferenceDao.update(preferences)
        }
    }

    fun getHasSystemGeneratedPasword(): Boolean {
        //  val preferences: Preference
        return try {
            preferences.hasSystemGeneratedPasword != null && preferences.hasSystemGeneratedPasword
        } catch (e: Exception) {
            LogUtil.e(TAG, "getHasSystemGeneratedPasword: " + e.message)
            false
        }
    }

    @Throws(LocalizedException::class)
    fun setHasSystemGeneratedPasword(bForce: Boolean) {
        preferences.hasSystemGeneratedPasword = bForce
        synchronized(preferenceDao) {
            preferenceDao.update(preferences)
        }
    }

    @Throws(LocalizedException::class)
    fun rememberPasswordDate() {
        preferences.passwordChangeDate = Date()
        synchronized(preferenceDao) {
            preferenceDao.update(preferences)
        }
    }

    fun getPasswordChangeDate(): Date? {
        //  val preferences: Preference
        return try {
            preferences.passwordChangeDate
        } catch (e: LocalizedException) {
            LogUtil.e(TAG, e.message, e)
            null
        }
    }

    fun getHashedPasswords(): String? = try {
        preferences.hashedPasswords
    } catch (e: LocalizedException) {
        LogUtil.e(TAG, e.message, e)
        null
    }

    fun setHashedPasswords(hashes: String) {
        try {
            preferences.hashedPasswords = hashes
        } catch (le: LocalizedException) {
            LogUtil.e(TAG, le.message, le)
        }
    }

    fun canUseSimplePassword(): Boolean {
        return RuntimeConfig.getClassUtil().getManagedConfigUtil(mApplication)?.canUseSimplePassword()
            ?: true
    }

    fun getFetchInBackground(): Boolean {
        // Gespeichert in den SharedPreferences, um auch beim Start darauf zugreifen zu können
        return sharedPreferences.getBoolean(FETCH_IN_BACKGROUND, true)
    }

    fun setFetchInBackground(bEnabled: Boolean) {
        val editor = sharedPreferences.edit()
        editor.putBoolean(FETCH_IN_BACKGROUND, bEnabled)
        editor.apply()
    }

    fun getFetchInBackgroundAccessToken(): String? {
        return sharedPreferences.getString(FETCH_IN_BACKGROUND_TOKEN, null)
    }

    fun setFetchInBackgroundAccessToken(token: String?) {
        sharedPreferences.edit().putString(FETCH_IN_BACKGROUND_TOKEN, token).apply()
    }

    fun isPurchaseSaved(ginloPurchase: GinloPurchaseImpl?): Boolean {
        try {
            ginloPurchase?.also {
                val token = it.purchaseToken
                val savedPurchases = preferences.savedPurchases ?: return false
                return savedPurchases.contains(token)
            }
        } catch (e: LocalizedException) {
            LogUtil.e(TAG, e.message, e)
            return false
        }
        return false
    }

    fun getSavedPurchases(): String? {
        return try {
            preferences.savedPurchases
        } catch (e: LocalizedException) {
            LogUtil.e(TAG, e.message, e)
            null
        }
    }

    fun resetSavedPurchases() {
        try {
            preferences.savedPurchases = null
        } catch (le: LocalizedException) {
            LogUtil.e(TAG, le.message, le)
        }
    }

    fun markPurchaseSaved(ginloPurchase: GinloPurchaseImpl?) {
        try {
            ginloPurchase?.also {
                var token = it.purchaseToken
                if (!it.orderId.isNullOrEmpty()) {
                    token = it.orderId
                }
                var savedPurchases: String? = preferences.savedPurchases
                savedPurchases = if (savedPurchases == null) {
                    token
                } else {
                    "$savedPurchases,$token"
                }
                preferences.savedPurchases = savedPurchases
            }
        } catch (e: LocalizedException) {
            LogUtil.e(TAG, e.message, e)
        }
    }

    /**
     * @return date as Format "dd.MM.yy" or ""
     */
    fun getContactSyncDate(): String? {
        return sharedPreferences.getString(CONTACT_SYNC_DATE, "")
    }

    /**
     * @param dateAsString date as Format "dd.MM.yy"
     */
    fun setContactSyncDate(dateAsString: String) {
        sharedPreferences.edit().putString(CONTACT_SYNC_DATE, dateAsString).apply()
    }

    /**
     * @return date as Format "dd.MM.yy" or ""
     */
    fun getBackgroundAccessTokenDate(): String? {
        return sharedPreferences.getString(BACKGROUND_ACCESS_TOKEN_DATE, "")
    }

    /**
     * @param dateAsString date as Format "dd.MM.yy"
     */
    fun setBackgroundAccessTokenDate(dateAsString: String) {
        sharedPreferences.edit().putString(BACKGROUND_ACCESS_TOKEN_DATE, dateAsString).apply()
    }

    /**
     * @param dateAsString date as Format "dd.MM.yy"
     */
    fun setPurchaseCheckDate(dateAsString: String) {
        sharedPreferences.edit().putString(NEXT_PURCHASE_CHECK_DATE, dateAsString).apply()
    }

    fun isCameraDisabled(): Boolean {
        return RuntimeConfig.getClassUtil().getManagedConfigUtil(mApplication)?.isCameraDisabled
            ?: false
    }

    fun isMicrophoneDisabled(): Boolean {
        return RuntimeConfig.getClassUtil().getManagedConfigUtil(mApplication)?.isMicrophoneDisabled
            ?: false
    }

    fun isSaveMediaToCameraRollDisabled(): Boolean {
        return RuntimeConfig.getClassUtil().getManagedConfigUtil(mApplication)?.isSaveMediaToCameraRollDisabled
            ?: false
    }

    fun isSendContactsDisabled(): Boolean {
        return RuntimeConfig.getClassUtil().getManagedConfigUtil(mApplication)?.isSendContactsDisabled
            ?: false
    }

    fun isLocationDisabled(): Boolean {
        return RuntimeConfig.getClassUtil().getManagedConfigUtil(mApplication)?.isLocationDisabled
            ?: false
    }

    fun isCopyPasteDisabled(): Boolean {
        return RuntimeConfig.getClassUtil().getManagedConfigUtil(mApplication)?.isCopyPasteDisabled
            ?: false
    }

    fun getRecoveryCodeEnabled(): Boolean {
        return if (!RuntimeConfig.isBAMandant()) {
            false
        } else sharedPreferences.getBoolean(ENABLE_RECOVERY_CODE, RECOVERY_CODE_ENABLED_DEFAULT)
    }

    /**
     * Gibt an, ob das Recovery vom Admin bestimmt wird, der Nutzer kann nicht waehlen
     * wenn ja, wird auch der Switch nicht angezeigt
     *
     * @return true = Company-Recovery, false = user-recovery (Mail, oder SMS)
     */
    fun getRecoveryByAdmin(): Boolean {
        return RuntimeConfig.getClassUtil().getManagedConfigUtil(mApplication)?.recoveryByAdmin
            ?: false
    }

    fun isRecoveryDisabled(): Boolean {
        return RuntimeConfig.getClassUtil().getManagedConfigUtil(mApplication)?.isRecoveryDisabled
            ?: false
    }

    /**
     * setzt die Nutzereinstellung, nur PK
     */
    fun setRecoveryCodeEnabled(value: Boolean, sendToServer: Boolean) {
        if (value) {
            if (sendToServer) {
                // neuen Code generieren
                startGenerateRecoveryCodeTask()
            }
        } else {
            setRecoveryTokenPhone(null)
            setRecoveryTokenEmail(null)
            mApplication.accountController.unsetRecoveryCode()
        }
        sharedPreferences.edit().putBoolean(ENABLE_RECOVERY_CODE, value).apply()
    }

    fun getSimsmeIdShownAtReg(): Boolean {
        return sharedPreferences.getBoolean(SIMSE_ID_SHOWN_AT_REG, false)
    }

    fun setSimsmeIdShownAtReg() {
        sharedPreferences.edit().putBoolean(SIMSE_ID_SHOWN_AT_REG, true).apply()
    }

    fun hasOldContactsMerged(): Boolean {
        return sharedPreferences.getBoolean(
            HAS_OLD_CONTACTS_MERGED,
            HAS_OLD_CONTACTS_MERGED_DEFAULT
        )
    }

    fun setHasOldContactsMerged() {
        sharedPreferences.edit().putBoolean(HAS_OLD_CONTACTS_MERGED, true).apply()
    }

    fun getMigrationVersion(): Int {
        return sharedPreferences.getInt(MIGRATION_VERSION, 0)
    }

    fun setMigrationVersion(migrationVersion: Int) {
        val oldVersion = sharedPreferences.getInt(MIGRATION_VERSION, 0)
        if (oldVersion < migrationVersion) {
            sharedPreferences.edit().putInt(MIGRATION_VERSION, migrationVersion).apply()
        }
    }

    fun hasSetOwnDeviceName(): Boolean {
        return sharedPreferences.getBoolean(
            HAS_SET_OWN_DEVICE_NAME,
            HAS_SET_OWN_DEVICE_NAME_DEFAULT
        )
    }

    fun setHasSetOwnDeviceName() {
        sharedPreferences.edit().putBoolean(HAS_SET_OWN_DEVICE_NAME, true).apply()
    }

    @Throws(LocalizedException::class)
    fun getDeviceMaxClients(): Int {
        return preferences.deviceMaxClients
    }

    @Throws(LocalizedException::class)
    fun getPersistMessageDays(): Int {
        return preferences.persistMessageDays
    }

    fun loadMandantenList(listener: OnLoadTenantListener) {
        loadMandantsFromBackend(listener)
    }

    override fun appIsUnlock() {
        if (mApplication.accountController.hasAccountFullState()) {
            loadServerConfigVersions(false, null)
        }
    }

    fun getLastPrivateIndexSyncTimeStamp(): String? {
        return sharedPreferences.getString(LAST_PRIVATE_INDEX_SYNC_TIME_STAMP, null)
    }

    fun setLastPrivateIndexSyncTimeStamp(timeStamp: String) {
        sharedPreferences.edit().putString(LAST_PRIVATE_INDEX_SYNC_TIME_STAMP, timeStamp).apply()
    }

    fun getLastCompanyIndexSyncTimeStamp(): String? {
        return sharedPreferences.getString(LAST_PRIVATE_COMPANY_SYNC_TIME_STAMP, null)
    }

    fun setLastCompanyIndexSyncTimeStamp(timeStamp: String) {
        sharedPreferences.edit().putString(LAST_PRIVATE_COMPANY_SYNC_TIME_STAMP, timeStamp).apply()
    }

    open fun onDeleteAllCompanyContacts() {
        sharedPreferences.edit().remove(LAST_PRIVATE_COMPANY_SYNC_TIME_STAMP).apply()
    }

    fun getLastCompanyIndexFullCheckSyncTimeStamp(): String? {
        return sharedPreferences.getString(LAST_PRIVATE_COMPANY_FULL_CHECK_SYNC_TIME_STAMP, null)
    }

    fun setLastCompanyIndexFullCheckSyncTimeStamp(timeStamp: String) {
        sharedPreferences.edit()
            .putString(LAST_PRIVATE_COMPANY_FULL_CHECK_SYNC_TIME_STAMP, timeStamp).apply()
    }

    fun addPrivateIndexGuidsToLoad(map: ArrayMap<String, String>) {
        synchronized(this) {
            val savedGuidsMap = loadPrivateIndexGuidsToLoad()

            for (i in 0 until map.size) {
                savedGuidsMap[map.keyAt(i)] = map.valueAt(i)
            }

            savePrivateIndexGuidsToLoad(savedGuidsMap)
        }
    }

    fun removePrivateIndexGuidsToLoad(map: ArrayMap<String, String>): ArrayMap<String, String> {
        synchronized(this) {
            val savedGuidsMap = loadPrivateIndexGuidsToLoad()

            for (i in 0 until map.size) {
                val cs = savedGuidsMap[map.keyAt(i)]
                if (cs.equals(map.valueAt(i))) {
                    savedGuidsMap.remove(map.keyAt(i))
                }
            }

            savePrivateIndexGuidsToLoad(savedGuidsMap)

            return savedGuidsMap
        }
    }

    private fun savePrivateIndexGuidsToLoad(map: ArrayMap<String, String>) {
        if (map.isEmpty) {
            sharedPreferences.edit().putString(PRIVATE_INDEX_GUIDS_TO_LOAD, "").apply()
            return
        }
        val sb = StringBuilder()
        sb.append(map.keyAt(0)).append("#").append(map.valueAt(0))
        for (i in 1 until map.size) {
            sb.append(",")
            sb.append(map.keyAt(i)).append("#").append(map.valueAt(i))
        }

        sharedPreferences.edit().putString(PRIVATE_INDEX_GUIDS_TO_LOAD, sb.toString()).apply()
    }

    private fun loadPrivateIndexGuidsToLoad(): ArrayMap<String, String> {
        val map = ArrayMap<String, String>()
        val mapString = sharedPreferences.getString(PRIVATE_INDEX_GUIDS_TO_LOAD, null)

        if (mapString.isNullOrEmpty()) {
            return map
        }

        val array = mapString.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        for (entry in array) {
            val values = entry.split("#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (values.size == 2) {
                map[values[0]] = values[1]
            }
        }

        return map
    }

    fun getPrivateIndexGuidsToLoad(): ArrayMap<String, String> {
        synchronized(this) {
            return loadPrivateIndexGuidsToLoad()
        }
    }

    fun isNotificationPreviewDisabledByAdmin(): Boolean {
        val mangedConfigUtil = RuntimeConfig.getClassUtil().getManagedConfigUtil(mApplication)
        return mangedConfigUtil?.isNotificationPreviewDisabled ?: false
    }

    fun isBiometricLoginDisabledByAdmin(): Boolean {
        val mangedConfigUtil = RuntimeConfig.getClassUtil().getManagedConfigUtil(mApplication)
        return mangedConfigUtil?.disableBiometricLogin ?: false
    }

    fun getNotificationPreviewEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            sharedPreferences.getBoolean(NOTIFICATION_PREVIEW_ENABLED, true)
        } else false
    }

    fun hasNotificationPreviewSetting(): Boolean {
        return try {
            val allPrefs = sharedPreferences.all

            allPrefs != null && allPrefs.containsKey(NOTIFICATION_PREVIEW_ENABLED)
        } catch (e: NullPointerException) {
            false
        }
    }

    @Throws(LocalizedException::class)
    fun setNotificationPreviewEnabled(enabled: Boolean, forceEnable: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val oldValue = sharedPreferences.getBoolean(NOTIFICATION_PREVIEW_ENABLED, true)

            if (enabled != oldValue || forceEnable) {
                if (enabled || forceEnable) {
                    val keyController = mApplication.keyController
                    SecurityUtil.writeNotificationKeyToDisc(
                        keyController.internalEncryptionKey,
                        mApplication
                    )
                    sharedPreferences.edit().putBoolean(NOTIFICATION_PREVIEW_ENABLED, true).apply()
                } else {
                    SecurityUtil.deleteNotificationKeyFromDisc(mApplication)
                    sharedPreferences.edit().putBoolean(NOTIFICATION_PREVIEW_ENABLED, false).apply()
                }
            }
        }
    }

    /**
     * prueft, ob der Client am Server den Wert fuer den Online-State gesetzt hat
     */
    fun checkPublicOnlineStateSet() {
        try {
            if (preferences.publicOnlineState == null) {
                setPublicOnlineState(true, null)
            }
        } catch (le: LocalizedException) {
            LogUtil.w(this@PreferencesController.javaClass.simpleName, le.message, le)
        }
    }

    /**
     * startet polling fuer den Online-State des Gegenueber
     */
    fun getPublicOnlineState(): Boolean {
        return try {
            val publicOnlineState = preferences.publicOnlineState
            publicOnlineState ?: PUBLIC_ONLINE_STATE_DEFAULT
        } catch (e: LocalizedException) {
            Log.w(this@PreferencesController.javaClass.simpleName, e.message, e)
            PUBLIC_ONLINE_STATE_DEFAULT
        }
    }

    /**
     * setzt dne online-status ind er db, ohne serveranfrage
     */
    @Throws(LocalizedException::class)
    fun setPublicOnlineStateInternally(visible: Boolean) {
        preferences.publicOnlineState = visible
        synchronized(preferenceDao) {
            preferenceDao.update(preferences)
        }
    }

    fun setPublicOnlineState(
        visible: Boolean,
        genericActionListener: GenericActionListener<String>?
    ) {

        try {
            val callback = object : AsyncHttpTask.AsyncHttpCallback<String> {
                override fun asyncLoaderServerRequest(listener: IBackendService.OnBackendResponseListener) {
                    BackendService.withSyncConnection(mApplication)
                        .setPublicOnlineState(visible, listener)
                }

                override fun asyncLoaderServerResponse(response: BackendResponse): String {
                    if (response.isError
                        || response.jsonArray == null
                        || response.jsonArray.size() == 0
                    ) {
                        return "false"
                    } else {
                        val accountGuid = mApplication.accountController.account.accountGuid
                        for (jsonElement in response.jsonArray) {
                            if (jsonElement != null) {
                                if (accountGuid == jsonElement.asString) {
                                    return "true"
                                }
                            }
                        }
                    }
                    return "false"
                }

                override fun asyncLoaderFinished(result: String) {
                    if ("true" == result) {
                        try {
                            setPublicOnlineStateInternally(visible)
                            genericActionListener?.onSuccess(null)
                        } catch (e: LocalizedException) {
                            LogUtil.w(TAG, e.message, e)
                        }
                    } else {
                        genericActionListener?.onFail(null, null)
                    }
                }

                override fun asyncLoaderFailed(errorMessage: String) {
                    genericActionListener?.onFail(errorMessage, null)
                }
            }
            AsyncHttpTask(callback).executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR)
        } catch (le: LocalizedException) {
            genericActionListener?.onFail(le.message, null)
        }
    }

    fun getDisableBackup(): Boolean {
        return try {
            preferences.disableBackup
        } catch (e: LocalizedException) {
            LogUtil.e(TAG, e.message, e)
            true
        }
    }

    fun setDisableBackup(disableBackup: Boolean) {
        try {
            preferences.disableBackup = disableBackup
        } catch (le: LocalizedException) {
            LogUtil.e(TAG, le.message, le)
        }
    }

    fun getBiometricAuthEnabled(): Boolean {
        return sharedPreferences.getBoolean(BIOMETRIC_AUTH_ENABLED, false)
    }

    fun setBiometricAuthEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(BIOMETRIC_AUTH_ENABLED, enabled).apply()
    }

    @Throws(LocalizedException::class)
    fun getEncryptedFtsDatabasePassword(): String? {
        return preferences.encryptedFtsDatabasePassword
    }

    @Throws(LocalizedException::class)
    fun setEncryptedFtsDatabasePassword(encryptedPassword: String?) {
        preferences.encryptedFtsDatabasePassword = encryptedPassword

        synchronized(preferenceDao) {
            preferenceDao.update(preferences)
        }
    }

    fun getLastUsedContactGuids(indexType: ContactController.IndexType?): Array<String>? {
        val key =
            if (indexType == ContactController.IndexType.INDEX_TYPE_COMPANY) LAST_USED_GUIDS_COMPANY else LAST_USED_GUIDS_DOMAIN
        val lastUsedGuids = sharedPreferences.getString(key, null) ?: return null

        return lastUsedGuids.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    }

    fun addLastUsedContactGuid(guid: String?, indexType: ContactController.IndexType?) {
        /*if (guid.isNullOrEmpty()) {
            return
        }*/

        val key =
            if (indexType == ContactController.IndexType.INDEX_TYPE_COMPANY) LAST_USED_GUIDS_COMPANY else LAST_USED_GUIDS_DOMAIN
        var lastUsedGuids = sharedPreferences.getString(key, null)

        if (lastUsedGuids == null) {
            lastUsedGuids = ""
        }

        val lastUsedGuidArray =
            lastUsedGuids.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var isGuidInLastUsedArray = false
        if (lastUsedGuidArray.isNotEmpty()) {
            for (lastUsedGuid in lastUsedGuidArray) {
                if (guid == lastUsedGuid) {
                    isGuidInLastUsedArray = true
                    break
                }
            }
        }

        val newLength =
            if (isGuidInLastUsedArray) lastUsedGuidArray.size else if (lastUsedGuidArray.size > LAST_USED_GUIDS_MAX_COUNT) LAST_USED_GUIDS_MAX_COUNT else lastUsedGuidArray.size + 1
        val newLastUsedArray = arrayOfNulls<String>(newLength)
        newLastUsedArray[0] = guid
        if (isGuidInLastUsedArray) {
            var i = 1
            for (lastUsedGuid in lastUsedGuidArray) {
                if (guid != lastUsedGuid) {
                    newLastUsedArray[i] = lastUsedGuid
                    i++
                }
            }
        } else {
            if (lastUsedGuidArray.isNotEmpty()) {
                System.arraycopy(
                    lastUsedGuidArray,
                    if (lastUsedGuidArray.size > LAST_USED_GUIDS_MAX_COUNT) 1 else 0,
                    newLastUsedArray,
                    1,
                    if (lastUsedGuidArray.size > LAST_USED_GUIDS_MAX_COUNT) LAST_USED_GUIDS_MAX_COUNT - 1 else lastUsedGuidArray.size
                )
            }
        }

        val newLastUsedGuids = newLastUsedArray.joinToString(",")
        //val newLastUsedGuids = StringUtil.getStringFromArray(",", newLastUsedArray)

        sharedPreferences.edit().putString(key, newLastUsedGuids).apply()
    }

    @Throws(LocalizedException::class)
    fun getSendCrashLogSetting(): Boolean {
        return preferences.sendCrashLog
    }

    @Throws(LocalizedException::class)
    fun setSendCrashLogSetting(value: Boolean) {
        preferences.sendCrashLog = value
        synchronized(preferenceDao) {
            preferenceDao.update(preferences)
        }
    }

    override fun appWillBeLocked() {
    }

    override fun appDidEnterForeground() {
        val accountController = mApplication.accountController
        if (accountController.hasAccountFullState()) {
            if (mApplication.loginController.isLoggedIn) {
                loadServerConfigVersions(false, null)
                // recoverypasswort setzen
            }
        }
    }

    override fun appGoesToBackGround() {
    }

    private class GenerateRecoveryCodeTask internal constructor(
        private val mApplication: SimsMeApplication,
        private val mListener: GenericActionListener<Void>
    ) : AsyncTask<Void, Void, Void>() {
        private val mAccountController = mApplication.accountController

        override fun doInBackground(vararg params: Void): Void? {
            if (mAccountController != null) {
                val createRecoveryCodeListener = object : CreateRecoveryCodeListener {
                    override fun onCreateSuccess(message: String?) {
                        if (!message.isNullOrEmpty()) {
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    mApplication,
                                    message,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }

                    override fun onCreateFailed(message: String?) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                mApplication,
                                message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                mAccountController.createRecoveryPassword(createRecoveryCodeListener)
            }
            return null
        }

        override fun onPostExecute(aVoid: Void?) {
            mListener.onSuccess(null)
        }
    }

    interface OnPreferenceChangedListener {
        fun onPreferenceChangedSuccess()

        fun onPreferenceChangedFail()
    }

    interface OnLoadTenantListener {
        fun onLoadTenantFinished()

        fun onLoadTenantFailed()
    }

    interface OnServerVersionChangedListener {
        /**
         * Info das die Serverversion, für die der Listener sich registriert hat, sich geaendert hat.
         * Nachdem der Listener seine Daten mit dem Server abgeglichen hat, muss er die Methode [.serverVersionIsUpToDate]
         * aufrufen, damit die Versionabgespeichert wird.
         *
         * @param serverVersionKey der Serverversion Key den es betrifft
         * @param newServerVersion die neue Serverversion
         * @param executor         Executor auf den die Task gestartet werden sollen
         */
        fun onServerVersionChanged(
            serverVersionKey: String,
            newServerVersion: String?,
            executor: Executor
        )
    }
}