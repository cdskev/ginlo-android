// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.controller;

import android.os.AsyncTask;
import android.os.Build;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.query.QueryBuilder;

import java.io.UnsupportedEncodingException;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import eu.ginlo_apps.ginlo.concurrent.task.AsyncHttpTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Account;
import eu.ginlo_apps.ginlo.greendao.DaoMaster;
import eu.ginlo_apps.ginlo.greendao.DaoSession;
import eu.ginlo_apps.ginlo.greendao.Device;
import eu.ginlo_apps.ginlo.greendao.DeviceDao;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.model.backend.DeviceModel;
import eu.ginlo_apps.ginlo.model.constant.Encoding;
import eu.ginlo_apps.ginlo.model.constant.JsonConstants;
import eu.ginlo_apps.ginlo.service.BackendService;
import eu.ginlo_apps.ginlo.service.IBackendService;
import eu.ginlo_apps.ginlo.util.ChecksumUtil;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.util.SecurityUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.XMLUtil;

/**
 * Created by Florian on 23.01.18.
 */

public class DeviceController {
    public final static String TAG = DeviceController.class.getSimpleName();
    private final SimsMeApplication mApplication;
    private final DeviceDao mDeviceDao;
    private Device mOwnDevice;
    private AsyncHttpTask<List<DeviceModel>> mLoadDevicesTask;
    private AsyncHttpTask<Void> mDeleteDeviceTask;
    private AsyncHttpTask<Void> mSetDeviceNameTask;

    public DeviceController(final SimsMeApplication application) {
        this.mApplication = application;

        Database db = mApplication.getDataBase();

        DaoMaster daoMaster = new DaoMaster(db);
        DaoSession daoSession = daoMaster.newSession();

        mDeviceDao = daoSession.getDeviceDao();

        loadDevice();

//      mApplication.getAppLifecycleController().registerAppLifecycleCallbacks(this);
//      mApplication.getLoginController().registerAppLockLifecycleCallbacks(this);
    }

    public static String getDefaultDeviceName() {
        String deviceName = "";
        String brand = Build.BRAND;

        if (!StringUtil.isNullOrEmpty(brand)) {
            deviceName = brand.toUpperCase();
        } else {
            String manufacturer = Build.MANUFACTURER;
            if (!StringUtil.isNullOrEmpty(manufacturer)) {
                deviceName = manufacturer.toUpperCase();
            }
        }

        String model = Build.MODEL;

        if (!StringUtil.isNullOrEmpty(model)) {
            if (model.toLowerCase(Locale.US).startsWith(deviceName.toLowerCase(Locale.US))) {
                deviceName = model.toUpperCase();
            } else {
                deviceName = deviceName + " " + model;
            }
        }

        return deviceName;
    }

    private void loadDevice() {
        final List<Device> devices;

        synchronized (mDeviceDao) {
            final QueryBuilder<Device> queryBuilder = mDeviceDao.queryBuilder();

            queryBuilder.where(DeviceDao.Properties.OwnDevice.eq(true));

            devices = queryBuilder.build().forCurrentThread().list();
        }

        if (devices != null && devices.size() > 0) {
            mOwnDevice = devices.get(0);
            LogUtil.i(TAG, "Device loaded.");
        }
    }

    @Nullable
    public Device getOwnDevice() {
        return mOwnDevice;
    }

    private void insertOrUpdateDevice(final Device device) {
        synchronized (mDeviceDao) {
            if (device.getId() == null) {
                mDeviceDao.insert(device);
            } else {
                mDeviceDao.update(device);
            }
        }
    }

    void createOwnDevice(@NonNull final Account account)
            throws LocalizedException {
        Device device = new Device();
        device.setGuid(account.getDeviceGuid());
        device.setAccountGuid(account.getAccountGuid());
        device.setOwnDevice(true);

        String publicKey = XMLUtil.getXMLFromPublicKey(mApplication.getKeyController().getDeviceKeyPair().getPublic());
        device.setPublicKey(publicKey);

        PrivateKey pk;
        try {
            pk = mApplication.getKeyController().getUserKeyPair().getPrivate();
        } catch (LocalizedException e) {
            String accPKString = account.getPrivateKey();
            if (StringUtil.isNullOrEmpty(accPKString)) {
                throw e;
            }
            pk = XMLUtil.getPrivateKeyFromXML(accPKString);

            if (pk == null) {
                throw e;
            }
        }

        device.setSignedDevicePublicKeyFingerprint(getSignedDevicePublicKeyFingerprint(publicKey, pk));

        insertOrUpdateDevice(device);

        mOwnDevice = device;
    }

    void createDeviceFromModel(@NonNull final DeviceModel deviceModel, final boolean isOwnDevice)
            throws LocalizedException {
        Device device = new Device();
        device.setGuid(deviceModel.guid);
        device.setAccountGuid(deviceModel.accountGuid);
        device.setOwnDevice(isOwnDevice);
        device.setPublicKey(deviceModel.publicKey);
        device.setSignedDevicePublicKeyFingerprint(deviceModel.pkSign);

        insertOrUpdateDevice(device);

        if (isOwnDevice) {
            mOwnDevice = device;
        }
    }

    String getSignedDevicePublicKeyFingerprint(@NonNull final String devicePublicKeyXML, @NonNull final PrivateKey accountPrivateKey)
            throws LocalizedException {
        String sha256Hash = ChecksumUtil.getSHA256ChecksumForString(devicePublicKeyXML);

        if (StringUtil.isNullOrEmpty(sha256Hash)) {
            throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Public Key Hash ist null");
        }

        byte[] pkSignBytes = SecurityUtil.signData(accountPrivateKey, sha256Hash.getBytes(), false);

        return Base64.encodeToString(pkSignBytes, Base64.DEFAULT);
    }

