package com.hyosakura.liteioc.bean.factory

import com.hyosakura.liteioc.core.ResolvableType

/**
 * @author LovesAsuna
 **/
interface ListableBeanFactory : BeanFactory {

    fun containsBeanDefinition(beanName: String): Boolean

    fun getBeanDefinitionCount(): Int

    fun getBeanDefinitionNames(): Array<String>

    fun <T> getBeanProvider(requiredType: Class<T>, allowEagerInit: Boolean): ObjectProvider<T>

    fun <T> getBeanProvider(requiredType: ResolvableType, allowEagerInit: Boolean): ObjectProvider<T>

    fun getBeanNamesForType(
        type: Class<*>?,
        includeNonSingletons: Boolean,
        allowEagerInit: Boolean
    ): Array<String>

    fun getBeanNamesForType(type: ResolvableType): Array<String>

}