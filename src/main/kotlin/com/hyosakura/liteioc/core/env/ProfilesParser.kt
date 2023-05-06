package com.hyosakura.liteioc.core.env

import org.jetbrains.annotations.Nullable
import java.util.*
import java.util.function.Predicate

internal object ProfilesParser {
    fun parse(vararg expressions: String): Profiles {
        require(expressions.isNotEmpty()) { "Must specify at least one profile" }
        val parsed = arrayOfNulls<Profiles>(expressions.size)
        for (i in expressions.indices) {
            parsed[i] = parseExpression(expressions[i])
        }
        return ParsedProfiles(expressions, parsed)
    }

    private fun parseExpression(expression: String?): Profiles {
        require(!expression.isNullOrEmpty()) { "Invalid profile expression [$expression]: must contain text" }
        val tokens = StringTokenizer(expression, "()&|!", true)
        return parseTokens(expression, tokens)
    }

    private fun parseTokens(expression: String, tokens: StringTokenizer, context: Context = Context.NONE): Profiles {
        val elements: MutableList<Profiles> = ArrayList()
        var operator: Operator? = null
        while (tokens.hasMoreTokens()) {
            val token = tokens.nextToken().trim { it <= ' ' }
            if (token.isEmpty()) {
                continue
            }
            when (token) {
                "(" -> {
                    val contents = parseTokens(expression, tokens, Context.BRACKET)
                    if (context == Context.INVERT) {
                        return contents
                    }
                    elements.add(contents)
                }

                "&" -> {
                    assertWellFormed(expression, operator == null || operator == Operator.AND)
                    operator = Operator.AND
                }

                "|" -> {
                    assertWellFormed(expression, operator == null || operator == Operator.OR)
                    operator = Operator.OR
                }

                "!" -> elements.add(not(parseTokens(expression, tokens, Context.INVERT)))
                ")" -> {
                    val merged = merge(expression, elements, operator)
                    if (context == Context.BRACKET) {
                        return merged
                    }
                    elements.clear()
                    elements.add(merged)
                    operator = null
                }

                else -> {
                    val value = equals(token)
                    if (context == Context.INVERT) {
                        return value
                    }
                    elements.add(value)
                }
            }
        }
        return merge(expression, elements, operator)
    }

    private fun merge(expression: String, elements: List<Profiles>, @Nullable operator: Operator?): Profiles {
        assertWellFormed(expression, !elements.isEmpty())
        if (elements.size == 1) {
            return elements[0]
        }
        val profiles = elements.toTypedArray()
        return if (operator == Operator.AND) and(*profiles) else or(*profiles)
    }

    private fun assertWellFormed(expression: String, wellFormed: Boolean) {
        require(wellFormed) { "Malformed profile expression [$expression]" }
    }

    private fun or(vararg profiles: Profiles): Profiles {
        return Profiles { activeProfile: Predicate<String> -> Arrays.stream(profiles).anyMatch(isMatch(activeProfile)) }
    }

    private fun and(vararg profiles: Profiles): Profiles {
        return Profiles { activeProfile: Predicate<String> -> Arrays.stream(profiles).allMatch(isMatch(activeProfile)) }
    }

    private fun not(profiles: Profiles): Profiles {
        return Profiles { activeProfile: Predicate<String> -> !profiles.matches(activeProfile) }
    }

    private fun equals(profile: String): Profiles {
        return Profiles { activeProfile: Predicate<String> -> activeProfile.test(profile) }
    }

    private fun isMatch(activeProfile: Predicate<String>): Predicate<Profiles> {
        return Predicate { profiles: Profiles -> profiles.matches(activeProfile) }
    }

    private enum class Operator {
        AND, OR
    }

    private enum class Context {
        NONE, INVERT, BRACKET
    }

    private class ParsedProfiles(expressions: Array<out String>, parsed: Array<Profiles?>) : Profiles {
        private val expressions: MutableSet<String> = LinkedHashSet()
        private val parsed: Array<Profiles?>

        init {
            Collections.addAll(this.expressions, *expressions)
            this.parsed = parsed
        }

        override fun matches(activeProfiles: Predicate<String>): Boolean {
            for (candidate in parsed) {
                if (candidate!!.matches(activeProfiles)) {
                    return true
                }
            }
            return false
        }

        override fun hashCode(): Int {
            return expressions.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null) {
                return false
            }
            if (javaClass != other.javaClass) {
                return false
            }
            val that = other as ParsedProfiles
            return expressions == that.expressions
        }

        override fun toString(): String {
            return expressions.joinToString(" or ")
        }
    }
}