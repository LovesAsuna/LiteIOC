package com.hyosakura.liteioc.core.io

import com.hyosakura.liteioc.util.ResourceUtil
import com.hyosakura.liteioc.util.StringUtil
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.*


open class UrlResource : AbstractFileResolvingResource {

    private val uri: URI?

    private val url: URL

    @Volatile
    private var cleanedUrl: URL? = null

    @Throws(MalformedURLException::class)
    constructor(uri: URI) {
        this.uri = uri
        url = uri.toURL()
    }

    constructor(url: URL) {
        this.uri = null
        this.url = url
    }

    @Throws(MalformedURLException::class)
    constructor(path: String) {
        this.uri = null
        this.url = URL(path)
        this.cleanedUrl = getCleanedUrl(url, path)
    }

    @Throws(MalformedURLException::class)
    constructor(protocol: String, location: String) : this(protocol, location, null)

    @Throws(MalformedURLException::class)
    constructor(protocol: String, location: String, fragment: String?) {
        try {
            uri = URI(protocol, location, fragment)
            url = uri.toURL()
        } catch (ex: URISyntaxException) {
            val exToThrow = MalformedURLException(ex.message)
            exToThrow.initCause(ex)
            throw exToThrow
        }
    }

    private fun getCleanedUrl(originalUrl: URL, originalPath: String): URL {
        val cleanedPath = StringUtil.cleanPath(originalPath)
        if (cleanedPath != originalPath) {
            try {
                return URL(cleanedPath)
            } catch (ex: MalformedURLException) {
                // Cleaned URL path cannot be converted to URL -> take original URL.
            }
        }
        return originalUrl
    }

    private fun getCleanedUrl(): URL {
        var cleanedUrl = cleanedUrl
        if (cleanedUrl != null) {
            return cleanedUrl
        }
        cleanedUrl = getCleanedUrl(url, (uri ?: url).toString())
        this.cleanedUrl = cleanedUrl
        return cleanedUrl
    }

    @Throws(IOException::class)
    override fun getInputStream(): InputStream {
        val con = url.openConnection()
        ResourceUtil.useCachesIfNecessary(con)
        return try {
            con.getInputStream()
        } catch (ex: IOException) {
            // Close the HTTP connection (if applicable).
            if (con is HttpURLConnection) {
                con.disconnect()
            }
            throw ex
        }
    }

    override fun getURL(): URL {
        return url
    }

    @Throws(IOException::class)
    override fun getURI(): URI {
        return uri ?: super.getURI()
    }

    override fun isFile(): Boolean {
        return if (uri != null) {
            super.isFile(uri)
        } else {
            super.isFile()
        }
    }

    @Throws(IOException::class)
    override fun getFile(): File {
        return if (uri != null) {
            super.getFile(uri)
        } else {
            super.getFile()
        }
    }

    @Throws(MalformedURLException::class)
    override fun createRelative(relativePath: String): Resource {
        return UrlResource(createRelativeURL(relativePath))
    }

    @Throws(MalformedURLException::class)
    protected fun createRelativeURL(relativePath: String): URL {
        var relativePath = relativePath
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1)
        }
        // # can appear in filenames, java.net.URL should not treat it as a fragment
        relativePath = relativePath.replace("#", "%23")
        // Use the URL constructor for applying the relative path as a URL spec
        return URL(url, relativePath)
    }

    override fun getFilename(): String {
        return StringUtil.getFilename(getCleanedUrl().path)!!
    }

    override fun getDescription(): String {
        return "URL [$url]"
    }

    override fun equals(other: Any?): Boolean {
        return this === other || other is UrlResource && getCleanedUrl() == other.getCleanedUrl()
    }

    override fun hashCode(): Int {
        return getCleanedUrl().hashCode()
    }

}