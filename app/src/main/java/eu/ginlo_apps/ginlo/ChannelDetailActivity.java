// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import dagger.android.AndroidInjection;
import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.activity.chat.ChannelChatActivity;
import eu.ginlo_apps.ginlo.adapter.ChannelToogleAdapter;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.ChannelController;
import eu.ginlo_apps.ginlo.controller.ChannelController.ChannelAsyncLoaderCallback;
import eu.ginlo_apps.ginlo.controller.ChannelController.ChannelIdentifier;
import eu.ginlo_apps.ginlo.controller.ChatImageController;
import eu.ginlo_apps.ginlo.controller.ChatOverviewController;
import eu.ginlo_apps.ginlo.controller.message.contracts.OnSendMessageListener;
import eu.ginlo_apps.ginlo.data.network.AppConnectivity;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Channel;
import eu.ginlo_apps.ginlo.greendao.Chat;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.backend.ChannelListModel;
import eu.ginlo_apps.ginlo.model.backend.ChannelModel;
import eu.ginlo_apps.ginlo.model.backend.ServiceModel;
import eu.ginlo_apps.ginlo.model.backend.ToggleModel;
import eu.ginlo_apps.ginlo.model.backend.ToggleSettingsModel;
import eu.ginlo_apps.ginlo.model.backend.serialization.ToggleSettingsModelDeserializer;
import eu.ginlo_apps.ginlo.util.ChannelColorUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil.OnCloseListener;
import eu.ginlo_apps.ginlo.util.ImageLoader;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.ToolbarColorizeHelper;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;
import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;

/**
 * @author Florian
 * @version $Id$
 */
