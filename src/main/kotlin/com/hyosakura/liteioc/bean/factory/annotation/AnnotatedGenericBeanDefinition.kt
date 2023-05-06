package com.hyosakura.liteioc.bean.factory.annotation

import com.hyosakura.liteioc.bean.factory.support.GenericBeanDefinition
import com.hyosakura.liteioc.core.type.AnnotationMetadata
import com.hyosakura.liteioc.core.type.StandardAnnotationMetadata

/**
 * @author LovesAsuna
 **/
class AnnotatedGenericBeanDefinition : GenericBeanDefinition, AnnotatedBeanDefinition {

    private val metadata: AnnotationMetadata

    constructor(beanClass: Class<*>) {
        setBeanClass(beanClass)
        this.metadata = AnnotationMetadata.introspect(beanClass)
    }

    constructor(metadata: AnnotationMetadata) {
        if (metadata is StandardAnnotationMetadata) {
            setBeanClass(metadata.getIntrospectedClass())
        } else {
            setBeanClassName(metadata.getClassName())
        }
        this.metadata = metadata
    }

    override fun getMetadata(): AnnotationMetadata {
        return metadata
    }

}