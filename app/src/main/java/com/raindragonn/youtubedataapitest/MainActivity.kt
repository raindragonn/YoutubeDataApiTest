package com.raindragonn.youtubedataapitest

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    companion object {
        const val YOUTUBE_DATA_API_KEY = "Youtube Data API 키"
    }

    private lateinit var _etSearch: EditText
    private lateinit var _btnSearch: Button
    private lateinit var _tvResult: TextView

    private val searchText
        get() = _etSearch.text.toString()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        _etSearch = findViewById(R.id.et_search)
        _btnSearch = findViewById(R.id.btn_search)
        _tvResult = findViewById(R.id.tv_result)

        _btnSearch.setOnClickListener {
            search()
        }
    }

    private fun search() {
        lifecycleScope.launch {
            val result = youtubeSearch(searchText).joinToString("\n") { it.snippet.title }
            _tvResult.text = result
        }
    }

    private suspend fun youtubeSearch(query: String): List<SearchResult> {
        val transport = NetHttpTransport()
        val jsonFactory: JsonFactory = JacksonFactory.getDefaultInstance()
        val youtube = YouTube.Builder(
            transport, jsonFactory,
        ) {}.setApplicationName(getString(R.string.app_name))
            .build()

        return withContext(Dispatchers.IO) {
            runCatching {
                // 매개변수 참고 [https://developers.google.com/youtube/v3/docs/search/list?hl=ko#parameters]
                val parts = listOf("id", "snippet").joinToString()
                val searchList =
                    youtube.search().list(parts).apply {
                        key = YOUTUBE_DATA_API_KEY
                        q = query
                        type = "video"
                        // 음악 카테고리 참고 [https://gist.github.com/dgp/1b24bf2961521bd75d6c]
                        videoCategoryId = "10"
                    }
                val response = searchList?.execute()
                response?.items ?: listOf()
            }.recover {
                it.printStackTrace()
                listOf()
            }.getOrElse { listOf() }
        }
    }

}