public class ChannelDetailActivity
        extends BaseActivity {

    public static final String CHANNEL_GUID = "CHANNEL_GUID";

    public static final String CHANNEL_NEW_CHECKSUM = "CHANNEL_NEW_CHECKSUM";

    //category of channel_list
    public static final String LIST_CATEGORY = "LIST_CATEGORY";

    public static final String EXTRA_FINISH_TO_OVERVIEW = "finishToOverview";

    protected ImageView mHeaderBgImageView;
    protected TextView mHeaderLabelTextView;
    protected Button mSubscribeButton;
    protected ChannelController mChannelController;
    protected ChannelToogleAdapter mAdapter;
    protected String mChannelGuid;
    protected Channel mChannel;
    private ImageView mChannelbackground;
    private View mHeader;
    private ImageView mHeaderLabelImageView;
    private ImageLoader mImageLoader;
    private String mNewChecksum;
    private long mChannelId;
    private String mListCategory;
    private boolean mFinishtoOverview = false;
    private boolean mDescTextViewIsExpand;
    private boolean mDisclaimerTextViewIsExpand;
    private CompoundButton.OnCheckedChangeListener mOnCheckedChangeListener;
    private boolean mDisableNoToggleDialog;
    private ChannelModel mChannelModel;

    @Inject
    public AppConnectivity appConnectivity;

    @Override
    protected void onCreateActivity(Bundle savedInstanceState) {
        try {
            if (mChannelGuid == null) {
                mChannelGuid = getIntent().getStringExtra(CHANNEL_GUID);
            }
            mNewChecksum = getIntent().getStringExtra(CHANNEL_NEW_CHECKSUM);

            if (getIntent().hasExtra("LIST_CATEGORY")) {
                mListCategory = getIntent().getStringExtra("LIST_CATEGORY").replace("\\", "").replace("\"", "");
            }

            if (getIntent().getBooleanExtra(EXTRA_FINISH_TO_OVERVIEW, false)) {
                mFinishtoOverview = true;
            }

            if (mChannelGuid == null) {
                finish();
                return;
            }

            mChannelbackground = findViewById(R.id.channel_detail_bg);
            mChannelbackground.setVisibility(View.GONE);

            mHeader = findViewById(R.id.channel_detail_header);

            // im Header Imageviews ausblenden, bis Images geladen wurden
            mHeaderBgImageView = mHeader.findViewById(R.id.channel_item_background);
            mHeaderLabelImageView = mHeader.findViewById(R.id.channel_item_label);
            mHeaderLabelTextView = mHeader.findViewById(R.id.channel_text_label);
            mHeaderBgImageView.setVisibility(View.GONE);
            mHeaderLabelImageView.setVisibility(View.GONE);

            mSubscribeButton = findViewById(R.id.channel_detail_subsrcibe_button);
            mSubscribeButton.setVisibility(View.GONE);
            //mSubscribeButton.setEnabled(false);

            mChannelController = ((SimsMeApplication) this.getApplication()).getChannelController();

            mImageLoader = initImageLoader(mChannelController);

            if (mChannel == null) {
                mChannel = mChannelController.getChannelFromDB(mChannelGuid);
            }

            mChannelId = mChannel.getId();

            fillViews(mChannel);
        } catch (LocalizedException e) {
            LogUtil.e(this.getClass().getName(), "onCreateActivity()", e);
            finish();
        }
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_channel_details;
    }

    @Override
    protected void onResumeActivity() {
    }

    @Override
    protected void onPauseActivity() {
        beforePause();
        super.onPauseActivity();
    }

    protected void beforePause() {
        if (!appConnectivity.isConnected()) {
            Toast.makeText(this, R.string.backendservice_internet_connectionFailed,
                    Toast.LENGTH_LONG).show();
            return;
        }
        final Channel channel = mChannelController.getChannelForId(mChannelId);

        if (channel.getIsSubscribedSave()) {
            if (mAdapter != null) {
                subscribeChannel(null);
            }
        }
    }

    private ImageLoader initImageLoader(final ChannelController channelController) {
        final ImageLoader imageLoader = new ImageLoader(this, ChatImageController.SIZE_CHAT_OVERVIEW, true) {
            @Override
            protected Bitmap processBitmap(final Object data) {
                try {
                    final ChannelIdentifier ci = (ChannelIdentifier) data;

                    return channelController.loadImage(ci.getClModel(), ci.getType());
                } catch (LocalizedException e) {
                    LogUtil.w(ChannelDetailActivity.this.getClass().getName(), "Image can't be loaded.", e);
                    return null;
                }
            }

            @Override
            protected void processBitmapFinished(final Object data, ImageView imageView) {
                final ChannelIdentifier ci = (ChannelIdentifier) data;
                //TODO FIXME
                if (ChannelController.IMAGE_TYPE_CHANNEL_BACKGROUND.equals(ci.getType()) && mChannelbackground != null) {
                    mChannelbackground.setVisibility(View.VISIBLE);
                } else if (ChannelController.IMAGE_TYPE_ITEM_BACKGROUND.equals(ci.getType()) && mHeaderBgImageView != null && mHeaderLabelTextView != null) {
                    mHeaderBgImageView.setVisibility(View.VISIBLE);

                    mHeaderLabelTextView.setVisibility(View.GONE);
                } else if (ChannelController.IMAGE_TYPE_PROVIDER_LABEL.equals(ci.getType()) && mHeaderLabelImageView != null && mHeaderLabelTextView != null) {
                    mHeaderLabelImageView.setVisibility(View.VISIBLE);

                    mHeaderLabelTextView.setVisibility(View.GONE);
                }
            }
        };

        imageLoader.setImageFadeIn(false);

        return imageLoader;
    }

    @Override
    protected void colorizeActivity() {
        if (mChannelModel != null) {
            final ChannelColorUtil ccu = new ChannelColorUtil(mChannelModel.layout, this);
            ToolbarColorizeHelper.colorizeToolbar(getToolbar(), ccu.getHeadColor(), ccu.getHeadBkColor(), this);
            mSubscribeButton.setTextColor(ccu.getHeadColor());
            mSubscribeButton.setBackgroundColor(ccu.getHeadBkColor());
        }
    }

    /**
     * @throws LocalizedException [!EXC_DESCRIPTION!]
     */
    private void fillViews(final Channel channel)
            throws LocalizedException {
        if (channel == null) {
            throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Channel Dao Model is null!");
        }

        mChannelModel = mChannelController.getChannelModel(channel);

        if (mChannelModel == null) {
            throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "Channel Model is null!");
        }

        setTitle(mChannelModel.shortDesc);

        final LinearLayout disclaimerRoot = (LinearLayout) getLayoutInflater().inflate(R.layout.channel_detail_footer, null);

        final TextView disclaimerHeaderTextView = disclaimerRoot.findViewById(R.id.channel_disclaimer_header);

        final TextView disclaimerTextView = disclaimerRoot.findViewById(R.id.channel_disclaimer_text);

        final ImageView arrowDisclaimerImgView = disclaimerRoot.findViewById(R.id.channel_disclaimer_arrow);

        final View labelAndDescriptionView = getLayoutInflater().inflate(R.layout.channel_description_text_view,
                null);
        final TextView descView = labelAndDescriptionView.findViewById(R.id.channel_detail_description_text);

        final ImageView arrowImgView = labelAndDescriptionView.findViewById(R.id.channel_detail_description_arrow);

        final TextView chooseToggleLabel = labelAndDescriptionView.findViewById(R.id.channel_detail_choose_toggle);

        descView.setText(mChannelModel.desc);

        final ListView listView = findViewById(R.id.channel_detail_toogle_list);

        listView.addHeaderView(labelAndDescriptionView);
        listView.addFooterView(disclaimerRoot);
        if (StringUtil.isEqual(Channel.TYPE_SERVICE, channel.getType())) {
            ServiceModel serviceModel = (ServiceModel) mChannelController.getChannelModel(channel);
            if (serviceModel != null) {
                final String serviceID = serviceModel.serviceId;
                if (!StringUtil.isNullOrEmpty(serviceID)) {
                    final int disclaimerId = getResources().getIdentifier(serviceID + "_disclaimer", "string", getPackageName());
                    disclaimerTextView.setText(getResources().getString(disclaimerId));

                    final int disclaimerHeaderId = getResources().getIdentifier(serviceID + "_disclaimer_header", "string", getPackageName());
                    disclaimerHeaderTextView.setText(getResources().getString(disclaimerHeaderId));

                    final int toggleHeaderId = getResources().getIdentifier(serviceID + "_details_section_toggles_title", "string", getPackageName());
                    chooseToggleLabel.setText(getResources().getString(toggleHeaderId));
                }
            }
        }

        mOnCheckedChangeListener = createCheckedChangedListener(channel);

        if ((mChannelModel.toggles != null) && (mChannelModel.toggles.length > 0)) {
            arrowImgView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mDescTextViewIsExpand) {
                        descView.setMaxLines(getResources().getInteger(R.integer.channel_desc_collapse_lines));
                        arrowImgView.setImageResource(R.drawable.chanel_info_open);
                        mDescTextViewIsExpand = false;
                        arrowImgView.setContentDescription(getResources().getString(R.string.content_description_channeldetails_less));
                    } else {
                        descView.setMaxLines(Integer.MAX_VALUE);
                        arrowImgView.setImageResource(R.drawable.chanel_info_close);
                        mDescTextViewIsExpand = true;
                        arrowImgView.setContentDescription(getResources().getString(R.string.content_description_channeldetails_less));
                    }
                }
            });

            arrowDisclaimerImgView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mDisclaimerTextViewIsExpand) {
                        disclaimerTextView.setMaxLines(getResources().getInteger(R.integer.channel_desc_collapse_lines));
                        arrowDisclaimerImgView.setImageResource(R.drawable.chanel_info_open);
                        mDisclaimerTextViewIsExpand = false;
                        arrowDisclaimerImgView.setContentDescription(getResources().getString(R.string.content_description_channeldetails_less));
                    } else {
                        disclaimerTextView.setMaxLines(Integer.MAX_VALUE);
                        arrowDisclaimerImgView.setImageResource(R.drawable.chanel_info_close);
                        mDisclaimerTextViewIsExpand = true;
                        arrowDisclaimerImgView.setContentDescription(getResources().getString(R.string.content_description_channeldetails_less));
                    }
                }
            });

            if (channel.getIsSubscribedSave() && (channel.getFilterJsonObject() != null)) {
                final GsonBuilder gsonBuilder = new GsonBuilder();

                gsonBuilder.registerTypeAdapter(ToggleSettingsModel.class, new ToggleSettingsModelDeserializer());

                Gson gson = gsonBuilder.create();

                Type stringToggleSettingsMap = new TypeToken<Map<String, ToggleSettingsModel>>() {
                }
                        .getType();
                Map<String, ToggleSettingsModel> map = gson.fromJson(channel.getFilterJsonObject(),
                        stringToggleSettingsMap);

                mAdapter = new ChannelToogleAdapter(this, mChannelModel.toggles, new HashMap<>(map),
                        mOnCheckedChangeListener, channel.getIsDeleted());
            } else {
                if (mListCategory != null) {
                    addPromoToggles(mChannelModel);
                }
                mAdapter = new ChannelToogleAdapter(this, mChannelModel.toggles, null, mOnCheckedChangeListener, channel.getIsDeleted());
            }
        } else {
            chooseToggleLabel.setVisibility(View.GONE);
            arrowImgView.setVisibility(View.GONE);
            descView.setMaxLines(Integer.MAX_VALUE);

            mAdapter = new ChannelToogleAdapter(this, new ToggleModel[]{}, null, mOnCheckedChangeListener, channel.getIsDeleted());
        }

        listView.setAdapter(mAdapter);

        mHeaderLabelTextView.setText(mChannelModel.shortDesc);

        if (!channel.getIsSubscribedSave()) {
            mSubscribeButton.setVisibility(View.VISIBLE);
            mSubscribeButton.setText(R.string.channel_subscribe_button);
        }

        final ChannelListModel clModel = new ChannelListModel();

        clModel.guid = channel.getGuid();
        clModel.checksum = channel.getChecksum();
        clModel.localChecksum = (mNewChecksum != null) ? mNewChecksum : channel.getChecksum();

        //Farbwerte setzen
        if (mChannelModel.layout != null) {
            ChannelColorUtil ccu = new ChannelColorUtil(mChannelModel.layout, this);

            ToolbarColorizeHelper.colorizeToolbar(getToolbar(), ccu.getHeadColor(), ccu.getHeadBkColor(), this);

            int textColor = ccu.getColorText();

            descView.setTextColor(textColor);
            disclaimerTextView.setTextColor(textColor);

            RelativeLayout root = findViewById(R.id.channel_details_layout);
            root.setContentDescription(mChannelModel.shortDesc);

            TextView label = findViewById(R.id.channel_detail_description_label);

            label.setTextColor(ccu.getColorLabelEnable());

            chooseToggleLabel.setTextColor(ccu.getColorLabelEnable());
            disclaimerHeaderTextView.setTextColor(ccu.getColorLabelEnable());

            if (mAdapter != null) {
                mAdapter.setChannelLayout(mChannelModel.layout);
            }
            if (!(mChannelModel instanceof ServiceModel) && ccu.getIbColor() != 0 && mHeaderBgImageView != null) {

                mHeaderBgImageView.setBackgroundColor(ccu.getIbColor());
                mHeaderBgImageView.setVisibility(View.VISIBLE);
            } else {
                final ChannelIdentifier headerBgIdentifier = new ChannelIdentifier(clModel,
                        ChannelController.IMAGE_TYPE_ITEM_BACKGROUND);
                mImageLoader.loadImage(headerBgIdentifier, mHeaderBgImageView);
            }

            if (ccu.getCbColor() != 0 && mChannelbackground != null) {
                mChannelbackground.setImageDrawable(null);
                mChannelbackground.setBackgroundColor(ccu.getCbColor());
                mChannelbackground.setVisibility(View.VISIBLE);
            } else {
                final ChannelIdentifier bgIdentifier = new ChannelIdentifier(clModel,
                        ChannelController.IMAGE_TYPE_CHANNEL_BACKGROUND);

                mImageLoader.loadImage(bgIdentifier, mChannelbackground);
            }

            if (StringUtil.isEqual(Channel.TYPE_SERVICE, channel.getType())) {
                final PorterDuffColorFilter colorFilter = new PorterDuffColorFilter(ccu.getHeadColor(), PorterDuff.Mode.SRC_ATOP);

                mSubscribeButton.getBackground().setColorFilter(colorFilter);
                mSubscribeButton.setText(getResources().getString(R.string.service_details_buttonssubscribe));
            }

            mDisableNoToggleDialog = true;
            //einmal aufrufen, damit der Button am anfang gestezt wird
            mOnCheckedChangeListener.onCheckedChanged(null, false);
            mDisableNoToggleDialog = false;
        }

        final ChannelIdentifier headerLabelIdentifier = new ChannelIdentifier(clModel,
                ChannelController.IMAGE_TYPE_PROVIDER_LABEL);

        mImageLoader.loadImage(headerLabelIdentifier, mHeaderLabelImageView);

        final ChannelIdentifier iconIdentifier = new ChannelIdentifier(clModel,
                ChannelController.IMAGE_TYPE_PROVIDER_ICON);

        mImageLoader.loadImage(iconIdentifier, new ImageView(this));
    }

    /**
     * @param channel
     * @return
     */
    protected CompoundButton.OnCheckedChangeListener createCheckedChangedListener(final Channel channel) {
        return new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {
                final HashMap<String, ToggleSettingsModel> filterValues = mAdapter.getFilterValues();

                if ((filterValues == null) || (filterValues.size() == 0)) {
                    //keine toggles

                    mSubscribeButton.setEnabled(true);
                } else if (mChannelController.getSelectedTogglesFilter(filterValues).equals("")) {
                    if (!channel.getIsSubscribedSave()) {
                        mSubscribeButton.setEnabled(false);
                    } else {
                        if (!mDisableNoToggleDialog) {
//                     boolean showAlert = true;
//                     for (ToggleSettingsModel model : filterValues.values())
//                     {
//                        if (StringUtil.isEqual(model.value, "on"))
//                        {
//                           showAlert = true;
//                           break;
//                        }
//                     }
//                     if (showAlert)
                            {
                                final String errorText;
                                if (StringUtil.isEqual(Channel.TYPE_SERVICE, channel.getType())) {
                                    errorText = getString(R.string.service_details_alert_message_filter_is_empty);
                                } else {
                                    errorText = getString(R.string.channel_details_alert_message_filter_is_empty);
                                }

                                DialogBuilderUtil.buildResponseDialog(ChannelDetailActivity.this,
                                        errorText,
                                        getString(R.string.chat_file_open_warning_title), //fixme eigenen string machen, spaeter mal
                                        getString(R.string.std_ok),
                                        null,
                                        null,
                                        null
                                ).show();
                            }
                        }
                    }
                } else {
                    if (!channel.getIsSubscribedSave()) {
                        mSubscribeButton.setEnabled(true);
                    }
                }
            }
        };
    }

    private void addPromoToggles(final ChannelModel model) {
        // forceToggles fuer promo
        try {
            final JsonParser jsonParser = new JsonParser();

            final JsonElement jsonElement = jsonParser.parse(model.category);

            final JsonArray jArray = jsonElement.getAsJsonArray();

            if (jArray.size() != 0) {
                for (int i = 0; i < jArray.size(); ++i) {
                    JsonObject jObj = jArray.get(i).getAsJsonObject();

                    if (jObj.has("type")
                            && jObj.get("type").toString().replace("\\", "").replace("\"", "").equals("category")) {
                        if (jObj.has("ident")
                                && jObj.get("ident").toString().replace("\\", "").replace("\"", "").equals(mListCategory)) {
                            if (jObj.has("@children")) {
                                JsonArray jChildrenArray = jObj.get("@children").getAsJsonArray();

                                for (int j = 0; j < jChildrenArray.size(); ++j) {
                                    JsonObject children = jChildrenArray.get(j).getAsJsonObject();
                                    String ident = children.get("ident").toString().replace("\\", "").replace("\"", "");
                                    String value = children.get("value").toString().replace("\\", "").replace("\"", "");
                                    String type = children.get("type").toString().replace("\\", "").replace("\"", "");

                                    if (StringUtil.isEqual(type, "forceToggle")) {
                                        for (int k = 0; k < model.toggles.length; ++k) {
                                            ToggleModel toggleModel = model.toggles[k];

                                            if (toggleModel.ident.equals(ident)) {
                                                model.toggles[k].defaultValue = value;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (IllegalStateException e) {
            LogUtil.e(ChannelDetailActivity.this.getClass().getName(), "addPromoToggles(): Json parse error", e);
        }
    }

    public void handleSubscribeClick(View view) {
        final Channel channel = mChannelController.getChannelForId(mChannelId);

        if (!channel.getIsSubscribedSave()) {
            if (mAdapter != null) {
                //neuen Chat in die DB
                SimsMeApplication app = (SimsMeApplication) ChannelDetailActivity.this.getApplication();

                Chat chat = app.getChannelChatController().getChatByGuid(channel.getGuid());

                if (chat == null) {
                    chat = new Chat();
                }

                try {
                    //Neuen Chat
                    byte[] aesKeyBytes = Base64.decode(channel.getAesKey(), Base64.NO_WRAP);

                    chat.setChatGuid(channel.getGuid());
                    chat.setType(Chat.TYPE_CHANNEL);
                    chat.setChatAESKey(new SecretKeySpec(aesKeyBytes, 0, aesKeyBytes.length, "AES"));
                    chat.setLastChatModifiedDate(new Date().getTime());
                } catch (LocalizedException e) {
                    return;
                }

                //starte ProgressDialog
                showIdleDialog(R.string.channel_subscribe_waiting);

                subscribeChannel(chat);
            }
        } else {
            DialogInterface.OnClickListener positiveOnClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog,
                                    int which) {
                    //             dialog.dismiss();
                    unsubscribeFromChannel(channel);
                }
            };

            DialogInterface.OnClickListener negativeOnClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog,
                                    int which) {
                    //             dialog.dismiss();
                }
            };

            AlertDialogWrapper alert = DialogBuilderUtil.buildResponseDialog(ChannelDetailActivity.this,
                    getString(R.string.channel_leave_confirm),
                    getString(R.string.channel_subscribe_button_cancel),
                    positiveOnClickListener,
                    negativeOnClickListener);

            alert.show();
        }
    }

    private void unsubscribeFromChannel(final Channel channel) {
        final Dialog waitProgress = DialogBuilderUtil.buildProgressDialog(this,
                R.string.channel_cancel_subscribe_waiting);

        waitProgress.show();
        mChannelController.cancelChannelSubscription(channel.getGuid(), channel.getType(), new ChannelAsyncLoaderCallback<String>() {
            @Override
            public void asyncLoaderFinishedWithSuccess(String result) {
                channel.setIsSubscribed(false);
                mChannelController.updateChannel(channel);

                SimsMeApplication app = (
                        (SimsMeApplication)
                                ChannelDetailActivity.this
                                        .getApplication()
                );

                //Chat löschen
                app.getChannelChatController().deleteChat(channel.getGuid(), true, null);

                waitProgress.dismiss();

                Intent intent = new Intent(ChannelDetailActivity.this,
                        RuntimeConfig.getClassUtil().getChatOverviewActivityClass());

                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }

            @Override
            public void asyncLoaderFinishedWithError(String errorMessage) {
                waitProgress.dismiss();
                showErrorDialog(getString(R.string.channel_cancel_subscription_error),
                        false);
            }
        });
    }

    private void subscribeChannel(final Chat newChat) {
        final HashMap<String, ToggleSettingsModel> filterValues = mAdapter.getFilterValues();

        mChannelController.subscribeChannel(mChannelId, filterValues, mChannel.getType(), new ChannelController.SubscribeChannelListener() {
            @Override
            public void subscribeChannelFinished(final Channel channel) {
                if (newChat != null) {
                    //neuen Chat in die DB
                    SimsMeApplication app = (SimsMeApplication) ChannelDetailActivity.this.getApplication();

                    app.getChannelChatController().insertOrUpdateChat(newChat);

                    //Willkommensnachricht
                    String message;

                    if (!StringUtil.isNullOrEmpty(channel.getWelcomeText())) {
                        message = channel.getWelcomeText();
                    } else {
                        message = ChannelDetailActivity.this.getString(R.string.channel_welcome_message,
                                channel
                                        .getShortDesc());
                    }

                    OnSendMessageListener onSentMessageListener = new OnSendMessageListener() {
                        @Override
                        public void onSaveMessageSuccess(Message message) {

                            dismissIdleDialog();

                            setResult(RESULT_OK);

                            // Wechseln in ChannelChat
                            Intent intent = new Intent(ChannelDetailActivity.this,
                                    ChannelChatActivity.class);

                            intent.putExtra(ChannelChatActivity.EXTRA_TARGET_GUID,
                                    newChat.getChatGuid());
                            startActivity(intent);

                            // bug 35149 - beim abonnieren die activity schliessen
                            finish();
                        }

                        @Override
                        public void onSendMessageSuccess(Message message, int countNotSendMessages) {

                        }

                        @Override
                        public void onSendMessageError(Message message, String errorMessage, String localizedErrorIdentifier) {
                        }
                    };

                    app.getChatOverviewController().chatChanged(null, newChat.getChatGuid(), null, ChatOverviewController.CHAT_CHANGED_NEW_CHAT);
                    app.getChannelChatController().sendSystemInfo(newChat.getChatGuid(),
                            null, message, -1, onSentMessageListener, false);
                }
            }

            @Override
            public void subscribeChannelFailed(String errorMessage) {
                //auskommentiert fuer haken entfernen und speichenr onpause()
                dismissIdleDialog();

                //showErrorDialog(getString(errorTextId), false);
                if (!StringUtil.isNullOrEmpty(errorMessage)) {
                    Toast.makeText(ChannelDetailActivity.this, errorMessage,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showErrorDialog(final String errorText,
                                 final boolean finishActivity) {
        final WeakReference<ChannelDetailActivity> activityWeakRef = new WeakReference<>(this);
        final OnCloseListener closeListener = new OnCloseListener() {
            @Override
            public void onClose(final int ref) {
                if (finishActivity && activityWeakRef.get() != null && !activityWeakRef.get().isFinishing()) {
                    activityWeakRef.get().finish();
                }
            }
        };

        if (!ChannelDetailActivity.this.isFinishing()
                && !ChannelDetailActivity.this.isActivityInForeground) {
            AlertDialogWrapper dialog = DialogBuilderUtil.buildErrorDialog(ChannelDetailActivity.this, errorText, 0,
                    closeListener);

            dialog.show();
        }
    }

    @Override
    public void onBackPressed() {
        if (mFinishtoOverview) {
            final Intent intent = new Intent(this, RuntimeConfig.getClassUtil().getChatOverviewActivityClass());

            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onBackArrowPressed(View unused) {
        onBackPressed();
    }
}
