package com.hyosakura.liteioc.core.env

/**
 * @author LovesAsuna
 **/
class PropertySourcesPropertyResolver(private val propertySources: PropertySources?) : AbstractPropertyResolver() {
    override fun containsProperty(key: String): Boolean {
        if (propertySources != null) {
            for (propertySource in propertySources) {
                if (propertySource.containsProperty(key)) {
                    return true
                }
            }
        }
        return false
    }

    override fun getProperty(key: String): String? {
        return getProperty(key, String::class.java, true)
    }

    override fun <T> getProperty(key: String, targetValueType: Class<T>): T? {
        return getProperty(key, targetValueType, true)
    }

    override fun getPropertyAsRawString(key: String): String? {
        return getProperty(key, String::class.java, false)
    }

    private fun <T> getProperty(key: String, targetValueType: Class<T>, resolveNestedPlaceholders: Boolean): T? {
        if (propertySources != null) {
            for (propertySource: PropertySource<*> in propertySources) {
                if (logger.isTraceEnabled) {
                    logger.trace(
                        ("Searching for key '" + key + "' in PropertySource '" + propertySource.name) + "'"
                    )
                }
                var value = propertySource.getProperty(key)
                if (value != null) {
                    if (resolveNestedPlaceholders && value is String) {
                        value = resolveNestedPlaceholders(value)
                    }
                    logKeyFound(key, propertySource, value)
                    return convertValueIfNecessary(value, targetValueType)
                }
            }
        }
        if (logger.isTraceEnabled) {
            logger.trace("Could not find key '$key' in any property source")
        }
        return null
    }

    private fun logKeyFound(key: String, propertySource: PropertySource<*>, value: Any) {
        if (logger.isDebugEnabled) {
            logger.debug(
                (("Found key '" + key + "' in PropertySource '" + propertySource.name) + "' with value of type " + value.javaClass.simpleName)
            )
        }
    }

}
