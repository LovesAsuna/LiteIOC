package com.hyosakura.liteioc.bean.factory.support

import com.hyosakura.liteioc.bean.BeanMetadataElement
import com.hyosakura.liteioc.bean.factory.ObjectFactory
import com.hyosakura.liteioc.bean.factory.config.TypedStringValue
import com.hyosakura.liteioc.util.ClassUtil
import java.beans.PropertyDescriptor
import java.io.Serializable
import java.lang.reflect.*
import java.util.*

/**
 * @author LovesAsuna
 **/
object AutowireUtil {

    val EXECUTABLE_COMPARATOR = Comparator { e1: Executable, e2: Executable ->
        val result = java.lang.Boolean.compare(
            Modifier.isPublic(e2.modifiers), Modifier.isPublic(e1.modifiers)
        )
        if (result != 0) result else e2.parameterCount.compareTo(e1.parameterCount)
    }

    fun isExcludedFromDependencyCheck(pd: PropertyDescriptor): Boolean {
        val wm = pd.writeMethod ?: return false
        if (!wm.declaringClass.name.contains("$$")) {
            // Not a CGLIB method so it's OK.
            return false
        }
        // It was declared by CGLIB, but we might still want to autowire it
        // if it was actually declared by the superclass.
        val superclass = wm.declaringClass.superclass
        return !ClassUtil.hasMethod(superclass, wm)
    }

    fun isSetterDefinedInInterface(pd: PropertyDescriptor, interfaces: Set<Class<*>>): Boolean {
        val setter = pd.writeMethod
        if (setter != null) {
            val targetClass = setter.declaringClass
            for (ifc in interfaces) {
                if (ifc.isAssignableFrom(targetClass) && ClassUtil.hasMethod(ifc, setter)) {
                    return true
                }
            }
        }
        return false
    }

    fun sortConstructors(constructors: Array<Constructor<*>>) {
        Arrays.sort(constructors, EXECUTABLE_COMPARATOR)
    }

    fun resolveAutowiringValue(autowiringValue: Any, requiredType: Class<*>): Any {
        var autowiringValue = autowiringValue
        if (autowiringValue is ObjectFactory<*> && !requiredType.isInstance(autowiringValue)) {
            val factory: ObjectFactory<*> = autowiringValue
            autowiringValue = if (autowiringValue is Serializable && requiredType.isInterface) {
                Proxy.newProxyInstance(
                    requiredType.classLoader, arrayOf(requiredType), ObjectFactoryDelegatingInvocationHandler(factory)
                )
            } else {
                return factory.getObject()!!
            }
        }
        return autowiringValue
    }

    fun resolveReturnTypeForFactoryMethod(
        method: Method, args: Array<Any?>, classLoader: ClassLoader?
    ): Class<*> {
        val declaredTypeVariables = method.typeParameters
        val genericReturnType = method.genericReturnType
        val methodParameterTypes = method.genericParameterTypes
        require(args.size == methodParameterTypes.size) { "Argument array does not match parameter count" }

        // Ensure that the type variable (e.g., T) is declared directly on the method
        // itself (e.g., via <T>), not on the enclosing class or interface.
        var locallyDeclaredTypeVariableMatchesReturnType = false
        for (currentTypeVariable in declaredTypeVariables) {
            if (currentTypeVariable == genericReturnType) {
                locallyDeclaredTypeVariableMatchesReturnType = true
                break
            }
        }
        if (locallyDeclaredTypeVariableMatchesReturnType) {
            for (i in methodParameterTypes.indices) {
                val methodParameterType = methodParameterTypes[i]
                val arg = args[i]
                if (methodParameterType == genericReturnType) {
                    if (arg is TypedStringValue) {
                        if (arg.hasTargetType()) {
                            return arg.getTargetType()
                        }
                        try {
                            val resolvedType = arg.resolveTargetType(classLoader)
                            if (resolvedType != null) {
                                return resolvedType
                            }
                        } catch (ex: ClassNotFoundException) {
                            throw IllegalStateException(
                                ("Failed to resolve value type [" +
                                        arg.getTargetTypeName()) + "] for factory method argument", ex
                            )
                        }
                    } else if (arg != null && arg !is BeanMetadataElement) {
                        // Only consider argument type if it is a simple value...
                        return arg.javaClass
                    }
                    return method.returnType
                } else if (methodParameterType is ParameterizedType) {
                    val actualTypeArguments = methodParameterType.actualTypeArguments
                    for (typeArg in actualTypeArguments) {
                        if ((typeArg == genericReturnType)) {
                            if (arg is Class<*>) {
                                return arg
                            } else {
                                var className: String? = null
                                if (arg is String) {
                                    className = arg
                                } else if (arg is TypedStringValue) {
                                    val targetTypeName = arg.getTargetTypeName()
                                    if (targetTypeName == null || (Class::class.java.name == targetTypeName)) {
                                        className = arg.getValue()
                                    }
                                }
                                if (className != null) {
                                    try {
                                        return ClassUtil.forName(className, classLoader)
                                    } catch (ex: ClassNotFoundException) {
                                        throw IllegalStateException(
                                            ("Could not resolve class name [" + arg +
                                                    "] for factory method argument"), ex
                                        )
                                    }
                                }
                                // Consider adding logic to determine the class of the typeArg, if possible.
                                // For now, just fall back...
                                return method.returnType
                            }
                        }
                    }
                }
            }
        }

        // Fall back...
        return method.returnType
    }

    private class ObjectFactoryDelegatingInvocationHandler(private val objectFactory: ObjectFactory<*>) :
        InvocationHandler, Serializable {

        @Throws(Throwable::class)
        override fun invoke(proxy: Any, method: Method, args: Array<Any>): Any {
            when (method.name) {
                "equals" ->
                    return proxy === args[0]

                "hashCode" ->
                    return System.identityHashCode(proxy)

                "toString" -> return objectFactory.toString()
            }
            return try {
                method.invoke(objectFactory.getObject(), args)
            } catch (ex: InvocationTargetException) {
                throw ex.targetException
            }
        }

    }
}