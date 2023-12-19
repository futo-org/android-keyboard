package org.futo.inputmethod.latin.xlm

import org.futo.inputmethod.keyboard.KeyDetector
import kotlin.math.sqrt

private fun normalize(pair: Pair<Int, Int>): Pair<Float, Float> {
    val magnitude = sqrt((pair.first * pair.first + pair.second * pair.second).toDouble())

    if(magnitude == 0.0) {
        return Pair(Float.NaN, Float.NaN)
    }

    return Pair((pair.first.toFloat() / magnitude).toFloat(), (pair.second.toFloat() / magnitude).toFloat())
}

private fun dot(pair1: Pair<Float, Float>, pair2: Pair<Float, Float>): Float {
    return pair1.first * pair2.first + pair1.second * pair2.second
}

object BatchInputConverter {
    fun convertToString(x: IntArray, y: IntArray, size: Int, keyDetector: KeyDetector, outX: MutableList<Int>, outY: MutableList<Int>): String {
        val coords = x.zip(y).toMutableList()

        var s = ""
        for(i in 0 until size){
            if((i == 0) || (i == (size - 1))) {
                val key =
                    keyDetector.detectHitKey(coords[i].first, coords[i].second)?.label ?: continue
                if(s.isNotEmpty() && s.last() == key.first()) continue
                s += key
                outX.add(x[i])
                outY.add(y[i])
                continue
            }

            val currCoord = coords[i]
            val lastCoord = coords[i - 1]
            val nextCoord = coords[i + 1]

            val directionFromLastCoord = normalize(Pair(currCoord.first - lastCoord.first, currCoord.second - lastCoord.second))
            val directionFromNextCoord = normalize(Pair(nextCoord.first - currCoord.first, nextCoord.second - currCoord.second))

            if(directionFromLastCoord.first.isNaN() || directionFromLastCoord.second.isNaN()) continue
            if(directionFromNextCoord.first.isNaN() || directionFromNextCoord.second.isNaN()) continue

            val dot = dot(directionFromLastCoord, directionFromNextCoord)

            // TODO: Figure out a good threshold
            if(dot < 0.86) {
                val key =
                    keyDetector.detectHitKey(coords[i].first, coords[i].second)?.label ?: continue
                if(s.isNotEmpty() && s.last() == key.first()) continue
                s += key
                outX.add(x[i])
                outY.add(y[i])
                //println("Adding $key, dot $dot, dirs $directionFromLastCoord $directionFromNextCoord, coords $lastCoord $currCoord $nextCoord")
            } else {
                // Simplify
                coords[i] = lastCoord
            }
        }

        println("Transformed string: [$s]")

        return s.lowercase()
    }
}