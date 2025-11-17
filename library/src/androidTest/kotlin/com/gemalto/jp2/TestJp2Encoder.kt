package com.gemalto.jp2

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gemalto.jp2.JP2Decoder.Companion.isJPEG2000
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Arrays
import kotlin.math.abs
import kotlin.math.log10

@RunWith(AndroidJUnit4::class)
class TestJp2Encoder {
    // Context of the app under test.
    private var ctx: Context? = null
    private var util: Util? = null

    @Before
    fun init() {
        ctx = ApplicationProvider.getApplicationContext()
        util = Util(ctx!!)
    }

    /*
      Encode an image with all RGB colors into JP2, decode it and compare with the original.
     */
    @Test
    @Throws(Exception::class)
    fun testEncodeAllColorsLossless() {
        val bmp = util!!.loadAssetBitmap("fullrgb.png")

        val enc = JP2Encoder(bmp)
        val encoded = enc.encode()
        Assert.assertNotNull(encoded)
        val bmpDecoded = JP2Decoder(encoded).decode()
        Assert.assertNotNull(bmpDecoded)

        util!!.assertBitmapsEqual(bmp, bmpDecoded!!)
    }

    /*
      Encode several normal images, decode them, compare them to the originals.
      Several small sizes are tested to make sure the library correctly sets the Number of Resolutions parameter.
      (OpenJPEG uses 6 resolutions by default, but it throws an error if the image is smaller than 32x32. For
      such small images the number of resolutions must be smaller.)
     */
    @Test
    @Throws(Exception::class)
    fun testEncodeSimple() {
        val images: Array<String?> = arrayOf(
            "lena.png",
            "lena-grey.png",
            "1x1.png",
            "2x2.png",
            "3x3.png",
            "32x15.png",
            "32x16.png"
        )

        for (i in images.indices) {
            val expected = util!!.loadAssetBitmap(images[i]!!)

            //test encode to file
            val outFile = File(ctx!!.filesDir, "tmp.tmp")
            Assert.assertTrue(JP2Encoder(expected).encode(outFile.path))
            var encoded = util!!.loadFile(outFile.path)
            Assert.assertNotNull(encoded)
            Assert.assertTrue(isJPEG2000(encoded))
            var decoded = JP2Decoder(encoded).decode()
            Assert.assertFalse(decoded!!.hasAlpha())
            util!!.assertBitmapsEqual(expected, decoded)
            outFile.delete()

            //test encode to stream
            val out = ByteArrayOutputStream()
            Assert.assertTrue(JP2Encoder(expected).encode(out) > 0)
            encoded = out.toByteArray()
            Assert.assertNotNull(encoded)
            Assert.assertTrue(isJPEG2000(encoded))
            decoded = JP2Decoder(encoded).decode()
            Assert.assertFalse(decoded!!.hasAlpha())
            util!!.assertBitmapsEqual(expected, decoded)

            //test encode to byte array
            encoded = JP2Encoder(expected).encode()
            Assert.assertNotNull(encoded)
            Assert.assertTrue(isJPEG2000(encoded))
            decoded = JP2Decoder(encoded).decode()
            Assert.assertFalse(decoded!!.hasAlpha())
            util!!.assertBitmapsEqual(expected, decoded)
        }
    }

    /*
      Encode transparent image (full-RGB and greyscale), decode it compare to the original.
      (The original is loaded from raw bytes to avoid rounding errors introduced by Android
      when decoding a transparent PNG due to pre-multiplied color values and other such nonsense.)
     */
    @Test
    @Throws(Exception::class)
    fun testEncodeTransparent() {
        val images: Array<String?> = arrayOf("transparent.raw", "transparent-grey.raw")
        val width = 175
        val height = 65

        for (i in images.indices) {
            val expected = util!!.loadAssetRawBitmap(images[i]!!, width, height)

            //test encode to file
            val outFile = File(ctx!!.filesDir, "tmp.tmp")
            Assert.assertTrue(JP2Encoder(expected).encode(outFile.path))
            var encoded = util!!.loadFile(outFile.path)
            Assert.assertNotNull(encoded)
            Assert.assertTrue(isJPEG2000(encoded))
            var decoded = JP2Decoder(encoded).decode()
            Assert.assertTrue(decoded!!.hasAlpha())
            util!!.assertBitmapsEqual(expected, decoded)
            outFile.delete()

            //test encode to stream
            val out = ByteArrayOutputStream()
            Assert.assertTrue(JP2Encoder(expected).encode(out) > 0)
            encoded = out.toByteArray()
            Assert.assertNotNull(encoded)
            Assert.assertTrue(isJPEG2000(encoded))
            decoded = JP2Decoder(encoded).decode()
            Assert.assertTrue(decoded!!.hasAlpha())
            util!!.assertBitmapsEqual(expected, decoded)

            //test encode to byte array
            encoded = JP2Encoder(expected).encode()
            Assert.assertNotNull(encoded)
            Assert.assertTrue(isJPEG2000(encoded))
            decoded = JP2Decoder(encoded).decode()
            Assert.assertTrue(decoded!!.hasAlpha())
            util!!.assertBitmapsEqual(expected, decoded)
        }
    }

