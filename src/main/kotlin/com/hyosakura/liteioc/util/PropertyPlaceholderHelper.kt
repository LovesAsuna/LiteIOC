package com.hyosakura.liteioc.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PropertyPlaceholderHelper {

    companion object {

        private val wellKnownSimplePrefixes: MutableMap<String, String> = mutableMapOf(
            "}" to "{",
            "]" to "[",
            ")" to "("
        )

    }

    private val placeholderPrefix: String

    private val placeholderSuffix: String

    private var simplePrefix: String

    private val valueSeparator: String?

    private val ignoreUnresolvablePlaceholders: Boolean

    constructor(placeholderPrefix: String, placeholderSuffix: String) : this(
        placeholderPrefix,
        placeholderSuffix,
        null,
        true
    )

    constructor(
        placeholderPrefix: String, placeholderSuffix: String,
        valueSeparator: String?, ignoreUnresolvablePlaceholders: Boolean
    ) {
        this.placeholderPrefix = placeholderPrefix
        this.placeholderSuffix = placeholderSuffix
        val simplePrefixForSuffix = wellKnownSimplePrefixes[this.placeholderSuffix]
        simplePrefix = if (simplePrefixForSuffix != null && this.placeholderPrefix.endsWith(simplePrefixForSuffix)) {
            simplePrefixForSuffix
        } else {
            this.placeholderPrefix
        }
        this.valueSeparator = valueSeparator
        this.ignoreUnresolvablePlaceholders = ignoreUnresolvablePlaceholders
    }

    private val logger: Logger = LoggerFactory.getLogger(
        PropertyPlaceholderHelper::class.java
    )

    fun replacePlaceholders(value: String, placeholderResolver: PlaceholderResolver): String {
        return parseStringValue(value, placeholderResolver, null)
    }

    private fun parseStringValue(
        value: String, placeholderResolver: PlaceholderResolver, visitedPlaceholders: MutableSet<String>?
    ): String {
        var visitedPlaceholders = visitedPlaceholders
        var startIndex: Int = value.indexOf(this.placeholderPrefix)
        if (startIndex == -1) {
            return value
        }
        val result = StringBuilder(value)
        while (startIndex != -1) {
            val endIndex: Int = findPlaceholderEndIndex(result, startIndex)
            if (endIndex != -1) {
                var placeholder = result.substring(startIndex + this.placeholderPrefix.length, endIndex)
                val originalPlaceholder = placeholder
                if (visitedPlaceholders == null) {
                    visitedPlaceholders = HashSet(4)
                }
                require(visitedPlaceholders.add(originalPlaceholder)) { "Circular placeholder reference '$originalPlaceholder' in property definitions" }
                // Recursive invocation, parsing placeholders contained in the placeholder key.
                placeholder = parseStringValue(placeholder, placeholderResolver, visitedPlaceholders)
                // Now obtain the value for the fully resolved key...
                var propVal = placeholderResolver.resolvePlaceholder(placeholder)
                if (propVal == null && this.valueSeparator != null) {
                    val separatorIndex: Int = placeholder.indexOf(this.valueSeparator)
                    if (separatorIndex != -1) {
                        val actualPlaceholder = placeholder.substring(0, separatorIndex)
                        val defaultValue: String = placeholder.substring(separatorIndex + this.valueSeparator.length)
                        propVal = placeholderResolver.resolvePlaceholder(actualPlaceholder)
                        if (propVal == null) {
                            propVal = defaultValue
                        }
                    }
                }
                if (propVal != null) {
                    // Recursive invocation, parsing placeholders contained in the
                    // previously resolved placeholder value.
                    propVal = parseStringValue(propVal, placeholderResolver, visitedPlaceholders)
                    result.replace(startIndex, endIndex + this.placeholderSuffix.length, propVal)
                    if (logger.isTraceEnabled) {
                        logger.trace("Resolved placeholder '$placeholder'")
                    }
                    startIndex = result.indexOf(this.placeholderPrefix, startIndex + propVal.length)
                } else if (this.ignoreUnresolvablePlaceholders) {
                    // Proceed with unprocessed value.
                    startIndex = result.indexOf(this.placeholderPrefix, endIndex + this.placeholderSuffix.length)
                } else {
                    throw IllegalArgumentException(
                        "Could not resolve placeholder '" + placeholder + "'" + " in value \"" + value + "\""
                    )
                }
                visitedPlaceholders.remove(originalPlaceholder)
            } else {
                startIndex = -1
            }
        }
        return result.toString()
    }

    private fun findPlaceholderEndIndex(buf: CharSequence, startIndex: Int): Int {
        var index = startIndex + placeholderPrefix.length
        var withinNestedPlaceholder = 0
        while (index < buf.length) {
            if (StringUtil.substringMatch(buf, index, placeholderSuffix)) {
                index = if (withinNestedPlaceholder > 0) {
                    withinNestedPlaceholder--
                    index + placeholderSuffix.length
                } else {
                    return index
                }
            } else if (StringUtil.substringMatch(buf, index, simplePrefix)) {
                withinNestedPlaceholder++
                index += simplePrefix.length
            } else {
                index++
            }
        }
        return -1
    }

    fun interface PlaceholderResolver {

        fun resolvePlaceholder(placeholderName: String): String?

    }

}