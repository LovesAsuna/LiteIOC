package com.hyosakura.liteioc.bean.factory.parsing

import com.hyosakura.liteioc.core.io.Resource

/**
 * @author LovesAsuna
 **/
class PassThroughSourceExtractor : SourceExtractor {

    override fun extractSource(sourceCandidate: Any, definingResource: Resource?): Any {
        return sourceCandidate
    }

}