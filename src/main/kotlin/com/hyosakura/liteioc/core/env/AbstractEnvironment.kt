package com.hyosakura.liteioc.core.env

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

/**
 * @author LovesAsuna
 **/
abstract class AbstractEnvironment : ConfigurableEnvironment {

    companion object {

        const val ACTIVE_PROFILES_PROPERTY_NAME = "ioc.profiles.active"

        const val RESERVED_DEFAULT_PROFILE_NAME = "default"

        const val DEFAULT_PROFILES_PROPERTY_NAME = "ioc.profiles.default"

    }

    private val activeProfiles: MutableSet<String> = LinkedHashSet()

    private val defaultProfiles: MutableSet<String> = LinkedHashSet<String>(getReservedDefaultProfiles())

    private val propertySources: MutablePropertySources

    private val propertyResolver: PropertyResolver

    protected val logger: Logger = LoggerFactory.getLogger(javaClass)

    constructor() : this(MutablePropertySources())

    constructor(propertySources: MutablePropertySources) {
        this.propertySources = propertySources
        propertyResolver = createPropertyResolver(propertySources)
        customizePropertySources(propertySources)
    }

    override fun getProperty(key: String): String? {
        return getProperty(key, String::class.java)
    }

    override fun getProperty(key: String, defaultValue: String): String {
        val value = getProperty(key)
        return value ?: defaultValue
    }

    override fun <T> getProperty(key: String, targetType: Class<T>): T? {
        return propertyResolver.getProperty(key, targetType)
    }

    override fun <T> getProperty(key: String, targetType: Class<T>, defaultValue: T): T {
        val value = getProperty(key, targetType)
        return value ?: defaultValue
    }

    @Throws(IllegalStateException::class)
    override fun getRequiredProperty(key: String): String {
        return propertyResolver.getRequiredProperty(key)
    }

    open fun getSystemEnvironment(): Map<String, Any> {
        return System.getenv()
    }

    @Throws(IllegalStateException::class)
    override fun <T> getRequiredProperty(key: String, targetType: Class<T>): T {
        return propertyResolver.getRequiredProperty(key, targetType)
    }

    override fun resolvePlaceholders(text: String): String {
        return propertyResolver.resolvePlaceholders(text)
    }

    @Throws(IllegalArgumentException::class)
    override fun resolveRequiredPlaceholders(text: String): String {
        return propertyResolver.resolveRequiredPlaceholders(text)
    }

    open fun customizePropertySources(propertySources: MutablePropertySources) {}

    open fun createPropertyResolver(propertySources: MutablePropertySources): ConfigurablePropertyResolver {
        return PropertySourcesPropertyResolver(propertySources)
    }

    open fun getReservedDefaultProfiles(): Set<String> {
        return setOf(RESERVED_DEFAULT_PROFILE_NAME)
    }

    override fun setActiveProfiles(vararg profiles: String) {
        if (logger.isDebugEnabled) {
            logger.debug("Activating profiles " + listOf(*profiles))
        }
        synchronized(this.activeProfiles) {
            this.activeProfiles.clear()
            for (profile in profiles) {
                validateProfile(profile)
                this.activeProfiles.add(profile)
            }
        }
    }

    override fun addActiveProfile(profile: String) {
        if (logger.isDebugEnabled) {
            logger.debug("Activating profile '$profile'")
        }
        validateProfile(profile)
        doGetActiveProfiles()
        synchronized(activeProfiles) { activeProfiles.add(profile) }
    }

    open fun doGetActiveProfiles(): Set<String>? {
        synchronized(activeProfiles) {
            if (activeProfiles.isEmpty()) {
                val profiles = doGetActiveProfilesProperty()
                if (!profiles.isNullOrEmpty()) {
                    setActiveProfiles(
                        *profiles.trim().split(",").toTypedArray()
                    )
                }
            }
            return activeProfiles
        }
    }

    open fun doGetActiveProfilesProperty(): String? {
        return getProperty(ACTIVE_PROFILES_PROPERTY_NAME)
    }

    open fun doGetDefaultProfiles(): Set<String>? {
        synchronized(defaultProfiles) {
            if (defaultProfiles == getReservedDefaultProfiles()) {
                val profiles = doGetDefaultProfilesProperty()
                if (!profiles.isNullOrEmpty()) {
                    setDefaultProfiles(
                        *profiles.trim().split(",").toTypedArray()
                    )
                }
            }
            return defaultProfiles
        }
    }

    open fun doGetDefaultProfilesProperty(): String? {
        return getProperty(DEFAULT_PROFILES_PROPERTY_NAME)
    }

    override fun setDefaultProfiles(vararg profiles: String) {
        synchronized(this.defaultProfiles) {
            this.defaultProfiles.clear()
            for (profile in profiles) {
                validateProfile(profile)
                this.defaultProfiles.add(profile)
            }
        }
    }

    override fun merge(parent: ConfigurableEnvironment) {
        val parentActiveProfiles = parent.getActiveProfiles()
        if (!parentActiveProfiles.isNullOrEmpty()) {
            synchronized(activeProfiles) {
                Collections.addAll(
                    activeProfiles,
                    *parentActiveProfiles
                )
            }
        }
        val parentDefaultProfiles = parent.getDefaultProfiles()
        if (!parentDefaultProfiles.isNullOrEmpty()) {
            synchronized(defaultProfiles) {
                defaultProfiles.remove(RESERVED_DEFAULT_PROFILE_NAME)
                Collections.addAll(defaultProfiles, *parentDefaultProfiles)
            }
        }
    }

    override fun getActiveProfiles(): Array<String>? {
        return doGetActiveProfiles()?.toTypedArray()
    }

    override fun getDefaultProfiles(): Array<String>? {
        return doGetDefaultProfiles()?.toTypedArray()
    }

    open fun validateProfile(profile: String?) {
        require(profile.isNullOrEmpty()) { "Invalid profile [$profile]: must contain text" }
        require(profile!![0] != '!') { "Invalid profile [$profile]: must not begin with ! operator" }
    }

    override fun acceptsProfiles(vararg profiles: String?): Boolean {
        for (profile in profiles) {
            if (!profile.isNullOrEmpty() && profile[0] == '!') {
                if (!isProfileActive(profile.substring(1))) {
                    return true
                }
            } else if (isProfileActive(profile)) {
                return true
            }
        }
        return false
    }

    override fun acceptsProfiles(profiles: Profiles?): Boolean {
        requireNotNull(profiles) { "Profiles must not be null" }
        return profiles.matches { profile -> isProfileActive(profile) }
    }

    open fun isProfileActive(profile: String?): Boolean {
        validateProfile(profile)
        val currentActiveProfiles = doGetActiveProfiles()
        return currentActiveProfiles!!.contains(profile) || currentActiveProfiles.isEmpty() && doGetDefaultProfiles()!!.contains(
            profile
        )
    }

    override fun containsProperty(key: String): Boolean {
        return this.propertyResolver.containsProperty(key)
    }

}