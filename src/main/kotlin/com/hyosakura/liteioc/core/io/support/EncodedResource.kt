package com.hyosakura.liteioc.core.io.support

import com.hyosakura.liteioc.core.io.InputStreamSource
import com.hyosakura.liteioc.core.io.Resource
import com.hyosakura.liteioc.util.ObjectUtil
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset


/**
 * @author LovesAsuna
 **/

class EncodedResource : InputStreamSource {

    private val resource: Resource

    private val encoding: String?

    private val charset: Charset?

    constructor(resource: Resource) : this(resource, null, null)


    constructor(resource: Resource, encoding: String?) : this(resource, encoding, null)

    constructor(resource: Resource, charset: Charset?) : this(resource, null, charset)

    private constructor(resource: Resource, encoding: String?, charset: Charset?) : super() {
        this.resource = resource
        this.encoding = encoding
        this.charset = charset
    }

    fun getResource(): Resource {
        return this.resource
    }

    fun getEncoding(): String? {
        return this.encoding
    }

    fun getCharset(): Charset? {
        return this.charset
    }

    fun requiresReader(): Boolean {
        return (this.encoding != null || this.charset != null)
    }

    @Throws(IOException::class)
    fun getReader(): Reader {
        return if (this.charset != null) {
            InputStreamReader(this.resource.getInputStream(), this.charset)
        } else if (this.encoding != null) {
            InputStreamReader(this.resource.getInputStream(), this.encoding)
        } else {
            InputStreamReader(this.resource.getInputStream())
        }
    }

    @Throws(IOException::class)
    @Override
    override fun getInputStream(): InputStream {
        return this.resource.getInputStream()
    }

    @Override
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is EncodedResource) {
            return false
        }
        return (this.resource == other.resource &&
                ObjectUtil.nullSafeEquals(this.charset, other.charset) &&
                ObjectUtil.nullSafeEquals(this.encoding, other.encoding))
    }

    @Override
    override fun hashCode(): Int {
        return this.resource.hashCode()
    }

    @Override
    override fun toString(): String {
        return this.resource.toString()
    }

}