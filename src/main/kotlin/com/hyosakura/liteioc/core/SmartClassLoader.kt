package com.hyosakura.liteioc.core

import java.security.ProtectionDomain

/**
 * @author LovesAsuna
 **/
interface SmartClassLoader {

    fun isClassReloadable(clazz: Class<*>): Boolean {
        return false
    }

    fun getOriginalClassLoader(): ClassLoader {
        return this as ClassLoader
    }

    fun publicDefineClass(name: String, b: ByteArray, protectionDomain: ProtectionDomain?): Class<*> {
        throw UnsupportedOperationException()
    }

}