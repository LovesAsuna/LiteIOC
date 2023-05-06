package com.hyosakura.liteioc.bean.factory

import com.hyosakura.liteioc.bean.BeansException
import com.hyosakura.liteioc.util.ClassUtil

/**
 * @author LovesAsuna
 **/
class BeanNotOfRequiredTypeException : BeansException {

    private val beanName: String

    private val requiredType: Class<*>

    private val actualType: Class<*>

    constructor(beanName: String, requiredType: Class<*>, actualType: Class<*>) : super(
        "Bean named '" + beanName + "' is expected to be of type '" + ClassUtil.getQualifiedName(requiredType) + "' but was actually of type '" + ClassUtil.getQualifiedName(
            actualType
        ) + "'"
    ) {
        this.beanName = beanName
        this.requiredType = requiredType
        this.actualType = actualType
    }

}