package com.hyosakura.liteioc.util.xml

import org.slf4j.Logger
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException

/**
 * @author LovesAsuna
 **/
class SimpleSaxErrorHandler(private val logger: Logger) : ErrorHandler {

    @Throws(SAXException::class)
    override fun warning(ex: SAXParseException) {
        logger.warn("Ignored XML validation warning", ex)
    }

    @Throws(SAXException::class)
    override fun error(ex: SAXParseException) {
        throw ex
    }

    @Throws(SAXException::class)
    override fun fatalError(ex: SAXParseException) {
        throw ex
    }

}