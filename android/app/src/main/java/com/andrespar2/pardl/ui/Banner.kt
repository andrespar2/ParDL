package com.andrespar2.pardl.ui

/**
 * Banner pixel-art "PARDL" generado con la misma fuente de mapa de bits que
 * usa la versión de PC (ParDL.Core.ps1), para conservar su identidad visual.
 */
object Banner {

    private val font: Map<Char, List<String>> = mapOf(
        'P' to listOf("11111", "10001", "11111", "10000", "10000"),
        'A' to listOf("11111", "10001", "11111", "10001", "10001"),
        'R' to listOf("11110", "10001", "11110", "10100", "10010"),
        'D' to listOf("11110", "10001", "10001", "10001", "11110"),
        'L' to listOf("10000", "10000", "10000", "10000", "11111")
    )

    /** Rejilla de píxeles encendidos/apagados (5 filas). */
    fun grid(text: String = "PARDL"): List<List<Boolean>> {
        val letters = text.uppercase().mapNotNull { font[it] }
        if (letters.isEmpty()) return emptyList()
        return (0 until 5).map { r ->
            val row = ArrayList<Boolean>()
            letters.forEachIndexed { i, m ->
                if (i > 0) row.add(false)
                m[r].forEach { row.add(it == '1') }
            }
            row
        }
    }
}
