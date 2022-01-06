// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.concurrent.manager;

import android.os.AsyncTask;
import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;

public class SerialExecutor implements Executor {
    private final ArrayDeque<Runnable> mTasks = new ArrayDeque<>();

    private Runnable mActive;

    public synchronized void execute(final Runnable r) {
        mTasks.offer(new Runnable() {
            public void run() {
                try {
                    r.run(); // es geht explizit darum, dass threads hintereinander ausgefuehrt werden
                } finally {
                    scheduleNext();
                }
            }
        });

        if (mActive == null) {
            scheduleNext();
        }
    }

    private synchronized void scheduleNext() {
        if ((mActive = mTasks.poll()) != null) {
            AsyncTask.THREAD_POOL_EXECUTOR.execute(mActive);
        }
    }

    public synchronized void cancelAll() {
        if (mActive != null) {
            cancelActive();
        }

        while ((mActive = mTasks.poll()) != null) {
            cancelActive();
        }
    }

    public synchronized int size() {
        return mTasks.size();
    }

    private void cancelActive() {
        if (mActive != null) {
            if (mActive instanceof ConcurrentTask) {
                ((ConcurrentTask) mActive).cancel();
            } else if (mActive instanceof AsyncTask) {
                ((AsyncTask) mActive).cancel(true);
            }
        }
    }
}