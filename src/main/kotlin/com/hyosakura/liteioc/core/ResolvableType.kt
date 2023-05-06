package com.hyosakura.liteioc.core

import com.hyosakura.liteioc.core.SerializableTypeWrapper.FieldTypeProvider
import com.hyosakura.liteioc.core.SerializableTypeWrapper.MethodParameterTypeProvider
import com.hyosakura.liteioc.core.SerializableTypeWrapper.TypeProvider
import com.hyosakura.liteioc.util.ClassUtil
import com.hyosakura.liteioc.util.ObjectUtil
import org.jetbrains.annotations.Nullable
import java.io.Serializable
import java.lang.reflect.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * @author LovesAsuna
 **/
open class ResolvableType {

    companion object {

        val NONE = ResolvableType(EmptyType.INSTANCE, null, null, 0)

        private val EMPTY_TYPES_ARRAY = emptyArray<ResolvableType>()

        private val cache: MutableMap<ResolvableType, ResolvableType> = ConcurrentHashMap(256)

        fun forClassWithGenerics(clazz: Class<*>, vararg generics: ResolvableType?): ResolvableType {
            val variables = clazz.typeParameters
            require(variables.size == generics.size) { "Mismatched number of generics specified for " + clazz.toGenericString() }
            val arguments = Array(generics.size) { i->
                val generic = generics[i]
                val argument = generic?.getType()
                if (argument != null && argument !is TypeVariable<*>) argument else variables[i]
            }
            val syntheticType = SyntheticParameterizedType(clazz, arguments)
            return forType(
                syntheticType,
                TypeVariablesVariableResolver(variables, generics)
            )
        }

        fun forRawClass(clazz: Class<*>?): ResolvableType {
            return object : ResolvableType(clazz) {

                override fun getGenerics(): Array<ResolvableType> {
                    return EMPTY_TYPES_ARRAY
                }

                override fun isAssignableFrom(other: Class<*>): Boolean {
                    return clazz == null || ClassUtil.isAssignable(clazz, other)
                }

                override fun isAssignableFrom(other: ResolvableType): Boolean {
                    val otherClass = other.resolve()
                    return otherClass != null && (clazz == null || ClassUtil.isAssignable(clazz, otherClass))
                }

            }
        }

        fun forClass(clazz: Class<*>?): ResolvableType {
            return ResolvableType(clazz)
        }

        fun forInstance(instance: Any): ResolvableType {
            if (instance is ResolvableTypeProvider) {
                val type = instance.getResolvableType()
                if (type != null) {
                    return type
                }
            }
            return forClass(instance.javaClass)
        }

        fun forField(field: Field, nestingLevel: Int, implementationClass: Class<*>?): ResolvableType {
            val owner = forType(implementationClass).`as`(field.declaringClass)
            return forType(null, FieldTypeProvider(field), owner.asVariableResolver()).getNested(nestingLevel)
        }

        fun forMethodReturnType(method: Method): ResolvableType {
            return forMethodParameter(MethodParameter(method, -1))
        }

        fun forMethodReturnType(method: Method, implementationClass: Class<*>): ResolvableType {
            val methodParameter = MethodParameter(method, -1, implementationClass)
            return forMethodParameter(methodParameter)
        }

        fun forMethodParameter(method: Method, parameterIndex: Int): ResolvableType {
            return forMethodParameter(MethodParameter(method, parameterIndex))
        }

        fun forMethodParameter(method: Method, parameterIndex: Int, implementationClass: Class<*>): ResolvableType {
            val methodParameter = MethodParameter(method, parameterIndex, implementationClass)
            return forMethodParameter(methodParameter)
        }

        fun forMethodParameter(methodParameter: MethodParameter): ResolvableType {
            return forMethodParameter(methodParameter, null)
        }

        fun forMethodParameter(methodParameter: MethodParameter, targetType: Type?): ResolvableType {
            return forMethodParameter(methodParameter, targetType, methodParameter.getNestingLevel())
        }

        fun forMethodParameter(
            methodParameter: MethodParameter, targetType: Type?, nestingLevel: Int
        ): ResolvableType {
            val owner = forType(methodParameter.getContainingClass()).`as`(methodParameter.declaringClass)
            return forType(
                targetType,
                MethodParameterTypeProvider(methodParameter),
                owner.asVariableResolver()
            ).getNested(nestingLevel, methodParameter.typeIndexesPerLevel)
        }

        fun forType(type: Type?): ResolvableType {
            return forType(type, null, null)
        }

        fun forType(type: Type?, owner: ResolvableType?): ResolvableType {
            var variableResolver: VariableResolver? = null
            if (owner != null) {
                variableResolver = owner.asVariableResolver()
            }
            return forType(type, variableResolver)
        }

        fun forType(type: Type?, variableResolver: VariableResolver?): ResolvableType {
            return forType(type, null, variableResolver)
        }

        fun forType(
            type: Type?,
            typeProvider: TypeProvider?,
            variableResolver: VariableResolver?
        ): ResolvableType {
            var type = type
            if (type == null && typeProvider != null) {
                type = SerializableTypeWrapper.forTypeProvider(typeProvider)
            }
            if (type == null) {
                return NONE
            }

            if (type is Class<*>) {
                return ResolvableType(type, typeProvider, variableResolver, null as ResolvableType?)
            }


            val resultType = ResolvableType(type, typeProvider, variableResolver)
            var cachedType = cache[resultType]
            if (cachedType == null) {
                cachedType = ResolvableType(type, typeProvider, variableResolver, resultType.hash)
                cache[cachedType] = cachedType
            }
            resultType.resolved = cachedType.resolved
            return resultType
        }
    }

