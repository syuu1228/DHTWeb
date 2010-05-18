Components.utils.import("resource://dhtfox/dhtfox.jsm");

const EXTENSION_PATH = Cc["@mozilla.org/extensions/manager;1"].getService(Ci.nsIExtensionManager).getInstallLocation("dhtfox@dhtfox.org").getItemLocation("dhtfox@dhtfox.org");
const JAR_DIR_PATH = "file:///" + EXTENSION_PATH.path.replace(/\\/g,"/") + "/java/";

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

function startDHT() {
	try {
		if(!DHTFox.isStarted()) {
			var urlArray = [];
			urlArray[0] = new java.net.URL(JAR_DIR_PATH + "clink170.jar");
			urlArray[1] = new java.net.URL(JAR_DIR_PATH + "commons-cli-1.2.jar");
			urlArray[2] = new java.net.URL(JAR_DIR_PATH + "DHTFox.jar");
			urlArray[3] = new java.net.URL(JAR_DIR_PATH + "javaFirefoxExtensionUtils.jar");
			urlArray[4] = new java.net.URL(JAR_DIR_PATH + "je-4.0.103.jar");
			urlArray[5] = new java.net.URL(JAR_DIR_PATH + "jul-to-slf4j-1.5.11.jar");
			urlArray[6] = new java.net.URL(JAR_DIR_PATH + "logback-classic-0.9.20.jar");
			urlArray[7] = new java.net.URL(JAR_DIR_PATH + "logback-core-0.9.20.jar");
			urlArray[8] = new java.net.URL(JAR_DIR_PATH + "slf4j-api-1.5.11.jar");
			
			var cl = java.net.URLClassLoader.newInstance(urlArray);
			policyAdd(cl, urlArray);
			var myClass = cl.loadClass('org.dhtfox.DHTFox');
			var result = DHTFox.start(myClass);
		}
	} catch (e) {
	    alert(e);
	}
}
function eventListener(ev) {
	try {
		var target = ev.target.wrappedJSObject;
		target.dhtFoxEnabled = 1;
		target.dhtFoxProxyURL = "http://127.0.0.1:8080/proxy/";
	} catch (ex) {
		alert(ex);
	}
}

window.addEventListener("load", function() {startDHT();}, true);
window.addEventListener("DHTFoxRequest", eventListener, false, true);
