package com.hyosakura.liteioc.core.env

import com.hyosakura.liteioc.core.convert.ConversionService
import com.hyosakura.liteioc.core.convert.support.DefaultConversionService
import com.hyosakura.liteioc.util.ClassUtil
import com.hyosakura.liteioc.util.PropertyPlaceholderHelper
import org.jetbrains.annotations.Nullable
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author LovesAsuna
 **/
abstract class AbstractPropertyResolver : ConfigurablePropertyResolver {

    val logger: Logger = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var conversionService: ConversionService? = null

    private var ignoreUnresolvableNestedPlaceholders = false

    private var nonStrictHelper: PropertyPlaceholderHelper? = null

    private var strictHelper: PropertyPlaceholderHelper? = null

    private val placeholderPrefix: String = "${"$"}{"

    private val placeholderSuffix: String = "}"

    @Nullable
    private val valueSeparator: String = ":"
    override fun containsProperty(key: String): Boolean {
        return getProperty(key) != null
    }

    override fun getProperty(key: String): String? {
        return getProperty(key, String::class.java)
    }

    override fun getProperty(key: String, defaultValue: String): String {
        val value = getProperty(key)
        return value ?: defaultValue
    }

    override fun <T> getProperty(key: String, targetType: Class<T>, defaultValue: T): T {
        val value = getProperty(key, targetType)
        return value ?: defaultValue
    }

    @Throws(IllegalStateException::class)
    override fun getRequiredProperty(key: String): String {
        return getProperty(key) ?: throw IllegalStateException("Required key '$key' not found")
    }

    @Throws(IllegalStateException::class)
    override fun <T> getRequiredProperty(key: String, valueType: Class<T>): T {
        return getProperty(key, valueType) ?: throw IllegalStateException("Required key '$key' not found")
    }

    private fun createPlaceholderHelper(ignoreUnresolvablePlaceholders: Boolean): PropertyPlaceholderHelper {
        return PropertyPlaceholderHelper(
            this.placeholderPrefix, this.placeholderSuffix,
            this.valueSeparator, ignoreUnresolvablePlaceholders
        )
    }

    override fun resolvePlaceholders(text: String): String {
        if (this.nonStrictHelper == null) {
            this.nonStrictHelper = createPlaceholderHelper(true)
        }
        return doResolvePlaceholders(text, this.nonStrictHelper!!)
    }

    @Throws(IllegalArgumentException::class)
    override fun resolveRequiredPlaceholders(text: String): String {
        if (this.strictHelper == null) {
            this.strictHelper = createPlaceholderHelper(false)
        }
        return doResolvePlaceholders(text, this.strictHelper!!)
    }

    private fun doResolvePlaceholders(text: String, helper: PropertyPlaceholderHelper): String {
        return helper.replacePlaceholders(text) { key -> this.getPropertyAsRawString(key) }
    }

    protected open fun resolveNestedPlaceholders(value: String): String {
        if (value.isEmpty()) {
            return value
        }
        return if (this.ignoreUnresolvableNestedPlaceholders) resolvePlaceholders(value) else resolveRequiredPlaceholders(
            value
        )
    }

    @Suppress("UNCHECKED_CAST")
    protected open fun <T> convertValueIfNecessary(value: Any, targetType: Class<T>?): T? {
        if (targetType == null) {
            return value as T
        }
        var conversionServiceToUse = this.conversionService
        if (conversionServiceToUse == null) {
            // Avoid initialization of shared DefaultConversionService if
            // no standard type conversion is needed in the first place...
            if (ClassUtil.isAssignableValue(targetType, value)) {
                return value as T
            }
            conversionServiceToUse = DefaultConversionService.getSharedInstance()
        }
        return conversionServiceToUse!!.convert(value, targetType)
    }

    protected abstract fun getPropertyAsRawString(key: String): String?

}