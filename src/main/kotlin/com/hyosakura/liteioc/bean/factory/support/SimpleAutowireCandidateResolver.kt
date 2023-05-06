package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.bean.factory.config.BeanDefinitionHolder
import com.hyosakura.liteioc.bean.factory.config.DependencyDescriptor

/**
 * @author LovesAsuna
 **/
class SimpleAutowireCandidateResolver : AutowireCandidateResolver {

    companion object {

        val INSTANCE = SimpleAutowireCandidateResolver()

    }

    override fun isAutowireCandidate(bdHolder: BeanDefinitionHolder, descriptor: DependencyDescriptor): Boolean {
        return bdHolder.getBeanDefinition().isAutowireCandidate()
    }

    override fun isRequired(descriptor: DependencyDescriptor): Boolean {
        return descriptor.isRequired()
    }

    override fun hasQualifier(descriptor: DependencyDescriptor): Boolean {
        return false
    }

    override fun getSuggestedValue(descriptor: DependencyDescriptor): Any? {
        return null
    }

    override fun getLazyResolutionProxyIfNecessary(
        descriptor: DependencyDescriptor,
        beanName: String?
    ): Any? {
        return null
    }

    override fun cloneIfNecessary(): AutowireCandidateResolver {
        return this
    }

}