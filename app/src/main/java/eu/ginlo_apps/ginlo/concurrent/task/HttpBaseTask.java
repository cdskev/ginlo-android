// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.concurrent.task;

import android.os.Build;
import android.util.Base64;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocketFactory;

import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import eu.ginlo_apps.ginlo.concurrent.task.HttpLazyMessageTask;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.model.param.HttpPostParams;
import eu.ginlo_apps.ginlo.util.StreamUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

public abstract class HttpBaseTask
    extends ConcurrentTask
{
    private final String userAgent = String.format(
        "Android SimsMe %s API %s",
        AppConstants.getAppVersionName(),
        Build.VERSION.SDK_INT
    );
    
    private final static String TAG = HttpBaseTask.class.getSimpleName();

    private final String mUsername;
    private final String mPassword;
    private final String mRequestGuid;
    private final HttpPostParams mHttpPostParams;
    private final KeyStore mKeyStore;
    private final OnConnectionDataUpdatedListener mOnConnectionDataUpdatedListener;
    int mConnectionTimeout;
    String mCommand;
    private String mResult;

    HttpBaseTask(
        KeyStore keyStore,
        HttpPostParams httpPostParams,
        String username,
        String password,
        String requestGuid
    ) {
        this(keyStore, httpPostParams, username, password, requestGuid, null);
    }

    HttpBaseTask(
        KeyStore keyStore,
        HttpPostParams httpPostParams,
        String username,
        String password,
        String requestGuid,
        OnConnectionDataUpdatedListener onConnectionDataUpdatedListener
    ) {
        super();

        this.mHttpPostParams = httpPostParams;
        this.mUsername = username;
        this.mPassword = password;
        this.mRequestGuid = requestGuid;
        this.mKeyStore = keyStore;
        this.mOnConnectionDataUpdatedListener = onConnectionDataUpdatedListener;

        if (httpPostParams != null && httpPostParams.getNameValuePairs().get("cmd") != null) {
            mCommand = httpPostParams.getNameValuePairs().get("cmd");
        }
    }

    private String getQuery(Map<String, String> params) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (first) {
                first = false;
            } else {
                result.append("&");
            }

            result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            result.append("=");
            result.append(URLEncoder
                .encode(entry.getValue() != null ? entry.getValue() : "", "UTF-8"));
        }

        return result.toString();
    }

    protected abstract SSLSocketFactory getSocketFactory(KeyStore keyStore)
        throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException,
               KeyManagementException;

    @Override
    public void run() {
        super.run();

        String result = null;

        for (int i = 0; i < 3; i++) {
            boolean isLazyTask = this instanceof HttpLazyMessageTask;
            if (isLazyTask) {
                // Beim LazyTask nicht wiederholen
                i = 2;
            }
            try {
                String urlString = mHttpPostParams.getUrl();

                if (urlString.endsWith("/MsgService") && (mUsername == null || mPassword == null)) {
                    LogUtil.e(TAG, String.format(
                            "Missing required username(%b) or password(%b) for auth'd communication with backend!",
                            mUsername == null,
                            mPassword == null));
                    throw new ProtocolException();
                }

                URL url = new URL(urlString);
                HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();

                urlConnection.setSSLSocketFactory(getSocketFactory(mKeyStore));

                urlConnection.setDoOutput(true);
                urlConnection.setDoInput(true);

                if ((mUsername != null) && (mPassword != null)) {
                    String concatString = mUsername + ":" + mPassword;
                    String authString = "Basic " + Base64.encodeToString(
                        concatString.getBytes(StandardCharsets.UTF_8),
                        Base64.NO_WRAP
                    );
                    urlConnection.addRequestProperty("Authorization", authString);
                }

                urlConnection.setRequestMethod("POST");
                urlConnection.setUseCaches(false);

                String postBody = getQuery(mHttpPostParams.getNameValuePairs());
                byte[] postBodyBytes = postBody.getBytes(StandardCharsets.UTF_8);

                urlConnection.setRequestProperty("Accept-Encoding", "gzip");
                urlConnection.addRequestProperty("User-Agent", userAgent);
                urlConnection
                    .addRequestProperty("X-Client-Version", "" + AppConstants.getAppVersionCode());
                urlConnection.addRequestProperty("X-Client-App", AppConstants.getAppName());

                if (!StringUtil.isNullOrEmpty(mCommand)) {
                    urlConnection.addRequestProperty("X-Client-Command", mCommand);
                }

                urlConnection.addRequestProperty("Connection", "keep-alive");

                //only zip above 3000bytes
                if (postBodyBytes.length > 3000) // 3000
                {
                    urlConnection.addRequestProperty("content-type", "application/x-gzip");
                    urlConnection.addRequestProperty("Content-Encoding", "gzip");

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    GZIPOutputStream gzip = new GZIPOutputStream(baos);
                    gzip.write(postBodyBytes);
                    gzip.flush();
                    gzip.close();
                    postBodyBytes = baos.toByteArray();

                    if (mConnectionTimeout != 0) {
                        urlConnection.setReadTimeout(mConnectionTimeout);
                        urlConnection.setConnectTimeout(mConnectionTimeout);
                    } else {
                        urlConnection.setReadTimeout(30 * 1000);
                        urlConnection.setConnectTimeout(30 * 1000);
                    }
                    LogUtil.i(TAG, "Connection Timeout set to: " + urlConnection.getConnectTimeout());

                } else {
                    urlConnection
                        .addRequestProperty("content-type", "application/x-www-form-urlencoded");
                    if (mConnectionTimeout != 0) {
                        urlConnection.setReadTimeout(mConnectionTimeout);
                        urlConnection.setConnectTimeout(mConnectionTimeout);
                    } else {
                        urlConnection.setReadTimeout(10 * 1000);
                        urlConnection.setConnectTimeout(10 * 1000);
                    }
                }
                LogUtil.i(TAG, "Connection Timeout set to: " + urlConnection.getConnectTimeout());
                urlConnection.setFixedLengthStreamingMode(postBodyBytes.length);

                urlConnection.connect();

                InputStream in = null;
                try {
                    BufferedOutputStream dos =
                        new BufferedOutputStream(urlConnection.getOutputStream());
                    dos.write(postBodyBytes);
                    dos.flush();
                    dos.close();

                    mResponseCode = urlConnection.getResponseCode();

                    if (mResponseCode != 200) {
                        LogUtil.e(TAG, "HTTP-Status:" + mResponseCode);
                        error();
                        return;
                    }
                    int contentLength = urlConnection.getHeaderFieldInt("X-Uncompressed-Length", 0);

                    if (contentLength != 0 && mOnConnectionDataUpdatedListener != null) {
                        mOnConnectionDataUpdatedListener.setFileSize(contentLength);
                    }

                    in = new BufferedInputStream(urlConnection.getInputStream());
                    String encoding = urlConnection.getContentEncoding();

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    if ("gzip".equals(encoding)) {
                        in = new GZIPInputStream(in);
                        StreamUtil.copyStreams(in, baos, mOnConnectionDataUpdatedListener);
                    } else {
                        StreamUtil.copyStreams(in, baos, mOnConnectionDataUpdatedListener);
                    }
                    result = new String(baos.toByteArray(), StandardCharsets.UTF_8);
                } catch (EOFException e) {
                    LogUtil.d(TAG, e.getMessage(), e);
                } finally {
                    StreamUtil.closeStream(in);
                    urlConnection.disconnect();
                }
            } catch (SocketTimeoutException e) {
                if (i < 2) {
                    continue;
                }
                if (!(this instanceof HttpLazyMessageTask)) {
                    LogUtil.e(TAG, e.getMessage(), e);
                    mLocalizedException = new LocalizedException(
                        LocalizedException.BACKEND_REQUEST_FAILED,
                        e.getClass().getSimpleName() + " " + e.getMessage()
                    );
                    error();
                    return;
                }
            } catch (UnsupportedEncodingException | IllegalStateException | ProtocolException e) {
                if (i < 2) {
                    continue;
                }
                LogUtil.e(TAG, e.getMessage(), e);
                mLocalizedException = new LocalizedException(
                    LocalizedException.BACKEND_REQUEST_FAILED,
                    e.getClass().getSimpleName() + " " + e.getMessage()
                );
                error();
                return;
            } catch (IOException e) {
                if (i < 2) {
                    continue;
                }
                if (e instanceof SSLHandshakeException) {
                    /* SSL certificate error*/
                    mLocalizedException =
                        new LocalizedException(LocalizedException.SSL_HANDSHAKE_FAILED, e);
                    LogUtil.e(TAG, e.getMessage(), e);
                    error();
                    return;
                }

                if (isLazyTask) {
                    if ((e instanceof SSLException) || (e instanceof EOFException)
                        || (e
                        .getCause() instanceof EOFException) || (e instanceof InterruptedIOException)) {
                        LogUtil.w(TAG, e.getMessage(), e);
                    } else {
                        LogUtil.e(TAG, e.getMessage(), e);
                        mLocalizedException = new LocalizedException(
                            LocalizedException.BACKEND_REQUEST_FAILED,
                            e.getClass().getSimpleName() + " " + e.getMessage()
                        );
                        error();
                        return;
                    }
                } else {
                    LogUtil.e(TAG, e.getMessage(), e);
                    mLocalizedException = new LocalizedException(
                        LocalizedException.BACKEND_REQUEST_FAILED,
                        e.getClass().getSimpleName() + " " + e.getMessage()
                    );
                    error();
                    return;
                }
            } catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException | KeyManagementException e) {
                if (i < 2) {
                    continue;
                }
                mLocalizedException =
                    new LocalizedException(LocalizedException.SSL_HANDSHAKE_FAILED, e);
                LogUtil.e(TAG, e.getMessage(), e);
                error();
                return;
            }

            break;
        }

        this.mResult = result;
        complete();
    }

    @Override
    public Object[] getResults() {
        return new Object[]
            {
                mRequestGuid,
                mResult
            };
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.mConnectionTimeout = connectionTimeout;
    }

    public interface OnConnectionDataUpdatedListener {
        void onConnectionDataUpdated(int value);

        void setFileSize(long fileSize);
    }
}
