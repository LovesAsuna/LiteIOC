package com.hyosakura.liteioc.core.annotation

/**
 * @author LovesAsuna
 **/
fun interface AnnotationFilter {

    companion object {

        var PLAIN: AnnotationFilter = packages("java.lang", "com.hyosakura.liteioc.lang")

        var JAVA = packages("java", "javax")

        var ALL: AnnotationFilter = object : AnnotationFilter {

            override fun matches(annotation: Annotation): Boolean {
                return true
            }

            override fun matches(type: Class<*>): Boolean {
                return true
            }

            override fun matches(typeName: String): Boolean {
                return true
            }

            override fun toString(): String {
                return "All annotations filtered"
            }

        }

        fun packages(vararg packages: String): AnnotationFilter {
            return PackagesAnnotationFilter(*packages)
        }

    }

    fun matches(annotation: Annotation): Boolean {
        return matches(annotation.annotationClass.java)
    }

    fun matches(type: Class<*>): Boolean {
        return matches(type.name)
    }

    fun matches(typeName: String): Boolean

}