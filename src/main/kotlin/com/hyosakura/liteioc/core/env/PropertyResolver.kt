package com.hyosakura.liteioc.core.env

interface PropertyResolver {

    fun containsProperty(key: String): Boolean

    fun getProperty(key: String): String?

    fun getProperty(key: String, defaultValue: String): String

    fun <T> getProperty(key: String, targetType: Class<T>): T?

    fun <T> getProperty(key: String, targetType: Class<T>, defaultValue: T): T

    @Throws(IllegalStateException::class)
    fun getRequiredProperty(key: String): String

    @Throws(IllegalStateException::class)
    fun <T> getRequiredProperty(key: String, targetType: Class<T>): T

    fun resolvePlaceholders(text: String): String

    @Throws(IllegalArgumentException::class)
    fun resolveRequiredPlaceholders(text: String): String

}