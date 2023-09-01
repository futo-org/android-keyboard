package org.futo.voiceinput.shared.util

fun Array<DoubleArray>.transpose(): Array<DoubleArray> {
    return Array(this[0].size) { i ->
        DoubleArray(this.size) { j ->
            this[j][i]
        }
    }
}

fun Array<DoubleArray>.shape(): IntArray {
    return arrayOf(size, this[0].size).toIntArray()
}

fun FloatArray.toDoubleArray(): DoubleArray {
    return this.map { it.toDouble() }.toDoubleArray()
}
