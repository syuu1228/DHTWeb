const Cc = Components.classes;
const Ci = Components.interfaces;

const EXTENSION_PATH = Cc["@mozilla.org/extensions/manager;1"].getService(Ci.nsIExtensionManager).getInstallLocation("dhtfox@dhtfox.org").getItemLocation("dhtfox@dhtfox.org");
const LOGBACK_FILE_PATH = EXTENSION_PATH.path.replace(/\\/g,"/") + "/java/logback.xml";
let EXPORTED_SYMBOLS = ["DHTFox"];
var consoleService = Components.classes["@mozilla.org/consoleservice;1"].
     getService(Components.interfaces.nsIConsoleService);
     
let DHTFox = {
	javaInstance: null,
	start: function(myClass) {
		if (this.javaInstance != null)
			return true;
		this.javaInstance = myClass.newInstance();
		this.javaInstance.invokeBackground(["-N", "-H 8080", "-l " + LOGBACK_FILE_PATH, "-x abc", "-p 9999", "125.6.175.11:3997"]);
	},
	isStarted: function() {
		if (this.javaInstance == null)
			return false;
		return true;
	}
};
