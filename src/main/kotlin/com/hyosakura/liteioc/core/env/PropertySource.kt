package com.hyosakura.liteioc.core.env

import com.hyosakura.liteioc.util.ObjectUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author LovesAsuna
 **/
abstract class PropertySource<T> {

    val logger: Logger = LoggerFactory.getLogger(javaClass)

    val name: String

    val source: T

    companion object {

        fun named(name: String): PropertySource<*> {
            return ComparisonPropertySource(name)
        }

    }

    constructor(name: String?, source: T) {
        require(!name.isNullOrEmpty()) { "Property source name must contain at least one character" }
        this.name = name
        this.source = source
    }

    @Suppress("UNCHECKED_CAST")
    constructor(name: String?) : this(name, Any() as T)

    open fun containsProperty(name: String): Boolean {
        return getProperty(name) != null
    }

    abstract fun getProperty(name: String): Any?

    override fun equals(other: Any?): Boolean {
        return this === other || other is PropertySource<*> && ObjectUtil.nullSafeEquals(name, other.name)
    }

    override fun hashCode(): Int {
        return ObjectUtil.nullSafeHashCode(name)
    }

    override fun toString(): String {
        return if (logger.isDebugEnabled) {
            javaClass.simpleName + "@" + System.identityHashCode(this) + " {name='" + name + "', properties=" + source + "}"
        } else {
            javaClass.simpleName + " {name='" + name + "'}"
        }
    }

    open class StubPropertySource(name: String) : PropertySource<Any>(name, Any()) {

        override fun getProperty(name: String): String? {
            return null
        }

    }

    internal class ComparisonPropertySource(name: String) : StubPropertySource(name) {

        override fun containsProperty(name: String): Boolean {
            throw UnsupportedOperationException(USAGE_ERROR)
        }

        override fun getProperty(name: String): String? {
            throw UnsupportedOperationException(USAGE_ERROR)
        }

        companion object {

            private const val USAGE_ERROR =
                "ComparisonPropertySource instances are for use with collection comparison only"

        }

    }

}