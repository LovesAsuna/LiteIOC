package com.hyosakura.liteioc.context

import com.hyosakura.liteioc.bean.FatalBeanException

/**
 * @author LovesAsuna
 **/
class ApplicationContextException : FatalBeanException {

    constructor(msg: String) : super(msg)

    constructor(msg: String, cause: Throwable?) : super(msg, cause)

}