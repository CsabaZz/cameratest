@file:JvmName("BindingUtil")

package com.zappyware.cameratest.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.widget.ImageView
import androidx.databinding.BindingAdapter
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.BufferedInputStream
import java.io.File
import kotlin.math.max

private const val DOWNSAMPLE_SIZE: Int = 1024

private val bitmapOptions = BitmapFactory.Options().apply {
    inJustDecodeBounds = false
    if (max(outHeight, outWidth) > DOWNSAMPLE_SIZE) {
        val scaleFactorX = outWidth / DOWNSAMPLE_SIZE + 1
        val scaleFactorY = outHeight / DOWNSAMPLE_SIZE + 1
        inSampleSize = max(scaleFactorX, scaleFactorY)
    }
}

private val bitmapRotationMatrix = Matrix().apply { postRotate(90F) }

@BindingAdapter(value = ["bitmapFile"])
fun ImageView.setBitmapFromFile(file: File) {
    createBitmapSingle(file)
        .flatMap { createGrayscaleSingle(it) }
        .observeOn(AndroidSchedulers.mainThread())
        .doOnSuccess { setImageBitmap(it) }
        .subscribe()
}

private fun createBitmapSingle(file: File): Single<Bitmap> {
    return Single.create<Bitmap> {
            val buffer = BufferedInputStream(file.inputStream()).let { stream ->
                ByteArray(stream.available()).also { bytes ->
                    stream.read(bytes)
                    stream.close()
                }
            }

            var bitmap = BitmapFactory.decodeByteArray(buffer, 0, buffer.size, bitmapOptions)
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, bitmapRotationMatrix, true)
            it.onSuccess(bitmap)
        }
        .subscribeOn(Schedulers.io())
}

private fun createGrayscaleSingle(bitmap: Bitmap): Single<Bitmap> {
    return Single.create<Bitmap> { it.onSuccess(bitmap.toGrayscale()) }
        .subscribeOn(Schedulers.computation())
}
