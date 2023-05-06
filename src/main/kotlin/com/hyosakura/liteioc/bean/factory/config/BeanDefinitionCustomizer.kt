package com.hyosakura.liteioc.bean.factory.config

import com.hyosakura.liteioc.bean.BeanDefinition

/**
 * @author LovesAsuna
 **/
fun interface BeanDefinitionCustomizer {

    fun customize(bd: BeanDefinition)

}