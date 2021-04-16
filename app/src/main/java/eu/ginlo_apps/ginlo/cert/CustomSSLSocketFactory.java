// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.cert;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;

import eu.ginlo_apps.ginlo.cert.CustomX509TrustManager;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class CustomSSLSocketFactory
        extends SSLSocketFactory {

    private static final String WRONG_PARAMETER_EXCEPTION = "createSocket: wrong parameters";
    private final SSLContext sslContext;

    /**
     *
     * @throws NoSuchAlgorithmException [!EXC_DESCRIPTION!]
     * @throws KeyManagementException   [!EXC_DESCRIPTION!]
     */
    public CustomSSLSocketFactory(KeyStore truststore)
            throws NoSuchAlgorithmException, KeyManagementException {
        super();

        TrustManager trustManager = new CustomX509TrustManager(truststore);

        sslContext = SSLContext.getInstance("TLS");

        sslContext.init(null, new TrustManager[]{trustManager}, null);
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return new String[0];
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return new String[0];
    }

    /**
     * @throws IOException [!EXC_DESCRIPTION!]
     */
    @Override
    public Socket createSocket(Socket socket,
                               String host,
                               int port,
                               boolean autoClose)
            throws IOException {
        return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        throw new IOException(WRONG_PARAMETER_EXCEPTION);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        throw new IOException(WRONG_PARAMETER_EXCEPTION);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        throw new IOException(WRONG_PARAMETER_EXCEPTION);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        throw new IOException(WRONG_PARAMETER_EXCEPTION);
    }
}
