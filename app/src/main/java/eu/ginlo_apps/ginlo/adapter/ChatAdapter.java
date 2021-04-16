// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo.adapter;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
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
import androidx.core.content.ContextCompat;
import androidx.emoji.widget.EmojiAppCompatTextView;
import com.google.zxing.Dimension;
import eu.ginlo_apps.ginlo.BuildConfig;
import eu.ginlo_apps.ginlo.OnLinkClickListener;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.concurrent.listener.ConcurrentTaskListener;
import eu.ginlo_apps.ginlo.concurrent.task.ConcurrentTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.AVChatController;
import eu.ginlo_apps.ginlo.controller.AttachmentController;
import eu.ginlo_apps.ginlo.controller.ChatImageController;
import eu.ginlo_apps.ginlo.controller.TaskManagerController;
import eu.ginlo_apps.ginlo.greendao.Channel;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.model.backend.ChannelModel;
import eu.ginlo_apps.ginlo.model.chat.AppGinloControlChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.BaseChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.ChannelChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.ChannelSelfDestructionChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.FileChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.ImageChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.LocationChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.SelfDestructionChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.SystemInfoChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.TextChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.VCardChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.AVChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.VideoChatItemVO;
import eu.ginlo_apps.ginlo.model.chat.VoiceChatItemVO;
import eu.ginlo_apps.ginlo.model.constant.MimeType;
import static eu.ginlo_apps.ginlo.model.constant.NumberConstants.FLOAT_05;
import eu.ginlo_apps.ginlo.util.BitmapUtil;
import eu.ginlo_apps.ginlo.util.ChannelColorUtil;
import eu.ginlo_apps.ginlo.util.ColorUtil;
import eu.ginlo_apps.ginlo.util.ContactUtil;
import eu.ginlo_apps.ginlo.util.DateUtil;
import eu.ginlo_apps.ginlo.util.FileUtil;
import eu.ginlo_apps.ginlo.util.MetricsUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.view.MaskImageView;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatAdapter
        extends ArrayAdapter<BaseChatItemVO> {

    private static final int LOCATION_WIDTH_OFFSET = 15;

    private final TaskManagerController taskManagerController;
    private final String[] directionMapping;
    private final Map<String, String> itemToTagMapping;
    private final String mChatGuid;
    private final AttachmentController mAttachmentController;
    private final AVChatController avChatController;
    private final SparseArrayCompat<ProgressBar> mProgressBarsMap;
    private final SparseArrayCompat<View> mPositionToViewMapping;
    private boolean mShowTimedMessages;
    private Map<Long, Integer> mMsgIdToPositionMapping;
    private CustomLinkMovementMethod mMovementMethod;
    private OnLinkClickListener mLinkClickListener;
    private ChannelColorUtil mCcu;
    private Context mContext;

    public ChatAdapter(final Activity activity,
                       final Application application,
                       final TaskManagerController taskManagerController,
                       final int layoutResourceId,
                       final ArrayList<BaseChatItemVO> data,
                       final String chatGuid,
                        final Context context) {
        super(activity, layoutResourceId, data);

        mContext = context;
        this.taskManagerController = taskManagerController;
        mChatGuid = chatGuid;
        mAttachmentController = ((SimsMeApplication) application).getAttachmentController();
        avChatController = ((SimsMeApplication) application).getAVChatController();

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
                    selectionOverlay.setBackgroundColor(mContext.getResources().getColor(chatItemVO.isSelected() ? R.color.color2_20 : R.color.transparent));
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

            if (StringUtil.isEqual(chatItemVO.citation.contentType, MimeType.IMAGE_JPEG)) {
                final ImageView contentImageView = commentRoot.findViewById(R.id.comment_image);
                contentImageView.setImageBitmap(chatItemVO.citation.previewImage);
                contentImageView.setVisibility(View.VISIBLE);
                if (!StringUtil.isNullOrEmpty(chatItemVO.citation.contentDesc)) {
                    contentTextView.setText(chatItemVO.citation.contentDesc);
                } else {
                    contentTextView.setText(getContext().getResources().getString(R.string.export_chat_type_image));
                }
            } else if (StringUtil.isEqual(chatItemVO.citation.contentType, MimeType.VIDEO_MPEG)) {
                final ImageView contentImageView = commentRoot.findViewById(R.id.comment_image);
                contentImageView.setImageBitmap(chatItemVO.citation.previewImage);
                contentImageView.setVisibility(View.VISIBLE);
                if (!StringUtil.isNullOrEmpty(chatItemVO.citation.contentDesc)) {
                    contentTextView.setText(chatItemVO.citation.contentDesc);
                } else {
                    contentTextView.setText(getContext().getResources().getString(R.string.export_chat_type_video));
                }
            } else if (StringUtil.isEqual(chatItemVO.citation.contentType, MimeType.MODEL_LOCATION)) {
                final ImageView contentImageView = commentRoot.findViewById(R.id.comment_image);
                contentImageView.setImageBitmap(chatItemVO.citation.previewImage);
                contentImageView.setVisibility(View.VISIBLE);
                if (!StringUtil.isNullOrEmpty(chatItemVO.citation.contentDesc)) {
                    contentTextView.setText(chatItemVO.citation.contentDesc);
                } else {
                    contentTextView.setText(getContext().getResources().getString(R.string.export_chat_type_location));
                }
            } else if (StringUtil.isEqual(chatItemVO.citation.contentType, MimeType.TEXT_PLAIN)
                    || StringUtil.isEqual(chatItemVO.citation.contentType, MimeType.APP_OCTET_STREAM)) {
                contentTextView.setText(chatItemVO.citation.text);
            } else if (StringUtil.isEqual(chatItemVO.citation.contentType, MimeType.TEXT_RSS) && nameTextView != null) {
                nameTextView.setText("");
                contentTextView.setText(chatItemVO.citation.text);
            } else if (StringUtil.isEqual(chatItemVO.citation.contentType, MimeType.TEXT_V_CARD)) {
                if (chatItemVO.citation.contentDesc != null) {
                    String vCardContactName = String.format(" \"%s\"", chatItemVO.citation.contentDesc);
                    contentTextView.setText(getContext().getResources().getString(R.string.chat_input_reply_contact) + vCardContactName);
                } else {
                    contentTextView.setText(getContext().getResources().getString(R.string.chat_input_reply_contact));
                }
            } else if (StringUtil.isEqual(chatItemVO.citation.contentType, MimeType.AUDIO_MPEG)) {
                contentTextView.setText(getContext().getResources().getString(R.string.export_chat_type_audio));
                // KS: AVC. Silly, it makes no sense to answer with citing an avc message.
            } else if (StringUtil.isEqual(chatItemVO.citation.contentType, MimeType.TEXT_V_CALL)) {
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
            Pattern pattern = Pattern.compile(RuntimeConfig.getScheme() + ":\\/\\/\\S*");
            String text = textChatItemVO.message;
            Matcher m = pattern.matcher(text);
            if (m.find()) {
                String replaceBy = text.substring(m.start(), m.end());

                textView.setText(StringUtil.replaceUrlNew(text, replaceBy, pattern, false));
            } else {
                textView.setText(textChatItemVO.message);
                Linkify.addLinks(textView, Linkify.WEB_URLS);
            }
            textView.setMovementMethod(getLinkMovementMethod());
            if(textChatItemVO.direction == BaseChatItemVO.DIRECTION_RIGHT)
                textView.setLinkTextColor(ColorUtil.getInstance().getContextMainColor((SimsMeApplication) getContext().getApplicationContext()));
            else
                textView.setLinkTextColor(ColorUtil.getInstance().getContextTextColor((SimsMeApplication) getContext().getApplicationContext()));

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
            textView.setPadding((int) (19 * dpScale + FLOAT_05), textView.getPaddingTop(), textView.getPaddingRight(), textView.getPaddingBottom());

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
                description.setTextColor(ContextCompat.getColor(getContext(), R.color.kColorAlert));
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

        final Bitmap bitmapCopy = BitmapUtil.scale(MetricsUtil.dpToPx(getContext(),
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
                description.setTextColor(ContextCompat.getColor(getContext(), R.color.kColorAlert));
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
                    background.setImageBitmapFormColor(ContextCompat.getColor(getContext(), R.color.kColorAlert));

                    final TextView label = linearLayout.findViewById(R.id.chat_item_text_view_type);
                    final TextView clockView = linearLayout.findViewById(R.id.chat_item_text_view_clock);
                    label.setTextColor(ContextCompat.getColor(getContext(), R.color.kColorAlert));
                    clockView.setTextColor(ContextCompat.getColor(getContext(), R.color.kColorAlert));
                } else {
                    background.setImageBitmapFormColor(ColorUtil.getInstance().getChatItemColor((SimsMeApplication) getContext().getApplicationContext()));
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
                textView.setLinkTextColor(ColorUtil.getInstance().getContextMainColor((SimsMeApplication) getContext().getApplicationContext()));
            else
                textView.setLinkTextColor(ColorUtil.getInstance().getContextTextColor((SimsMeApplication) getContext().getApplicationContext()));

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

            // Always use black - ColorUtil is Theme dependent!
            // background.setImageBitmapFormColor(ColorUtil.getInstance().getChatItemColor((SimsMeApplication) getContext().getApplicationContext()));
            background.setImageBitmapFormColor(ContextCompat.getColor(getContext(), R.color.black));
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

        final Dimension profileImageDimension = ChatImageController.getDimensionForSize(getContext().getResources(),
                ChatImageController.SIZE_CHAT);
        final DisplayMetrics displayMetrics = MetricsUtil.getDisplayMetrics(getContext());

        final Bitmap locationBitmap;

        if (locationChatItemVO.image.getWidth() > (displayMetrics.widthPixels - profileImageDimension.getWidth())) {
            final int width = displayMetrics.widthPixels - profileImageDimension.getWidth() - LOCATION_WIDTH_OFFSET;
            final int height = Math.round((width / (float) locationChatItemVO.image.getWidth()) * locationChatItemVO.image.getHeight());

            locationBitmap = BitmapUtil.scale(width, height, locationChatItemVO.image);
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
            Pattern pattern = Pattern.compile(RuntimeConfig.getScheme() + ":\\/\\/\\S*");
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
                background.setImageBitmapFormColor(ContextCompat.getColor(getContext(), R.color.kColorAlert));
            }
            destructionType.setTextColor(ContextCompat.getColor(getContext(), R.color.kColorAlert));
            destructionLabel.setTextColor(ContextCompat.getColor(getContext(), R.color.kColorAlert));
        } else {
            if (background != null) {
                background.setImageBitmapFormColor(ColorUtil.getInstance().getChatItemColor((Application) getContext().getApplicationContext()));
            }
        }
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
                    mimeType = FileUtil.getMimeTypeFromPath(filename);
                }
                if (mimeType == null) {
                    mimeType = "";
                }

                int resID = FileUtil.getIconForMimeType(mimeType);

                if (resID != FileUtil.MIMETYPE_NOT_FOUND) {
                    final Bitmap placeholder = BitmapFactory.decodeResource(getContext().getResources(), resID);
                    foreground.setImageBitmap(placeholder);
                } else {
                    foreground.setImageBitmap(null);
                }

                if (mAttachmentController.isAttachmentLocallyAvailable(fileChatItemVO.attachmentGuid)) {
                    background.setMask(R.drawable.data_placeholder);
                } else {
                    background.setMask(R.drawable.data_placeholder_not_loaded);
                }
                background.setImageBitmapFormColor(ColorUtil.getInstance().getChatItemColor((Application) getContext().getApplicationContext()));
            }
        }
        filenameTextView.setText(filename);
        filesizeTextView.setText(filesize);
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
