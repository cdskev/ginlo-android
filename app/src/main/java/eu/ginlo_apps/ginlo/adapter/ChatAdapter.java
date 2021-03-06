// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.adapter;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.collection.SparseArrayCompat;
import androidx.emoji.widget.EmojiAppCompatTextView;
import com.google.zxing.Dimension;

import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.ContactDetailActivity;
import eu.ginlo_apps.ginlo.OnLinkClickListener;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.components.RLottieImageView;
import eu.ginlo_apps.ginlo.concurrent.listener.ConcurrentTaskListener;
import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AVChatController;
import eu.ginlo_apps.ginlo.controller.AttachmentController;
import eu.ginlo_apps.ginlo.controller.ContactController;
import eu.ginlo_apps.ginlo.controller.ImageController;
import eu.ginlo_apps.ginlo.controller.TaskManagerController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.greendao.Channel;
import eu.ginlo_apps.ginlo.greendao.Contact;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.DecryptedMessage;
import eu.ginlo_apps.ginlo.model.Mandant;
import eu.ginlo_apps.ginlo.model.backend.ChannelModel;
import eu.ginlo_apps.ginlo.model.chat.AppGinloControlChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.BaseChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.ChannelChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.ChannelSelfDestructionChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.FileChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.ImageChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.LocationChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.RichContentChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.SelfDestructionChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.SystemInfoChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.TextChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.VCardChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.AVChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.VideoChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.VoiceChatItemVO;
import eu.ginlo_apps.ginlo.util.MimeUtil;
import eu.ginlo_apps.ginlo.util.ChannelColorUtil;
import eu.ginlo_apps.ginlo.util.ImageUtil;
import eu.ginlo_apps.ginlo.util.ScreenDesignUtil;
import eu.ginlo_apps.ginlo.util.ContactUtil;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.GuidUtil;
import eu.ginlo_apps.ginlo.util.MetricsUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.view.MaskImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatAdapter extends ArrayAdapter<BaseChatItemVO> {

    private static final int LOCATION_WIDTH_OFFSET = 15;
    private static final String TAG = ChatAdapter.class.getSimpleName();

    // Set this to true if a click on a quote in a message should lead to the
    // original quoted message.
    // Set to false if ca click on the citate has no effect.
    private static final boolean CITATE_IS_CLICKABLE = false;

    private final TaskManagerController taskManagerController;
    private final String[] directionMapping;
    private final Map<String, String> itemToTagMapping;
    private final String mChatGuid;
    private final SimsMeApplication mApplication;
    private final AttachmentController mAttachmentController;
    private final AVChatController avChatController;
    private final ContactController mContactController;
    private final ImageController mImageController;
    private final SparseArrayCompat<ProgressBar> mProgressBarsMap;
    private final SparseArrayCompat<View> mPositionToViewMapping;
    private boolean mShowTimedMessages;
    private Map<Long, Integer> mMsgIdToPositionMapping;
    private CustomLinkMovementMethod mMovementMethod;
    private OnLinkClickListener mLinkClickListener;
    private ChannelColorUtil mCcu;
    private final Activity mActivity;
    private final Context mContext;

    public ChatAdapter(final Activity activity,
                       final Application application,
                       final TaskManagerController taskManagerController,
                       final int layoutResourceId,
                       final ArrayList<BaseChatItemVO> data,
                       final String chatGuid,
                        final Context context) {
        super(activity, layoutResourceId, data);

        mActivity = activity;
        mContext = context;
        this.taskManagerController = taskManagerController;
        mChatGuid = chatGuid;
        mApplication = (SimsMeApplication) application;
        mAttachmentController = mApplication.getAttachmentController();
        avChatController = mApplication.getAVChatController();
        mContactController = mApplication.getContactController();
        mImageController = mApplication.getImageController();

        directionMapping = new String[2];
        directionMapping[BaseChatItemVO.DIRECTION_LEFT] = "left";
        directionMapping[BaseChatItemVO.DIRECTION_RIGHT] = "right";

        itemToTagMapping = new HashMap<>();
        itemToTagMapping.put(ChannelChatItemVO.class.getName(), "image_text_channel");
        itemToTagMapping.put(ChannelChatItemVO.class.getName() + "s", "text");
        itemToTagMapping.put(TextChatItemVO.class.getName(), "text");
        itemToTagMapping.put(ImageChatItemVO.class.getName(), "image");
        itemToTagMapping.put(VideoChatItemVO.class.getName(), "video");
        itemToTagMapping.put(VoiceChatItemVO.class.getName(), "voice");
        itemToTagMapping.put(AppGinloControlChatItemVO.class.getName(), "appginlocontrol");
        itemToTagMapping.put(AVChatItemVO.class.getName(), "avc");
        itemToTagMapping.put(LocationChatItemVO.class.getName(), "location");
        itemToTagMapping.put(VCardChatItemVO.class.getName(), "vcard");
        itemToTagMapping.put(SelfDestructionChatItemVO.class.getName(), "self_destruction");
        itemToTagMapping.put(SystemInfoChatItemVO.class.getName(), "system_info");
        itemToTagMapping.put(ChannelSelfDestructionChatItemVO.class.getName(), "self_destruction_channel");
        itemToTagMapping.put(RichContentChatItemVO.class.getName(), "richcontent");
        itemToTagMapping.put(FileChatItemVO.class.getName(), "file");

        mProgressBarsMap = new SparseArrayCompat<>();
        mPositionToViewMapping = new SparseArrayCompat<>();

        mShowTimedMessages = false;
    }

    public boolean getShowTimedMessages() {
        return mShowTimedMessages;
    }

    public void setShowTimedMessages(boolean newState) {
        mShowTimedMessages = newState;
    }

    @Override
    @NonNull
    public View getView(final int position,
                        final View convertView,
                        final ViewGroup parent) {
        synchronized (this) {
            if (position >= this.getCount()) {
                return new LinearLayout(getContext());
            }

            final BaseChatItemVO chatItemVO = getItem(position);
            BaseChatItemVO chatItemBefore = null;

            if (position > 0) {
                chatItemBefore = getItem(position - 1);
            }

            final LinearLayout chatItemLayout = getLayoutForChatItem(position, chatItemVO, chatItemBefore, convertView);

            if (chatItemLayout != null) {
                View selectionOverlay = chatItemLayout.findViewById(R.id.chat_item_selection_overlay);
                if(selectionOverlay != null)
                {
                    selectionOverlay.setBackgroundColor(chatItemVO.isSelected() ? ScreenDesignUtil.getInstance().getAppAccentColor(mApplication) : ScreenDesignUtil.getInstance().getTransparentColor(mApplication));
                }
                mPositionToViewMapping.put(position, chatItemLayout);
                return chatItemLayout;
            }
            return new LinearLayout(getContext());
        }
    }

    public View getView(final int position) {
        return mPositionToViewMapping.get(position);
    }

    public Integer getPositionFromMessageId(Long msgId) {
        if (msgId == null) {
            return null;
        }
        fillMsgIdToPositionMapping();
        return mMsgIdToPositionMapping.get(msgId);
    }

    public ProgressBar getProgressBarForPosition(final int position) {
        if (mProgressBarsMap == null) {
            return null;
        } else {
            return mProgressBarsMap.get(position);
        }
    }

    public void removeProgressBarFromMap(final int position) {
        if (mProgressBarsMap != null) {
            mProgressBarsMap.remove(position);
        }
    }

    private LinearLayout getLayoutForChatItem(final int position,
                                              final BaseChatItemVO chatItemVO,
                                              final BaseChatItemVO chatItemBefore,
                                              final View convertView) {
        LinearLayout linearLayout = (LinearLayout) convertView;
        final LayoutInflater layoutInflater = LayoutInflater.from(getContext());

        linearLayout = inflateMatchingLayout(layoutInflater, linearLayout, chatItemVO);

        if (!chatItemVO.isValid) {
            fillInvalidChatItem(linearLayout);
        } else if (chatItemVO instanceof ChannelSelfDestructionChatItemVO) {
            fillChannelSelfDestructionChatItem(linearLayout);
        } else if (chatItemVO instanceof ChannelChatItemVO) {
            if (StringUtil.isEqual(Channel.TYPE_SERVICE, ((ChannelChatItemVO) chatItemVO).channelType)) {
                fillTextServiceChatItem(linearLayout, (ChannelChatItemVO) chatItemVO);
            } else {
                fillTextChannelChatItem(linearLayout, (ChannelChatItemVO) chatItemVO);
            }
        } else if (chatItemVO instanceof TextChatItemVO) {
            fillTextChatItem(linearLayout, (TextChatItemVO) chatItemVO);
        } else if (chatItemVO instanceof ImageChatItemVO) {
            fillImageChatItem(linearLayout, (ImageChatItemVO) chatItemVO);
        } else if (chatItemVO instanceof VideoChatItemVO) {
            fillVideoChatItem(linearLayout, (VideoChatItemVO) chatItemVO);
        } else if (chatItemVO instanceof VoiceChatItemVO) {
            fillVoiceChatItem(linearLayout, (VoiceChatItemVO) chatItemVO);
        } else if (chatItemVO instanceof VCardChatItemVO) {
            fillVCardChatItem(linearLayout, (VCardChatItemVO) chatItemVO);
        } else if (chatItemVO instanceof AppGinloControlChatItemVO) {
            // KS: No chatItem for APP_GINLO_CONTROL to suppress message.
            // However, ChatItemVO Class is implemented. You may use this for your convenience.
            if(BuildConfig.SHOW_AGC_MESSAGES) {
                fillAppGinloControlChatItem(linearLayout, (AppGinloControlChatItemVO) chatItemVO);
            } else {
                // Should normally not happen, because we don't keep AGC messages if SHOW_AGC_MESSAGES
                // is not set.
                return null;
            }
        } else if (chatItemVO instanceof AVChatItemVO) {
            fillAVChatItem(linearLayout, (AVChatItemVO) chatItemVO);
        } else if (chatItemVO instanceof LocationChatItemVO) {
            fillLocationChatItem(linearLayout, (LocationChatItemVO) chatItemVO);
        } else if (chatItemVO instanceof SelfDestructionChatItemVO) {
            fillSelfDestructionChatItem(linearLayout, (SelfDestructionChatItemVO) chatItemVO);
        } else if (chatItemVO instanceof SystemInfoChatItemVO) {
            fillSystemInfoChatItem(linearLayout, (SystemInfoChatItemVO) chatItemVO);
        } else if (chatItemVO instanceof RichContentChatItemVO) {
            fillRichContentChatItem(linearLayout, (RichContentChatItemVO) chatItemVO);
        } else if (chatItemVO instanceof FileChatItemVO) {
            fillFileChatItem(linearLayout, (FileChatItemVO) chatItemVO);
    }

        // Aktuell verwenden wir die gecachten Items sowieso nicht
        // Wenn wir diese Zeile auskommentieren, braucht das anlegen einen viewItem statt > 30 ms eher < 10 ms

        if (chatItemVO.direction == BaseChatItemVO.DIRECTION_RIGHT) {
            final View maskImageBubble = linearLayout.findViewById(R.id.chat_item_linear_layout_chat_bubble);
            if (maskImageBubble != null) {
                maskImageBubble.getBackground().setLevel(chatItemVO.getState());
            }
        }

        if (!(chatItemVO instanceof SystemInfoChatItemVO) || ((SystemInfoChatItemVO) chatItemVO).isAbsentMessage) {
            setMessageDetails(linearLayout, chatItemVO, chatItemBefore);
        }

        if (!(chatItemVO instanceof SystemInfoChatItemVO)) {
            setMessageStatus(linearLayout, chatItemVO);
        }

        ProgressBar progressBar = linearLayout.findViewById(R.id.progressBar_download);
        if (progressBar != null) {
            int key = mProgressBarsMap.indexOfValue(progressBar);
            if (key != -1) {
                mProgressBarsMap.remove(key);
            }

            mProgressBarsMap.put(position, progressBar);
        }

        //citation
        fillCitation(chatItemVO, linearLayout);

        return linearLayout;
    }

    private void fillCitation(final BaseChatItemVO chatItemVO, LinearLayout linearLayout) {
        final View commentRoot = linearLayout.findViewById(R.id.comment_root);

        if (commentRoot == null) return;

        if(!CITATE_IS_CLICKABLE) {
            commentRoot.setFocusable(false);
            commentRoot.setClickable(false);
            commentRoot.setOnClickListener(null);
        }

        if (chatItemVO.citation != null) {
            commentRoot.setTag(chatItemVO.citation.msgGuid);
            commentRoot.setVisibility(View.VISIBLE);

            final TextView nameTextView = commentRoot.findViewById(R.id.comment_name);
            if (nameTextView != null) {
                nameTextView.setText(chatItemVO.citation.nickname);
            }
            final TextView dateTextView = commentRoot.findViewById(R.id.comment_date);
            if (dateTextView != null) {
                DateUtil.setDateToTextView(getContext(), dateTextView, chatItemVO.citation.datesend);
            }

            final TextView contentTextView = commentRoot.findViewById(R.id.comment_text);

            if (contentTextView == null) {
                return;
            }

            if (StringUtil.isEqual(chatItemVO.citation.contentType, MimeUtil.MIME_TYPE_IMAGE_JPEG)) {
                final ImageView contentImageView = commentRoot.findViewById(R.id.comment_image);
                contentImageView.setImageBitmap(chatItemVO.citation.previewImage);
                contentImageView.setVisibility(View.VISIBLE);
                if (!StringUtil.isNullOrEmpty(chatItemVO.citation.contentDesc)) {
                    contentTextView.setText(chatItemVO.citation.contentDesc);
                } else {
                    contentTextView.setText(getContext().getResources().getString(R.string.export_chat_type_image));
                }
            } else if (StringUtil.isEqual(chatItemVO.citation.contentType, MimeUtil.MIME_TYPE_VIDEO_MPEG)) {
                final ImageView contentImageView = commentRoot.findViewById(R.id.comment_image);
                contentImageView.setImageBitmap(chatItemVO.citation.previewImage);
                contentImageView.setVisibility(View.VISIBLE);
                if (!StringUtil.isNullOrEmpty(chatItemVO.citation.contentDesc)) {
                    contentTextView.setText(chatItemVO.citation.contentDesc);
                } else {
                    contentTextView.setText(getContext().getResources().getString(R.string.export_chat_type_video));
                }
            } else if (StringUtil.isEqual(chatItemVO.citation.contentType, MimeUtil.MIME_TYPE_MODEL_LOCATION)) {
                final ImageView contentImageView = commentRoot.findViewById(R.id.comment_image);
                contentImageView.setImageBitmap(chatItemVO.citation.previewImage);
                contentImageView.setVisibility(View.VISIBLE);
                if (!StringUtil.isNullOrEmpty(chatItemVO.citation.contentDesc)) {
                    contentTextView.setText(chatItemVO.citation.contentDesc);
                } else {
                    contentTextView.setText(getContext().getResources().getString(R.string.export_chat_type_location));
                }
            } else if (StringUtil.isEqual(chatItemVO.citation.contentType, MimeUtil.MIME_TYPE_TEXT_PLAIN)
                    || MimeUtil.hasUnspecificBinaryMimeType(chatItemVO.citation.contentType)
                    || MimeUtil.isRichContentMimetype(chatItemVO.citation.contentType)) {
                contentTextView.setText(chatItemVO.citation.text);
            } else if (StringUtil.isEqual(chatItemVO.citation.contentType, MimeUtil.MIME_TYPE_TEXT_RSS) && nameTextView != null) {
                nameTextView.setText("");
                contentTextView.setText(chatItemVO.citation.text);
            } else if (StringUtil.isEqual(chatItemVO.citation.contentType, MimeUtil.MIME_TYPE_TEXT_V_CARD)) {
                if (chatItemVO.citation.contentDesc != null) {
                    String vCardContactName = String.format(" \"%s\"", chatItemVO.citation.contentDesc);
                    contentTextView.setText(getContext().getResources().getString(R.string.chat_input_reply_contact) + vCardContactName);
                } else {
                    contentTextView.setText(getContext().getResources().getString(R.string.chat_input_reply_contact));
                }
            } else if (StringUtil.isEqual(chatItemVO.citation.contentType, MimeUtil.MIME_TYPE_AUDIO_MPEG)) {
                contentTextView.setText(getContext().getResources().getString(R.string.export_chat_type_audio));
                // KS: AVC. Silly, it makes no sense to answer with citing an avc message.
            } else if (StringUtil.isEqual(chatItemVO.citation.contentType, MimeUtil.MIME_TYPE_TEXT_V_CALL)) {
                contentTextView.setText(getContext().getResources().getString(R.string.export_chat_type_avc));
            }
            contentTextView.setVisibility(View.VISIBLE);
        } else {
            commentRoot.setVisibility(View.GONE);
        }

    }

    private LinearLayout inflateMatchingLayout(final LayoutInflater layoutInflater,
                                               final LinearLayout linearLayout,
                                               final BaseChatItemVO chatItemVO) {
        String tag;

        if (chatItemVO instanceof SystemInfoChatItemVO) {
            if (((SystemInfoChatItemVO) chatItemVO).isAbsentMessage) {
                tag = "text" + "_" + "left";
            } else {
                tag = itemToTagMapping.get(chatItemVO.getClassName());
            }
        } else {
            tag = itemToTagMapping.get(chatItemVO.getClassName()) + "_" + directionMapping[chatItemVO.direction];
        }

        if (!chatItemVO.isValid) {
            tag = "text_" + directionMapping[chatItemVO.direction];
        }

        if (linearLayout == null ||
                (linearLayout.getTag() != null && !linearLayout.getTag().equals(tag))) {
            final String idName = "chat_item_" + tag + "_layout";
            final int id = getContext().getResources().getIdentifier(idName, "layout", BuildConfig.APPLICATION_ID);

            return (LinearLayout) layoutInflater.inflate(id, null, false);
        }

        return linearLayout;
    }

    private void setMessageDetails(final LinearLayout linearLayout,
                                   final BaseChatItemVO chatItemVO,
                                   final BaseChatItemVO chatItemVOBefore) {
        final TextView detailView = linearLayout.findViewById(R.id.chat_item_text_view_date);

        if (detailView != null) {
            final String dateString = DateUtil.getTimeStringFromMillis(chatItemVO.getDateSend());

            detailView.setText(dateString);

            if (mCcu != null) {
                detailView.setTextColor(mCcu.getMsgColorTime());
            }
        }

        if ((chatItemVO.type == BaseChatItemVO.TYPE_GROUP) && (chatItemVO.direction == BaseChatItemVO.DIRECTION_LEFT)) {
            final TextView senderNameTextView = linearLayout.findViewById(R.id.chat_item_sender_name);

            if (senderNameTextView != null) {
                senderNameTextView.setVisibility(View.VISIBLE);
                senderNameTextView.setText(chatItemVO.name);

                String name = chatItemVO.name;

                senderNameTextView.setTextColor(ContactUtil.getColorForName(name));
            }
        }

        final TextView dateView = linearLayout.findViewById(R.id.chat_item_text_view_date_only);

        final String date2 = DateUtil.getDateStringFromMillis(chatItemVO.getDateSend());
        final String today = DateUtil.getDateStringFromMillis(new Date().getTime());

        if (chatItemVOBefore != null) {
            if (dateView != null) {
                final String dateBefore = DateUtil.getDateStringFromMillis(chatItemVOBefore.getDateSend());

                if (StringUtil.isEqual(dateBefore, date2)) {
                    dateView.setVisibility(View.GONE);
                } else {
                    dateView.setVisibility(View.VISIBLE);
                    if (StringUtil.isEqual(today, date2)) {
                        dateView.setText(getContext().getResources().getString(R.string.chat_overview_date_today));
                    } else {
                        dateView.setText(date2);
                    }
                }
            }
        } else {
            if (dateView != null) {
                dateView.setVisibility(View.VISIBLE);
                if (StringUtil.isEqual(today, date2)) {
                    dateView.setText(getContext().getResources().getString(R.string.chat_overview_date_today));
                } else {
                    dateView.setText(date2);
                }
            }
        }

        // Get sender image and set their avatar
        if (chatItemVO.type != BaseChatItemVO.TYPE_CHANNEL) {
            final MaskImageView maskedAvatarView = linearLayout.findViewById(R.id.chat_item_mask_image_view_chat_image);

            try {
                // Set up trust level information
                if (chatItemVO.direction == BaseChatItemVO.DIRECTION_LEFT) {
                    final TextView contactTypeTextView = linearLayout.findViewById(R.id.chat_item_contact_type);

                    // Default fillings
                    int indicatorColor = ScreenDesignUtil.getInstance().getLowColor(mApplication);
                    int indicatorContrastColor = ScreenDesignUtil.getInstance().getLowContrastColor(mApplication);
                    String tmpTextViewText = "     ";

                    final Contact contact = mContactController.getContactByGuid(chatItemVO.getFromGuid());
                    if(contact != null) {
                        switch(contact.getState()) {
                            case Contact.STATE_HIGH_TRUST:
                                indicatorColor = ScreenDesignUtil.getInstance().getHighColor(mApplication);
                                indicatorContrastColor = ScreenDesignUtil.getInstance().getHighContrastColor(mApplication);
                                break;
                            case Contact.STATE_MIDDLE_TRUST:
                                indicatorColor = ScreenDesignUtil.getInstance().getMediumColor(mApplication);
                                indicatorContrastColor = ScreenDesignUtil.getInstance().getMediumContrastColor(mApplication);
                                break;
                            case Contact.STATE_LOW_TRUST:
                            default:
                                // Special filling for red green blindness
                                tmpTextViewText = " !!! ";
                        }

                        // KS: These may be user settings in the future
                        final boolean showAccountLabel = true;
                        final boolean onlyShowForeignAccountTypes = true;

                        String contactTypeIdent;
                        if(showAccountLabel && (contactTypeIdent = contact.getMandant()) != null) {
                            final Mandant contactType = mApplication.getPreferencesController().getMandantFromIdent(contactTypeIdent);

                            if (contactTypeTextView != null && contactType != null) {
                                // Show some user information including account type (business/private)
                                if (StringUtil.isNullOrEmpty(contactType.ident) || StringUtil.isEqual(BuildConfig.SIMSME_MANDANT_DEFAULT, contactType.ident)) {
                                    if (onlyShowForeignAccountTypes) {
                                        // Only show "private" label if we are a business account type
                                        if (RuntimeConfig.isBAMandant()) {
                                            tmpTextViewText = (String) mApplication.getResources().getText(R.string.private_contact_label_text);
                                        }
                                    } else {
                                        // Always show account label
                                        tmpTextViewText = (String) mApplication.getResources().getText(R.string.private_contact_label_text);
                                    }
                                } else if (!StringUtil.isNullOrEmpty(contactType.label)) {
                                    if (onlyShowForeignAccountTypes) {
                                        // Only show label if contact account type differs from ours
                                        if (!BuildConfig.SIMSME_MANDANT.equals(contactType.ident)) {
                                            tmpTextViewText = contactType.label;
                                        }
                                    } else {
                                        // Always show account label
                                        tmpTextViewText = contactType.label;
                                    }
                                }
                            }
                        }
                    }

                    if (contactTypeTextView != null) {
                        contactTypeTextView.setText(tmpTextViewText);
                        contactTypeTextView.setTextColor(indicatorContrastColor);
                        contactTypeTextView.getBackground().setColorFilter(indicatorColor, PorterDuff.Mode.SRC_ATOP);
                        contactTypeTextView.setVisibility(View.VISIBLE);
                    }
                }
            } catch (Exception e) {
                LogUtil.w(TAG, "setMessageDetails: Could not set avatar/info for Guid " + chatItemVO.getFromGuid() + ": " + e.getMessage());
            }

            if(maskedAvatarView != null) {
                maskedAvatarView.setMaskRatio(1.0f);
                mImageController.fillViewWithProfileImageByGuid(chatItemVO.getFromGuid(), maskedAvatarView, ImageUtil.SIZE_ORIGINAL, false);

                // Allow for click on profile image
                View.OnClickListener profileOnClickListener = v -> {
                    final Intent intent = new Intent(mActivity, ContactDetailActivity.class);

                    try {
                        Contact contact = mContactController.getContactByGuid(chatItemVO.getFromGuid());
                        intent.putExtra(ContactDetailActivity.EXTRA_CONTACT, contact);
                        intent.putExtra(ContactDetailActivity.EXTRA_MODE, ContactDetailActivity.MODE_NO_SEND_BUTTON);
                        //intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        mActivity.startActivity(intent);
                    } catch (LocalizedException e) {
                        LogUtil.e(TAG, "setMessageDetails: Could not find contact " + chatItemVO.getFromGuid() + ": " + e.getMessage());
                    }
                };

                // Only for chat partners not for us.
                if (!GuidUtil.isSystemChat(chatItemVO.getFromGuid()) && chatItemVO.direction == BaseChatItemVO.DIRECTION_LEFT) {
                    maskedAvatarView.setOnClickListener(profileOnClickListener);
                }
            }
        }
    }

    private void fillInvalidChatItem(final LinearLayout linearLayout) {
        final TextView textView = linearLayout.findViewById(R.id.chat_item_text_view_message);
        if (textView != null) {
            textView.setText(getContext().getResources().getString(R.string.chat_encryption_signatureIsInvalid));
        }
    }

    private void fillTextChatItem(final LinearLayout linearLayout,
                                  final TextChatItemVO textChatItemVO) {
        final TextView textView = linearLayout.findViewById(R.id.chat_item_text_view_message);
        if (textView != null) {
            Pattern pattern = Pattern.compile("ginlo[A-Za-z0-9_.\\\\-~]*:\\/\\/\\S*");
            String text = textChatItemVO.message;
            Matcher m = pattern.matcher(text);
            if (m.find()) {
                String replaceBy = text.substring(m.start(), m.end());
                textView.setText(StringUtil.replaceUrlNew(text, replaceBy, pattern, false));
                //LogUtil.d(TAG, "fillTextChatItem: replaceUrlNew result = " + textView.getText());
            } else {
                textView.setText(textChatItemVO.message);
                Linkify.addLinks(textView, Linkify.WEB_URLS);
                //LogUtil.d(TAG, "fillTextChatItem: Linkify.addLinks result = " + textView.getText());
            }
            textView.setMovementMethod(getLinkMovementMethod());
            if(textChatItemVO.direction == BaseChatItemVO.DIRECTION_RIGHT)
                textView.setLinkTextColor(ScreenDesignUtil.getInstance().getContextMainColor((SimsMeApplication) getContext().getApplicationContext()));
            else
                textView.setLinkTextColor(ScreenDesignUtil.getInstance().getContextTextColor((SimsMeApplication) getContext().getApplicationContext()));

        }

        View additionalInfo = linearLayout.findViewById(R.id.additional_message_info);

        if(additionalInfo == null) return;

        if (textChatItemVO.isPriority) {
            additionalInfo.setVisibility(View.VISIBLE);
        } else {
            additionalInfo.setVisibility(View.GONE);
        }
    }

    private void fillTextChannelChatItem(final LinearLayout linearLayout,
                                         final ChannelChatItemVO channelChatItemVO) {
        final TextView textView = linearLayout.findViewById(R.id.chat_item_text_view_message);
        final TextView textViewHeader = linearLayout.findViewById(R.id.chat_item_text_view_message_header);

        if (!StringUtil.isNullOrEmpty(channelChatItemVO.messageContent)) {
            if (textView != null) {
                if (channelChatItemVO.shortLinkText != null) {
                    SpannableString ss = SpannableString.valueOf(channelChatItemVO.messageContent);
                    Linkify.addLinks(ss, Linkify.WEB_URLS);
                    textView.setText(StringUtil.replaceUrlNew(ss, channelChatItemVO.shortLinkText, null, true));
                } else {
                    textView.setText(channelChatItemVO.messageContent);
                    Linkify.addLinks(textView, Linkify.WEB_URLS);
                }
                textView.setMovementMethod(getLinkMovementMethod());
            }

            if (textViewHeader != null) {
                // possible nullpointer in EmojiAppCompatTextView
                if (!StringUtil.isNullOrEmpty(channelChatItemVO.messageHeader)) {
                    textViewHeader.setText(channelChatItemVO.messageHeader);
                } else {
                    // fuer den unwahrscheinlichen Fall, dass es eine Nachricht ohne Header gibt
                    textViewHeader.setVisibility(View.GONE);
                }
                textViewHeader.setMovementMethod(getLinkMovementMethod());
            }
        } else {
            if (textView != null) {
                textView.setVisibility(View.GONE);
            }

            if (textViewHeader != null) {
                textViewHeader.setVisibility(View.GONE);
            }
        }

        final RelativeLayout imageWrapper = linearLayout.findViewById(R.id.chat_item_image_wrapper);
        final ImageView imageView = linearLayout.findViewById(R.id.chat_item_image_view);

        if (channelChatItemVO.image != null) {
            if (imageView != null) {
                imageView.setImageBitmap(channelChatItemVO.image);
            }
        } else {
            if (imageWrapper != null) {
                imageWrapper.setVisibility(View.GONE);
            }
        }

        //Section
        final TextView sectionView = linearLayout.findViewById(R.id.chat_item_section_name);

        if (sectionView != null) {
            if (!StringUtil.isNullOrEmpty(channelChatItemVO.section)) {
                int activeSectionColor = mCcu != null ? mCcu.getMsgColorSectionActive() : Color.RED;
                int inactiveSectionColor = mCcu != null ? mCcu.getMsgColorSectionInactive() : Integer.MAX_VALUE;

                SpannableString spannable = new SpannableString(channelChatItemVO.section);
                int lastPipeIndex = channelChatItemVO.section.lastIndexOf('|');

                if ((lastPipeIndex > -1) && (lastPipeIndex < (channelChatItemVO.section.length() - 1))) {
                    spannable.setSpan(new ForegroundColorSpan(activeSectionColor),
                            lastPipeIndex + 1, channelChatItemVO.section.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                    if (inactiveSectionColor != Integer.MAX_VALUE) {
                        sectionView.setTextColor(inactiveSectionColor);
                    }
                    sectionView.setText(spannable);
                } else {
                    sectionView.setText(channelChatItemVO.section);
                    sectionView.setTextColor(activeSectionColor);
                }
            } else {
                sectionView.setText("");
            }
        }

        final ProgressBar progressBar = linearLayout.findViewById(R.id.progressBar_download);
        final ImageView progressBarImage = linearLayout.findViewById(R.id.progressBar_download_image);

        if (progressBar != null && progressBarImage != null) {
            if (StringUtil.isNullOrEmpty(channelChatItemVO.attachmentGuid)
                    || mAttachmentController.isAttachmentLocallyAvailable(channelChatItemVO.attachmentGuid)) {
                progressBar.setVisibility(View.GONE);
                progressBarImage.setVisibility(View.GONE);
            } else {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(0);
                progressBarImage.setVisibility(View.VISIBLE);
            }
        }
    }

    private void fillTextServiceChatItem(final LinearLayout linearLayout,
                                         final ChannelChatItemVO channelChatItemVO) {
        final TextView textView = linearLayout.findViewById(R.id.chat_item_text_view_message);

        if (textView != null) {
            float dpScale = getContext().getResources().getDisplayMetrics().density;
            textView.setPadding((int) (19 * dpScale + 0.5f), textView.getPaddingTop(), textView.getPaddingRight(), textView.getPaddingBottom());

            if (!StringUtil.isNullOrEmpty(channelChatItemVO.messageContent)) {
                if (channelChatItemVO.shortLinkText != null) {
                    textView.setText(StringUtil.replaceUrlNew(channelChatItemVO.messageContent, channelChatItemVO.shortLinkText, null, false));
                } else {
                    textView.setText(channelChatItemVO.messageContent);
                    Linkify.addLinks(textView, Linkify.WEB_URLS);
                }
                textView.setMovementMethod(getLinkMovementMethod());
            } else {
                textView.setVisibility(View.GONE);
            }
        }
    }

    private void fillChannelSelfDestructionChatItem(final LinearLayout linearLayout) {
        View animationView = linearLayout.findViewById(R.id.chat_item_szf_animation_view);
        AnimationDrawable ad = (AnimationDrawable) animationView.getBackground();
        ad.start();
    }

    private void fillFileChatItem(final LinearLayout linearLayout,
                                  final FileChatItemVO fileChatItemVO) {
        final TextView filenameTextView = linearLayout.findViewById(R.id.chat_item_text_view_file_name);
        final TextView filesizeTextView = linearLayout.findViewById(R.id.chat_item_text_view_file_size);

        String filename = fileChatItemVO.fileName;
        if (StringUtil.isNullOrEmpty(filename)) {
            filename = getContext().getString(R.string.chat_filename_unknown);
        }

        String filesize = fileChatItemVO.fileSize;
        if (StringUtil.isNullOrEmpty(filesize)) {
            filesize = getContext().getString(R.string.chat_filesize_unknown);
        } else {
            try {
                long sizeInBytes = Long.parseLong(filesize);
                filesize = StringUtil.getReadableByteCount(sizeInBytes);
            } catch (NumberFormatException e) {
                filesize = getContext().getString(R.string.chat_filesize_unknown);
            }
        }

        if (!StringUtil.isNullOrEmpty(fileChatItemVO.attachmentGuid)) {
            final MaskImageView background = linearLayout.findViewById(R.id.chat_item_data_placeholder_bg);
            final ImageView foreground = linearLayout.findViewById(R.id.chat_item_data_placeholder);
            if (background != null && foreground != null) {
                String mimeType;
                if (StringUtil.isNullOrEmpty(filename)) {
                    mimeType = "";
                } else {
                    mimeType = MimeUtil.getMimeTypeFromFilename(filename);
                }
                if (mimeType == null) {
                    mimeType = "";
                }

                int resID = MimeUtil.getIconForMimeType(mimeType);
                Bitmap placeholder = null;
                if (resID != MimeUtil.MIMETYPE_NOT_FOUND) {
                    placeholder = BitmapFactory.decodeResource(getContext().getResources(), resID);
                }
                foreground.setImageBitmap(placeholder);


                LogUtil.d(TAG, "fillFileChatItem: fillFileChatItem.attachment = " + fileChatItemVO.attachmentGuid);
                LogUtil.d(TAG, "fillFileChatItem: fillFileChatItem.fileMimeType = " + mimeType);


                final ProgressBar progressBar = linearLayout.findViewById(R.id.progressBar_download);
                final ImageView progressBarImage = linearLayout.findViewById(R.id.progressBar_download_image);
                if (mAttachmentController.isAttachmentLocallyAvailable(fileChatItemVO.attachmentGuid)) {
                    background.setMask(R.drawable.data_placeholder);
                    if (progressBar != null && progressBarImage != null) {
                        progressBar.setVisibility(View.GONE);
                        progressBarImage.setVisibility(View.GONE);
                    }
                } else {
                    background.setMask(R.drawable.data_placeholder_not_loaded);
                    if (progressBar != null && progressBarImage != null) {
                        progressBar.setVisibility(View.VISIBLE);
                        progressBar.setProgress(0);
                        progressBarImage.setImageBitmap(placeholder);
                        progressBarImage.setVisibility(View.VISIBLE);
                    }
                }

                background.setImageBitmapFormColor(ScreenDesignUtil.getInstance().getChatItemColor((Application) getContext().getApplicationContext()));
            }
        }

        if(filenameTextView != null) {
            filenameTextView.setText(filename);
        }

        if(filenameTextView != null) {
            filesizeTextView.setText(filesize);
        }
    }

    private void fillRichContentChatItem(final LinearLayout linearLayout,
                                  final RichContentChatItemVO richContentChatItemVO) {

        if (!StringUtil.isNullOrEmpty(richContentChatItemVO.attachmentGuid)) {
            final ImageView placeholder = linearLayout.findViewById(R.id.chat_item_glide_data_placeholder);
            final ImageView glideForeground = linearLayout.findViewById(R.id.chat_item_glide_data);
            final RLottieImageView lottieForeground = linearLayout.findViewById(R.id.chat_item_lottie_data);
            if (glideForeground != null && lottieForeground != null) {

                // Show a placeholder
                placeholder.setVisibility(View.VISIBLE);
                glideForeground.setVisibility(View.GONE);
                lottieForeground.setVisibility(View.GONE);

                AttachmentController.OnAttachmentLoadedListener richContentLoadedListener = new AttachmentController.OnAttachmentLoadedListener() {
                    @Override
                    public void onBitmapLoaded(File file, DecryptedMessage decryptedMsg) {}

                    @Override
                    public void onVideoLoaded(File videoFile, DecryptedMessage decryptedMsg) {}

                    @Override
                    public void onAudioLoaded(File audioFile, DecryptedMessage decryptedMsg) {}

                    @Override
                    public void onRichContentLoaded(File dataFile, DecryptedMessage decryptedMsg) {
                        LogUtil.d(TAG, "onRichContentLoaded: Attachment file now available: " + dataFile);
                        final String mimeType = MimeUtil.grabMimeType(dataFile.getPath(), decryptedMsg, null);

                        if  (MimeUtil.isGlideMimetype(mimeType)) {
                            // Glide
                            mImageController.fillViewWithImageFromUri(
                                    Uri.fromFile(dataFile),
                                    glideForeground, false,
                                    mApplication.getPreferencesController().getAnimateRichContent());
                            glideForeground.setVisibility(View.VISIBLE);
                            lottieForeground.setVisibility(View.GONE);
                            placeholder.setVisibility(View.GONE);
                        } else if (MimeUtil.isLottieFile(mimeType, dataFile)) {
                            // Lottie!
                            // setAutoRepeat() must be done before setAnimation()
                            if(mApplication.getPreferencesController().getAnimateRichContent()) {
                                lottieForeground.setAutoRepeat(true);
                                lottieForeground.setAnimation(Uri.fromFile(dataFile), 512, 512);
                                lottieForeground.playAnimation();
                            } else {
                                lottieForeground.setAutoRepeat(false);
                                lottieForeground.setAnimation(Uri.fromFile(dataFile), 512, 512);
                                lottieForeground.stopAnimation();
                            }

                            lottieForeground.setVisibility(View.VISIBLE);
                            glideForeground.setVisibility(View.GONE);
                            placeholder.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onFileLoaded(File dataFile, DecryptedMessage decryptedMsg) {}

                    @Override
                    public void onLoadedFailed(String message) {}

                    @Override
                    public void onHasNoAttachment(String message) {}

                    @Override
                    public void onHasAttachment(boolean finishedWork) {}

                };

                DecryptedMessage decMsg = null;
                final Message msg = mApplication.getMessageController().getMessageById(richContentChatItemVO.messageId);
                if(msg != null) {
                    decMsg = mApplication.getMessageDecryptionController().decryptMessage(msg, false);
                    if(decMsg != null) {
                        if(mAttachmentController.isAttachmentLocallyAvailable(msg.getAttachment())) {
                            try {
                                mAttachmentController.loadAttachment(decMsg, richContentLoadedListener, false, null);
                            } catch (LocalizedException e) {
                                LogUtil.e(TAG, "fillRichContentChatItem: locally richContentLoadedListener caused: " + e.getMessage());
                            }

                        } else if(mApplication.getPreferencesController().getAlwaysDownloadRichContent()) {
                            mImageController.fillViewWithImageFromResource(
                                    R.raw.crypto2a,
                                    placeholder, false);

                            try {
                                mAttachmentController.loadAttachment(decMsg, richContentLoadedListener, false, null);
                            } catch (LocalizedException e) {
                                LogUtil.e(TAG, "fillRichContentChatItem: richContentLoadedListener caused: " + e.getMessage());
                            }
                        }
                    }
                }

                // Don't show the bubble tail on rich content messages
                View bubbleTail = linearLayout.findViewById(R.id.bubble_tail);
                if (bubbleTail != null) {
                    bubbleTail.setVisibility(View.INVISIBLE);
                }
            }
        }
    }

    private void fillImageChatItem(final LinearLayout linearLayout,
                                   final ImageChatItemVO imageChatItemVO) {
        final ImageView maskImageBubble = linearLayout.findViewById(R.id.chat_item_data_placeholder_bg);
        maskImageBubble.setImageBitmap(imageChatItemVO.image);

        final TextView description = linearLayout.findViewById(R.id.chat_item_description);
        if (!StringUtil.isNullOrEmpty(imageChatItemVO.attachmentDesc)) {
            description.setVisibility(View.VISIBLE);
            description.setText(imageChatItemVO.attachmentDesc);
        } else {
            description.setVisibility(View.GONE);
        }

        final ProgressBar progressBar = linearLayout.findViewById(R.id.progressBar_download);
        final ImageView progressBarImage = linearLayout.findViewById(R.id.progressBar_download_image);
        if (progressBar != null && progressBarImage != null) {
            if (mAttachmentController.isAttachmentLocallyAvailable(imageChatItemVO.attachmentGuid)) {
                progressBar.setVisibility(View.GONE);
                progressBarImage.setVisibility(View.GONE);
            } else {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(0);
                progressBarImage.setVisibility(View.VISIBLE);
            }
        }

        View importantView = linearLayout.findViewById(R.id.priority_image);
        if (imageChatItemVO.isPriority) {
            if (importantView != null) {
                importantView.setVisibility(View.VISIBLE);
                description.setTextColor(ScreenDesignUtil.getInstance().getAlertColor(mApplication));
            }
        } else {
            if (importantView != null) {
                importantView.setVisibility(View.GONE);
            }
        }
    }

    private void fillVideoChatItem(final LinearLayout linearLayout,
                                   final VideoChatItemVO videoChatItemVO) {
        final ImageView imageView = linearLayout.findViewById(R.id.chat_item_data_placeholder_bg);

        final Bitmap bitmapCopy = ImageUtil.scale(MetricsUtil.dpToPx(getContext(),
                videoChatItemVO.image.getWidth()),
                MetricsUtil.dpToPx(getContext(),
                        videoChatItemVO.image.getHeight()), videoChatItemVO.image);

        imageView.setImageBitmap(bitmapCopy);

        final TextView description = linearLayout.findViewById(R.id.chat_item_description);

        if (!StringUtil.isNullOrEmpty(videoChatItemVO.attachmentDesc)) {
            description.setVisibility(View.VISIBLE);
            description.setText(videoChatItemVO.attachmentDesc);
        } else {
            description.setVisibility(View.GONE);
        }

        final ProgressBar progressBar = linearLayout.findViewById(R.id.progressBar_download);
        final ImageView progressBarImage = linearLayout.findViewById(R.id.progressBar_download_image);
        if (progressBarImage != null) {
            if (mAttachmentController.isAttachmentLocallyAvailable(videoChatItemVO.attachmentGuid)) {
                progressBar.setVisibility(View.GONE);
                progressBarImage.setImageResource(R.drawable.play);
                progressBarImage.setBackground(null);
            } else {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(0);
                progressBarImage.setImageResource(R.drawable.video_loading);
            }
        }

        View importantView = linearLayout.findViewById(R.id.priority_image);
        if (videoChatItemVO.isPriority) {
            if (importantView != null) {
                importantView.setVisibility(View.VISIBLE);
                description.setTextColor(ScreenDesignUtil.getInstance().getAlertColor(mApplication));
            }
        } else {
            if (importantView != null) {
                importantView.setVisibility(View.GONE);
            }
        }
    }

    private void fillVoiceChatItem(final LinearLayout linearLayout,
                                   final VoiceChatItemVO voiceChatItemVO) {
        if (!StringUtil.isNullOrEmpty(voiceChatItemVO.attachmentGuid)) {
            final MaskImageView background = linearLayout.findViewById(R.id.chat_item_data_placeholder_bg);
            final ImageView foreground = linearLayout.findViewById(R.id.chat_item_data_placeholder);
            if (background != null && foreground != null) {

                if (mAttachmentController.isAttachmentLocallyAvailable(voiceChatItemVO.attachmentGuid)) {
                    background.setMask(R.drawable.sound_placeholder);
                } else {
                    background.setMask(R.drawable.sound_placeholder_not_loaded);
                }

                if (voiceChatItemVO.isPriority) {
                    background.setImageBitmapFormColor(ScreenDesignUtil.getInstance().getAlertColor(mApplication));

                    final TextView label = linearLayout.findViewById(R.id.chat_item_text_view_type);
                    final TextView clockView = linearLayout.findViewById(R.id.chat_item_text_view_clock);
                    label.setTextColor(ScreenDesignUtil.getInstance().getAlertColor(mApplication));
                    clockView.setTextColor(ScreenDesignUtil.getInstance().getAlertColor(mApplication));
                } else {
                    background.setImageBitmapFormColor(ScreenDesignUtil.getInstance().getChatItemColor((SimsMeApplication) getContext().getApplicationContext()));
                }
            }
        }
    }

    // KS: APP_GINLO_CONTROL
    private void fillAppGinloControlChatItem(final LinearLayout linearLayout,
                                  final AppGinloControlChatItemVO appGinloControlChatItemVO) {
        final TextView textView = linearLayout.findViewById(R.id.chat_item_text_view_message);
        if (textView != null) {
            textView.setText(appGinloControlChatItemVO.displayMessage);

            if(appGinloControlChatItemVO.direction == BaseChatItemVO.DIRECTION_RIGHT)
                textView.setLinkTextColor(ScreenDesignUtil.getInstance().getContextMainColor((SimsMeApplication) getContext().getApplicationContext()));
            else
                textView.setLinkTextColor(ScreenDesignUtil.getInstance().getContextTextColor((SimsMeApplication) getContext().getApplicationContext()));

        }
    }

    // KS: AVC
    private void fillAVChatItem(final LinearLayout linearLayout,
                                   final AVChatItemVO avChatItemVO) {

        final MaskImageView background = linearLayout.findViewById(R.id.chat_item_data_placeholder_bg);
        final ImageView foreground = linearLayout.findViewById(R.id.chat_item_data_placeholder);
        if (background != null && foreground != null) {

            background.setMask(R.drawable.ic_phone_call2);

            final TextView clockView = linearLayout.findViewById(R.id.chat_item_text_view_clock);
            if (clockView != null) {
                if (avChatController != null) {
                    final long cTime =  System.currentTimeMillis();

                    // Processing only if message is not too old
                    if ((avChatItemVO.getDateSend() + AVChatController.getCallTimeoutMillis()) > cTime) {
                        String[] roomInfo = AVChatController.deserializeRoomInfoMessageString(avChatItemVO.room);
                        String[] pwAndRoom = avChatController.getPasswordAndRoom();
                        if(pwAndRoom != null) {
                            // There is already a room configuration - check whether it matches the one of the message
                            if (roomInfo != null) {
                                if (!pwAndRoom[1].equals(roomInfo[1]) && ((avChatItemVO.getDateSend() + (AVChatController.AVC_CALL_ANSWER_TIME * 1000)) < cTime)) {
                                    // Not the same room and not a very young message - don't show "pickup"
                                    clockView.setVisibility(View.GONE);
                                }
                            }
                        }
                    } else {
                        clockView.setVisibility(View.GONE);
                    }
                } else {
                    // avChatController not available!
                    clockView.setVisibility(View.GONE);
                }
            }
            background.setImageBitmapFormColor(ScreenDesignUtil.getInstance().getBlackColor(mApplication));
        }
    }

    private void fillVCardChatItem(final LinearLayout linearLayout,
                                   final VCardChatItemVO vCardChatItemVO) {
        final MaskImageView contactMaskImageView = linearLayout.findViewById(R.id.chat_item_mask_image_view_contact_image);
        final TextView contactTextView = linearLayout.findViewById(R.id.chat_item_text_view_contact_label);

        if (vCardChatItemVO.photo != null) {
            contactMaskImageView.setImageBitmap(vCardChatItemVO.photo);
            linearLayout.setTag("" + vCardChatItemVO.messageId);
        } else if (vCardChatItemVO.photoUrl != null) {
            final ConcurrentTaskListener listener = new ConcurrentTaskListener() {
                @Override
                public void onStateChanged(final ConcurrentTask task,
                                           final int state) {
                    if (state == ConcurrentTask.STATE_COMPLETE) {
                        final Bitmap image = (Bitmap) task.getResults()[0];

                        if (image != null) {
                            contactMaskImageView.setImageBitmap(image);
                        }
                    }
                }
            };
            taskManagerController.getHttpTaskManager().executeDownloadImageTask(vCardChatItemVO.photoUrl, listener);
            linearLayout.setTag("" + vCardChatItemVO.messageId);
        }

        String templateText = getContext().getString(R.string.chats_contactMessageCell_sendContactTittle);

        templateText = String.format(templateText, vCardChatItemVO.displayInfo);
        contactTextView.setText(templateText);
    }

    private void fillLocationChatItem(final LinearLayout linearLayout,
                                      final LocationChatItemVO locationChatItemVO) {
        final ImageView maskImageBubble = linearLayout.findViewById(R.id.chat_item_data_placeholder_bg);

        final Dimension profileImageDimension = ImageUtil.getDimensionForSize(getContext().getResources(),
                ImageUtil.SIZE_CHAT);
        final DisplayMetrics displayMetrics = MetricsUtil.getDisplayMetrics(getContext());

        final Bitmap locationBitmap;

        if (locationChatItemVO.image.getWidth() > (displayMetrics.widthPixels - profileImageDimension.getWidth())) {
            final int width = displayMetrics.widthPixels - profileImageDimension.getWidth() - LOCATION_WIDTH_OFFSET;
            final int height = Math.round((width / (float) locationChatItemVO.image.getWidth()) * locationChatItemVO.image.getHeight());

            locationBitmap = ImageUtil.scale(width, height, locationChatItemVO.image);
        } else {
            locationBitmap = locationChatItemVO.image;
        }
        maskImageBubble.setImageBitmap(locationBitmap);
    }

    private void fillSystemInfoChatItem(final LinearLayout linearLayout,
                                        final SystemInfoChatItemVO systemInfoChatItemVO) {
        if (!systemInfoChatItemVO.isAbsentMessage) {
            final EmojiAppCompatTextView systemInfoTextView = linearLayout.findViewById(R.id.chat_item_text_view_system_info);

            if (systemInfoTextView != null) {
                systemInfoTextView.setText(systemInfoChatItemVO.infoText);
            }
        } else {
            final TextView textView = linearLayout.findViewById(R.id.chat_item_text_view_message);
            // Erkennen der Simsme Patter
            //Pattern pattern = Pattern.compile(RuntimeConfig.getScheme() + ":\\/\\/\\S*");
            //KS: Linkify *all* URLs
            Pattern pattern = Pattern.compile("[A-Za-z0-9_.\\-~]+" + ":\\/\\/\\S*");

            String text = systemInfoChatItemVO.infoText;
            Matcher m = pattern.matcher(text);
            if (textView != null) {
                if (m.find()) {
                    String replaceBy = text.substring(m.start(), m.end());
                    textView.setText(StringUtil.replaceUrlNew(text, replaceBy, pattern, false));
                } else {
                    textView.setText(text);
                    Linkify.addLinks(textView, Linkify.WEB_URLS);
                }
                textView.setMovementMethod(getLinkMovementMethod());
            }

            View bubble = linearLayout.findViewById(R.id.chat_item_linear_layout_chat_bubble);
            bubble.setBackgroundResource(R.drawable.chat_white);
        }
    }

    private void fillSelfDestructionChatItem(final LinearLayout linearLayout,
                                             final SelfDestructionChatItemVO selfDestructionChatItemVO) {
        final TextView destructionType = linearLayout.findViewById(R.id.chat_item_text_view_destruction_type);
        final TextView destructionLabel = linearLayout.findViewById(R.id.chat_item_text_view_destruction_label);

        switch (selfDestructionChatItemVO.destructionType) {
            case SelfDestructionChatItemVO.TYPE_TEXT:
                destructionType.setText(getContext().getString(R.string.chats_destructionMessageCell_textType));
                break;
            case SelfDestructionChatItemVO.TYPE_VIDEO:
                destructionType.setText(getContext().getString(R.string.chats_destructionMessageCell_videoType));
                break;
            case SelfDestructionChatItemVO.TYPE_VOICE:
                destructionType.setText(getContext().getString(R.string.chats_voiceMessage_title));
                break;
            case SelfDestructionChatItemVO.TYPE_IMAGE:
                destructionType.setText(getContext().getString(R.string.chats_destructionMessageCell_imageType));
                break;
            default:
                break;
        }

        if (selfDestructionChatItemVO.destructionParams.countdown != null) {
            if (selfDestructionChatItemVO.destructionParams.countdown < 2) {
                destructionLabel.setText(selfDestructionChatItemVO.destructionParams.countdown + " " + getContext().getString(R.string.chats_selfdestruction_countdown_second));
            } else {
                destructionLabel.setText(selfDestructionChatItemVO.destructionParams.countdown + " " + getContext().getString(R.string.chats_selfdestruction_countdown_seconds));
            }
        } else if (selfDestructionChatItemVO.destructionParams.date != null) {
            destructionLabel.setText(DateUtil.getDateAndTimeStringFromMillis(selfDestructionChatItemVO.destructionParams
                    .date.getTime()));
        }

        final MaskImageView background = linearLayout.findViewById(R.id.chat_item_data_placeholder_bg);
        if (!StringUtil.isNullOrEmpty(selfDestructionChatItemVO.attachmentGuid)) {

            final ImageView foreground = linearLayout.findViewById(R.id.chat_item_data_placeholder);
            if (background != null && foreground != null) {

                if (mAttachmentController.isAttachmentLocallyAvailable(selfDestructionChatItemVO.attachmentGuid)) {
                    background.setMask(R.drawable.szf_overlay);
                } else {
                    background.setMask(R.drawable.szf_overlay_not_loaded);
                }
            }
        } else {
            if (background != null) {
                background.setMask(R.drawable.szf_overlay);
            }
        }

        if (selfDestructionChatItemVO.isPriority) {
            if (background != null) {
                background.setImageBitmapFormColor(ScreenDesignUtil.getInstance().getAlertColor(mApplication));
            }
            destructionType.setTextColor(ScreenDesignUtil.getInstance().getAlertColor(mApplication));
            destructionLabel.setTextColor(ScreenDesignUtil.getInstance().getAlertColor(mApplication));
        } else {
            if (background != null) {
                background.setImageBitmapFormColor(ScreenDesignUtil.getInstance().getChatItemColor((Application) getContext().getApplicationContext()));
            }
        }
    }

    private void setMessageStatus(final LinearLayout linearLayout,
                                  final BaseChatItemVO chatItemVO) {
        final FrameLayout messageStatusFrameLayout = linearLayout.findViewById(R.id.chat_item_frame_layout_message_status);

        if (messageStatusFrameLayout != null) {
            messageStatusFrameLayout.setVisibility(View.GONE);
            if (chatItemVO.direction == BaseChatItemVO.DIRECTION_RIGHT) {
                linearLayout.setAlpha(1.0f);

                if (chatItemVO.hasSendError) {
                    messageStatusFrameLayout.setVisibility(View.VISIBLE);
                    messageStatusFrameLayout.getBackground().setLevel(Message.MESSAGE_STATUS_ERROR);
                    return;
                }

                if (!chatItemVO.isSendConfirmed()) {
                    linearLayout.setAlpha(0.3f);
                    return;
                }

                messageStatusFrameLayout.setVisibility(View.VISIBLE);
                messageStatusFrameLayout.getBackground().setLevel(Message.MESSAGE_STATUS_SENT);

                if (!chatItemVO.hasDownloaded) {
                    return;
                }
                messageStatusFrameLayout.getBackground().setLevel(Message.MESSAGE_STATUS_DOWNLOADED);

                if (!chatItemVO.hasRead) {
                    return;
                }
                messageStatusFrameLayout.getBackground().setLevel(Message.MESSAGE_STATUS_READ);
            }
        }
    }

    public void removerItemByGuid(final Long messageGuid, final boolean notify) {
        ((Activity) getContext()).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (messageGuid != null) {
                    for (int i = 0; i < getCount(); ++i) {
                        BaseChatItemVO item = getItem(i);
                        if (item != null && item.messageId == messageGuid) {
                            remove(item);
                            if (notify) {
                                notifyDataSetChanged();
                            }

                            return;
                        }
                    }
                }
            }
        });
    }

    public void setOnLinkClickListener(final OnLinkClickListener linkClickListener) {
        mLinkClickListener = linkClickListener;
    }

    private void fillMsgIdToPositionMapping() {
        int count = getCount();

        if (mMsgIdToPositionMapping == null) {
            mMsgIdToPositionMapping = new HashMap<>();
        } else {
            mMsgIdToPositionMapping.clear();
        }

        for (int i = 0; i < count; ++i) {
            BaseChatItemVO item = getItem(i);
            if (item != null) {
                mMsgIdToPositionMapping.put(item.messageId, i);
            }
        }
    }

    private MovementMethod getLinkMovementMethod() {
        if (mMovementMethod == null) {
            mMovementMethod = new CustomLinkMovementMethod();
        }

        return mMovementMethod;
    }

    public void setChannelModel(ChannelModel channelModel) {
        if (channelModel != null) {
            mCcu = new ChannelColorUtil(channelModel.layout, getContext());
        }
    }

    public String getChatGuid() {
        return mChatGuid;
    }

    private class CustomLinkMovementMethod
            extends LinkMovementMethod {

        @Override
        public boolean onTouchEvent(final TextView widget,
                                    final Spannable buffer,
                                    final MotionEvent event) {
            final int action = event.getAction();

            if (action == MotionEvent.ACTION_UP) {  //action == MotionEvent.ACTION_DOWN ) ||

                if (widget != null) {
                    final int x = (int) event.getX() - widget.getTotalPaddingLeft() + widget.getScrollX();
                    final int y = (int) event.getY() - widget.getTotalPaddingTop() + widget.getScrollY();
                    final Layout layout = widget.getLayout();

                    if (layout != null) {
                        final int line = layout.getLineForVertical(y);
                        final int off = layout.getOffsetForHorizontal(line, x);
                        final ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);

                        if ((link != null) && (link.length > 0) && (mLinkClickListener != null)) {
                            if (link[0] instanceof URLSpan) {
                                mLinkClickListener.onLinkClick(((URLSpan) link[0]).getURL());
                            }

                            return false;
                        }
                    }
                }
            }
            return super.onTouchEvent(widget, buffer, event);
        }
    }
}
