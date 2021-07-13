// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.concurrent.task;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.util.Base64;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
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

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocketFactory;

import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.model.param.HttpPostParams;
import eu.ginlo_apps.ginlo.util.FileUtil;
import eu.ginlo_apps.ginlo.util.StreamUtil;
import eu.ginlo_apps.ginlo.util.StringUtil;

public abstract class HttpBaseTask
    extends ConcurrentTask
{
    private final String userAgent = String.format(
        "Android ginlo %s API %s",
        AppConstants.getAppVersionName(),
        Build.VERSION.SDK_INT
    );
    
    private final static String TAG = HttpBaseTask.class.getSimpleName();
    private final static String WAKELOCK_TAG = "ginlo:" + TAG;
    private final static int WAKELOCK_FLAGS = PowerManager.PARTIAL_WAKE_LOCK;

    private final String mUsername;
    private final String mPassword;
    private final String mRequestGuid;
    private final HttpPostParams mHttpPostParams;
    private final KeyStore mKeyStore;
    private final OnConnectionDataUpdatedListener mOnConnectionDataUpdatedListener;
    private final SimsMeApplication simsMeApplication = SimsMeApplication.getInstance();
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

    /**
     * Collect all to-send message parameters to URLencoded POST request string.
     * This method replaces deprecated getQuery() and is doing the job using streams.
     * Big attachments could otherwise result in oom.
     * @param params Map with parameters for message to send
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    private File prepareQueryFile(Map<String, String> params)
            throws UnsupportedEncodingException, IOException {

        File destFile = File.createTempFile("post", "query");
        Writer result = new FileWriter(destFile);
        boolean first = true;

        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (first) {
                first = false;
            } else {
                result.append("&");
            }

            result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            result.append("=");

            if(entry.getKey().equalsIgnoreCase("message")) {
                String v = entry.getValue();
                if(v != null && v.startsWith("/")) {
                    // Message is stored in separate file
                    File messageFile = new File(v);
                    if(!messageFile.exists()) {
                        LogUtil.e(TAG, "prepareQueryFile(): Message file " + v + " not found!");
                        throw new IOException();
                    }
                    FileInputStream fi = new FileInputStream(messageFile);
                    int read = 0;
                    int size = 0;
                    byte[] data = new byte[StreamUtil.STREAM_BUFFER_SIZE];
                    String dataString = null;
                    while ((read = fi.read(data, 0, StreamUtil.STREAM_BUFFER_SIZE)) > 0) {
                        dataString = new String(data, StandardCharsets.UTF_8).substring(0, read);
                        result.append(URLEncoder.encode(dataString, "UTF-8"));
                        size += read;
                        if(mOnConnectionDataUpdatedListener != null) {
                            mOnConnectionDataUpdatedListener.onConnectionDataUpdated(size);

                        }
                    }
                    fi.close();
                    FileUtil.deleteFile(messageFile);
                    continue;
                }
            }
            result.append(URLEncoder
                    .encode(entry.getValue() != null ? entry.getValue() : "", "UTF-8"));
        }
        result.close();
        return destFile;
    }

    protected abstract SSLSocketFactory getSocketFactory(KeyStore keyStore)
        throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException,
               KeyManagementException;

    @Override
    public void run() {
        super.run();

        String result = null;
        File postQueryFile = null;
        File gzipPostQueryFile = null;
        File queryResultsFile = null;

        PowerManager pm = (PowerManager)simsMeApplication.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(WAKELOCK_FLAGS, WAKELOCK_TAG);
        wl.acquire(10*60*1000L /*10 minutes*/);

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

                urlConnection.setRequestProperty("Accept-Encoding", "gzip");
                urlConnection.addRequestProperty("User-Agent", userAgent);
                urlConnection
                        .addRequestProperty("X-Client-Version", "" + AppConstants.getAppVersionCode());
                urlConnection.addRequestProperty("X-Client-App", AppConstants.getAppName());

                if (!StringUtil.isNullOrEmpty(mCommand)) {
                    urlConnection.addRequestProperty("X-Client-Command", mCommand);
                }

                urlConnection.addRequestProperty("Connection", "keep-alive");

                // KS: Use postQuery file to avoid oom in case of *really* big requests.
                // Only create file once and keep until (hopefully) sent successfully.
                if(postQueryFile == null) {
                    postQueryFile = prepareQueryFile(mHttpPostParams.getNameValuePairs());
                }

                File outputFile = postQueryFile;
                long outputFileSize = postQueryFile.length();

                // Only zip above 4 KBytes
                if (outputFileSize > 4096)
                {
                    urlConnection.addRequestProperty("Content-Type", "application/x-gzip");
                    urlConnection.addRequestProperty("Content-Encoding", "gzip");

                    // Only zip if not already done for this task
                    if(gzipPostQueryFile == null) {
                        gzipPostQueryFile = FileUtil.gzipFile(postQueryFile);
                    }

                    if(gzipPostQueryFile == null) {
                        LogUtil.e(TAG, "Failed to gzip " + postQueryFile.getPath());
                        // Continue with unzipped file ...
                    } else {
                        outputFile = gzipPostQueryFile;
                        outputFileSize = gzipPostQueryFile.length();
                    }
                } else {
                    urlConnection.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                }

                LogUtil.d(TAG, "Using queryFile: " + outputFile.getPath()
                        + " (Size: " + outputFileSize + ")");


                if (mConnectionTimeout == 0) {
                    mConnectionTimeout = 60 * 1000;
                }

                urlConnection.setConnectTimeout(mConnectionTimeout);

                // KS: Calculate read timeout depending on send request size. Otherwise we run
                // into the timeout, if sending the request takes too long.
                // Assume upload rate at min. 100 Kbit/s
                int readTimeoutInMillis = (int) (outputFileSize / 100);
                if (readTimeoutInMillis < mConnectionTimeout) {
                    readTimeoutInMillis = mConnectionTimeout;
                }
                urlConnection.setReadTimeout(readTimeoutInMillis);
                LogUtil.i(TAG, "Start " + (isLazyTask ? "lazy ":"")  + "request with connect/read timeouts set to: " +
                        urlConnection.getConnectTimeout() + "/" +
                        urlConnection.getReadTimeout());

                urlConnection.setFixedLengthStreamingMode(outputFileSize);
                urlConnection.connect();

                InputStream in = null;

                try {
                    if (mOnConnectionDataUpdatedListener != null) {
                        mOnConnectionDataUpdatedListener.setFileSize(outputFileSize);
                    }


                    FileInputStream fis = new FileInputStream(outputFile);
                    BufferedOutputStream dos = new BufferedOutputStream(urlConnection.getOutputStream());
                    StreamUtil.copyStreamsWithProgressIndication(fis, dos, mOnConnectionDataUpdatedListener);
                    dos.close();
                    fis.close();

                    mResponseCode = urlConnection.getResponseCode();

                    LogUtil.i(TAG, "Connected and request sent. Got HTTP-Status " + mResponseCode + ".");
                    if (mResponseCode != 200) {
                        // Release wakelock
                        wl.release();
                        error();
                        return;
                    }

                    int contentLength = urlConnection.getHeaderFieldInt("Content-Length", 0);
                    if(contentLength == 0) {
                        contentLength = urlConnection.getHeaderFieldInt("X-Uncompressed-Length", 0);
                        if(contentLength == 0) {
                            LogUtil.d(TAG, "Backend will serve us unknown number of bytes!");
                        } else {
                            LogUtil.d(TAG, "Backend will serve us " + contentLength + " bytes (X-Uncompressed-Length)");
                        }
                    } else {
                        LogUtil.d(TAG, "Backend will serve us " + contentLength + " bytes (Content-Length)");
                    }

                    if (contentLength != 0 && mOnConnectionDataUpdatedListener != null) {
                        mOnConnectionDataUpdatedListener.setFileSize(contentLength);
                    }

                    in = new BufferedInputStream(urlConnection.getInputStream());
                    String encoding = urlConnection.getContentEncoding();
                    if ("gzip".equals(encoding)) {
                        in = new GZIPInputStream(in);
                    }

                    // Only load response to file if we are working on attachments
                    if(mCommand.equals("getAttachment") && (contentLength == 0 || contentLength > FileUtil.MAX_RAM_PROCESSING_SIZE)) {
                        // Much data expected or size unknown - use queryResultsFile
                        queryResultsFile = File.createTempFile("query", "results");
                        FileOutputStream fos = new FileOutputStream(queryResultsFile);
                        StreamUtil.copyStreamsWithProgressIndication(in, fos, mOnConnectionDataUpdatedListener);
                        fos.close();
                        result = queryResultsFile.getPath();
                        LogUtil.d(TAG, "Created queryResultsFile: " + result
                                + " (Size: " + queryResultsFile.length() + ")");
                    } else {
                        // Less data in response - can be managed in memory
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        StreamUtil.copyStreamsWithProgressIndication(in, baos, mOnConnectionDataUpdatedListener);
                        result = new String(baos.toByteArray(), StandardCharsets.UTF_8);
                        baos.close();
                        LogUtil.d(TAG, "Process queryResults in RAM - size: " + result.length());
                    }

                } catch (IOException e) {
                    if("timeout".equalsIgnoreCase(e.getMessage())) {
                        LogUtil.w(TAG, "Got timeout while connecting to backend!");
                    } else {
                        LogUtil.e(TAG, "Got IOException while connecting to backend: " + e.getMessage(), e);
                    }
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
                        LocalizedException.BACKEND_REQUEST_FAILED, TAG + " " + e.getMessage()
                    );
                    // Release wakelock
                    wl.release();
                    error();
                    return;
                }
            } catch (UnsupportedEncodingException | IllegalStateException | ProtocolException e) {
                if (i < 2) {
                    continue;
                }
                LogUtil.e(TAG, e.getMessage(), e);
                mLocalizedException = new LocalizedException(
                    LocalizedException.BACKEND_REQUEST_FAILED, TAG + " " + e.getMessage()
                );
                // Release wakelock
                wl.release();
                error();
                return;
            } catch (IOException e) {
                if (i < 2) {
                    continue;
                }
                if (e instanceof SSLHandshakeException) {
                    /* SSL certificate error*/
                    mLocalizedException = new LocalizedException(
                            LocalizedException.SSL_HANDSHAKE_FAILED, e);
                    LogUtil.e(TAG, e.getMessage(), e);
                    // Release wakelock
                    wl.release();
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
                            LocalizedException.BACKEND_REQUEST_FAILED, TAG + " " + e.getMessage()
                        );
                        // Release wakelock
                        wl.release();
                        error();
                        return;
                    }
                } else {
                    LogUtil.e(TAG, e.getMessage(), e);
                    mLocalizedException = new LocalizedException(
                        LocalizedException.BACKEND_REQUEST_FAILED, TAG + " " + e.getMessage()
                    );
                    // Release wakelock
                    wl.release();
                    error();
                    return;
                }
            } catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException | KeyManagementException e) {
                if (i < 2) {
                    continue;
                }
                mLocalizedException = new LocalizedException(LocalizedException.SSL_HANDSHAKE_FAILED, e);
                LogUtil.e(TAG, e.getMessage(), e);
                // Release wakelock
                wl.release();
                error();
                return;
            } finally {
                // Clean up temp files if request succeeded or failed 3 times
                if(i == 2 || mResponseCode == 200) {
                    FileUtil.deleteFile(postQueryFile);
                    FileUtil.deleteFile(gzipPostQueryFile);
                }
            }
            break;
        }

        LogUtil.d(TAG, "Backend call done.");
        // Release wakelock
        wl.release();
        if(wl.isHeld()) {
            LogUtil.w(TAG, "Wakelock held!");
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
