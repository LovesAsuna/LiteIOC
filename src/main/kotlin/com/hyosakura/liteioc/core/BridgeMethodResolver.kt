package com.hyosakura.liteioc.core

import com.hyosakura.liteioc.util.ClassUtil
import com.hyosakura.liteioc.util.ReflectionUtil
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object BridgeMethodResolver {

    private val cache: MutableMap<Method, Method> = ConcurrentHashMap()

    fun findBridgedMethod(bridgeMethod: Method): Method {
        if (!bridgeMethod.isBridge) {
            return bridgeMethod
        }
        var bridgedMethod = cache[bridgeMethod]
        if (bridgedMethod == null) {
            // Gather all methods with matching name and parameter size.
            val candidateMethods: MutableList<Method> = ArrayList()
            val filter = ReflectionUtil.MethodFilter { candidateMethod ->
                isBridgedCandidateFor(
                    candidateMethod,
                    bridgeMethod
                )
            }
            ReflectionUtil.doWithMethods(bridgeMethod.declaringClass, candidateMethods::add, filter)
            if (candidateMethods.isNotEmpty()) {
                bridgedMethod =
                    if (candidateMethods.size == 1) candidateMethods[0] else searchCandidates(
                        candidateMethods,
                        bridgeMethod
                    )
            }
            if (bridgedMethod == null) {
                // A bridge method was passed in but we couldn't find the bridged method.
                // Let's proceed with the passed-in method and hope for the best...
                bridgedMethod = bridgeMethod
            }
            cache.put(bridgeMethod, bridgedMethod)
        }
        return bridgedMethod
    }

    private fun isBridgedCandidateFor(candidateMethod: Method, bridgeMethod: Method): Boolean {
        return !candidateMethod.isBridge && candidateMethod.name == bridgeMethod.name && candidateMethod.parameterCount == bridgeMethod.parameterCount
    }

    private fun searchCandidates(candidateMethods: List<Method>, bridgeMethod: Method): Method? {
        if (candidateMethods.isEmpty()) {
            return null
        }
        var previousMethod: Method? = null
        var sameSig = true
        for (candidateMethod in candidateMethods) {
            if (isBridgeMethodFor(bridgeMethod, candidateMethod, bridgeMethod.declaringClass)) {
                return candidateMethod
            } else if (previousMethod != null) {
                sameSig = sameSig &&
                        Arrays.equals(candidateMethod.genericParameterTypes, previousMethod.genericParameterTypes)
            }
            previousMethod = candidateMethod
        }
        return if (sameSig) candidateMethods[0] else null
    }

    fun isBridgeMethodFor(bridgeMethod: Method, candidateMethod: Method, declaringClass: Class<*>): Boolean {
        if (isResolvedTypeMatch(candidateMethod, bridgeMethod, declaringClass)) {
            return true
        }
        val method = findGenericDeclaration(bridgeMethod)
        return method != null && isResolvedTypeMatch(method, candidateMethod, declaringClass)
    }

    private fun isResolvedTypeMatch(genericMethod: Method, candidateMethod: Method, declaringClass: Class<*>): Boolean {
        val genericParameters = genericMethod.genericParameterTypes
        if (genericParameters.size != candidateMethod.parameterCount) {
            return false
        }
        val candidateParameters = candidateMethod.parameterTypes
        for (i in candidateParameters.indices) {
            val genericParameter: ResolvableType = ResolvableType.forMethodParameter(genericMethod, i, declaringClass)
            val candidateParameter = candidateParameters[i]
            if (candidateParameter.isArray) {
                // An array type: compare the component type.
                if (candidateParameter.componentType != genericParameter.getComponentType().toClass()) {
                    return false
                }
            }
            // A non-array type: compare the type itself.
            if (ClassUtil.resolvePrimitiveIfNecessary(candidateParameter) != ClassUtil.resolvePrimitiveIfNecessary(
                    genericParameter.toClass()
                )
            ) {
                return false
            }
        }
        return true
    }

    private fun findGenericDeclaration(bridgeMethod: Method): Method? {
        // Search parent types for method that has same signature as bridge.
        var superclass = bridgeMethod.declaringClass.superclass
        while (superclass != null && Any::class.java != superclass) {
            val method = searchForMatch(superclass, bridgeMethod)
            if (method != null && !method.isBridge) {
                return method
            }
            superclass = superclass.superclass
        }
        val interfaces: Array<Class<*>> = ClassUtil.getAllInterfacesForClass(bridgeMethod.declaringClass)
        return searchInterfaces(interfaces, bridgeMethod)
    }

    private fun searchInterfaces(interfaces: Array<Class<*>>, bridgeMethod: Method): Method? {
        for (ifc in interfaces) {
            var method = searchForMatch(ifc, bridgeMethod)
            if (method != null && !method.isBridge) {
                return method
            } else {
                method = searchInterfaces(ifc.interfaces, bridgeMethod)
                if (method != null) {
                    return method
                }
            }
        }
        return null
    }

    private fun searchForMatch(type: Class<*>, bridgeMethod: Method): Method? {
        return try {
            type.getDeclaredMethod(bridgeMethod.name, *bridgeMethod.parameterTypes)
        } catch (ex: NoSuchMethodException) {
            null
        }
    }

    fun isVisibilityBridgeMethodPair(bridgeMethod: Method, bridgedMethod: Method): Boolean {
        return if (bridgeMethod === bridgedMethod) {
            true
        } else bridgeMethod.returnType == bridgedMethod.returnType && bridgeMethod.parameterCount == bridgedMethod.parameterCount &&
                Arrays.equals(bridgeMethod.parameterTypes, bridgedMethod.parameterTypes)
    }

}