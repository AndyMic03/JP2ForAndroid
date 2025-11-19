package com.andymic.jpeg2k

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.andymic.jpeg2k.JP2Decoder.Companion.isJPEG2000
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStream

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see [Testing documentation](http://d.android.com/tools/testing)
 */
@RunWith(AndroidJUnit4::class)
class TestJp2Decoder {
    // Context of the app under test.
    private lateinit var ctx: Context
    private lateinit var util: Util

    @Before
    fun init() {
        ctx = ApplicationProvider.getApplicationContext()
        util = Util(ctx)
    }

    /*
     * Test that the JP2Decoder.isJPEG2000() method works properly for both valid and invalid data.
     */
    @Test
    @Throws(Exception::class)
    fun testIsJPEG2000() {

        var data = util.loadAssetFile("lena.jp2")
        Assert.assertTrue("jp2 file not detected as jpeg 2000", isJPEG2000(data))
        data = util.loadAssetFile("lena.j2k")
        Assert.assertTrue("j2k file not detected as jpeg 2000", isJPEG2000(data))
        data = util.loadAssetFile("lena.png")
        Assert.assertFalse("png file detected as jpeg 2000", isJPEG2000(data))
        data = null
        Assert.assertFalse("null data detected as jpeg 2000", isJPEG2000(data))
        data = ByteArray(0)
        Assert.assertFalse("empty data detected as jpeg 2000", isJPEG2000(data))
        data = ByteArray(1)
        Assert.assertFalse("short invalid data detected as jpeg 2000", isJPEG2000(data))
    }

    /*
     * Decode a JP2 image with all RGB colors - compare with original in PNG
     */
    @Test
    @Throws(Exception::class)
    fun testDecodeAllColorsLossless() {
        val encoded = util.loadAssetFile("fullrgb.jp2")
        val bmpExpected = util.loadAssetBitmap("fullrgb.png")
        val bmpDecoded = JP2Decoder(encoded).decode()
        Assert.assertNotNull(bmpDecoded)

        util.assertBitmapsEqual(bmpExpected, bmpDecoded!!)
    }

    /*
      Decode a normal image, a greyscale image, a tiny image, in JP2 and J2K format, compare them with the expected results.
     */
    @Test
    @Throws(Exception::class)
    fun testDecodeSimple() {
        val jp2Files =
            arrayOf("lena.jp2", "lena.j2k", "1x1.jp2", "1x1.j2k", "lena-grey.jp2")
        val expectedFiles =
            arrayOf("lena.png", "lena.png", "1x1.png", "1x1.png", "lena-grey.png")


        for (i in jp2Files.indices) {
            val expected = util.loadAssetBitmap(expectedFiles[i])

            //test decode from file
            val outFile = util.createFile(util.loadAssetFile(jp2Files[i]))

            var decoded = JP2Decoder(outFile.path).decode()
            Assert.assertFalse(decoded!!.hasAlpha())

            util.assertBitmapsEqual(expected, decoded)
            outFile.delete()

            util.openAssetStream(jp2Files[i]).use { `in` ->
                decoded = JP2Decoder(`in`).decode()
                util.assertBitmapsEqual(expected, decoded!!)
            }
            //test decode from byte array
            val data = util.loadAssetFile(jp2Files[i])
            decoded = JP2Decoder(data).decode()
            util.assertBitmapsEqual(expected, decoded!!)
        }
    }

    /*
      Decode a transparent RGB image and a transparent greyscale image, compare them with the expected results.
      (Note: we don't store the expected results as PNG in this test, because Android stores transparent bitmaps
       with color components pre-multiplied with alpha. Due to different rounding mechanisms in OpenJPEG and Android,
       and possibly different Bitmap configs being used, this can lead to minor differences in pixel values when util.loading
       the same transparent image from PNG and JP2. So we store the expected results as RAW and util.load them using
       the same bitmap config as our library uses; this way we ensure that the rounding errors in both decoded and
       expected bitmap will be the same.)
     */
    @Test
    @Throws(Exception::class)
    fun testDecodeTransparent() {
        //test RGBA image
        var decoded = JP2Decoder(util.loadAssetFile("transparent.jp2")).decode()
        Assert.assertTrue(decoded!!.hasAlpha())
        var expected = util.loadAssetRawBitmap(
            "transparent.raw",
            decoded.getWidth(),
            decoded.getHeight()
        )
        util.assertBitmapsEqual(expected, decoded)

        //test greyscale image with alpha
        decoded = JP2Decoder(util.loadAssetFile("transparent-grey.jp2")).decode()
        Assert.assertTrue(decoded!!.hasAlpha())
        expected = util.loadAssetRawBitmap(
            "transparent-grey.raw",
            decoded.getWidth(),
            decoded.getHeight()
        )
        util.assertBitmapsEqual(expected, decoded)
    }

    /*
      Test decoder wrong input.
     */
    @Test
    @Throws(Exception::class)
    fun testDecodeError() {
        Assert.assertNull(JP2Decoder(util.loadAssetFile("lena.png")).decode())
        Assert.assertNull(JP2Decoder(null as ByteArray?).decode())
        Assert.assertNull(JP2Decoder(ByteArray(0)).decode())
        Assert.assertNull(JP2Decoder(ByteArray(1)).decode())
        Assert.assertNull(JP2Decoder(ByteArray(2)).decode())
        Assert.assertNull(JP2Decoder(ByteArray(16000000)).decode())
        Assert.assertNull(JP2Decoder(null as InputStream?).decode())
        Assert.assertNull(JP2Decoder(null as String?).decode())

        //decode from wrong file
        val outFile = util.createFile(util.loadAssetFile("lena.png"))
        Assert.assertNull(JP2Decoder(outFile.path).decode())
        outFile.delete()
        //decode from non-existent file
        Assert.assertNull(JP2Decoder(outFile.path).decode())

        //incomplete JPEG 2000 file
        val data = util.loadAssetFile("lena.jp2")
        Assert.assertNull(JP2Decoder(data!!.copyOf(data.size / 2)).decode())
    }

    @Test
    @Throws(Throwable::class)
    fun testSubsampling() {
        val dataList: MutableList<ExpectedHeader> = ArrayList()
        dataList.add(ExpectedHeader("subsampling_1.jp2", 1280, 1024, false, 6, 6))
        dataList.add(ExpectedHeader("subsampling_2.jp2", 640, 512, false, 6, 5))
        for (expected in dataList) {
            val data = util.loadAssetFile(expected.file)
            val dec = JP2Decoder(data)
            val header = dec.readHeader()
            Assert.assertNotNull(expected.file + " header is null", header)
            Assert.assertEquals(
                expected.file + " header, Wrong width",
                expected.width.toLong(),
                header!!.width.toLong()
            )
            Assert.assertEquals(
                expected.file + " header, Wrong height",
                expected.height.toLong(),
                header.height.toLong()
            )
            Assert.assertEquals(
                expected.file + " header, Wrong alpha",
                expected.hasAlpha,
                header.hasAlpha
            )
            Assert.assertEquals(
                expected.file + " header, Wrong number of resolutions",
                expected.numResolutions.toLong(),
                header.numResolutions.toLong()
            )
            Assert.assertEquals(
                expected.file + " header, Wrong number of quality layers",
                expected.numQualityLayers.toLong(),
                header.numQualityLayers.toLong()
            )

            val jp2Bitmap = dec.decode()
            val pngBitmap = util.loadAssetBitmap(expected.file.replace(".jp2", ".png"))
            util.assertBitmapsEqual(expected.file, pngBitmap, jp2Bitmap!!)
        }
    }

    /*
     * Test reading header information.
     */
    @Test
    @Throws(Throwable::class)
    fun testReadHeader() {
        val dataList: MutableList<ExpectedHeader> = ArrayList()
        dataList.add(ExpectedHeader("headerTest-r1-l1.jp2", 335, 151, false, 1, 1))
        dataList.add(ExpectedHeader("headerTest-r2-l3.j2k", 335, 151, false, 2, 3))
        dataList.add(ExpectedHeader("headerTest-r5-l1.j2k", 335, 151, false, 5, 1))
        dataList.add(ExpectedHeader("headerTest-r7-l5.jp2", 335, 151, false, 7, 5))
        dataList.add(ExpectedHeader("tiled-r6-l6.jp2", 2717, 3701, false, 6, 6))
        dataList.add(ExpectedHeader("tiled-r6-l1.j2k", 2717, 3701, false, 6, 1))
        dataList.add(ExpectedHeader("transparent.jp2", 175, 65, true, 6, 1))

        var header: JP2Decoder.Header?
        for (expected in dataList) {
            val data = util.loadAssetFile(expected.file)
            for (i in 0..2) {
                when (i) {
                    0 -> {
                        //read header from byte array
                        header = JP2Decoder(data).readHeader()
                    }

                    1 -> {
                        //read header from file
                        val f = util.createFile(data)
                        header = JP2Decoder(f.path).readHeader()
                        f.delete()
                    }

                    else -> {
                        //read header from input stream
                        header = JP2Decoder(util.openAssetStream(expected.file)).readHeader()
                    }
                }

                Assert.assertNotNull(expected.file + ", Header is null", header)
                Assert.assertEquals(
                    expected.file + ", Wrong width",
                    expected.width.toLong(),
                    header!!.width.toLong()
                )
                Assert.assertEquals(
                    expected.file + ", Wrong height",
                    expected.height.toLong(),
                    header.height.toLong()
                )
                Assert.assertEquals(
                    expected.file + ", Wrong alpha",
                    expected.hasAlpha,
                    header.hasAlpha
                )
                Assert.assertEquals(
                    expected.file + ", Wrong number of resolutions",
                    expected.numResolutions.toLong(),
                    header.numResolutions.toLong()
                )
                Assert.assertEquals(
                    expected.file + ", Wrong number of quality layers",
                    expected.numQualityLayers.toLong(),
                    header.numQualityLayers.toLong()
                )
            }
        }

        //test wrong data
        val data = util.loadAssetFile("lena.png")
        //byte array
        header = JP2Decoder(data).readHeader()
        Assert.assertNull(header)
        //file
        val f = util.createFile(data)
        header = JP2Decoder(data).readHeader()
        Assert.assertNull(header)
        f.delete()
        //non-existent file
        header = JP2Decoder(data).readHeader()
        Assert.assertNull(header)
        //input stream
        header = JP2Decoder(util.openAssetStream("lena.png")).readHeader()
        Assert.assertNull(header)
        //null input
        Assert.assertNull(JP2Decoder(null as ByteArray?).readHeader())
        Assert.assertNull(JP2Decoder(null as InputStream?).readHeader())
        Assert.assertNull(JP2Decoder(null as String?).readHeader())
    }

    /*
        Check that an exception is thrown when bad parameters are used.
     */
    @Test
    @Throws(Exception::class)
    fun testParamsErrors() {
        val dec = JP2Decoder(null as ByteArray?)
        try {
            dec.setLayersToDecode(-1)
            Assert.fail("Exception should have been thrown")
        } catch (_: IllegalArgumentException) {
        }
        try {
            dec.setLayersToDecode(Int.MIN_VALUE)
            Assert.fail("Exception should have been thrown")
        } catch (_: IllegalArgumentException) {
        }
        try {
            dec.setSkipResolutions(-1)
            Assert.fail("Exception should have been thrown")
        } catch (_: IllegalArgumentException) {
        }
        try {
            dec.setSkipResolutions(Int.MIN_VALUE)
            Assert.fail("Exception should have been thrown")
        } catch (_: IllegalArgumentException) {
        }
    }

    private class LRTestParams(var file: String?, var skipResolutions: Int?, var decodeLayers: Int?)

    @Test
    @Throws(Exception::class)
    fun testLayersAndResolutions() {
        val file = util.loadAssetFile("decodeTest.jp2") //7 resolutions, 5 quality layers
        val params: MutableList<LRTestParams> = ArrayList()
        params.add(LRTestParams("decodeTest-r3.png", 3, null))
        params.add(LRTestParams("decodeTest-r3.png", 3, 0))
        params.add(LRTestParams("decodeTest-r6.png", 6, null))
        params.add(LRTestParams("decodeTest-r6.png", 6, 0))
        params.add(LRTestParams("decodeTest-l1.png", 0, 1))
        params.add(LRTestParams("decodeTest-l1.png", null, 1))
        params.add(LRTestParams("decodeTest-l4.png", 0, 4))
        params.add(LRTestParams("decodeTest-l4.png", null, 4))
        params.add(LRTestParams("decodeTest-r1l1.png", 1, 1))
        params.add(LRTestParams("decodeTest-r1l4.png", 1, 4))
        params.add(LRTestParams("decodeTest-r2l5.png", 2, 5))

        for (param in params) {
            val expected = util.loadAssetBitmap(param.file!!)
            val dec = JP2Decoder(file)
            if (param.decodeLayers != null) dec.setLayersToDecode(param.decodeLayers!!)
            if (param.skipResolutions != null) dec.setSkipResolutions(param.skipResolutions!!)
            val decoded = dec.decode()
            util.assertBitmapsEqual("Error in " + param.file, expected, decoded!!)
        }
    }

    @Test
    @Throws(Throwable::class)
    fun testDecodeMultithreaded() {
        //test decoding in multiple (4) threads.
        //We load 4 different images, decode them repeatedly in 4 threads and check that we
        //always get the expected result.
        val t1 = DecoderThread("lena.jp2", "lena.png")
        val t2 = DecoderThread("lena-rotated90.jp2", "lena-rotated90.png")
        val t3 = DecoderThread("lena-rotated180.jp2", "lena-rotated180.png")
        val t4 = DecoderThread("lena-rotated270.jp2", "lena-rotated270.png")

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

    internal inner class DecoderThread(var jp2File: String?, var pngFile: String?) : Thread() {
        var finished: Boolean = false
        var error: Throwable? = null
        var expected: Bitmap = util.loadAssetBitmap(pngFile!!)
        var encoded: ByteArray? = util.loadAssetFile(jp2File!!)

        override fun run() {
            try {
                for (i in 0..<5) {
                    //test byte array
                    var decoded = JP2Decoder(encoded).decode()
                    util.assertBitmapsEqual(
                        "decoded $jp2File is different from $pngFile",
                        expected,
                        decoded!!
                    )

                    //test decode from file
                    val outFile = util.createFile(encoded)
                    decoded = JP2Decoder(outFile.path).decode()
                    util.assertBitmapsEqual(
                        "decoded $jp2File is different from $pngFile",
                        expected,
                        decoded!!
                    )
                    outFile.delete()

                    util.openAssetStream(jp2File!!).use { `in` ->
                        decoded = JP2Decoder(`in`).decode()
                        util.assertBitmapsEqual(
                            "decoded $jp2File is different from $pngFile",
                            expected,
                            decoded!!
                        )
                    }
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

    private class ExpectedHeader(
        var file: String, width: Int, height: Int, hasAlpha: Boolean, numResolutions: Int,
        numQualityLayers: Int
    ) : JP2Decoder.Header() {
        init {
            this.width = width
            this.height = height
            this.hasAlpha = hasAlpha
            this.numResolutions = numResolutions
            this.numQualityLayers = numQualityLayers
        }
    }
}
