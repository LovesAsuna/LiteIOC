package com.hyosakura.liteioc.util

import com.hyosakura.liteioc.util.ReflectionUtil.FieldFilter
import com.hyosakura.liteioc.util.ReflectionUtil.MethodFilter
import java.lang.reflect.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object ReflectionUtil {

    private val declaredMethodsCache: MutableMap<Class<*>, Array<Method>> = ConcurrentHashMap(256)

    private val declaredFieldsCache: MutableMap<Class<*>, Array<Field>> = ConcurrentHashMap(256)

    val USER_DECLARED_METHODS: MethodFilter =
        MethodFilter { method: Method -> !method.isBridge && !method.isSynthetic && method.declaringClass != Any::class.java }

    private val EMPTY_CLASS_ARRAY: Array<Class<*>> = emptyArray()

    private val EMPTY_FIELD_ARRAY = emptyArray<Field>()

    @Throws(NoSuchMethodException::class)
    fun <T> accessibleConstructor(clazz: Class<T>, vararg parameterTypes: Class<*>?): Constructor<T> {
        val ctor = clazz.getDeclaredConstructor(*parameterTypes)
        makeAccessible(ctor)
        return ctor
    }

    fun makeAccessible(ctor: Constructor<*>) {
        if ((!Modifier.isPublic(ctor.modifiers) || !Modifier.isPublic(ctor.declaringClass.modifiers)) && !ctor.isAccessible) {
            ctor.isAccessible = true
        }
    }

    fun makeAccessible(method: Method) {
        if ((!Modifier.isPublic(method.modifiers) || !Modifier.isPublic(method.declaringClass.modifiers)) && !method.isAccessible) {
            method.isAccessible = true
        }
    }

    fun makeAccessible(field: Field) {
        if ((!Modifier.isPublic(field.modifiers) || !Modifier.isPublic(field.declaringClass.modifiers) || Modifier.isFinal(
                field.modifiers
            )) && !field.isAccessible
        ) {
            field.isAccessible = true
        }
    }

    fun invokeMethod(method: Method, target: Any?): Any? {
        return invokeMethod(
            method, target, *emptyArray()
        )
    }

    fun invokeMethod(method: Method, target: Any?, vararg args: Any): Any? {
        try {
            return method.invoke(target, *args)
        } catch (ex: Exception) {
            handleReflectionException(ex)
        }
        throw IllegalStateException("Should never get here")
    }

    // Exception handling
    fun handleReflectionException(ex: java.lang.Exception) {
        check(ex !is NoSuchMethodException) { "Method not found: " + ex.message }
        check(ex !is IllegalAccessException) { "Could not access method or field: " + ex.message }
        if (ex is InvocationTargetException) {
            handleInvocationTargetException(ex)
        }
        if (ex is RuntimeException) {
            throw ex
        }
        throw UndeclaredThrowableException(ex)
    }

    fun handleInvocationTargetException(ex: InvocationTargetException) {
        rethrowRuntimeException(ex.targetException)
    }

    fun rethrowRuntimeException(ex: Throwable) {
        if (ex is RuntimeException) {
            throw ex
        }
        if (ex is Error) {
            throw ex
        }
        throw UndeclaredThrowableException(ex)
    }

    fun getUniqueDeclaredMethods(
        leafClass: Class<*>, mf: MethodFilter?
    ): Array<Method> {
        val methods = ArrayList<Method>(20)
        doWithMethods(leafClass, { method: Method ->
            var knownSignature = false
            var methodBeingOverriddenWithCovariantReturnType: Method? = null
            for (existingMethod in methods) {
                if (method.name == existingMethod.name && method.parameterCount == existingMethod.parameterCount && Arrays.equals(
                        method.parameterTypes,
                        existingMethod.parameterTypes
                    )
                ) {
                    // Is this a covariant return type situation?
                    if (existingMethod.returnType != method.returnType && existingMethod.returnType.isAssignableFrom(
                            method.returnType
                        )
                    ) {
                        methodBeingOverriddenWithCovariantReturnType = existingMethod
                    } else {
                        knownSignature = true
                    }
                    break
                }
            }
            if (methodBeingOverriddenWithCovariantReturnType != null) {
                methods.remove(methodBeingOverriddenWithCovariantReturnType)
            }
            if (!knownSignature) {
                methods.add(method)
            }
        }, mf)
        return methods.toTypedArray()
    }

    fun getAllDeclaredMethods(leafClass: Class<*>): Array<Method> {
        val methods = ArrayList<Method>(20)
        doWithMethods(leafClass) {
            methods.add(it)
        }
        return methods.toTypedArray()
    }

    fun getDeclaredMethods(clazz: Class<*>): Array<Method> {
        return getDeclaredMethods(clazz, true)
    }

    private fun getDeclaredMethods(clazz: Class<*>, defensive: Boolean): Array<Method> {
        var result = declaredMethodsCache[clazz]
        if (result == null) {
            try {
                val declaredMethods = clazz.declaredMethods
                val defaultMethods = findConcreteMethodsOnInterfaces(clazz)
                if (defaultMethods != null) {
                    val temp = arrayOfNulls<Method>(declaredMethods.size + defaultMethods.size)
                    System.arraycopy(declaredMethods, 0, temp, 0, declaredMethods.size)
                    @Suppress("UNCHECKED_CAST") result = temp as Array<Method>
                    var index = declaredMethods.size
                    for (defaultMethod in defaultMethods) {
                        result[index] = defaultMethod
                        index++
                    }
                } else {
                    result = declaredMethods
                }
                declaredMethodsCache[clazz] = if (result!!.isEmpty()) emptyArray() else result
            } catch (ex: Throwable) {
                throw IllegalStateException(
                    "Failed to introspect Class [" + clazz.name + "] from ClassLoader [" + clazz.classLoader + "]", ex
                )
            }
        }
        return if ((result.isEmpty() || !defensive)) result else result.clone()
    }

    fun findField(clazz: Class<*>, name: String): Field? {
        return findField(clazz, name, null)
    }

    fun findField(clazz: Class<*>, name: String?, type: Class<*>?): Field? {
        require(name != null || type != null) { "Either name or type of the field must be specified" }
        var searchType: Class<*> = clazz
        while (Any::class.java != searchType && searchType != null) {
            val fields = getDeclaredFields(searchType)
            for (field in fields) {
                if ((name == null || name == field.name) && (type == null || type == field.type)) {
                    return field
                }
            }
            searchType = searchType.superclass
        }
        return null
    }

    private fun findConcreteMethodsOnInterfaces(clazz: Class<*>): List<Method>? {
        var result: MutableList<Method>? = null
        for (ifc in clazz.interfaces) {
            for (ifcMethod in ifc.methods) {
                if (!Modifier.isAbstract(ifcMethod.modifiers)) {
                    if (result == null) {
                        result = ArrayList()
                    }
                    result.add(ifcMethod)
                }
            }
        }
        return result
    }

    fun doWithLocalMethods(clazz: Class<*>, mc: MethodCallback) {
        val methods = getDeclaredMethods(clazz, false)
        for (method in methods) {
            try {
                mc.doWith(method)
            } catch (ex: IllegalAccessException) {
                throw IllegalStateException("Not allowed to access method '" + method.name + "': " + ex)
            }
        }
    }

    fun doWithMethods(clazz: Class<*>, mc: MethodCallback) {
        doWithMethods(clazz, mc, null)
    }

    fun doWithMethods(
        clazz: Class<*>, mc: MethodCallback, mf: MethodFilter?
    ) {
        if (mf == USER_DECLARED_METHODS && clazz == Any::class.java) {
            // nothing to introspect
            return
        }
        val methods = getDeclaredMethods(clazz, false)
        for (method in methods) {
            if (mf != null && !mf.matches(method)) {
                continue
            }
            try {
                mc.doWith(method)
            } catch (ex: IllegalAccessException) {
                throw IllegalStateException("Not allowed to access method '" + method.name + "': " + ex)
            }
        }
        // Keep backing up the inheritance hierarchy.
        if (clazz.superclass != null && (mf != USER_DECLARED_METHODS || clazz.superclass != Any::class.java)) {
            doWithMethods(clazz.superclass, mc, mf)
        } else if (clazz.isInterface) {
            for (superIfc in clazz.interfaces) {
                doWithMethods(superIfc, mc, mf)
            }
        }
    }

    fun doWithLocalFields(clazz: Class<*>, fc: FieldCallback) {
        for (field in getDeclaredFields(clazz)) {
            try {
                fc.doWith(field)
            } catch (ex: IllegalAccessException) {
                throw IllegalStateException("Not allowed to access field '" + field.name + "': " + ex)
            }
        }
    }

    private fun getDeclaredFields(clazz: Class<*>): Array<Field> {
        var result = declaredFieldsCache[clazz]
        if (result == null) {
            try {
                result = clazz.declaredFields
                declaredFieldsCache[clazz] = if (result.isEmpty()) EMPTY_FIELD_ARRAY else result
            } catch (ex: Throwable) {
                throw IllegalStateException(
                    "Failed to introspect Class [" + clazz.name + "] from ClassLoader [" + clazz.classLoader + "]", ex
                )
            }
        }
        return result!!
    }

    fun findMethod(clazz: Class<*>, name: String): Method? {
        return findMethod(clazz, name, *EMPTY_CLASS_ARRAY)
    }

    fun findMethod(clazz: Class<*>, name: String, vararg paramTypes: Class<*>): Method? {
        var searchType: Class<*>? = clazz
        while (searchType != null) {
            val methods = if (searchType.isInterface) searchType.methods else getDeclaredMethods(
                searchType, false
            )
            for (method in methods) {
                if (name == method.name && (hasSameParams(
                        method, paramTypes
                    ))
                ) {
                    return method
                }
            }
            searchType = searchType.superclass
        }
        return null
    }

    private fun hasSameParams(method: Method, paramTypes: Array<out Class<*>>): Boolean {
        return paramTypes.size == method.parameterCount && Arrays.equals(paramTypes, method.parameterTypes)
    }

    fun interface MethodCallback {

        @Throws(IllegalArgumentException::class, IllegalAccessException::class)
        fun doWith(method: Method)

    }

    fun interface MethodFilter {

        fun matches(method: Method): Boolean

        fun and(next: MethodFilter): MethodFilter {
            return MethodFilter { method -> matches(method) && next.matches(method) }
        }

    }

    fun interface FieldCallback {

        fun doWith(field: Field)

    }

    fun interface FieldFilter {

        fun matches(field: Field): Boolean

        fun and(next: FieldFilter): FieldFilter {
            return FieldFilter { field -> matches(field) && next.matches(field) }
        }

    }

}