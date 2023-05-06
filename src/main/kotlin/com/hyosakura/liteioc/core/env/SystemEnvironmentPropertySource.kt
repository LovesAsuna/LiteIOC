package com.hyosakura.liteioc.core.env

import java.util.*

/**
 * @author LovesAsuna
 **/
class SystemEnvironmentPropertySource : MapPropertySource {

    constructor(name: String, source: Map<String, Any>) : super(name, source)

    override fun containsProperty(name: String): Boolean {
        return getProperty(name) != null
    }

    override fun getProperty(name: String): Any? {
        val actualName = resolvePropertyName(name)
        if (logger.isDebugEnabled && name != actualName) {
            logger.debug(
                "PropertySource '" + name + "' does not contain property '" + name +
                        "', but found equivalent '" + actualName + "'"
            )
        }
        return super.getProperty(actualName)
    }

    protected fun resolvePropertyName(name: String): String {
        var resolvedName = checkPropertyName(name)
        if (resolvedName != null) {
            return resolvedName
        }
        val uppercasedName = name.uppercase(Locale.getDefault())
        if (name != uppercasedName) {
            resolvedName = checkPropertyName(uppercasedName)
            if (resolvedName != null) {
                return resolvedName
            }
        }
        return name
    }

    private fun checkPropertyName(name: String): String? {
        // Check name as-is
        if (source.containsKey(name)) {
            return name
        }
        // Check name with just dots replaced
        val noDotName = name.replace('.', '_')
        if (name != noDotName && source.containsKey(noDotName)) {
            return noDotName
        }
        // Check name with just hyphens replaced
        val noHyphenName = name.replace('-', '_')
        if (name != noHyphenName && source.containsKey(noHyphenName)) {
            return noHyphenName
        }
        // Check name with dots and hyphens replaced
        val noDotNoHyphenName = noDotName.replace('-', '_')
        return if (noDotName != noDotNoHyphenName && source.containsKey(noDotNoHyphenName)) {
            noDotNoHyphenName
        } else null
        // Give up
    }

}