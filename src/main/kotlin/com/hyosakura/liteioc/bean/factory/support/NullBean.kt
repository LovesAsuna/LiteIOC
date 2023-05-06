package com.hyosakura.liteioc.bean.factory.support

/**
 * @author LovesAsuna
 **/
class NullBean {

    override fun equals(obj: Any?): Boolean {
        return this === obj || obj == null
    }

    override fun hashCode(): Int {
        return NullBean::class.java.hashCode()
    }

    override fun toString(): String {
        return "null"
    }

}