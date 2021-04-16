// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.view.cropimage;

import android.os.Bundle;
import eu.ginlo_apps.ginlo.BaseActivity;
import java.util.ArrayList;

public abstract class MonitoredActivity
        extends BaseActivity {

    private final ArrayList<LifeCycleListener> mListeners = new ArrayList<>();

    public void addLifeCycleListener(LifeCycleListener listener) {
        if (mListeners.contains(listener)) {
            return;
        }
        mListeners.add(listener);
    }

    public void removeLifeCycleListener(LifeCycleListener listener) {
        mListeners.remove(listener);
    }

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        for (LifeCycleListener listener : mListeners) {
            listener.onActivityCreated();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (LifeCycleListener listener : mListeners) {
            listener.onActivityDestroyed();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        for (LifeCycleListener listener : mListeners) {
            listener.onActivityStarted();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        for (LifeCycleListener listener : mListeners) {
            listener.onActivityStopped();
        }
    }

    interface LifeCycleListener {
        void onActivityCreated();

        void onActivityDestroyed();

        void onActivityStarted();

        void onActivityStopped();
    }

    public static class LifeCycleAdapter
            implements LifeCycleListener {
        public void onActivityCreated() {
        }

        public void onActivityDestroyed() {
        }

        public void onActivityStarted() {
        }

        public void onActivityStopped() {
        }
    }
}
