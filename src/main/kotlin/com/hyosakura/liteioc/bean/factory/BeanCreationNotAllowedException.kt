package com.hyosakura.liteioc.bean.factory

/**
 * @author LovesAsuna
 **/
class BeanCreationNotAllowedException : BeanCreationException {

    constructor(beanName: String, msg: String) : super(beanName, msg)

}