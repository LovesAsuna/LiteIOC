package com.hyosakura.liteioc.core.io

import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * @author LovesAsuna
 **/
class DescriptiveResource(private val description: String) : AbstractResource() {

    override fun exists(): Boolean {
        return false
    }

    override fun isReadable(): Boolean {
        return false
    }

    @Throws(IOException::class)
    override fun getInputStream(): InputStream {
        throw FileNotFoundException(
            getDescription() + " cannot be opened because it does not point to a readable resource"
        )
    }

    override fun getDescription(): String {
        return description
    }

    override fun equals(other: Any?): Boolean {
        return this === other || other is DescriptiveResource && other.description == description
    }

    override fun hashCode(): Int {
        return description.hashCode()
    }

}