/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.ginlo_apps.ginlo.view.cropimage;

import java.util.Iterator;
import java.util.WeakHashMap;
import org.jetbrains.annotations.NotNull;

public class BitmapManager {

    private static BitmapManager sManager = null;
    private final WeakHashMap<Thread, ThreadStatus> mThreadStatus =
            new WeakHashMap<>();

    private BitmapManager() {

    }

    public static synchronized BitmapManager instance() {

        if (sManager == null) {
            sManager = new BitmapManager();
        }
        return sManager;
    }

    private synchronized ThreadStatus getOrCreateThreadStatus(Thread t) {

        ThreadStatus status = mThreadStatus.get(t);
        if (status == null) {
            status = new ThreadStatus();
            mThreadStatus.put(t, status);
        }
        return status;
    }

    synchronized void cancelThreadDecoding(ThreadSet threads) {

        for (Thread t : threads) {
            cancelThreadDecoding(t);
        }
    }

    private synchronized void cancelThreadDecoding(Thread t) {

        ThreadStatus status = getOrCreateThreadStatus(t);
        status.mAllowDecoding = false;

        notifyAll();
    }

    private static class ThreadStatus {

        boolean mAllowDecoding = true;

        @NotNull
        @Override
        public String toString() {

            String s;

            if (mAllowDecoding) {
                s = "Allow";
            } else {
                s = "Cancel";
            }
            s = "thread state = " + s;
            return s;
        }
    }

    public static class ThreadSet implements Iterable<Thread> {
        private final WeakHashMap<Thread, Object> mWeakCollection =
                new WeakHashMap<>();

        @NotNull
        public Iterator<Thread> iterator() {
            return mWeakCollection.keySet().iterator();
        }
    }
}
