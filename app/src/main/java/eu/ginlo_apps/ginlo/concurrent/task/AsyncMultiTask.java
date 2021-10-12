// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.concurrent.task;

import android.os.AsyncTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import java.util.ArrayList;
import java.util.List;

public class AsyncMultiTask<T>
        extends AsyncTask<Void, Void, String> {
    private final AsyncMultiCallback<T> mCallback;

    private final List<T> mObjects;

    public AsyncMultiTask(final List<T> objects,
                          final SimsMeApplication context,
                          final AsyncMultiCallback<T> callback)
            throws LocalizedException {
        if ((context == null) || (context.getAccountController() == null)
                || (context.getAccountController().getAccount() == null)) {
            throw new LocalizedException(LocalizedException.ACCOUNT_UNKNOWN);
        }

        mCallback = callback;
        if (objects != null) {
            mObjects = new ArrayList<>(objects);
        } else {
            mObjects = null;
        }
    }

    @Override
    protected String doInBackground(final Void... params) {
        String errorText = null;

        if ((mObjects == null) || (mObjects.size() < 1)) {
            return null;
        }

        for (final T object : mObjects) {
            errorText = mCallback.asyncRequest(object);

            if (errorText != null) {
                break;
            }
        }

        return errorText;
    }

    @Override
    protected void onPostExecute(final String msg) {
        if (msg == null) {
            mCallback.asyncMultiFinished();
        }
    }

    public interface AsyncMultiCallback<T> {
        /**
         * @return value != null, error text, the task will be canceled
         */
        String asyncRequest(final T object);

        void asyncMultiFinished();
    }
}
