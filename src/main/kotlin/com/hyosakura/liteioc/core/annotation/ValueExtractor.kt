package com.hyosakura.liteioc.core.annotation

import java.lang.reflect.Method

/**
 * @author LovesAsuna
 **/
fun interface ValueExtractor {

    fun extract(attribute: Method, `object`: Any?): Any?

}