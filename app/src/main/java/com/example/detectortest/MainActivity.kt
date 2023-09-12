package com.example.detectortest

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.scale
import com.example.detectortest.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.button.setOnClickListener {
            selectFromGallery.launch("image/*")
        }
    }

    private val selectFromGallery =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uriFromResult ->
            uriFromResult ?: return@registerForActivityResult
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(this.contentResolver, uriFromResult))
            } else {
                MediaStore.Images.Media.getBitmap(this.contentResolver, uriFromResult)
            }
            val detector = TFDetector(applicationContext)
            detector.detect(bitmap.copy(Bitmap.Config.ARGB_8888, false))
        }

    /**
     * Если заюзать обрезку, то детекция отрабатывает
     * Кроме того, работает с размытием
     */
    fun cropBitmap(bitmap: Bitmap): Bitmap {
        // Convert the bitmap to a mutable configuration
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        // Get the dimensions of the original bitmap
        val originalWidth = mutableBitmap.width
        val originalHeight = mutableBitmap.height

        // Calculate the new dimensions for the cropped bitmap
        val newWidth = originalWidth - 100 /** Большим фото надо 50-100 px, маленьким достаточно 1 */
        val newHeight = originalHeight

        // Create a new bitmap with the cropped dimensions
        val croppedBitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)

        // Copy pixels from the original bitmap to the cropped bitmap excluding the outermost column
        for (y in 0 until originalHeight) {
            for (x in 0 until newWidth) {
                croppedBitmap.setPixel(x, y, mutableBitmap.getPixel(x, y))
            }
        }

        return croppedBitmap
    }
}