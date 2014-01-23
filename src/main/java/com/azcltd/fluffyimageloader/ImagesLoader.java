package com.azcltd.fluffyimageloader;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import com.azcltd.fluffyimageloader.cache.LruCache;
import com.azcltd.fluffyimageloader.loader.ResourceSpecs;
import com.azcltd.fluffyimageloader.loader.ResourcesLoader;

import java.io.InputStream;
import java.util.Collection;

public class ImagesLoader extends ResourcesLoader<Bitmap> {

    public static final int NO_IMAGE_RES_ID = -1;

	private static ImagesLoader sLoader;

	public static ImagesLoader get() {
		if (sLoader == null) throw new RuntimeException("Instance of image loader was not created with create(Context) method");
		return sLoader;
	}

	public static void create(Context context) {
		if (sLoader == null) sLoader = new ImagesLoader(context.getApplicationContext());
	}

	private LruCache<String, Bitmap> mMemoryCache;

	protected ImagesLoader(Context appContext) {
		super(appContext);

		int availableMemory = ((ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass();
		int cacheMemory = availableMemory * 1024 * 1024 / 8;
		if (isVerbose()) Log.d(TAG, "0. Initializing memory cache of size: " + cacheMemory);
		mMemoryCache = new LruCache<String, Bitmap>(cacheMemory) {
			protected int sizeOf(String key, Bitmap value) {
				return value == null ? 0 : value.getRowBytes() * value.getHeight();
			}
		};
	}

	@Override
	protected Bitmap getFromMemoryCache(String key) {
		return mMemoryCache.get(key);
	}

	@Override
	protected void putToMemoryCache(String key, Bitmap image) {
		mMemoryCache.put(key, image);
	}

	@Override
	protected Bitmap loadFromStream(InputStream in, Collection<ResourceSpecs<Bitmap>> specsList) {
		if (in == null) {
			if (isVerbose()) Log.e(TAG, "ImagesLoader.loadFromStream method: InputStream is null");
			return null;
		}

		// BitmapFactory.Options opts = new BitmapFactory.Options();
		// opts.inSampleSize = 2;
		// opts.inPreferredConfig = Bitmap.Config.RGB_565;
		// opts.inDither = true;
		// bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);

		try {
			Bitmap bitmap = BitmapFactory.decodeStream(in);
			ImageSpecs.setOutOfMemory(specsList, false);
			return bitmap;
		} catch (OutOfMemoryError e) {
			Log.e(TAG, "Not enough memory to load an image: " + e.getMessage());
			ImageSpecs.setOutOfMemory(specsList, true);
			mMemoryCache.evictAll();
			return null;
		} finally {
			try {
				in.close();
			} catch (Exception ignored) {
			}
		}
	}

	/**
	 * Loads image using given specs object
	 * 
	 * @param specs
	 */
	public void loadImage(ImageSpecs specs) {
		loadResource(specs);
	}

    /**
     * Loads image into given <code>view</code>.
     */
    public void loadImage(ImageView view, String uri) {
        loadImage(view, null, uri, NO_IMAGE_RES_ID, false, false);
    }

    /**
     * Loads image into given <code>view</code>.
     * Shows <code>progressView</code> instead of <code>view</code> while loading.
     */
    public void loadImage(ImageView view, View progressView, String uri) {
        loadImage(view, progressView, uri, NO_IMAGE_RES_ID, false, false);
    }

	/**
	 * Loads image into given <code>view</code>.
	 */
	public void loadImage(ImageView view, String uri, boolean useDelay, boolean useAnimation) {
		loadImage(view, null, uri, NO_IMAGE_RES_ID, useDelay, useAnimation);
	}

    /**
     * Loads image into given <code>view</code>.<br/>
     * Shows <code>progressView</code> instead of <code>view</code> while loading.
     */
    public void loadImage(ImageView view, View progressView, String uri, boolean useDelay, boolean useAnimation) {
        loadImage(view, progressView, uri, NO_IMAGE_RES_ID, false, useAnimation);
    }

	/**
	 * Loads image into given <code>view</code>.<br/>
	 * Shows drawable with id <code>emptyImageRes</code> while loading the image and if no image is loaded.
	 */
	public void loadImage(ImageView view, String uri, int emptyImageRes, boolean useDelay, boolean useAnimation) {
		loadImage(view, null, uri, emptyImageRes, useDelay, useAnimation);
	}

	/**
	 * Loads image from <code>uri</code> into given <code>view</code>.<br>
	 * Shows <code>progressView</code> instead of <code>view</code> while loading (if <code>progressView</code> is not null).<br>
	 * Uses <code>emptyImageRes</code> (if not equals to NO_IMAGE_RES_ID) as image placeholder while loading.<br>
	 * Optionally can apply small delay before starting loading image.
	 */
	public void loadImage(ImageView view, View progressView, String uri, int emptyImageRes, boolean useDelay, boolean useAnimation) {
		view.setImageDrawable(null);

		ImageSpecs specs = ImageSpecs.getImageSpecsFromView(view, uri);
		specs.setImageView(view);
		specs.setProgressView(progressView);
		if (useDelay) specs.setDelay(300);
		specs.setWithAnimation(hasAlphaAnimationProblems() ? false : useAnimation);

		loadResource(specs); // Loading image

		if (emptyImageRes != NO_IMAGE_RES_ID && view.getDrawable() == null) {
			// Setting empty image if image was not immediately loaded from in-memory cache
			view.setImageResource(emptyImageRes);
		}
	}

	private static boolean hasAlphaAnimationProblems() {
		return Build.VERSION.SDK_INT >= 11 && Build.VERSION.SDK_INT <= 15;
	}

}
