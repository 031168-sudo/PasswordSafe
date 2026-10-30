package com.example.passwordsafe.core

import java.security.SecureRandom
import kotlin.math.ln

data class GeneratorOptions(
    val length: Int = 20,
    val lower: Boolean = true,
    val upper: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val excludeAmbiguous: Boolean = true,
)

object PasswordGenerator {
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.<>/?~"
    private const val AMBIGUOUS = "Il1O0o|`'\""
    private val rng = SecureRandom()

    fun generate(o: GeneratorOptions): String {
        val sets = buildList {
            if (o.lower) add(LOWER)
            if (o.upper) add(UPPER)
            if (o.digits) add(DIGITS)
            if (o.symbols) add(SYMBOLS)
        }.map { s -> if (o.excludeAmbiguous) s.filterNot { it in AMBIGUOUS } else s }
            .ifEmpty { listOf(LOWER) }
        val length = o.length.coerceAtLeast(sets.size)
        val all = sets.joinToString("")
        // По одному символу из каждой выбранной группы, остальное — из общего набора, затем перемешиваем.
        val chars = sets.map { it[rng.nextInt(it.length)] }.toMutableList()
        while (chars.size < length) chars.add(all[rng.nextInt(all.length)])
        for (i in chars.indices.reversed()) {
            val j = rng.nextInt(i + 1)
            val t = chars[i]; chars[i] = chars[j]; chars[j] = t
        }
        return chars.joinToString("")
    }

    /** Грубая оценка энтропии в битах по алфавиту и длине. */
    fun entropyBits(pw: String): Double {
        if (pw.isEmpty()) return 0.0
        var pool = 0
        if (pw.any { it.isLowerCase() && it.code < 128 }) pool += 26
        if (pw.any { it.isUpperCase() && it.code < 128 }) pool += 26
        if (pw.any { it.isDigit() }) pool += 10
        if (pw.any { !it.isLetterOrDigit() && it.code < 128 }) pool += 32
        if (pw.any { it.code >= 128 }) pool += 66
        val unique = pw.toSet().size
        val effectiveLen = minOf(pw.length.toDouble(), unique * 1.5)
        return effectiveLen * ln(pool.toDouble()) / ln(2.0)
    }

    /** 0 — очень слабый … 4 — очень сильный. */
    fun strength(pw: String): Int {
        val bits = entropyBits(pw)
        return when {
            bits < 28 -> 0
            bits < 45 -> 1
            bits < 60 -> 2
            bits < 80 -> 3
            else -> 4
        }
    }
}
