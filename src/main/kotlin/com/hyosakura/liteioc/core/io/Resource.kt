package com.hyosakura.liteioc.core.io

import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URL
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel

/**
 * @author LovesAsuna
 **/
interface Resource : InputStreamSource {

    fun exists(): Boolean

    fun isReadable(): Boolean {
        return exists()
    }

    fun isOpen(): Boolean {
        return false
    }

    fun isFile(): Boolean {
        return false
    }

    @Throws(IOException::class)
    fun getURL(): URL

    @Throws(IOException::class)
    fun getURI(): URI

    @Throws(IOException::class)
    fun getFile(): File

    @Throws(IOException::class)
    fun readableChannel(): ReadableByteChannel {
        return Channels.newChannel(getInputStream())
    }

    @Throws(IOException::class)
    fun contentLength(): Long

    @Throws(IOException::class)
    fun lastModified(): Long

    @Throws(IOException::class)
    fun createRelative(relativePath: String): Resource?

    fun getFilename(): String?

    fun getDescription(): String

}