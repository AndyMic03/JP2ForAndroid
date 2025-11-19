package com.andymic.jpeg2k.test

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andymic.jpeg2k.JP2Decoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.IOException
import java.io.InputStream

class MainViewModel : ViewModel() {

    private val _decodedBitmap = MutableLiveData<Bitmap?>()
    val decodedBitmap: LiveData<Bitmap?> = _decodedBitmap

    private val _logMessages = MutableLiveData<String>()
    val logMessages: LiveData<String> = _logMessages

    companion object {
        private const val TAG = "MainViewModel"
    }

    /**
     * This function now runs the decoding logic inside a coroutine.
     */
    fun decodeJp2Image(context: Context, viewWidth: Int, viewHeight: Int) {
        // Launch a coroutine in the ViewModel's scope.
        // This coroutine will be automatically cancelled if the ViewModel is cleared.
        viewModelScope.launch {
            _logMessages.value = "View resolution: $viewWidth x $viewHeight"

            // Run the blocking (I/O, decoding) code on the IO dispatcher
            val bitmap = withContext(Dispatchers.IO) {
                var inputStream: InputStream? = null
                try {
                    inputStream = context.assets.open("balloon.jp2")

                    val decoder = JP2Decoder(inputStream)
                    val header = decoder.readHeader()

                    if (header == null) {
                        _logMessages.postValue("Error: Could not read JP2 header.")
                        return@withContext null
                    }

                    var imgWidth = header.width
                    var imgHeight = header.height
                    _logMessages.postValue("JP2 resolution: $imgWidth x $imgHeight")

                    var skipResolutions = 1
                    while (skipResolutions < header.numResolutions) {
                        imgWidth = imgWidth shr 1
                        imgHeight = imgHeight shr 1
                        if (imgWidth < viewWidth && imgHeight < viewHeight) break
                        else skipResolutions++
                    }
                    skipResolutions--
                    _logMessages.postValue("Skipping $skipResolutions resolutions")

                    if (skipResolutions > 0) {
                        decoder.setSkipResolutions(skipResolutions)
                    }

                    // Decode the image
                    val result = decoder.decode()
                    _logMessages.postValue("Decoded at resolution: ${result?.width} x ${result?.height}")

                    result // Return the bitmap
                } catch (e: IOException) {
                    e.printStackTrace()
                    _logMessages.postValue("Error: ${e.message}")
                    null
                } finally {
                    close(inputStream)
                }
            }

            // Post the final bitmap to LiveData, which will update the UI
            _decodedBitmap.value = bitmap
        }
    }

    private fun close(obj: Closeable?) {
        try {
            obj?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}