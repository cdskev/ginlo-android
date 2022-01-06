// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.concurrent.task;

import eu.ginlo_apps.ginlo.cert.CustomSSLSocketFactory;
import eu.ginlo_apps.ginlo.concurrent.task.HttpBaseTask;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.param.HttpPostParams;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocketFactory;

public class HttpSingleTask
        extends HttpBaseTask {
    private final static String TAG = HttpSingleTask.class.getSimpleName();
    private static SSLSocketFactory gSSLSocketFactory;

    private static Semaphore gSemaphore = null;

    public HttpSingleTask(KeyStore keyStore,
                          HttpPostParams httpPostParams,
                          String username,
                          String password,
                          String requestGuid,
                          OnConnectionDataUpdatedListener onConnectionDataUpdatedListener
    ) {
        super(keyStore, httpPostParams, username, password, requestGuid, onConnectionDataUpdatedListener);
    }

    private static Semaphore getSyncLock() {
        synchronized (HttpSingleTask.class) {
            if (gSemaphore == null) {
                LogUtil.i(TAG, "Create new Single HTTP Task Lock");
                gSemaphore = new Semaphore(3, true);
            }
            return gSemaphore;
        }
    }

    @Override
    public void run() {
        Semaphore sem = getSyncLock();
        try {
            if (!sem.tryAcquire(10, TimeUnit.SECONDS)) {
                gSemaphore = null;
                sem = getSyncLock();
                sem.tryAcquire(10, TimeUnit.SECONDS);
            }
            if (mCommand != null) {
                LogUtil.i(TAG, "Start command: " + mCommand);
            }
            super.run();
            if (mCommand != null) {
                LogUtil.i(TAG, "End command: "  + mCommand);
            }
        } catch (InterruptedException ignored) {
        } finally {
            sem.release(1);
        }
    }

    @Override
    protected SSLSocketFactory getSocketFactory(KeyStore keyStore) throws NoSuchAlgorithmException, KeyManagementException {
        synchronized (HttpSingleTask.class) {
            if (gSSLSocketFactory == null) {
                gSSLSocketFactory = new CustomSSLSocketFactory(keyStore);
                System.setProperty("http.keepAlive", "true");
            }
            return gSSLSocketFactory;
        }
    }
}
