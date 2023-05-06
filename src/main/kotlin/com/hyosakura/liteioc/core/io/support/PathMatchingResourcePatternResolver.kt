package com.hyosakura.liteioc.core.io.support

import com.hyosakura.liteioc.core.io.DefaultResourceLoader
import com.hyosakura.liteioc.core.io.Resource
import com.hyosakura.liteioc.core.io.ResourceLoader
import com.hyosakura.liteioc.core.io.UrlResource
import com.hyosakura.liteioc.core.io.support.ResourcePatternResolver.Companion.CLASSPATH_ALL_URL_PREFIX
import com.hyosakura.liteioc.util.ResourceUtil
import com.hyosakura.liteioc.util.StringUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.net.URLClassLoader
import java.util.*

/**
 * @author LovesAsuna
 **/
class PathMatchingResourcePatternResolver : ResourcePatternResolver {

    private val logger: Logger = LoggerFactory.getLogger(
        PathMatchingResourcePatternResolver::class.java
    )

    private val resourceLoader: ResourceLoader

    constructor() {
        this.resourceLoader = DefaultResourceLoader()
    }

    constructor(resourceLoader: ResourceLoader) {
        this.resourceLoader = resourceLoader
    }

    @Throws(IOException::class)
    override fun getResources(locationPattern: String): Array<Resource> {
        return if (locationPattern.startsWith(CLASSPATH_ALL_URL_PREFIX)) {
            findAllClassPathResources(locationPattern.substring(CLASSPATH_ALL_URL_PREFIX.length))
        } else {
            arrayOf(getResourceLoader().getResource(locationPattern))
        }
    }

    @Throws(IOException::class)
    private fun findAllClassPathResources(location: String): Array<Resource> {
        var path = location
        if (path.startsWith("/")) {
            path = path.substring(1)
        }
        val result: Set<Resource> = doFindAllClassPathResources(path)
        if (logger.isTraceEnabled) {
            logger.trace("Resolved classpath location [$location] to resources $result")
        }
        return result.toTypedArray()
    }

    @Throws(IOException::class)
    private fun doFindAllClassPathResources(path: String): Set<Resource> {
        val result: MutableSet<Resource> = LinkedHashSet(16)
        val cl = getClassLoader()
        val resourceUrls = if (cl != null) cl.getResources(path) else ClassLoader.getSystemResources(path)
        while (resourceUrls.hasMoreElements()) {
            val url = resourceUrls.nextElement()
            result.add(convertClassLoaderURL(url))
        }
        if (path.isEmpty()) {
            // The above result is likely to be incomplete, i.e. only containing file system references.
            // We need to have pointers to each of the jar files on the classpath as well...
            addAllClassLoaderJarRoots(cl, result)
        }
        return result
    }

    private fun addAllClassLoaderJarRoots(classLoader: ClassLoader?, result: MutableSet<Resource>) {
        if (classLoader is URLClassLoader) {
            try {
                for (url: URL in classLoader.urLs) {
                    try {
                        val jarResource =
                            if (ResourceUtil.URL_PROTOCOL_JAR == url.protocol) UrlResource(url) else UrlResource(
                                ResourceUtil.JAR_URL_PREFIX + url + ResourceUtil.JAR_URL_SEPARATOR
                            )
                        if (jarResource.exists()) {
                            result.add(jarResource)
                        }
                    } catch (ex: MalformedURLException) {
                        if (logger.isDebugEnabled) {
                            logger.debug(
                                "Cannot search for matching files underneath [" + url + "] because it cannot be converted to a valid 'jar:' URL: " + ex.message
                            )
                        }
                    }
                }
            } catch (ex: Exception) {
                if (logger.isDebugEnabled) {
                    logger.debug(
                        ("Cannot introspect jar files since ClassLoader [" + classLoader + "] does not support 'getURLs()': " + ex)
                    )
                }
            }
        }
        if (classLoader === ClassLoader.getSystemClassLoader()) {
            // "java.class.path" manifest evaluation...
            addClassPathManifestEntries(result)
        }
        if (classLoader != null) {
            try {
                // Hierarchy traversal...
                addAllClassLoaderJarRoots(classLoader.parent, result)
            } catch (ex: Exception) {
                if (logger.isDebugEnabled) {
                    logger.debug(
                        ("Cannot introspect jar files in parent ClassLoader since [" + classLoader + "] does not support 'getParent()': " + ex)
                    )
                }
            }
        }
    }

    private fun addClassPathManifestEntries(result: MutableSet<Resource>) {
        try {
            val javaClassPathProperty = System.getProperty("java.class.path")
            for (path: String in javaClassPathProperty.split(System.getProperty("path.separator"))) {
                try {
                    var filePath = File(path).absolutePath
                    val prefixIndex = filePath.indexOf(':')
                    if (prefixIndex == 1) {
                        // Possibly "c:" drive prefix on Windows, to be upper-cased for proper duplicate detection
                        filePath = StringUtil.capitalize(filePath)
                    }
                    // # can appear in directories/filenames, java.net.URL should not treat it as a fragment
                    filePath = filePath.replace("#", "%23")
                    // Build URL that points to the root of the jar file
                    val jarResource = UrlResource(
                        (ResourceUtil.JAR_URL_PREFIX + ResourceUtil.FILE_URL_PREFIX) + filePath + ResourceUtil.JAR_URL_SEPARATOR
                    )
                    // Potentially overlapping with URLClassLoader.getURLs() result above!
                    if (!result.contains(jarResource) && !hasDuplicate(filePath, result) && jarResource.exists()) {
                        result.add(jarResource)
                    }
                } catch (ex: MalformedURLException) {
                    if (logger.isDebugEnabled) {
                        logger.debug(
                            "Cannot search for matching files underneath [" + path + "] because it cannot be converted to a valid 'jar:' URL: " + ex.message
                        )
                    }
                }
            }
        } catch (ex: java.lang.Exception) {
            if (logger.isDebugEnabled) {
                logger.debug("Failed to evaluate 'java.class.path' manifest entries: $ex")
            }
        }
    }

    private fun hasDuplicate(filePath: String, result: Set<Resource>): Boolean {
        if (result.isEmpty()) {
            return false
        }
        val duplicatePath = if (filePath.startsWith("/")) filePath.substring(1) else "/$filePath"
        return try {
            result.contains(
                UrlResource(
                    (ResourceUtil.JAR_URL_PREFIX + ResourceUtil.FILE_URL_PREFIX) +
                            duplicatePath + ResourceUtil.JAR_URL_SEPARATOR
                )
            )
        } catch (ex: MalformedURLException) {
            // Ignore: just for testing against duplicate.
            false
        }
    }

    private fun convertClassLoaderURL(url: URL): Resource {
        return UrlResource(url)
    }

    override fun getResource(location: String): Resource {
        return getResourceLoader().getResource(location)
    }

    fun getResourceLoader(): ResourceLoader {
        return this.resourceLoader
    }

    override fun getClassLoader(): ClassLoader? {
        return getResourceLoader().getClassLoader()
    }

}