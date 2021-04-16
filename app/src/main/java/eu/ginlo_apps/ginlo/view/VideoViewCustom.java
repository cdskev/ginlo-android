// Copyright (c) 2020-2021 ginlo.net GmbH

package eu.ginlo_apps.ginlo.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.VideoView;

/**
 * Created by SGA on 30.07.2015.
 */
public class VideoViewCustom
        extends VideoView {

    private PlayPauseListener mListener;

    public VideoViewCustom(Context context) {
        super(context);
    }

    public VideoViewCustom(Context context,
                           AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VideoViewCustom(Context context,
                           AttributeSet attrs,
                           int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * @param listener
     */
    public void setPlayPauseListener(PlayPauseListener listener) {
        mListener = listener;
    }

    @Override
    public void pause() {
        super.pause();
        if (mListener != null) {
            mListener.onPause();
        }
    }

    @Override
    public void start() {
        super.start();
        if (mListener != null) {
            mListener.onPlay();
        }
    }

    public interface PlayPauseListener {
        void onPlay();

        void onPause();
    }
}
