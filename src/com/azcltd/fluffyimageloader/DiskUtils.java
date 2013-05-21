package com.azcltd.fluffyimageloader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Comparator;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

public class DiskUtils {

    private static final String DIR_EXTERNAL_CACHE = "Android/data/%s/cache";
    private static final String DIR_INTERNAL_CACHE = "/cache";
    private static final String DIR_CAMERA_PHOTOS = "Android/data/%s/camera";
    private static final String DIR_TEMP = "Android/data/%s/temp";

    private static boolean sIsUsingInternalCache;

    public static File getCacheDir(Context context) {
        File cacheDir = null;
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File externalDir = Environment.getExternalStorageDirectory();
            if (externalDir != null) {
                cacheDir = new File(externalDir, String.format(DIR_EXTERNAL_CACHE, context.getPackageName()));
                sIsUsingInternalCache = false;
            }
        }

        if (cacheDir == null) {
            sIsUsingInternalCache = true;
            cacheDir = new File(context.getCacheDir(), DIR_INTERNAL_CACHE);
        }

        cacheDir.mkdirs();
        return cacheDir;
    }

    public static boolean isInternalCacheUsed() {
        return sIsUsingInternalCache;
    }

    public static File getCacheFileForUri(Context context, String uri) throws FileNotFoundException {
        if (uri == null) throw new FileNotFoundException("Uri for file is null");
        return new File(getCacheDir(context), getFileNameFromUri(uri));
    }

    /**
     * May return null if SD card is not available
     */
    public static File getFileForNewPhoto(Context context, String ext) {
        return getRandomFile(context, ext, DIR_CAMERA_PHOTOS);
    }

    public static File getTempFile(Context context, String ext) {
        return getRandomFile(context, ext, DIR_TEMP);
    }

    public static File getRandomFile(Context context, String ext, String dir) {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File externalDir = Environment.getExternalStorageDirectory();
            if (externalDir != null) {
                File photosDir = new File(externalDir, String.format(dir, context.getPackageName()));
                photosDir.mkdirs();
                createNomediaFile(context, photosDir);

                File photo = createRandomFile(photosDir, ext);
                try {
                    photo.createNewFile();
                } catch (IOException e) {
                    return null;
                }
                return photo;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public static File createRandomFile(File dir, String ext) {
        if (!dir.isDirectory()) return null;
        File[] files = dir.listFiles();

        while (true) {
            String fileName = String.valueOf((int) (Math.random() * 1e10)) + '.' + ext;
            boolean found = false;

            if (files != null) {
                for (File file : files) {
                    if (file.getName().equals(fileName)) {
                        found = true;
                        break;
                    }
                }
            }

            if (!found) return new File(dir, fileName);
        }
    }

    /**
     * @return Name of file from given Uri. May be null
     */
    public static String getFileNameFromUri(String uri) {
        if (uri == null) return null;
        try {
            String lastPart = uri.substring(uri.lastIndexOf("/") + 1);
            String name = uri.hashCode() + '-' + URLEncoder.encode(lastPart, "UTF-8");
            return name.replace('%', '_'); // To use in Uri (avoid encoding)
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    /**
     * Converts file path to URI string (appending file:// if needed)
     */
    public static String toUri(String filePath) {
        if (filePath == null || filePath.length() == 0) return null;
        return filePath.startsWith("file://") ? filePath : "file://" + filePath;
    }

    public static void createNomediaFile(Context context, File dir) {
        try {
            File nomedia = new File(dir, ".nomedia");
            if (!isInternalCacheUsed() && !nomedia.exists() && nomedia.createNewFile()) {
                Intent intent = new Intent(Intent.ACTION_MEDIA_MOUNTED);
                intent.setData(Uri.fromFile(Environment.getExternalStorageDirectory()));
                context.sendBroadcast(intent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void keepCacheDirWithinSize(Context context, int internalSize, int externalSize) {
        keepDirWithinSize(getCacheDir(context), isInternalCacheUsed() ? internalSize : externalSize);
    }

    public static void keepDirWithinSize(File dir, int size) {
        if (!dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        long dirSize = countDirSize(dir);

        if (dirSize < size) return;

        Arrays.sort(files, new FileDateComparator());
        int count = files.length;
        File file;
        for (int i = 0; i < count; i++) {
            file = files[i];
            long currentSize = file.isDirectory() ? 0l : file.length();
            if (file.delete()) {
                dirSize -= currentSize;
                if (dirSize < size) break;
            }
        }
    }

    private static long countDirSize(File dir) {
        if (!dir.isDirectory()) return -1;
        File[] files = dir.listFiles();
        if (files == null) return 0;

        long dirSize = 0L;
        for (File file : files) {
            dirSize += file.isDirectory() ? 0L : file.length();
        }
        return dirSize;
    }

    public static void cleanDir(File dir) {
        if (!dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files)
            file.delete();
    }

    public static void removeFile(String filePath) {
        if (null == filePath) return;
        File file = new File(filePath);
        if (file.isFile()) file.delete();
    }

    private static class FileDateComparator implements Comparator<File> {
        @Override
        public int compare(File f1, File f2) {
            return (int) (f1.lastModified() - f2.lastModified());
        }
    }
}
