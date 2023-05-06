package com.hyosakura.liteioc.core.convert.support

import com.hyosakura.liteioc.core.convert.ConversionFailedException
import com.hyosakura.liteioc.core.convert.TypeDescriptor
import com.hyosakura.liteioc.core.convert.converter.GenericConverter

/**
 * @author LovesAsuna
 **/
object ConversionUtil {

    fun invokeConverter(
        converter: GenericConverter, source: Any?,
        sourceType: TypeDescriptor, targetType: TypeDescriptor
    ): Any? {
        return try {
            converter.convert(source, sourceType, targetType)
        } catch (ex: ConversionFailedException) {
            throw ex
        } catch (ex: Throwable) {
            throw ConversionFailedException(sourceType, targetType, source, ex)
        }
    }

}