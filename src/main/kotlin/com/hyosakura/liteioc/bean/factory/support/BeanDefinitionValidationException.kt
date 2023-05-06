package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.bean.FatalBeanException

/**
 * @author LovesAsuna
 **/
class BeanDefinitionValidationException : FatalBeanException {

    constructor(msg: String) : super(msg)

    constructor(msg: String, cause: Throwable) : super(msg, cause)

}