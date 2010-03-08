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

var urlArray = []; // Build a regular JavaScript array (LiveConnect will auto-convert to a Java array)
urlArray[0] = new java.net.URL(dhtFoxJarpath);
//urlArray[1] = new java.net.URL(clinkJarpath);
//urlArray[2] = new java.net.URL(classLoaderJarpath);
urlArray[1] = new java.net.URL(classLoaderJarpath);
var cl = java.net.URLClassLoader.newInstance(urlArray);

var myClass = cl.loadClass('org.dhtfox.DHTFox'); // use the same loader from above
//var kvs = myClass.newInstance(new java.lang.String('abc'), new java.lang.Boolean(true));
var kvs = myClass.newInstance();
alert(kvs);

try {
	kvs.initialize('abc', false);
	kvs.join('125.6.175.11:3997');
} catch(e) {
	alert(e);
}

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
