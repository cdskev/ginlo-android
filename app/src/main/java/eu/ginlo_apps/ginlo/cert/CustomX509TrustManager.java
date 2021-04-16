// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.cert;

import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.util.ChecksumUtil;
import eu.ginlo_apps.ginlo.log.LogUtil;

import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.HashMap;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
class CustomX509TrustManager
        implements X509TrustManager {

    private final KeyStore truststore;

    private final HashMap<String, Boolean> fingerPrints;

    public CustomX509TrustManager(KeyStore truststore) {
        this.truststore = truststore;
        this.fingerPrints = new HashMap<>();

        initFingerPrints();
    }

    private void initFingerPrints() {
        try {
            Enumeration<String> aliases = truststore.aliases();

            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                Certificate cert = truststore.getCertificate(alias);

                fingerPrints.put(ChecksumUtil.getSHA1ChecksumForData(cert.getPublicKey().getEncoded()), true);
            }
        } catch (KeyStoreException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
        }
    }

    /**
     * @throws CertificateException [!EXC_DESCRIPTION!]
     */
    @Override
    public void checkClientTrusted(X509Certificate[] chain,
                                   String authType)
            throws CertificateException {
    }

    /**
     * @throws CertificateException [!EXC_DESCRIPTION!]
     */
    @Override
    public void checkServerTrusted(X509Certificate[] chain,
                                   String authType)
            throws CertificateException {
        boolean trusted = true;

        for (X509Certificate certificate : chain) {
            String sha1 = ChecksumUtil.getSHA1ChecksumForData(certificate.getPublicKey().getEncoded());

            trusted = trusted && fingerPrints.containsKey(sha1);
        }

        if (!trusted && BuildConfig.CERTIFICATE_PINNING_ENABLED) {
            throw new CertificateException("Untrusted certificate!");
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return null;
    }
}
