package com.example.framedrop

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ReceiveFragment : Fragment() {

    private lateinit var viewFinder: PreviewView
    private lateinit var btnBack: ImageButton
    private lateinit var statusText: TextView
    private lateinit var timerText: TextView
    private lateinit var progressCard: MaterialCardView
    private lateinit var fileNameText: TextView
    private lateinit var progressText: TextView
    private lateinit var progressPercent: TextView
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var scanningPulse: View

    private lateinit var cameraExecutor: ExecutorService
    private val receivedChunks = HashMap<Int, ByteArray>()
    private var totalBlocks = 0
    private var isFinished = false
    private var startTime: Long = 0
    private var isTimerRunning = false
    private val timerHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isTimerRunning) {
                val elapsed = System.currentTimeMillis() - startTime
                val seconds = elapsed / 1000.0
                timerText.text = String.format("%.1fs", seconds)
                timerHandler.postDelayed(this, 100)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_receive, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewFinder = view.findViewById(R.id.viewFinder)
        btnBack = view.findViewById(R.id.btnBack)
        statusText = view.findViewById(R.id.statusText)
        timerText = view.findViewById(R.id.timerText)
        progressCard = view.findViewById(R.id.progressCard)
        fileNameText = view.findViewById(R.id.fileNameText)
        progressText = view.findViewById(R.id.progressText)
        progressPercent = view.findViewById(R.id.progressPercent)
        progressBar = view.findViewById(R.id.progressBar)
        scanningPulse = view.findViewById(R.id.scanningPulse)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Keep screen on
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // Start scanning animation
        startScanningAnimation()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun startScanningAnimation() {
        // Animate scanning line up and down
        scanningPulse.animate()
            .translationY(280f)
            .setDuration(2000)
            .withEndAction {
                scanningPulse.animate()
                    .translationY(0f)
                    .setDuration(2000)
                    .withEndAction {
                        if (isAdded) startScanningAnimation()
                    }
                    .start()
            }
            .start()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, RawQrAnalyzer(object : RawQrListener {
                        override fun onPacketReceived(rawData: ByteArray) {
                            handleIncomingPacket(rawData)
                        }
                    }))
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @Synchronized
    private fun handleIncomingPacket(rawData: ByteArray) {
        if (isFinished) return

        if (rawData.size < 8) return
        val buffer = ByteBuffer.wrap(rawData)

        val blockId = buffer.int
        val total = buffer.int

        if (total < 0 || total > 500000) return

        if (total != totalBlocks) {
            receivedChunks.clear()
            totalBlocks = total
            isFinished = false

            isTimerRunning = false
            timerHandler.removeCallbacks(timerRunnable)

            activity?.runOnUiThread {
                progressBar.max = total
                progressBar.progress = 0
                progressCard.visibility = View.VISIBLE
                timerText.visibility = View.VISIBLE
                timerText.text = "0.0s"
                statusText.text = "Receiving file..."
                fileNameText.text = "Receiving..."
                progressText.text = "0 / $total"
                progressPercent.text = "0%"
            }
        }

        if (receivedChunks.isEmpty() && !isTimerRunning) {
            startTime = System.currentTimeMillis()
            isTimerRunning = true
            timerHandler.post(timerRunnable)
        }

        if (!receivedChunks.containsKey(blockId)) {
            val payload = rawData.copyOfRange(8, rawData.size)
            receivedChunks[blockId] = payload

            activity?.runOnUiThread {
                val progress = receivedChunks.size
                progressBar.progress = progress
                progressText.text = "$progress / $totalBlocks"
                val percent = (progress * 100 / totalBlocks)
                progressPercent.text = "$percent%"
            }

            if (receivedChunks.size == totalBlocks) {
                isFinished = true
                stopTimerAndSave()
            }
        }
    }

    private fun stopTimerAndSave() {
        isTimerRunning = false
        timerHandler.removeCallbacks(timerRunnable)
        val finalTime = (System.currentTimeMillis() - startTime) / 1000.0

        try {
            val sortedMap = receivedChunks.toSortedMap()
            val bos = java.io.ByteArrayOutputStream()
            for (bytes in sortedMap.values) {
                bos.write(bytes)
            }
            val fullData = bos.toByteArray()

            val buffer = ByteBuffer.wrap(fullData)
            val trueSize = buffer.int
            val content = fullData.copyOfRange(4, 4 + trueSize)

            val nameLen = content[0].toInt() and 0xFF
            val originalName = String(content, 1, nameLen, Charsets.UTF_8)
            val finalData = content.copyOfRange(1 + nameLen, content.size)

            // Create FrameDrop Received folder in Downloads
            val downloadsPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val frameDropFolder = File(downloadsPath, "FrameDrop Received")

            // Create directory if it doesn't exist
            if (!frameDropFolder.exists()) {
                frameDropFolder.mkdirs()
            }

            var file = File(frameDropFolder, originalName)
            var counter = 1

            val nameNoExt = if (originalName.contains(".")) originalName.substringBeforeLast(".") else originalName
            val ext = if (originalName.contains(".")) "." + originalName.substringAfterLast(".") else ""

            while (file.exists()) {
                file = File(frameDropFolder, "$nameNoExt($counter)$ext")
                counter++
            }

            FileOutputStream(file).use { it.write(finalData) }

            activity?.runOnUiThread {
                statusText.text = "Transfer complete!"
                fileNameText.text = file.name

                val sizeMb = finalData.size / 1024.0 / 1024.0
                val sizeStr = if (sizeMb < 1) "${finalData.size / 1024} KB" else String.format("%.1f MB", sizeMb)

                saveToHistory(file.name, String.format("%.1fs", finalTime), sizeStr)

                Toast.makeText(requireContext(), "File saved to FrameDrop Received", Toast.LENGTH_SHORT).show()

                // Navigate back after 2 seconds
                view?.postDelayed({
                    findNavController().navigateUp()
                }, 2000)
            }
        } catch (e: Exception) {
            Log.e("Save", "Error saving file", e)
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), "Error saving file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveToHistory(fileName: String, timeTaken: String, size: String) {
        val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)
        val editor = sharedPref.edit()

        val jsonString = sharedPref.getString("history_data", null)
        val jsonArray = if (jsonString != null) JSONArray(jsonString) else JSONArray()

        val obj = JSONObject()
        obj.put("fileName", fileName)
        obj.put("timeTaken", timeTaken)
        obj.put("size", size)
        obj.put("timestamp", System.currentTimeMillis())

        // Insert at beginning
        val newArray = JSONArray()
        newArray.put(obj)
        for (i in 0 until jsonArray.length()) {
            newArray.put(jsonArray.get(i))
        }

        editor.putString("history_data", newArray.toString())
        editor.apply()
    }

    private fun allPermissionsGranted() = arrayOf(Manifest.permission.CAMERA).all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(requireContext(), "Camera permission required", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clear keep screen on flag
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        cameraExecutor.shutdown()
        timerHandler.removeCallbacks(timerRunnable)
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
    }
}