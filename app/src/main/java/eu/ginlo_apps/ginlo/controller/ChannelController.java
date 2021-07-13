// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller;

import android.content.ComponentCallbacks2;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import org.greenrobot.greendao.query.QueryBuilder;
import org.greenrobot.greendao.query.WhereCondition;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.crypto.spec.SecretKeySpec;

import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.concurrent.task.AsyncLoaderTask;
import eu.ginlo_apps.ginlo.concurrent.task.AsyncMultiTask;
import eu.ginlo_apps.ginlo.concurrent.task.AsyncMultiTask.AsyncMultiCallback;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.ChatOverviewController;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.controller.contracts.LowMemoryCallback;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnSendMessageListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Channel;
import eu.ginlo_apps.ginlo.greendao.ChannelDao;
import eu.ginlo_apps.ginlo.greendao.ChannelDao.Properties;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.model.backend.ChannelCategoryModel;
import eu.ginlo_apps.ginlo.model.backend.ChannelLayoutModel;
import eu.ginlo_apps.ginlo.model.backend.ChannelListModel;
import eu.ginlo_apps.ginlo.model.backend.ChannelModel;
import eu.ginlo_apps.ginlo.model.backend.ServiceListModel;
import eu.ginlo_apps.ginlo.model.backend.ServiceModel;
import eu.ginlo_apps.ginlo.model.backend.ToggleChildModel;
import eu.ginlo_apps.ginlo.model.backend.ToggleModel;
import eu.ginlo_apps.ginlo.model.backend.ToggleSettingsModel;
import eu.ginlo_apps.ginlo.model.backend.serialization.ChannelLayoutModelDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.ChannelListModelDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.ChannelModelDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.ServiceListModelDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.ServiceModelDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.ToggleChildModelDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.ToggleModelDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.ToggleSettingsModelSerializer;
import eu.ginlo_apps.ginlo.service.BackendService;
import eu.ginlo_apps.ginlo.service.IBackendService;
import eu.ginlo_apps.ginlo.util.ConfigUtil;
import eu.ginlo_apps.ginlo.util.FileUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

