// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.*;

import androidx.appcompat.widget.Toolbar;

import com.github.chrisbanes.photoview.PhotoView;

import eu.ginlo_apps.ginlo.BaseActivity;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.exception.LocalizedException;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.*;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;
import eu.ginlo_apps.ginlo.view.VideoViewCustom;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class ViewAttachmentActivity
        extends BaseActivity implements OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener {

    private final static String TAG = ViewAttachmentActivity.class.getSimpleName();

    public static final String EXTRA_BITMAP_URI = "AttachmentActivity.imageData";
    public static final String EXTRA_VIDEO_URI = "AttachmentActivity.videoUri";
    public static final String EXTRA_VOICE_URI = "AttachmentActivity.voiceUri";
    public static final String EXTRA_ATTACHMENT_GUID = "AttachmentActivity.atachementGuid";
    public static final String EXTRA_ATTACHMENT_DESCRIPTION = "AttachmentActivity.AttachmentDescription";
    private static final int HANDLER_TIME = 3000;

    private PhotoView imageView;
    private VideoViewCustom videoView;
    private Uri videoUri;
    private Bitmap bitmap;
    private Uri voiceUri;
    private MediaController mMediaController;
    private TextView mAttachmentDescriptionView;
    private boolean mHasAttachmentDesc;
    private AudioPlayer mAudioPlayer;

    @Override
    protected void onCreateActivity(final Bundle savedInstanceState) {
        try {
            final Intent intent = getIntent();

            final String attachmentIdentTag = intent.hasExtra(EXTRA_ATTACHMENT_GUID)
                    ? ChecksumUtil.getMD5ChecksumForString(intent.getStringExtra(EXTRA_ATTACHMENT_GUID))
                    : null;

            imageView = findViewById(R.id.attachment_image_view);
            imageView.setZoomable(true);
            imageView.setMaximumScale(6.0f);
            videoView = findViewById(R.id.attachment_video_view);
            final ScrollView scrollView = findViewById(R.id.attachment_description_scrollview);
            final PreferencesController preferencesController = ((SimsMeApplication) getApplication()).getPreferencesController();

            mAttachmentDescriptionView = findViewById(R.id.view_attachment_description);

            final String imageUriString = intent.getStringExtra(EXTRA_BITMAP_URI);
            final String videoUriString = intent.getStringExtra(EXTRA_VIDEO_URI);
            final String voiceUriString = intent.getStringExtra(EXTRA_VOICE_URI);

            final String attachmentDescription = intent.getStringExtra(EXTRA_ATTACHMENT_DESCRIPTION);

            if (!StringUtil.isNullOrEmpty(attachmentDescription)) {
                mAttachmentDescriptionView.setVisibility(View.VISIBLE);
                mAttachmentDescriptionView.setText(attachmentDescription);
                mHasAttachmentDesc = true;
            }

            if (imageUriString != null) {
                final Uri imageUri = Uri.parse(imageUriString);
                LogUtil.d(TAG, "Preparing for image display: " + imageUri);

                bitmap = BitmapUtil.decodeUri(this, imageUri, 0, true);
                imageView.setImageBitmap(bitmap);

                videoView.setVisibility(View.GONE);

                final View.OnTouchListener imageOtl;
                if (mHasAttachmentDesc) {
                    imageOtl = new View.OnTouchListener() {
                        private final float distThreshold = MetricsUtil.dpToPx(ViewAttachmentActivity.this, 5);
                        private float yVal;

                        @Override
                        public boolean onTouch(final View v, final MotionEvent event) {
                            switch(event.getAction()) {
                                case KeyEvent.ACTION_DOWN:
                                    yVal = event.getY();
                                    break;
                                case KeyEvent.ACTION_UP:
                                    final float dist = Math.abs(event.getY() - yVal);
                                    if (dist <= distThreshold) {
                                        final boolean isVisible = mAttachmentDescriptionView.getVisibility() == View.VISIBLE;

                                        if (isVisible) {
                                            mAttachmentDescriptionView.startAnimation(mAnimationSlideOut);
                                        } else {
                                            mAttachmentDescriptionView.setVisibility(View.VISIBLE);
                                            mAttachmentDescriptionView.startAnimation(mAnimationSlideIn);
                                        }
                                    }
                                    break;
                                default:
                                    LogUtil.w(TAG, "No action defined here for key event. Pass it on.");
                            }
                            imageView.dispatchTouchEvent(event);
                            return false;
                        }
                    };
                } else {
                    imageOtl = new View.OnTouchListener() {

                        @Override
                        public boolean onTouch(final View v, final MotionEvent event) {
                            imageView.dispatchTouchEvent(event);
                            return false;
                        }
                    };
                }
                scrollView.setOnTouchListener(imageOtl);
            } else if (videoUriString != null) {
                videoUri = Uri.parse(videoUriString);
                LogUtil.d(TAG, "Preparing for video playback: " + videoUri);

                final Uri uri = Uri.parse(VideoProvider.CONTENT_URI_BASE + videoUri.getPath());
                mMediaController = new MediaController(this);
                videoView.setOnPreparedListener(this);
                videoView.setOnCompletionListener(this);
                videoView.setOnErrorListener(this);
                videoView.setVideoURI(uri);
                videoView.setMediaController(mMediaController);

                imageView.setVisibility(View.GONE);

                final View.OnTouchListener videoOtl = new View.OnTouchListener() {

                    private final float distThreshold = MetricsUtil.dpToPx(ViewAttachmentActivity.this, 5);
                    private float yVal;

                    @Override
                    public boolean onTouch(final View v, final MotionEvent event) {

                        switch(event.getAction()) {
                            case KeyEvent.ACTION_DOWN:
                                yVal = event.getY();
                                break;
                            case KeyEvent.ACTION_UP:
                                final float dist = Math.abs(event.getY() - yVal);
                                if (dist <= distThreshold) {
                                    // It is not a swipe, but a 'click', so allow for underlying views to process it.
                                    videoView.dispatchTouchEvent(event);
                                    mMediaController.dispatchTouchEvent(event);
                                    return true;
                                }
                                break;
                            default:
                                LogUtil.w(TAG, "No action defined here for key event.");
                        }
                        return false;
                    }
                };
                scrollView.setOnTouchListener(videoOtl);

                final int paddingLR = MetricsUtil.dpToPx(this, 8);
                final int paddingBottom = MetricsUtil.dpToPx(this, 100);
                mAttachmentDescriptionView.setPadding(paddingLR, 0, paddingLR, paddingBottom);

                videoView.setPlayPauseListener(new VideoViewCustom.PlayPauseListener() {
                    @Override
                    public void onPlay() {
                        if(mHasAttachmentDesc) {
                            mAttachmentDescriptionView.setVisibility(View.GONE);
                        }
                        getToolbar().setVisibility(View.GONE);
                    }

                    @Override
                    public void onPause() {
                        if(mHasAttachmentDesc) {
                            mAttachmentDescriptionView.setVisibility(View.VISIBLE);
                        }
                        getToolbar().setVisibility(View.VISIBLE);
                    }
                });
            } else if (voiceUriString != null) {
                voiceUri = Uri.parse(voiceUriString);
                LogUtil.d(TAG, "Preparing for audio playback: " + voiceUri);

                final Bitmap waveform = AudioUtil.getPlaceholder(this);  // AudioUtil.getWaveformFromLevels(AudioUtil.getLevels(this,

                imageView.setImageBitmap(waveform);
                imageView.setScaleType(ImageView.ScaleType.CENTER);
                mAudioPlayer = new AudioPlayer(voiceUri, this);
                mAudioPlayer.play();
                videoView.setVisibility(View.GONE);
            }

            final DialogInterface.OnClickListener doNothing = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface arg0,
                                    final int arg1) {
                }
            };

            final DialogInterface.OnClickListener saveMediaHandler = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface arg0,
                                    final int arg1) {
                    saveMedia(attachmentIdentTag, true);
                }
            };

            if (!preferencesController.isSaveMediaToCameraRollDisabled() && preferencesController.isOpenInAllowed()) {
                setRightActionBarImage(R.drawable.ic_add_to_photos_white_24dp, new OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        final String title = ViewAttachmentActivity.this.getResources().getString(R.string.dialog_saveToGallery_title);
                        final String body = ViewAttachmentActivity.this.getResources().getString(R.string.dialog_saveToGallery_body);
                        final AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialog(ViewAttachmentActivity.this,
                                body, title,
                                saveMediaHandler,
                                doNothing);
                        dialog.show();
                    }
                }, getResources().getString(R.string.content_description_view_attachment_save), -1);
            }

            if (preferencesController.getSaveMediaToGallery()) {
                saveMedia(attachmentIdentTag, false);
            }

            mAnimationSlideIn = new AlphaAnimation(0, 1);
            mAnimationSlideIn.setDuration(500);

            mAnimationSlideOut = new AlphaAnimation(1, 0);
            mAnimationSlideOut.setDuration(500);

            mAnimationSlideOut.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(final Animation animation) {

                }

                @Override
                public void onAnimationEnd(final Animation animation) {
                    mAttachmentDescriptionView.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationRepeat(final Animation animation) {

                }
            });
        } catch (final LocalizedException e) {
            finish();
        }
    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_attachment;
    }

    @Override
    protected void onResumeActivity() {
        if (mAudioPlayer != null) {
            try {
                mAudioPlayer.play();
            } catch (final LocalizedException e) {
                LogUtil.e(TAG, e.getMessage());
            }
        }
    }

    @Override
    public void onPauseActivity() {
        if (mAudioPlayer != null) {
            mAudioPlayer.pause();
        }
        super.onPauseActivity();
    }

    @Override
    protected void onDestroy() {
        if (mAudioPlayer != null) {
            mAudioPlayer.release();
        }
        super.onDestroy();
    }

    private void saveMedia(final String attachmentIdentTag, final boolean showToast) {
        requestPermission(PermissionUtil.PERMISSION_FOR_WRITE_EXTERNAL_STORAGE, R.string.permission_rationale_write_external_storage, new PermissionUtil.PermissionResultCallback() {
            @Override
            public void permissionResult(final int permission, final boolean permissionGranted) {
                if (permission == PermissionUtil.PERMISSION_FOR_WRITE_EXTERNAL_STORAGE && permissionGranted) {
                    if (bitmap != null) {
                        final FileUtil fileUtil = new FileUtil(ViewAttachmentActivity.this);
                        OutputStream out = null;
                        final File tmp;

                        try {
                            tmp = fileUtil.getTempFile();
                            out = new FileOutputStream(tmp);
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
                            out.flush();

                            fileUtil.savePhoto(Uri.fromFile(tmp), attachmentIdentTag);

                            if (showToast) {
                                Toast.makeText(ViewAttachmentActivity.this, R.string.dialog_saveToGallery_bitmap_finished, Toast.LENGTH_LONG).show();
                            }
                        } catch (final IOException e) {
                            LogUtil.e(TAG, e.getMessage(), e);
                        } finally {
                            StreamUtil.closeStream(out);
                        }
                    } else if (videoUri != null) {
                        (new FileUtil(ViewAttachmentActivity.this)).saveVideo(videoUri, attachmentIdentTag);

                        if (showToast) {
                            Toast.makeText(ViewAttachmentActivity.this, R.string.dialog_saveToGallery_video_finished, Toast.LENGTH_LONG).show();
                        }
                    } else if (voiceUri != null) {
                        (new FileUtil(ViewAttachmentActivity.this)).saveVoice(voiceUri, attachmentIdentTag);

                        if (showToast) {
                            Toast.makeText(ViewAttachmentActivity.this, R.string.dialog_saveToGallery_voice_finished, Toast.LENGTH_LONG).show();
                        }
                    }
                }
            }
        });
    }

    @Override
    public void onPrepared(final MediaPlayer mp) {
        LogUtil.d(TAG, "MediaPlayer onPrepared().");
        mp.start();

        if (HANDLER_TIME < videoView.getDuration()) {
            final Handler handler = new Handler();
            final Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    if (mHasAttachmentDesc) {
                        mAttachmentDescriptionView.setVisibility(View.GONE);
                    }
                    getToolbar().setVisibility(View.GONE);

                }
            };
            handler.postDelayed(runnable, HANDLER_TIME);
        }

        if (mMediaController != null) {
            mMediaController.show();
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        LogUtil.d(TAG, "MediaPlayer onCompletion().");
        if (mHasAttachmentDesc) {
            mAttachmentDescriptionView.setVisibility(View.VISIBLE);
        }
        getToolbar().setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        LogUtil.e(TAG, "Errorcode (" + what + ", " + extra + ") from MediaPlayer! Exiting.");
        finish();
        return false;
    }
}

