// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util;

import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatCheckBox;
import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;
import eu.ginlo_apps.ginlo.view.ProgressDialog;

public class DialogBuilderUtil {
    public DialogBuilderUtil() {
    }

    public static AlertDialogWrapper buildErrorDialog(BaseActivity context,
                                                      String errorMessage) {
        return buildErrorDialog(context, errorMessage, 0, null);
    }

    public static AlertDialogWrapper buildErrorDialog(final BaseActivity context,
                                                      final String errorMessage,
                                                      final int ref,
                                                      final OnCloseListener onCloseListener) {
        if (context == null || context.isFinishing() || !context.isActivityInForeground()) {
            return new AlertDialogWrapper(null, context);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context,
                ColorUtil.getInstance().getAlertDialogStyle(context.getApplication()));

        builder.setMessage(errorMessage);

        final String buttonText = context.getResources().getString(android.R.string.ok);
        builder.setPositiveButton(buttonText, null);

        final AlertDialog dialog = builder.create();

        colorizeButtons(context, dialog);

        if (onCloseListener != null) {

            OnDismissListener dismissListener = new OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    onCloseListener.onClose(ref);
                }
            };
            dialog.setOnDismissListener(dismissListener);
        }

        return new AlertDialogWrapper(dialog, context);
    }

    public static AlertDialogWrapper buildErrorDialog(final BaseActivity context,
                                                      final String errorMessage,
                                                      final String title,
                                                      final String buttonTitle,
                                                      final OnClickListener onCloseListener) {
        if (context == null || context.isFinishing() || !context.isActivityInForeground()) {
            return new AlertDialogWrapper(null, context);
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(errorMessage);
        builder.setPositiveButton(buttonTitle, onCloseListener);

        final AlertDialog dialog = builder.create();

        colorizeButtons(context, dialog);

        return new AlertDialogWrapper(dialog, context);
    }

    public static ProgressDialog buildProgressDialog(final Context context,
                                                     final int resid) {
        ProgressDialog dialog = new ProgressDialog(context, R.style.Theme_AppCompat_Light_Dialog);

        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_idle_loader_layout, null, false);

        TextView tv = view.findViewById(R.id.progressText);
        dialog.setTitleTextView(tv);

        if (resid > -1) {
            tv.setText(resid);
        } else {
            tv.setVisibility(View.GONE);
        }

        dialog.setContentView(view);

        return dialog;
    }

    public static AlertDialogWrapper buildResponseDialog(BaseActivity context,
                                                         String message,
                                                         String title,
                                                         OnClickListener positiveOnClickListener,
                                                         OnClickListener negativeOnClickListener) {
        String positiveButton = context.getResources().getString(R.string.general_yes);
        String negativeButton = context.getResources().getString(R.string.general_no);

        return buildResponseDialog(context, message, title, positiveButton, negativeButton, positiveOnClickListener,
                negativeOnClickListener);
    }

    public static AlertDialogWrapper buildResponseDialog(BaseActivity context,
                                                         String message,
                                                         String title,
                                                         String positiveButton,
                                                         String negativeButton,
                                                         OnClickListener positiveOnClickListener,
                                                         OnClickListener negativeOnClickListener) {
        return buildResponseDialog(context, message, false, title, positiveButton, negativeButton, positiveOnClickListener, negativeOnClickListener);
    }

    public static AlertDialogWrapper buildResponseDialog(BaseActivity context,
                                                         String message,
                                                         boolean formatMessage,
                                                         String title,
                                                         String positiveButton,
                                                         String negativeButton,
                                                         OnClickListener positiveOnClickListener,
                                                         OnClickListener negativeOnClickListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        if (formatMessage) {
            builder.setMessage(Html.fromHtml(message));
        } else {
            builder.setMessage(message);
        }
        if (title != null) {
            builder.setTitle(title);
        }

        if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(positiveButton)) {
            builder.setPositiveButton(positiveButton, positiveOnClickListener);
        }

        if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(negativeButton)) {
            builder.setNegativeButton(negativeButton, negativeOnClickListener);
        }

        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        colorizeButtons(context, dialog);
        return new AlertDialogWrapper(dialog, context);
    }

    public static AlertDialogWrapper buildResponseDialogV7(BaseActivity activity,
                                                           String message,
                                                           String title,
                                                           String positiveButton,
                                                           String negativeButton,
                                                           OnClickListener positiveOnClickListener,
                                                           OnClickListener negativeOnClickListener) {
        return buildResponseDialogWithDontShowAgain(activity, message, title, positiveButton,
                negativeButton, positiveOnClickListener, negativeOnClickListener, null);
    }

    public static AlertDialogWrapper buildResponseDialogWithDontShowAgain(final BaseActivity activity,
                                                                          String message,
                                                                          String title,
                                                                          String positiveButton,
                                                                          String negativeButton,
                                                                          OnClickListener positiveOnClickListener,
                                                                          OnClickListener negativeOnClickListener,
                                                                          final DoNotShowAgainChoiceListener doNotShowAgainChoiceListner) {

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);

        builder.setMessage(message);
        if (title != null) {
            builder.setTitle(title);
        }

        if (doNotShowAgainChoiceListner != null) {
            View checkboxItemView = activity.getLayoutInflater().inflate(R.layout.item_checkbox_text, null);
            if (checkboxItemView != null) {
                AppCompatCheckBox checkbox = checkboxItemView.findViewById(R.id.item_checkbox_text);

                if (checkbox != null) {
                    //Farbe der Checkbox setzen geht leider nur unter Marshmallow+
                    if (SystemUtil.hasMarshmallow() && eu.ginlo_apps.ginlo.util.RuntimeConfig.isBAMandant()) {
                        final Drawable d = checkbox.getButtonDrawable();
                        if (d != null) {
                            final ColorUtil colorUtil = ColorUtil.getInstance();
                            final int accentColor = colorUtil.getAppAccentColor(activity.getSimsMeApplication());
                            d.setColorFilter(accentColor, PorterDuff.Mode.SRC_ATOP);
                        }
                    }
                    checkbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            doNotShowAgainChoiceListner.doNotShowAgainClicked(isChecked);
                        }
                    });
                    builder.setView(checkboxItemView);
                }
            }
        }
        if (!eu.ginlo_apps.ginlo.util.StringUtil.isNullOrEmpty(positiveButton)) {
            builder.setPositiveButton(positiveButton, positiveOnClickListener);
        }

        if (!StringUtil.isNullOrEmpty(negativeButton)) {
            builder.setNegativeButton(negativeButton, negativeOnClickListener);
        }

        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        colorizeButtons(activity, dialog);
        return new AlertDialogWrapper(dialog, activity);
    }

    public static void colorizeButtons(final Context context, final AlertDialog dialog) {
        if (RuntimeConfig.isBAMandant()) {
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface d) {
                    final ColorUtil colorUtil = ColorUtil.getInstance();

                    int appAccentColor = colorUtil.getAppAccentColor((Application) context.getApplicationContext());
                    if (appAccentColor == -1) {
                        appAccentColor = colorUtil.getAppAccentContrastColor((Application) context.getApplicationContext());
                    }

                    final Button button1 = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    if (button1 != null) {
                        button1.setTextColor(appAccentColor);
                    }
                    final Button button2 = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                    if (button2 != null) {
                        button2.setTextColor(appAccentColor);
                    }
                    final Button button3 = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                    if (button3 != null) {
                        button3.setTextColor(appAccentColor);
                    }
                }
            });
        }
    }

    public interface OnCloseListener {
        void onClose(int ref);
    }

    public interface DoNotShowAgainChoiceListener {
        void doNotShowAgainClicked(boolean isChecked);
    }
}