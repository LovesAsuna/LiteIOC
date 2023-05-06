package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.bean.BeanDefinition

/**
 * @author LovesAsuna
 **/
interface BeanNameGenerator {

    fun generateBeanName(definition: BeanDefinition, registry: BeanDefinitionRegistry): String

}