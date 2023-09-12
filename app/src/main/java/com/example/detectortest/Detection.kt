package com.example.detectortest

import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class Detection(
    val bbox: RectF,
    val label: String,
    val score: Float,
) : Parcelable {
    @IgnoredOnParcel
    val xmin = bbox.left
    @IgnoredOnParcel
    val ymin = bbox.top
    @IgnoredOnParcel
    val xmax = bbox.right
    @IgnoredOnParcel
    val ymax = bbox.bottom
    @IgnoredOnParcel
    val height = ymax - ymin
    @IgnoredOnParcel
    val width = xmax - xmin
    @IgnoredOnParcel
    val xcenter = (xmin + xmax) / 2
    @IgnoredOnParcel
    val ycenter = (ymin + ymax) / 2
    @IgnoredOnParcel
    val pointCenter = PointF(xcenter, ycenter)
}

fun Detection.toAbsRect(width: Int, height: Int): Rect = this.bbox.toAbsRect(width, height)

fun Detection.relativeTo(other: RectF, allowOutOfBoundsCoords: Boolean = true): Detection {
    val ax = 1 / (other.right - other.left)
    val bx = -ax * other.left
    val ay = 1 / (other.bottom - other.top)
    val by = -ay * other.top

    var newXmin = (ax * xmin + bx)
    var newYmin = (ay * ymin + by)
    var newXmax = (ax * xmax + bx)
    var newYmax = (ay * ymax + by)

    if (!allowOutOfBoundsCoords) {
        newXmin = newXmin.coerceIn(0.0f, 1.0f)
        newYmin = newYmin.coerceIn(0.0f, 1.0f)
        newXmax = newXmax.coerceIn(0.0f, 1.0f)
        newYmax = newYmax.coerceIn(0.0f, 1.0f)
    }
    val newRect = RectF(newXmin, newYmin, newXmax, newYmax)
    return Detection(newRect, label, score)
}

fun RectF.toAbsRect(width: Int, height: Int): Rect {
    return Rect(
        (this.left * width).toInt(),
        (this.top * height).toInt(),
        (this.right * width).toInt(),
        (this.bottom * height).toInt(),
    )
}