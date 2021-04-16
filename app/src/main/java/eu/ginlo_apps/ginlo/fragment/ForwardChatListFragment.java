// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.ViewExtensionsKt;
import eu.ginlo_apps.ginlo.activity.chatsOverview.ChatsAdapter;
import eu.ginlo_apps.ginlo.controller.ChatOverviewController;
import eu.ginlo_apps.ginlo.util.ImageLoader;

public class ForwardChatListFragment
        extends Fragment {
    private RecyclerView recyclerView;

    private ChatsAdapter chatsAdapter;

    private ChatOverviewController mChatOverviewController;

    private int mMode;

    private TextView mNoChatsFoundTextView;

    private String mNoChatsFoundMessage;

    public void init(final ChatOverviewController chatOverviewController, final ChatsAdapter chatsAdapter, int mode, final String noChatsFoundMessage) {
        mChatOverviewController = chatOverviewController;
        this.chatsAdapter = chatsAdapter;
        mMode = mode;
        mNoChatsFoundMessage = noChatsFoundMessage;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        LinearLayout root = (LinearLayout) ViewExtensionsKt.themedInflate(inflater, getContext(), R.layout.fragment_forward_chatlist, container,
                false);

        if (chatsAdapter != null) {
            mChatOverviewController.setAdapter(chatsAdapter);
            mChatOverviewController.setMode(mMode);
            initChatList(root);
        }

        mNoChatsFoundTextView = root.findViewById(R.id.forward_pager_chatlist_no_chats_textview);

        return root;
    }

    private void initChatList(final ViewGroup root) {
        recyclerView = root.findViewById(R.id.forward_list_view);
        recyclerView.setAdapter(chatsAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireActivity()));
    }

    public void refresh() {
        if (mChatOverviewController != null && recyclerView != null) {
            mChatOverviewController.setAdapter(chatsAdapter);
            mChatOverviewController.setMode(mMode);
            mChatOverviewController.filterMessages(false);
            if (chatsAdapter.getItemCount() != 0) {
                recyclerView.setVisibility(View.VISIBLE);
                chatsAdapter.notifyDataSetChanged();
                mNoChatsFoundTextView.setVisibility(View.GONE);
            } else {
                recyclerView.setVisibility(View.GONE);
                mNoChatsFoundTextView.setVisibility(View.VISIBLE);
                mNoChatsFoundTextView.setText(mNoChatsFoundMessage);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    public int getMode() {
        return mMode;
    }
}
