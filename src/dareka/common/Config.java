package dareka.common;

public class Config {
    @Deprecated
    public static final String LISTEN_PORT = "listenPort";
    @Deprecated
    public static final String PROXY_HOST = "proxyHost";
    @Deprecated
    public static final String PROXY_PORT = "proxyPort";
    @Deprecated
    public static final String TITLE = "title";

    @Deprecated
    private int listenPort = 8080;
    @Deprecated
    private String proxyHost = "";
    @Deprecated
    private int proxyPort = 8081;
    @Deprecated
    private boolean title = true;

    // [nl] staticな型別デフォルト値付きgetter
    public static boolean getBoolean(String key, boolean def) {
        String value = System.getProperty(key);
        if (value == null) {
            return def;
        } else {
            return Boolean.parseBoolean(value);
        }
    }

    public static String getString(String key, String def) {
        return System.getProperty(key, def);
    }

    public static int getInteger(String key, int def) {
        String value = System.getProperty(key);
        if (value == null) {
            return def;
        } else {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return def;
            }
        }
    }

    @Deprecated
    public int getListenPort() {
        return listenPort;
    }

    @Deprecated
    public void setListenPort(int listenPort) {
        if (listenPort < 1 || 65535 < listenPort) {
            throw new IllegalArgumentException("invalid listen port: "
                    + listenPort);
        }
        this.listenPort = listenPort;
    }

    @Deprecated
    public String getProxyHost() {
        return proxyHost;
    }

    @Deprecated
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    @Deprecated
    public int getProxyPort() {
        return proxyPort;
    }

    @Deprecated
    public void setProxyPort(int proxyPort) {
        if (proxyPort < 1 || 65535 < proxyPort) {
            throw new IllegalArgumentException("invalid proxy port: "
                    + proxyPort);
        }
        this.proxyPort = proxyPort;
    }

    @Deprecated
    public boolean isTitle() {
        return title;
    }

    @Deprecated
    public void setTitle(boolean title) {
        this.title = title;
    }
}
