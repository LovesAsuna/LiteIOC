package com.hyosakura.liteioc.core

import java.lang.reflect.Method

object GenericTypeResolver {

    fun resolveReturnType(method: Method, clazz: Class<*>): Class<*> {
        return ResolvableType.forMethodReturnType(method, clazz).resolve(method.returnType)
    }

}