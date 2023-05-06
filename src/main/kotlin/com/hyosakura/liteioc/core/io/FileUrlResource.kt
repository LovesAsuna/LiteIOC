package com.hyosakura.liteioc.core.io

import com.hyosakura.liteioc.util.ResourceUtil
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.MalformedURLException
import java.net.URL
import java.nio.channels.FileChannel
import java.nio.channels.WritableByteChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption

/**
 * @author LovesAsuna
 **/
class FileUrlResource : UrlResource, WritableResource {

    @Volatile
    private var file: File? = null

    constructor(url: URL) : super(url)

    @Throws(MalformedURLException::class)
    constructor(location: String) : super(ResourceUtil.URL_PROTOCOL_FILE, location)

    @Throws(IOException::class)
    override fun getFile(): File {
        var file = file
        if (file != null) {
            return file
        }
        file = super.getFile()
        this.file = file
        return file
    }

    override fun isWritable(): Boolean {
        return try {
            val file = getFile()
            file.canWrite() && !file.isDirectory
        } catch (ex: IOException) {
            false
        }
    }

    @Throws(IOException::class)
    override fun getOutputStream(): OutputStream {
        return Files.newOutputStream(getFile().toPath())
    }

    @Throws(IOException::class)
    override fun writableChannel(): WritableByteChannel {
        return FileChannel.open(getFile().toPath(), StandardOpenOption.WRITE)
    }

    @Throws(MalformedURLException::class)
    override fun createRelative(relativePath: String): Resource {
        return FileUrlResource(createRelativeURL(relativePath))
    }

}