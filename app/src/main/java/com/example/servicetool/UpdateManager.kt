package com.example.servicetool

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class UpdateManager(private val context: Context) {
    
    companion object {
        private const val TAG = "UpdateManager"
        private const val GITHUB_API_URL = "https://api.github.com/repos/YOUR_USERNAME/ServiceTool/releases/latest"
        private const val UPDATE_CHECK_KEY = "last_update_check"
        private const val UPDATE_CHECK_INTERVAL = 24 * 60 * 60 * 1000L // 24 Stunden
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    
    data class GitHubRelease(
        @SerializedName("tag_name") val tagName: String,
        @SerializedName("name") val name: String,
        @SerializedName("body") val body: String,
        @SerializedName("assets") val assets: List<Asset>
    )
    
    data class Asset(
        @SerializedName("name") val name: String,
        @SerializedName("browser_download_url") val downloadUrl: String,
        @SerializedName("size") val size: Long
    )
    
    data class UpdateInfo(
        val version: String,
        val changelog: String,
        val downloadUrl: String,
        val fileSize: Long,
        val isUpdateAvailable: Boolean
    )
    
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking for updates...")
            
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Update check failed: ${response.code}")
                return@withContext null
            }
            
            val responseBody = response.body?.string() ?: return@withContext null
            val release = gson.fromJson(responseBody, GitHubRelease::class.java)
            
            // Finde die APK-Datei
            val apkAsset = release.assets.find { it.name.endsWith(".apk") }
                ?: return@withContext null
            
            // Extrahiere Versionsnummer aus Tag (z.B. "v1.2.0" -> "1.2.0")
            val latestVersion = release.tagName.removePrefix("v")
            val currentVersion = getCurrentVersion()
            
            val isUpdateAvailable = isNewerVersion(currentVersion, latestVersion)
            
            // Speichere Zeitstempel der letzten Prüfung
            prefs.edit().putLong(UPDATE_CHECK_KEY, System.currentTimeMillis()).apply()
            
            return@withContext UpdateInfo(
                version = latestVersion,
                changelog = release.body,
                downloadUrl = apkAsset.downloadUrl,
                fileSize = apkAsset.size,
                isUpdateAvailable = isUpdateAvailable
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            return@withContext null
        }
    }
    
    suspend fun downloadAndInstallUpdate(updateInfo: UpdateInfo, 
                                       onProgress: (Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Downloading update from: ${updateInfo.downloadUrl}")
            
            val request = Request.Builder()
                .url(updateInfo.downloadUrl)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "Download failed: ${response.code}")
                return@withContext false
            }
            
            val body = response.body ?: return@withContext false
            val contentLength = body.contentLength()
            
            // Download-Verzeichnis
            val downloadDir = File(context.getExternalFilesDir(null), "updates")
            downloadDir.mkdirs()
            
            val apkFile = File(downloadDir, "ServiceTool-${updateInfo.version}.apk")
            
            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        if (contentLength > 0) {
                            val progress = (totalBytesRead * 100 / contentLength).toInt()
                            withContext(Dispatchers.Main) {
                                onProgress(progress)
                            }
                        }
                    }
                }
            }
            
            // Installation starten
            installApk(apkFile)
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading update", e)
            return@withContext false
        }
    }
    
    private fun installApk(apkFile: File) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }
            
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        
        context.startActivity(intent)
    }
    
    private fun getCurrentVersion(): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }
    
    private fun isNewerVersion(current: String, latest: String): Boolean {
        try {
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            
            for (i in 0 until maxOf(currentParts.size, latestParts.size)) {
                val currentPart = currentParts.getOrNull(i) ?: 0
                val latestPart = latestParts.getOrNull(i) ?: 0
                
                if (latestPart > currentPart) return true
                if (latestPart < currentPart) return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing versions", e)
        }
        
        return false
    }
    
    fun shouldCheckForUpdate(): Boolean {
        val lastCheck = prefs.getLong(UPDATE_CHECK_KEY, 0)
        return System.currentTimeMillis() - lastCheck > UPDATE_CHECK_INTERVAL
    }
}