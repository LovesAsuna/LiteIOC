package com.hyosakura.liteioc.core.io

import com.hyosakura.liteioc.util.ResourceUtil
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLConnection
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel
import java.nio.file.NoSuchFileException
import java.nio.file.StandardOpenOption

/**
 * @author LovesAsuna
 **/
abstract class AbstractFileResolvingResource : AbstractResource() {

    override fun exists(): Boolean {
        return try {
            val url = getURL()
            if (ResourceUtil.isFileURL(url)) {
                // Proceed with file system resolution
                getFile().exists()
            } else {
                // Try a URL connection content-length header
                val con = url.openConnection()
                customizeConnection(con)
                val httpCon = if (con is HttpURLConnection) con else null
                if (httpCon != null) {
                    val code = httpCon.responseCode
                    if (code == HttpURLConnection.HTTP_OK) {
                        return true
                    } else if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                        return false
                    }
                }
                if (con.contentLengthLong > 0) {
                    return true
                }
                if (httpCon != null) {
                    // No HTTP OK status, and no content-length header: give up
                    httpCon.disconnect()
                    false
                } else {
                    // Fall back to stream existence: can we open the stream?
                    getInputStream().close()
                    true
                }
            }
        } catch (ex: IOException) {
            false
        }
    }

    override fun isReadable(): Boolean {
        return try {
            checkReadable(getURL())
        } catch (ex: IOException) {
            false
        }
    }

    open fun checkReadable(url: URL): Boolean {
        return try {
            if (ResourceUtil.isFileURL(url)) {
                // Proceed with file system resolution
                val file = getFile()
                file.canRead() && !file.isDirectory
            } else {
                // Try InputStream resolution for jar resources
                val con = url.openConnection()
                customizeConnection(con)
                if (con is HttpURLConnection) {
                    val code: Int = con.responseCode
                    if (code != HttpURLConnection.HTTP_OK) {
                        con.disconnect()
                        return false
                    }
                }
                val contentLength = con.contentLengthLong
                if (contentLength > 0) {
                    true
                } else if (contentLength == 0L) {
                    // Empty file or directory -> not considered readable...
                    false
                } else {
                    // Fall back to stream existence: can we open the stream?
                    getInputStream().close()
                    true
                }
            }
        } catch (ex: IOException) {
            false
        }
    }

    override fun isFile(): Boolean {
        return try {
            val url = getURL()
            ResourceUtil.URL_PROTOCOL_FILE == url.protocol
        } catch (ex: IOException) {
            false
        }
    }

    @Throws(IOException::class)
    override fun getFile(): File {
        val url = getURL()
        return ResourceUtil.getFile(url, getDescription())
    }

    @Throws(IOException::class)
    override fun getFileForLastModifiedCheck(): File {
        val url = getURL()
        return if (ResourceUtil.isJarURL(url)) {
            val actualUrl: URL = ResourceUtil.extractArchiveURL(url)
            ResourceUtil.getFile(actualUrl, "Jar URL")
        } else {
            getFile()
        }
    }

    protected open fun isFile(uri: URI): Boolean {
        return try {
            ResourceUtil.URL_PROTOCOL_FILE == uri.scheme
        } catch (ex: IOException) {
            false
        }
    }

    @Throws(IOException::class)
    protected open fun getFile(uri: URI): File = ResourceUtil.getFile(uri, getDescription())

    @Throws(IOException::class)
    override fun readableChannel(): ReadableByteChannel {
        return try {
            // Try file system channel
            FileChannel.open(getFile().toPath(), StandardOpenOption.READ)
        } catch (ex: FileNotFoundException) {
            // Fall back to InputStream adaptation in superclass
            super.readableChannel()
        } catch (ex: NoSuchFileException) {
            super.readableChannel()
        }
    }

    @Throws(IOException::class)
    override fun contentLength(): Long {
        val url = getURL()
        return if (ResourceUtil.isFileURL(url)) {
            // Proceed with file system resolution
            val file = getFile()
            val length = file.length()
            if (length == 0L && !file.exists()) {
                throw FileNotFoundException(
                    getDescription() +
                            " cannot be resolved in the file system for checking its content length"
                )
            }
            length
        } else {
            // Try a URL connection content-length header
            val con = url.openConnection()
            customizeConnection(con)
            con.contentLengthLong
        }
    }

    @Throws(IOException::class)
    override fun lastModified(): Long {
        val url = getURL()
        var fileCheck = false
        if (ResourceUtil.isFileURL(url) || ResourceUtil.isJarURL(url)) {
            // Proceed with file system resolution
            fileCheck = true
            try {
                val fileToCheck = getFileForLastModifiedCheck()
                val lastModified = fileToCheck.lastModified()
                if (lastModified > 0L || fileToCheck.exists()) {
                    return lastModified
                }
            } catch (ex: FileNotFoundException) {
                // Defensively fall back to URL connection check instead
            }
        }
        // Try a URL connection last-modified header
        val con = url.openConnection()
        customizeConnection(con)
        val lastModified = con.lastModified
        if (fileCheck && lastModified == 0L && con.contentLengthLong <= 0) {
            throw FileNotFoundException(
                getDescription() +
                        " cannot be resolved in the file system for checking its last-modified timestamp"
            )
        }
        return lastModified
    }

    @Throws(IOException::class)
    open fun customizeConnection(con: URLConnection) {
        ResourceUtil.useCachesIfNecessary(con)
        if (con is HttpURLConnection) {
            customizeConnection(con)
        }
    }

    @Throws(IOException::class)
    open fun customizeConnection(con: HttpURLConnection) {
        con.requestMethod = "HEAD"
    }

}