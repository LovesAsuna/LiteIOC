package com.hyosakura.liteioc.core.annotation

/**
 * @author LovesAsuna
 **/
@Suppress("UNCHECKED_CAST")
abstract class MergedAnnotationSelectors private constructor() {

    companion object {

        private val NEAREST: MergedAnnotationSelector<*> = Nearest()

        private val FIRST_DIRECTLY_DECLARED: MergedAnnotationSelector<*> = FirstDirectlyDeclared()
        open fun <A : Annotation> nearest(): MergedAnnotationSelector<A> {
            return NEAREST as MergedAnnotationSelector<A>
        }

        open fun <A : Annotation> firstDirectlyDeclared(): MergedAnnotationSelector<A> {
            return FIRST_DIRECTLY_DECLARED as MergedAnnotationSelector<A>
        }

    }

    private class Nearest : MergedAnnotationSelector<Annotation> {
        override fun isBestCandidate(annotation: MergedAnnotation<Annotation>): Boolean {
            return annotation.getDistance() == 0
        }

        override fun select(
            existing: MergedAnnotation<Annotation>, candidate: MergedAnnotation<Annotation>
        ): MergedAnnotation<Annotation> {
            return if (candidate.getDistance() < existing.getDistance()) {
                candidate
            } else existing
        }

    }

    private class FirstDirectlyDeclared : MergedAnnotationSelector<Annotation> {
        override fun isBestCandidate(annotation: MergedAnnotation<Annotation>): Boolean {
            return annotation.getDistance() == 0
        }

        override fun select(
            existing: MergedAnnotation<Annotation>, candidate: MergedAnnotation<Annotation>
        ): MergedAnnotation<Annotation> {
            return if (existing.getDistance() > 0 && candidate.getDistance() == 0) {
                candidate
            } else existing
        }

    }

}