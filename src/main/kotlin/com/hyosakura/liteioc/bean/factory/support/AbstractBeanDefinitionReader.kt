package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.bean.factory.BeanDefinitionStoreException
import com.hyosakura.liteioc.core.env.Environment
import com.hyosakura.liteioc.core.env.EnvironmentCapable
import com.hyosakura.liteioc.core.env.StandardEnvironment
import com.hyosakura.liteioc.core.io.Resource
import com.hyosakura.liteioc.core.io.ResourceLoader
import com.hyosakura.liteioc.core.io.support.PathMatchingResourcePatternResolver
import com.hyosakura.liteioc.core.io.support.ResourcePatternResolver
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*

/**
 * @author LovesAsuna
 **/
abstract class AbstractBeanDefinitionReader : BeanDefinitionReader, EnvironmentCapable {

    protected val logger: Logger = LoggerFactory.getLogger(javaClass)

    private val registry: BeanDefinitionRegistry

    private var resourceLoader: ResourceLoader? = null

    private var beanClassLoader: ClassLoader? = null

    private var environment: Environment

    protected constructor(registry: BeanDefinitionRegistry) {
        this.registry = registry

        // Determine ResourceLoader to use.
        if (this.registry is ResourceLoader) {
            this.resourceLoader = this.registry
        } else {
            this.resourceLoader = PathMatchingResourcePatternResolver()
        }

        // Inherit Environment if possible
        if (this.registry is EnvironmentCapable) {
            this.environment = (this.registry as EnvironmentCapable).getEnvironment()
        } else {
            this.environment = StandardEnvironment()
        }
    }

    override fun getEnvironment(): Environment {
        return this.environment
    }

    override fun getRegistry(): BeanDefinitionRegistry {
        return this.registry
    }

    fun setEnvironment(environment: Environment) {
        this.environment = environment
    }

    open fun setResourceLoader(resourceLoader: ResourceLoader?) {
        this.resourceLoader = resourceLoader
    }

    open fun getResourceLoader(): ResourceLoader? {
        return resourceLoader
    }

    @Throws(BeanDefinitionStoreException::class)
    override fun loadBeanDefinitions(vararg resources: Resource): Int {
        var count = 0
        for (resource in resources) {
            count += loadBeanDefinitions(resource)
        }
        return count
    }

    @Throws(BeanDefinitionStoreException::class)
    override fun loadBeanDefinitions(vararg locations: String): Int {
        var count = 0
        for (location in locations) {
            count += loadBeanDefinitions(location)
        }
        return count
    }

    @Throws(BeanDefinitionStoreException::class)
    override fun loadBeanDefinitions(location: String): Int {
        return loadBeanDefinitions(location, null)
    }

    @Throws(BeanDefinitionStoreException::class)
    open fun loadBeanDefinitions(location: String, actualResources: MutableSet<Resource>?): Int {
        val resourceLoader = getResourceLoader()
            ?: throw BeanDefinitionStoreException(
                "Cannot load bean definitions from location [$location]: no ResourceLoader available"
            )
        return if (resourceLoader is ResourcePatternResolver) {
            // Resource pattern matching available.
            try {
                val resources: Array<Resource> = resourceLoader.getResources(location)
                val count = loadBeanDefinitions(*resources)
                if (actualResources != null) {
                    Collections.addAll(actualResources, *resources)
                }
                if (logger.isTraceEnabled) {
                    logger.trace("Loaded $count bean definitions from location pattern [$location]")
                }
                count
            } catch (ex: IOException) {
                throw BeanDefinitionStoreException(
                    "Could not resolve bean definition resource pattern [$location]", ex
                )
            }
        } else {
            // Can only load single resources by absolute URL.
            val resource = resourceLoader.getResource(location)
            val count = loadBeanDefinitions(resource)
            actualResources?.add(resource)
            if (logger.isTraceEnabled) {
                logger.trace("Loaded $count bean definitions from location [$location]")
            }
            count
        }
    }

}