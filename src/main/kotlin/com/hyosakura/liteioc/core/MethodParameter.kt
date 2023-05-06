package com.hyosakura.liteioc.core

import com.hyosakura.liteioc.util.ClassUtil
import com.hyosakura.liteioc.util.ObjectUtil
import org.jetbrains.annotations.Nullable
import java.lang.reflect.*
import java.util.*
import java.util.function.Predicate
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.kotlinFunction

class MethodParameter {

    val executable: Executable

    val parameterIndex: Int

    @Volatile
    private var parameter: Parameter? = null

    @JvmField
    var nestingLevel: Int

    @JvmField
    var typeIndexesPerLevel: MutableMap<Int, Int>? = null

    @Volatile
    private var containingClass: Class<*>? = null

    @Volatile
    private var parameterType: Class<*>? = null

    @Volatile
    private var genericParameterType: Type? = null

    @Volatile
    private var parameterAnnotations: Array<Annotation>? = null

    @Volatile
    private var parameterNameDiscoverer: ParameterNameDiscoverer? = null

    @Volatile
    private var parameterName: String? = null

    @Volatile
    private var nestedMethodParameter: MethodParameter? = null

    constructor(method: Method, parameterIndex: Int) : this(method, parameterIndex, 1)

    constructor(method: Method, parameterIndex: Int, nestingLevel: Int = 1) {
        this.executable = method
        this.parameterIndex = validateIndex(method, parameterIndex)
        this.nestingLevel = nestingLevel
    }

    constructor(constructor: Constructor<*>, parameterIndex: Int, nestingLevel: Int = 1) {
        this.executable = constructor
        this.parameterIndex = validateIndex(constructor, parameterIndex)
        this.nestingLevel = nestingLevel
    }

    internal constructor(executable: Executable, parameterIndex: Int, containingClass: Class<*>?) {
        this.executable = executable
        this.parameterIndex = validateIndex(executable, parameterIndex)
        nestingLevel = 1
        this.containingClass = containingClass
    }

    constructor(original: MethodParameter) {
        executable = original.executable
        parameterIndex = original.parameterIndex
        parameter = original.parameter
        nestingLevel = original.nestingLevel
        typeIndexesPerLevel = original.typeIndexesPerLevel
        containingClass = original.containingClass
        parameterType = original.parameterType
        genericParameterType = original.genericParameterType
        parameterAnnotations = original.parameterAnnotations
        parameterNameDiscoverer = original.parameterNameDiscoverer
        parameterName = original.parameterName
    }

    val method: Method?
        get() = if (executable is Method) executable else null

    val constructor: Constructor<*>?
        get() = if (executable is Constructor<*>) executable else null

    val declaringClass: Class<*>
        get() = executable.declaringClass

    val member: Member
        get() = executable

    val annotatedElement: AnnotatedElement
        get() = executable

    fun getParameter(): Parameter? {
        check(parameterIndex >= 0) { "Cannot retrieve Parameter descriptor for method return type" }
        var parameter = parameter
        if (parameter == null) {
            parameter = executable.parameters[parameterIndex]
            this.parameter = parameter
        }
        return parameter
    }

    @Deprecated("since 5.2 in favor of {@link #nested(Integer)}")
    fun increaseNestingLevel() {
        nestingLevel++
    }

    @Deprecated(
        """since 5.2 in favor of retaining the original MethodParameter and
	  using {@link #nested(Integer)} if nesting is required"""
    )
    fun decreaseNestingLevel() {
        getTypeIndexesPerLevel()!!.remove(nestingLevel)
        nestingLevel--
    }

    fun getNestingLevel(): Int {
        return nestingLevel
    }

    fun withTypeIndex(typeIndex: Int): MethodParameter {
        return nested(nestingLevel, typeIndex)
    }

    @Deprecated("since 5.2 in favor of {@link #withTypeIndex}")
    fun setTypeIndexForCurrentLevel(typeIndex: Int) {
        getTypeIndexesPerLevel()!![nestingLevel] = typeIndex
    }

    @get:Nullable
    val typeIndexForCurrentLevel: Int?
        get() = getTypeIndexForLevel(nestingLevel)


    fun getTypeIndexForLevel(nestingLevel: Int): Int? {
        return getTypeIndexesPerLevel()!![nestingLevel]
    }

