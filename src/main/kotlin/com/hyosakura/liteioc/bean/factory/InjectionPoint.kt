package com.hyosakura.liteioc.bean.factory

import com.hyosakura.liteioc.core.MethodParameter
import java.lang.reflect.Field

/**
 * @author LovesAsuna
 **/
open class InjectionPoint {

    var methodParameter: MethodParameter? = null

    var field: Field? = null

    @Volatile
    private var fieldAnnotations: Array<Annotation>? = null

    constructor(methodParameter: MethodParameter) {
        this.methodParameter = methodParameter
    }

    constructor(field: Field) {
        this.field = field
    }

    constructor(original: InjectionPoint) {
        methodParameter = if (original.methodParameter != null) MethodParameter(original.methodParameter!!) else null
        this.field = original.field
        this.fieldAnnotations = original.fieldAnnotations
    }

    fun getAnnotations(): Array<Annotation> {
        return if (field != null) {
            var fieldAnnotations = this.fieldAnnotations
            if (fieldAnnotations == null) {
                fieldAnnotations = this.field!!.annotations
                this.fieldAnnotations = fieldAnnotations
            }
            fieldAnnotations!!
        } else {
            this.methodParameter!!.getParameterAnnotations()
        }
    }

    fun obtainMethodParameter(): MethodParameter {
        return requireNotNull(methodParameter) { "Neither Field nor MethodParameter" }
    }

    override fun toString(): String {
        return if (field != null) "field '" + field!!.name + "'" else methodParameter.toString()
    }

}