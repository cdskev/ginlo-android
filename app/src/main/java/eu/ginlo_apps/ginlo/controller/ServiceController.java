// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller;

import androidx.annotation.NonNull;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.concurrent.task.AsyncLoaderTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Channel;
import eu.ginlo_apps.ginlo.greendao.ChannelDao;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.model.backend.ServiceListModel;
import eu.ginlo_apps.ginlo.model.backend.ServiceModel;
import eu.ginlo_apps.ginlo.model.backend.serialization.ServiceListModelDeserializer;
import eu.ginlo_apps.ginlo.model.backend.serialization.ServiceModelDeserializer;
import eu.ginlo_apps.ginlo.service.BackendService;
import eu.ginlo_apps.ginlo.service.IBackendService;
import eu.ginlo_apps.ginlo.util.ConfigUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.greenrobot.greendao.query.QueryBuilder;

public class ServiceController implements PreferencesController.OnServerVersionChangedListener {
    private final Gson gson;
    private final ChannelDao mChannelDao;
    private final SimsMeApplication mContext;
    private final eu.ginlo_apps.ginlo.controller.ChannelController mChannelController;
    private List<ServicesChangedListener> mServicesChangedListener;

    public ServiceController(final SimsMeApplication application) {
        mContext = application;
        mChannelController = application.getChannelController();
        mChannelDao = application.getChannelDao();

        final GsonBuilder gsonBuilder = new GsonBuilder();

        gsonBuilder.registerTypeAdapter(ServiceListModel.class, new ServiceListModelDeserializer());
        gsonBuilder.registerTypeAdapter(ServiceModel.class, new ServiceModelDeserializer());
        gson = gsonBuilder.create();

        mContext.getPreferencesController().registerServerChangedListener(ConfigUtil.SERVER_VERSION_GET_SERVICES, this);
    }

    public void addServiceChangedListener(@NonNull final ServicesChangedListener listener) {
        if (mServicesChangedListener == null) {
            mServicesChangedListener = new ArrayList<>(5);
        }

        if (!mServicesChangedListener.contains(listener)) {
            mServicesChangedListener.add(listener);
        }
    }

    public void removeServiceChangedListener(@NonNull final ServicesChangedListener listener) {
        if (mServicesChangedListener != null) {
            mServicesChangedListener.remove(listener);
        }
    }

