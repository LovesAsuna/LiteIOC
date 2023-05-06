package com.hyosakura.liteioc.aop.support

import com.hyosakura.liteioc.aop.LiteIocProxy
import com.hyosakura.liteioc.aop.TargetClassAware
import com.hyosakura.liteioc.util.ClassUtil

object AopUtil {
    fun getTargetClass(candidate: Any): Class<*> {
        var result: Class<*>? = null
        if (candidate is TargetClassAware) {
            result = candidate.getTargetClass()
        }
        if (result == null) {
            result =
                if (isByteBuddyProxy(candidate)) candidate.javaClass.superclass else candidate.javaClass
        }
        return result!!
    }

    fun isByteBuddyProxy(`object`: Any?): Boolean {
        return `object` is LiteIocProxy &&
                `object`.javaClass.name.contains(ClassUtil.BYTEBUDDY_CLASS_SEPARATOR)
    }
}