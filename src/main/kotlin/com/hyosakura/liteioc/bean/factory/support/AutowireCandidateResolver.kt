package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.bean.factory.config.BeanDefinitionHolder
import com.hyosakura.liteioc.bean.factory.config.DependencyDescriptor
import com.hyosakura.liteioc.util.BeanUtil

/**
 * @author LovesAsuna
 **/
interface AutowireCandidateResolver {
    fun isAutowireCandidate(bdHolder: BeanDefinitionHolder, descriptor: DependencyDescriptor): Boolean {
        return bdHolder.getBeanDefinition().isAutowireCandidate()
    }

    fun isRequired(descriptor: DependencyDescriptor): Boolean {
        return descriptor.isRequired()
    }

    fun hasQualifier(descriptor: DependencyDescriptor): Boolean {
        return false
    }

    fun getSuggestedValue(descriptor: DependencyDescriptor): Any? {
        return null
    }

    fun getLazyResolutionProxyIfNecessary(descriptor: DependencyDescriptor, beanName: String?): Any? {
        return null
    }

    fun cloneIfNecessary(): AutowireCandidateResolver {
        return BeanUtil.instantiateClass(javaClass)
    }

}