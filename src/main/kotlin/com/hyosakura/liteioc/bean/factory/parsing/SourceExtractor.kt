package com.hyosakura.liteioc.bean.factory.parsing

import com.hyosakura.liteioc.core.io.Resource

/**
 * @author LovesAsuna
 **/
fun interface SourceExtractor {

    fun extractSource(sourceCandidate: Any, definingResource: Resource?): Any?

}