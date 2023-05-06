package com.hyosakura.liteioc.core

import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * @author LovesAsuna
 **/
interface ParameterNameDiscoverer {

    fun getParameterNames(method: Method): Array<String>?

    fun getParameterNames(ctor: Constructor<*>): Array<String>?

}