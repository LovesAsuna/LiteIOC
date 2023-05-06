package com.hyosakura.liteioc.context.annotation

import com.hyosakura.liteioc.bean.factory.annotation.AnnotatedGenericBeanDefinition
import com.hyosakura.liteioc.bean.factory.annotation.AnnotationScopeMetadataResolver
import com.hyosakura.liteioc.bean.factory.config.BeanDefinitionCustomizer
import com.hyosakura.liteioc.bean.factory.config.BeanDefinitionHolder
import com.hyosakura.liteioc.bean.factory.support.BeanDefinitionReaderUtils
import com.hyosakura.liteioc.bean.factory.support.BeanDefinitionRegistry
import com.hyosakura.liteioc.bean.factory.support.BeanNameGenerator
import com.hyosakura.liteioc.core.annotation.AnnotationConfigUtil
import com.hyosakura.liteioc.core.env.Environment
import com.hyosakura.liteioc.core.env.EnvironmentCapable
import com.hyosakura.liteioc.core.env.StandardEnvironment
import java.util.function.Supplier

/**
 * @author LovesAsuna
 **/
class AnnotatedBeanDefinitionReader {

    companion object {

        private fun getOrCreateEnvironment(registry: BeanDefinitionRegistry): Environment {
            return if (registry is EnvironmentCapable) {
                (registry as EnvironmentCapable).getEnvironment()
            } else StandardEnvironment()
        }

    }

    private val registry: BeanDefinitionRegistry

    private val beanNameGenerator: BeanNameGenerator = AnnotationBeanNameGenerator.INSTANCE

    private val scopeMetadataResolver: ScopeMetadataResolver = AnnotationScopeMetadataResolver()

    constructor(registry: BeanDefinitionRegistry) : this(registry, getOrCreateEnvironment(registry))

    constructor(registry: BeanDefinitionRegistry, environment: Environment) {
        this.registry = registry
    }

    fun register(vararg componentClasses: Class<*>) {
        for (componentClass in componentClasses) {
            registerBean(componentClass)
        }
    }

    fun registerBean(beanClass: Class<*>) {
        doRegisterBean(beanClass, null, null, null)
    }

    fun registerBean(beanClass: Class<*>, name: String?) {
        doRegisterBean(beanClass, name, null, null)
    }

    private fun <T> doRegisterBean(
        beanClass: Class<T>,
        name: String?,
        supplier: Supplier<T>?,
        customizers: Array<BeanDefinitionCustomizer>?
    ) {
        val abd = AnnotatedGenericBeanDefinition(beanClass)

        abd.setInstanceSupplier(supplier)

        val scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(abd)

        abd.setScope(scopeMetadata.getScopeName())

        val beanName = name ?: this.beanNameGenerator.generateBeanName(abd, registry)

        AnnotationConfigUtil.processCommonDefinitionAnnotations(abd)

        if (customizers != null) {
            for (customizer in customizers) {
                customizer.customize(abd)
            }
        }

        val definitionHolder = BeanDefinitionHolder(abd, beanName)

        BeanDefinitionReaderUtils.registerBeanDefinition(definitionHolder, registry)
    }

}