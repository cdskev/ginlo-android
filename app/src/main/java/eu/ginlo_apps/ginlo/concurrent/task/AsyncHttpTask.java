// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.concurrent.task;

import android.os.AsyncTask;

import eu.ginlo_apps.ginlo.concurrent.task.AsyncLoaderTask;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.backend.BackendResponse;
import eu.ginlo_apps.ginlo.service.IBackendService;
import eu.ginlo_apps.ginlo.util.StringUtil;

public class AsyncHttpTask<T>
        extends AsyncTask<Void, Void, String> {

    private final static String TAG = AsyncHttpTask.class.getSimpleName();
    private final AsyncHttpCallback<T> mCallback;
    private String mErrorText;
    private T mResult;

    public AsyncHttpTask(final AsyncHttpCallback<T> callback) {
        mCallback = callback;
    }

    @Override
    protected String doInBackground(final Void... params) {

        mErrorText = null;

        final IBackendService.OnBackendResponseListener listener = new IBackendService.OnBackendResponseListener() {
            @Override
            public void onBackendResponse(final BackendResponse response) {
                if (response.isError) {
                    mErrorText = response.errorMessage == null ? "" : response.errorMessage;
                    LogUtil.e(TAG , "onBackendResponse: Error: " + mErrorText);
                } else {
                    try {
                        mResult = mCallback.asyncLoaderServerResponse(response);
                    } catch (LocalizedException e) {
                        LogUtil.e(TAG, "onBackendResponse: " + e.getMessage());
                        mErrorText = e.getMessage() + " (" + e.getIdentifier() + ")";
                    }
                }
            }
        };

        mCallback.asyncLoaderServerRequest(listener);

        return mErrorText;
    }

    @Override
    protected void onPostExecute(final String msg) {
        if (msg != null) {
            mCallback.asyncLoaderFailed(msg);
        } else {
            mCallback.asyncLoaderFinished(mResult);
        }
    }

    @Override
    protected void onCancelled(final String msg) {
        mCallback.asyncLoaderFailed(msg);
    }

    public interface AsyncHttpCallback<T> {
        void asyncLoaderServerRequest(final IBackendService.OnBackendResponseListener listener);

        T asyncLoaderServerResponse(final BackendResponse response)
                throws LocalizedException;

        void asyncLoaderFinished(T result);

        void asyncLoaderFailed(String errorMessage);
    }
}