    private void loadServiceDetailsMultiBatch(final ServiceListModel[] serviceListModels, final LoadServiceDataListener listener, Executor executor) {

        if (serviceListModels == null || serviceListModels.length == 0) {
            if (listener != null) {
                listener.loadServiceDataFailed(true, null);
            }
            return;
        }

        final StringBuilder serviceGuids = new StringBuilder();

        for (ServiceListModel clm : serviceListModels) {
            if (serviceGuids.length() != 0) {
                serviceGuids.append(",");
            }
            serviceGuids.append(clm.guid);
        }

        try {
            final AsyncLoaderTask.AsyncLoaderCallback<String> asyncCallback = new AsyncLoaderTask.AsyncLoaderCallback<String>() {
                private boolean unsubscribedServicesExist = false;

                @Override
                public void asyncLoaderServerRequest(IBackendService.OnBackendResponseListener listener) {
                    BackendService.withSyncConnection(mContext)
                            .getServiceDetailsBatch(serviceGuids.toString(), listener);
                }

                @Override
                public String asyncLoaderServerResponse(BackendResponse response) throws LocalizedException {

                    final JsonArray jsonArray = response.jsonArray;
                    if ((jsonArray != null) && (jsonArray.size() > 0)) {
                        //for (int i = 0; i < jsonArray.size(); ++i)
                        for (JsonElement jsonObject : jsonArray) {
                            //JsonObject jsonObject = jsonArray.get(i);
                            if (jsonObject != null) {
                                synchronized (ServiceController.this.gson) {
                                    try {
                                        ServiceModel serviceModel = ServiceController.this.gson.fromJson(jsonObject, ServiceModel.class);
                                        final ServiceListModel serviceListModelTmp = ServiceController.this.gson.fromJson(jsonObject, ServiceListModel.class);

                                        final Channel service = mChannelController.getChannelFromDB(serviceListModelTmp.guid);

                                        service.setChannelJsonObject(serviceModel.channelJsonObject);
                                        service.setShortDesc(serviceModel.shortDesc);
                                        service.setAesKey(serviceModel.aesKey);
                                        service.setIv(serviceModel.iv);
                                        service.setShortLinkText(serviceModel.shortLinkText);
                                        service.setExternalUrl(serviceModel.externalUrl);
                                        service.setSearchText(serviceModel.searchText);
                                        service.setWelcomeText(serviceModel.welcomeText);
                                        service.setSuggestionText(serviceModel.suggestionText);
                                        service.setFeedbackContact(serviceModel.feedbackContact);

                                        if (serviceModel.checksum != null) {
                                            service.setChecksum(serviceModel.checksum);
                                        }

                                        mChannelController.updateChannel(service);

                                        if (!unsubscribedServicesExist && (service.getIsSubscribed() == null || !service.getIsSubscribed())) {
                                            unsubscribedServicesExist = true;
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
                    listener.loadServiceDataFinish(serviceListModels, unsubscribedServicesExist);
                }

                @Override
                public void asyncLoaderFailed(String errorMessage) {
                    listener.loadServiceDataFailed(false, errorMessage);
                }
            };
            new AsyncLoaderTask<>(asyncCallback).executeOnExecutor(executor);
        } catch (Exception e) {
            LogUtil.w(ChannelController.class.getSimpleName(), "LOC EXC", e);

            final int resourceId = mContext.getResources().getIdentifier("service_tryAgainLater", "string",
                    BuildConfig.APPLICATION_ID);

            String errorText = mContext.getResources().getString(resourceId);

            listener.loadServiceDataFailed(false, errorText);
        }
    }

    public boolean hasUnsubscribedServices() {

        synchronized (mChannelDao) {
            final QueryBuilder<Channel> queryBuilder = mChannelDao.queryBuilder();

            queryBuilder.where(ChannelDao.Properties.Type.eq(Channel.TYPE_SERVICE));
            queryBuilder.whereOr(ChannelDao.Properties.IsSubscribed.isNull(), ChannelDao.Properties.IsSubscribed.eq(false));

            final List<Channel> channels = queryBuilder.build().forCurrentThread().list();

            if (channels != null && channels.size() > 0) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void onServerVersionChanged(final String serverVersionKey, final String newServerVersion, final Executor executor) {
        try {
            final LoadServiceDataListener loadServiceDataListener = new LoadServiceDataListener() {
                @Override
                public void loadServiceDataFinish(ServiceListModel[] list, boolean unsubscribedServicesExist) {
                    mContext.getPreferencesController().serverVersionIsUpToDate(ConfigUtil.SERVER_VERSION_GET_SERVICES, newServerVersion);
                    if (mServicesChangedListener != null) {
                        for (ServicesChangedListener listener : mServicesChangedListener) {
                            listener.onServicesChanged(unsubscribedServicesExist);
                        }
                    }
                }

                @Override
                public void loadServiceDataFailed(boolean noChannels, String errorMessage) {
                    ///
                }
            };

            ServiceAsyncLoaderCallback<ServiceListModel[]> loadServiceListCallback = new ServiceAsyncLoaderCallback<ServiceListModel[]>() {
                @Override
                public void asyncLoaderFinishedWithSuccess(ServiceListModel[] result) {

                    loadServiceDetailsMultiBatch(result, loadServiceDataListener, executor);
                }

                @Override
                public void asyncLoaderFinishedWithError(String errorMessage) {
                    //
                }
            };

            loadServiceList(loadServiceListCallback, executor);
        } catch (LocalizedException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
        }
    }

    private void loadServiceList(final ServiceAsyncLoaderCallback<ServiceListModel[]> callback, Executor executor)
            throws LocalizedException {
        final AsyncLoaderTask.AsyncLoaderCallback<ServiceListModel[]> asyncCallback
                = new AsyncLoaderTask.AsyncLoaderCallback<ServiceListModel[]>() {
            @Override
            public void asyncLoaderServerRequest(final IBackendService.OnBackendResponseListener listener) {
                BackendService.withSyncConnection(mContext)
                        .getServices(listener);
            }

            @Override
            public ServiceListModel[] asyncLoaderServerResponse(final BackendResponse response)
                    throws LocalizedException {
                ServiceListModel[] serviceListArray = null;
                final JsonArray jsonArray = response.jsonArray;

                if (jsonArray != null) {
                    synchronized (ServiceController.this.gson) {
                        try {
                            serviceListArray = ServiceController.this.gson.fromJson(jsonArray, ServiceListModel[].class);
                        } catch (JsonSyntaxException e) {
                            final int resourceId = mContext.getResources().getIdentifier("service_tryAgainLater", "string",
                                    BuildConfig.APPLICATION_ID);

                            final String errorText = mContext.getResources().getString(resourceId);

                            throw new LocalizedException(LocalizedException.PARSE_JSON_FAILED, errorText);
                        }
                    }

                    if ((serviceListArray != null) && (serviceListArray.length > 0)) {
                        // Channel Dao Objekte anlegen oder aktualisieren
                        final Map<String, Channel> serviceMap = mChannelController.getDBChannels(serviceListArray, Channel.TYPE_SERVICE);

                        for (final ServiceListModel clModel : serviceListArray) {
                            if (StringUtil.isNullOrEmpty(clModel.checksum)) {
                                continue;
                            }

                            Channel service = serviceMap.get(clModel.guid);

                            if (service != null) {
                                clModel.isSubscribed = service.getIsSubscribedSave();

                                if (service.getChecksum() != null) {
                                    // alte lokale gespeicherte Checksumme merken
                                    clModel.localChecksum = service.getChecksum();
                                }

                                if (!StringUtil.isEqual(clModel.localChecksum, clModel.checksum)) {
                                    // DB Objekt aktualisieren
                                    service.setShortDesc(clModel.shortDesc);
                                    service.setChecksum(clModel.checksum);
                                    synchronized (mChannelDao) {
                                        mChannelDao.update(service);
                                    }
                                }
                            } else {
                                // DB Objekt anlegen
                                service = new Channel();
                                service.setGuid(clModel.guid);
                                service.setShortDesc(clModel.shortDesc);
                                service.setChecksum(clModel.checksum);
                                service.setType(Channel.TYPE_SERVICE);
                                synchronized (mChannelDao) {
                                    mChannelDao.insert(service);
                                }
                            }
                        }
                    }
                }
                return serviceListArray;
            }

            @Override
            public void asyncLoaderFinished(final ServiceListModel[] result) {
                callback.asyncLoaderFinishedWithSuccess(result);
            }

            @Override
            public void asyncLoaderFailed(final String errorMessage) {
                callback.asyncLoaderFinishedWithError(errorMessage);
            }
        };

        new AsyncLoaderTask<>(asyncCallback)
                .executeOnExecutor(executor != null ? executor : AsyncLoaderTask.THREAD_POOL_EXECUTOR);
    }

    interface LoadServiceDataListener {
        void loadServiceDataFinish(final ServiceListModel[] list, final boolean unsubscribedServicesExist);

        void loadServiceDataFailed(final boolean noChannels,
                                   final String errorMessage);
    }

    public interface ServicesChangedListener {
        void onServicesChanged(final boolean hasUnsubscribedServices);
    }

    public interface ServiceAsyncLoaderCallback<T> {
        void asyncLoaderFinishedWithSuccess(T result);

        void asyncLoaderFinishedWithError(String errorMessage);
    }
}
