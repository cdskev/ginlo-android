// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment;

import android.content.Context;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.View;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.fragment.IOnFragmentViewClickable;

import java.util.List;

/**
 * Created by Florian on 21.06.16.
 */
public class BaseFragment extends Fragment implements IOnFragmentViewClickable {
    protected OnFragmentInteractionListener mListener;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnBackupRestoreFragmentInteractionListener");
        }
    }

    protected void handleAction(int action, Bundle arguments) {
        if (mListener != null) {
            mListener.onFragmentInteraction(action, arguments);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onFragmentViewClick(View view) {
        if (view != null) {
            List<Fragment> fragments = getChildFragments();
            if (fragments != null) {
                for (Fragment fragment : fragments) {
                    if ((fragment instanceof IOnFragmentViewClickable)) {
                        ((IOnFragmentViewClickable) fragment).onFragmentViewClick(view);
                    }
                }
            }
        }
    }

    protected List<Fragment> getChildFragments() {
        return null;
    }

    SimsMeApplication getSimsmeApplication()
            throws LocalizedException {
        Context context = getContext();
        if (context == null) {
            throw new LocalizedException(LocalizedException.NO_CONTEXT_AVAILABLE);
        }

        Context app = context.getApplicationContext();
        if (!(app instanceof SimsMeApplication)) {
            throw new LocalizedException(LocalizedException.NO_CONTEXT_AVAILABLE);
        }

        return (SimsMeApplication) app;
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     */
    public interface OnFragmentInteractionListener {
        void onFragmentInteraction(int action, Bundle arguments);
    }
}
