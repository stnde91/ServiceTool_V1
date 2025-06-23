package com.example.servicetool

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class GitHubUpdateService(private val context: Context) {
    
    companion object {
        private const val GITHUB_API_URL = "https://api.github.com/repos/stnde91/ServiceTool_V1/releases/latest"
        private const val TAG = "GitHubUpdateService"
    }
    
    data class UpdateInfo(
        val version: String,
        val downloadUrl: String,
        val description: String,
        val isNewVersion: Boolean
    )
    
    private val loggingManager = LoggingManager.getInstance(context)
    
    suspend fun checkForUpdates(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            loggingManager.logInfo("UPDATE", "GitHub Update-Check gestartet")
            
            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)
                
                val latestVersion = jsonObject.getString("tag_name").removePrefix("v")
                val description = jsonObject.optString("body", "Neue Version verfügbar")
                
                // Suche nach APK Asset
                val assets = jsonObject.getJSONArray("assets")
                var downloadUrl: String? = null
                
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }
                
                if (downloadUrl != null) {
                    val currentVersion = getCurrentAppVersion()
                    val isNewVersion = isVersionNewer(latestVersion, currentVersion)
                    
                    loggingManager.logInfo("UPDATE", "Update-Check: Aktuelle Version: $currentVersion, Neueste Version: $latestVersion")
                    
                    return@withContext UpdateInfo(
                        version = latestVersion,
                        downloadUrl = downloadUrl,
                        description = description,
                        isNewVersion = isNewVersion
                    )
                } else {
                    loggingManager.logWarning("UPDATE", "Keine APK-Datei in GitHub Release gefunden")
                }
            } else {
                loggingManager.logError("UPDATE", "GitHub API Fehler: HTTP $responseCode")
            }
            
            connection.disconnect()
        } catch (e: Exception) {
            loggingManager.logError("UPDATE", "Update-Check Fehler: ${e.message}", e)
        }
        
        return@withContext null
    }
    
    fun downloadAndInstallUpdate(updateInfo: UpdateInfo, onProgress: (String) -> Unit) {
        try {
            onProgress("Download wird vorbereitet...")
            
            val request = DownloadManager.Request(Uri.parse(updateInfo.downloadUrl))
            request.setTitle("ServiceTool Update v${updateInfo.version}")
            request.setDescription("Lade neue App-Version herunter...")
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "ServiceTool_v${updateInfo.version}.apk")
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)
            
            // Download-Überwachung
            val downloadReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        val query = DownloadManager.Query()
                        query.setFilterById(downloadId)
                        val cursor = downloadManager.query(query)
                        
                        if (cursor.moveToFirst()) {
                            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                val uriString = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                                val file = File(Uri.parse(uriString).path!!)
                                
                                context?.unregisterReceiver(this)
                                onProgress("Download abgeschlossen. Installation wird gestartet...")
                                installApk(file)
                                
                                loggingManager.logInfo("UPDATE", "Update-Download erfolgreich: ${file.absolutePath}")
                            } else if (status == DownloadManager.STATUS_FAILED) {
                                context?.unregisterReceiver(this)
                                onProgress("Download fehlgeschlagen!")
                                loggingManager.logError("UPDATE", "Update-Download fehlgeschlagen")
                            }
                        }
                        cursor.close()
                    }
                }
            }
            
            context.registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            onProgress("Download gestartet...")
            loggingManager.logInfo("UPDATE", "Update-Download gestartet für Version ${updateInfo.version}")
            
        } catch (e: Exception) {
            onProgress("Download-Fehler: ${e.message}")
            loggingManager.logError("UPDATE", "Update-Download Fehler: ${e.message}", e)
        }
    }
    
    private fun installApk(apkFile: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0+ - FileProvider verwenden
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
                intent.setDataAndType(uri, "application/vnd.android.package-archive")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                // Android 6.0 und älter
                intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
            }
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            
            loggingManager.logInfo("UPDATE", "APK-Installation gestartet: ${apkFile.absolutePath}")
            
        } catch (e: Exception) {
            loggingManager.logError("UPDATE", "APK-Installation Fehler: ${e.message}", e)
        }
    }
    
    private fun getCurrentAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0"
        }
    }
    
    private fun isVersionNewer(latestVersion: String, currentVersion: String): Boolean {
        return try {
            val latest = parseVersion(latestVersion)
            val current = parseVersion(currentVersion)
            
            for (i in 0 until maxOf(latest.size, current.size)) {
                val latestPart = latest.getOrNull(i) ?: 0
                val currentPart = current.getOrNull(i) ?: 0
                
                when {
                    latestPart > currentPart -> return true
                    latestPart < currentPart -> return false
                }
            }
            false
        } catch (e: Exception) {
            loggingManager.logError("UPDATE", "Version-Vergleich Fehler: ${e.message}", e)
            false
        }
    }
    
    private fun parseVersion(version: String): List<Int> {
        return version.split(".")
            .map { it.replace(Regex("[^0-9]"), "") }
            .filter { it.isNotEmpty() }
            .map { it.toInt() }
    }
}