package com.azcltd.fluffyimageloader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.content.Context;

public class DiskUriCache {

    private static final int BUFFER_SIZE = 2048;

    private Context mAppContext;
    private int mMaxExternalDiskUsage;
    private int mMaxInternalDiskUsage;

    public DiskUriCache(Context appContext, int maxExternalCacheSize, int maxInternalCacheSize) {
        mAppContext = appContext;
        mMaxExternalDiskUsage = maxExternalCacheSize;
        mMaxInternalDiskUsage = maxInternalCacheSize;

        DiskUtils.createNomediaFile(appContext, DiskUtils.getCacheDir(appContext));
    }

    /**
     * @param uri
     *            Source Uri of resource
     * @param in
     *            InputStream to save on disk. Will be closed at the end.
     */
    public boolean save(String uri, InputStream in) {
        if (in == null) return false;

        OutputStream out = null;
        File file = null;
        try {
            file = DiskUtils.getCacheFileForUri(mAppContext, uri);
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

    public void delete(String uri) {
        try {
            DiskUtils.getCacheFileForUri(mAppContext, uri).delete();
        } catch (FileNotFoundException e) {
        }
    }

    public boolean isExists(String uri) {
        return getPath(uri) != null;
    }

    /**
     * @return Uri of the cached file
     */
    public String get(String uri) {
        return DiskUtils.toUri(getPath(uri));
    }

    public String getPath(String uri) {
        try {
            File file = DiskUtils.getCacheFileForUri(mAppContext, uri);
            return file.exists() ? file.getAbsolutePath() : null;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    public void clean() {
        DiskUtils.cleanDir(DiskUtils.getCacheDir(mAppContext));
    }

}
