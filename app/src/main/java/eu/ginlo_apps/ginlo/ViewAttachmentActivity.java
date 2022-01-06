// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.*;

import com.github.chrisbanes.photoview.PhotoView;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.StyledPlayerControlView;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.controller.PreferencesController;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.*;
import eu.ginlo_apps.ginlo.view.AlertDialogWrapper;

public class ViewAttachmentActivity
        extends BaseActivity implements Player.Listener {

    private final static String TAG = ViewAttachmentActivity.class.getSimpleName();

    public static final String EXTRA_BITMAP_URI = "AttachmentActivity.imageData";
    public static final String EXTRA_VIDEO_URI = "AttachmentActivity.videoUri";
    public static final String EXTRA_VOICE_URI = "AttachmentActivity.voiceUri";
    public static final String EXTRA_ATTACHMENT_GUID = "AttachmentActivity.atachementGuid";
    public static final String EXTRA_ATTACHMENT_DESCRIPTION = "AttachmentActivity.AttachmentDescription";

    public static final String LOCAL_MEDIA_URI_PREF = "ViewAttachmentActivity.LOCAL_MEDIA_URI";
    public static final int LOCAL_MEDIA_URI_ACTIONCODE = 114;

    private PhotoView imageView;
    private Uri imageUri;
    private StyledPlayerView videoView;
    private StyledPlayerControlView audioView;
    private Uri videoUri;
    private Uri voiceUri;
    private TextView mAttachmentDescriptionView;
    private boolean mHasAttachmentDesc;
    private SimpleExoPlayer mAudioPlayer;
    private SimpleExoPlayer mVideoPlayer;
    private StorageUtil mStorageUtil;
    private Uri mMediaDestinationUri = null;
    private String mAttachmentIdentTag = null;

    @Override
    protected void onCreateActivity(final Bundle savedInstanceState) {

        mStorageUtil = new StorageUtil(this.mApplication);
        mMediaDestinationUri = mStorageUtil.getMediaDestinationUri();

        final Intent intent = getIntent();

        mAttachmentIdentTag = intent.hasExtra(EXTRA_ATTACHMENT_GUID)
                ? ChecksumUtil.getMD5ChecksumForString(intent.getStringExtra(EXTRA_ATTACHMENT_GUID))
                : null;

        imageView = findViewById(R.id.attachment_image_view);
        imageView.setZoomable(true);
        imageView.setMaximumScale(6.0f);

        videoView = findViewById(R.id.attachment_video_view);
        audioView = findViewById(R.id.attachment_audio_view);

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

        Point screenSize = new Point();
        getWindowManager().getDefaultDisplay().getSize(screenSize);

        if (imageUriString != null) {
            imageUri = Uri.parse(imageUriString);
            LogUtil.d(TAG, "Preparing for image display: " + imageUri);

            //bitmap = BitmapUtil.decodeUri(this, imageUri, 0, true);
            Bitmap bitmap = BitmapUtil.decodeUri(this, imageUri, screenSize.x, screenSize.y, true);
            imageView.setImageBitmap(bitmap);

            imageView.setVisibility(View.VISIBLE);
            videoView.setVisibility(View.GONE);
            audioView.setVisibility(View.GONE);

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
            LogUtil.d(TAG, "Preparing for video playback: " + videoUriString);

            mVideoPlayer  = new SimpleExoPlayer.Builder(this).build();
            videoView.setPlayer(mVideoPlayer);

            //final Uri uri = Uri.parse(VideoProvider.CONTENT_URI_BASE + videoUri.getPath());
            MediaItem mediaItem = MediaItem.fromUri(videoUri);
            mVideoPlayer.setMediaItem(mediaItem);
            mVideoPlayer.prepare();
            mVideoPlayer.setPlayWhenReady(false);
            videoView.setControllerHideOnTouch(true);
            videoView.setControllerAutoShow(true);

            mVideoPlayer.addListener(this);

            videoView.setVisibility(View.VISIBLE);
            imageView.setVisibility(View.GONE);
            audioView.setVisibility(View.GONE);
            scrollView.setVisibility(View.GONE);

        } else if (voiceUriString != null) {
            voiceUri = Uri.parse(voiceUriString);
            LogUtil.d(TAG, "Preparing for audio playback: " + voiceUri);

            mAudioPlayer  = new SimpleExoPlayer.Builder(this).build();
            audioView.setPlayer(mAudioPlayer);

            MediaItem mediaItem = MediaItem.fromUri(voiceUri);
            mAudioPlayer.setMediaItem(mediaItem);
            mAudioPlayer.prepare();
            mAudioPlayer.play();

            mAudioPlayer.addListener(this);

            audioView.setVisibility(View.VISIBLE);
            imageView.setVisibility(View.GONE);
            videoView.setVisibility(View.GONE);
            scrollView.setVisibility(View.GONE);
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
                saveMedia(true, true);
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

                    if (mMediaDestinationUri == null) {
                        showMediaDirChooser();
                    }
                }
            }, getResources().getString(R.string.content_description_view_attachment_save), -1);
        }

    }

    @Override
    protected int getActivityLayout() {
        return R.layout.activity_attachment;
    }

    @Override
    protected void onResumeActivity() {
        if (preferencesController.getSaveMediaToGallery()) {
            if(mMediaDestinationUri == null) {
                showMediaDirChooser();
                return;
            }
            saveMedia(false, false);
        }
    }

    private void showMediaDirChooser() {
        final DialogInterface.OnClickListener showMediaDirHandler = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface arg0, final int arg1) {
                Intent chooseDirIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                /*
                if (externalFilesDirUri != null) {
                   intent.putExtra("android.provider.extra.INITIAL_URI", externalFilesDirUri);
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    chooseDirIntent.putExtra(DocumentsContract.EXTRA_PROMPT, "Allow Write Permission");
                }
                 */

                chooseDirIntent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                chooseDirIntent.putExtra("android.content.extra.SHOW_ADVANCED", true);
                try {
                    startActivityForResult(chooseDirIntent, LOCAL_MEDIA_URI_ACTIONCODE);
                } catch (android.content.ActivityNotFoundException e) {
                    LogUtil.e(TAG, "onResumeActivity: Could not start dir chooser for LOCAL_MEDIA_URI_ACTIONCODE: " + e.getMessage());
                }
            }
        };

        final String title = ViewAttachmentActivity.this.getResources().getString(R.string.dialog_saveToGallery_title);
        final String body = ViewAttachmentActivity.this.getResources().getString(R.string.dialog_saveToGallery_choose_mediadir);
        final AlertDialogWrapper dialog = DialogBuilderUtil.buildResponseDialog(ViewAttachmentActivity.this,
                body, false, title,
                "Ok", null, showMediaDirHandler, null);
        dialog.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == LOCAL_MEDIA_URI_ACTIONCODE) {
            switch(resultCode) {
                case Activity.RESULT_OK:
                    Uri resultUri = data.getData();
                    LogUtil.d(TAG, "onActivityResult: LOCAL_MEDIA_URI_ACTIONCODE returned " + resultUri);
                    if(resultUri != null) {
                        final int takeFlags = data.getFlags()
                                & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        try {
                            getContentResolver().takePersistableUriPermission(resultUri, takeFlags);
                        } catch (Exception e) {
                            LogUtil.e(TAG, "onActivityResult: Caught exception in takePersistableUriPermission: " + e.getMessage(), e);
                            return;
                        }
                        LogUtil.i(TAG, "onActivityResult: New media destination set to " + resultUri);
                        preferencesController.getSharedPreferences().edit().putString(LOCAL_MEDIA_URI_PREF, resultUri.toString()).apply();
                        mMediaDestinationUri = resultUri;
                    } else {
                        // Uri is null
                        LogUtil.i(TAG, "onActivityResult: Media destination preference removed!");
                        preferencesController.getSharedPreferences().edit().remove(LOCAL_MEDIA_URI_PREF).apply();
                        mMediaDestinationUri = null;
                    }
                    break;
                case Activity.RESULT_CANCELED:
                    // Back pressed? Keep status quo
                    LogUtil.w(TAG, "onActivityResult: Media destination chooser cancelled by user!");
                    mMediaDestinationUri = mStorageUtil.getMediaDestinationUri();
                    break;
                default:
                    LogUtil.w(TAG, "onActivityResult: LOCAL_MEDIA_URI_ACTIONCODE returned with resultCode = " + resultCode);
                    this.onBackPressed();
                    break;
            }
        }
    }

    @Override
    public void onPauseActivity() {
        if (mAudioPlayer != null) {
            mAudioPlayer.pause();
        }
        if (mVideoPlayer != null) {
            mVideoPlayer.pause();
        }
        super.onPauseActivity();
    }

    @Override
    public void onBackPressed() {
        releasePlayers();
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        releasePlayers();
        super.onDestroy();
    }

    private void releasePlayers() {
        if (mAudioPlayer != null) {
            mAudioPlayer.release();
        }
        mAudioPlayer = null;
        if (mVideoPlayer != null) {
            mVideoPlayer.release();
        }
        mVideoPlayer = null;
    }

    private void saveMedia(final boolean showToast, final boolean forceOverwrite) {
        if(mAttachmentIdentTag == null) {
            return;
        }

        requestPermission(PermissionUtil.PERMISSION_FOR_WRITE_EXTERNAL_STORAGE, R.string.permission_rationale_write_external_storage, new PermissionUtil.PermissionResultCallback() {
            @Override
            public void permissionResult(final int permission, final boolean permissionGranted) {
                if (permission == PermissionUtil.PERMISSION_FOR_WRITE_EXTERNAL_STORAGE && permissionGranted) {
                    // KS: It is not possible for the user to save the original image file. For security or other reasons?
                    // For now we use savePhoto() below to ensure that recipients always save the original image content.
                    // KS2 20211211: Rollback, since we have no filename. If original file is requested
                    // users should send as file not as image.
                    if (imageUri != null) {
                        final Bitmap bitmap = BitmapUtil.decodeUri(ViewAttachmentActivity.this, imageUri, 0, true);
                        if (bitmap != null) {
                            final FileUtil fileUtil = new FileUtil(ViewAttachmentActivity.this);
                            OutputStream out = null;
                            final File tmp;

                            try {
                                tmp = fileUtil.getTempFile();
                                out = new FileOutputStream(tmp);
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
                                out.flush();

                                //fileUtil.savePhoto(Uri.fromFile(tmp), mAttachmentIdentTag);
                                if(!mStorageUtil.storeMediaFile(Uri.fromFile(tmp),
                                        mMediaDestinationUri,
                                        "img_" + mAttachmentIdentTag + ".jpg",
                                        "image/jpg",
                                        forceOverwrite)) {
                                    LogUtil.e(TAG, "saveMedia: Save image failed!");
                                    Toast.makeText(ViewAttachmentActivity.this, R.string.dialog_saveToGallery_failed, Toast.LENGTH_LONG).show();
                                    return;
                                }

                                if (showToast) {
                                    Toast.makeText(ViewAttachmentActivity.this, R.string.dialog_saveToGallery_bitmap_finished, Toast.LENGTH_LONG).show();
                                }
                            } catch (final IOException e) {
                                LogUtil.e(TAG, "saveMedia: Save image failed with " + e.getMessage());
                            } finally {
                                StreamUtil.closeStream(out);
                            }
                        }
                    /*
                    if (imageUri != null) {
                        (new FileUtil(ViewAttachmentActivity.this)).savePhoto(imageUri, mAttachmentIdentTag);

                        if (showToast) {
                            Toast.makeText(ViewAttachmentActivity.this, R.string.dialog_saveToGallery_bitmap_finished, Toast.LENGTH_LONG).show();
                        }
                     */
                    } else if (videoUri != null) {
                        //(new FileUtil(ViewAttachmentActivity.this)).saveVideo(videoUri, mAttachmentIdentTag);
                        if(!mStorageUtil.storeMediaFile(videoUri,
                                mMediaDestinationUri,
                                "vid_" + mAttachmentIdentTag + ".mp4",
                                "video/mp4",
                                forceOverwrite)) {
                            LogUtil.e(TAG, "saveMedia: Save video failed!");
                            Toast.makeText(ViewAttachmentActivity.this, R.string.dialog_saveToGallery_failed, Toast.LENGTH_LONG).show();
                            return;
                        }

                        if (showToast) {
                            Toast.makeText(ViewAttachmentActivity.this, R.string.dialog_saveToGallery_video_finished, Toast.LENGTH_LONG).show();
                        }
                    } else if (voiceUri != null) {
                        //(new FileUtil(ViewAttachmentActivity.this)).saveVoice(voiceUri, mAttachmentIdentTag);
                        if(!mStorageUtil.storeMediaFile(voiceUri,
                                mMediaDestinationUri,
                                "audio_" + mAttachmentIdentTag + ".m4a",
                                "audio/mpeg",
                                forceOverwrite)) {
                            LogUtil.e(TAG, "saveMedia: Save audio failed!");
                            Toast.makeText(ViewAttachmentActivity.this, R.string.dialog_saveToGallery_failed, Toast.LENGTH_LONG).show();
                            return;
                        }

                        if (showToast) {
                            Toast.makeText(ViewAttachmentActivity.this, R.string.dialog_saveToGallery_voice_finished, Toast.LENGTH_LONG).show();
                        }
                    }
                }
            }
        });
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        LogUtil.d(TAG, "onIsPlayingChanged: " + isPlaying);
        if (isPlaying) {
            if(mHasAttachmentDesc) {
                mAttachmentDescriptionView.setVisibility(View.GONE);
            }
            getToolbar().setVisibility(View.GONE);
            // Active playback.
        } else {
            getToolbar().setVisibility(View.VISIBLE);
            // Not playing because playback is paused, ended, suppressed, or the player
            // is buffering, stopped or failed. Check player.getPlayWhenReady,
            // player.getPlaybackState, player.getPlaybackSuppressionReason and
            // player.getPlaybackError for details.
        }
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        if (error.type == ExoPlaybackException.TYPE_SOURCE) {
            IOException cause = error.getSourceException();
            if (cause instanceof HttpDataSource.HttpDataSourceException) {
                // An HTTP error occurred.
                HttpDataSource.HttpDataSourceException httpError = (HttpDataSource.HttpDataSourceException) cause;
                // This is the request for which the error occurred.
                DataSpec requestDataSpec = httpError.dataSpec;
                // It's possible to find out more about the error both by casting and by
                // querying the cause.
                if (httpError instanceof HttpDataSource.InvalidResponseCodeException) {
                    // Cast to InvalidResponseCodeException and retrieve the response code,
                    // message and headers.
                } else {
                    // Try calling httpError.getCause() to retrieve the underlying cause,
                    // although note that it may be null.
                }
            }
        }
        releasePlayers();
    }
}

