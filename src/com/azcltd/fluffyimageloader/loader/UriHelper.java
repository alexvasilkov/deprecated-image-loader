package com.azcltd.fluffyimageloader.loader;

import android.content.ContentResolver;
import android.net.Uri;

class UriHelper {

    private String mUriScheme;

    public UriHelper(String uri) {
        mUriScheme = uri == null ? null: Uri.parse(uri).getScheme();
    }

    public boolean isLocal() {
        return
            ContentResolver.SCHEME_FILE.equals(mUriScheme) ||
            ContentResolver.SCHEME_CONTENT.equals(mUriScheme) ||
            ContentResolver.SCHEME_ANDROID_RESOURCE.equals(mUriScheme);
    }

    public boolean isRemote() {
        return
            "http".equals(mUriScheme) ||
            "https".equals(mUriScheme) ||
            "ftp".equals(mUriScheme);
    }

}
