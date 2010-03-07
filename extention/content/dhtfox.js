//Get extension folder installation path...
var extensionPath = Components.classes["@mozilla.org/extensions/manager;1"].
            getService(Components.interfaces.nsIExtensionManager).
            getInstallLocation("dhtfox@dhtfox.org"). // guid of extension
            getItemLocation("dhtfox@dhtfox.org");

// Get path to the JAR files (the following assumes your JARs are within a
// directory called "java" at the root of your extension's folder hierarchy)
// You must add this utilities (classloader) JAR to give your extension full privileges
var classLoaderJarpath = "file:///" + extensionPath.path.replace(/(J\\(B/g,"/") + "/java/javaFirefoxExtensionUtils.jar";
// Add the paths for all the other JAR files that you will be using
var myJarpath = "file:///" + extensionPath.path.replace(/(J\\(B/g,"/") +
"/java/dhtfox.jar"; // seems you don't actually have to replace the backslashes as they work as well

var urlArray = []; // Build a regular JavaScript array (LiveConnect will auto-convert to a Java array)
urlArray[0] = new java.net.URL(myJarpath);
urlArray[1] = new java.net.URL(classLoaderJarpath);
var cl = java.net.URLClassLoader.newInstance(urlArray);

var myClass = cl.loadClass('org.dhtfox.DHTFox'); // use the same loader from above
//var kvs = myClass.newInstance('abc', true);
var kvs = null;
//kvs.join('125.6.175.11:3997');

function get() {
	var key = document.getElementById('key').value;
	var value = kvs.get(key);
	document.getElementById('value').value = value;
}

function put() {
	var key = document.getElementById('key').value;
	var value = document.getElementById('value').value;
	kvs.put(key, value);
	document.getElementById('key').value = '';
	document.getElementById('value').value = '';
}