    /*
        Check that the requested output format is produced.
     */
    @Test
    @Throws(Exception::class)
    fun testEncodeFormat() {
        val expected = util!!.loadAssetBitmap("lena.png")
        //test jp2 format
        var data = JP2Encoder(expected).setOutputFormat(JP2Encoder.OutputFormat.JP2).encode()
        Assert.assertTrue(
            "JP2 header not found",
            startsWith(data!!, JP2_MAGIC) || startsWith(
                data,
                JP2_RFC3745_MAGIC
            )
        )
        var decoded = JP2Decoder(data).decode()
        util!!.assertBitmapsEqual(expected, decoded!!)

        //test j2k format
        data = JP2Encoder(expected).setOutputFormat(JP2Encoder.OutputFormat.J2K).encode()
        Assert.assertTrue(
            "JP2 header not found",
            startsWith(data!!, J2K_CODESTREAM_MAGIC)
        )
        decoded = JP2Decoder(data).decode()
        util!!.assertBitmapsEqual(expected, decoded!!)
    }

    /*
        Check that an exception is thrown when bad parameters are used.
     */
    @Test
    @Throws(Exception::class)
    fun testEncodeParamsErrors() {
        val expected = util!!.loadAssetBitmap("lena.png")

        try {
            //zero is not allowed
            JP2Encoder(expected).setCompressionRatio(1f, 0f, 3f)
            Assert.fail("Exception should have been thrown")
        } catch (_: IllegalArgumentException) {
        }

        try {
            //negative numbers not allowed is not allowed
            JP2Encoder(expected).setCompressionRatio(-1f, 2f, 3f)
            Assert.fail("Exception should have been thrown")
        } catch (_: IllegalArgumentException) {
        }

        try {
            //negative numbers not allowed
            JP2Encoder(expected).setVisualQuality(30f, 20f, -10f)
            Assert.fail("Exception should have been thrown")
        } catch (_: IllegalArgumentException) {
        }

        var enc = JP2Encoder(expected).setCompressionRatio(20f, 10f, 1f)
        try {
            enc.setVisualQuality(10f, 20f, 30f)
            Assert.fail("Exception should have been thrown")
        } catch (_: IllegalArgumentException) {
        }

        enc = JP2Encoder(expected).setVisualQuality(10f, 20f, 30f)
        try {
            enc.setCompressionRatio(20f, 10f, 1f)
            Assert.fail("Exception should have been thrown")
        } catch (_: IllegalArgumentException) {
        }

        for (resolutions in intArrayOf(0, 32)) {
            try {
                JP2Encoder(expected).setNumResolutions(resolutions)
                Assert.fail("Exception should have been thrown")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    /*
       Check that correct number of resolutions is accepted and too high number of resolutions is rejected
       for multiple image sizes.
     */
    @Test
    fun testNumResolutions() {
        //triplets - (width, height, maxResolutions)
        val testData = arrayOf<IntArray?>(
            intArrayOf(1, 1, 1),
            intArrayOf(2, 1, 1),
            intArrayOf(2, 2, 2),
            intArrayOf(5, 1, 1),
            intArrayOf(5, 3, 2),
            intArrayOf(5, 4, 3),
            intArrayOf(63, 63, 6),
            intArrayOf(64, 63, 6),
            intArrayOf(64, 64, 7),
            intArrayOf(1023, 1024, 10),
            intArrayOf(1024, 1024, 11),
        )

        for (data in testData) {
            val width = data!![0]
            val height = data[1]
            val maxResolutions = data[2]
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
            val enc = JP2Encoder(bmp)
            for (res in 1..maxResolutions) {
                try {
                    enc.setNumResolutions(res)
                } catch (e: IllegalArgumentException) {
                    e.printStackTrace()
                    Assert.fail(
                        String.format(
                            "%d should be an acceptable number of resolutions for image size %d x %d, but an exception was thrown",
                            res,
                            width,
                            height
                        )
                    )
                }
            }
            for (res in maxResolutions + 1..31) {
                try {
                    enc.setNumResolutions(res)
                    Assert.fail(
                        String.format(
                            "%d is not an acceptable number of resolutions for image size %d x %d. An exception should have been thrown",
                            res,
                            width,
                            height
                        )
                    )
                } catch (_: IllegalArgumentException) {
                }
            }
        }
    }

    /*
      Test that the visual quality setting is working as it should.
     */
    @Test
    @Throws(Exception::class)
    fun testQuality() {
        val orig = util!!.loadAssetBitmap("lena.png")

        //start with lossless image
        var encoded = JP2Encoder(orig).setVisualQuality(0f).encode()
        Assert.assertNotNull(encoded)
        var lastSize = encoded!!.size
        var decoded = JP2Decoder(encoded).decode()
        Assert.assertNotNull(decoded)
        util!!.assertBitmapsEqual(orig, decoded!!)

        //continue with lossy compression - decrease quality and check that PSNR is about right and file size decrease as well.
        //we only use "reasonable" PSNR values here; PSNR > 70 is pretty much unattainable and PSNR < 20 is mostly just garbage
        for (quality in floatArrayOf(70f, 60f, 50f, 40f, 30f, 20f)) {
            encoded = JP2Encoder(orig).setVisualQuality(quality).encode()
            Assert.assertNotNull(encoded)
            Assert.assertTrue(
                "Lower quality ($quality) should lead to smaller file",
                encoded!!.size < lastSize
            )
            lastSize = encoded.size

            decoded = JP2Decoder(encoded).decode()
            Assert.assertNotNull(decoded)
            assertPsnr(quality.toDouble(), orig, decoded!!)
        }

        //test multiple qualities

        //use qualities in an unsorted order (the library should sort it)
        val qualities = floatArrayOf(30f, 50f, 20f)
        encoded = JP2Encoder(orig).setVisualQuality(*qualities).encode()
        val header = JP2Decoder(encoded).readHeader()
        Assert.assertEquals(
            "wrong number of quality layers",
            header!!.numQualityLayers.toLong(),
            qualities.size.toLong()
        )

        val sortedQualities = qualities.copyOf(qualities.size)
        Arrays.sort(sortedQualities)
        //test partial decoding
        for (i in qualities.indices) {
            decoded = JP2Decoder(encoded).setLayersToDecode(i + 1).decode()
            assertPsnr(sortedQualities[i].toDouble(), orig, decoded!!)
        }
        //test full decoding
        //all layers (implicit)
        decoded = JP2Decoder(encoded).decode()
        assertPsnr(sortedQualities[sortedQualities.size - 1].toDouble(), orig, decoded!!)
        //all layers (explicit)
        decoded = JP2Decoder(encoded).setLayersToDecode(0).decode()
        assertPsnr(sortedQualities[sortedQualities.size - 1].toDouble(), orig, decoded!!)
        //too high number of layers
        decoded = JP2Decoder(encoded).setLayersToDecode(qualities.size + 1).decode()
        assertPsnr(sortedQualities[sortedQualities.size - 1].toDouble(), orig, decoded!!)
    }

    private fun assertPsnr(expectedPsnr: Double, orig: Bitmap, decoded: Bitmap) {
        assertPsnr(null, expectedPsnr, orig, decoded)
    }

    private fun assertPsnr(message: String?, expectedPsnr: Double, orig: Bitmap, decoded: Bitmap) {
        assertPsnr(message, expectedPsnr, 0.1, orig, decoded)
    }

    private fun assertPsnr(
        message: String?,
        expectedPsnr: Double,
        maxDiff: Double,
        orig: Bitmap,
        decoded: Bitmap
    ) {
        val psnr = psnr(orig, decoded)
        val psnrDiff = abs(1 - psnr / expectedPsnr)
        Assert.assertTrue(
            (if (message == null) "" else "$message; ") + String.format(
                "expected PSNR %f, actual PSNR %f (difference %d %%; max allowed difference is %d %%)",
                expectedPsnr,
                psnr,
                (psnrDiff * 100).toInt(),
                (maxDiff * 100).toInt()
            ), psnrDiff <= maxDiff
        )
    }

    /*
      Test that the compression ratio setting is working as it should. First check, that the lossless setting produces
      identical image, then go to lossy compression, increase the ratio and check that the file size is close to the
      expected value.
     */
    @Test
    @Throws(Exception::class)
    fun testRatio() {
        val orig = util!!.loadAssetBitmap("lena.png")

        //start with lossless image
        var encoded = JP2Encoder(orig).setCompressionRatio(1f).encode()
        Assert.assertNotNull(encoded)
        var decoded = JP2Decoder(encoded).decode()
        Assert.assertNotNull(decoded)
        util!!.assertBitmapsEqual(orig, decoded!!)

        //continue with lossy compression - increase compression ratio and check the file size is as expected
        val allowedSizeDifference =
            5 //we allow maximum 5 % difference between expected and actual size
        for (ratio in intArrayOf(2, 10, 20, 50, 100, 1000, 2000)) {
            //use J2K file format as it has less overhead to throw off our computation
            encoded = JP2Encoder(orig).setOutputFormat(JP2Encoder.OutputFormat.J2K)
                .setCompressionRatio(ratio.toFloat()).encode()
            Assert.assertNotNull(encoded)
            //original image data size is width x height x number_of_bytes_per_pixel (3)
            val expectedSize = (orig.getWidth() * orig.getHeight() * 3) / ratio
            val sizeDiff =
                abs(1 - (encoded!!.size * 1.0 / expectedSize)) * 100 //size difference in percents
            Assert.assertTrue(
                String.format(
                    "Expected approximate size %d bytes for ratio %d, but got %d bytes, which is %.2f %% off. "
                            + "Maximum allowed difference is %d %%.",
                    expectedSize,
                    ratio,
                    encoded.size,
                    sizeDiff,
                    allowedSizeDifference
                ),
                sizeDiff <= allowedSizeDifference
            )

            //test that the result can actually be decoded
            decoded = JP2Decoder(encoded).decode()
            Assert.assertNotNull(decoded)
        }

        //test multiple ratios (check that partial decoding of multiple quality layers yields the same psnr as a single-layer image)
        val ratios = floatArrayOf(30f, 20f, 40f)
        encoded = JP2Encoder(orig).setCompressionRatio(*ratios).encode()

        //sort the ratios
        val sortedRatios = ratios.copyOf(ratios.size)
        //reverse the sorted array (ratios must be sorted in descending order)
        for (i in 0..<sortedRatios.size / 2) {
            val tmp = sortedRatios[i]
            sortedRatios[i] = sortedRatios[sortedRatios.size - i - 1]
            sortedRatios[sortedRatios.size - i - 1] = tmp
        }

        //we get the psnr values for each ratio - we encode a single quality layer image and get its PSNR
        val expectedPsnr = DoubleArray(ratios.size)
        for (i in ratios.indices) {
            val tmp = JP2Encoder(orig).setCompressionRatio(sortedRatios[i]).encode()
            expectedPsnr[i] = psnr(JP2Decoder(tmp).decode()!!, orig)
        }

        //now check that we get the correct psnr in partial decode
        val dec = JP2Decoder(encoded)
        for (i in sortedRatios.indices) {
            decoded = dec.setLayersToDecode(i + 1).decode()
            assertPsnr(expectedPsnr[i], orig, decoded!!)
        }
        //test full decoding
        //all layers (implicit)
        decoded = JP2Decoder(encoded).decode()
        assertPsnr(expectedPsnr[sortedRatios.size - 1], orig, decoded!!)
        //all layers (explicit)
        decoded = JP2Decoder(encoded).setLayersToDecode(0).decode()
        assertPsnr(expectedPsnr[sortedRatios.size - 1], orig, decoded!!)
        //too high number of layers
        decoded = JP2Decoder(encoded).setLayersToDecode(sortedRatios.size + 1).decode()
        assertPsnr(expectedPsnr[sortedRatios.size - 1], orig, decoded!!)
    }

    /* test that multiple resolutions is correctly encoded */
    @Test
    @Throws(Exception::class)
    fun testResolutions() {
        val bmp = Bitmap.createBitmap(551, 645, Bitmap.Config.ARGB_8888)
        val resCount = intArrayOf(1, 3, 7, 9)

        for (numResolutions in resCount) {
            val encoded = JP2Encoder(bmp).setNumResolutions(numResolutions).encode()
            var expectedWidth = bmp.getWidth()
            var expectedHeight = bmp.getHeight()
            for (i in 0..<numResolutions) {
                if (i > 0) {
                    expectedWidth = (expectedWidth + 1) shr 1
                    expectedHeight = (expectedHeight + 1) shr 1
                }
                val decoded = JP2Decoder(encoded).setSkipResolutions(i).decode()
                Assert.assertNotNull(decoded)
                Assert.assertEquals(
                    "Wrong width",
                    expectedWidth.toLong(),
                    decoded!!.getWidth().toLong()
                )
                Assert.assertEquals(
                    "Wrong height",
                    expectedHeight.toLong(),
                    decoded.getHeight().toLong()
                )
            }
            //test too high number of resolutions to skip - should decode the lowest available resolution
            val decoded = JP2Decoder(encoded).setSkipResolutions(numResolutions).decode()
            Assert.assertNotNull(decoded)
            Assert.assertEquals(
                "Wrong width",
                expectedWidth.toLong(),
                decoded!!.getWidth().toLong()
            )
            Assert.assertEquals(
                "Wrong height",
                expectedHeight.toLong(),
                decoded.getHeight().toLong()
            )
        }
    }

    /* Test that multiple resolutions and quality layers combine well - decode all quality layers for each resolution,
       make sure that the quality is as expected (compute PSNR compared to a lossless compression at the same resolution).
     */
    @Test
    @Throws(Exception::class)
    fun testQualityLayersAndResolutions() {
        val orig = util!!.loadAssetBitmap("encodeTest.png")
        val qualities = floatArrayOf(20f, 30f, 40f)
        val resCount = 3
        val enc = JP2Encoder(orig).setVisualQuality(*qualities).setNumResolutions(resCount)
        val encoded = enc.encode()
        //test that encoding into a file produces the same result
        val encodedFile = File(ctx!!.filesDir, "test.jp2")
        Assert.assertTrue("encoding into file failed", enc.encode(encodedFile.path))
        Assert.assertArrayEquals(
            "encoding into file and byte array produced different data",
            encoded,
            util!!.loadFile(encodedFile.path)
        )
        encodedFile.delete()

        //encode lossless - as reference -  with the same number of resolutions
        val encodedLossless = JP2Encoder(orig).setNumResolutions(resCount).encode()

        for (skipRes in 0..<resCount) {
            val origResized = JP2Decoder(encodedLossless).setSkipResolutions(skipRes).decode()
            for (numLayers in qualities.indices) {
                val decoded =
                    JP2Decoder(encoded).setSkipResolutions(skipRes).setLayersToDecode(numLayers + 1)
                        .decode()
                //We allow bigger PSNR differences in lower resolutions because OpenJPEG doesn't hit the target quality so precisely there.
                //This value is good for the selected image and quality values - in case either is changed, this might have to be tweaked.
                val maxPsnrDiff = 0.1 * (skipRes + 1)
                assertPsnr(
                    String.format(
                        "bad PSNR for skipRes = %d, numLayers = %d",
                        skipRes,
                        numLayers
                    ), qualities[numLayers].toDouble(), maxPsnrDiff, origResized!!, decoded!!
                )
            }
        }
    }

    private fun psnr(bmp1: Bitmap, bmp2: Bitmap): Double {
        Assert.assertEquals(
            "bitmaps have different width",
            bmp1.getWidth().toLong(),
            bmp2.getWidth().toLong()
        )
        Assert.assertEquals(
            "bitmaps have different height",
            bmp1.getHeight().toLong(),
            bmp2.getHeight().toLong()
        )
        val pixels1 = IntArray(bmp1.getWidth() * bmp1.getHeight())
        val pixels2 = IntArray(bmp2.getWidth() * bmp2.getHeight())
        bmp1.getPixels(pixels1, 0, bmp1.getWidth(), 0, 0, bmp1.getWidth(), bmp1.getHeight())
        bmp2.getPixels(pixels2, 0, bmp2.getWidth(), 0, 0, bmp2.getWidth(), bmp2.getHeight())
        return psnr(pixels1, pixels2)
    }

    private fun psnr(pixels1: IntArray, pixels2: IntArray): Double {
        val mse = meanSquareError(pixels1, pixels2)
        return 20 * log10(255.0) - 10 * log10(mse)
    }

    private fun meanSquareError(pixels1: IntArray, pixels2: IntArray): Double {
        var acc: Long = 0
        for (i in pixels1.indices) {
            //we compare the differences only in the R,G,B channels, we ignore the alpha
            var offset = 8
            while (offset < 32) {
                val diff = ((pixels1[i] shr offset) and 0xFF) - ((pixels2[i] shr offset) and 0xFF)
                acc += (diff * diff).toLong()
                offset += 8
            }
        }
        return acc * 1.0 / (pixels1.size * 3) //3 channels
    }


    @Test
    @Throws(Throwable::class)
    fun testEncodeMultithreaded() {
        //test encoding in multiple (4) threads.
        //We load 4 different images, encode them repeatedly in 4 threads and check that we
        //always get the expected result.
        val t1 = EncoderThread("lena.png")
        val t2 = EncoderThread("lena-rotated90.png")
        val t3 = EncoderThread("lena-rotated180.png")
        val t4 = EncoderThread("lena-rotated270.png")

        t1.start()
        t2.start()
        t3.start()
        t4.start()

        while (!t1.finished || !t2.finished || !t3.finished || !t4.finished) {
            t1.checkError()
            t2.checkError()
            t3.checkError()
            t4.checkError()
            try {
                Thread.sleep(500)
            } catch (_: InterruptedException) {
            }
        }
        t1.checkError()
        t2.checkError()
        t3.checkError()
        t4.checkError()
    }

    internal inner class EncoderThread(var pngFile: String) : Thread() {
        var finished: Boolean = false
        var error: Throwable? = null
        var expected: Bitmap = util!!.loadAssetBitmap(pngFile)

        override fun run() {
            try {
                for (i in 0..<5) {
                    //test byte array
                    var encoded = JP2Encoder(expected).encode()
                    var decoded = JP2Decoder(encoded).decode()
                    util!!.assertBitmapsEqual(
                        "encoded $pngFile is different the original",
                        expected,
                        decoded!!
                    )

                    //test encode to file
                    val outFile = File.createTempFile("testjp2", "tmp", ctx!!.filesDir)
                    Assert.assertTrue(JP2Encoder(expected).encode(outFile.path))
                    encoded = util!!.loadFile(outFile.path)
                    decoded = JP2Decoder(encoded).decode()
                    util!!.assertBitmapsEqual(
                        "encoded $pngFile is different the original",
                        expected,
                        decoded!!
                    )
                    outFile.delete()

                    //test encode into stream
                    val out = ByteArrayOutputStream()
                    Assert.assertTrue(JP2Encoder(expected).encode(out) > 0)
                    decoded = JP2Decoder(out.toByteArray()).decode()
                    util!!.assertBitmapsEqual(
                        "encoded $pngFile is different the original",
                        expected,
                        decoded!!
                    )
                }
            } catch (e: Throwable) {
                error = e
            }
            finished = true
        }

        @Throws(Throwable::class)
        fun checkError() {
            if (error != null) throw error as Throwable
        }
    }

    companion object {
        private val JP2_RFC3745_MAGIC = byteArrayOf(
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x0c.toByte(),
            0x6a.toByte(),
            0x50.toByte(),
            0x20.toByte(),
            0x20.toByte(),
            0x0d.toByte(),
            0x0a.toByte(),
            0x87.toByte(),
            0x0a.toByte()
        )
        private val JP2_MAGIC =
            byteArrayOf(0x0d.toByte(), 0x0a.toByte(), 0x87.toByte(), 0x0a.toByte())
        private val J2K_CODESTREAM_MAGIC =
            byteArrayOf(0xff.toByte(), 0x4f.toByte(), 0xff.toByte(), 0x51.toByte())

        //does array1 start with contents of array2?
        private fun startsWith(array1: ByteArray, array2: ByteArray): Boolean {
            for (i in array2.indices) {
                if (array1[i] != array2[i]) return false
            }
            return true
        }
    }
}
