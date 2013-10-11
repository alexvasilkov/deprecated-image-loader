package com.azcltd.fluffyimageloader.loader;


public interface OnResourceLoadingListener<T> {

	/**
	 * Called when resource is placed to queue for downloading
	 */
	public void onPrepare(ResourceSpecs<T> specs);

	/**
	 * Called when loader thread is starting download resource
	 */
	public void onStart(ResourceSpecs<T> specs);

	/**
	 * Called when resource is loaded (from Internet or from cache).
	 * @param res Loaded resource or {@code null} if some error is occurred.
	 */
	public void onLoaded(ResourceSpecs<T> specs, T res, boolean fromMemory, boolean fromDisk);

	// TODO: add onProgress

}
