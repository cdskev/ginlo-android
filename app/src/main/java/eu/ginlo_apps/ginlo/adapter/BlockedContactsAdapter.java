// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.adapter;

import android.content.Context;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import eu.ginlo_apps.ginlo.BlockedContactsActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.util.ContactUtil;
import eu.ginlo_apps.ginlo.log.LogUtil;

import java.util.ArrayList;

/**
 * @author Florian
 * @version $Revision$, $Date$, $Author$
 */
public class BlockedContactsAdapter
        extends ArrayAdapter<Contact> {

    private final BlockedContactsActivity mContext;

    public BlockedContactsAdapter(Context context,
                                  int resource,
                                  ArrayList<Contact> items) {
        super(context, resource, items);

        mContext = (BlockedContactsActivity) context;
    }

    @Override
    public View getView(int position,
                        View view,
                        ViewGroup parent) {
        View itemView = view;

        if (view == null) {
            LayoutInflater inflater = mContext.getLayoutInflater();

            itemView = inflater.inflate(R.layout.blocked_contact_item_layout, null);
        }

        if (position >= this.getCount()) {
            return itemView;
        }

        try {
            final Contact contact = getItem(position);
            if (contact != null) {

                TextView nameTextView = itemView.findViewById(R.id.blocked_contact_item_text_view_name);
                TextView unblockTextView = itemView.findViewById(R.id.blocked_contact_item_text_view_unblock);

                unblockTextView.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        mContext.handleUnblockClick(contact);
                    }
                });

                String fatHtmlText = ContactUtil.getDisplayText(contact);

                Spanned textHtml = Html.fromHtml(fatHtmlText);

                nameTextView.setText(textHtml);
            }
        } catch (final LocalizedException e) {
            LogUtil.w(this.getClass().getName(), e.getMessage(), e);
        }
        return itemView;
    }
}
