package com.alfas.amortizacioncredito.domain.service

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.alfas.amortizacioncredito.data.feature.FeatureFlagManager
import com.alfas.amortizacioncredito.data.feature.FeatureKey
import com.alfas.amortizacioncredito.data.model.AppUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AppUpdateManager(
    private val context: Context,
    private val featureFlagManager: FeatureFlagManager = FeatureFlagManager(context)
) {

    private val currentVersionCode = try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            pInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            pInfo.versionCode
        }
    } catch (_: Exception) {
        1
    }

    private val currentVersionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
    } catch (_: Exception) {
        "1.0.0"
    }

    suspend fun checkForUpdates(): AppUpdateInfo = withContext(Dispatchers.IO) {
        if (!featureFlagManager.isEnabled(FeatureKey.VPS_AUTO_UPDATE)) {
            return@withContext AppUpdateInfo(
                hasUpdate = false,
                currentVersionCode = currentVersionCode,
                currentVersionName = currentVersionName
            )
        }

        try {
            val vpsUrl = featureFlagManager.getVpsUrl().trimEnd('/') + "/api/update"
            val url = URL(vpsUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"

            if (conn.responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val latestCode = json.optInt("latestVersionCode", currentVersionCode)
                val latestName = json.optString("latestVersionName", currentVersionName)
                val apkUrl = json.optString("apkUrl", "")
                val notes = json.optString("releaseNotes", "Mejoras de rendimiento y nuevas funciones.")
                val mandatory = json.optBoolean("isMandatory", false)

                val hasUpdate = latestCode > currentVersionCode
                AppUpdateInfo(
                    hasUpdate = hasUpdate,
                    latestVersionCode = latestCode,
                    latestVersionName = latestName,
                    currentVersionCode = currentVersionCode,
                    currentVersionName = currentVersionName,
                    apkUrl = apkUrl,
                    releaseNotes = notes,
                    isMandatory = mandatory
                )
            } else {
                AppUpdateInfo(hasUpdate = false, currentVersionCode = currentVersionCode, currentVersionName = currentVersionName)
            }
        } catch (e: Exception) {
            AppUpdateInfo(hasUpdate = false, currentVersionCode = currentVersionCode, currentVersionName = currentVersionName)
        }
    }

    fun downloadAndInstallApk(apkUrl: String, apkName: String = "CreditHome_Update.apk") {
        try {
            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("Descargando actualización de CreditHome")
                .setDescription("Descarga de la última versión desde el servidor VPS")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, apkName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            // Intent con navegador directo como alternativa
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        }
    }
}
