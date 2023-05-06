package com.hyosakura.liteioc.core.annotation

/**
 * @author LovesAsuna
 **/
interface MergedAnnotationSelector<A : Annotation> {

    fun isBestCandidate(annotation: MergedAnnotation<A>): Boolean {
        return false
    }

    fun select(existing: MergedAnnotation<A>, candidate: MergedAnnotation<A>): MergedAnnotation<A>

}