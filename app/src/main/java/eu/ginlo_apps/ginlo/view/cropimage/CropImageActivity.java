// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.view.cropimage;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.*;
import android.media.FaceDetector;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import eu.ginlo_apps.ginlo.R;
import eu.ginlo_apps.ginlo.log.LogUtil;
import eu.ginlo_apps.ginlo.util.ImageUtil;

import java.util.concurrent.CountDownLatch;

import static eu.ginlo_apps.ginlo.model.constant.NumberConstants.INT_1024;

/**
 * The activity can crop specific region of interest from an image.
 */
public class CropImageActivity
        extends MonitoredActivity {
    public static final String IMAGE_PATH = "image-path";
    public static final String SCALE = "scale";
    public static final String OUTPUT_X = "outputX";
    public static final String OUTPUT_Y = "outputY";
    public static final String CIRCLE_CROP = "circleCrop";
    public static final String RETURN_DATA = "return-data";
    public static final String RETURN_DATA_AS_BITMAP = "data";
    private static final String TAG = "CropImageActivity";
    private static final String ASPECT_X = "aspectX";
    private static final String ASPECT_Y = "aspectY";
    private static final String SCALE_UP_IF_NEEDED = "scaleUpIfNeeded";
    private static final String ACTION_INLINE_DATA = "inline-data";
    private final static int IMAGE_MAX_SIZE = INT_1024;

    private final Handler mHandler = new Handler();
    private final eu.ginlo_apps.ginlo.view.cropimage.BitmapManager.ThreadSet mDecodingThreads = new eu.ginlo_apps.ginlo.view.cropimage.BitmapManager.ThreadSet();
    boolean mWaitingToPick;  // Whether we are wait the user to pick a face.
    boolean mSaving;  // Whether the "save" button is already clicked.
    eu.ginlo_apps.ginlo.view.cropimage.HighlightView mCrop;
    private boolean mCircleCrop = false;
    private int mAspectX;
    private int mAspectY;
    private int mOutputX;
    private int mOutputY;
    private boolean mScale;
    private CropImageView mImageView;
    private Bitmap mBitmap;
    private String mImagePath;
    private final Runnable mRunFaceDetection = new Runnable() {
        final FaceDetector.Face[] mFaces = new FaceDetector.Face[3];
        @SuppressWarnings("hiding")
        float mScale = 1F;
        Matrix mImageMatrix;
        int mNumFaces;

        // For each face, we create a HightlightView for it.
        private void handleFace(FaceDetector.Face f) {
            PointF midPoint = new PointF();

            int r = ((int) (f.eyesDistance() * mScale)) * 2;

            f.getMidPoint(midPoint);
            midPoint.x *= mScale;
            midPoint.y *= mScale;

            int midX = (int) midPoint.x;
            int midY = (int) midPoint.y;

            eu.ginlo_apps.ginlo.view.cropimage.HighlightView hv = new eu.ginlo_apps.ginlo.view.cropimage.HighlightView(mImageView);

            int width = mBitmap.getWidth();
            int height = mBitmap.getHeight();

            Rect imageRect = new Rect(0, 0, width, height);

            RectF faceRect = new RectF(midX, midY, midX, midY);

            faceRect.inset(-r, -r);
            if (faceRect.left < 0) {
                faceRect.inset(-faceRect.left, -faceRect.left);
            }

            if (faceRect.top < 0) {
                faceRect.inset(-faceRect.top, -faceRect.top);
            }

            if (faceRect.right > imageRect.right) {
                faceRect.inset(faceRect.right - imageRect.right, faceRect.right - imageRect.right);
            }

            if (faceRect.bottom > imageRect.bottom) {
                faceRect.inset(faceRect.bottom - imageRect.bottom, faceRect.bottom - imageRect.bottom);
            }

            hv.setup(mImageMatrix, imageRect, faceRect, mCircleCrop, (mAspectX != 0) && (mAspectY != 0));

            mImageView.add(hv);
        }

        // Create a default HightlightView if we found no face in the picture.
        private void makeDefault() {
            eu.ginlo_apps.ginlo.view.cropimage.HighlightView hv = new eu.ginlo_apps.ginlo.view.cropimage.HighlightView(mImageView);

            int width = mBitmap.getWidth();
            int height = mBitmap.getHeight();

            Rect imageRect = new Rect(0, 0, width, height);

            // make the default size about 4/5 of the width or height
            int cropWidth = Math.min(width, height) * 4 / 5;
            int cropHeight = cropWidth;

            if ((mAspectX != 0) && (mAspectY != 0)) {
                if (mAspectX > mAspectY) {
                    cropHeight = cropWidth * mAspectY / mAspectX;
                } else {
                    cropWidth = cropHeight * mAspectX / mAspectY;
                }
            }

            int x = (width - cropWidth) / 2;
            int y = (height - cropHeight) / 2;

            RectF cropRect = new RectF(x, y, x + cropWidth, y + cropHeight);

            hv.setup(mImageMatrix, imageRect, cropRect, mCircleCrop, (mAspectX != 0) && (mAspectY != 0));

            mImageView.mHighlightViews.clear();  // Thong added for rotate

            mImageView.add(hv);
        }

        // Scale the image down for faster face detection.
        private Bitmap prepareBitmap() {
            if (mBitmap == null) {
                return null;
            }

            // 256 pixels wide is enough.
            if (mBitmap.getWidth() > 256) {
                mScale = 256.0F / mBitmap.getWidth();
            }

            Matrix matrix = new Matrix();

            matrix.setScale(mScale, mScale);

            if (mBitmap.isRecycled()) {
                mBitmap = getBitmap(mImagePath);
            }

            return Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), matrix, true);
        }

        public void run() {
            mImageMatrix = mImageView.getImageMatrix();

            Bitmap faceBitmap = prepareBitmap();

            mScale = 1.0F / mScale;
            if (faceBitmap != null) {
                FaceDetector detector = new FaceDetector(faceBitmap.getWidth(), faceBitmap.getHeight(), mFaces.length);

                mNumFaces = detector.findFaces(faceBitmap, mFaces);
            }

            mHandler.post(new Runnable() {
                public void run() {
                    mWaitingToPick = mNumFaces > 1;
                    if (mNumFaces > 0) {
                        for (int i = 0; i < mNumFaces; i++) {
                            handleFace(mFaces[i]);
                        }
                    } else {
                        makeDefault();
                    }
                    mImageView.invalidate();
                    if (mImageView.mHighlightViews.size() == 1) {
                        mCrop = mImageView.mHighlightViews.get(0);
                        mCrop.setFocus(true);
                    }

                    if (mNumFaces > 1) {
                        Toast.makeText(CropImageActivity.this, "Multi face crop help", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    };
    // These options specifiy the output image size and whether we should
    // scale the output to fit it (or just crop it).
    private boolean mScaleUp = true;

    @Override
    protected int getActivityLayout() {
        return R.layout.cropimage;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreateActivity(Bundle icicle) {
        super.onCreateActivity(icicle);

        mImageView = findViewById(R.id.image);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();

        if (extras != null) {
            if (extras.getBoolean(CIRCLE_CROP)) {
                mImageView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

                mCircleCrop = true;
                mAspectX = 1;
                mAspectY = 1;
            } else {
                if (extras.containsKey(ASPECT_X) && (extras.get(ASPECT_X) instanceof Integer)) {
                    mAspectX = extras.getInt(ASPECT_X);
                } else {
                    throw new IllegalArgumentException("aspect_x must be integer");
                }
                if (extras.containsKey(ASPECT_Y) && (extras.get(ASPECT_Y) instanceof Integer)) {
                    mAspectY = extras.getInt(ASPECT_Y);
                } else {
                    throw new IllegalArgumentException("aspect_y must be integer");
                }
            }

            mImagePath = extras.getString(IMAGE_PATH);

            mBitmap = getBitmap(mImagePath);

            mOutputX = extras.getInt(OUTPUT_X);
            mOutputY = extras.getInt(OUTPUT_Y);
            mScale = extras.getBoolean(SCALE, true);
            mScaleUp = extras.getBoolean(SCALE_UP_IF_NEEDED, true);
        }

        if (mBitmap == null) {
            LogUtil.d(TAG, "onCreateActivity: Bitmap is null!");
            finish();
            return;
        }

        // Make UI fullscreen.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        findViewById(R.id.discard).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        findViewById(R.id.save).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onSaveClicked();
                finish();
            }
        });

        findViewById(R.id.rotateLeft).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mBitmap = eu.ginlo_apps.ginlo.view.cropimage.Util.rotateImage(mBitmap, -90);

                eu.ginlo_apps.ginlo.view.cropimage.RotateBitmap rotateBitmap = new RotateBitmap(mBitmap);

                mImageView.setImageRotateBitmapResetBase(rotateBitmap, true);
                mRunFaceDetection.run();
            }
        });

        startFaceDetection();
    }

    private Bitmap getBitmap(String path) {
        if (path == null) {
            return null;
        } else {
            return ImageUtil.decodeUri(this, Uri.parse(path), IMAGE_MAX_SIZE, IMAGE_MAX_SIZE, true);
        }
    }

    private void startFaceDetection() {
        if (isFinishing()) {
            return;
        }

        mImageView.setImageBitmapResetBase(mBitmap, true);

        Util.startBackgroundJob(this, new Runnable() {
            public void run() {
                final CountDownLatch latch = new CountDownLatch(1);
                final Bitmap b = mBitmap;

                mHandler.post(new Runnable() {
                    public void run() {
                        if ((b != null) && (!b.equals(mBitmap))) {
                            mImageView.setImageBitmapResetBase(b, true);
                            mBitmap = b;
                        }
                        if (mImageView.getScale() == 1F)
                        {
                            mImageView.center();
                        }
                        latch.countDown();
                    }
                });
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                mRunFaceDetection.run();
            }
        }, mHandler);
    }

    private void onSaveClicked() {
        // TODO this code needs to change to use the decode/crop/encode single
        // step api so that we don't require that the whole (possibly large)
        // bitmap doesn't have to be read into memory
        if (mSaving) {
            return;
        }

        if (mCrop == null) {
            return;
        }

        mSaving = true;

        Rect r = mCrop.getCropRect();

        int width = r.width();
        int height = r.height();

        // If we are circle cropping, we want alpha channel, which is the
        // third param here.
        Bitmap croppedImage;

        try {
            croppedImage = Bitmap.createBitmap(width, height,
                    mCircleCrop ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);
        } catch (Exception e) {
            LogUtil.e(TAG, "onSaveClicked: createBitmap failed with " + e.getMessage());
            return;
        }

        if (croppedImage == null) {
            return;
        }

        Canvas cv = new Canvas(croppedImage);
        Rect dr = new Rect(0, 0, width, height);
        cv.drawBitmap(mBitmap, r, dr, null);

        if (mCircleCrop) {
            // OK, so what's all this about?
            // Bitmaps are inherently rectangular but we want to return
            // something that's basically a circle.  So we fill in the
            // area around the circle with alpha.  Note the all important
            // PortDuff.Mode.CLEAR.
            Canvas c = new Canvas(croppedImage);
            Path p = new Path();

            p.addCircle(width / 2F, height / 2F, width / 2F, Path.Direction.CW);
            c.clipPath(p, Region.Op.DIFFERENCE);
            c.drawColor(0x00000000, PorterDuff.Mode.CLEAR);
        }

        /* If the output is required to a specific size then scale or fill */
        if ((mOutputX != 0) && (mOutputY != 0)) {
            if (mScale) {
                /* Scale the image to the required dimensions */
                Bitmap old = croppedImage;

                croppedImage = Util.transform(new Matrix(), croppedImage, mOutputX, mOutputY, mScaleUp);
            } else {
                /* Don't scale the image crop it to the size requested.
                 * Create an new image with the cropped image in the center and
                 * the extra space filled.
                 */

                // Don't scale the image but instead fill it so it's the
                // required dimension
                Bitmap b = Bitmap.createBitmap(mOutputX, mOutputY, Bitmap.Config.RGB_565);
                Canvas canvas = new Canvas(b);

                Rect srcRect = mCrop.getCropRect();
                Rect dstRect = new Rect(0, 0, mOutputX, mOutputY);

                int dx = (srcRect.width() - dstRect.width()) / 2;
                int dy = (srcRect.height() - dstRect.height()) / 2;

                /* If the srcRect is too big, use the center part of it. */
                srcRect.inset(Math.max(0, dx), Math.max(0, dy));

                /* If the dstRect is too big, use the center part of it. */
                dstRect.inset(Math.max(0, -dx), Math.max(0, -dy));

                /* Draw the cropped bitmap in the center */
                canvas.drawBitmap(mBitmap, srcRect, dstRect, null);

                /* Set the cropped bitmap as the new bitmap */
                croppedImage = b;
            }
        }

        //FPL: es wird das Bild nur im Intent zurückgegeben, Keine Speichern!
        Bundle extras = new Bundle();
        extras.putParcelable(RETURN_DATA_AS_BITMAP, croppedImage);
        setResult(RESULT_OK, (new Intent()).setAction(ACTION_INLINE_DATA).putExtras(extras));
    }

    @Override
    protected void onPauseActivity() {
        BitmapManager.instance().cancelThreadDecoding(mDecodingThreads);
    }

    @Override
    protected void onResumeActivity() {
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }
}