    private fun getTypeIndexesPerLevel(): MutableMap<Int, Int>? {
        if (typeIndexesPerLevel == null) {
            typeIndexesPerLevel = HashMap(4)
        }
        return typeIndexesPerLevel
    }


    @JvmOverloads
    fun nested(typeIndex: Int? = null): MethodParameter {
        var nestedParam = nestedMethodParameter
        if (nestedParam != null && typeIndex == null) {
            return nestedParam
        }
        nestedParam = nested(nestingLevel + 1, typeIndex)
        if (typeIndex == null) {
            nestedMethodParameter = nestedParam
        }
        return nestedParam
    }

    private fun nested(nestingLevel: Int, typeIndex: Int?): MethodParameter {
        val copy = clone()
        copy.nestingLevel = nestingLevel
        if (typeIndexesPerLevel != null) {
            copy.typeIndexesPerLevel = HashMap(typeIndexesPerLevel)
        }
        if (typeIndex != null) {
            copy.getTypeIndexesPerLevel()!![copy.nestingLevel] = typeIndex
        }
        copy.parameterType = null
        copy.genericParameterType = null
        return copy
    }

    private fun hasNullableAnnotation(): Boolean {
        for (ann in getParameterAnnotations()) {
            if ("Nullable" == ann.javaClass.simpleName) {
                return true
            }
        }
        return false
    }

    fun nestedIfOptional(): MethodParameter {
        return if (getParameterType() == Optional::class.java) nested() else this
    }

    fun withContainingClass(containingClass: Class<*>?): MethodParameter {
        val result = clone()
        result.containingClass = containingClass
        result.parameterType = null
        return result
    }

    @Deprecated("")
    fun setContainingClass(containingClass: Class<*>?) {
        this.containingClass = containingClass
        parameterType = null
    }

    fun getContainingClass(): Class<*> {
        val containingClass = containingClass
        return containingClass ?: declaringClass
    }

    @Deprecated("")
    fun setParameterType(parameterType: Class<*>?) {
        this.parameterType = parameterType
    }

    fun getParameterType(): Class<*> {
        var paramType = parameterType
        if (paramType != null) {
            return paramType
        }
        if (getContainingClass() != declaringClass) {
            paramType = ResolvableType.forMethodParameter(this, null, 1).resolve()
        }
        if (paramType == null) {
            paramType = computeParameterType()
        }
        parameterType = paramType
        return paramType
    }

    fun getGenericParameterType(): Type {
        var paramType = genericParameterType
        if (paramType == null) {
            if (parameterIndex < 0) {
                val method = method
                paramType =
                    if (method != null) (if (KotlinDetector.isKotlinReflectPresent && KotlinDetector.isKotlinType(
                            getContainingClass()
                        )
                    ) KotlinDelegate.getGenericReturnType(method) else method.genericReturnType) else Void.TYPE
            } else {
                val genericParameterTypes = executable.genericParameterTypes
                var index = parameterIndex
                if (executable is Constructor<*> && ClassUtil.isInnerClass(executable.getDeclaringClass()) && genericParameterTypes.size == executable.getParameterCount() - 1) {
                    // Bug in javac: type array excludes enclosing instance parameter
                    // for inner classes with at least one generic constructor parameter,
                    // so access it with the actual parameter index lowered by 1
                    index = parameterIndex - 1
                }
                paramType =
                    if (index >= 0 && index < genericParameterTypes.size) genericParameterTypes[index] else computeParameterType()
            }
            genericParameterType = paramType
        }
        return paramType!!
    }

    private fun computeParameterType(): Class<*> {
        if (parameterIndex < 0) {
            val method = method ?: return Void.TYPE
            return if (KotlinDetector.isKotlinReflectPresent && KotlinDetector.isKotlinType(getContainingClass())) {
                KotlinDelegate.getReturnType(method)
            } else method.returnType
        }
        return executable.parameterTypes[parameterIndex]
    }

    fun getNestedParameterType(): Class<*> {
        return if (nestingLevel > 1) {
            var type = getGenericParameterType()
            for (i in 2..nestingLevel) {
                if (type is ParameterizedType) {
                    val args = type.actualTypeArguments
                    val index = getTypeIndexForLevel(i)
                    type = args[index ?: (args.size - 1)]
                }
                // TODO: Object.class if unresolvable
            }
            if (type is Class<*>) {
                return type
            } else if (type is ParameterizedType) {
                val arg = type.rawType
                if (arg is Class<*>) {
                    return arg
                }
            }
            Any::class.java
        } else {
            getParameterType()
        }
    }

