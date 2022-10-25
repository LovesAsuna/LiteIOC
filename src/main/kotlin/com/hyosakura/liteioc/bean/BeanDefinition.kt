package com.hyosakura.liteioc.bean

/**
 * 用于封装Bean标签数据
 *
 * @author LovesAsuna
 */
data class BeanDefinition(
    var id: String? = null,
    var className: String? = null,
    var propertyValues: MutablePropertyValues = MutablePropertyValues()
)