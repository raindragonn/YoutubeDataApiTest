package com.raindragonn.youtubedataapitest

import android.accounts.AccountManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.YouTubeScopes
import com.google.api.services.youtube.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    companion object {
        const val PREF_NAME = "PREF_NAME"
        const val KEY_SELECTED_NAME = "KEY_SELECTED_NAME"
    }

    private lateinit var _etSearch: EditText
    private lateinit var _btnSearch: Button
    private lateinit var _tvResult: TextView

    private val searchText
        get() = _etSearch.text.toString()

    private val _credential: GoogleAccountCredential by lazy {
        GoogleAccountCredential.usingOAuth2(
            applicationContext,
            listOf(YouTubeScopes.YOUTUBE_READONLY),
        ).setBackOff(ExponentialBackOff())
    }

    private val _credentialLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(), ::credentialCallBack
    )

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
        if (checkSignIn()) {
            lifecycleScope.launch {
                val result = youtubeSearch(searchText).joinToString("\n") { it.snippet.title }
                _tvResult.text = result
            }
        }
    }

    private fun checkSignIn(): Boolean {
        val lastSelectedAccountName = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .getString(KEY_SELECTED_NAME, null)
        return if (lastSelectedAccountName.isNullOrBlank()) {
            val signInIntent = _credential.newChooseAccountIntent()
            _credentialLauncher.launch(signInIntent)
            false
        } else {
            _credential.selectedAccountName = lastSelectedAccountName
            true
        }
    }

    private fun credentialCallBack(result: ActivityResult) {
        val accountName = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME) ?: return

        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_SELECTED_NAME, accountName)
        }
        _credential.selectedAccountName = accountName

        Toast.makeText(this, "검색 가능", Toast.LENGTH_SHORT).show()
    }

    private suspend fun youtubeSearch(query: String): List<SearchResult> {
        val transport = NetHttpTransport()
        val jsonFactory: JsonFactory = JacksonFactory.getDefaultInstance()
        val youtube = YouTube.Builder(
            transport, jsonFactory, _credential
        ).setApplicationName(getString(R.string.app_name))
            .build()

        return withContext(Dispatchers.IO) {
            runCatching {
                // 매개변수 참고 [https://developers.google.com/youtube/v3/docs/search/list?hl=ko#parameters]
                val parts = listOf("id", "snippet").joinToString()
                val searchList =
                    youtube.search().list(parts).apply {
                        q = query
                        type = "video"
                        // 음악 카테고리 참고 [https://gist.github.com/dgp/1b24bf2961521bd75d6c]
                        videoCategoryId = "10"
                    }
                val response = searchList?.execute()
                response?.items ?: listOf()
            }.recover { throwable ->
                if (throwable is UserRecoverableAuthIOException) {
                    launch(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "인증이 필요합니다.", Toast.LENGTH_SHORT).show()
                        // 엑세스 권한이 없는 경우 권한 요청을 위한 Intent
                        val accessRequestIntent = throwable.intent
                        _credentialLauncher.launch(accessRequestIntent)
                    }
                }
                listOf()
            }.getOrNull() ?: listOf()
        }
    }

}