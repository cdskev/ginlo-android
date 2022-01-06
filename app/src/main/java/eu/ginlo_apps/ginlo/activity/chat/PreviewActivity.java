// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.activity.chat;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.TranslateAnimation;
import static android.widget.AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.VideoView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import eu.ginlo_apps.ginlo.CameraActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.TextExtensionsKt;
import eu.ginlo_apps.ginlo.concurrent.manager.SerialExecutor;
import eu.ginlo_apps.ginlo.concurrent.task.ConvertToChatItemVOTask;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.message.ChatController;
import eu.ginlo_apps.ginlo.controller.message.MessageController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.fragment.ChatInputFragment;
import eu.ginlo_apps.ginlo.fragment.SelfdestructionFragment.DatePickerFragment;
import eu.ginlo_apps.ginlo.fragment.SelfdestructionFragment.TimePickerFragment;
import eu.ginlo_apps.ginlo.fragment.emojipicker.EmojiPickerCallback;
import eu.ginlo_apps.ginlo.fragment.emojipicker.EmojiPickerFragment;
import eu.ginlo_apps.ginlo.greendao.Message;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.model.param.MessageDestructionParams;
import eu.ginlo_apps.ginlo.util.BitmapUtil;
import eu.ginlo_apps.ginlo.util.ColorUtil;
import eu.ginlo_apps.ginlo.util.DialogBuilderUtil;
import eu.ginlo_apps.ginlo.util.FileUtil;
import eu.ginlo_apps.ginlo.util.ImageLoader;
import eu.ginlo_apps.ginlo.util.JsonUtil;
import eu.ginlo_apps.ginlo.util.KeyboardUtil;
import eu.ginlo_apps.ginlo.util.Listener.GenericActionListener;
import eu.ginlo_apps.ginlo.util.MetricsUtil;
import eu.ginlo_apps.ginlo.util.PermissionUtil;
import eu.ginlo_apps.ginlo.util.RuntimeConfig;
import eu.ginlo_apps.ginlo.util.StringUtil;
import eu.ginlo_apps.ginlo.util.SystemUtil;
import eu.ginlo_apps.ginlo.util.VideoProvider;
import eu.ginlo_apps.ginlo.util.VideoUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class PreviewActivity
        extends ChatInputActivity
        implements EmojiPickerCallback {

    private static final String TAG = PreviewActivity.class.getSimpleName();
    private static final String PREVIEW_URI = "mediaUri";
    private static final String ACTION_TYPE = "actionType";
    private static final SerialExecutor COPY_SERIAL_EXECUTOR = new SerialExecutor();

    public static final int MAX_MEDIA_ITEMS = 10;
    public static final int SELECT_PHOTOS_ACTION = 100;
    public static final int SELECT_VIDEOS_ACTION = 200;
    public static final int TAKE_VIDEOS_ACTION = 400;
    public static final int TAKE_PHOTOS_ACTION = 500;
    public static final String EXTRA_PREVIEW_ACTION = "PreviewActivity.previewAction";
    public static final String EXTRA_PREVIEW_TITLE = "PreviewActivity.previewTitle";
    public static final String EXTRA_DESTRUCTION_PARAMS = "PreviewActivity.destructionParams";
    public static final String EXTRA_TIMER = "PreviewActivity.timer";
    public static final String EXTRA_URIS = "PreviewActivity.image";
    public static final String EXTRA_TEXTS = "PreviewActivity.texts";
    public static final String EXTRA_IS_PRIORITY = "PreviewActivity.isPriority";
    public static final String EXTRA_SHOW_ADD_BUTTON = "PreviewActivity.showAddButton";

    private ImageLoader mImageLoader;
    private RecyclerView mThumbnailRecyclerView;
    private ThumbnailAdapter mAdapter;
    private float mDensityMultiplier;
    private int mPreviewAction;
    private PreviewPagerAdapter mPreviewPagerAdapter;
    private ViewPager mViewPager;
    private int mCurrentPosition;
    private Uri mTakePhotoUri;
    private boolean mShowAddButton;
    private ArrayList<ImageModel> mModels;

    private final GenericActionListener<ArrayList<String>> mCopyListener = new GenericActionListener<ArrayList<String>>() {
        @Override
        public void onSuccess(ArrayList<String> objects) {
            if (objects != null) {
                for (final String uri : objects) {
                    if (!addUri(uri, "")) {
                        break;
                    }
                }

                updateUI();
            }

            dismissIdleDialog();
        }

        @Override
        public void onFail(String message, String errorIdent) {
            dismissIdleDialog();
        }
    };

    @Override
    protected ChatController getChatController() {
        return null;
    }

    @Override
    protected void onCreateActivity(final Bundle savedInstanceState) {
        try {
            super.onCreateActivity(savedInstanceState);

            final Intent intent = getIntent();

            mModels = new ArrayList<>();

            if (savedInstanceState != null) {
                String savedUris = savedInstanceState.getString(EXTRA_URIS);
                if (!StringUtil.isNullOrEmpty(savedUris)) {
                    JsonArray ja = JsonUtil.getJsonArrayFromString(savedUris);
                    if (ja != null) {
                        for (int i = 0; i < ja.size(); i++) {
                            JsonElement je = ja.get(0);
                            if (je.isJsonObject()) {
                                ImageModel im = new ImageModel();
                                if (im.fillModelFromJsonObject(je.getAsJsonObject())) {
                                    mModels.add(im);
                                }
                            }
                        }
                    }
                }
            }

            if (!intent.hasExtra(EXTRA_PREVIEW_ACTION) || (mModels.size() < 1 && !intent.hasExtra(EXTRA_URIS))) {
                throw new LocalizedException(LocalizedException.NO_DATA_FOUND, "EXTRA_... parameters are missing");
            }

            final ArrayList<String> uris;
            final ArrayList<String> texts;

            if (mModels.size() < 1) {
                uris = intent.getStringArrayListExtra(EXTRA_URIS);
                texts = intent.getStringArrayListExtra(EXTRA_TEXTS);
                if ((uris == null) || (uris.size() < 1)) {
                    throw new LocalizedException(LocalizedException.NO_DATA_FOUND);
                }
            } else {
                uris = null;
                texts = null;
            }

            final int slideheigthSixLines = (int) getResources().getDimension(R.dimen.chat_slideheight_six_lines);
            mAnimationSlideInSixLines = new TranslateAnimation(0, 0, slideheigthSixLines, 0);
            mAnimationSlideOutSixLines = new TranslateAnimation(0, 0, 0, slideheigthSixLines);

            mChatInputContainerId = R.id.preview_chat_input_fragment_placeholder;
            mFragmentContainerId = R.id.preview_fragment_container;

            if (savedInstanceState != null) {
                Fragment f = getSupportFragmentManager().findFragmentById(mChatInputContainerId);

                if (f instanceof ChatInputFragment) {
                    mChatInputFragment = (ChatInputFragment) f;
                }
            }

            if (mChatInputFragment == null) {
                mChatInputFragment = new ChatInputFragment();
                getSupportFragmentManager().beginTransaction()
                        .add(mChatInputContainerId, mChatInputFragment).commit();
            }

            mChatInputFragment.setSimpleUi(true);

            mChatInputFragment.setAllowSendWithEmptyMessage(true);

            mImageLoader = initImageLoader();

            mThumbnailRecyclerView = findViewById(R.id.preview_thumbnail_list);

            mThumbnailRecyclerView.setHasFixedSize(true);

            final LinearLayout imageListContainer = findViewById(R.id.preview_bottom_bar_inner_images);

            // Layout Manager mit Befuellung von Rechts nach Links
            final RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, true);

            mThumbnailRecyclerView.setLayoutManager(layoutManager);

            mDensityMultiplier = this.getResources().getDisplayMetrics().density;

            if (intent.hasExtra(EXTRA_PREVIEW_TITLE)) {
                setTitle(intent.getStringExtra(EXTRA_PREVIEW_TITLE));
            }

            if (intent.hasExtra(EXTRA_CITATED_MSG_MODEL_ID)) {
                //leider kann man beim inten zwar das chatitemV0 mitgeben, aber das Image geht dabei verloren
                final long id = intent.getLongExtra(EXTRA_CITATED_MSG_MODEL_ID, 0);

                if (id != 0) {
                    final SimsMeApplication application = (SimsMeApplication) getApplication();
                    final MessageController messageController = application.getMessageController();
                    final Message message = messageController.getMessageById(id);

                    mCitatedChatItem = ConvertToChatItemVOTask.getChatItemVO(message,
                            application,
                            application.getSingleChatController(),
                            application.getChannelController(), 1);
                    if (mCitatedChatItem != null) {
                        imageListContainer.setVisibility(View.GONE);
                    }
                }
            }

            final MessageDestructionParams destructionParams = intent.hasExtra(EXTRA_DESTRUCTION_PARAMS)
                    ? SystemUtil.dynamicDownCast(intent.getSerializableExtra(EXTRA_DESTRUCTION_PARAMS), MessageDestructionParams.class)
                    : null;
            final Date timerDate = intent.hasExtra(EXTRA_TIMER)
                    ? SystemUtil.dynamicDownCast(intent.getSerializableExtra(PreviewActivity.EXTRA_TIMER), Date.class)
                    : null;

            final boolean mode = destructionParams == null && timerDate != null;

            if (timerDate != null || destructionParams != null) {
                showSelfdestructionFragment(mode, destructionParams, timerDate);
            }

            mPreviewAction = intent.getIntExtra(EXTRA_PREVIEW_ACTION, SELECT_PHOTOS_ACTION);

            if (uris != null) {
                showIdleDialog();
                CopyTask ct = new CopyTask(getSimsMeApplication(), new GenericActionListener<ArrayList<String>>() {
                    @Override
                    public void onSuccess(ArrayList<String> internalUris) {
                        dismissIdleDialog();

                        if (internalUris == null || internalUris.size() <= 0) {
                            return;
                        }

                        for (int i = 0; i < internalUris.size(); i++) {
                            final String uri = internalUris.get(i);
                            String text = "";
                            if (texts != null && texts.size() > i) {
                                text = texts.get(i);
                            }
                            if (!addUri(uri, text)) {
                                break;
                            }
                        }

                        if (mModels != null) {
                            ImageModel model = mModels.get(mModels.size() - 1);
                            if (!StringUtil.isNullOrEmpty(model.text)) {
                                if (mChatInputFragment.getEditText() != null) {
                                    mChatInputFragment.setChatInputText(model.text, true);
                                } else {
                                    mChatInputFragment.setStartText(model.text);
                                }
                            }
                        }

                        updateUI();
                    }

                    @Override
                    public void onFail(String message, String errorIdent) {
                        dismissIdleDialog();
                        Toast.makeText(getSimsMeApplication(), R.string.chats_addAttachments_some_imports_fails, Toast.LENGTH_LONG).show();
                        finish();
                    }
                });

                ct.executeOnExecutor(COPY_SERIAL_EXECUTOR, uris.size() > 10 ? uris.subList(0, 9) : uris);
            }

            mShowAddButton = intent.getBooleanExtra(EXTRA_SHOW_ADD_BUTTON, true);

            mAdapter = new ThumbnailAdapter();

            mThumbnailRecyclerView.setAdapter(mAdapter);

            mPreviewPagerAdapter = new PreviewPagerAdapter(getSupportFragmentManager());
            if (mModels.size() > 0) {
                mCurrentPosition = mModels.size() - 1;
            }
            mViewPager = findViewById(R.id.preview_pager);

            mViewPager.setAdapter(mPreviewPagerAdapter);
            if (mCurrentPosition > -1) {
                mViewPager.setCurrentItem(mCurrentPosition);
            }

            mViewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {

                @Override
                public void onPageScrollStateChanged(final int state) {
                    if (state == SCROLL_STATE_TOUCH_SCROLL) {
                        if (mModels != null && mModels.size() > mCurrentPosition) {
                            ImageModel model = mModels.get(mCurrentPosition);
                            if (model != null) {
                                model.text = mChatInputFragment.getChatInputText();
                            }
                        }
                    }
                }

                @Override
                public void onPageSelected(final int position) {
                    mCurrentPosition = position;

                    final int adapterPos = mAdapter.getAdapterPositionForNormalizedPosition(mCurrentPosition) - (mShowAddButton ? 0 : 1);

                    for (int i = 0; i < mAdapter.getItemCount(); i++) {
                        final ViewHolder vh = (ViewHolder) mThumbnailRecyclerView.findViewHolderForAdapterPosition(i);
                        if (vh != null) {
                            final Boolean selected = adapterPos == i;
                            vh.mBorderView.setSelected(selected);
                            mChatInputFragment.setChatInputText(mModels.get(mCurrentPosition).text, true);
                            mChatInputFragment.requestFocusForInput();
                            if (RuntimeConfig.isBAMandant()) {
                                if (selected) {
                                    vh.mBorderView.getBackground().setColorFilter(ColorUtil.getInstance().getAppAccentColor(getSimsMeApplication()), PorterDuff.Mode.SRC_ATOP);
                                } else {
                                    vh.mBorderView.getBackground().setColorFilter(ColorUtil.getInstance().getNamedColor("actionSecondary", getSimsMeApplication()),
                                            PorterDuff.Mode.SRC_ATOP);
                                }
                            }
                        }
                    }
                    mThumbnailRecyclerView.smoothScrollToPosition(adapterPos);
                }
            });

            //BUG 38637 -  Fragment sollte hier hinzugefuegt werden, damit es eine size erhaelt
            if (mEmojiconsFragment == null) {
                mEmojiconsFragment = new EmojiPickerFragment();
                getSupportFragmentManager().beginTransaction()
                        .add(mFragmentContainerId, mEmojiconsFragment).commit();
            }

            hideOrShowFragment(mEmojiconsFragment, false, false);
        } catch (final LocalizedException e) {
            LogUtil.e(TAG, e.getMessage(), e);
            setResult(RESULT_CANCELED, getIntent());
            finish();
        }
    }

    @Override
    protected void onSaveInstanceState(final Bundle bundle) {
        if (mModels != null) {
            JsonArray ja = new JsonArray();
            for (ImageModel model : mModels) {
                JsonObject jo = model.getAsJsonObject();

                if (jo != null) {
                    ja.add(jo);
                }
            }

            String jaString = ja.toString();

            bundle.putString(PreviewActivity.EXTRA_URIS, jaString);
        }

        super.onSaveInstanceState(bundle);
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_preview;
    }

    @Override
    protected void onResumeActivity() {
        super.onResumeActivity();
        checkDeleteIcon();
    }

    @Override
    public void handleCopyMessageClick(final View view) {
        //Do Nothing
    }

    @Override
    public void handleForwardMessageClick(final View view) {
        //Do Nothing
    }

    @Override
    public void handleForwardMessageImageClick(final View view) {
        //Do Nothing
    }

    @Override
    public void handleDeleteMessageClick(final View view) {
        //Do Nothing
    }

    @Override
    public void handleMessageInfoClick() {
        //Do Nothing
    }

    @Override
    public void handleMessageCommentClick() {
        //Do Nothing
    }

    @Override
    public void handleAddAttachmentClick() {
        //Do Nothing
    }

    @Override
    public void scrollIfLastChatItemIsNotShown() {
        //Do Nothing
    }

    @Override
    public void handleSendVoiceClick(final Uri voiceUri) {
        //Do Nothing
    }

    @Override
    public boolean handleAVCMessageClick(int callType) {
        return true;
    }

    @Override
    public void smoothScrollToEnd() {
        //Do Nothing
    }

    private void checkDeleteIcon() {
        if (mModels != null && mModels.size() > 1) {
            final View.OnClickListener onRightActionBarImageClickListener = new View.OnClickListener() {
                @Override
                public void onClick(final View v) {

                    if (mCurrentPosition > -1 && mCurrentPosition < mModels.size()) {
                        removeUri(mModels.get(mCurrentPosition));

                        mCurrentPosition = Math.max(0, mCurrentPosition - 1);
                        updateUI();
                    }
                }
            };

            setRightActionBarImage(R.drawable.ic_delete_white_24dp,
                    onRightActionBarImageClickListener,
                    getResources().getString(R.string.content_description_send_media_delete), -1);
        } else {
            removeRightActionBarImage();
        }
    }

    private ImageLoader initImageLoader() {
        //Image Loader zum Laden der ChatoverviewItems Icons
        final ImageLoader imageLoader = new ImageLoader(this, 0, false) {
            @Override
            protected Bitmap processBitmap(final Object data) {
                Bitmap thumbnail = null;

                if (data instanceof ThumbnailIdentifier) {
                    final ThumbnailIdentifier identifier = (ThumbnailIdentifier) data;

                    final Uri uri = Uri.parse(identifier.uriString);

                    if ((mPreviewAction == SELECT_PHOTOS_ACTION) || (mPreviewAction == TAKE_PHOTOS_ACTION)) {
                        thumbnail = BitmapUtil.decodeUri(PreviewActivity.this, uri,
                                Math.round(50 * mDensityMultiplier), true);
                    } else if ((mPreviewAction == SELECT_VIDEOS_ACTION) || (mPreviewAction == TAKE_VIDEOS_ACTION)) {
                        final int widthHeight = Math.round(50 * mDensityMultiplier);

                        thumbnail = VideoUtil.getThumbnail(PreviewActivity.this, uri, widthHeight,
                                widthHeight);
                    }
                }

                return thumbnail;
            }

            @Override
            protected void processBitmapFinished(final Object data, final ImageView imageView) {
                //Nothing to do
            }
        };

        // Add a cache to the image loader
        imageLoader.addImageCache(getSupportFragmentManager(), 0.1f);

        //
        imageLoader.setImageFadeIn(false);

        return imageLoader;
    }

    public boolean handleSendMessageClick(final String text) {

        KeyboardUtil.toggleSoftInputKeyboard(PreviewActivity.this, mChatInputFragment.getEditText(), false);

        if (mModels != null && mModels.size() > mCurrentPosition) {
            ImageModel model = mModels.get(mCurrentPosition);
            if (model != null) {
                model.text = mChatInputFragment.getEditText().getText().toString();
            }
        }

        boolean validDate = true;
        final Intent intent = new Intent();
        MessageDestructionParams destructionParams = null;

        if ((mModels == null) || (mModels.size() < 1)) {
            setResult(RESULT_CANCELED, getIntent());
            finish();
            return true;
        }

        String msg = getResources().getString(R.string.chats_selfdestruction_invalid_date);

        if (mSelfdestructionFragment != null) {
            if (mChatInputFragment != null) {
                if (mChatInputFragment.getDestructionEnabled()) {
                    destructionParams = mSelfdestructionFragment.getDestructionConfiguration();
                    if (destructionParams != null && destructionParams.date != null) {
                        if (!destructionParams.date.after(Calendar.getInstance().getTime())) {
                            validDate = false;
                        }
                    }
                    intent.putExtra(EXTRA_DESTRUCTION_PARAMS, destructionParams);
                }

                if (mChatInputFragment.getTimerEnabled()) {
                    if (mSelfdestructionFragment.getTimerDate() != null
                            && !mSelfdestructionFragment.getTimerDate().after(Calendar.getInstance().getTime())) {
                        validDate = false;
                    }
                    final Calendar oneYear = Calendar.getInstance();
                    oneYear.add(Calendar.YEAR, 1);
                    if (mSelfdestructionFragment.getTimerDate() != null && mSelfdestructionFragment.getTimerDate().after(oneYear.getTime())) {
                        validDate = false;
                        msg = getResources().getString(R.string.chats_timedmessage_invalid_date2);
                    }
                    intent.putExtra(EXTRA_TIMER, mSelfdestructionFragment.getTimerDate());
                }
            }
            if (destructionParams != null && destructionParams.date != null
                    && mSelfdestructionFragment.getTimerDate() != null && destructionParams.date.before(mSelfdestructionFragment.getTimerDate())) {
                msg = getResources().getString(R.string.chats_selfdestruction_sddate_before_senddate);
                validDate = false;
            }
        }

        final ArrayList<String> uris = new ArrayList<>(mModels.size());
        final ArrayList<String> texts = new ArrayList<>(mModels.size());

        for (ImageModel model : mModels) {
            uris.add(model.uri);
            texts.add(model.text);
        }

        intent.putStringArrayListExtra(EXTRA_URIS, uris);
        intent.putStringArrayListExtra(EXTRA_TEXTS, texts);

        if (mIsPriority) {
            intent.putExtra(EXTRA_IS_PRIORITY, true);
        }

        if (validDate) {
            setResult(RESULT_OK, intent);
            finish();
        } else {
            mChatInputFragment.setTypingState();
            DialogBuilderUtil.buildErrorDialog(this, msg).show();
        }
        return validDate;
    }

    public void showDatePickerDialog(final View view) {
        if (mSelfdestructionFragment != null) {
            final DatePickerFragment newFragment = new DatePickerFragment();

            newFragment.setFragment(mSelfdestructionFragment);
            newFragment.show(getFragmentManager(), "datePicker");
        }
    }

    public void showTimePickerDialog(final View view) {
        if (mSelfdestructionFragment != null) {
            final TimePickerFragment newFragment = new TimePickerFragment();

            newFragment.setFragment(mSelfdestructionFragment);
            newFragment.show(getFragmentManager(), "timePicker");
        }
    }

    public void onThumbnailClick(final View view) {
        if (mModels != null && mModels.size() > mCurrentPosition) {
            ImageModel im = mModels.get(mCurrentPosition);
            if (im != null) {
                im.text = mChatInputFragment.getEditText().getText().toString();
            }
        }

        final ViewParent parent = view.getParent();

        if ((parent instanceof View)) {
            final int pos = mAdapter.getNormalizedPositionForView((View) parent, mThumbnailRecyclerView) - (mShowAddButton ? 0 : 1);

            for (int i = 0; i < mAdapter.getItemCount(); i++) {
                final ViewHolder vh = (ViewHolder) mThumbnailRecyclerView.findViewHolderForAdapterPosition(i);

                if (vh != null) {
                    vh.mBorderView.setSelected(false);
                    if (RuntimeConfig.isBAMandant()) {
                        vh.mBorderView.getBackground().setColorFilter(ColorUtil.getInstance().getNamedColor("actionSecondary", getSimsMeApplication()),
                                PorterDuff.Mode.SRC_ATOP);
                    }
                }
            }

            if (pos == (mAdapter.getItemCount() - 1) && mShowAddButton) {
                if (mModels.size() != MAX_MEDIA_ITEMS) {
                    startActionIntent();
                }
            } else {
                view.setSelected(true);
                mCurrentPosition = pos;
                mViewPager.setCurrentItem(pos);
                if (mModels.size() > mCurrentPosition) {
                    ImageModel model = mModels.get(mCurrentPosition);
                    if (model != null) {
                        mChatInputFragment.setChatInputText(model.text != null ? model.text : "", true);
                    }
                }
            }
        }
    }

    private void startActionIntent() {
        switch (mPreviewAction) {
            case SELECT_PHOTOS_ACTION: {
                startSelectPhotosAction();
                break;
            }
            case SELECT_VIDEOS_ACTION: {
                startSelectVideosAction();
                break;
            }
            case TAKE_PHOTOS_ACTION: {
                startTakePhotoAction();
                break;
            }
            case TAKE_VIDEOS_ACTION: {
                startTakeVideoAction();
                break;
            }
            default: {
                LogUtil.w(TAG, LocalizedException.UNDEFINED_ARGUMENT);
                break;
            }
        }
    }

    //@SuppressLint("NewApi")
    private void startSelectPhotosAction() {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
            requestPermission(PermissionUtil.PERMISSION_FOR_READ_EXTERNAL_STORAGE,
                    R.string.permission_rationale_read_external_storage,
                    new PermissionUtil.PermissionResultCallback() {
                        @Override
                        public void permissionResult(int permission, boolean permissionGranted) {
                            if (permission == PermissionUtil.PERMISSION_FOR_READ_EXTERNAL_STORAGE && permissionGranted) {
                                startSelectPhotoIntent();
                            }
                        }
                    });
        } else {
            startSelectPhotoIntent();
        }
    }

    private void startSelectPhotoIntent() {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);

        intent.setType("image/*");

        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        router.startExternalActivityForResult(intent, SELECT_PHOTOS_ACTION);
    }

    private void startSelectVideosAction() {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)) {
            requestPermission(PermissionUtil.PERMISSION_FOR_READ_EXTERNAL_STORAGE,
                    R.string.permission_rationale_read_external_storage,
                    new PermissionUtil.PermissionResultCallback() {
                        @Override
                        public void permissionResult(int permission, boolean permissionGranted) {
                            if (permission == PermissionUtil.PERMISSION_FOR_READ_EXTERNAL_STORAGE && permissionGranted) {
                                startSelectVideoIntent();
                            }
                        }
                    });
        } else {
            startSelectVideoIntent();
        }
    }

    private void startSelectVideoIntent() {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);

        intent.setType("video/*");

        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        router.startExternalActivityForResult(intent, SELECT_VIDEOS_ACTION);
    }

    private void startTakePhotoAction() {
        requestPermission(PermissionUtil.PERMISSION_FOR_CAMERA, R.string.permission_rationale_camera, new PermissionUtil.PermissionResultCallback() {
            @Override
            public void permissionResult(final int permission, final boolean permissionGranted) {
                if (permission == PermissionUtil.PERMISSION_FOR_CAMERA && permissionGranted) {
                    final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                    try {
                        final FileUtil fu = new FileUtil(getSimsMeApplication());
                        final File takenPhotoFile = fu.createTmpImageFileAddInIntent(intent);
                        mTakePhotoUri = Uri.fromFile(takenPhotoFile);
                        router.startExternalActivityForResult(intent, TAKE_PHOTOS_ACTION);
                    } catch (final LocalizedException e) {
                        LogUtil.w(TAG, e.getMessage(), e);
                    }
                }
            }
        });
    }

    private void startTakeVideoAction() {
        requestPermission(PermissionUtil.PERMISSION_FOR_VIDEO, R.string.permission_rationale_camera, new PermissionUtil.PermissionResultCallback() {
            @Override
            public void permissionResult(final int permission, final boolean permissionGranted) {
                if (permission == PermissionUtil.PERMISSION_FOR_VIDEO && permissionGranted) {
                    final Intent intent = new Intent(PreviewActivity.this, CameraActivity.class);

                    startActivityForResult(intent, TAKE_VIDEOS_ACTION);
                }
            }
        });
    }

    @Override
    protected void onActivityPostLoginResult(final int requestCode,
                                             final int resultCode,
                                             final Intent returnIntent) {
        super.onActivityPostLoginResult(requestCode, resultCode, returnIntent);

        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case SELECT_PHOTOS_ACTION: {
                    final FileUtil fileUtil = new FileUtil(this);

                    final FileUtil.UrisResultContainer resultContainer = fileUtil.getUrisFromImageActionIntent(returnIntent);

                    handleResult(resultContainer);
                    break;
                }
                case SELECT_VIDEOS_ACTION: {
                    final FileUtil fileUtil = new FileUtil(this);

                    final FileUtil.UrisResultContainer resultContainer = fileUtil.getUrisFromVideoActionIntent(returnIntent);

                    handleResult(resultContainer);
                    break;
                }
                case TAKE_VIDEOS_ACTION: {
                    final String path = returnIntent.getStringExtra("data");

                    if (path != null) {
                        final Uri recordedVideo = Uri.fromFile(new File(path));
                        addUri(recordedVideo.toString(), null);
                        updateUI();
                    }
                    break;
                }
                case TAKE_PHOTOS_ACTION: {
                    if (mTakePhotoUri != null) {
                        Uri takenPhoto = null;

                        try {
                            takenPhoto = (new FileUtil(this)).copyFileToInternalDir(mTakePhotoUri);
                        } catch (final LocalizedException e) {
                            LogUtil.e(TAG, e.getMessage(), e);
                        }

                        if (takenPhoto != null) {
                            addUri(takenPhoto.toString(), null);

                            updateUI();
                        }
                    }
                    break;
                }
                default: {
                    LogUtil.w(TAG, LocalizedException.UNDEFINED_ARGUMENT);
                    break;
                }
            }
        }
    }

    private void handleResult(final FileUtil.UrisResultContainer resultContainer) {
        final ArrayList<String> uris = resultContainer.getUris();

        if ((uris != null)
                && ((uris.size() + mAdapter.getItemCount() - 1) > MAX_MEDIA_ITEMS)) {
            Toast.makeText(this, getString(R.string.chats_addAttachments_too_many), Toast.LENGTH_LONG).show();
        } else if (resultContainer.getHasImportError()) {
            Toast.makeText(this, getString(R.string.chats_addAttachments_some_imports_fails), Toast.LENGTH_LONG).show();
        } else if (resultContainer.getHasFileTooLargeError()) {
            Toast.makeText(this, getString(R.string.chats_addAttachment_too_big), Toast.LENGTH_LONG).show();
        }

        if ((uris != null) && (uris.size() > 0)) {
            showIdleDialog();
            CopyTask ct = new CopyTask(getSimsMeApplication(), mCopyListener);
            ct.executeOnExecutor(COPY_SERIAL_EXECUTOR, uris);
        }
    }

    private void updateUI() {
        if (mModels == null) {
            return;
        }
        mCurrentPosition = Math.max(0, mModels.size() - 1);
        mAdapter.notifyDataSetChanged();
        mPreviewPagerAdapter.notifyDataSetChanged();
        mViewPager.setCurrentItem(mCurrentPosition);

        if (mCurrentPosition < mModels.size()) {
            ImageModel model = mModels.get(mCurrentPosition);
            if (StringUtil.isNullOrEmpty(model.text)) {
                mChatInputFragment.setChatInputText(model.text, true);
            }
        }

        checkDeleteIcon();
    }

    private boolean addUri(final String uri, final String text) {
        if (mModels == null) {
            mModels = new ArrayList<>();
        }

        return mModels.size() < MAX_MEDIA_ITEMS && !StringUtil.isNullOrEmpty(uri) && mModels.add(new ImageModel(uri, text));
    }

    private void removeUri(final ImageModel model) {
        if (mModels == null || model == null) {
            return;
        }

        mModels.remove(model);
        mPreviewPagerAdapter = new PreviewPagerAdapter(getSupportFragmentManager());
        mViewPager.setAdapter(mPreviewPagerAdapter);
        checkDeleteIcon();
    }

    @Override
    public void onEmojiSelected(@NotNull String unicode) {
        mChatInputFragment.appendText(unicode);
    }

    @Override
    public void onBackSpaceSelected() {
        TextExtensionsKt.backspace(mChatInputFragment.getEditText());
    }

    @Override
    public void onBackPressed() {
        if ((mChatInputFragment != null) && (mChatInputFragment.getEmojiEnabled())) {
            mChatInputFragment.showEmojiPicker(false);
            KeyboardUtil.toggleSoftInputKeyboard(this, mChatInputFragment.getEditText(), true);
        } else if ((mChatInputFragment != null) && (mChatInputFragment.isDestructionViewShown())) {
            mChatInputFragment.closeDestructionPicker(true);
        } else {
            try {
                FileUtil fu = new FileUtil(this);
                fu.deleteAllFilesInDir(fu.getInternalMediaDir());
            } catch (Exception e) {
                LogUtil.w(TAG, e.getMessage(), e);
            }
            super.onBackPressed();
        }
    }

    @Override
    public boolean canSendMedia() {
        return getSimsMeApplication().getPreferencesController().canSendMedia();
    }


    // Private classes ...

    private static class ViewHolder
            extends RecyclerView.ViewHolder {

        private final ImageView mThumbnailView;
        private final View mBorderView;

        private ViewHolder(final View itemView) {
            super(itemView);

            mThumbnailView = itemView.findViewById(R.id.preview_thumbnail_image);
            mBorderView = itemView.findViewById(R.id.preview_thumbnail_border);
        }
    }

    public static class PreviewObjectFragment
            extends Fragment implements Player.Listener {
        private static final int VIDEO_DELAY = 200;
        private StyledPlayerView mVideoView;
        private SimpleExoPlayer mVideoPlayer;
        private Uri mVideoUri;
        private int mVideoPlayTries;

        @Override
        public View onCreateView(final LayoutInflater inflater,
                                 final ViewGroup container,
                                 final Bundle savedInstanceState) {
            final Bundle args = getArguments();
            final String previewUri = args.getString(PREVIEW_URI);
            final int action = args.getInt(ACTION_TYPE);

            if ((action == SELECT_PHOTOS_ACTION) || (action == TAKE_PHOTOS_ACTION)) {
                final PhotoView imageView = new PhotoView(getActivity());
                imageView.setMaximumScale(6.0f);
                imageView.setAdjustViewBounds(true);

                final DisplayMetrics metrics = MetricsUtil.getDisplayMetrics(getActivity());

                final Bitmap previewBitmap = BitmapUtil.decodeUri(getActivity(), Uri.parse(previewUri), metrics.widthPixels,
                        true);

                if (previewBitmap != null) {
                    imageView.setImageBitmap(previewBitmap);
                }

                return imageView;
            } else if ((action == SELECT_VIDEOS_ACTION) || (action == TAKE_VIDEOS_ACTION)) {
                final View v = inflater.inflate(R.layout.fragment_imageview_helper_layout, container, false);
                final LinearLayout linearLayout = v.findViewById(R.id.imageview_helper_main_layout);

                if (previewUri != null && previewUri.startsWith("content")) {
                    mVideoUri = Uri.parse(previewUri);
                } else {
                    final Uri path = Uri.parse(previewUri);

                    mVideoUri = Uri.parse(VideoProvider.CONTENT_URI_BASE + path.getPath());
                }

                mVideoView = linearLayout.findViewById(R.id.videoView);
                mVideoPlayer  = new SimpleExoPlayer.Builder(getActivity()).build();
                mVideoView.setPlayer(mVideoPlayer);

                MediaItem mediaItem = MediaItem.fromUri(mVideoUri);
                mVideoPlayer.setMediaItem(mediaItem);
                mVideoPlayer.prepare();
                mVideoPlayer.setPlayWhenReady(false);
                mVideoView.setControllerHideOnTouch(true);
                mVideoView.setControllerAutoShow(true);

                mVideoPlayer.addListener(this);

                mVideoPlayTries = 0;

                return linearLayout;
            }

            return new LinearLayout(null);
        }

        private void releasePlayers() {
            if (mVideoPlayer != null) {
                mVideoPlayer.release();
            }
            mVideoPlayer = null;
        }

        @Override
        public void onDestroy() {
            releasePlayers();
            super.onDestroy();
        }

        @Override
        public void onResume() {
            super.onResume();
        }

        @Override
        public void onPause() {
            if (mVideoPlayer != null) {
                mVideoPlayer.pause();
            }
            super.onPause();
        }

        /* KS: Deprecated! Code moved to onPause() and onResume().
        @Override
        public void setUserVisibleHint(final boolean isVisibleToUser) {
            if (isVisibleToUser) {
                if (mVideoView != null) {
                    mVideoView.setVideoURI(mVideoUri);
                    if (mMediaController == null) {
                        mMediaController = new MediaController(getActivity());
                    }
                    mVideoView.seekTo(1);
                    mMediaController.hide();
                }
            } else {
                if (mVideoView != null) {
                    mVideoView.stopPlayback();
                }

                if ((mMediaController != null)) {
                    mMediaController.hide();
                }
            }

            super.setUserVisibleHint(isVisibleToUser);
        }
         */
    }

    private static class CopyTask extends AsyncTask<List, Void, ArrayList<String>> {
        private final SimsMeApplication mApplication;
        private final GenericActionListener<ArrayList<String>> mListener;
        private boolean mError;

        CopyTask(@NonNull final SimsMeApplication application, GenericActionListener<ArrayList<String>> listener) {
            mApplication = application;
            mListener = listener;
        }

        @Override
        protected ArrayList<String> doInBackground(List... arrayLists) {
            if (arrayLists == null || arrayLists.length < 1) {
                return null;
            }

            FileUtil fu = new FileUtil(mApplication);
            List uris = arrayLists[0];
            ArrayList<String> internalUris = new ArrayList<>(uris.size());

            for (final Object uriObject : uris) {
                try {
                    if (uriObject instanceof String) {
                        Uri internalUri = fu.copyFileToInternalDir(Uri.parse((String) uriObject), fu.getInternalMediaDir());
                        if (internalUri != null) {
                            internalUris.add(internalUri.toString());
                        }
                    }
                } catch (LocalizedException e) {
                    mError = true;
                }
            }

            return internalUris;
        }

        @Override
        protected void onPostExecute(ArrayList<String> internalUris) {
            if (internalUris == null || internalUris.size() <= 0 || mError) {
                if (mListener != null) {
                    mListener.onFail("", "");
                }
            }

            if (mListener != null) {
                mListener.onSuccess(internalUris);
            }
        }
    }

    private static class ThumbnailIdentifier {
        private final String uriString;

        private ThumbnailIdentifier(final String uriString) {
            this.uriString = uriString;
        }
    }

    private class ThumbnailAdapter
            extends RecyclerView.Adapter<ViewHolder> {

        @Override
        public @NotNull ViewHolder onCreateViewHolder(final ViewGroup parent,
                                                      final int viewType) {
            // create a new view
            final RelativeLayout v = (RelativeLayout) LayoutInflater.from(parent.getContext()).inflate(R.layout.preview_thumbnail_view,
                    parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(final @NotNull ViewHolder holder,
                                     int aPosition) {
            //position wird umgedreht, da wir eine RTL Befuellung haben
            final int position = -aPosition + (mModels.size() - (mShowAddButton ? 0 : 1));

            if (position == mModels.size()) {
                holder.mThumbnailView.setScaleType(ImageView.ScaleType.CENTER);
                holder.mThumbnailView.setImageResource(R.drawable.chat_add);
                if (mModels.size() == MAX_MEDIA_ITEMS) {
                    holder.mThumbnailView.setImageResource(R.drawable.ic_dnd_on_black_24dp);
                } else {
                    holder.mThumbnailView.setImageResource(R.drawable.chat_add);
                }
            } else {
                final boolean selected = mCurrentPosition == position;
                holder.mBorderView.setSelected(selected);
                if (RuntimeConfig.isBAMandant()) {
                    if (selected) {
                        holder.mBorderView.getBackground().setColorFilter(ColorUtil.getInstance().getAppAccentColor(getSimsMeApplication()), PorterDuff.Mode.SRC_ATOP);
                    } else {
                        holder.mBorderView.getBackground().setColorFilter(ColorUtil.getInstance().getNamedColor("actionSecondary", getSimsMeApplication()),
                                PorterDuff.Mode.SRC_ATOP);
                    }
                }
                mImageLoader.loadImage(new ThumbnailIdentifier(mModels.get(position).uri), holder.mThumbnailView);
            }
        }

        @Override
        public int getItemCount() {
            return (mModels != null ? mModels.size() : 0) + (mShowAddButton ? 1 : 0);
        }

        private int getNormalizedPositionForView(final View view,
                                                 final RecyclerView recyclerView) {
            int position = recyclerView.getChildAdapterPosition(view);

            position = -position + mModels.size();

            return position;
        }

        private int getAdapterPositionForNormalizedPosition(final int position) {
            return -position + mModels.size();
        }
    }

    private class PreviewPagerAdapter
            extends FragmentStatePagerAdapter {
        private PreviewPagerAdapter(final FragmentManager fm) {
            super(fm, FragmentStatePagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @Override
        public @NotNull Fragment getItem(final int i) {
            final Fragment fragment = new PreviewObjectFragment();

            final Bundle args = new Bundle();

            args.putString(PREVIEW_URI, mModels.get(i).uri);
            args.putInt(ACTION_TYPE, mPreviewAction);

            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public int getCount() {
            return mModels.size();
        }
    }

    private static class ImageModel {
        String uri;
        String text;

        ImageModel() {
        }

        ImageModel(final String uri, final String text) {
            this.uri = uri;
            this.text = text;
        }

        JsonObject getAsJsonObject() {
            JsonObject jo = new JsonObject();

            if (!StringUtil.isNullOrEmpty(uri)) {
                jo.addProperty("uri", uri);
            }

            if (!StringUtil.isNullOrEmpty(text)) {
                jo.addProperty("text", text);
            }

            return jo;
        }

        boolean fillModelFromJsonObject(@NonNull final JsonObject jo) {
            String tmpUri = JsonUtil.stringFromJO("uri", jo);
            String tmpText = JsonUtil.stringFromJO("text", jo);

            if (StringUtil.isNullOrEmpty(tmpUri)) {
                return false;
            }

            text = tmpText;
            uri = tmpUri;

            return true;
        }
    }
}
