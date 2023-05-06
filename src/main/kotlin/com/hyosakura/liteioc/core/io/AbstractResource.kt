package com.hyosakura.liteioc.core.io

import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.util.function.Supplier

/**
 * @author LovesAsuna
 **/
abstract class AbstractResource : Resource {

    override fun exists(): Boolean {
        // Try file existence: can we find the file in the file system?
        if (isFile()) {
            try {
                return getFile().exists()
            } catch (ex: IOException) {
                debug({ "Could not retrieve File for existence check of " + getDescription() }, ex)
            }
        }
        // Fall back to stream existence: can we open the stream?
        return try {
            getInputStream().close()
            true
        } catch (ex: Throwable) {
            debug({ "Could not retrieve InputStream for existence check of " + getDescription() }, ex)
            false
        }
    }

    override fun isReadable(): Boolean {
        return exists()
    }

    override fun isOpen(): Boolean {
        return false
    }

    override fun isFile(): Boolean {
        return false
    }

    @Throws(IOException::class)
    override fun getURL(): URL {
        throw FileNotFoundException(getDescription() + " cannot be resolved to URL")
    }

    @Throws(IOException::class)
    override fun getURI(): URI {
        val url = getURL()
        return try {
            URI(url.toString().replace(" ", "%20"))
        } catch (ex: URISyntaxException) {
            throw IOException("Invalid URI [$url]", ex)
        }
    }

    @Throws(IOException::class)
    override fun getFile(): File {
        throw FileNotFoundException(getDescription() + " cannot be resolved to absolute file path")
    }

    @Throws(IOException::class)
    override fun createRelative(relativePath: String): Resource {
        throw FileNotFoundException("Cannot create a relative resource for " + getDescription())
    }

    @Throws(IOException::class)
    override fun readableChannel(): ReadableByteChannel {
        return Channels.newChannel(getInputStream())
    }

    @Throws(IOException::class)
    override fun contentLength(): Long {
        val `is` = getInputStream()
        return try {
            var size: Long = 0
            val buf = ByteArray(256)
            var read: Int
            while (`is`.read(buf).also { read = it } != -1) {
                size += read.toLong()
            }
            size
        } finally {
            try {
                `is`.close()
            } catch (ex: IOException) {
                debug({ "Could not close content-length InputStream for " + getDescription() }, ex)
            }
        }
    }

    @Throws(IOException::class)
    override fun lastModified(): Long {
        val fileToCheck = getFileForLastModifiedCheck()
        val lastModified = fileToCheck.lastModified()
        if (lastModified == 0L && !fileToCheck.exists()) {
            throw FileNotFoundException(
                getDescription() +
                        " cannot be resolved in the file system for checking its last-modified timestamp"
            )
        }
        return lastModified
    }

    @Throws(IOException::class)
    protected open fun getFileForLastModifiedCheck(): File {
        return getFile()
    }

    override fun getFilename(): String? {
        return null
    }

    override fun equals(other: Any?): Boolean {
        return this === other || other is Resource && other.getDescription().equals(getDescription())
    }

    override fun hashCode(): Int = getDescription().hashCode()

    override fun toString(): String = getDescription()

    private fun debug(message: Supplier<String>, ex: Throwable) {
        val logger = LoggerFactory.getLogger(javaClass)
        if (logger.isDebugEnabled) {
            logger.debug(message.get(), ex)
        }
    }

}