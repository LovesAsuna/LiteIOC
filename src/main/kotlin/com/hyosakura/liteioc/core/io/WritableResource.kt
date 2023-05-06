package com.hyosakura.liteioc.core.io

import java.io.IOException
import java.io.OutputStream
import java.nio.channels.Channels
import java.nio.channels.WritableByteChannel

/**
 * @author LovesAsuna
 **/
interface WritableResource {

    fun isWritable(): Boolean {
        return true
    }

    @Throws(IOException::class)
    fun getOutputStream(): OutputStream

    @Throws(IOException::class)
    fun writableChannel(): WritableByteChannel {
        return Channels.newChannel(getOutputStream())
    }

}