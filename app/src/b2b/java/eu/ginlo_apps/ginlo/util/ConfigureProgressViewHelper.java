// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.util;

import android.app.Activity;
import androidx.annotation.NonNull;

import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.controller.contracts.ConfigureCompanyAccountListener;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.constant.AppConstants;
import eu.ginlo_apps.ginlo.view.ProgressDownloadDialog;

public class ConfigureProgressViewHelper implements
        ConfigureCompanyAccountListener
{
   private static final String TAG = "ConfigureProgressViewHelper";

   final private Activity mActivity;
   final private ConfigureProgressListener mListener;
   private ProgressDownloadDialog mProgressDialog;

   public interface ConfigureProgressListener
   {
      void onFinish();
      void onError(String errorMsg, String detailErrorMsg);
   }

   public ConfigureProgressViewHelper(@NonNull final Activity activity, @NonNull final ConfigureProgressListener listener)
   {
      mActivity = activity;
      mListener = listener;
   }

   /**
    * onConfigureStateChanged
    *
    * @param state new state
    */
   @Override
   public void onConfigureStateChanged(int state)
   {
      switch (state)
      {
         case AppConstants.CONFIGURE_COMPANY_STATE_STARTED:
         {
            updateProgress("", "", true, -1, -1);
            break;
         }
         case AppConstants.CONFIGURE_COMPANY_STATE_CONNECTING:
         {
            String text = mActivity.getString(R.string.mdm_register_2);
            updateProgress(text, "", true, -1, -1);
            break;
         }
         case AppConstants.CONFIGURE_COMPANY_STATE_GET_CONFIG:
         {
            String text = mActivity.getString(R.string.mdm_register_3);
            updateProgress(text, "", true, -1, -1);
            break;
         }
         case AppConstants.CONFIGURE_COMPANY_STATE_GET_COMPANY_INDEX_START:
         {
            String text = mActivity.getString(R.string.mdm_register_4);
            updateProgress(text, "", true, -1, -1);
            break;
         }
         case AppConstants.CONFIGURE_COMPANY_STATE_GET_DOMAIN_INDEX_START:
         {
            String text = mActivity.getString(R.string.mdm_register_5);
            updateProgress(text, "", true, -1, -1);
            break;
         }
         case AppConstants.CONFIGURE_COMPANY_STATE_FINISHED:
         {
            dismissProgress();
            mListener.onFinish();
            break;
         }
      }
   }

   /**
    * onConfigureStateUpdate
    *
    * @param state   state
    * @param current current
    * @param size    size
    */
   @Override
   public void onConfigureStateUpdate(int state, int current, int size)
   {
      switch (state)
      {
         case AppConstants.CONFIGURE_COMPANY_STATE_GET_COMPANY_INDEX_SIZE:
         {
            String text = mActivity.getString(R.string.mdm_register_4);
            updateProgress(text, current + " " + mActivity.getString(R.string.backup_restore_progress_secondary) + " " + size, true, size, current);
            break;
         }
         case AppConstants.CONFIGURE_COMPANY_STATE_GET_COMPANY_INDEX_UPDATE:
         {
            String text = mActivity.getString(R.string.mdm_register_4);
            updateProgress(text, current + " " + mActivity.getString(R.string.backup_restore_progress_secondary) + " " + size, false, size, current);
            break;
         }
         case AppConstants.CONFIGURE_COMPANY_STATE_GET_DOMAIN_INDEX_SIZE:
         {
            String text = mActivity.getString(R.string.mdm_register_5);
            updateProgress(text, current + " " + mActivity.getString(R.string.backup_restore_progress_secondary) + " " + size, true, size, current);
            break;
         }
         case AppConstants.CONFIGURE_COMPANY_STATE_GET_DOMAIN_INDEX_UPDATE:
         {
            String text = mActivity.getString(R.string.mdm_register_5);
            updateProgress(text, current + " " + mActivity.getString(R.string.backup_restore_progress_secondary) + " " + size, false, size, current);
            break;
         }
      }
   }

   /**
    * onConfigureFailed
    *  @param message         message
    * @param errorIdentifier errorIdentifier
    * @param lastState last state
    */
   @Override
   public void onConfigureFailed(String message, String errorIdentifier, int lastState)
   {
      dismissProgress();
      String errorText;

      switch (lastState)
      {
         case AppConstants.CONFIGURE_COMPANY_STATE_CONNECTING:
         {
            errorText = mActivity.getString(R.string.mdm_login_company_encryption_not_loaded);
            break;
         }
         case AppConstants.CONFIGURE_COMPANY_STATE_GET_CONFIG:
         case AppConstants.CONFIGURE_COMPANY_STATE_GET_COMPANY_INDEX_START:
         case AppConstants.CONFIGURE_COMPANY_STATE_GET_COMPANY_INDEX_SIZE:
         case AppConstants.CONFIGURE_COMPANY_STATE_GET_COMPANY_INDEX_UPDATE:
         case AppConstants.CONFIGURE_COMPANY_STATE_GET_DOMAIN_INDEX_START:
         case AppConstants.CONFIGURE_COMPANY_STATE_GET_DOMAIN_INDEX_SIZE:
         case AppConstants.CONFIGURE_COMPANY_STATE_GET_DOMAIN_INDEX_UPDATE:
         case AppConstants.CONFIGURE_COMPANY_STATE_STARTED:
         default:
         {
            errorText = mActivity.getString(R.string.action_failed);
         }
      }

      String errorDetail = !StringUtil.isNullOrEmpty(message) ? (message + " ") : "";

      errorDetail = errorDetail + (!StringUtil.isNullOrEmpty(errorIdentifier) ? ("(" + errorIdentifier + ")") : "");
      mListener.onError(errorText, errorDetail);
   }

   private void updateProgress(@NonNull final String firstText, final String secondaryText, boolean indeterminate, int max, int current)
   {
      boolean show = false;

      if (mProgressDialog == null)
      {
         mProgressDialog = ProgressDownloadDialog.buildProgressDownloadDialog(mActivity);

         show = true;
      }

      mProgressDialog.setIndeterminate(indeterminate);
      mProgressDialog.updateMessage(firstText);
      if (!indeterminate)
      {
         mProgressDialog.setMax(max);
         mProgressDialog.updateProgress(current);
      }

      mProgressDialog.updateSecondaryTextView(secondaryText);

      if (show)
      {
         mProgressDialog.show();
      }
   }

   private void dismissProgress()
   {
      try
      {
         if (mProgressDialog != null)
         {
            mProgressDialog.dismiss();
            mProgressDialog = null;
         }
      }
      catch (IllegalArgumentException e)
      {
         LogUtil.w(TAG, "dismissProgress", e);
         mProgressDialog = null;
      }
   }
}
