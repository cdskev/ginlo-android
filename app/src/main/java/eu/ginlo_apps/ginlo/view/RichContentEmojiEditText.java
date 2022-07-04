package eu.ginlo_apps.ginlo.view;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BuildCompat;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;
import androidx.emoji.widget.EmojiAppCompatEditText;

import eu.ginlo_apps.ginlo.context.SimsMeApplication;
import eu.ginlo_apps.ginlo.fragment.ChatInputFragment;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.MimeUtil;

public class RichContentEmojiEditText extends EmojiAppCompatEditText
        implements InputConnectionCompat.OnCommitContentListener {

    private static final String TAG = "RichContentEmojiEditText";
    private Context context;
    private SimsMeApplication application;
    private ChatInputFragment listener = null;

    public RichContentEmojiEditText(Context context) {
        this(context, null, 0);
    }

    public RichContentEmojiEditText(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RichContentEmojiEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        this.application = (SimsMeApplication) context.getApplicationContext();

    }


    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        final InputConnection ic = super.onCreateInputConnection(outAttrs);

        EditorInfoCompat.setContentMimeTypes(outAttrs, MimeUtil.getRichContentMimeTypes().toArray(new String[0]));
        final InputConnectionCompat.OnCommitContentListener callback = this;
        if(ic != null) {
            return InputConnectionCompat.createWrapper(ic, outAttrs, callback);
        }

        return null;
    }

    @Override
    public boolean onCommitContent(@NonNull InputContentInfoCompat inputContentInfo, int flags, @Nullable Bundle opts) {
        if (BuildCompat.isAtLeastNMR1() && (flags &
                InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0) {
            try {
                inputContentInfo.requestPermission();
            }
            catch (Exception e) {
                LogUtil.e(TAG, "onCommitContent: Caught " + e.getMessage());
                return false;
            }
        }

        // Trigger ChatInputFragment for further processing
        if(listener != null) {
            listener.onRichInputReceived(inputContentInfo);
        }
        return true;
    }

    // Currently we have ChatInputFragment which contains the EditView only
    public void registerListener(ChatInputFragment listener) {
        this.listener = listener;
    }

    public interface RichContentListener {
        void onRichInputReceived(InputContentInfoCompat inputContentInfo);
    }
}
