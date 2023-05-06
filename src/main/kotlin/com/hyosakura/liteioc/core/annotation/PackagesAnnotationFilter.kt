package com.hyosakura.liteioc.core.annotation

import java.util.*

/**
 * @author LovesAsuna
 **/
class PackagesAnnotationFilter : AnnotationFilter {

    private var prefixes: Array<String>

    private var hashCode = 0

    constructor(vararg packages: String) {
        prefixes = Array(packages.size) { i ->
            val pkg = packages[i]
            require(pkg.isNotEmpty()) { "Packages array must not have empty elements" }
            "$pkg."
        }
        Arrays.sort(prefixes)
        hashCode = prefixes.contentHashCode()
    }

    override fun matches(annotationType: String): Boolean {
        for (prefix in prefixes) {
            if (annotationType.startsWith(prefix)) {
                return true
            }
        }
        return false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return if (other == null || javaClass != other.javaClass) {
            false
        } else prefixes.contentEquals((other as PackagesAnnotationFilter).prefixes)
    }

    override fun hashCode(): Int = hashCode

    override fun toString(): String {
        return "Packages annotation filter: " + prefixes.joinToString(",")
    }

}