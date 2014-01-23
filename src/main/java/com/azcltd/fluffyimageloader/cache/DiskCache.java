package com.azcltd.fluffyimageloader.cache;

import android.content.Context;

import java.io.*;

public class DiskCache {

    private static final int BUFFER_SIZE = 2048;

    private Context mAppContext;
    private int mMaxExternalDiskUsage;
    private int mMaxInternalDiskUsage;

    public DiskCache(Context appContext, int maxExternalCacheSize, int maxInternalCacheSize) {
        mAppContext = appContext;
        mMaxExternalDiskUsage = maxExternalCacheSize;
        mMaxInternalDiskUsage = maxInternalCacheSize;

        DiskUtils.createNomediaFile(appContext, DiskUtils.getCacheDir(appContext));
    }

    /**
     * @param key
     *            Resource key
     * @param in
     *            InputStream to save on disk. Will be closed at the end.
     */
    public boolean save(String key, InputStream in) {
        if (in == null) return false;

        OutputStream out = null;
        File file = null;
        try {
            file = DiskUtils.getCacheFileForName(mAppContext, key);
            file.getParentFile().mkdirs();
            out = new FileOutputStream(file);
            // Copying in to out
            byte[] buffer = new byte[BUFFER_SIZE];
            int c;
            while ((c = in.read(buffer)) != -1) {
                out.write(buffer, 0, c);
            }
        } catch (Throwable e) {
            if(file != null) file.delete();
            return false;
        } finally {
            if (in != null) try {
                in.close();
            } catch (IOException e) {
            }
            if (out != null) try {
                out.close();
            } catch (IOException e) {
            }
        }
        DiskUtils.keepCacheDirWithinSize(mAppContext, mMaxInternalDiskUsage, mMaxExternalDiskUsage);
        return file.exists(); // may be cleaned while keepDirWithinSize
    }

    public void delete(String key) {
        try {
            DiskUtils.getCacheFileForName(mAppContext, key).delete();
        } catch (FileNotFoundException e) {
            // Not in the cache
        }
    }

    public boolean isExists(String key) {
        return getPath(key) != null;
    }

    /**
     * @return Path for cached file
     */
    public String get(String key) {
        return DiskUtils.toUri(getPath(key));
    }

    private String getPath(String key) {
        try {
            File file = DiskUtils.getCacheFileForName(mAppContext, key);
            return file.exists() ? file.getAbsolutePath() : null;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    public void clean() {
        DiskUtils.cleanDir(DiskUtils.getCacheDir(mAppContext));
    }

}
