package com.zappyware.cameratest.utils

import android.graphics.*

fun Bitmap.toGrayscaleAllPixels(redVal: Float = 0.299f, greenVal: Float = 0.587f, blueVal: Float = 0.114f): Bitmap {
    val bmOut = Bitmap.createBitmap(width, height, config)

    var alpha: Int
    var red: Int
    var green: Int
    var blue: Int

    var pixel: Int

    val width = width
    val height = height

    for (x in 0 until width) {
        for (y in 0 until height) {
            pixel = getPixel(x, y)

            alpha = Color.alpha(pixel)
            red = Color.red(pixel)
            green = Color.green(pixel)
            blue = Color.blue(pixel)

            blue = (redVal * red + greenVal * green + blueVal * blue).toInt()
            green = blue
            red = green

            bmOut.setPixel(x, y, Color.argb(alpha, red, green, blue))
        }
    }

    return bmOut
}

fun Bitmap.toGrayscale(): Bitmap {
    val paint = Paint()
    paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })

    val grayscaledBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    grayscaledBitmap.run {
        val canvas = Canvas(this)
        canvas.drawBitmap(this@toGrayscale, 0F, 0F, paint)
    }

    return grayscaledBitmap
}