    fun getNestedGenericParameterType(): Type {
        return if (nestingLevel > 1) {
            var type = getGenericParameterType()
            for (i in 2..nestingLevel) {
                if (type is ParameterizedType) {
                    val args = type.actualTypeArguments
                    val index = getTypeIndexForLevel(i)
                    type = args[index ?: (args.size - 1)]
                }
            }
            type
        } else {
            getGenericParameterType()
        }
    }

    fun getMethodAnnotations(): Array<Annotation> {
        return adaptAnnotationArray(this.executable.annotations)
    }

    fun <A : Annotation> getMethodAnnotation(annotationType: Class<A>): A? {
        val annotation = annotatedElement.getAnnotation(annotationType)
        return adaptAnnotation(annotation)
    }

    fun <A : Annotation> hasMethodAnnotation(annotationType: Class<A>?): Boolean {
        return annotatedElement.isAnnotationPresent(annotationType)
    }

    fun getParameterAnnotations(): Array<Annotation> {
        var paramAnns = parameterAnnotations
        if (paramAnns == null) {
            val annotationArray = executable.parameterAnnotations
            var index = parameterIndex
            if (executable is Constructor<*> && ClassUtil.isInnerClass(executable.getDeclaringClass()) && annotationArray.size == executable.getParameterCount() - 1) {
                // Bug in javac in JDK <9: annotation array excludes enclosing instance parameter
                // for inner classes, so access it with the actual parameter index lowered by 1
                index = parameterIndex - 1
            }
            paramAnns =
                if (index >= 0 && index < annotationArray.size) adaptAnnotationArray(annotationArray[index]) else EMPTY_ANNOTATION_ARRAY
            parameterAnnotations = paramAnns
        }
        return paramAnns
    }

    fun hasParameterAnnotations(): Boolean {
        return getParameterAnnotations().isNotEmpty()
    }

    @Suppress("UNCHECKED_CAST")
    fun <A : Annotation> getParameterAnnotation(annotationType: Class<A>): A? {
        val anns = getParameterAnnotations()
        for (ann in anns) {
            if (annotationType.isInstance(ann)) {
                return ann as A
            }
        }
        return null
    }

    fun <A : Annotation> hasParameterAnnotation(annotationType: Class<A>): Boolean {
        return getParameterAnnotation(annotationType) != null
    }

    fun initParameterNameDiscovery(parameterNameDiscoverer: ParameterNameDiscoverer?) {
        this.parameterNameDiscoverer = parameterNameDiscoverer
    }

    fun getParameterName(): String? {
        if (parameterIndex < 0) {
            return null
        }
        val discoverer: ParameterNameDiscoverer? = parameterNameDiscoverer
        if (discoverer != null) {
            var parameterNames: Array<String>? = null
            if (executable is Method) {
                parameterNames = discoverer.getParameterNames(executable)
            } else if (executable is Constructor<*>) {
                parameterNames = discoverer.getParameterNames(executable)
            }
            if (parameterNames != null) {
                parameterName = parameterNames[parameterIndex]
            }
            parameterNameDiscoverer = null
        }
        return parameterName
    }

    fun <A : Annotation?> adaptAnnotation(annotation: A): A {
        return annotation
    }

    fun adaptAnnotationArray(annotations: Array<Annotation>): Array<Annotation> {
        return annotations
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return if (other !is MethodParameter) {
            false
        } else getContainingClass() == other.getContainingClass() && ObjectUtil.nullSafeEquals(
            typeIndexesPerLevel, other.typeIndexesPerLevel
        ) && nestingLevel == other.nestingLevel && parameterIndex == other.parameterIndex && executable == other.executable
    }

    override fun hashCode(): Int {
        return 31 * executable.hashCode() + parameterIndex
    }

    override fun toString(): String {
        val method = method
        return (if (method != null) "method '" + method.name + "'" else "constructor") + " parameter " + parameterIndex
    }

    fun clone(): MethodParameter {
        return MethodParameter(this)
    }

