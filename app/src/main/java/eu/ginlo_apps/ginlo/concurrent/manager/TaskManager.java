// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.concurrent.manager;

import android.os.AsyncTask;
import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.Executor;

public abstract class TaskManager {
    private static final HashMap<String, Executor> TASK_EXECUTOR = new HashMap<>();

    private final HashSet<ConcurrentTask> mTasks;

    TaskManager() {
        mTasks = new HashSet<>();
    }

    public void cancelAllTasks() {
        synchronized (mTasks) {
            // Um ganz sivher zu sein, iterieren wir über ein Kopie der Tasks ...
            for (ConcurrentTask task : (HashSet<ConcurrentTask>) mTasks.clone()) {
                task.cancel();
                removeTask(task);
            }
        }
    }

    void execute(ConcurrentTask task) {
        addTask(task);

        if (!TASK_EXECUTOR.containsKey(task.getClass().getSimpleName())) {
            TASK_EXECUTOR.put(task.getClass().getSimpleName(), new SerialExecutor());
        }
        task.executeOnExecutor(TASK_EXECUTOR.get(task.getClass().getSimpleName()));
    }

    private void addTask(ConcurrentTask task) {
        synchronized (mTasks) {
            mTasks.add(task);
        }
    }

    private void removeTask(ConcurrentTask task) {
        synchronized (mTasks) {
            mTasks.remove(task);
        }
    }

    private static class SerialExecutor
            implements Executor {

        final ArrayDeque<Runnable> mTasks = new ArrayDeque<>();

        Runnable mActive;

        public synchronized void execute(final Runnable r) {
            mTasks.offer(new Runnable() {
                public void run() {
                    try {
                        r.run(); //es geht explizit darum, dass threads hintereinander ausgefuehrt werden
                    } finally {
                        scheduleNext();
                    }
                }
            });

            if (mActive == null) {
                scheduleNext();
            }
        }

        synchronized void scheduleNext() {
            if ((mActive = mTasks.poll()) != null) {
                AsyncTask.THREAD_POOL_EXECUTOR.execute(mActive);
            }
        }
    }
}
