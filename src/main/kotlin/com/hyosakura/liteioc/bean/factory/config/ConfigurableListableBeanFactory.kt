package com.hyosakura.liteioc.bean.factory.config

import com.hyosakura.liteioc.bean.BeanDefinition
import com.hyosakura.liteioc.bean.BeansException
import com.hyosakura.liteioc.bean.factory.ListableBeanFactory
import com.hyosakura.liteioc.bean.factory.NoSuchBeanDefinitionException

/**
 * @author LovesAsuna
 **/
interface ConfigurableListableBeanFactory : ListableBeanFactory, AutowireCapableBeanFactory, ConfigurableBeanFactory {

    fun registerResolvableDependency(dependencyType: Class<*>, autowiredValue: Any?)

    @Throws(BeansException::class)
    fun preInstantiateSingletons()

    @Throws(NoSuchBeanDefinitionException::class)
    fun getBeanDefinition(beanName: String): BeanDefinition

    @Throws(NoSuchBeanDefinitionException::class)
    fun isAutowireCandidate(beanName: String, descriptor: DependencyDescriptor): Boolean

}