    private var type: Type

    private var typeProvider: TypeProvider?

    private var variableResolver: VariableResolver?

    private var componentType: ResolvableType?

    private var hash: Int?

    private var resolved: Class<*>? = null

    @Volatile
    private var superType: ResolvableType? = null

    @Volatile
    private var interfaces: Array<ResolvableType>? = null

    @Volatile
    private var generics: Array<ResolvableType>? = null

    private constructor(clazz: Class<*>?) {
        this.resolved = clazz ?: Any::class.java
        this.type = resolved!!
        this.typeProvider = null
        this.variableResolver = null
        this.componentType = null
        this.hash = null
    }

    private constructor(
        type: Type, typeProvider: TypeProvider?, variableResolver: VariableResolver?
    ) {
        this.type = type
        this.typeProvider = typeProvider
        this.variableResolver = variableResolver
        this.componentType = null
        this.hash = calculateHashCode()
        this.resolved = null
    }

    private constructor(
        type: Type, typeProvider: TypeProvider?,
        variableResolver: VariableResolver?, componentType: ResolvableType?
    ) {
        this.type = type
        this.typeProvider = typeProvider
        this.variableResolver = variableResolver
        this.componentType = componentType
        this.hash = null
        this.resolved = resolveClass()
    }

    private constructor(
        type: Type, typeProvider: TypeProvider?,
        variableResolver: VariableResolver?, hash: Int?
    ) {
        this.type = type
        this.typeProvider = typeProvider
        this.variableResolver = variableResolver
        this.componentType = null
        this.hash = hash
        this.resolved = resolveClass()
    }

    fun getType(): Type {
        return SerializableTypeWrapper.unwrap(type)
    }

    fun getRawClass(): Class<*>? {
        if (type == resolved) {
            return resolved
        }
        var rawType: Type? = type
        if (rawType is ParameterizedType) {
            rawType = rawType.rawType
        }
        return if (rawType is Class<*>) rawType else null
    }

    fun resolve(): Class<*>? {
        return this.resolved
    }

    fun resolve(fallback: Class<*>): Class<*> {
        return resolved ?: fallback
    }

    private fun resolveClass(): Class<*>? {
        if (type == EmptyType.INSTANCE) {
            return null
        }
        if (type is Class<*>) {
            return type as Class<*>
        }
        if (type is GenericArrayType) {
            val resolvedComponent = getComponentType().resolve()
            return if (resolvedComponent != null) java.lang.reflect.Array.newInstance(
                resolvedComponent,
                0
            ).javaClass else null
        }
        return resolveType().resolve()
    }

    fun toClass(): Class<*> {
        return resolve(Any::class.java)
    }

    open fun asCollection(): ResolvableType {
        return `as`(MutableCollection::class.java)
    }

    open fun asMap(): ResolvableType {
        return `as`(MutableMap::class.java)
    }

    fun `as`(type: Class<*>): ResolvableType {
        if (this == NONE) {
            return NONE
        }
        val resolved = resolve()
        if (resolved == null || resolved == type) {
            return this
        }
        for (interfaceType in getInterfaces()) {
            val interfaceAsType = interfaceType.`as`(type)
            if (interfaceAsType !== NONE) {
                return interfaceAsType
            }
        }
        return getSuperType().`as`(type)
    }

