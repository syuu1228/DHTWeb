function policyAdd (loader, urls) {
    try {
        var str = 'edu.mit.simile.javaFirefoxExtensionUtils.URLSetPolicy';
        var policyClass = java.lang.Class.forName(
            str,
            true,
            loader
            );
        var policy = policyClass.newInstance();
        policy.setOuterPolicy(java.security.Policy.getPolicy());
        java.security.Policy.setPolicy(policy);
        policy.addPermission(new java.security.AllPermission());
        for (var j=0; j < urls.length; j++) {
            policy.addURL(urls[j]);
        }
    }catch(e) {
        alert(e+'::'+e.lineNumber);
    }
}

const Cc = Components.classes;
const Ci = Components.interfaces;
const extensionPath = Cc["@mozilla.org/extensions/manager;1"].getService(Ci.nsIExtensionManager).getInstallLocation("dhtfox@dhtfox.org").getItemLocation("dhtfox@dhtfox.org");
const jarDirPath = "file:///" + extensionPath.path.replace(/\\/g,"/") + "/java/";

var urlArray = [];
urlArray[0] = new java.net.URL(jarDirPath + "javaFirefoxExtensionUtils.jar");
urlArray[1] = new java.net.URL(jarDirPath + "DHTFox.jar");
urlArray[2] = new java.net.URL(jarDirPath + "jul-to-slf4j-1.5.11.jar");
urlArray[3] = new java.net.URL(jarDirPath + "miniupnpc.jar");
urlArray[4] = new java.net.URL(jarDirPath + "slf4j-api-1.5.11.jar");
urlArray[5] = new java.net.URL(jarDirPath + "FirefoxLogger.jar");

function LoggerCallback() {
    this.log = function(msg) {
        var consoleService = Cc["@mozilla.org/consoleservice;1"]
        .getService(Ci.nsIConsoleService);
        consoleService.logStringMessage(msg);
    }
    this.error = function(msg) {
        var consoleService = Cc["@mozilla.org/consoleservice;1"]
        .getService(Ci.nsIConsoleService);
        consoleService.logStringMessage(msg);
    }
}

var cacheCounter = 0;
function CacheCallback() {
    this.getCacheEntry = function(url) {
        try {
            const cacheService = Cc["@mozilla.org/network/cache-service;1"].getService(Ci.nsICacheService);
            var cacheSession = cacheService.createSession("HTTP", Ci.nsICache.STORE_ANYWHERE, true);
            cacheSession.doomEntriesIfExpired = false;
            var cacheEntry = cacheSession.openCacheEntry(url, Ci.nsICache.ACCESS_READ, true);
            return cacheEntry;
        } catch(e) {
            alert(e);
            return null;
        }
    }
    this.readAll = function(cacheEntry) {
        try {
            var iStream = cacheEntry.openInputStream(0);
            var bStream = Cc["@mozilla.org/binaryinputstream;1"].createInstance(Ci.nsIBinaryInputStream);
            bStream.setInputStream(iStream);
		    
            var filePath = extensionPath.clone();
            filePath.append("temp");
            filePath.append(cacheCounter++);
            var aFile = Cc["@mozilla.org/file/local;1"].createInstance(Ci.nsILocalFile);
            aFile.initWithPath(filePath.path);
		    
            var outstream = Cc["@mozilla.org/network/safe-file-output-stream;1"].createInstance(Ci.nsIFileOutputStream);
            outstream.init(aFile, 0x02 | 0x08 | 0x20, 0664, 0);
            var bytes = bStream.readBytes(cacheEntry.dataSize);
			
            outstream.write(bytes, bytes.length);
            if (outstream instanceof Ci.nsISafeOutputStream) {
                outstream.finish();
            } else {
                outstream.close();
            }
            bStream.close();
            iStream.close();
            cacheEntry.close();
            return filePath.path;
        } catch(e) {
            alert(e);
            return null;
        }
    }
}

try {
    var cl = java.net.URLClassLoader.newInstance(urlArray);
    policyAdd(cl, urlArray);
    var myClass = cl.loadClass('org.dhtfox.DHTFox');
    var dht = myClass.newInstance();
    var success = dht.start("aaa", false, "125.6.175.11:3997", 3997, 8080, new CacheCallback(), new LoggerCallback());
    if (!success)
        alert("dht start failed");
} catch (e) {
    alert(e);
}
