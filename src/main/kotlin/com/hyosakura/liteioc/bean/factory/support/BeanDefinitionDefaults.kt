package com.hyosakura.liteioc.bean.factory.support

/**
 * @author LovesAsuna
 **/
data class BeanDefinitionDefaults(
    var lazyInit: Boolean = false,
    var autowireMode: Int = AbstractBeanDefinition.AUTOWIRE_NO,
    var dependencyCheck: Int = AbstractBeanDefinition.DEPENDENCY_CHECK_NONE,
    var initMethodName: String? = null,
    var destroyMethodName: String? = null,
)