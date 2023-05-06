package com.hyosakura.liteioc.context.annotation

import com.hyosakura.liteioc.bean.BeanDefinition

/**
 * @author LovesAsuna
 **/
class FullyQualifiedAnnotationBeanNameGenerator : AnnotationBeanNameGenerator() {

    companion object {

        val INSTANCE = FullyQualifiedAnnotationBeanNameGenerator()

    }

    override fun buildDefaultBeanName(definition: BeanDefinition): String {
        val beanClassName = definition.getBeanClassName()
        requireNotNull(beanClassName != null) { "No bean class name set" }
        return beanClassName!!
    }

}