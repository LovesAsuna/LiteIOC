package com.hyosakura.liteioc.util

import org.jetbrains.annotations.Nullable
import java.io.File
import java.io.FileNotFoundException
import java.net.*
import java.util.*

object ResourceUtil {

    const val CLASSPATH_URL_PREFIX = "classpath:"

    const val FILE_URL_PREFIX = "file:"

    const val JAR_URL_PREFIX = "jar:"

    const val WAR_URL_PREFIX = "war:"

    const val URL_PROTOCOL_FILE = "file"

    const val URL_PROTOCOL_JAR = "jar"

    const val URL_PROTOCOL_WAR = "war"

    const val URL_PROTOCOL_ZIP = "zip"

    const val URL_PROTOCOL_WSJAR = "wsjar"

    const val URL_PROTOCOL_VFSZIP = "vfszip"

    const val URL_PROTOCOL_VFSFILE = "vfsfile"

    const val URL_PROTOCOL_VFS = "vfs"

    const val JAR_FILE_EXTENSION = ".jar"

    const val JAR_URL_SEPARATOR = "!/"

    const val WAR_URL_SEPARATOR = "*/"

    fun isUrl(@Nullable resourceLocation: String?): Boolean {
        if (resourceLocation == null) {
            return false
        }
        return if (resourceLocation.startsWith(CLASSPATH_URL_PREFIX)) {
            true
        } else try {
            URL(resourceLocation)
            true
        } catch (ex: MalformedURLException) {
            false
        }
    }

    @Throws(FileNotFoundException::class)
    fun getURL(resourceLocation: String): URL {
        if (resourceLocation.startsWith(CLASSPATH_URL_PREFIX)) {
            val path = resourceLocation.substring(CLASSPATH_URL_PREFIX.length)
            val cl = ClassUtil.getDefaultClassLoader()
            val url = if (cl != null) cl.getResource(path) else ClassLoader.getSystemResource(path)
            if (url == null) {
                val description = "class path resource [$path]"
                throw FileNotFoundException(
                    description +
                            " cannot be resolved to URL because it does not exist"
                )
            }
            return url
        }
        return try {
            // try URL
            URL(resourceLocation)
        } catch (ex: MalformedURLException) {
            // no URL -> treat as file path
            try {
                File(resourceLocation).toURI().toURL()
            } catch (ex2: MalformedURLException) {
                throw FileNotFoundException(
                    "Resource location [" + resourceLocation +
                            "] is neither a URL not a well-formed file path"
                )
            }
        }
    }

    @Throws(FileNotFoundException::class)
    fun getFile(resourceLocation: String): File {
        if (resourceLocation.startsWith(CLASSPATH_URL_PREFIX)) {
            val path = resourceLocation.substring(CLASSPATH_URL_PREFIX.length)
            val description = "class path resource [$path]"
            val cl = ClassUtil.getDefaultClassLoader()
            val url = (if (cl != null) cl.getResource(path) else ClassLoader.getSystemResource(path))
                ?: throw FileNotFoundException(
                    description +
                            " cannot be resolved to absolute file path because it does not exist"
                )
            return getFile(url, description)
        }
        return try {
            // try URL
            getFile(URL(resourceLocation))
        } catch (ex: MalformedURLException) {
            // no URL -> treat as file path
            File(resourceLocation)
        }
    }

    @Throws(FileNotFoundException::class)
    fun getFile(resourceUrl: URL): File {
        return getFile(resourceUrl, "URL")
    }

    @Throws(FileNotFoundException::class)
    fun getFile(resourceUrl: URL, description: String): File {
        if (URL_PROTOCOL_FILE != resourceUrl.protocol) {
            throw FileNotFoundException(
                description + " cannot be resolved to absolute file path " +
                        "because it does not reside in the file system: " + resourceUrl
            )
        }
        return try {
            File(toURI(resourceUrl).schemeSpecificPart)
        } catch (ex: URISyntaxException) {
            // Fallback for URLs that are not valid URIs (should hardly ever happen).
            File(resourceUrl.file)
        }
    }

    @Throws(FileNotFoundException::class)
    fun getFile(resourceUri: URI): File {
        return getFile(resourceUri, "URI")
    }

    @Throws(FileNotFoundException::class)
    fun getFile(resourceUri: URI, description: String): File {
        if (URL_PROTOCOL_FILE != resourceUri.scheme) {
            throw FileNotFoundException(
                description + " cannot be resolved to absolute file path " +
                        "because it does not reside in the file system: " + resourceUri
            )
        }
        return File(resourceUri.schemeSpecificPart)
    }

    fun isFileURL(url: URL): Boolean {
        val protocol = url.protocol
        return URL_PROTOCOL_FILE == protocol || URL_PROTOCOL_VFSFILE == protocol || URL_PROTOCOL_VFS == protocol
    }

    fun isJarURL(url: URL): Boolean {
        val protocol = url.protocol
        return URL_PROTOCOL_JAR == protocol || URL_PROTOCOL_WAR == protocol || URL_PROTOCOL_ZIP == protocol || URL_PROTOCOL_VFSZIP == protocol || URL_PROTOCOL_WSJAR == protocol
    }

    fun isJarFileURL(url: URL): Boolean {
        return URL_PROTOCOL_FILE == url.protocol &&
                url.path.lowercase(Locale.getDefault()).endsWith(JAR_FILE_EXTENSION)
    }

    @Throws(MalformedURLException::class)
    fun extractJarFileURL(jarUrl: URL): URL {
        val urlFile = jarUrl.file
        val separatorIndex = urlFile.indexOf(JAR_URL_SEPARATOR)
        return if (separatorIndex != -1) {
            var jarFile = urlFile.substring(0, separatorIndex)
            try {
                URL(jarFile)
            } catch (ex: MalformedURLException) {
                // Probably no protocol in original jar URL, like "jar:C:/mypath/myjar.jar".
                // This usually indicates that the jar file resides in the file system.
                if (!jarFile.startsWith("/")) {
                    jarFile = "/$jarFile"
                }
                URL(FILE_URL_PREFIX + jarFile)
            }
        } else {
            jarUrl
        }
    }

    @Throws(MalformedURLException::class)
    fun extractArchiveURL(jarUrl: URL): URL {
        val urlFile = jarUrl.file
        val endIndex = urlFile.indexOf(WAR_URL_SEPARATOR)
        if (endIndex != -1) {
            // Tomcat's "war:file:...mywar.war*/WEB-INF/lib/myjar.jar!/myentry.txt"
            val warFile = urlFile.substring(0, endIndex)
            if (URL_PROTOCOL_WAR == jarUrl.protocol) {
                return URL(warFile)
            }
            val startIndex = warFile.indexOf(WAR_URL_PREFIX)
            if (startIndex != -1) {
                return URL(warFile.substring(startIndex + WAR_URL_PREFIX.length))
            }
        }

        // Regular "jar:file:...myjar.jar!/myentry.txt"
        return extractJarFileURL(jarUrl)
    }

    @Throws(URISyntaxException::class)
    fun toURI(url: URL): URI {
        return toURI(url.toString())
    }

    @Throws(URISyntaxException::class)
    fun toURI(location: String): URI {
        return URI(location.replace(" ", "%20"))
    }

    fun useCachesIfNecessary(con: URLConnection) {
        con.useCaches = con.javaClass.simpleName.startsWith("JNLP")
    }

}