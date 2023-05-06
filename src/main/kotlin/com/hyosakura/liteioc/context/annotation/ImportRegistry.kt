package com.hyosakura.liteioc.context.annotation

import com.hyosakura.liteioc.core.type.AnnotationMetadata

/**
 * @author LovesAsuna
 **/
interface ImportRegistry {

    fun getImportingClassFor(importedClass: String): AnnotationMetadata?

    fun removeImportingClass(importingClass: String)

}