    fun asVariableResolver(): VariableResolver? {
        return if (this == NONE) {
            null
        } else DefaultVariableResolver(this)
    }

    fun resolveType(): ResolvableType {
        if (type is ParameterizedType) {
            return forType((type as ParameterizedType).rawType, variableResolver)
        }
        if (type is WildcardType) {
            var resolved: Type? = resolveBounds((type as WildcardType).upperBounds)
            if (resolved == null) {
                resolved = resolveBounds((type as WildcardType).lowerBounds)
            }
            return forType(resolved, variableResolver)
        }
        if (type is TypeVariable<*>) {
            val variable = type
            // Try default variable resolution
            if (variableResolver != null) {
                val resolved = variableResolver!!.resolveVariable(variable as TypeVariable<*>)
                if (resolved != null) {
                    return resolved
                }
            }
            // Fallback to bounds
            return forType(resolveBounds((variable as TypeVariable<*>).bounds), variableResolver)
        }
        return NONE
    }

    private fun resolveBounds(bounds: Array<Type>): Type? {
        return if (bounds.isEmpty() || bounds[0] == Any::class.java) {
            null
        } else bounds[0]
    }

    private fun resolveVariable(variable: TypeVariable<*>): ResolvableType? {
        if (this.type is TypeVariable<*>) {
            return resolveType().resolveVariable(variable)
        }
        if (this.type is ParameterizedType) {
            val type = this.type as ParameterizedType
            val resolved = resolve() ?: return null
            val variables = resolved.typeParameters
            for (i in variables.indices) {
                if (ObjectUtil.nullSafeEquals(variables[i].name, variable.name)) {
                    val actualType: Type = type.actualTypeArguments[i]
                    return forType(actualType, variableResolver)
                }
            }
            val ownerType = type.ownerType
            if (ownerType != null) {
                return forType(ownerType, variableResolver).resolveVariable(variable)
            }
        }
        if (type is WildcardType) {
            return resolveType().resolveVariable(variable)
        }
        return variableResolver?.resolveVariable(variable)
    }

    fun getNested(nestingLevel: Int): ResolvableType {
        return getNested(nestingLevel, null)
    }

    fun getNested(nestingLevel: Int, typeIndexesPerLevel: Map<Int, Int>?): ResolvableType {
        var result = this
        for (i in 2..nestingLevel) {
            if (result.isArray()) {
                result = result.getComponentType()
            } else {
                // Handle derived types
                while (result !== NONE && !result.hasGenerics()) {
                    result = result.getSuperType()
                }
                var index = typeIndexesPerLevel?.get(i)
                index = index ?: (result.getGenerics().size - 1)
                result = result.getGeneric(index)
            }
        }
        return result
    }

    fun getSuperType(): ResolvableType {
        val resolved = resolve() ?: return NONE
        return try {
            val superclass = resolved.genericSuperclass ?: return NONE
            var superType = superType
            if (superType == null) {
                superType = forType(superclass, this)
                this.superType = superType
            }
            superType
        } catch (ex: TypeNotPresentException) {
            NONE
        }
    }

    fun getGeneric(vararg indexes: Int): ResolvableType {
        var generics = getGenerics()
        if (indexes.isEmpty()) {
            return if (generics.isEmpty()) NONE else generics[0]
        }
        var generic = this
        for (index in indexes) {
            generics = generic.getGenerics()
            if (index < 0 || index >= generics.size) {
                return NONE
            }
            generic = generics[index]
        }
        return generic
    }

    open fun getGenerics(): Array<ResolvableType> {
        if (this == NONE) {
            return EMPTY_TYPES_ARRAY
        }
        var generics = generics
        if (generics == null) {
            if (type is Class<*>) {
                val typeParams = (type as Class<*>).typeParameters
                generics = Array(typeParams.size) { i ->
                    forType(typeParams[i], this)
                }
            } else if (type is ParameterizedType) {
                val actualTypeArguments = (type as ParameterizedType).actualTypeArguments
                generics = Array(actualTypeArguments.size) { i ->
                    forType(actualTypeArguments[i], variableResolver)
                }
            } else {
                generics = resolveType().getGenerics()
            }
            this.generics = generics
        }
        return generics
    }

    fun hasGenerics(): Boolean {
        return getGenerics().isNotEmpty()
    }

