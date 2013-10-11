package com.azcltd.fluffyimageloader.loader;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import com.azcltd.fluffyimageloader.cache.DefaultCacheKeyGenerator;
import com.azcltd.fluffyimageloader.cache.DiskCache;
import com.azcltd.fluffyimageloader.cache.ICacheKeyGenerator;
import com.azcltd.fluffyimageloader.loader.ResourcesLoadingManager.LoadingState;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class ResourcesLoader<T> {

    public static final long MIN_LOADING_DELAY = 100;
    protected static final String TAG = "ResourcesLoader";

    private static final int MAX_EXTERNAL_CACHE_SIZE = 100 * 1024 * 1024;
    private static final int MAX_INTERNAL_CACHE_SIZE = 30 * 1024 * 1024;
    private static final int DOWNLOAD_THREAD_POOL_SIZE = 4;
    private static final int LOCAL_LOADER_THREAD_POOL_SIZE = 2;

    private static final ICacheKeyGenerator DEFAULT_CACHE_KEY_GENERATOR = new DefaultCacheKeyGenerator();

    private boolean mIsVerbose = false;

    private final Context mAppContext;
    private final DiskCache mDiskCache;
    private final ResourcesLoadingManager<T> mLoadingManager;

    private ICacheKeyGenerator mCacheKeyGenerator;

    private Thread mManagerThread;
    private final ExecutorService mDownloadThreadPool;
    private final ExecutorService mLocalLoaderThreadPool;
    private final HttpClient mHttpClient;

    private final Handler mHandler;

    public ResourcesLoader(Context appContext) {
        this(appContext, MAX_EXTERNAL_CACHE_SIZE, MAX_INTERNAL_CACHE_SIZE);
    }

    public ResourcesLoader(Context appContext, int maxExternalCacheSize, int maxInternalCacheSize) {
        mAppContext = appContext;
        mDiskCache = new DiskCache(appContext, maxExternalCacheSize, maxInternalCacheSize);
        mLoadingManager = new ResourcesLoadingManager<T>();
        mDownloadThreadPool = Executors.newFixedThreadPool(DOWNLOAD_THREAD_POOL_SIZE);
        mLocalLoaderThreadPool = Executors.newFixedThreadPool(LOCAL_LOADER_THREAD_POOL_SIZE);
        mHttpClient = ConcurrentHttpClient.createHttpClient(DOWNLOAD_THREAD_POOL_SIZE);
        mHandler = new LoadHandler<T>(mLoadingManager);
    }

    public void setCacheKeyGenerator(ICacheKeyGenerator generator) {
        mCacheKeyGenerator = generator;
    }

    public void setVerbose(boolean verbose) {
        mIsVerbose = verbose;
    }

    private String toKey(String uri) {
        return (mCacheKeyGenerator == null ? DEFAULT_CACHE_KEY_GENERATOR : mCacheKeyGenerator).toCacheKey(uri);
    }

    public boolean isVerbose() {
        return mIsVerbose;
    }

    protected void loadResource(ResourceSpecs<T> specs) {
        if (specs == null) return;

        String uri = specs.getUri();
        if (uri == null || uri.length() == 0) {
            if (isVerbose()) Log.d(TAG, "1. Resource was not loaded, uri is empty");
            specs.onLoaded(null, true, false);
            return;
        }

        T res = getFromMemoryCache(toKey(uri));
        if (res != null) {
            if (isVerbose()) Log.d(TAG, "1. Resource is loaded from memory cache in same moment: " + uri);
            specs.onLoaded(res, true, false);
        } else {
            if (isVerbose()) Log.d(TAG, "1. Resource is posted to the queue: " + uri);
            specs.onPrepare();
            mLoadingManager.addSpecs(specs);
        }

        if (mManagerThread == null) {
            mManagerThread = new Thread(new ManagerTask());
            mManagerThread.start();
        }
    }

    protected abstract T getFromMemoryCache(String key);

    protected abstract void putToMemoryCache(String key, T res);

    /**
     * @param in InputStream from which resource should be loaded. Should be closed inside this method! May be null.
     * @return Will be called from background thread to get resource object.
     */
    protected abstract T loadFromStream(InputStream in, Collection<ResourceSpecs<T>> specsList);

    private InputStream openFileUriAsInputStream(String fileUri) {
        try {
            return mAppContext.getContentResolver().openInputStream(Uri.parse(fileUri));
        } catch (FileNotFoundException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
            return null;
        }
    }

    private T saveLoadedResource(String uri, InputStream in, boolean skipDiskCache) {
        Set<ResourceSpecs<T>> specsList = mLoadingManager.getSpecsList(uri);
        if (specsList == null) return null;

        String key = toKey(uri);
        T res;
        if (!skipDiskCache && ResourceSpecs.isUseDiskCache(specsList)) {
            // Saving stream to cached file and then reading from this file
            mDiskCache.save(key, in);
            InputStream in2 = openFileUriAsInputStream(mDiskCache.get(key));
            res = loadFromStream(in2, specsList);
        } else {
            // Reading straight from given stream
            res = loadFromStream(in, specsList);
        }

        // Saving in memory cache if needed
        if (res != null && ResourceSpecs.isUseMemoryCache(specsList)) putToMemoryCache(key, res);

        return res;
    }

    private void notifyLoaded(String uri, T res, boolean fromMemory, boolean fromDisk) {
        mLoadingManager.setResult(uri, res);
        mLoadingManager.setState(uri, LoadingState.WAIT_DISPLAYING);
        int action;
        if (fromMemory) {
            action = LoadHandler.ACTION_ON_LOADED_FROM_MEMORY;
        } else if (fromDisk) {
            action = LoadHandler.ACTION_ON_LOADED_FROM_DISK;
        } else {
            action = LoadHandler.ACTION_ON_LOADED;
        }
        mHandler.sendMessage(mHandler.obtainMessage(action, uri));
    }

    private void scheduleDownload(String uri) {
        if (hasInternetConnection()) {
            mLoadingManager.setState(uri, LoadingState.WAIT_DOWNLOADING);
            mDownloadThreadPool.submit(new DownloadTask(uri));
        } else {
            if (isVerbose()) Log.d(TAG, "No internet connection is available");
            notifyLoaded(uri, null, true, false);
        }
    }

    /**
     * Checks if the device has Internet connection.
     */
    private boolean hasInternetConnection() {
        ConnectivityManager cm = (ConnectivityManager) mAppContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo wifiNetwork = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (wifiNetwork != null && wifiNetwork.isConnected()) return true;

        NetworkInfo mobileNetwork = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        if (mobileNetwork != null && mobileNetwork.isConnected()) return true;

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    private void scheduleLocalLoader(String uri, String cachedFileUri) {
        mLoadingManager.setState(uri, LoadingState.WAIT_LOADING);
        mLocalLoaderThreadPool.submit(new LocalLoaderTask(uri, cachedFileUri));
    }

    private void fillHttpHeaders(HttpUriRequest request, String uri) {
        Set<ResourceSpecs<T>> specsList = mLoadingManager.getSpecsList(uri);
        if (specsList == null) return;

        // Getting first specs from set for given URI
        ResourceSpecs<T> lastSpecs = specsList.size() == 0 ? null : specsList.iterator().next();
        if (lastSpecs == null || lastSpecs.getHeaders() == null) return;

        // Adding headers to request
        for (Map.Entry<String, String> pair : lastSpecs.getHeaders().entrySet()) {
            request.addHeader(pair.getKey(), pair.getValue());
        }
    }

    private class ManagerTask extends FailSafeRunnable {
        @Override
        protected void runSafe() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND);
            try {
                while (true) {
                    String uri;

                    synchronized (mLoadingManager) {
                        uri = mLoadingManager.getNextUriToManage(false);
                        boolean isEmptyQueue = mLoadingManager.getNextUriToManage(true) == null;

                        if (uri == null) {
                            if (isVerbose()) Log.d(TAG, "2. Manager thread is waiting for another resource to load");
                            if (isEmptyQueue) {
                                // There are no waiting resources, so we can wait infinitely
                                mLoadingManager.wait();
                            } else {
                                // There is other waiting resource, so we should check it again after a small delay
                                mLoadingManager.wait(MIN_LOADING_DELAY);
                            }
                            continue;
                        }
                    }

                    mLoadingManager.setState(uri, LoadingState.MANAGING);

                    UriHelper uriHelper = new UriHelper(uri);

                    if (!mLoadingManager.isOutdated(uri)) {
                        T res = getFromMemoryCache(toKey(uri));
                        if (res != null) {
                            if (isVerbose()) Log.d(TAG, "2. Resource is found in memory cache: " + uri);
                            notifyLoaded(uri, res, true, false);
                        } else if (mDiskCache.isExists(toKey(uri))) {
                            if (isVerbose())
                                Log.d(TAG, "2. Resource is found in disk cache, scheduling loader: " + uri);
                            scheduleLocalLoader(uri, mDiskCache.get(toKey(uri)));
                        } else if (uriHelper.isLocal()) {
                            if (isVerbose())
                                Log.d(TAG, "2. No resources found in cache, scheduling local loader: " + uri);
                            scheduleLocalLoader(uri, null);
                        } else if (uriHelper.isRemote()) {
                            if (isVerbose()) Log.d(TAG, "2. No resources found in cache, scheduling download: " + uri);
                            scheduleDownload(uri);
                        } else {
                            if (isVerbose()) Log.d(TAG, "2. Unknown Uri scheme, skipping resource: " + uri);
                            notifyLoaded(uri, null, true, false);
                        }
                    } else {
                        if (isVerbose())
                            Log.d(TAG, "2. Resource was outdated and will not be loaded (manager thread): " + uri);
                    }
                }
            } catch (InterruptedException e) {
                // Thread will be closed
            }
            mManagerThread = null;
        }
    }

    private class DownloadTask extends FailSafeRunnable {
        private String mUri;

        public DownloadTask(String uri) {
            mUri = uri;
        }

        @Override
        protected void runSafe() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND + 8);

            String uri = mUri;
            if (uri == null) return;

            if (!mLoadingManager.isOutdated(uri)) {
                T res = getFromMemoryCache(toKey(uri));
                if (res != null) {
                    if (isVerbose())
                        Log.w(TAG, "3. Resource was found in memory cache - no downloading is needed: " + uri);
                    notifyLoaded(uri, res, true, false);
                } else if (mDiskCache.isExists(toKey(uri))) {
                    if (isVerbose()) Log.w(TAG, "3. Resource was found on disk - no downloading is needed: " + uri);
                    scheduleLocalLoader(uri, mDiskCache.get(toKey(uri)));
                } else {
                    if (isVerbose()) Log.d(TAG, "3. Starting download process for resource: " + uri);
                    mLoadingManager.setState(uri, LoadingState.DOWNLOADING);
                    mHandler.sendMessage(mHandler.obtainMessage(LoadHandler.ACTION_ON_START, uri));

                    // TODO: add progress

                    try {
                        HttpGet request = new HttpGet(uri);
                        fillHttpHeaders(request, uri);
                        HttpResponse resp = mHttpClient.execute(request);

                        int statusCode = resp.getStatusLine().getStatusCode();
                        boolean isOk = statusCode / 100 == 2;
                        HttpEntity entity = resp.getEntity();

                        if (isOk) {
                            InputStream in = entity == null ? null : entity.getContent();
                            res = saveLoadedResource(uri, in, false);
                            if (isVerbose())
                                Log.d(TAG, "3. Resource downloading is " + (res == null ? "failed" : "succeeded") + ": " + uri);
                        } else {
                            if (isVerbose())
                                Log.d(TAG, "3. Resource downloading is failed, http status code " + statusCode + ": " + uri);
                        }

                        if (entity != null) entity.consumeContent();

                    } catch (Exception e) {
                        if (isVerbose())
                            Log.d(TAG, "3. Exception while downloading resource: " + e.getMessage() + " (" + uri + ")");
                    }

                    notifyLoaded(uri, res, false, false);
                }
            } else {
                if (isVerbose()) Log.d(TAG, "3. Resource was outdated before downloading: " + uri);
            }
        }

    }

    private class LocalLoaderTask extends FailSafeRunnable {

        private String mUri;
        private String mCachedFileUri;

        public LocalLoaderTask(String uri, String cachedFileUri) {
            mUri = uri;
            mCachedFileUri = cachedFileUri;
        }

        @Override
        protected void runSafe() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND);

            String uri = mUri;
            if (uri == null) return;

            if (!mLoadingManager.isOutdated(uri)) {
                T res = getFromMemoryCache(toKey(uri));
                if (res != null) {
                    if (isVerbose()) Log.w(TAG, "4. Resource is found in memory cache: " + uri);
                    notifyLoaded(uri, res, true, false);
                } else if (mCachedFileUri != null) {
                    if (isVerbose()) Log.d(TAG, "4. Loading resource from disk cache: " + uri);

                    mLoadingManager.setState(uri, LoadingState.LOADING);

                    InputStream in = openFileUriAsInputStream(mCachedFileUri);
                    res = saveLoadedResource(mUri, in, true);
                    notifyLoaded(uri, res, false, true);
                } else {
                    if (isVerbose()) Log.d(TAG, "4. Loading local resource: " + uri);

                    mLoadingManager.setState(uri, LoadingState.LOADING);

                    InputStream in = openFileUriAsInputStream(mUri);
                    res = saveLoadedResource(mUri, in, false);
                    notifyLoaded(uri, res, false, true);
                }

                if (isVerbose())
                    Log.d(TAG, "4. Resource loading is " + (res == null ? "failed" : "succeeded") + ": " + uri);
            } else {
                if (isVerbose()) Log.d(TAG, "4. Resource was outdated before loading: " + uri);
            }
        }

    }

    private abstract static class FailSafeRunnable implements Runnable {

        @Override
        public final void run() {
            try {
                runSafe();
            } catch (Throwable e) {
                Log.e(TAG, "Thread was finished with error", e);
            }
        }

        protected abstract void runSafe();

    }

    private static class LoadHandler<T> extends Handler {
        public static final int ACTION_ON_START = 0;
        public static final int ACTION_ON_LOADED = 1;
        public static final int ACTION_ON_LOADED_FROM_MEMORY = 2;
        public static final int ACTION_ON_LOADED_FROM_DISK = 3;

        private ResourcesLoadingManager<T> mLoadingManager;

        private LoadHandler(ResourcesLoadingManager<T> loadingManager) {
            mLoadingManager = loadingManager;
        }

        @Override
        public void handleMessage(Message msg) {
            String uri = (String) msg.obj;
            if (mLoadingManager.isOutdated(uri)) return;

            switch (msg.what) {
                case ACTION_ON_START: {
                    Set<ResourceSpecs<T>> set = mLoadingManager.getSpecsList(uri);
                    if (set == null) break;
                    for (ResourceSpecs<T> specs : set)
                        specs.onStart();
                    break;
                }
                case ACTION_ON_LOADED_FROM_MEMORY:
                case ACTION_ON_LOADED_FROM_DISK:
                case ACTION_ON_LOADED: {
                    boolean fromMemory = (msg.what == ACTION_ON_LOADED_FROM_MEMORY);
                    boolean fromDisk = (msg.what == ACTION_ON_LOADED_FROM_DISK);
                    T res = mLoadingManager.getResult(uri);
                    Set<ResourceSpecs<T>> set = mLoadingManager.remove(uri);
                    for (ResourceSpecs<T> specs : set)
                        specs.onLoaded(res, fromMemory, fromDisk);
                    break;
                }
            }
        }
    }

}