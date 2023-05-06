package com.hyosakura.liteioc.bean.factory

/**
 * @author LovesAsuna
 **/
class BeanCurrentlyInCreationException : BeanCreationException {

    constructor(beanName: String) : super(
        beanName,
        "Requested bean is currently in creation: Is there an unresolvable circular reference?"
    )

    constructor(beanName: String, msg: String) : super(beanName, msg)

}