    open fun resolveGeneric(vararg indexes: Int): Class<*>? {
        return getGeneric(*indexes).resolve()
    }

    fun isArray(): Boolean {
        return if (this == NONE) {
            false
        } else type is Class<*> && (type as Class<*>).isArray ||
                type is GenericArrayType || resolveType().isArray()
    }

    fun getComponentType(): ResolvableType {
        if (this == NONE) {
            return NONE
        }
        if (componentType != null) {
            return componentType!!
        }
        if (type is Class<*>) {
            val componentType = (type as Class<*>).componentType
            return forType(componentType, variableResolver)
        }
        return if (type is GenericArrayType) {
            forType((type as GenericArrayType).genericComponentType, variableResolver)
        } else resolveType().getComponentType()
    }

    private fun calculateHashCode(): Int {
        var hashCode: Int = ObjectUtil.nullSafeHashCode(type)
        if (typeProvider != null) {
            hashCode = 31 * hashCode + ObjectUtil.nullSafeHashCode(typeProvider!!.getType())
        }
        if (variableResolver != null) {
            hashCode = 31 * hashCode + ObjectUtil.nullSafeHashCode(variableResolver!!.getSource())
        }
        if (componentType != null) {
            hashCode = 31 * hashCode + ObjectUtil.nullSafeHashCode(componentType)
        }
        return hashCode
    }

    fun getInterfaces(): Array<ResolvableType> {
        val resolved = resolve() ?: return EMPTY_TYPES_ARRAY
        var interfaces: Array<ResolvableType>? = interfaces
        if (interfaces == null) {
            val genericIfcs = resolved.genericInterfaces
            interfaces = Array(genericIfcs.size) { i ->
                forType(genericIfcs[i], this)
            }
            this.interfaces = interfaces
        }
        return interfaces
    }

    open fun isInstance(obj: Any?): Boolean {
        return obj != null && isAssignableFrom(obj.javaClass)
    }

    open fun isAssignableFrom(other: Class<*>): Boolean {
        return isAssignableFrom(forClass(other), null)
    }

    open fun isAssignableFrom(other: ResolvableType): Boolean {
        return isAssignableFrom(other, null)
    }

    private fun isAssignableFrom(other: ResolvableType, matchedBefore: MutableMap<Type, Type>?): Boolean {
        var matchedBefore = matchedBefore

        // If we cannot resolve types, we are not assignable
        if (this == NONE || other == NONE) {
            return false
        }

        // Deal with array by delegating to the component type
        if (isArray()) {
            return other.isArray() && getComponentType().isAssignableFrom(other.getComponentType())
        }
        if (matchedBefore != null && matchedBefore[type] === other.type) {
            return true
        }

        // Deal with wildcard bounds
        val ourBounds = WildcardBounds[this]
        val typeBounds = WildcardBounds[other]

        // In the form X is assignable to <? extends Number>
        if (typeBounds != null) {
            return ourBounds != null && ourBounds.isSameKind(typeBounds) && ourBounds.isAssignableFrom(*typeBounds.bounds)
        }

        // In the form <? extends Number> is assignable to X...
        if (ourBounds != null) {
            return ourBounds.isAssignableFrom(other)
        }

        // Main assignability check about to follow
        var exactMatch = matchedBefore != null // We're checking nested generic variables now...
        var checkGenerics = true
        var ourResolved: Class<*>? = null
        if (type is TypeVariable<*>) {
            val variable = type as TypeVariable<*>
            // Try default variable resolution
            if (variableResolver != null) {
                val resolved = variableResolver!!.resolveVariable(variable)
                if (resolved != null) {
                    ourResolved = resolved.resolve()
                }
            }
            if (ourResolved == null) {
                // Try variable resolution against target type
                if (other.variableResolver != null) {
                    val resolved = other.variableResolver!!.resolveVariable(variable)
                    if (resolved != null) {
                        ourResolved = resolved.resolve()
                        checkGenerics = false
                    }
                }
            }
            if (ourResolved == null) {
                // Unresolved type variable, potentially nested -> never insist on exact match
                exactMatch = false
            }
        }
        if (ourResolved == null) {
            ourResolved = resolve(Any::class.java)
        }
        val otherResolved = other.toClass()

        // We need an exact type match for generics
        // List<CharSequence> is not assignable from List<String>
        if (if (exactMatch) ourResolved != otherResolved else !ClassUtil.isAssignable(ourResolved, otherResolved)) {
            return false
        }
        if (checkGenerics) {
            // Recursively check each generic
            val ourGenerics = getGenerics()
            val typeGenerics = other.`as`(ourResolved).getGenerics()
            if (ourGenerics.size != typeGenerics.size) {
                return false
            }
            if (matchedBefore == null) {
                matchedBefore = IdentityHashMap(1)
            }
            matchedBefore[type] = other.type
            for (i in ourGenerics.indices) {
                if (!ourGenerics[i].isAssignableFrom(typeGenerics[i], matchedBefore)) {
                    return false
                }
            }
        }
        return true
    }

