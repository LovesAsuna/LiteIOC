package com.hyosakura.liteioc.core.annotation

/**
 * @author LovesAsuna
 **/
class AnnotationAttributes : LinkedHashMap<String, Any> {

    companion object {

        private const val UNKNOWN = "unknown"
        fun fromMap(map: Map<String, Any>?): AnnotationAttributes? {
            if (map == null) {
                return null
            }
            return if (map is AnnotationAttributes) {
                map
            } else AnnotationAttributes(map)
        }

    }

    private val annotationType: Class<out Annotation>?

    private val displayName: String?

    constructor() {
        this.annotationType = null
        this.displayName = UNKNOWN
    }

    constructor(map: Map<String, Any>) : super(map) {
        this.annotationType = null
        this.displayName = UNKNOWN
    }

    constructor(annotationType: Class<out Annotation>) {
        this.annotationType = annotationType
        this.displayName = annotationType.name
    }

    fun getBoolean(attributeName: String): Boolean {
        return getRequiredAttribute(attributeName, Boolean::class.javaObjectType)
    }

    fun getStringArray(attributeName: String): Array<String> {
        return getRequiredAttribute(attributeName, emptyArray<String>()::class.java)
    }

    fun getString(attributeName: String): String {
        return getRequiredAttribute(attributeName, String::class.java)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getClass(attributeName: String): Class<out T> {
        return getRequiredAttribute(attributeName, Class::class.java) as Class<out T>
    }

    fun getClassArray(attributeName: String): Array<Class<*>> {
        return getRequiredAttribute(attributeName, emptyArray<Class<*>>()::class.java)
    }

    @Suppress("UNCHECKED_CAST")
    fun <E : Enum<*>> getEnum(attributeName: String): E {
        return getRequiredAttribute(attributeName, Enum::class.java) as E
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getRequiredAttribute(attributeName: String, expectedType: Class<T>): T {
        var value = get(attributeName)!!
        if (!expectedType.isInstance(value) && expectedType.isArray &&
            expectedType.componentType.isInstance(value)
        ) {
            val array = java.lang.reflect.Array.newInstance(expectedType.componentType, 1)
            java.lang.reflect.Array.set(array, 0, value)
            value = array
        }
        require(expectedType.isInstance(value)) {
            String.format(
                "Attribute '%s' is of type %s, but %s was expected in attributes for annotation [%s]",
                attributeName, value.javaClass.simpleName, expectedType.simpleName,
                displayName
            )
        }
        return value as T
    }
}