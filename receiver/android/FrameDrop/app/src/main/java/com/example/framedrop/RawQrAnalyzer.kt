package com.example.framedrop

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.nio.ByteBuffer
import java.util.zip.CRC32

interface RawQrListener {
    // We only pass the valid bytes. No parsing here.
    fun onPacketReceived(rawData: ByteArray)
}

class RawQrAnalyzer(private val listener: RawQrListener) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        barcode.rawBytes?.let { rawData ->
                            processRawData(rawData)
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun processRawData(data: ByteArray) {
        // Minimum size: 8 bytes Header + 4 bytes CRC = 12 bytes
        if (data.size < 12) return

        // 1. Separate Data (Everything) from CRC (Last 4 bytes)
        val content = data.copyOfRange(0, data.size - 4)
        val remoteCrcBytes = data.copyOfRange(data.size - 4, data.size)

        val remoteCrc = ByteBuffer.wrap(remoteCrcBytes).int.toLong() and 0xFFFFFFFF

        // 2. Calculate Local CRC
        val crc32 = CRC32()
        crc32.update(content)
        val localCrc = crc32.value

        // 3. If Valid, send the CONTENT (Header + Body) to Activity
        if (localCrc == remoteCrc) {
            listener.onPacketReceived(content)
        }
    }
}