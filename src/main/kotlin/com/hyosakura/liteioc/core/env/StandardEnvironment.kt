package com.hyosakura.liteioc.core.env

import org.apache.logging.log4j.util.PropertiesUtil.getSystemProperties

class StandardEnvironment : AbstractEnvironment {

    constructor()

    constructor(propertySources: MutablePropertySources) : super(propertySources)

    override fun customizePropertySources(propertySources: MutablePropertySources) {
        propertySources.addLast(
            PropertiesPropertySource(SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, getSystemProperties())
        )
        propertySources.addLast(
            SystemEnvironmentPropertySource(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, getSystemEnvironment())
        )
    }

    companion object {

        const val SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME = "systemEnvironment"

        const val SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME = "systemProperties"

    }

}