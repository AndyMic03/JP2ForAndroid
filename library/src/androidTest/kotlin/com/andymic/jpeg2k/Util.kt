package com.andymic.jpeg2k

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.junit.Assert
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.abs

class Util(private val ctx: Context) {
    fun assertBitmapsEqual(expected: Bitmap, actual: Bitmap) {
        assertBitmapsEqual(null, expected, actual)
    }

    fun assertBitmapsEqual(message: String?, expected: Bitmap, actual: Bitmap) {
        Assert.assertEquals(message, expected.getWidth().toLong(), actual.getWidth().toLong())
        Assert.assertEquals(message, expected.getHeight().toLong(), actual.getHeight().toLong())
        val pixels1 = IntArray(expected.getWidth() * expected.getHeight())
        val pixels2 = IntArray(actual.getWidth() * actual.getHeight())
        expected.getPixels(
            pixels1,
            0,
            expected.getWidth(),
            0,
            0,
            expected.getWidth(),
            expected.getHeight()
        )
        actual.getPixels(pixels2, 0, actual.getWidth(), 0, 0, actual.getWidth(), actual.getHeight())
        for (i in pixels1.indices) {
            if (pixels1[i] != pixels2[i]) {
                val expR = pixels1[i] shr 16 and 0xFF
                val expG = pixels1[i] shr 8 and 0xFF
                val expB = pixels1[i] and 0xFF

                val actR = pixels2[i] shr 16 and 0xFF
                val actG = pixels2[i] shr 8 and 0xFF
                val actB = pixels2[i] and 0xFF

                val diff = abs(expR - actR) + abs(expG - actG) + abs(expB - actB)

                if (diff > 3) {
                    Assert.fail(
                        (if (message != null) "$message; " else "") + String.format(
                            "pixel %d different - expected %08X, got %08X",
                            i,
                            pixels1[i],
                            pixels2[i]
                        )
                    )
                }
            }
        }
    }

    fun assertBitmapsEqual(expected: IntArray, actual: IntArray) {
        assertBitmapsEqual(null, expected, actual)
    }

    fun assertBitmapsEqual(message: String?, expected: IntArray, actual: IntArray) {
        Assert.assertEquals(
            (if (message != null) "$message; " else "") + "different number of pixels",
            expected.size.toLong(),
            actual.size.toLong()
        )
        for (i in expected.indices) {
            if (expected[i] != actual[i]) {
                Assert.fail(
                    (if (message != null) "$message; " else "") + String.format(
                        "pixel %d different - expected %08X, got %08X",
                        i,
                        expected[i],
                        actual[i]
                    )
                )
            }
        }
    }

    @Throws(Exception::class)
    fun loadAssetFile(name: String): ByteArray? {
        ctx.resources.assets.open(name).use { inputStream ->
            val out = ByteArrayOutputStream(inputStream.available())
            val buffer = ByteArray(8192)
            var count: Int
            while ((inputStream.read(buffer).also { count = it }) >= 0) {
                out.write(buffer, 0, count)
            }
            return out.toByteArray()
        }
    }

    @Throws(IOException::class)
    fun openAssetStream(name: String): InputStream {
        return ctx.assets.open(name)
    }

    @Throws(Exception::class)
    fun loadFile(name: String?): ByteArray? {
        FileInputStream(name).use { inputStream ->
            val out = ByteArrayOutputStream(inputStream.available())
            val buffer = ByteArray(8192)
            var count: Int
            while ((inputStream.read(buffer).also { count = it }) >= 0) {
                out.write(buffer, 0, count)
            }
            return out.toByteArray()
        }
    }

    @Throws(Exception::class)
    fun loadAssetBitmap(name: String): Bitmap {
        ctx.resources.assets.open(name).use { inputStream ->
            val opts = BitmapFactory.Options()
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888
            opts.inPremultiplied = false
            var bmp = BitmapFactory.decodeStream(inputStream, null, opts)
            if (bmp!!.getConfig() != Bitmap.Config.ARGB_8888) {
                //convert to ARGB_8888 for pixel comparison purposes
                val pixels = IntArray(bmp.getWidth() * bmp.getHeight())
                bmp.getPixels(
                    pixels,
                    0,
                    bmp.getWidth(),
                    0,
                    0,
                    bmp.getWidth(),
                    bmp.getHeight()
                )
                bmp = Bitmap.createBitmap(
                    pixels,
                    bmp.getWidth(),
                    bmp.getHeight(),
                    Bitmap.Config.ARGB_8888
                )
            }
            return bmp
        }
    }

    @Throws(Exception::class)
    fun loadAssetRawPixels(name: String): IntArray {
        //raw bitmaps are stored by component in RGBA order (i.e.) first all R, then all G, then all B, then all A
        var data: ByteArray? = null
        ctx.resources.assets.open(name).use { inputStream ->
            val out = ByteArrayOutputStream(inputStream.available())
            val buffer = ByteArray(8192)
            var count: Int
            while ((inputStream.read(buffer).also { count = it }) >= 0) {
                out.write(buffer, 0, count)
            }
            data = out.toByteArray()
        }
        Assert.assertEquals("raw data length not divisible by 4", 0, (data!!.size % 4).toLong())
        val length = data.size / 4
        val pixels = IntArray(length)
        for (i in 0..<length) {
            pixels[i] = (((data[i].toInt() and 0xFF) shl 16) //R
                    or ((data[i + length].toInt() and 0xFF) shl 8) //G
                    or (data[i + length * 2].toInt() and 0xFF) //B
                    or ((data[i + length * 3].toInt() and 0xFF) shl 24)) //A
        }
        return pixels
    }

    @Throws(Exception::class)
    fun loadAssetRawBitmap(name: String, width: Int, height: Int): Bitmap {
        val pixels = loadAssetRawPixels(name)
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    @Throws(IOException::class)
    fun createFile(name: String, encoded: ByteArray?): File {
        val outFile = File(ctx.filesDir, name)
        val out = FileOutputStream(outFile)
        out.write(encoded)
        out.close()
        return outFile
    }

    @Throws(IOException::class)
    fun createFile(encoded: ByteArray?): File {
        val outFile = File.createTempFile("testjp2", "tmp", ctx.filesDir)
        val out = FileOutputStream(outFile)
        out.write(encoded)
        out.close()
        return outFile
    }
}
