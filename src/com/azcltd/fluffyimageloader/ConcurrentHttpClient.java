package com.azcltd.fluffyimageloader;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRoute;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.protocol.HttpContext;

public class ConcurrentHttpClient extends DefaultHttpClient {

    private static final int DEFAULT_TIMEOUT = 20000;
    private static final String ACCEPT_ENCODING = "Accept-Encoding";

    private ConcurrentHttpClient(ClientConnectionManager cm, BasicHttpParams params) {
        super(cm, params);
    }

    public static ConcurrentHttpClient createHttpClient(int maxConnections) {
        return createHttpClient(DEFAULT_TIMEOUT, maxConnections);
    }

    public static ConcurrentHttpClient createHttpClient(int timeout, final int maxConnections) {
        BasicHttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, timeout);
        HttpConnectionParams.setSoTimeout(params, timeout);
        ConnManagerParams.setMaxTotalConnections(params, maxConnections);
        ConnManagerParams.setMaxConnectionsPerRoute(params, new ConnPerRoute() {
            @Override
            public int getMaxForRoute(HttpRoute route) {
                return maxConnections;
            }
        });

        // Creating http client with thread safe connection manager
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        final SSLSocketFactory sslSocketFactory = SSLSocketFactory.getSocketFactory();
        schemeRegistry.register(new Scheme("https", sslSocketFactory, 443));
        ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);

        ConcurrentHttpClient httpClient = new ConcurrentHttpClient(cm, params);

        // Adding gzipping
        httpClient.addRequestInterceptor(new HttpRequestInterceptor() {
            public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
                if (!request.containsHeader(ACCEPT_ENCODING)) request.addHeader(ACCEPT_ENCODING, "gzip");
            }
        });

        httpClient.addResponseInterceptor(new HttpResponseInterceptor() {
            public void process(HttpResponse response, HttpContext context) throws HttpException, IOException {
                HttpEntity entity = response.getEntity();
                if (entity == null) return;
                Header ceheader = entity.getContentEncoding();
                if (ceheader == null) return;
                for (HeaderElement el : ceheader.getElements()) {
                    if (el.getName().equalsIgnoreCase("gzip")) {
                        response.setEntity(new GzipDecompressingEntity(response.getEntity()));
                        return;
                    }
                }
            }
        });

        return httpClient;
    }

    private static class GzipDecompressingEntity extends HttpEntityWrapper {

        public GzipDecompressingEntity(final HttpEntity entity) {
            super(entity);
        }

        public InputStream getContent() throws IOException, IllegalStateException {
            return new GZIPInputStream(wrappedEntity.getContent());
        }

        public long getContentLength() {
            return -1; // length of ungzipped content is not known
        }
    }

}
