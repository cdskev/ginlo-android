// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.concurrent.task;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import eu.ginlo_apps.ginlo.log.LogUtil;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class DownloadImageTask
        extends ConcurrentTask {
    private final String urlString;

    private Bitmap result;

    public DownloadImageTask(String urlString) {
        super();
        this.urlString = urlString;
    }

    @Override
    public void run() {
        super.run();
        try {
            URL url = new URL(urlString);

            result = BitmapFactory.decodeStream((InputStream) url.getContent());
        } catch (IOException e) {
            LogUtil.e(this.getClass().getName(), e.getMessage(), e);
            error();
        }
        complete();
    }

    @Override
    public Object[] getResults() {
        return new Object[]{result};
    }
}
