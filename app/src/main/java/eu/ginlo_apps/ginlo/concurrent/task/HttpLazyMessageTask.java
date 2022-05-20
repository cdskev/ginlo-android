// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.concurrent.task;

import eu.ginlo_apps.ginlo.cert.CustomSSLSocketFactory;
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
                               int connectTimeout) {
        super(keyStore, httpPostParams, username, password, requestGuid);

        if (mConnectTimeout != connectTimeout) {
            mConnectTimeout = connectTimeout;
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
