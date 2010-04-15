const Cc = Components.classes;
const Ci = Components.interfaces;

const EXTENSION_PATH = Cc["@mozilla.org/extensions/manager;1"].getService(Ci.nsIExtensionManager).getInstallLocation("dhtfox@dhtfox.org").getItemLocation("dhtfox@dhtfox.org");
const LOGBACK_FILE_PATH = EXTENSION_PATH.path.replace(/\\/g,"/") + "/java/logback.xml";
let EXPORTED_SYMBOLS = ["DHTFox"];
var consoleService = Components.classes["@mozilla.org/consoleservice;1"].
     getService(Components.interfaces.nsIConsoleService);

function LoggerCallback() {
    this.log = function(msg) {
        consoleService.logStringMessage(msg);
    }
    this.error = function(msg) {
        consoleService.logStringMessage(msg);
    }
}

var cacheCounter = 0;
function CacheCallback() {
    this.getCacheEntry = function(url) {
        const cacheService = Cc["@mozilla.org/network/cache-service;1"].getService(Ci.nsICacheService);
        try {
            var cacheSession = cacheService.createSession("HTTP", Ci.nsICache.STORE_ANYWHERE, true);
        	var cacheEntry = cacheSession.openCacheEntry(url, Ci.nsICache.ACCESS_READ, true);
        	return cacheEntry;
        } catch(e) {
            return null;
        }
    }
    this.readAll = function(cacheEntry) {
        try {
            var iStream = cacheEntry.openInputStream(0);
            var bStream = Cc["@mozilla.org/binaryinputstream;1"].createInstance(Ci.nsIBinaryInputStream);
            bStream.setInputStream(iStream);
		    
            var filePath = EXTENSION_PATH.clone();
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
	    consoleService.logStringMessage(e);
            return null;
        }
    }
}

let DHTFox = {
	javaInstance: null,
	start: function(myClass) {
		if (this.javaInstance != null)
			return true;
		this.javaInstance = myClass.newInstance();
		return this.javaInstance.start("aaa", false, "125.6.175.11:3997", 3997, 8080, new CacheCallback(), new LoggerCallback(), LOGBACK_FILE_PATH);
	},
	stop: function() {
		this.javaInstance.stop();
	},
	isStarted: function() {
		if (this.javaInstance == null)
			return false;
		return true;
	}
};
