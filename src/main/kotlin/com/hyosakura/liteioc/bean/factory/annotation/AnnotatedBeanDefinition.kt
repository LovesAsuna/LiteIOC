package com.hyosakura.liteioc.bean.factory.annotation

import com.hyosakura.liteioc.bean.BeanDefinition
import com.hyosakura.liteioc.core.type.AnnotationMetadata

/**
 * @author LovesAsuna
 **/
interface AnnotatedBeanDefinition : BeanDefinition {

    fun getMetadata(): AnnotationMetadata

}