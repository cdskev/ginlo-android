// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.view;

import android.app.Dialog;
import android.content.DialogInterface;
import androidx.annotation.Nullable;
import eu.ginlo_apps.ginlo.BaseActivity;

import java.lang.ref.WeakReference;

/**
 * Created by Florian on 17.12.15.
 */
public class AlertDialogWrapper {
    private final Dialog mDialog;
    private WeakReference<BaseActivity> mActivityWeakRef;

    /**
     * AlertDialogWrapper
     *
     * @param dialog
     * @param activity
     */
    public AlertDialogWrapper(Dialog dialog, BaseActivity activity) {
        mDialog = dialog;

        if (activity != null) {
            mActivityWeakRef = new WeakReference<>(activity);
        }
    }

    /**
     * show
     */
    public void show() {
        if (mDialog != null) {
            if (mActivityWeakRef != null && mActivityWeakRef.get() != null
                    && !mActivityWeakRef.get().isFinishing() && mActivityWeakRef.get().isActivityInForeground()) {
                mDialog.show();
            }
        }
    }

    /**
     * setOnDismissListener
     *
     * @param listener
     */
    public void setOnDismissListener(final DialogInterface.OnDismissListener listener) {
        if (mDialog != null) {
            mDialog.setOnDismissListener(listener);
        }
    }

    /**
     * setMessage
     *
     * @param charSequence
     */
    public void setMessage(CharSequence charSequence) {
        if (mDialog != null) {
            if (mDialog instanceof androidx.appcompat.app.AlertDialog) {
                ((androidx.appcompat.app.AlertDialog) mDialog).setMessage(charSequence);
            }
//         else if (mDialog instanceof AlertDialog)
//         {
//            ((AlertDialog) mDialog).setMessage(charSequence);
//         }
        }
    }

    /**
     * getDialog
     *
     * @return
     */
    @Nullable
    public Dialog getDialog() {
        return mDialog;
    }
}
