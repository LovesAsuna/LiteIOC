package com.hyosakura.liteioc.bean.factory.config

import com.hyosakura.liteioc.bean.BeanDefinition
import com.hyosakura.liteioc.bean.TypeConverter
import com.hyosakura.liteioc.bean.factory.BeanFactory
import com.hyosakura.liteioc.bean.factory.NoSuchBeanDefinitionException

/**
 * @author LovesAsuna
 **/
interface ConfigurableBeanFactory : BeanFactory, SingletonBeanRegistry {

    companion object {

        const val SCOPE_SINGLETON = "singleton"

        const val SCOPE_PROTOTYPE = "prototype"

    }

    @Throws(NoSuchBeanDefinitionException::class)
    fun getMergedBeanDefinition(beanName: String): BeanDefinition

    fun setCurrentlyInCreation(beanName: String, inCreation: Boolean)

    fun isCurrentlyInCreation(beanName: String): Boolean

    fun isCacheBeanMetadata(): Boolean

    @Throws(IllegalStateException::class)
    fun setParentBeanFactory(parentBeanFactory: BeanFactory?)

    fun getTypeConverter(): TypeConverter

    fun getBeanClassLoader(): ClassLoader?

    fun destroySingletons()

    fun registerDependentBean(beanName: String, dependentBeanName: String)

}