public class ChannelController
        implements LowMemoryCallback,
        PreferencesController.OnServerVersionChangedListener {

    public static final String TAG = ChannelController.class.getSimpleName();
    public static final String IMAGE_TYPE_PROVIDER_LABEL = "pl";
    public static final String IMAGE_TYPE_ITEM_BACKGROUND = "ib";
    public static final String IMAGE_TYPE_CHANNEL_BACKGROUND = "cb";
    public static final String IMAGE_TYPE_PROVIDER_ICON = "ic";
    public static final String IMAGE_TYPE_PROMOTION_BACKGROUND = "prb";
    public static final String IMAGE_TYPE_PROMOTION_LOGO = "prl";
    private static final String CHANNEL_IMAGE_DIR = "/channelsImages";

    private static Semaphore gSubscribeSemaphore;
    private final String mPixelDimension;

    private final SimsMeApplication mContext;
    private final Gson gson;
    private final ChannelDao mChannelDao;
    private final int mDeleteMessageMaxCount;
    private final long mDeleteMessageMaxMillisecs;
    private final List<SubscribeChannelNotificationListener> subscribeChannelNotificationListeners;
    private HashMap<String, ToggleSettingsModel> mRecommendedChannelFilterValues;
    private boolean isChannelSubsribeRunning;
    private boolean isCancelChannelSubsribeRunning;
    private Map<String, ChannelModel> mChannelModelCache;
    private List<ChannelAsyncLoaderCallback<ChannelListModel[]>> mLoadChannelDataListerners;
    private boolean mIsChannelDataLoading;

    public ChannelController(final SimsMeApplication application) {
        mContext = application;

        mDeleteMessageMaxCount = application.getResources().getInteger(R.integer.channel_delete_message_maxcount);
        mDeleteMessageMaxMillisecs = TimeUnit.DAYS.toMillis(BuildConfig.CHANNEL_DELETE_MESSAGE_MAXDAYS);

        mPixelDimension = getPixelDimensionString(mContext.getResources().getDisplayMetrics().densityDpi);

        mChannelDao = application.getChannelDao();

        final GsonBuilder gsonBuilder = new GsonBuilder();

        gsonBuilder.registerTypeAdapter(ChannelListModel.class, new ChannelListModelDeserializer());
        gsonBuilder.registerTypeAdapter(ChannelModel.class, new ChannelModelDeserializer());
        gsonBuilder.registerTypeAdapter(ToggleChildModel.class, new ToggleChildModelDeserializer());
        gsonBuilder.registerTypeAdapter(ToggleModel.class, new ToggleModelDeserializer());
        gsonBuilder.registerTypeAdapter(ChannelLayoutModel.class, new ChannelLayoutModelDeserializer());
        gsonBuilder.registerTypeAdapter(ServiceListModel.class, new ServiceListModelDeserializer());
        gsonBuilder.registerTypeAdapter(ServiceModel.class, new ServiceModelDeserializer());
        gson = gsonBuilder.create();

        application.getAppLifecycleController().registerLowMemoryCallback(this);

        subscribeChannelNotificationListeners = new ArrayList<>();

        application.getPreferencesController().registerServerChangedListener(ConfigUtil.SERVER_VERSION_GET_CHANNELS, this);
    }

    public ChannelDao getDao() {
        return mChannelDao;
    }

    public void loadChannelList(final ChannelAsyncLoaderCallback<ChannelListModel[]> callback)
            throws LocalizedException {
        
        if(callback == null) {
            throw new LocalizedException(TAG + "Callback is null!");
        }
        
        final List<Channel> channels = getChannelsFromDB(Channel.TYPE_CHANNEL);

        boolean bLoadFromServer = (channels == null || channels.size() < 1);

        if (!bLoadFromServer) {
            HashMap<String, String> cache = new HashMap<>();
            for (Channel chan : channels) {
                // Doppelte Einträge
                if (cache.containsKey(chan.getGuid())) {
                    synchronized (mChannelDao) {
                        mChannelDao.delete(chan);
                    }
                    continue;
                }
                cache.put(chan.getGuid(), chan.getGuid());
                if (StringUtil.isNullOrEmpty(chan.getChannelJsonObject())) {
                    bLoadFromServer = true;
                }
            }
        }

        if (bLoadFromServer) {
            loadChannelData(callback, null);
        } else {

            loadChannelListInternally(new ChannelAsyncLoaderCallback<ChannelListModel[]>() {
                @Override
                public void asyncLoaderFinishedWithSuccess(ChannelListModel[] input) {
                    HashMap<String, Channel> channelMap = new HashMap<>(channels.size());

                    ArrayList<ChannelListModel> result = new ArrayList<>();

                    for (Channel channel : channels) {
                        if (!StringUtil.isNullOrEmpty(channel.getGuid())) {
                            channelMap.put(channel.getGuid(), channel);
                        }
                    }

                    for (ChannelListModel model : input) {
                        if (!StringUtil.isNullOrEmpty(model.guid)) {
                            Channel channel = channelMap.get(model.guid);
                            if (channel != null && !(StringUtil.isNullOrEmpty(channel.getChannelJsonObject()))) {
                                model.promotion = channel.getPromotion() != null && channel.getPromotion();
                                model.description = channel.getDescription();
                                result.add(model);
                            }
                        }
                    }

                    callback.asyncLoaderFinishedWithSuccess(result.toArray(new ChannelListModel[]{}));
                }

                @Override
                public void asyncLoaderFinishedWithError(String errorMessage) {
                    callback.asyncLoaderFinishedWithError(errorMessage);
                }
            }, null);
        }
    }

    private void loadChannelListInternally(final ChannelAsyncLoaderCallback<ChannelListModel[]> callback, Executor executor)
            throws LocalizedException {
        final AsyncLoaderTask.AsyncLoaderCallback<ChannelListModel[]> asyncCallback
                = new AsyncLoaderTask.AsyncLoaderCallback<ChannelListModel[]>() {
            @Override
            public void asyncLoaderServerRequest(final IBackendService.OnBackendResponseListener listener) {
                BackendService.withSyncConnection(mContext)
                        .getChannelList(listener);
            }

            @Override
            public ChannelListModel[] asyncLoaderServerResponse(final BackendResponse response)
                    throws LocalizedException {
                ChannelListModel[] channelListArray = null;
                final JsonArray jsonArray = response.jsonArray;

                if (jsonArray != null) {
                    synchronized (ChannelController.this.gson) {
                        try {
                            channelListArray = ChannelController.this.gson.fromJson(jsonArray, ChannelListModel[].class);
                        } catch (JsonSyntaxException e) {
                            LogUtil.e(TAG, e.getMessage(), e);
                            final int resourceId = mContext.getResources().getIdentifier("service_tryAgainLater", "string",
                                    BuildConfig.APPLICATION_ID);

                            final String errorText = mContext.getResources().getString(resourceId);

                            throw new LocalizedException(LocalizedException.PARSE_JSON_FAILED, errorText);
                        }
                    }

                    if ((channelListArray != null) && (channelListArray.length > 0)) {
                        // Channel Dao Objekte anlegen oder aktualisieren
                        final Map<String, Channel> channelMap = getDBChannels(channelListArray, Channel.TYPE_CHANNEL);

                        checkForDeletedChannels(channelListArray);

                        for (final ChannelListModel clModel : channelListArray) {
                            if (StringUtil.isNullOrEmpty(clModel.checksum)) {
                                continue;
                            }

                            Channel channel = channelMap.get(clModel.guid);

                            if ((channel != null)) {
                                clModel.isSubscribed = channel.getIsSubscribedSave();

                                // alte lokale gespeicherte Checksumme merken
                                clModel.localChecksum = channel.getChecksum();

                                if (!StringUtil.isEqual(clModel.localChecksum, clModel.checksum)) {
                                    // DB Objekt aktualisieren
                                    channel.setShortDesc(clModel.shortDesc);
                                    channel.setChecksum(clModel.checksum);
                                    synchronized (mChannelDao) {
                                        mChannelDao.update(channel);
                                    }
                                }
                            } else {
                                // DB Objekt anlegen
                                channel = new Channel();
                                channel.setGuid(clModel.guid);
                                channel.setShortDesc(clModel.shortDesc);
                                channel.setChecksum(clModel.checksum);
                                channel.setType(Channel.TYPE_CHANNEL);
                                synchronized (mChannelDao) {
                                    mChannelDao.insert(channel);
                                }
                            }
                        }
                    }
                }
                return channelListArray;
            }

            @Override
            public void asyncLoaderFinished(final ChannelListModel[] result) {
                callback.asyncLoaderFinishedWithSuccess(result);
            }

            @Override
            public void asyncLoaderFailed(final String errorMessage) {
                callback.asyncLoaderFinishedWithError(errorMessage);
            }
        };

        if (executor == null) {
            new AsyncLoaderTask<>(asyncCallback).execute();
        } else {
            new AsyncLoaderTask<>(asyncCallback).executeOnExecutor(executor);
        }
    }

    // FIXME copy from the channelchatactivity. Try to group them somewhere
    private void autoSubscribeChannel(final Channel channel, HashMap<String, ToggleSettingsModel> aToogles) {
        final HashMap<String, ToggleSettingsModel> toogles;
        if (aToogles == null) {
            toogles = new HashMap<>();

            ChannelModel cm;
            try {
                cm = getChannelModelFromJson(channel.getChannelJsonObject(), Channel.TYPE_CHANNEL);
                for (ToggleModel toggleModel : cm.toggles) {
                    toogles.put(toggleModel.ident, new ToggleSettingsModel(toggleModel.filterOn, toggleModel.defaultValue));
                }
            } catch (LocalizedException e) {
                LogUtil.e(TAG, e.getMessage(), e);
                return;
            }
        } else {
            toogles = new HashMap<>(aToogles);
            boolean dontSubscribe = true;
            for (ToggleSettingsModel tm : toogles.values()) {
                if (StringUtil.isEqual(tm.value, "on")) {
                    dontSubscribe = false;
                    break;
                }
            }
            if (dontSubscribe) {
                return;
            }
        }

        if (gSubscribeSemaphore == null) {
            gSubscribeSemaphore = new Semaphore(1, false);
        }

        try {
            for (int i = 0; i < 10; ++i) {
                if (gSubscribeSemaphore.tryAcquire(10, TimeUnit.SECONDS)) {
                    final boolean result = subscribeChannel(channel.getId(), toogles, channel.getType(), new SubscribeChannelListener() {
                        @Override
                        public void subscribeChannelFinished(Channel channel) {
                            final Chat newChat = new Chat();
                            try {
                                //Neuen Chat
                                byte[] aesKeyBytes = Base64.decode(channel.getAesKey(), Base64.NO_WRAP);

                                newChat.setChatGuid(channel.getGuid());
                                newChat.setType(Chat.TYPE_CHANNEL);
                                newChat.setChatAESKey(new SecretKeySpec(aesKeyBytes, 0, aesKeyBytes.length, "AES"));
                                newChat.setLastChatModifiedDate(new Date().getTime());
                            } catch (LocalizedException e) {
                                LogUtil.e(TAG, e.getMessage(), e);
                                gSubscribeSemaphore.release();
                                return;
                            }

                            //neuen Chat in die DB
                            mContext.getChannelChatController().insertOrUpdateChat(newChat);

                            //Willkommensnachricht
                            String message;

                            if (!StringUtil.isNullOrEmpty(channel.getWelcomeText())) {
                                message = channel.getWelcomeText();
                            } else {
                                message = mContext.getString(R.string.channel_welcome_message,
                                        channel.getShortDesc());
                            }

                            OnSendMessageListener onSentMessageListener = new OnSendMessageListener() {
                                @Override
                                public void onSaveMessageSuccess(Message message) {
                                }

                                @Override
                                public void onSendMessageSuccess(Message message, int countNotSendMessages) {

                                }

                                @Override
                                public void onSendMessageError(Message message, String errorMessage, String localizedErrorIdentifier) {
                                }
                            };

                            mContext.getChatOverviewController().chatChanged(null, newChat.getChatGuid(), null,
                                    ChatOverviewController.CHAT_CHANGED_NEW_CHAT);

                            mContext.getChannelChatController().sendSystemInfo(newChat.getChatGuid(),
                                    null, null, null, message, -1, onSentMessageListener, false);
                            gSubscribeSemaphore.release();
                        }

                        @Override
                        public void subscribeChannelFailed(String errorMessage) {
                            gSubscribeSemaphore.release();
                        }
                    });
                    if (result) {
                        return;
                    }
                }
            }
        } catch (InterruptedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
        }
        gSubscribeSemaphore.release();
    }

    private void checkForDeletedChannels(final ChannelListModel[] serverChannels) {

        final List<Channel> localChannels = getChannelsFromDB(Channel.TYPE_CHANNEL);

        //uebverpruefung macht nur Sinn, wenn das ChannelDao existiert und schon kanaele abboniert wurden
        if (mChannelDao != null && localChannels != null && localChannels.size() != 0) {
            // ein mal ueber alle Container interieren, um Komplexitaet zu reduzieren (3n statt n * n)
            final HashMap<String, Boolean> checkMap = new HashMap<>();
            // lokale kanaele in Map schreiben
            for (Channel localChannel : localChannels) {
                final String localChannelGuid = localChannel.getGuid();
                if (!StringUtil.isNullOrEmpty(localChannelGuid)) {
                    checkMap.put(localChannelGuid, false);
                }
            }

            // lokale Werte mit Serverwerten ueberschreiben
            for (int i = 0; i < serverChannels.length; ++i) {
                String serverChannelGuid = serverChannels[i].guid;
                if (!StringUtil.isNullOrEmpty(serverChannelGuid)) {
                    checkMap.put(serverChannelGuid, true);
                }
            }

            // alle Werte suchen, die nicht ueberschrieben worden - diese existieren nur lokal, aber nicht auf dem Server
            for (String guid : checkMap.keySet()) {
                final Channel channel = getChannelFromDB(guid);

                final Boolean existsOnServer = checkMap.get(guid);

                if (channel != null) {
                    final boolean isLocallyDeleted = channel.getIsDeleted();
                    //channel ist am Server geloescht aber lokal noch nicht als geloescht markiert
                    if (existsOnServer != null && !existsOnServer && !isLocallyDeleted) {
                        channel.setIsDeleted(true);
                        mChannelDao.update(channel);
                        LogUtil.i(TAG, "Channel with GUID: " + channel.getGuid() + " is set to 'deleted'");
                    }
                    //channel ist am Server nicht (mehr) geloescht aber lokal als geloescht markiert
                    //sollte nicht vorkommen
                    else if (existsOnServer != null && existsOnServer && isLocallyDeleted) {
                        channel.setIsDeleted(false);
                        mChannelDao.update(channel);
                        LogUtil.i(TAG, "Channel with GUID: " + channel.getGuid() + " is set to 'undeleted'");
                    }
                }
            }
        }
    }

    private void loadChannelDetailsMultiBatch(final ChannelListModel[] channelListModels, final Executor executor) {

        if (channelListModels.length == 0) {
            return;
        }

        final StringBuilder channelGuids = new StringBuilder();

        //map, um spaeter darin ueber die GUID suchen zu koennen
        final Map<String, ChannelListModel> modelMap = new HashMap<>();

        for (ChannelListModel clm : channelListModels) {
            if (channelGuids.length() != 0) {
                channelGuids.append(",");
            }
            channelGuids.append(clm.guid);
            modelMap.put(clm.guid, clm);
        }

        try {
            final AsyncLoaderTask.AsyncLoaderCallback<String> asyncCallback
                    = new AsyncLoaderTask.AsyncLoaderCallback<String>() {
                @Override
                public void asyncLoaderServerRequest(IBackendService.OnBackendResponseListener listener) {
                    BackendService.withSyncConnection(mContext)
                            .getChannelDetailsBatch(channelGuids.toString(), listener);
                }

                @Override
                public String asyncLoaderServerResponse(BackendResponse response) throws LocalizedException {
                    final JsonArray jsonArray = response.jsonArray;
                    if ((jsonArray != null) && (jsonArray.size() > 0)) {
                        for (JsonElement jsonObject : jsonArray) {
                            if (jsonObject != null) {
                                synchronized (ChannelController.this.gson) {
                                    try {
                                        ChannelModel channelModel = ChannelController.this.gson.fromJson(jsonObject, ChannelModel.class);
                                        final ChannelListModel channelListModelTmp = ChannelController.this.gson.fromJson(jsonObject, ChannelListModel.class);

                                        final ChannelListModel channelListModel = modelMap.get(channelListModelTmp.guid);
                                        final Channel channel = getChannelFromDB(channelListModelTmp.guid);

                                        final boolean firstLoad = channel.getAesKey() == null;

                                        channel.setChannelJsonObject(channelModel.channelJsonObject);
                                        channel.setShortDesc(channelModel.shortDesc);
                                        channel.setAesKey(channelModel.aesKey);
                                        channel.setIv(channelModel.iv);
                                        channel.setShortLinkText(channelModel.shortLinkText);
                                        channel.setPromotion(channelModel.promotion);
                                        channel.setExternalUrl(channelModel.externalUrl);
                                        channel.setSearchText(channelModel.searchText);
                                        channel.setCategory(channelModel.category);
                                        channel.setWelcomeText(channelModel.welcomeText);
                                        channel.setSuggestionText(channelModel.suggestionText);
                                        channel.setFeedbackContact(channelModel.feedbackContact);

                                        if (channelListModel == null) {
                                            throw new LocalizedException(LocalizedException.JSON_OBJECT_NULL);
                                        }
                                        channelListModel.promotion = channelModel.promotion;

                                        if (channelModel.checksum != null) {
                                            channel.setChecksum(channelModel.checksum);
                                        }

                                        updateChannel(channel);

                                        if (channel.getIsSubscribed() == null || !channel.getIsSubscribed()) {
                                            if (isChannelMandatory(channel)) {
                                                autoSubscribeChannel(channel, null);
                                            } else if (firstLoad && isChannelRecommended(channel)) {
                                                autoSubscribeChannel(channel, mRecommendedChannelFilterValues);
                                            }
                                        }
                                    } catch (JsonSyntaxException e) {
                                        throw new LocalizedException(LocalizedException.PARSE_JSON_FAILED, e);
                                    }
                                }
                            } else {
                                throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "JSON Object null");
                            }
                        }
                    } else {
                        throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "JSON Array null");
                    }
                    return "";
                }

                @Override
                public void asyncLoaderFinished(String result) {

                    if (mLoadChannelDataListerners != null) {
                        for (ChannelAsyncLoaderCallback<ChannelListModel[]> dataListener : mLoadChannelDataListerners) {
                            dataListener.asyncLoaderFinishedWithSuccess(channelListModels);
                        }
                        mLoadChannelDataListerners = null;
                    }
                    mIsChannelDataLoading = false;
                }

                @Override
                public void asyncLoaderFailed(String errorMessage) {
                    if (mLoadChannelDataListerners != null) {
                        for (ChannelAsyncLoaderCallback<ChannelListModel[]> dataListener : mLoadChannelDataListerners) {
                            dataListener.asyncLoaderFinishedWithError(errorMessage);
                        }
                        mLoadChannelDataListerners = null;
                    }
                    mIsChannelDataLoading = false;
                }
            };

            if (executor == null) {
                new AsyncLoaderTask<>(asyncCallback).execute();
            } else {
                new AsyncLoaderTask<>(asyncCallback).executeOnExecutor(executor);
            }

            //Cache leeren
            if (mChannelModelCache != null) {
                mChannelModelCache.clear();
            }
        } catch (Exception e) {
            LogUtil.w(TAG, e.getMessage(), e);

            if (mLoadChannelDataListerners != null) {
                final int resourceId = mContext.getResources().getIdentifier("service_tryAgainLater", "string",
                        BuildConfig.APPLICATION_ID);

                String errorText = mContext.getResources().getString(resourceId);

                for (ChannelAsyncLoaderCallback<ChannelListModel[]> dataListener : mLoadChannelDataListerners) {
                    dataListener.asyncLoaderFinishedWithError(errorText);
                }
            }
            mIsChannelDataLoading = false;
        }
    }

    public boolean isChannelMandatory(final Channel channel)
            throws LocalizedException {
        final String options = getChannelModelFromJson(channel.getChannelJsonObject(), channel.getType()).options;
        return options != null && options.contains("mandatory");
    }

    private boolean isChannelRecommended(final Channel channel)
            throws LocalizedException {
        final String options = getChannelModelFromJson(channel.getChannelJsonObject(), Channel.TYPE_CHANNEL).options;
        return options != null && options.contains("recommended");
    }

    private boolean subscribeChannel(final String channelGuid,
                                     final String filter,
                                     final String type,
                                     final ChannelAsyncLoaderCallback<String> callback
    ) {

        if (!isChannelSubsribeRunning) {
            isChannelSubsribeRunning = true;

            final AsyncLoaderTask.AsyncLoaderCallback<String> asyncCallback = new AsyncLoaderTask.AsyncLoaderCallback<String>() {
                @Override
                public void asyncLoaderServerRequest(final IBackendService.OnBackendResponseListener listener) {
                    if (StringUtil.isEqual(Channel.TYPE_SERVICE, type)) {
                        BackendService.withSyncConnection(mContext)
                                .subscribeService(channelGuid, filter, listener);
                    } else {
                        BackendService.withSyncConnection(mContext)
                                .subscribeChannel(channelGuid, filter, listener);
                    }
                }

                @Override
                public String asyncLoaderServerResponse(final BackendResponse response) {
                    String checksum = null;
                    final JsonArray jsonArray = response.jsonArray;

                    if ((jsonArray != null) && (jsonArray.size() > 0)) {
                        checksum = jsonArray.get(0).getAsString();
                    }
                    return checksum;
                }

                @Override
                public void asyncLoaderFinished(final String result) {
                    ChannelController.this.isChannelSubsribeRunning = false;
                    callback.asyncLoaderFinishedWithSuccess(result);
                }

                @Override
                public void asyncLoaderFailed(final String errorMessage) {
                    ChannelController.this.isChannelSubsribeRunning = false;
                    callback.asyncLoaderFinishedWithError(errorMessage);
                }
            };

            new AsyncLoaderTask<>(asyncCallback)
                    .executeOnExecutor(AsyncLoaderTask.THREAD_POOL_EXECUTOR);

            return true;
        } else {
            return false;
        }
    }

    public boolean cancelChannelSubscription(final String channelGuid,
                                             final String type,
                                             final ChannelAsyncLoaderCallback<String> callback) {

        if (!isCancelChannelSubsribeRunning) {
            isCancelChannelSubsribeRunning = true;

            final AsyncLoaderTask.AsyncLoaderCallback<String> asyncCallback = new AsyncLoaderTask.AsyncLoaderCallback<String>() {
                @Override
                public void asyncLoaderServerRequest(final IBackendService.OnBackendResponseListener listener) {
                    if (StringUtil.isNullOrEmpty(type) || StringUtil.isEqual(Channel.TYPE_CHANNEL, type)) {
                        BackendService.withSyncConnection(mContext)
                                .cancelChannelSubscription(channelGuid, listener);
                    } else if (StringUtil.isEqual(Channel.TYPE_SERVICE, type)) {
                        BackendService.withSyncConnection(mContext)
                                .cancelServiceSubscription(channelGuid, listener);
                    }
                }

                @Override
                public String asyncLoaderServerResponse(final BackendResponse response) {
                    String channelGuid = null;
                    final JsonArray jsonArray = response.jsonArray;

                    if ((jsonArray != null) && (jsonArray.size() > 0)) {
                        channelGuid = jsonArray.get(0).getAsString();
                    }
                    return channelGuid;
                }

                @Override
                public void asyncLoaderFinished(final String result) {
                    ChannelController.this.isCancelChannelSubsribeRunning = false;
                    callback.asyncLoaderFinishedWithSuccess(result);
                }

                @Override
                public void asyncLoaderFailed(final String errorMessage) {
                    ChannelController.this.isCancelChannelSubsribeRunning = false;
                    callback.asyncLoaderFinishedWithError(errorMessage);
                }
            };

            new AsyncLoaderTask<>(asyncCallback).execute();

            return true;
        } else {
            return false;
        }
    }

    public Bitmap loadImage(final ChannelListModel clModel,
                            final String type)
            throws LocalizedException {
        if ((clModel.guid == null) || (type == null)) {
            return null;
        }

        final boolean loadImageFromServer = !StringUtil.isNullOrEmpty(clModel.localChecksum)
                && !StringUtil.isEqual(clModel.checksum, clModel.localChecksum);

        return loadImage(clModel.guid, type, loadImageFromServer);
    }

    public Bitmap loadImage(final String channelGuid,
                            final String type)
            throws LocalizedException {
        return loadImage(channelGuid, type, false);
    }

    public Bitmap loadImage(final String channelGuid,
                            final String type,
                            final boolean loadImageFromServer)
            throws LocalizedException {
        Bitmap returnImage = null;

        final File imageFile = loadImageFromFileSystem(channelGuid, type);

        if (imageFile.exists()) {
            if (loadImageFromServer) {
                imageFile.delete();
            } else {
                returnImage = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
            }
        }

        if (returnImage == null) {
            // Bild vom Server holen und im File speichern
            loadImageFromServer(imageFile, channelGuid, type);

            if (imageFile.exists()) {
                returnImage = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
            }
        }

        return returnImage;
    }

    Bitmap loadLocalImage(final String channelGuid,
                          final String type)
            throws LocalizedException {
        Bitmap returnImage = null;
        final File imageFile = loadImageFromFileSystem(channelGuid, type);

        if (imageFile.exists()) {
            returnImage = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
        }
        return returnImage;
    }

    private File loadImageFromFileSystem(final String channelGuid,
                                         final String type)
            throws LocalizedException {
        File internalFileDir;

        synchronized (mContext) {
            internalFileDir = mContext.getFilesDir();
        }

        final File channelImageDir = new File(internalFileDir.getAbsolutePath() + CHANNEL_IMAGE_DIR);

        if (!channelImageDir.isDirectory()) {
            channelImageDir.mkdirs();
        }
        final String localImageFileName = getLocalImageFileName(channelGuid, type);
        if (StringUtil.isNullOrEmpty(localImageFileName)) {
            throw new LocalizedException(LocalizedException.OBJECT_NULL);
        }

        return new File(channelImageDir, localImageFileName);
    }

    private void loadImageFromServer(final File imageFile,
                                     final String channelGuid,
                                     final String type) {
        final IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(final BackendResponse response) {
                if (!response.isError) {
                    final JsonArray jsonArray = response.jsonArray;

                    if ((jsonArray != null) && (jsonArray.get(0) != null)) {
                        final byte[] content = Base64.decode(jsonArray.get(0).getAsString(), Base64.DEFAULT);

                        FileUtil.saveToFile(imageFile, content);
                    }
                }
            }
        };

        BackendService.withSyncConnection(mContext)
                .getChannelAsset(channelGuid, type, mPixelDimension, listener);
    }

    private String getPixelDimensionString(final int densityDpi) {
        if (densityDpi < DisplayMetrics.DENSITY_HIGH) {
            return "mdpi";
        } else if (densityDpi < DisplayMetrics.DENSITY_XHIGH) {
            return "hdpi";
        } else if (densityDpi < DisplayMetrics.DENSITY_XXHIGH) {
            return "xhdpi";
        } else {
            return "xxhdpi";
        }
    }

    private String getLocalImageFileName(final String channelGuid,
                                         final String type) {
        if (StringUtil.isNullOrEmpty(channelGuid) || StringUtil.isNullOrEmpty(type)) {
            return null;
        }

        return channelGuid + "_" + type;
    }

    public void getCategoriesFromBackend(final ChannelAsyncLoaderCallback<ArrayList<ChannelCategoryModel>> callback) {

        final AsyncLoaderTask.AsyncLoaderCallback<ArrayList<ChannelCategoryModel>> asyncCallback
                = new AsyncLoaderTask.AsyncLoaderCallback<ArrayList<ChannelCategoryModel>>() {
            @Override
            public void asyncLoaderServerRequest(final IBackendService.OnBackendResponseListener listener) {
                BackendService.withSyncConnection(mContext)
                        .getChannelCategories(listener);
            }

            @Override
            public ArrayList<ChannelCategoryModel> asyncLoaderServerResponse(final BackendResponse response)
                    throws LocalizedException {
                final JsonArray jsonArray = response.jsonArray;

                ArrayList<ChannelCategoryModel> categoryList = new ArrayList<>();

                ChannelCategoryModel channelCategoryModelHeader = new ChannelCategoryModel();

                channelCategoryModelHeader.ident = null;
                channelCategoryModelHeader.imageKey = null;
                channelCategoryModelHeader.titleKey = mContext.getString(R.string.categoriesTitle);
                channelCategoryModelHeader.items = null;

                categoryList.add(channelCategoryModelHeader);

                if ((jsonArray != null) && (jsonArray.size() > 0)) {
                    for (int i = 0; i < jsonArray.size(); ++i) {
                        JsonObject item = jsonArray.get(i).getAsJsonObject();

                        if (item.has("Category")) {
                            JsonObject category = item.get("Category").getAsJsonObject();
                            ChannelCategoryModel channelCategoryModel = new ChannelCategoryModel();

                            channelCategoryModel.ident = category.has("ident") ? category.get("ident").getAsString()
                                    : null;
                            channelCategoryModel.imageKey = category.has("imageKey")
                                    ? category.get("imageKey").getAsString() : null;
                            channelCategoryModel.titleKey = category.has("titleKey")
                                    ? category.get("titleKey").getAsString() : null;
                            channelCategoryModel.items = "";
                            if (category.has("@items")) {
                                StringBuilder sb = new StringBuilder();
                                JsonArray jsa = category.getAsJsonArray("@items");

                                if (jsa != null) {
                                    for (int i2 = 0; i2 < jsa.size(); i2++) {
                                        if (i2 != 0) {
                                            sb.append(",");
                                        }
                                        sb.append(jsa.get(i2).getAsString());
                                    }
                                }
                                channelCategoryModel.items = sb.toString();
                            }
                            categoryList.add(channelCategoryModel);
                        }
                    }
                }

                return categoryList;
            }

            @Override
            public void asyncLoaderFinished(final ArrayList<ChannelCategoryModel> result) {
                callback.asyncLoaderFinishedWithSuccess(result);
            }

            @Override
            public void asyncLoaderFailed(final String errorMessage) {
                callback.asyncLoaderFinishedWithError(errorMessage);
            }
        };

        new AsyncLoaderTask<>(asyncCallback).execute();
    }

    public ChannelModel getChannelModelFromJson(final String json, final String type)
            throws LocalizedException {
        try {
            if (StringUtil.isEqual(Channel.TYPE_SERVICE, type)) {
                return gson.fromJson(json, ServiceModel.class);
            } else {
                return gson.fromJson(json, ChannelModel.class);
            }
        } catch (JsonSyntaxException e) {
            LogUtil.e(TAG, e.getMessage(), e);
            final int resourceId = mContext.getResources().getIdentifier("service_tryAgainLater", "string",
                    BuildConfig.APPLICATION_ID);

            final String errorText = mContext.getResources().getString(resourceId);

            throw new LocalizedException(LocalizedException.PARSE_JSON_FAILED, errorText);
        }
    }

    public ChannelModel getChannelModel(final String channelGuid)
            throws LocalizedException {
        Channel c = getChannelFromDB(channelGuid);

        return getChannelModel(c);
    }

    public ChannelModel getChannelModel(final Channel channel)
            throws LocalizedException {

        if ((channel == null) || StringUtil.isNullOrEmpty(channel.getChannelJsonObject())) {
            return null;
        }

        if (mChannelModelCache == null) {
            mChannelModelCache = new HashMap<>();
        }

        synchronized (mChannelModelCache) {
            if (mChannelModelCache != null && mChannelModelCache.containsKey(channel.getGuid())) {
                return mChannelModelCache.get(channel.getGuid());
            }

            ChannelModel cm = getChannelModelFromJson(channel.getChannelJsonObject(), channel.getType());

            if (cm != null && mChannelModelCache != null) {
                mChannelModelCache.put(channel.getGuid(), cm);
            }

            return cm;
        }
    }

    public Channel getChannelForId(Long channelId) {
        if (channelId == null) {
            return null;
        }

        synchronized (mChannelDao) {
            return mChannelDao.load(channelId);
        }
    }

    public void updateChannel(Channel channel) {
        if (channel == null) {
            return;
        }

        synchronized (mChannelDao) {
            mChannelDao.update(channel);
        }
    }

    public Channel getChannelFromDB(final String guid) {
        if (guid == null) {
            return null;
        }

        synchronized (mChannelDao) {
            final QueryBuilder<Channel> queryBuilder = mChannelDao.queryBuilder();

            queryBuilder.where(Properties.Guid.eq(guid));

            final List<Channel> channels = queryBuilder.build().forCurrentThread().list();

            if (channels != null && channels.size() > 0) {
                return channels.get(0);
            }
        }

        return null;
    }

    public List<Channel> getChannelsFromDB(String type) {
        synchronized (mChannelDao) {
            final List<Channel> channels;
            if (StringUtil.isEqual(Channel.TYPE_ALL, type)) {
                channels = mChannelDao.loadAll();
            } else if (StringUtil.isEqual(Channel.TYPE_CHANNEL, type)) {
                channels = mChannelDao.queryBuilder().whereOr(Properties.Type.isNull(), Properties.Type.eq(Channel.TYPE_CHANNEL)).build().forCurrentThread().list();
            } else if (StringUtil.isEqual(Channel.TYPE_SERVICE, type)) {
                channels = mChannelDao.queryBuilder().where(Properties.Type.eq(Channel.TYPE_SERVICE)).build().forCurrentThread().list();
            } else {
                return Collections.emptyList();
            }
            if ((channels != null) && (channels.size() > 0)) {
                return channels;
            }
        }
        return Collections.emptyList();
    }

    public List<Channel> getSubscribedChannelsFromDB(String type) {
        synchronized (mChannelDao) {
            QueryBuilder<Channel> builder = mChannelDao.queryBuilder().where(Properties.IsSubscribed.eq(1L));

            if (StringUtil.isEqual(Channel.TYPE_CHANNEL, type)) {
                builder.whereOr(Properties.Type.isNull(), Properties.Type.eq(Channel.TYPE_CHANNEL));
            } else if (StringUtil.isEqual(Channel.TYPE_SERVICE, type)) {
                builder.where(Properties.Type.eq(Channel.TYPE_SERVICE));
            }

            final List<Channel> channels = builder.build().forCurrentThread().list();

            if ((channels != null) && (channels.size() > 0)) {
                return channels;
            }
        }
        return Collections.emptyList();
    }

    Map<String, Channel> getDBChannels(final ChannelListModel[] models, String type) {
        final List<Channel> channels;
        synchronized (mChannelDao) {
            final QueryBuilder<Channel> queryBuilder = mChannelDao.queryBuilder();

            if (models.length <= 0) {
                return new HashMap<>();
            } else if (models.length == 1) {
                queryBuilder.where(Properties.Guid.eq(models[0].guid));
            } else if (models.length == 2) {
                queryBuilder.whereOr(Properties.Guid.eq(models[0].guid), Properties.Guid.eq(models[1].guid));
            } else {
                final WhereCondition[] moreConditions = new WhereCondition[models.length - 2];

                for (int i = 2; i < models.length; i++) {
                    moreConditions[i - 2] = Properties.Guid.eq(models[i].guid);
                }

                queryBuilder.whereOr(Properties.Guid.eq(models[0].guid), Properties.Guid.eq(models[1].guid), moreConditions);
            }

            if (StringUtil.isEqual(Channel.TYPE_CHANNEL, type)) {
                //SGA: mType == null sollte nicht vorkommen, totzdem zur Sicherheit
                channels = queryBuilder.whereOr(Properties.Type.isNull(),
                        Properties.Type.eq(Channel.TYPE_CHANNEL)).build().forCurrentThread().list();
            } else if (StringUtil.isEqual(Channel.TYPE_SERVICE, type)) {
                channels = queryBuilder.where(Properties.Type.eq(Channel.TYPE_SERVICE)).build().forCurrentThread().list();
            } else {
                return new HashMap<>();
            }
        }

        if (channels.size() > 0) {
            final Map<String, Channel> channelMap = new HashMap<>(channels.size());

            for (final Channel channel : channels) {
                channelMap.put(channel.getGuid(), channel);
            }
            return channelMap;
        } else {
            return new HashMap<>();
        }
    }

    public boolean getDisableChannelNotification(String guid) {
        Channel channel = getChannelFromDB(guid);

        if (channel != null) {
            return channel.getDisableNotification();
        } else {
            return false;
        }
    }

    public void setDisableChannelNotification(final String guid,
                                              final String type,
                                              final boolean disable,
                                              final ChannelAsyncLoaderCallback<String> callback) {
        final AsyncLoaderTask.AsyncLoaderCallback<String> asyncCallback = new AsyncLoaderTask.AsyncLoaderCallback<String>() {
            @Override
            public void asyncLoaderServerRequest(final IBackendService.OnBackendResponseListener listener) {
                if (StringUtil.isEqual(Channel.TYPE_SERVICE, type)) {
                    BackendService.withSyncConnection(mContext)
                            .setServiceNotificationForService(guid, !disable, listener);
                } else {
                    BackendService.withSyncConnection(mContext)
                            .setChannelNotificationForChannel(guid, !disable, listener);
                }
            }

            @Override
            public String asyncLoaderServerResponse(final BackendResponse response) {
                if (response.jsonObject.has("guid")) {
                    Channel channel = getChannelFromDB(guid);

                    channel.setDisableNotification(disable);
                    synchronized (mChannelDao) {
                        mChannelDao.update(channel);
                    }
                    return response.jsonObject.get("guid").getAsString();
                }
                return null;
            }

            @Override
            public void asyncLoaderFinished(final String result) {
                callback.asyncLoaderFinishedWithSuccess(result);
            }

            @Override
            public void asyncLoaderFailed(final String errorMessage) {
                callback.asyncLoaderFinishedWithError(errorMessage);
            }
        };

        new AsyncLoaderTask<>(asyncCallback).execute();
    }

    public void setRecommendedChannelFilterValues(HashMap<String, ToggleSettingsModel> recommendedChannelFilterValues) {
        mRecommendedChannelFilterValues = recommendedChannelFilterValues;
    }

    public int getDeleteMessageMaxCount() {
        return mDeleteMessageMaxCount;
    }

    public long getDeleteMessageMaxMillisecs() {
        return mDeleteMessageMaxMillisecs;
    }

    @Override
    public void onLowMemory(int state) {
        if ((state == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
                || (state == ComponentCallbacks2.TRIM_MEMORY_COMPLETE)) {
            if (mChannelModelCache != null) {
                mChannelModelCache.clear();
            }
        }
    }

    @Override
    public void onServerVersionChanged(final String serverVersionKey, final String newServerVersion, Executor executor) {
        if (!ConfigUtil.INSTANCE.channelsEnabled()) {
            mContext.getPreferencesController().serverVersionIsUpToDate(ConfigUtil.SERVER_VERSION_GET_CHANNELS, newServerVersion);
        }

        ChannelAsyncLoaderCallback<ChannelListModel[]> loadChannelDataListener = new ChannelAsyncLoaderCallback<ChannelListModel[]>() {
            @Override
            public void asyncLoaderFinishedWithSuccess(ChannelListModel[] list) {
                final List<ChannelListModel> modelsForServer = new ArrayList<>();

                for (int i = 0; i < list.length; ++i) {
                    final ChannelListModel model = list[i];
                    if (i < 7 || model.promotion) {
                        modelsForServer.add(model);
                    }
                }
                try {

                    final AsyncMultiCallback<ChannelListModel> multiCallback = new AsyncMultiCallback<ChannelListModel>() {
                        @Override
                        public String asyncRequest(ChannelListModel object) {
                            try {
                                loadImage(object, IMAGE_TYPE_ITEM_BACKGROUND);
                                loadImage(object, IMAGE_TYPE_PROVIDER_LABEL);

                                if (object.promotion) {
                                    loadImage(object, IMAGE_TYPE_PROMOTION_BACKGROUND);
                                    loadImage(object, IMAGE_TYPE_PROMOTION_LOGO);
                                }
                            } catch (LocalizedException e) {
                                LogUtil.e(TAG, e.getMessage(), e);
                            }
                            return null;
                        }

                        @Override
                        public void asyncMultiFinished() {
                            LogUtil.i(TAG, "Channel assets preloaded: ");
                            mContext.getPreferencesController().serverVersionIsUpToDate(ConfigUtil.SERVER_VERSION_GET_CHANNELS, newServerVersion);
                        }
                    };
                    final AsyncMultiTask<ChannelListModel> async = new AsyncMultiTask<>(modelsForServer, mContext, multiCallback);

                    async.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                } catch (LocalizedException e) {
                    LogUtil.w(TAG, e.getLocalizedMessage(), e);
                }
            }

            @Override
            public void asyncLoaderFinishedWithError(String errorMessage) {

            }
        };
        loadChannelData(loadChannelDataListener, executor);
    }

    private void loadChannelData(final ChannelAsyncLoaderCallback<ChannelListModel[]> listener, Executor executor) {
        if (mLoadChannelDataListerners == null) {
            mLoadChannelDataListerners = new ArrayList<>();
        }

        if ((listener != null) && !mLoadChannelDataListerners.contains(listener)) {
            mLoadChannelDataListerners.add(listener);
        }

        if (mIsChannelDataLoading) {
            return;
        }

        try {
            mIsChannelDataLoading = true;

            ChannelAsyncLoaderCallback<ChannelListModel[]> loadChannelListCallback = new ChannelAsyncLoaderCallback<ChannelListModel[]>() {
                @Override
                public void asyncLoaderFinishedWithSuccess(ChannelListModel[] result) {
                    if ((result == null) || (result.length < 1)) {
                        for (ChannelAsyncLoaderCallback<ChannelListModel[]> dataListener : mLoadChannelDataListerners) {
                            dataListener.asyncLoaderFinishedWithError(null);
                        }

                        mLoadChannelDataListerners = null;
                        mIsChannelDataLoading = false;

                        return;
                    }

                    loadChannelDetailsMultiBatch(result, null);
                }

                @Override
                public void asyncLoaderFinishedWithError(String errorMessage) {
                    for (ChannelAsyncLoaderCallback<ChannelListModel[]> dataListener : mLoadChannelDataListerners) {
                        dataListener.asyncLoaderFinishedWithError(null);
                    }

                    mLoadChannelDataListerners = null;
                    mIsChannelDataLoading = false;
                }
            };

            loadChannelListInternally(loadChannelListCallback, executor);
        } catch (LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
            mIsChannelDataLoading = false;

            for (ChannelAsyncLoaderCallback<ChannelListModel[]> dataListener : mLoadChannelDataListerners) {
                dataListener.asyncLoaderFinishedWithError(null);
            }

            mLoadChannelDataListerners = null;
        }
    }

    public void registerSubscribeChannelNotificationListener(SubscribeChannelNotificationListener listener) {
        if (listener != null) {
            subscribeChannelNotificationListeners.add(listener);
        }
    }

    public void unregisterSubscribeChannelNotificationListener(SubscribeChannelNotificationListener listener) {
        if (listener != null) {
            subscribeChannelNotificationListeners.remove(listener);
        }
    }

    private void notifySubscribeChannelNotificationListener(final boolean subscribe, final String guid) {
        for (SubscribeChannelNotificationListener listener : subscribeChannelNotificationListeners) {
            if (subscribe) {
                listener.newChannelSubscribe(guid);
            } else {
                listener.newChannelUnsubscribe(guid);
            }
        }
    }

    public boolean subscribeChannel(final Long channelId,
                                    final HashMap<String, ToggleSettingsModel> filterValues,
                                    final String type,
                                    final SubscribeChannelListener subscribeListener
    ) {
        final Channel channel = getChannelForId(channelId);
        String filter = "";

        if ((filterValues != null) && (filterValues.size() > 0)) {
            //Eingestellte Filter
            filter = getSelectedTogglesFilter(filterValues);
        }

        //start subscribe
        return subscribeChannel(channel.getGuid(), filter, type, new ChannelAsyncLoaderCallback<String>() {
            @Override
            public void asyncLoaderFinishedWithSuccess(String result) {
                String filterJson = null;

                if ((filterValues != null) && (filterValues.size() > 0)) {
                    final GsonBuilder gsonBuilder = new GsonBuilder();

                    gsonBuilder.registerTypeAdapter(ToggleSettingsModel.class,
                            new ToggleSettingsModelSerializer());

                    Gson gson = gsonBuilder.create();
                    Type stringToggleSettingsMap = new TypeToken<HashMap<String, ToggleSettingsModel>>() {
                    }
                            .getType();

                    //Toggle-Einstellungen in json schreiben
                    filterJson = gson.toJson(filterValues, stringToggleSettingsMap);
                }

                //Channel in DB aktualiseren
                channel.setFilterJsonObject(filterJson);
                channel.setIsSubscribed(true);

                updateChannel(channel);

                notifySubscribeChannelNotificationListener(true, channel.getGuid());

                if (subscribeListener != null) {
                    subscribeListener.subscribeChannelFinished(channel);
                }
            }

            @Override
            public void asyncLoaderFinishedWithError(String errorMessage) {
                if (subscribeListener != null) {
                    subscribeListener.subscribeChannelFailed(errorMessage);
                }
            }
        });
    }

    public String getSelectedTogglesFilter(Map<String, ToggleSettingsModel> filterValues) {
        StringBuilder sb = new StringBuilder();

        for (ToggleSettingsModel tsModel : filterValues.values()) {
            if (tsModel.filter != null) {
                sb.append(tsModel.filter).append("|");
            }
        }

        if (sb.length() <= 0) {
            return "";
        }

        String filter = sb.substring(0, sb.length() - 1);
        String[] filterArray = filter.split("&");

        if (filterArray.length > 1) {
            sb = new StringBuilder();
            for (String seq : filterArray) {
                sb.append(seq).append("|");
            }

            filter = sb.substring(0, sb.length() - 1);
        }

        return filter;
    }

    public interface ChannelAsyncLoaderCallback<T> {
        void asyncLoaderFinishedWithSuccess(T result);

        void asyncLoaderFinishedWithError(String errorMessage);
    }

    public interface SubscribeChannelListener {
        void subscribeChannelFinished(Channel channel);

        void subscribeChannelFailed(String errorMessage);
    }

    public interface SubscribeChannelNotificationListener {
        void newChannelSubscribe(final String guid);

        void newChannelUnsubscribe(final String guid);
    }

    public static class ChannelIdentifier {
        private ChannelListModel mClModel;

        private String mType;

        private String guid;

        public ChannelIdentifier(final ChannelListModel clModel,
                                 final String type) {
            this.mClModel = clModel;
            this.mType = type;
        }

        public ChannelIdentifier(final String guid,
                                 final String type) {
            this.guid = guid;
            this.mType = type;
        }

        public ChannelListModel getClModel() {
            return mClModel;
        }

        public String getType() {
            return mType;
        }

        public String getGuid() {
            return guid;
        }

        @Override
        public String toString() {
            return this.mType + ":" + ((guid != null) ? guid : mClModel.guid);
        }
    }
}
