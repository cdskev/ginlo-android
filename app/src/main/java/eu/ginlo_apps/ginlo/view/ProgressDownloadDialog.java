// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.view;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.util.ColorUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;

/**
 * Created by Florian on 22.06.16.
 */
public class ProgressDownloadDialog extends AlertDialog {
    private ProgressBar mProgressBar;
    private TextView mSecondaryTextView;

    private ProgressDownloadDialog(Context context) {
        super(context);
    }

    protected ProgressDownloadDialog(Context context, int theme) {
        super(context, theme);
    }

    protected ProgressDownloadDialog(Context context, boolean cancelable, OnCancelListener cancelListener) {
        super(context, cancelable, cancelListener);
    }

    public static ProgressDownloadDialog buildProgressDownloadDialog(Activity context) {
        ProgressDownloadDialog progressDownloadDialog = new ProgressDownloadDialog(context);
        View view = context.getLayoutInflater().inflate(R.layout.download_progress_view, null);
        if (view != null) {
            ProgressBar pb = view.findViewById(R.id.progressBar_download);
            progressDownloadDialog.setProgressBar(pb);

            TextView tv = view.findViewById(R.id.progressBar_download_secondary_text_view);
            progressDownloadDialog.setSecondaryTextView(tv);
            progressDownloadDialog.setView(view);
            progressDownloadDialog.setCancelable(false);
        }
        progressDownloadDialog.setCanceledOnTouchOutside(false);

        return progressDownloadDialog;
    }

    private void setProgressBar(ProgressBar progressBar) {
        mProgressBar = progressBar;
    }

    private void setSecondaryTextView(TextView secondaryTextView) {
        mSecondaryTextView = secondaryTextView;
    }

    public void setMax(int max) {
        mProgressBar.setMax(max);
    }

    public void updateProgress(int percent) {
        mProgressBar.setProgress(percent);
    }

    public void updateMessage(String msg) {
        setMessage(msg);
    }

    public void updateSecondaryTextView(String msg) {
        mSecondaryTextView.setText(msg);
    }

    public void setIndeterminate(boolean indeterminate) {
        mProgressBar.setIndeterminate(indeterminate);
    }
}
