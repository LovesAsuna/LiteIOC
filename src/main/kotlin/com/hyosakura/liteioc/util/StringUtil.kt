package com.hyosakura.liteioc.util

import java.util.*

object StringUtil {

    private val EMPTY_STRING_ARRAY = arrayOf<String>()

    private const val FOLDER_SEPARATOR = "/"

    private const val FOLDER_SEPARATOR_CHAR = '/'

    private const val WINDOWS_FOLDER_SEPARATOR = "\\"

    private const val TOP_PATH = ".."

    private const val CURRENT_PATH = "."

    fun getSetterMethodByFieldName(fieldName: String): String {
        return "set" + fieldName.substring(0, 1).uppercase(Locale.getDefault()) + fieldName.substring(1)
    }

    fun toStringArray(collection: Collection<String>?): Array<String> {
        return if (collection.isNullOrEmpty()) {
            EMPTY_STRING_ARRAY
        } else {
            collection.toTypedArray()
        }
    }

    fun capitalize(str: String): String {
        return changeFirstCharacterCase(str, true)
    }

    fun uncapitalize(str: String): String {
        return changeFirstCharacterCase(str, false)
    }

    private fun changeFirstCharacterCase(str: String, capitalize: Boolean): String {
        if (str.isEmpty()) {
            return str
        }
        val baseChar = str[0]
        val updatedChar: Char = if (capitalize) {
            baseChar.uppercaseChar()
        } else {
            baseChar.lowercaseChar()
        }
        if (baseChar == updatedChar) {
            return str
        }
        val chars = str.toCharArray()
        chars[0] = updatedChar
        return String(chars)
    }

    fun cleanPath(path: String): String {
        if (path.isEmpty()) {
            return path
        }
        val normalizedPath: String = path.replace(
            WINDOWS_FOLDER_SEPARATOR,
            FOLDER_SEPARATOR
        )
        var pathToUse = normalizedPath

        // Shortcut if there is no work to do
        if (pathToUse.indexOf('.') == -1) {
            return pathToUse
        }

        // Strip prefix from path to analyze, to not treat it as part of the
        // first path element. This is necessary to correctly parse paths like
        // "file:core/../core/io/Resource.class", where the ".." should just
        // strip the first "core" directory while keeping the "file:" prefix.
        val prefixIndex = pathToUse.indexOf(':')
        var prefix = ""
        if (prefixIndex != -1) {
            prefix = pathToUse.substring(0, prefixIndex + 1)
            if (prefix.contains(FOLDER_SEPARATOR)) {
                prefix = ""
            } else {
                pathToUse = pathToUse.substring(prefixIndex + 1)
            }
        }
        if (pathToUse.startsWith(FOLDER_SEPARATOR)) {
            prefix += FOLDER_SEPARATOR
            pathToUse = pathToUse.substring(1)
        }
        val pathArray = pathToUse.split(FOLDER_SEPARATOR)
        // we never require more elements than pathArray and in the common case the same number
        val pathElements: Deque<String> = ArrayDeque(pathArray.size)
        var tops = 0
        for (i in pathArray.indices.reversed()) {
            val element = pathArray[i]
            if (CURRENT_PATH == element) {
                // Points to current directory - drop it.
            } else if (TOP_PATH == element) {
                // Registering top path found.
                tops++
            } else {
                if (tops > 0) {
                    // Merging path element with element corresponding to top path.
                    tops--
                } else {
                    // Normal path element found.
                    pathElements.addFirst(element)
                }
            }
        }

        // All path elements stayed the same - shortcut
        if (pathArray.size == pathElements.size) {
            return normalizedPath
        }
        // Remaining top paths need to be retained.
        for (i in 0 until tops) {
            pathElements.addFirst(TOP_PATH)
        }
        // If nothing else left, at least explicitly point to current path.
        if (pathElements.size == 1 && pathElements.last.isEmpty() && !prefix.endsWith(FOLDER_SEPARATOR)) {
            pathElements.addFirst(CURRENT_PATH)
        }
        val joined: String = pathElements.joinToString(FOLDER_SEPARATOR)
        // avoid string concatenation with empty prefix
        return if (prefix.isEmpty()) joined else prefix + joined
    }

    fun applyRelativePath(path: String, relativePath: String): String {
        val separatorIndex: Int = path.lastIndexOf(FOLDER_SEPARATOR_CHAR)
        return if (separatorIndex != -1) {
            var newPath = path.substring(0, separatorIndex)
            if (!relativePath.startsWith(FOLDER_SEPARATOR)) {
                newPath += FOLDER_SEPARATOR_CHAR
            }
            newPath + relativePath
        } else {
            relativePath
        }
    }

    fun getFilename(path: String?): String? {
        if (path == null) {
            return null
        }
        val separatorIndex: Int = path.lastIndexOf(FOLDER_SEPARATOR_CHAR)
        return if (separatorIndex != -1) path.substring(separatorIndex + 1) else path
    }

    fun substringMatch(str: CharSequence, index: Int, substring: CharSequence): Boolean {
        if (index + substring.length > str.length) {
            return false
        }
        for (i in substring.indices) {
            if (str[index + i] != substring[i]) {
                return false
            }
        }
        return true
    }

}