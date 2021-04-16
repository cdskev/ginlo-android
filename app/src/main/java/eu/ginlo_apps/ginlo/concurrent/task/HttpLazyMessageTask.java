// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.concurrent.task;

import eu.ginlo_apps.ginlo.cert.CustomSSLSocketFactory;
import eu.ginlo_apps.ginlo.concurrent.task.HttpBaseTask;
import eu.ginlo_apps.ginlo.model.param.HttpPostParams;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.SSLSocketFactory;

public class HttpLazyMessageTask
        extends HttpBaseTask {
    private static SSLSocketFactory gSSLSocketFactory;

    public HttpLazyMessageTask(KeyStore keyStore,
                               HttpPostParams httpPostParams,
                               String username,
                               String password,
                               String requestGuid,
                               int connectionTimeout) {
        super(keyStore, httpPostParams, username, password, requestGuid);

        if (mConnectionTimeout != connectionTimeout) {
            mConnectionTimeout = connectionTimeout;
        }
    }

    @Override
    protected SSLSocketFactory getSocketFactory(KeyStore keyStore) throws NoSuchAlgorithmException, KeyManagementException {
        synchronized (HttpLazyMessageTask.class) {
            if (gSSLSocketFactory == null) {
                gSSLSocketFactory = new CustomSSLSocketFactory(keyStore);
                System.setProperty("http.keepAlive", "true");
            }
            return gSSLSocketFactory;
        }
    }
}
