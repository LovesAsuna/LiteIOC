package com.hyosakura.liteioc.core.io

import com.hyosakura.liteioc.util.ClassUtil
import com.hyosakura.liteioc.util.ResourceUtil
import com.hyosakura.liteioc.util.ResourceUtil.CLASSPATH_URL_PREFIX
import com.hyosakura.liteioc.util.StringUtil
import java.net.MalformedURLException
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * @author LovesAsuna
 **/
open class DefaultResourceLoader : ResourceLoader {

    private var classLoader: ClassLoader? = null

    private val protocolResolvers: MutableSet<ProtocolResolver> = LinkedHashSet<ProtocolResolver>(4)

    private val resourceCaches: MutableMap<Class<*>, Map<Resource, *>> = ConcurrentHashMap(4)

    constructor()

    constructor(classLoader: ClassLoader?) {
        this.classLoader = classLoader
    }

    fun setClassLoader(classLoader: ClassLoader?) {
        this.classLoader = classLoader
    }

    override fun getClassLoader(): ClassLoader? {
        return if (classLoader != null) classLoader else ClassUtil.getDefaultClassLoader()
    }

    fun addProtocolResolver(resolver: ProtocolResolver) {
        protocolResolvers.add(resolver)
    }

    fun getProtocolResolvers(): Collection<ProtocolResolver> {
        return protocolResolvers
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getResourceCache(valueType: Class<T>): Map<Resource, T> {
        return resourceCaches.computeIfAbsent(
            valueType
        ) { ConcurrentHashMap<Resource, Any>() } as Map<Resource, T>
    }

    fun clearResourceCaches() {
        resourceCaches.clear()
    }

    override fun getResource(location: String): Resource {
        for (protocolResolver in getProtocolResolvers()) {
            val resource = protocolResolver.resolve(location, this)
            if (resource != null) {
                return resource
            }
        }
        return if (location.startsWith("/")) {
            getResourceByPath(location)
        } else if (location.startsWith(CLASSPATH_URL_PREFIX)) {
            ClassPathResource(location.substring(CLASSPATH_URL_PREFIX.length), getClassLoader())
        } else {
            try {
                // Try to parse the location as a URL...
                val url = URL(location)
                if (ResourceUtil.isFileURL(url)) FileUrlResource(url) else UrlResource(url)
            } catch (ex: MalformedURLException) {
                // No URL -> resolve as resource path.
                getResourceByPath(location)
            }
        }
    }

    fun getResourceByPath(path: String): Resource {
        return ClassPathContextResource(path, getClassLoader())
    }

    class ClassPathContextResource(path: String, classLoader: ClassLoader?) : ClassPathResource(path, classLoader) {
        val pathWithinContext: String
            get() = getPath()

        override fun createRelative(relativePath: String): Resource {
            val pathToUse: String = StringUtil.applyRelativePath(getPath(), relativePath)
            return ClassPathContextResource(pathToUse, getClassLoader())
        }

    }

}