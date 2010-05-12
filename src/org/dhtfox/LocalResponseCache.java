package org.dhtfox;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.CacheRequest;
import java.net.CacheResponse;
import java.net.HttpURLConnection;
import java.net.ResponseCache;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalResponseCache extends ResponseCache {

    final static Logger logger = LoggerFactory.getLogger(LocalResponseCache.class);
    public static final boolean IS_CACHE_DISABLED = false;

    static {
        if (IS_CACHE_DISABLED) {
            System.out.println("[AERITH] Cache disabled");
        }
    }
    public static final File CHACHE_DIR = new File(
            System.getProperty("user.home") + File.separator + ".dhtfox");

    static {
        if (!CHACHE_DIR.exists()) {
            CHACHE_DIR.mkdir();
        }
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private LocalResponseCache() {
    }

    public static void installResponseCache() {
        if (!IS_CACHE_DISABLED) {
            ResponseCache.setDefault(new LocalResponseCache());
        }
    }

    /**
     * Returns the local File corresponding to the given remote URI.
     */
    public static File getLocalFile(URI remoteUri) {
        if (remoteUri.getPath().matches("^/requests/")) {
            try {
                remoteUri = new URI(remoteUri.getPath().replaceFirst("^/requests/", ""));
            } catch (URISyntaxException ex) {
                logger.warn(ex.getMessage(), ex);
            }
        }
        String fileName = null;
		try {
			fileName = URLEncoder.encode(remoteUri.toString(), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			logger.warn(e.getMessage(), e);
		}
        logger.info("getLocalFile uri:{} fileName:{}", remoteUri, fileName);
        return new File(CHACHE_DIR, fileName);
    }

    public static File getLocalHeader(URI remoteUri) {
        if (remoteUri.getPath().matches("^/requests/")) {
            try {
                remoteUri = new URI(remoteUri.getPath().replaceFirst("^/requests/", ""));
            } catch (URISyntaxException ex) {
                logger.warn(ex.getMessage(), ex);
            }
        }
        String fileName = null;
		try {
			fileName = URLEncoder.encode(remoteUri.toString(), "UTF-8") + ".header";
		} catch (UnsupportedEncodingException e) {
			logger.warn(e.getMessage(), e);
		}
        logger.info("getLocalHeader: uri{} fileName:{}", remoteUri, fileName);
        return new File(CHACHE_DIR, fileName);
    }

    /**
     * Returns true if the resource at the given remote URI is newer than
     * the resource cached locally.
     */
    private static boolean isUpdateAvailable(URI remoteUri, File localFile) {
        return false;
        /*
        URLConnection conn;
        try {
            conn = remoteUri.toURL().openConnection();
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
            return false;
        } catch (IOException ex) {
            ex.printStackTrace();
            return false;
        }
        if (!(conn instanceof HttpURLConnection)) {
            // don't bother with non-http connections
            return false;
        }

        long localLastMod = localFile.lastModified();
        long remoteLastMod = 0L;
        HttpURLConnection httpconn = (HttpURLConnection) conn;
        // disable caching so we don't get in feedback loop with ResponseCache
        httpconn.setUseCaches(false);
        try {
            httpconn.connect();
            remoteLastMod = httpconn.getLastModified();
        } catch (IOException ex) {
            //ex.printStackTrace();
            return false;
        } finally {
            httpconn.disconnect();
        }
        logger.info("isUpdateAvaiable:{},{}", remoteUri, (remoteLastMod > localLastMod));
        return (remoteLastMod > localLastMod);
         */
    }

    @Override
    public CacheResponse get(URI uri, String rqstMethod,
            Map<String, List<String>> rqstHeaders)
            throws IOException {
        File localFile = getLocalFile(uri);
        File localHeader = getLocalHeader(uri);
        if (!localFile.exists() || !localHeader.exists()) {
            // the file isn't already in our cache, return null
            return null;
        }

        if (isUpdateAvailable(uri, localFile)) {
            // there is an update available, so don't return cached version
            return null;
        }

        if (!localFile.exists() || !localHeader.exists()) {
            // the file isn't already in our cache, return null
            return null;
        }
        logger.info("get:{}", uri);
        return new LocalCacheResponse(localFile, localHeader);
    }

    @Override
    public CacheRequest put(URI uri, URLConnection conn)
            throws IOException {
        // only cache http(s) GET requests
        if (!(conn instanceof HttpURLConnection)
                || !(((HttpURLConnection) conn).getRequestMethod().equals("GET"))) {
            return null;
        }
        logger.info("put:{}", uri);
        File localFile = getLocalFile(uri);
        File localHeader = getLocalHeader(uri);
        return new LocalCacheRequest(localFile, localHeader, conn.getHeaderFields());
    }

    private class LocalCacheResponse extends CacheResponse {

        private FileInputStream fis;
        private Map<String, List<String>> headers = null;

        private LocalCacheResponse(File localFile, File localHeader) {
            FileInputStream fisHeader = null;
            ObjectInputStream ois = null;
            try {
                this.fis = new FileInputStream(localFile);
                fisHeader = new FileInputStream(localHeader);
                ois = new ObjectInputStream(fisHeader);
                this.headers = (Map<String, List<String>>) ois.readObject();
            } catch (ClassNotFoundException ex) {
                logger.warn(ex.getMessage(), ex);
            } catch (IOException ex) {
                logger.warn(ex.getMessage(), ex);
            } finally {
                try {
                    fisHeader.close();
                    ois.close();
                } catch (IOException e) {
                }
            }
        }

        @Override
        public Map<String, List<String>> getHeaders()
                throws IOException {
            return headers;
        }

        @Override
        public InputStream getBody()
                throws IOException {
            return fis;
        }
    }

    private class LocalCacheRequest extends CacheRequest {

        private final File localFile, localHeader;
        private FileOutputStream fos, fosHeader;

        private LocalCacheRequest(File localFile, File localHeader, Map<String, List<String>> headerFields) {
            this.localFile = localFile;
            this.localHeader = localHeader;
            {
                ObjectOutputStream oos = null;
                try {
                    this.fos = new FileOutputStream(localFile);
                    this.fosHeader = new FileOutputStream(localHeader);
                    oos = new ObjectOutputStream(fosHeader);
                    oos.writeObject(headerFields);
                } catch (IOException ex) {
                    logger.warn(ex.getMessage(), ex);
                } finally {
                    try {
                        oos.close();
                        this.fosHeader.close();
                    } catch (IOException ex) {
                        logger.warn(ex.getMessage(), ex);
                    }
                }
            }
        }

        @Override
        public OutputStream getBody()
                throws IOException {
            return fos;
        }

        @Override
        public void abort() {
            // abandon the cache attempt by closing the stream and deleting
            // the local file
            try {
                fos.close();
                localFile.delete();
            } catch (IOException e) {
            }
            try {
                fosHeader.close();
                localHeader.delete();
            } catch (IOException e) {
            }
        }
    }
}
