package com.yamibo.pocket300.data.download

import android.graphics.BitmapFactory
import java.io.File

/**
 * Uses the device decoder in bounds-only mode so a download is not committed when the current
 * Android version cannot parse the image into readable dimensions.
 */
internal object AndroidPostDownloadImageDecoderValidator : PostDownloadImageDecoderValidator {
    override fun canDecode(file: File): Boolean {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth > 0 &&
            options.outHeight > 0 &&
            !options.outMimeType.isNullOrBlank()
    }
}
