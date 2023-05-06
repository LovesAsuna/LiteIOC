package com.hyosakura.liteioc.util

/**
 * @author LovesAsuna
 **/
class MethodInvoker {

    companion object {
        fun getTypeDifferenceWeight(paramTypes: Array<Class<*>?>, args: Array<out Any?>): Int {
            var result = 0
            for (i in paramTypes.indices) {
                if (!ClassUtil.isAssignableValue(paramTypes[i]!!, args[i])) {
                    return Int.MAX_VALUE
                }
                if (args[i] != null) {
                    val paramType = paramTypes[i]!!
                    var superClass: Class<*>? = args[i]!!.javaClass.superclass
                    while (superClass != null) {
                        if (paramType == superClass) {
                            result += 2
                            superClass = null
                        } else if (ClassUtil.isAssignable(paramType, superClass)) {
                            result += 2
                            superClass = superClass.superclass
                        } else {
                            superClass = null
                        }
                    }
                    if (paramType.isInterface) {
                        result += 1
                    }
                }
            }
            return result
        }
    }


}