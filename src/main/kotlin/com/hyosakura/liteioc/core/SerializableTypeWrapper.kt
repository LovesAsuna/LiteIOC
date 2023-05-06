package com.hyosakura.liteioc.core

import com.hyosakura.liteioc.util.ObjectUtil
import com.hyosakura.liteioc.util.ReflectionUtil
import java.io.IOException
import java.io.ObjectInputStream
import java.io.Serializable
import java.lang.reflect.*
import java.util.concurrent.ConcurrentHashMap

/**
 * @author LovesAsuna
 **/
object SerializableTypeWrapper {

    private val SUPPORTED_SERIALIZABLE_TYPES = arrayOf<Class<*>>(
        GenericArrayType::class.java, ParameterizedType::class.java, TypeVariable::class.java, WildcardType::class.java
    )

    val cache: MutableMap<Type, Type> = ConcurrentHashMap(256)

    fun forTypeProvider(provider: TypeProvider): Type? {
        val providedType = provider.getType()
        if (providedType == null || providedType is Serializable) {
            // No serializable type wrapping necessary (e.g. for java.lang.Class)
            return providedType
        }
        // Obtain a serializable type proxy for the given provider...
        var cached = cache[providedType]
        if (cached != null) {
            return cached
        }
        for (type in SUPPORTED_SERIALIZABLE_TYPES) {
            if (type.isInstance(providedType)) {
                val classLoader = provider.javaClass.classLoader
                val interfaces = arrayOf<Class<*>>(
                    type,
                    SerializableTypeProxy::class.java,
                    Serializable::class.java
                )
                val handler = TypeProxyInvocationHandler(provider)
                cached = Proxy.newProxyInstance(classLoader, interfaces, handler) as Type
                cache[providedType] = cached
                return cached
            }
        }
        throw IllegalArgumentException("Unsupported Type class: " + providedType.javaClass.name)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Type> unwrap(type: T): T {
        var unwrapped: Type? = null
        if (type is SerializableTypeProxy) {
            unwrapped = (type as SerializableTypeProxy).getTypeProvider().getType()
        }
        return if (unwrapped != null) unwrapped as T else type
    }

    internal interface SerializableTypeProxy {

        fun getTypeProvider(): TypeProvider

    }

    interface TypeProvider : Serializable {

        fun getType(): Type?


        fun getSource(): Any? {
            return null
        }

    }

    private class TypeProxyInvocationHandler(private val provider: TypeProvider) : InvocationHandler, Serializable {

        @Throws(Throwable::class)
        override fun invoke(proxy: Any, method: Method, args: Array<Any>): Any? {
            when (method.name) {
                "equals" -> {
                    var other = args[0]
                    // Unwrap proxies for speed
                    if (other is Type) {
                        other = unwrap(other)
                    }
                    return ObjectUtil.nullSafeEquals(provider.getType(), other)
                }

                "hashCode" -> return ObjectUtil.nullSafeHashCode(provider.getType())
                "getTypeProvider" -> return provider
            }
            if (Type::class.java == method.returnType && args.isEmpty()) {
                return forTypeProvider(
                    MethodInvokeTypeProvider(
                        provider, method, -1
                    )
                )!!
            } else if (Array<Type>::class.java == method.returnType && args.isEmpty()) {
                val result = arrayOfNulls<Type>((method.invoke(provider.getType()) as Array<Type?>).size)
                for (i in result.indices) {
                    result[i] = forTypeProvider(
                        MethodInvokeTypeProvider(
                            provider, method, i
                        )
                    )
                }
                return result
            }
            return try {
                method.invoke(provider.getType(), *args)
            } catch (ex: InvocationTargetException) {
                throw ex.targetException
            }
        }
    }

    class MethodInvokeTypeProvider(
        private val provider: TypeProvider,
        @field:Transient private var method: Method,
        private val index: Int
    ) : TypeProvider {

        private val methodName: String

        private val declaringClass: Class<*>

        @Volatile
        @Transient
        private var result: Any? = null

        init {
            methodName = method.name
            declaringClass = method.declaringClass
        }

        override fun getType(): Type? {
            var result = result
            if (result == null) {
                // Lazy invocation of the target method on the provided type
                result = ReflectionUtil.invokeMethod(method, provider.getType())
                // Cache the result for further calls to getType()
                this.result = result
            }
            return if (result is Array<*> && result.isArrayOf<Type>()) (result as Array<Type>)[index] else result as Type?
        }

        override fun getSource(): Any? {
            return null
        }

        @Throws(IOException::class, ClassNotFoundException::class)
        private fun readObject(inputStream: ObjectInputStream) {
            inputStream.defaultReadObject()
            val method: Method = ReflectionUtil.findMethod(declaringClass, methodName)
                ?: throw IllegalStateException("Cannot find method on deserialization: $methodName")
            check(!(method.returnType != Type::class.java && method.returnType != Array<Type>::class.java)) { "Invalid return type on deserialized method - needs to be Type or Type[]: $method" }
            this.method = method
        }
    }

    class FieldTypeProvider(@field:Transient private var field: Field) : TypeProvider {

        private val fieldName: String = field.name

        private val declaringClass: Class<*> = field.declaringClass

        override fun getType(): Type {
            return field.genericType
        }

        override fun getSource(): Any {
            return field
        }

        @Throws(IOException::class, ClassNotFoundException::class)
        private fun readObject(inputStream: ObjectInputStream) {
            inputStream.defaultReadObject()
            try {
                field = declaringClass.getDeclaredField(fieldName)
            } catch (ex: Throwable) {
                throw IllegalStateException("Could not find original class structure", ex)
            }
        }
    }

    class MethodParameterTypeProvider(@field:Transient private var methodParameter: MethodParameter) : TypeProvider {

        private val methodName: String? = if (methodParameter.method != null) methodParameter.method!!.name else null
        private val parameterTypes: Array<Class<*>> = methodParameter.executable.parameterTypes
        private val declaringClass: Class<*> = methodParameter.declaringClass
        private val parameterIndex: Int = methodParameter.parameterIndex

        override fun getType(): Type {
            return methodParameter.getGenericParameterType()
        }

        override fun getSource(): Any {
            return methodParameter
        }

        @Throws(IOException::class, ClassNotFoundException::class)
        private fun readObject(inputStream: ObjectInputStream) {
            inputStream.defaultReadObject()
            methodParameter = try {
                if (methodName != null) {
                    MethodParameter(
                        declaringClass.getDeclaredMethod(methodName, *parameterTypes), parameterIndex
                    )
                } else {
                    MethodParameter(
                        declaringClass.getDeclaredConstructor(*parameterTypes), parameterIndex
                    )
                }
            } catch (ex: Throwable) {
                throw IllegalStateException("Could not find original class structure", ex)
            }
        }
    }

}