    void deleteOwnDevice() {
        if (mOwnDevice != null && mOwnDevice.getId() != null) {
            synchronized (mDeviceDao) {
                mDeviceDao.delete(mOwnDevice);
            }
        }
    }

    public void loadDevicesFromBackend(final GenericActionListener<List<DeviceModel>> listener)
            throws LocalizedException {
        if (mLoadDevicesTask != null) {
            return;
        }

        mLoadDevicesTask = new AsyncHttpTask<>(new AsyncHttpTask.AsyncHttpCallback<List<DeviceModel>>() {
            @Override
            public void asyncLoaderServerRequest(IBackendService.OnBackendResponseListener listener) {
                BackendService.withSyncConnection(mApplication)
                        .getDevices(listener);
            }

            @Override
            public List<DeviceModel> asyncLoaderServerResponse(BackendResponse response)
                    throws LocalizedException {
                if (response.jsonArray == null) {
                    throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "JSON Array null");
                }
                List<DeviceModel> deviceModelList = new ArrayList<>(response.jsonArray.size());
                for (JsonElement je : response.jsonArray) {
                    JsonObject deviceJO = JsonUtil.searchJsonObjectRecursive(je, JsonConstants.DEVICE_OBJECT_KEY);
                    if (deviceJO == null) {
                        continue;
                    }

                    deviceModelList.add(new DeviceModel(deviceJO));
                }
                Collections.sort(deviceModelList, new Comparator<DeviceModel>() {
                    @Override
                    public int compare(DeviceModel d1, DeviceModel d2) {
                        String comp1 = d1.name == null ? "" : d1.name;
                        String comp2 = d2.name == null ? "" : d2.name;
                        return comp1.compareTo(comp2);
                    }
                });

                return deviceModelList;
            }

            @Override
            public void asyncLoaderFinished(List<DeviceModel> result) {
                mLoadDevicesTask = null;
                if (listener != null) {
                    listener.onSuccess(result);
                }
            }

            @Override
            public void asyncLoaderFailed(String errorMessage) {
                mLoadDevicesTask = null;
                if (listener != null) {
                    listener.onFail(errorMessage, null);
                }
            }
        });

        mLoadDevicesTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void deleteDeviceFromBackend(final String deviceGuid, final GenericActionListener<Void> listener)
            throws LocalizedException {

        if(StringUtil.isNullOrEmpty(deviceGuid)) {
            LogUtil.w(TAG, "deleteDeviceFromBackend: DeviceGuid is " + deviceGuid + "!");
            return;
        }

        if (mDeleteDeviceTask != null) {
            return;
        }

        mDeleteDeviceTask = new AsyncHttpTask<>(new AsyncHttpTask.AsyncHttpCallback<Void>() {
            @Override
            public void asyncLoaderServerRequest(IBackendService.OnBackendResponseListener listener) {
                BackendService.withSyncConnection(mApplication)
                        .deleteDevice(deviceGuid, listener);
            }

            @Override
            public Void asyncLoaderServerResponse(BackendResponse response)
                    throws LocalizedException {
                return null;
            }

            @Override
            public void asyncLoaderFinished(Void result) {
                mDeleteDeviceTask = null;
                if (listener != null) {
                    listener.onSuccess(result);
                }
            }

            @Override
            public void asyncLoaderFailed(String errorMessage) {
                mDeleteDeviceTask = null;
                if (listener != null) {
                    listener.onFail(errorMessage, null);
                }
            }
        });

        mDeleteDeviceTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void changeDeviceNameAtBackend(final String deviceGuid, final String deviceName, final GenericActionListener<Void> listener)
            throws LocalizedException {
        if (mSetDeviceNameTask != null) {
            return;
        }

        if(StringUtil.isNullOrEmpty(deviceGuid) || StringUtil.isNullOrEmpty(deviceName)) {
            LogUtil.w(TAG, "changeDeviceNameAtBackend: Invalid parameters (deviceGuid=" + deviceGuid + ", deviceName=" + deviceName + ").");
            return;
        }

        final String deviceNameEncoded;

        try {
            deviceNameEncoded = Base64.encodeToString(deviceName.getBytes(Encoding.UTF8), Base64.DEFAULT);
        } catch (UnsupportedEncodingException e) {
            throw new LocalizedException(LocalizedException.UNSUPPORTED_ENCODING_EXCEPTION, "Unsupported Encoding", e);
        }

        mSetDeviceNameTask = new AsyncHttpTask<>(new AsyncHttpTask.AsyncHttpCallback<Void>() {
            @Override
            public void asyncLoaderServerRequest(IBackendService.OnBackendResponseListener listener) {
                BackendService.withSyncConnection(mApplication)
                        .setDeviceName(deviceGuid, deviceNameEncoded, listener);
            }

            @Override
            public Void asyncLoaderServerResponse(BackendResponse response)
                    throws LocalizedException {
                return null;
            }

            @Override
            public void asyncLoaderFinished(Void result) {
                mSetDeviceNameTask = null;
                if (listener != null) {
                    listener.onSuccess(result);
                }
            }

            @Override
            public void asyncLoaderFailed(String errorMessage) {
                mSetDeviceNameTask = null;
                if (listener != null) {
                    listener.onFail(errorMessage, null);
                }
            }
        });

        mSetDeviceNameTask.executeOnExecutor(AsyncHttpTask.THREAD_POOL_EXECUTOR);
    }
}
