package com.example.detectortest

import android.graphics.Bitmap
import android.graphics.Color

class ConvolutionMatrix(size: Int) {
    private val matrix: Array<DoubleArray> = Array(size) { DoubleArray(size) }

    fun setAll(value: Double) {
        for (i in matrix.indices) {
            for (j in matrix[i].indices) {
                matrix[i][j] = value
            }
        }
    }

    fun applyConvolution(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // Convert bitmap to ARGB_8888 configuration
        val convertedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        val newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val size = matrix.size
        val radius = size / 2

        for (x in radius until width - radius) {
            for (y in radius until height - radius) {
                var r = 0.0
                var g = 0.0
                var b = 0.0

                for (i in 0 until size) {
                    for (j in 0 until size) {
                        val pixel = convertedBitmap.getPixel(x + i - radius, y + j - radius)
                        val factor = matrix[i][j]

                        r += Color.red(pixel) * factor
                        g += Color.green(pixel) * factor
                        b += Color.blue(pixel) * factor
                    }
                }

                val newPixel = Color.rgb(
                    r.toInt().coerceIn(0, 255),
                    g.toInt().coerceIn(0, 255),
                    b.toInt().coerceIn(0, 255)
                )
                newBitmap.setPixel(x, y, newPixel)
            }
        }

        return newBitmap
    }
}