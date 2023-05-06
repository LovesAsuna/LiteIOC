package com.hyosakura.liteioc.core.io

import com.hyosakura.liteioc.util.ClassUtil
import com.hyosakura.liteioc.util.ObjectUtil
import com.hyosakura.liteioc.util.StringUtil
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.URL

/**
 * @author LovesAsuna
 **/
open class ClassPathResource : AbstractFileResolvingResource {

    private val path: String

    private var classLoader: ClassLoader? = null

    private var clazz: Class<*>? = null

    constructor(path: String) : this(path, null as ClassLoader?)

    constructor(path: String, classLoader: ClassLoader?) {
        var pathToUse: String = StringUtil.cleanPath(path)
        if (pathToUse.startsWith("/")) {
            pathToUse = pathToUse.substring(1)
        }
        this.path = pathToUse
        this.classLoader = classLoader ?: ClassUtil.getDefaultClassLoader()
    }

    constructor(path: String, clazz: Class<*>?) {
        this.path = StringUtil.cleanPath(path)
        this.clazz = clazz
    }

    fun getPath(): String {
        return path
    }

    fun getClassLoader(): ClassLoader? {
        return if (clazz != null) clazz!!.classLoader else classLoader!!
    }

    override fun exists(): Boolean {
        return resolveURL() != null
    }

    override fun isReadable(): Boolean {
        val url = resolveURL()
        return url != null && checkReadable(url)
    }

    fun resolveURL(): URL? {
        return try {
            if (clazz != null) {
                clazz!!.getResource(path)
            } else if (classLoader != null) {
                classLoader!!.getResource(path)
            } else {
                ClassLoader.getSystemResource(path)
            }
        } catch (ex: IllegalArgumentException) {
            // Should not happen according to the JDK's contract:
            // see https://github.com/openjdk/jdk/pull/2662
            null
        }
    }

    @Throws(IOException::class)
    override fun getInputStream(): InputStream {
        val `is`: InputStream? = if (clazz != null) {
            clazz!!.getResourceAsStream(path)
        } else if (classLoader != null) {
            classLoader!!.getResourceAsStream(path)
        } else {
            ClassLoader.getSystemResourceAsStream(path)
        }
        if (`is` == null) {
            throw FileNotFoundException(getDescription() + " cannot be opened because it does not exist")
        }
        return `is`
    }

    @Throws(IOException::class)
    override fun getURL(): URL {
        return resolveURL()
            ?: throw FileNotFoundException(getDescription() + " cannot be resolved to URL because it does not exist")
    }

    override fun createRelative(relativePath: String): Resource {
        val pathToUse = StringUtil.applyRelativePath(this.path, relativePath)
        return if (clazz != null) ClassPathResource(pathToUse, clazz) else ClassPathResource(pathToUse, classLoader)
    }

    override fun getFilename(): String? {
        return StringUtil.getFilename(path)
    }

    override fun getDescription(): String {
        val builder = StringBuilder("class path resource [")
        var pathToUse = path
        if (clazz != null && !pathToUse.startsWith("/")) {
            builder.append(ClassUtil.classPackageAsResourcePath(clazz))
            builder.append('/')
        }
        if (pathToUse.startsWith("/")) {
            pathToUse = pathToUse.substring(1)
        }
        builder.append(pathToUse)
        builder.append(']')
        return builder.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return if (other !is ClassPathResource) {
            false
        } else path == other.path &&
                ObjectUtil.nullSafeEquals(classLoader, other.classLoader) &&
                ObjectUtil.nullSafeEquals(clazz, other.clazz)
    }

    override fun hashCode(): Int {
        return path.hashCode()
    }

}