package com.example.detectortest

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TFDetector (
    appContext: Context
) : Detector {
    private val imageProcessor = ImageProcessor.Builder()
        //.add(ResizeOp(640, 480, ResizeOp.ResizeMethod.BILINEAR))
        .add(NormalizeOp(0f, 255f))
        .build()

    private val tflite: Interpreter
    private val tensorImage = TensorImage(DataType.FLOAT32)
    private val boxesTensor = TensorBuffer.createFixedSize(intArrayOf(1, 1000, 4), DataType.FLOAT32)
    private val detectionsCountTensor = TensorBuffer.createFixedSize(intArrayOf(4), DataType.UINT8)
    private val labelsTensor = TensorBuffer.createFixedSize(intArrayOf(1, 1000), DataType.FLOAT32)
    private val scoresTensor = TensorBuffer.createFixedSize(intArrayOf(1, 1000), DataType.FLOAT32)
    private val outputs = mutableMapOf<Int, Any>(
        0 to boxesTensor.buffer, // 1000 values (4 float)
        1 to detectionsCountTensor.buffer, // 1 value (objects count)
        2 to labelsTensor.buffer, // 1000 values
        3 to scoresTensor.buffer, // 1000 values
    )

    init {
        val tfliteModel = loadTFLiteModelFromAsset(appContext.assets, "scanny-detector-640-480-fp16.tflite")
        val tfliteOptions = Interpreter.Options()
        tflite = Interpreter(tfliteModel, tfliteOptions)
        tflite.allocateTensors()
    }

    override fun detect(image: Bitmap): List<Detection> {
        for (buffer in outputs.values) {
            (buffer as ByteBuffer).rewind()
        }

        val paddedImage = resizeWithPadding(image, 480, 640)
        tensorImage.load(paddedImage)
        val tensorImage = imageProcessor.process(tensorImage) /** Входной тензор корректен, у Сергея отрабатывает */
        tflite.runForMultipleInputsOutputs(arrayOf(tensorImage.buffer), outputs)
        return convert(image.width, image.height, 480, 640)
    }

    override fun close() {
        tflite.close()
    }

    private fun convert(srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int): List<Detection> {
        var detectionsCount = 0 /** Просто смотрю дебагером кол-во детекций, в activity не выводится картинка или тп */
        detectionsCountTensor.intArray.forEach { count ->
            detectionsCount += count
            if (count < 255)
                return@forEach
        }
        val boxesTensor = boxesTensor.floatArray
        val labelsTensor = labelsTensor.floatArray
        val scoresTensor = scoresTensor.floatArray
        val detections = ArrayList<Detection>(detectionsCount)

        Log.d("DETECTIONS", "$detectionsCount")

        val srcRatio = 1f * srcWidth / srcHeight
        val dstRatio = 1f * dstWidth / dstHeight
        var ax = 1f
        var bx = 0f
        var ay = 1f
        var by = 0f
        if (dstRatio >= srcRatio) {
            val notScaledDstWidth = (srcWidth * dstRatio / srcRatio).toInt()
            ax = 1f * notScaledDstWidth / srcWidth
            bx = -ax * ((notScaledDstWidth - srcWidth) / 2) / notScaledDstWidth
        } else {
            val notScaledDstHeight = (srcHeight * srcRatio / dstRatio).toInt()
            ay = 1f * notScaledDstHeight / srcHeight
            by = -ay * ((notScaledDstHeight - srcHeight) / 2) / notScaledDstHeight
        }

        for (k in 0 until detectionsCount) {
            val det = Detection(
                RectF(
                    ax * boxesTensor[k * 4 + 0] + bx,
                    ay * boxesTensor[k * 4 + 1] + by,
                    ax * boxesTensor[k * 4 + 2] + bx,
                    ay * boxesTensor[k * 4 + 3] + by,
                ),
                labelFloatToStr(labelsTensor[k]),
                scoresTensor[k],
            )
            detections.add(det)
        }

        return detections
    }

    private fun labelFloatToStr(labelFloat: Float): String {
        return when(labelFloat) {
            0.0f -> "item"
            1.0f -> "pricetag"
            2.0f -> "shelf"
            else -> ""
        }
    }

    private fun resizeWithPadding(bitmap: Bitmap, w: Int, h: Int): Bitmap {
        val srcRatio = 1f * bitmap.width / bitmap.height
        val dstRatio = 1f * w / h

        val paddedBitmap = Bitmap.createBitmap(w, h, (Bitmap.Config.ARGB_8888))
        val canvas = Canvas(paddedBitmap)
        canvas.drawARGB(0xff, 0x0, 0x0, 0x0)
        val dstRect = if (dstRatio >= srcRatio) {
            val newWidth = (h * srcRatio).toInt()
            Rect(w / 2 - newWidth / 2, 0, w / 2 + newWidth / 2, h)
        } else {
            val newHeight = (w / srcRatio).toInt()
            Rect(0, h / 2 - newHeight / 2, w, h / 2 + newHeight / 2)
        }

        canvas.drawBitmap(bitmap, null, dstRect, null)

        return paddedBitmap
    }

    private fun loadTFLiteModelFromAsset(assets: AssetManager, modelPath: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel: FileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun writeStringToFile(context: Context, fileName: String, data: String) {
        val resolver: ContentResolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
        }

        var outputStream: OutputStream? = null
        var uri: Uri? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.run {
                    uri = this.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), contentValues)
                    outputStream = uri?.let { this.openOutputStream(it) }
                }
            } else {
                val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val file = File(documentsDir, fileName)
                outputStream = FileOutputStream(file)
            }

            outputStream?.use { it.write(data.toByteArray()) }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            outputStream?.close()
        }
    }
}