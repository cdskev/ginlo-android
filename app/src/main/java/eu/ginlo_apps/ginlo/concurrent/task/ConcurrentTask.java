// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.concurrent.task;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import eu.ginlo_apps.ginlo.concurrent.listener.ConcurrentTaskListener;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.log.LogUtil;

public abstract class ConcurrentTask
        extends AsyncTask<Void, Void, Void> {
    public static final int STATE_RUNNING = 0;

    public static final int STATE_COMPLETE = 1;

    public static final int STATE_ERROR = 2;

    public static final int STATE_CANCELED = 3;

    private final boolean mCalledFromMainThread;
    LocalizedException mLocalizedException;
    int mResponseCode;
    private int state;
    private String id;
    private ConcurrentTaskListener listener;

    protected ConcurrentTask() {

        mCalledFromMainThread = Looper.getMainLooper().getThread().equals(Thread.currentThread());
    }

    @Override
    protected Void doInBackground(Void... params) {
        this.run();

        return null;
    }

    @Override
    protected void onCancelled(Void aVoid) {
        if (getListener() != null) {
            getListener().onStateChanged(this, state);
        }
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        if (state == STATE_COMPLETE) {
            if (getListener() != null) {
                long startTime = System.currentTimeMillis();
                getListener().onStateChanged(this, state);
                long endTime = System.currentTimeMillis();
                if (Looper.getMainLooper().getThread().equals(Thread.currentThread())) {
                    if ((endTime - startTime) > 10) {
                        LogUtil.i(this.getClass().getSimpleName(),
                                "Needed Time for onPostExecute on Mainthread :" + (endTime - startTime) + " Listener " + getListener().getClass());
                    }
                }
            }
        }
    }

    public void runSync() {
        doInBackground((Void[]) null);
    }

    public void run() {
        state = STATE_RUNNING;
    }

    public void addListener(ConcurrentTaskListener listener) {
        this.listener = listener;
    }

    private ConcurrentTaskListener getListener() {
        return listener;
    }

    protected void complete() {
        if (isRunning()) {
            state = STATE_COMPLETE;
        }

        if (getStatus() != Status.RUNNING) {
            if (mCalledFromMainThread) {
                new Handler(Looper.getMainLooper()).post(
                        new Runnable() {
                            @Override
                            public void run() {
                                onPostExecute(null);
                            }
                        }
                );
            } else {
                onPostExecute(null);
            }
        }
    }

    void error() {
        state = STATE_ERROR;

        if (getStatus() == Status.RUNNING) {
            cancel(false);
        } else {
            if (mCalledFromMainThread) {
                new Handler(Looper.getMainLooper()).post(
                        new Runnable() {
                            @Override
                            public void run() {
                                onCancelled(null);
                            }
                        }
                );
            } else {
                onCancelled(null);
            }
        }
    }

    public void cancel() {
        state = STATE_CANCELED;

        if (getStatus() == Status.RUNNING) {
            cancel(true);
        } else {
            if (mCalledFromMainThread) {
                new Handler(Looper.getMainLooper()).post(
                        new Runnable() {
                            @Override
                            public void run() {
                                onCancelled(null);
                            }
                        }
                );
            } else {
                onCancelled(null);
            }
        }
    }

    int getState() {
        return state;
    }

    private boolean isRunning() {
        return getState() == STATE_RUNNING;
    }

    public boolean isCanceled() {
        return getState() == STATE_CANCELED;
    }

    boolean isError() {
        return getState() == STATE_ERROR;
    }

    boolean isComplete() {
        return getState() == STATE_COMPLETE;
    }

    @Override
    public int hashCode() {
        if (id == null) {
            id = GuidUtil.generateGuid("");
        }

        return id.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        return (this == other);
    }

    public abstract Object[] getResults();

    public LocalizedException getLocalizedException() {
        return mLocalizedException;
    }

    public int getResponseCode() {
        return mResponseCode;
    }
}
