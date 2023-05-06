package com.hyosakura.liteioc.core.annotation

/**
 * @author LovesAsuna
 **/
interface AnnotationsProcessor<C, R> {

    fun doWithAggregate(context: C, aggregateIndex: Int): R? {
        return null
    }

    fun doWithAnnotations(context: C, aggregateIndex: Int, source: Any?, annotations: Array<out Annotation>): R?

    fun finish(result: R?): R? {
        return result
    }

}