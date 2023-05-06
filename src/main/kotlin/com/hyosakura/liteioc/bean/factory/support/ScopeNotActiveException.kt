package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.bean.factory.BeanCreationException

/**
 * @author LovesAsuna
 **/
class ScopeNotActiveException : BeanCreationException {

    constructor(beanName: String, scopeName: String, cause: IllegalStateException) : super(
        beanName,
        "Scope '$scopeName' is not active for the current thread; consider defining a scoped proxy for this bean if you intend to refer to it from a singleton",
        cause
    )

}