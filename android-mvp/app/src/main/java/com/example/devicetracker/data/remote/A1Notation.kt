package com.example.devicetracker.data.remote

fun toA1Column(columnCount: Int): String {
    require(columnCount > 0) { "columnCount must be > 0" }

    var value = columnCount
    val result = StringBuilder()
    while (value > 0) {
        val remainder = (value - 1) % 26
        result.append(('A'.code + remainder).toChar())
        value = (value - 1) / 26
    }
    return result.reverse().toString()
}
