package com.azcltd.fluffyimageloader;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * This class helps synchronize and manage resources' Uris queue. <br/>
 * <br/>
 * It also provide functionality to maintain set of "specs objects" (see {@code ResourceSpecs} class) for one Uri (i.e. if resource from
 * given Uri was already scheduled for loading we can avoid scheduling another loading process for same Uri). <br/>
 * <br/>
 * Also each Uri can be in several loading states (see {@code LoadingState} enum).
 */
public class ResourcesLoadingManager<T> {

    private Map<String, Set<ResourceSpecs<T>>> mMap = new LinkedHashMap<String, Set<ResourceSpecs<T>>>();
    private Map<String, LoadingState> mStateMap = new LinkedHashMap<String, LoadingState>();
    private Map<String, Long> mStartTimeMap = new HashMap<String, Long>();
    private Map<String, T> mResultsMap = new HashMap<String, T>();

    /**
     * Adding given specs object to loading queue. If corresponding resource Uri was already scheduled for loading but was not yet loaded,
     * then this specs object will be appended to the list of pending specs for given Uri. When resource for given Uri will be loaded all
     * corresponding "waiting" specs objects will be notified. <br/>
     * <br/>
     * After specs were added {@code this.notify()} method will be called to wake up first waiting thread.
     */
    public synchronized void addSpecs(ResourceSpecs<T> specs) {
        String uri = specs.getUri();
        Set<ResourceSpecs<T>> list = mMap.get(uri);
        if (list == null) {
            list = new HashSet<ResourceSpecs<T>>();
            mMap.put(uri, list);
            mStartTimeMap.put(uri, System.currentTimeMillis() + specs.getDelay());
        }
        list.add(specs);

        LoadingState state = mStateMap.get(uri);
        if (state == null) {
            mStateMap.put(uri, LoadingState.WAIT_MANAGING);
        } else if (state == LoadingState.DOWNLOADING) {
            specs.onStart();
        }

        notify();
    }

    /**
     * @return Snapshot (copy) set of specs objects currently waiting for given Uri to be loaded.<br/>
     *         May return {@code null} if given Uri is no more in the loading queue. I.e. given Uri was already loaded and all waiting specs
     *         are already notified. Or if there are no more valid (not outdated) specs objects for given Uri.
     * @see isOutdated() method
     */
    public synchronized Set<ResourceSpecs<T>> getSpecsList(String uri) {
        return mMap.containsKey(uri) ? new HashSet<ResourceSpecs<T>>(mMap.get(uri)) : null;
    }

    /**
     * Specs object may be reused several times and new resource Uri can be set for loading. If another Uris was set for some of waiting
     * specs objects we should remove them from set of waiting objects for given Uri.<br/>
     * <br/>
     * This approach is designed for loading images into ListView or similar views where if items were scrolled very fast we should avoid
     * loading "outdated" and show them in wrong positions in list.
     * 
     * @param uri
     * @return True if all corresponding specs objects for given {@code uri} were outdated. False otherwise.
     */
    public synchronized boolean isOutdated(String uri) {
        Set<ResourceSpecs<T>> set = mMap.get(uri);
        if (set == null) return true;

        Iterator<ResourceSpecs<T>> iter = set.iterator();
        while (iter.hasNext()) {
            String actualUri = iter.next().getUri();
            if (actualUri == null || !actualUri.equals(uri)) iter.remove();
        }

        if (set.isEmpty()) {
            remove(uri);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Method to retrieve stored loaded object for given Uri.
     */
    public synchronized T getResult(String uri) {
        return mResultsMap.get(uri);
    }

    /**
     * Method for storing loaded object for given Uri.<br/>
     * Do nothing if given Uri is not in queue.
     */
    public synchronized void setResult(String uri, T res) {
        if (mMap.containsKey(uri)) mResultsMap.put(uri, res);
    }

    /**
     * Removes given Uri from queue and returns all corresponding sepcs objects.
     * 
     * @return May return {@code null} if specified {@code uri} was already deleted from queue (i.e. all specs were outdated)
     */
    public synchronized Set<ResourceSpecs<T>> remove(String uri) {
        mResultsMap.remove(uri);
        mStateMap.remove(uri);
        mStartTimeMap.remove(uri);
        return mMap.remove(uri);
    }

    /**
     * Setting current loading state for Uri.<br/>
     * Do nothing if given Uri is not in queue.
     * 
     * @see LoadingState
     */
    public synchronized void setState(String uri, LoadingState state) {
        if (mMap.containsKey(uri)) mStateMap.put(uri, state);
    }

    /**
     * Getting current loading state for Uri.
     * 
     * @return Loading state. May return {@code null} if given Uri was already removed from loading queue.
     * @see LoadingState
     */
    public synchronized LoadingState getState(String uri) {
        return mStateMap.get(uri);
    }

    /**
     * Finds and returns first Uri waiting to be managed.
     * 
     * @return First Uri to process. May return {@code null} if no Uris are waiting to be managed.
     * @see LoadingState.WAIT_MANAGING
     */
    public synchronized String getNextUriToManage(boolean skipDelayCheck) {
        for (String uri : mStateMap.keySet()) {
            if (mStateMap.get(uri) == LoadingState.WAIT_MANAGING
                    && (skipDelayCheck || System.currentTimeMillis() - mStartTimeMap.get(uri) > 0)) {
                return uri;
            }
        }
        return null;
    }

    public static enum LoadingState {
        WAIT_MANAGING, MANAGING, WAIT_DOWNLOADING, DOWNLOADING, WAIT_LOADING, LOADING, WAIT_DISPLAYING;
    }

}
