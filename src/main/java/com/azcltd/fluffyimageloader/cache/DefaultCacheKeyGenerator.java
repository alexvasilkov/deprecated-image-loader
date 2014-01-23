package com.azcltd.fluffyimageloader.cache;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class DefaultCacheKeyGenerator implements ICacheKeyGenerator {

    @Override
    public String toCacheKey(String uri) {
        if (uri == null) return null;
        try {
            String lastPart = uri.substring(uri.lastIndexOf("/") + 1);
            String name = uri.hashCode() + '-' + URLEncoder.encode(lastPart, "UTF-8");
            return name.replace('%', '_'); // To use in other Uri (avoid encoding)
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

}
