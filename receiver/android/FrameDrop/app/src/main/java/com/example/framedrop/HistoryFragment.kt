package com.example.framedrop

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONArray

class HistoryFragment : Fragment() {

    private lateinit var historyRecycler: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var btnSearch: ImageButton
    private lateinit var searchLayout: TextInputLayout
    private lateinit var searchEditText: TextInputEditText
    private lateinit var titleText: TextView
    private lateinit var filterChipGroup: ChipGroup

    private val historyAdapter = HistoryAdapter()
    private var allItems = listOf<HistoryItem>()
    private var isSearchVisible = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        historyRecycler = view.findViewById(R.id.historyRecycler)
        emptyState = view.findViewById(R.id.emptyState)
        btnSearch = view.findViewById(R.id.btnSearch)
        searchLayout = view.findViewById(R.id.searchLayout)
        searchEditText = view.findViewById(R.id.searchEditText)
        titleText = view.findViewById(R.id.titleText)
        filterChipGroup = view.findViewById(R.id.filterChipGroup)

        historyRecycler.layoutManager = LinearLayoutManager(requireContext())
        historyRecycler.adapter = historyAdapter

        setupSearchFunctionality()
        setupFilterFunctionality()
        loadHistory()
    }

    private fun setupSearchFunctionality() {
        btnSearch.setOnClickListener {
            toggleSearch()
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterItems(s.toString())
            }
        })
    }

    private fun toggleSearch() {
        isSearchVisible = !isSearchVisible

        if (isSearchVisible) {
            // Show search box
            titleText.visibility = View.GONE
            searchLayout.visibility = View.VISIBLE
            searchEditText.requestFocus()

            // Show keyboard
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(searchEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        } else {
            // Hide search box
            titleText.visibility = View.VISIBLE
            searchLayout.visibility = View.GONE
            searchEditText.text?.clear()

            // Hide keyboard
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(searchEditText.windowToken, 0)
        }
    }

    private fun setupFilterFunctionality() {
        filterChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) {
                // No filter selected, show all
                filterByType("All")
            } else {
                val checkedChip = view?.findViewById<Chip>(checkedIds[0])
                val filterType = checkedChip?.text.toString()
                filterByType(filterType)
            }
        }
    }

    private fun filterByType(type: String) {
        val query = searchEditText.text.toString()
        var filtered = allItems

        // Apply type filter
        if (type != "All") {
            filtered = filtered.filter { item ->
                when (type) {
                    "Images" -> item.fileName.matches(Regex(".*\\.(jpg|jpeg|png|gif|webp|bmp|svg)$", RegexOption.IGNORE_CASE))
                    "Documents" -> item.fileName.matches(Regex(".*\\.(pdf|doc|docx|txt|xls|xlsx|ppt|pptx)$", RegexOption.IGNORE_CASE))
                    "Videos" -> item.fileName.matches(Regex(".*\\.(mp4|avi|mkv|mov|wmv|flv|webm)$", RegexOption.IGNORE_CASE))
                    "Others" -> !item.fileName.matches(Regex(".*\\.(jpg|jpeg|png|gif|webp|bmp|svg|pdf|doc|docx|txt|xls|xlsx|ppt|pptx|mp4|avi|mkv|mov|wmv|flv|webm)$", RegexOption.IGNORE_CASE))
                    else -> true
                }
            }
        }

        // Apply search query
        if (query.isNotEmpty()) {
            filtered = filtered.filter { item ->
                item.fileName.contains(query, ignoreCase = true)
            }
        }

        displayItems(filtered)
    }

    private fun filterItems(query: String) {
        // Get current filter type
        val checkedChip = filterChipGroup.checkedChipId
        val filterType = if (checkedChip != View.NO_ID) {
            view?.findViewById<Chip>(checkedChip)?.text.toString()
        } else {
            "All"
        }

        filterByType(filterType)
    }

    private fun displayItems(items: List<HistoryItem>) {
        historyAdapter.setItems(items)

        if (items.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            historyRecycler.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            historyRecycler.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun loadHistory() {
        val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)
        val jsonString = sharedPref.getString("history_data", null)

        if (jsonString != null) {
            try {
                val jsonArray = JSONArray(jsonString)
                val list = ArrayList<HistoryItem>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        HistoryItem(
                            fileName = obj.getString("fileName"),
                            timeTaken = obj.getString("timeTaken"),
                            size = obj.getString("size"),
                            timestamp = obj.getLong("timestamp")
                        )
                    )
                }

                allItems = list
                displayItems(allItems)

            } catch (e: Exception) {
                Log.e("History", "Error loading history", e)
                emptyState.visibility = View.VISIBLE
                historyRecycler.visibility = View.GONE
            }
        } else {
            emptyState.visibility = View.VISIBLE
            historyRecycler.visibility = View.GONE
        }
    }
}