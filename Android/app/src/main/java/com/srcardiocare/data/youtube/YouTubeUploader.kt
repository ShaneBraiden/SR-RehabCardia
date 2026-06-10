// YouTubeUploader.kt — Uploads videos to YouTube as unlisted via Data API v3
package com.srcardiocare.data.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Uploads videos directly to YouTube using a resumable upload.
 * Requires an OAuth2 access token with youtube.upload scope.
 */
object YouTubeUploader {

    /**
     * Uploads a video to YouTube as unlisted.
     * @return the YouTube video ID (e.g. "dQw4w9WgXcQ")
     */
    suspend fun uploadVideo(
        accessToken: String,
        videoBytes: ByteArray,
        title: String,
        description: String = "",
        mimeType: String = "video/mp4"
    ): String = withContext(Dispatchers.IO) {
        val metadataJson = JSONObject().apply {
            put("snippet", JSONObject().apply {
                put("title", title)
                put("description", description)
                put("categoryId", "27") // Education
            })
            put("status", JSONObject().apply {
                put("privacyStatus", "unlisted")
            })
        }

        // Step 1: Initiate resumable upload session
        val initUrl = URL(
            "https://www.googleapis.com/upload/youtube/v3/videos" +
                "?uploadType=resumable&part=snippet,status"
        )
        val initConn = (initUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("X-Upload-Content-Type", mimeType)
            setRequestProperty("X-Upload-Content-Length", videoBytes.size.toString())
            doOutput = true
        }
        initConn.outputStream.use { it.write(metadataJson.toString().toByteArray()) }

        if (initConn.responseCode != 200) {
            val errorBody = initConn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw Exception("YouTube upload init failed (${initConn.responseCode}): $errorBody")
        }
        val uploadUrl = initConn.getHeaderField("Location")
            ?: throw Exception("No upload URL returned from YouTube")

        // Step 2: Upload the video bytes to the resumable session URL
        val uploadConn = (URL(uploadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            setRequestProperty("Content-Type", mimeType)
            setRequestProperty("Content-Length", videoBytes.size.toString())
            doOutput = true
        }
        uploadConn.outputStream.use { it.write(videoBytes) }

        if (uploadConn.responseCode != 200) {
            val errorBody = uploadConn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw Exception("YouTube video upload failed (${uploadConn.responseCode}): $errorBody")
        }

        val response = uploadConn.inputStream.bufferedReader().readText()
        JSONObject(response).getString("id")
    }

    /** Constructs a YouTube watch URL from a video ID. */
    fun watchUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"

    /** Constructs a YouTube embed URL from a video ID. */
    fun embedUrl(videoId: String): String = "https://www.youtube.com/embed/$videoId"

    /** Constructs a YouTube thumbnail URL from a video ID. */
    fun thumbnailUrl(videoId: String): String = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

    /** Extracts YouTube video ID from various URL formats. */
    fun extractVideoId(url: String): String? {
        val pattern = Regex(
            "(?:youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/embed/)([a-zA-Z0-9_-]{11})"
        )
        return pattern.find(url)?.groupValues?.get(1)
    }
}
