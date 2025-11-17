package com.gemalto.jp2.test

import android.graphics.Bitmap
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.gemalto.jp2.JP2Decoder
import java.io.Closeable
import java.io.IOException
import java.io.InputStream

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val imgView = findViewById<ImageView>(R.id.image)
        imgView.getViewTreeObserver().addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                //we want to decode the JP2 only when the layout is created and we know the ImageView size
                imgView.getViewTreeObserver().removeGlobalOnLayoutListener(this)
                DecodeJp2AsyncTask(imgView).execute()
            }
        })
    }

    private fun close(obj: Closeable?) {
        try {
            obj?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }


    /**
     * This is an example of how to take advantage of the [JP2Decoder.setSkipResolutions] method in a multi-resolution JP2 image.
     * We take the size of the ImageView component, the resolution of the image, the number of available resolutions, and we determine how many
     * of them we can skip without losing any detail.
     *
     * We know that each successive JP2 resolution is half of the previous resolution. So if the first (highest) resolution is 4000x3000, then
     * the next resolution (if present) will be 2000x1500, the next one 1000x750, and so on.
     *
     * Therefore if the ImageView size is 1800x1800 for example, we know that we can skip one resolution. (The 2000x1500 version is bigger - at least
     * in one dimension - than 1800x1800. The 1000x750 is smaller and we would lose image details.)
     */
    private inner class DecodeJp2AsyncTask(private val view: ImageView) :
        AsyncTask<Void?, Void?, Bitmap?>() {
        //get the size of the ImageView
        private val width: Int = view.width
        private val height: Int = view.height

        @Deprecated("Deprecated in Java")
        override fun doInBackground(vararg voids: Void?): Bitmap? {
            Log.d(TAG, String.format("View resolution: %d x %d", width, height))
            var ret: Bitmap? = null
            var `in`: InputStream? = null
            try {
                `in` = assets.open("balloon.jp2")

                //create a new JP2 decoder object
                val decoder = JP2Decoder(`in`)

                //read image information only, but don't decode the actual image
                val header = decoder.readHeader()

                //get the size of the image
                var imgWidth = header!!.width
                var imgHeight = header.height
                Log.d(TAG, String.format("JP2 resolution: %d x %d", imgWidth, imgHeight))

                //we halve the resolution until we go under the ImageView size or until we run out of the available JP2 image resolutions
                var skipResolutions = 1
                while (skipResolutions < header.numResolutions) {
                    imgWidth = imgWidth shr 1
                    imgHeight = imgHeight shr 1
                    if (imgWidth < width && imgHeight < height) break
                    else skipResolutions++
                }

                //we break the loop when skipResolutions goes over the correct value
                skipResolutions--
                Log.d(TAG, String.format("Skipping %d resolutions", skipResolutions))

                //set the number of resolutions to skip
                if (skipResolutions > 0) decoder.setSkipResolutions(skipResolutions)

                //decode the image
                ret = decoder.decode()
                Log.d(
                    TAG,
                    String.format(
                        "Decoded at resolution: %d x %d",
                        ret!!.getWidth(),
                        ret.getHeight()
                    )
                )
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                close(`in`)
            }
            return ret
        }

        @Deprecated("Deprecated in Java")
        override fun onPostExecute(bitmap: Bitmap?) {
            if (bitmap != null) {
                view.setImageBitmap(bitmap)
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