    private object KotlinDelegate {

        fun isOptional(param: MethodParameter): Boolean {
            val method = param.method
            val index = param.parameterIndex
            if (method != null && index == -1) {
                val function = method.kotlinFunction
                return function != null && function.returnType.isMarkedNullable
            }
            val function: KFunction<*>?
            val predicate: Predicate<KParameter>
            if (method != null) {
                if (param.getParameterType().name == "kotlin.coroutines.Continuation") {
                    return true
                }
                function = method.kotlinFunction
                predicate = Predicate { p: KParameter -> KParameter.Kind.VALUE == p.kind }
            } else {
                val ctor = param.constructor
                requireNotNull(ctor) { "Neither method nor constructor found" }
                function = ctor.kotlinFunction
                predicate =
                    Predicate { p: KParameter -> KParameter.Kind.VALUE == p.kind || KParameter.Kind.INSTANCE == p.kind }
            }
            if (function != null) {
                var i = 0
                for (kParameter in function.parameters) {
                    if (predicate.test(kParameter)) {
                        if (index == i++) {
                            return kParameter.type.isMarkedNullable || kParameter.isOptional
                        }
                    }
                }
            }
            return false
        }

        fun getGenericReturnType(method: Method): Type {
            try {
                val function = method.kotlinFunction
                if (function != null && function.isSuspend) {
                    return function.returnType.javaType
                }
            } catch (ex: UnsupportedOperationException) {
                // probably a synthetic class - let's use java reflection instead
            }
            return method.genericReturnType
        }

        fun getReturnType(method: Method): Class<*> {
            try {
                val function = method.kotlinFunction
                if (function != null && function.isSuspend) {
                    var paramType: Type = function.returnType.javaType
                    if (paramType === Unit::class.java) {
                        paramType = Void.TYPE
                    }
                    return ResolvableType.forType(paramType).resolve(method.returnType)
                }
            } catch (ex: UnsupportedOperationException) {
                // probably a synthetic class - let's use java reflection instead
            }
            return method.returnType
        }
    }

    fun isOptional(): Boolean {
        return getParameterType() == Optional::class.java || hasNullableAnnotation() || KotlinDetector.isKotlinReflectPresent &&
                KotlinDetector.isKotlinType(getContainingClass()) && KotlinDelegate.isOptional(
            this
        )
    }

    companion object {

        private val EMPTY_ANNOTATION_ARRAY = emptyArray<Annotation>()

        @Deprecated("as of 5.0, in favor of {@link #forExecutable}")
        fun forMethodOrConstructor(methodOrConstructor: Any, parameterIndex: Int): MethodParameter {
            require(methodOrConstructor is Executable) { "Given object [$methodOrConstructor] is neither a Method nor a Constructor" }
            return forExecutable(methodOrConstructor, parameterIndex)
        }

        fun forExecutable(executable: Executable, parameterIndex: Int): MethodParameter {
            return if (executable is Method) {
                MethodParameter(executable, parameterIndex)
            } else if (executable is Constructor<*>) {
                MethodParameter(executable, parameterIndex)
            } else {
                throw IllegalArgumentException("Not a Method/Constructor: $executable")
            }
        }

        fun forParameter(parameter: Parameter): MethodParameter {
            return forExecutable(parameter.declaringExecutable, findParameterIndex(parameter))
        }

        protected fun findParameterIndex(parameter: Parameter): Int {
            val executable = parameter.declaringExecutable
            val allParams = executable.parameters
            // Try first with identity checks for greater performance.
            for (i in allParams.indices) {
                if (parameter === allParams[i]) {
                    return i
                }
            }
            // Potentially try again with object equality checks in order to avoid race
            // conditions while invoking java.lang.reflect.Executable.getParameters().
            for (i in allParams.indices) {
                if (parameter == allParams[i]) {
                    return i
                }
            }
            throw IllegalArgumentException(
                "Given parameter [" + parameter + "] does not match any parameter in the declaring executable"
            )
        }

        private fun validateIndex(executable: Executable, parameterIndex: Int): Int {
            val count = executable.parameterCount
            require(
                parameterIndex >= -1 && parameterIndex < count
            ) { "Parameter index needs to be between -1 and " + (count - 1) }
            return parameterIndex
        }
    }
}