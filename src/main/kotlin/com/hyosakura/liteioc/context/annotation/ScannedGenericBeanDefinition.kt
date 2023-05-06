package com.hyosakura.liteioc.context.annotation

import com.hyosakura.liteioc.bean.factory.annotation.AnnotatedBeanDefinition
import com.hyosakura.liteioc.bean.factory.support.GenericBeanDefinition
import com.hyosakura.liteioc.core.type.AnnotationMetadata

/**
 * @author LovesAsuna
 **/
class ScannedGenericBeanDefinition : GenericBeanDefinition, AnnotatedBeanDefinition {

    private val metadata: AnnotationMetadata

    constructor(beanClass: Class<*>) {
        setBeanClass(beanClass)
        this.metadata = AnnotationMetadata.introspect(beanClass)
    }

    override fun getMetadata(): AnnotationMetadata {
        return metadata
    }

}