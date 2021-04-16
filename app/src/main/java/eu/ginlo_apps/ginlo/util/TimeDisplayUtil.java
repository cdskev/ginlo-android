// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util;

import android.os.Handler;
import android.os.Message;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;

public class TimeDisplayUtil {
    private final TimeHandler timeHandler;
    private final TextView textView;
    private final OnClockStoppedHandler stopHandler;
    private Timer timer;
    private UpdateTimeTask updateTimeTask;
    private int maxSeconds;
    private boolean stopped;

    public TimeDisplayUtil(TextView textView,
                           OnClockStoppedHandler stopHandler) {
        this.textView = textView;
        timeHandler = new TimeHandler(this);
        maxSeconds = -1;
        this.stopHandler = stopHandler;
    }

    public void start() {
        stopped = false;
        textView.setText("00:00");
        timer = new Timer();
        updateTimeTask = new UpdateTimeTask();
        timer.scheduleAtFixedRate(updateTimeTask, 0, 1000);
    }

    public void start(int maxSeconds) {
        this.maxSeconds = maxSeconds;
        start();
    }

    public void stop() {
        if (!stopped) {
            stopped = true;
            if (timer != null) {
                timer.cancel();
                timer.purge();
            }
            maxSeconds = -1;

            if (stopHandler != null) {
                stopHandler.onStop();
            }
        }
    }

    public interface OnClockStoppedHandler {
        void onStop();
    }

    private static class TimeHandler extends Handler {

        private final WeakReference<TimeDisplayUtil> parent;

        TimeHandler(TimeDisplayUtil parent) {
            this.parent = new WeakReference<>(parent);
        }

        @Override
        public void handleMessage(Message msg) {
            TimeDisplayUtil that = parent.get();

            if ((that.maxSeconds > 0) && (that.updateTimeTask.seconds > that.maxSeconds)) {
                that.stop();
            } else {
                that.textView.setText(that.updateTimeTask.getFormattedTime());
            }
        }
    }

    private class UpdateTimeTask extends TimerTask {
        private int seconds;

        UpdateTimeTask() {
            seconds = -1;
        }

        String getFormattedTime() {
            int m = seconds / 60;
            String mm = String.valueOf(m);

            if (m < 10) {
                mm = "0" + mm;
            }

            int s = seconds % 60;
            String ss = String.valueOf(s);

            if (s < 10) {
                ss = "0" + ss;
            }

            return mm + ":" + ss;
        }

        @Override
        public void run() {
            timeHandler.obtainMessage(1).sendToTarget();
            seconds += 1;
        }
    }
}
