package com.example.detectortest

import android.graphics.Bitmap

interface Detector {
    fun detect(image: Bitmap): List<Detection>
    fun close()
}