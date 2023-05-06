package com.hyosakura.liteioc.bean.factory

import com.hyosakura.liteioc.bean.BeansException

/**
 * @author LovesAsuna
 **/
class UnsatisfiedDependencyException : BeanCreationException {

    val injectionPoint: InjectionPoint?

    constructor(beanName: String?, propertyName: String, msg: String) : super(
        beanName,
        "Unsatisfied dependency expressed through bean property '" + propertyName + "'" + if (msg.isNotEmpty()) ": $msg" else ""
    ) {
        this.injectionPoint = null
    }

    constructor(beanName: String?, propertyName: String, ex: BeansException?) : this(beanName, propertyName, "") {
        initCause(ex)
    }

    constructor(
        beanName: String?, injectionPoint: InjectionPoint?, msg: String
    ) : super(
        beanName,
        "Unsatisfied dependency expressed through " + injectionPoint +
                if (msg.isNotEmpty()) ": $msg" else ""
    ) {
        this.injectionPoint = injectionPoint
    }

    constructor(beanName: String?, injectionPoint: InjectionPoint?, ex: BeansException?) : this(
        beanName,
        injectionPoint,
        ""
    ) {
        initCause(ex)
    }

}