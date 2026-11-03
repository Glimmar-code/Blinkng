package com.example.data.models

import com.blinkng.shared.discovery.BlinkUniversitySearch

/** Reuse exactly the same university identifiers and alias search as Windows. */
object NigerianUniversitySearch {
    fun canonical(name: String) = BlinkUniversitySearch.canonical(name)
    fun search(query: String, limit: Int = 80) = BlinkUniversitySearch.search(query, limit)
}
