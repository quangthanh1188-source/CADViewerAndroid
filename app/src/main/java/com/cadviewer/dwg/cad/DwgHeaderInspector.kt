package com.cadviewer.dwg.cad

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Đọc trực tiếp phần đầu tệp nhị phân .DWG bằng thuần Kotlin:
 * - Xác định chính xác phiên bản AutoCAD (R14 đến R2018+)
 * - Trích xuất ảnh xem trước (BMP/PNG) nhúng sẵn trong tệp DWG nếu có
 */
object DwgHeaderInspector {

    // Sentinel đánh dấu vùng ảnh preview của định dạng DWG
    private val PREVIEW_START_SENTINEL = byteArrayOf(
        0x1F, 0x25, 0x6D, 0x07, 0xD4.toByte(), 0x36, 0x28, 0x28,
        0x9D.toByte(), 0x57, 0xCA.toByte(), 0x3F, 0x9D.toByte(), 0x44, 0x10, 0x2B
    )

    private val PNG_MAGIC = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    fun inspect(file: File): DwgMetadata {
        val bytes = file.readBytes()
        val versionCode = if (bytes.size >= 6) {
            String(bytes, 0, 6, Charsets.US_ASCII)
        } else {
            "UNKNOWN"
        }

        val autoCadVersion = when (versionCode) {
            "AC1014" -> "AutoCAD R14"
            "AC1015" -> "AutoCAD 2000 / 2000i / 2002"
            "AC1018" -> "AutoCAD 2004 / 2005 / 2006"
            "AC1021" -> "AutoCAD 2007 / 2008 / 2009"
            "AC1024" -> "AutoCAD 2010 / 2011 / 2012"
            "AC1027" -> "AutoCAD 2013 / 2014 / 2015 / 2016 / 2017"
            "AC1032" -> "AutoCAD 2018 / 2019 / 2020 - 2026"
            else -> "Không nhận diện ($versionCode)"
        }

        val preview = extractPreviewBitmap(bytes)
        return DwgMetadata(
            versionCode = versionCode,
            autoCadVersion = autoCadVersion,
            fileSizeBytes = bytes.size.toLong(),
            previewBitmap = preview,
            parsedVectorNative = false
        )
    }

    private fun extractPreviewBitmap(data: ByteArray): Bitmap? {
        val sentinelIdx = indexOf(data, PREVIEW_START_SENTINEL)
        if (sentinelIdx < 0) return null

        // Tìm PNG nhúng trong vùng preview (AutoCAD 2013+ thường dùng PNG)
        val searchLimit = (sentinelIdx + 2_000_000).coerceAtMost(data.size)
        val pngIdx = indexOf(data, PNG_MAGIC, sentinelIdx, searchLimit)
        if (pngIdx > 0) {
            val maxLen = (searchLimit - pngIdx).coerceAtMost(1_500_000)
            BitmapFactory.decodeByteArray(data, pngIdx, maxLen)?.let { return it }
        }

        // Thử đọc DIB/BMP (AutoCAD 2000-2012)
        if (sentinelIdx + 30 < data.size) {
            val headerStart = sentinelIdx + 16
            val totalSize = readInt32LE(data, headerStart)
            val numImages = data[headerStart + 4].toInt() and 0xFF
            var cursor = headerStart + 5
            for (i in 0 until numImages.coerceAtMost(5)) {
                if (cursor + 9 > data.size) break
                val imgType = data[cursor].toInt() and 0xFF
                val imgStart = readInt32LE(data, cursor + 1)
                val imgSize = readInt32LE(data, cursor + 5)
                cursor += 9
                if (imgType == 2 && imgStart > 0 && imgSize > 40 && imgStart + imgSize <= data.size) {
                    val bmpBytes = wrapDibAsBmp(data, imgStart, imgSize)
                    BitmapFactory.decodeByteArray(bmpBytes, 0, bmpBytes.size)?.let { return it }
                } else if (imgType == 6 && imgStart > 0 && imgSize > 16 && imgStart + imgSize <= data.size) {
                    BitmapFactory.decodeByteArray(data, imgStart, imgSize)?.let { return it }
                }
            }
            if (totalSize > 0) {
                return null
            }
        }
        return null
    }

    private fun wrapDibAsBmp(data: ByteArray, offset: Int, length: Int): ByteArray {
        val fileSize = 14 + length
        val headerSize = readInt32LE(data, offset)
        val bitsPerPixel = (data[offset + 14].toInt() and 0xFF) or ((data[offset + 15].toInt() and 0xFF) shl 8)
        val colorCount = if (bitsPerPixel <= 8) (1 shl bitsPerPixel) else 0
        val pixelOffset = 14 + headerSize + colorCount * 4

        val out = ByteArray(fileSize)
        out[0] = 'B'.code.toByte()
        out[1] = 'M'.code.toByte()
        writeInt32LE(out, 2, fileSize)
        writeInt32LE(out, 10, pixelOffset)
        System.arraycopy(data, offset, out, 14, length)
        return out
    }

    private fun readInt32LE(b: ByteArray, off: Int): Int {
        if (off + 4 > b.size) return 0
        return (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)
    }

    private fun writeInt32LE(b: ByteArray, off: Int, value: Int) {
        b[off] = (value and 0xFF).toByte()
        b[off + 1] = ((value shr 8) and 0xFF).toByte()
        b[off + 2] = ((value shr 16) and 0xFF).toByte()
        b[off + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray, start: Int = 0, end: Int = data.size): Int {
        val limit = (end - pattern.size).coerceAtMost(data.size - pattern.size)
        outer@ for (i in start..limit) {
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