    interface VariableResolver : Serializable {

        fun getSource(): Any

        fun resolveVariable(variable: TypeVariable<*>): ResolvableType?

    }

    private class DefaultVariableResolver constructor(private val source: ResolvableType) : VariableResolver {

        override fun resolveVariable(variable: TypeVariable<*>): ResolvableType? {
            return source.resolveVariable(variable)
        }

        override fun getSource(): Any {
            return source
        }

    }

    class EmptyType : Type, Serializable {

        fun readResolve(): Any {
            return INSTANCE
        }

        companion object {
            val INSTANCE: Type = EmptyType()
        }

    }

    private class TypeVariablesVariableResolver(
        private val variables: Array<out TypeVariable<*>>,
        private val generics: Array<out ResolvableType?>
    ) : VariableResolver {

        override fun resolveVariable(variable: TypeVariable<*>): ResolvableType? {
            val variableToCompare = SerializableTypeWrapper.unwrap(variable)
            for (i in variables.indices) {
                val resolvedVariable = SerializableTypeWrapper.unwrap(variables[i])
                if (ObjectUtil.nullSafeEquals(resolvedVariable, variableToCompare)) {
                    return generics[i]
                }
            }
            return null
        }

        override fun getSource(): Any {
            return generics
        }

    }

    private class SyntheticParameterizedType(
        private val rawType: Type, private val typeArguments: Array<Type>
    ) : ParameterizedType, Serializable {

        override fun getTypeName(): String {
            val typeName = rawType.typeName
            if (typeArguments.isNotEmpty()) {
                val stringJoiner = StringJoiner(", ", "<", ">")
                for (argument in typeArguments) {
                    stringJoiner.add(argument.typeName)
                }
                return typeName + stringJoiner
            }
            return typeName
        }

        override fun getOwnerType(): Type? {
            return null
        }

        override fun getRawType(): Type {
            return rawType
        }

        override fun getActualTypeArguments(): Array<Type> {
            return typeArguments
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            return if (other !is ParameterizedType) {
                false
            } else other.ownerType == null && rawType == other.rawType &&
                    Arrays.equals(typeArguments, other.actualTypeArguments)
        }

        override fun hashCode(): Int {
            return rawType.hashCode() * 31 + typeArguments.contentHashCode()
        }

        override fun toString(): String {
            return typeName
        }

    }

    private class WildcardBounds(
        private val kind: Kind,
        var bounds: Array<ResolvableType>
    ) {

        fun isSameKind(bounds: WildcardBounds): Boolean {
            return kind == bounds.kind
        }

        fun isAssignableFrom(vararg types: ResolvableType): Boolean {
            for (bound in bounds) {
                for (type in types) {
                    if (!isAssignable(bound, type)) {
                        return false
                    }
                }
            }
            return true
        }

        private fun isAssignable(source: ResolvableType?, from: ResolvableType): Boolean {
            return if (kind == Kind.UPPER) source!!.isAssignableFrom(from) else from.isAssignableFrom(
                source!!
            )
        }

        enum class Kind {
            UPPER, LOWER
        }

        companion object {

            operator fun get(type: ResolvableType): WildcardBounds? {
                var resolveToWildcard = type
                while (resolveToWildcard.getType() !is WildcardType) {
                    if (resolveToWildcard == NONE) {
                        return null
                    }
                    resolveToWildcard = resolveToWildcard.resolveType()
                }
                val wildcardType = resolveToWildcard.type as WildcardType
                val boundsType = if (wildcardType.lowerBounds.isNotEmpty()) Kind.LOWER else Kind.UPPER
                val bounds = if (boundsType == Kind.UPPER) wildcardType.upperBounds else wildcardType.lowerBounds
                val resolvableBounds = Array(bounds.size) { i ->
                    forType(bounds[i], type.variableResolver)
                }
                return WildcardBounds(boundsType, resolvableBounds)
            }

        }
    }

}