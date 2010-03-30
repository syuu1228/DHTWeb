//Get extension folder installation path...
var extensionPath = Components.classes["@mozilla.org/extensions/manager;1"].
            getService(Components.interfaces.nsIExtensionManager).
            getInstallLocation("dhtfox@dhtfox.org"). // guid of extension
            getItemLocation("dhtfox@dhtfox.org");

// Get path to the JAR files (the following assumes your JARs are within a
// directory called "java" at the root of your extension's folder hierarchy)
// You must add this utilities (classloader) JAR to give your extension full privileges
var classLoaderJarpath = "file:///" + extensionPath.path.replace(/\\/g,"/") + "/java/javaFirefoxExtensionUtils.jar";
// Add the paths for all the other JAR files that you will be using
var dhtFoxJarpath = "file:///" + extensionPath.path.replace(/\\/g,"/") + "/java/DHTFox.jar"; // seems you don't actually have to replace the backslashes as they work as well
//var clinkJarpath = "file:///" + extensionPath.path.replace(/\\/g,"/") + "/java/clink170.jar";
var glueJarpath = "file:///" + extensionPath.path.replace(/\\/g,"/") + "/java/MozillaGlue.jar";
var interfacesJarpath = "file:///" + extensionPath.path.replace(/\\/g,"/") + "/java/MozillaInterfaces.jar";

var urlArray = []; // Build a regular JavaScript array (LiveConnect will auto-convert to a Java array)
urlArray[0] = new java.net.URL(dhtFoxJarpath);
urlArray[1] = new java.net.URL(classLoaderJarpath);

//urlArray[1] = new java.net.URL(glueJarpath);
//urlArray[2] = new java.net.URL(interfaceJarpath);
//urlArray[3] = new java.net.URL(classLoaderJarpath);

var cl = java.net.URLClassLoader.newInstance(urlArray);

//var myClass = cl.loadClass('org.dhtfox.DHTFox'); // use the same loader from above
var myClass = cl.loadClass('TestClass'); // use the same loader from above
//var kvs = myClass.newInstance(new java.lang.String('abc'), new java.lang.Boolean(true));
var kvs = myClass.newInstance();
var path = kvs.get("http://www.mozilla-japan.org/images/menu_back.gif");
alert("path:"+path);

function CallbackTest() {
	this.callback = function(url) {
		const cacheService = Components.classes["@mozilla.org/network/cache-service;1"].getService(Components.interfaces.nsICacheService);
		var cacheSession = cacheService.createSession("HTTP", Components.interfaces.nsICache.STORE_ANYWHERE, true);
		cacheSession.doomEntriesIfExpired = false;
		var cacheEntry = cacheSession.openCacheEntry(url, Components.interfaces.nsICache.ACCESS_READ, true);
		cacheEntry.close();
	}
}
var ret = kvs.testObj(new CallbackTest(), "http://hogehoge");
alert(ret);

//try {
//	kvs.initialize('abc', false);
//	kvs.join('125.6.175.11:3997');
//} catch(e) {
//	alert(e);
//}

function get() {
	try {
		var key = document.getElementById('key').value;
		var value = kvs.get(key);
		document.getElementById('value').value = value;
	} catch (e) {
		alert(e);
	}
}

function put() {
	try {
		var key = document.getElementById('key').value;
		var value = document.getElementById('value').value;
		kvs.put(key, value);
		document.getElementById('key').value = '';
		document.getElementById('value').value = '';
	} catch (e) {
		alert(e);
	}
}
