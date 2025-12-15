package com.example.framedrop

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import org.json.JSONArray

class HomeFragment : Fragment() {

    private lateinit var btnReceive: MaterialButton
    private lateinit var btnSend: MaterialButton
    private lateinit var filesReceivedCount: TextView
    private lateinit var totalSizeText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnReceive = view.findViewById(R.id.btnReceive)
        btnSend = view.findViewById(R.id.btnSend)
        filesReceivedCount = view.findViewById(R.id.filesReceivedCount)
        totalSizeText = view.findViewById(R.id.totalSizeText)

        // Load stats
        updateStats()

        // Button listeners
        btnReceive.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_receive)
        }

        btnSend.setOnClickListener {
            // Future feature
        }
    }

    override fun onResume() {
        super.onResume()
        updateStats()
    }

    private fun updateStats() {
        val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)
        val jsonString = sharedPref.getString("history_data", null)

        if (jsonString != null) {
            try {
                val jsonArray = JSONArray(jsonString)
                val fileCount = jsonArray.length()
                var totalBytes = 0L

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val sizeStr = obj.getString("size")
                    totalBytes += parseSizeToBytes(sizeStr)
                }

                filesReceivedCount.text = fileCount.toString()
                totalSizeText.text = formatSize(totalBytes)

            } catch (e: Exception) {
                filesReceivedCount.text = "0"
                totalSizeText.text = "0 MB"
            }
        } else {
            filesReceivedCount.text = "0"
            totalSizeText.text = "0 MB"
        }
    }

    private fun parseSizeToBytes(sizeStr: String): Long {
        return try {
            val parts = sizeStr.trim().split(" ")
            val value = parts[0].toDouble()
            val unit = parts[1].uppercase()

            when (unit) {
                "KB" -> (value * 1024).toLong()
                "MB" -> (value * 1024 * 1024).toLong()
                "GB" -> (value * 1024 * 1024 * 1024).toLong()
                else -> 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}