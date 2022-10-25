package com.hyosakura.liteioc.util

import java.util.*

object StringUtil {
    fun getSetterMethodByFieldName(fieldName: String): String {
        return "set" + fieldName.substring(0, 1).uppercase(Locale.getDefault()) + fieldName.substring(1)
    }
}