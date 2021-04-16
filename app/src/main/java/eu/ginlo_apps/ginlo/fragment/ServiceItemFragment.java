// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment;

import android.app.Activity;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.model.backend.ServiceListModel;

/**
 * Created by SGA on 26.01.2017.
 */

public class ServiceItemFragment
        extends Fragment {
    private ServiceListModel mModel;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        LinearLayout root = (LinearLayout) inflater.inflate(R.layout.fragment_service_list_item, container,
                false);

        final Activity activity = getActivity();

        if (mModel != null && activity != null) {

            final String serviceID = mModel.serviceId;
            final int imageResourceId = activity.getResources().getIdentifier(serviceID + "_bg", "drawable", activity.getPackageName());
            final int titleId = activity.getResources().getIdentifier(serviceID + "_title", "string", activity.getPackageName());
            final int textId = activity.getResources().getIdentifier(serviceID + "_text", "string", activity.getPackageName());
            final int hintId = activity.getResources().getIdentifier(serviceID + "_hint", "string", activity.getPackageName());

            ImageView imageView = root.findViewById(R.id.service_list_item_image);
            TextView header = root.findViewById(R.id.service_list_item_text_header);
            TextView text = root.findViewById(R.id.service_list_item_text);
            TextView hint = root.findViewById(R.id.service_list_item_hint);

            imageView.setImageResource(imageResourceId);
            header.setText(activity.getResources().getString(titleId));
            text.setText(activity.getResources().getString(textId));
            hint.setText(activity.getResources().getString(hintId));
        }

        return root;
    }

    public ServiceListModel getModel() {
        return mModel;
    }

    public void setModel(ServiceListModel model) {
        mModel = model;
    }
}
