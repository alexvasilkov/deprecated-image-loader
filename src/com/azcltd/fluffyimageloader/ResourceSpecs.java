package com.azcltd.fluffyimageloader;

import java.util.Collection;
import java.util.Map;

public abstract class ResourceSpecs<T> {

    private String mUri;
    private boolean mUseMemoryCache;
    private boolean mUseDiskCache;
    private long mDelay;

    private OnResourceLoadingListener<T> mOnLoadingListener;

    private Map<String, String> mHeaders;

    public ResourceSpecs(String uri) {
        setUri(uri);
    }

    public String getUri() {
        return mUri;
    }

    public void setUri(String uri) {
        mUri = uri;

        UriHelper uriHelper = new UriHelper(uri);
        if (uriHelper.isLocal()) {
            mUseMemoryCache = true;
        } else if (uriHelper.isRemote()) {
            mUseDiskCache = true;
            mUseMemoryCache = true;
        }
    }

    public boolean isUseMemoryCache() {
        return mUseMemoryCache;
    }

    public void setUseMemoryCache(boolean useMemoryCache) {
        this.mUseMemoryCache = useMemoryCache;
    }

    public boolean isUseDiskCache() {
        return mUseDiskCache;
    }

    public void setUseDiskCache(boolean useDiskCache) {
        this.mUseDiskCache = useDiskCache;
    }

    public long getDelay() {
        return mDelay;
    }

    public void setDelay(long delay) {
        mDelay = delay;
    }

    public void setOnResourceLoadingListener(OnResourceLoadingListener<T> listener) {
        mOnLoadingListener = listener;
    }

    public Map<String, String> getHeaders() {
    	return mHeaders;
    }

    public void setHeaders(Map<String, String> headers) {
        mHeaders = headers;
    }

    /**
     * Called when resource is placed to queue for downloading
     */
    protected void onPrepare() {
        if (mOnLoadingListener != null) mOnLoadingListener.onPrepare(this);
    }

    /**
     * Called when loader thread is starting download resource
     */
    protected void onStart() {
        if (mOnLoadingListener != null) mOnLoadingListener.onStart(this);
    }

    /**
     * Called when resource is loaded (from Internet or from cache).
     * 
     * @param res
     *            Loaded resource or {@code null} if some error is occurred.
     */
    protected void onLoaded(T res, boolean fromMemory, boolean fromDisk) {
        if (mOnLoadingListener != null) mOnLoadingListener.onLoaded(this, res, fromMemory, fromDisk);
    }

    static <T> boolean isUseMemoryCache(Collection<ResourceSpecs<T>> list) {
        for (ResourceSpecs<?> specs : list)
            if (specs.isUseMemoryCache()) return true;
        return false;
    }

    static <T> boolean isUseDiskCache(Collection<ResourceSpecs<T>> list) {
        for (ResourceSpecs<?> specs : list)
            if (specs.isUseDiskCache()) return true;
        return false;
    }

}
