package com.azcltd.fluffyimageloader;

import android.graphics.Bitmap;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import com.azcltd.fluffyimageloader.loader.ResourceSpecs;

import java.lang.ref.WeakReference;
import java.util.Collection;

public class ImageSpecs extends ResourceSpecs<Bitmap> {

    private static final long ANIMATION_DURATION = 200;
    private static final int TAG_IMAGE_SPECS_OBJECT = -1;

    private WeakReference<ImageView> mImageView;
    private WeakReference<View> mProgressView;
    private boolean mIsDisplayImageWhileProgress;
    private boolean mIsOutOfMemory;
    private boolean mIsWithAnimation;

    public ImageSpecs(String uri) {
        super(uri);
    }

    /**
     * Preferred way of getting ImageSpecs object.<br/>
     * Should be used within ListViews.<br/>
     * This method will try to find existing specs associated with given ImageView. If no specs found new one will be created.
     */
    public static ImageSpecs getImageSpecsFromView(ImageView imageView, String uri) {
        ImageSpecs specs = (ImageSpecs) imageView.getTag(TAG_IMAGE_SPECS_OBJECT);
        if (specs == null) {
            specs = new ImageSpecs(uri);
            imageView.setTag(TAG_IMAGE_SPECS_OBJECT, specs);
        } else {
            specs.setUri(uri);
        }
        specs.setImageView(imageView);
        return specs;
    }

    public static ImageSpecs getImageSpecs(String uri) {
        return new ImageSpecs(uri);
    }

    @Override
    public void onPrepare() {
        super.onPrepare();
        // Show progress indicator
        ImageView imageView = getImageView();
        View progress = getProgressView();

        if (imageView != null && progress != null) {
            progress.setVisibility(View.VISIBLE);
            if (!isDisplayImageWhileProgress()) imageView.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onLoaded(Bitmap image, boolean fromMemory, boolean fromDisk) {
        super.onLoaded(image, fromMemory, fromDisk);

        if (isOutOfMemory()) {
            // TODO: pass bitmap decoding options, make several attempts
        }

        ImageView imageView = getImageView();
        if (imageView == null) return;

        if (image != null) {
            imageView.setImageBitmap(image);

            if (!fromMemory && mIsWithAnimation) {
                Animation showAnimation = new AlphaAnimation(0f, 1f);
                showAnimation.setDuration(ANIMATION_DURATION);
                imageView.startAnimation(showAnimation);
            }
        }

        // Hiding progress indicator
        View progress = getProgressView();
        if (progress != null) {
            progress.setVisibility(View.INVISIBLE);
            if (!isDisplayImageWhileProgress()) imageView.setVisibility(View.VISIBLE);
        }
    }

    public ImageSpecs setImageView(ImageView imageView) {
        mImageView = new WeakReference<ImageView>(imageView);
        return this;
    }

    public ImageView getImageView() {
        return mImageView == null ? null : mImageView.get();
    }

    /**
     * @param display
     *            If false and ProgressView was set then image will be hided during loading process.<br/>
     *            If true or no ProgressView was set image will not be hided.</br>Default is false.
     */
    public ImageSpecs setDisplayImageWhileProgress(boolean display) {
        mIsDisplayImageWhileProgress = display;
        return this;
    }

    public boolean isDisplayImageWhileProgress() {
        return mIsDisplayImageWhileProgress;
    }

    public ImageSpecs setProgressView(View progress) {
        mProgressView = new WeakReference<View>(progress);
        return this;
    }

    public View getProgressView() {
        return mProgressView == null ? null : mProgressView.get();
    }

    public boolean isWithAnimation() {
        return mIsWithAnimation;
    }

    public void setWithAnimation(boolean isWithAnimation) {
        mIsWithAnimation = isWithAnimation;
    }

    /**
     * @return true if last attempt to load image was finished with OutOfMemoryError
     */
    public boolean isOutOfMemory() {
        return mIsOutOfMemory;
    }

    static void setOutOfMemory(Collection<ResourceSpecs<Bitmap>> list, boolean isOutOfMemory) {
        for (ResourceSpecs<Bitmap> specs : list)
            ((ImageSpecs) specs).mIsOutOfMemory = isOutOfMemory;
    }

}
