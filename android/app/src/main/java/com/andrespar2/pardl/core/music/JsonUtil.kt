package com.andrespar2.pardl.core.music

import org.json.JSONArray
import org.json.JSONObject

/** Utilidades defensivas para JSON de APIs externas. */
object JsonUtil {

    fun text(obj: JSONObject?, vararg keys: String): String? {
        if (obj == null) return null
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val value = obj.opt(key)
            val text = when (value) {
                is JSONArray -> (0 until value.length())
                    .mapNotNull { value.optString(it).ifBlank { null } }
                    .joinToString(", ")
                else -> value.toString()
            }
            if (text.isNotBlank() && text != "null") return text.trim()
        }
        return null
    }

    fun long(obj: JSONObject?, vararg keys: String): Long? {
        if (obj == null) return null
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val raw = obj.opt(key).toString().trim()
            raw.toLongOrNull()?.let { return it }
            raw.toDoubleOrNull()?.let { return it.toLong() }
        }
        return null
    }

    fun int(obj: JSONObject?, vararg keys: String): Int? =
        long(obj, *keys)?.let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else null }

    fun bool(obj: JSONObject?, vararg keys: String): Boolean? {
        if (obj == null) return null
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val value = obj.opt(key)
            if (value is Boolean) return value
            val raw = value.toString().lowercase()
            if (raw == "true" || raw == "1") return true
            if (raw == "false" || raw == "0") return false
        }
        return null
    }

    fun array(obj: JSONObject?, key: String): JSONArray? =
        obj?.optJSONArray(key)?.takeIf { it.length() > 0 }

    fun objects(array: JSONArray?): List<JSONObject> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    /** Duración en segundos a partir de "123.4", "3:45" o "1:02:03". */
    fun durationSec(raw: String?): Int? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value.equals("null", true)) return null
        value.toDoubleOrNull()?.let { return it.toInt().takeIf { sec -> sec > 0 } }
        val parts = value.split(':').mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]).takeIf { it > 0 }
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]).takeIf { it > 0 }
            else -> null
        }
    }

    fun trackNumber(raw: String?): Int? =
        raw?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it > 0 }

    fun bitrateKbps(raw: String?): Int? {
        val text = raw?.trim()?.lowercase().orEmpty()
        if (text.isEmpty() || text.contains("vbr")) return null
        return Regex("(\\d{2,4})").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?.takeIf { it in 8..1536 }
    }
}
