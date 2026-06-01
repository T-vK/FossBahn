package de.openbahn.navigator.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import de.openbahn.api.debug.OpenBahnDebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class AppUpdateInstaller(private val context: Context) {
    private val updatesDir: File
        get() = File(context.cacheDir, "updates").also { it.mkdirs() }

    suspend fun downloadApk(release: ReleaseApk): File? = withContext(Dispatchers.IO) {
        val target = File(updatesDir, release.fileName)
        runCatching {
            val connection = (URL(release.downloadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 120_000
                setRequestProperty("User-Agent", "OpenBahnNavigator")
            }
            try {
                if (connection.responseCode !in 200..299) {
                    OpenBahnDebugLog.w("AppUpdate", "Download failed HTTP ${connection.responseCode}")
                    return@withContext null
                }
                connection.inputStream.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                OpenBahnDebugLog.d("AppUpdate", "Downloaded ${release.fileName} (${target.length()} bytes)")
                target
            } finally {
                connection.disconnect()
            }
        }.getOrElse {
            OpenBahnDebugLog.w("AppUpdate", "Download error: ${it.message}", it)
            null
        }
    }

    fun canInstallPackages(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun promptInstall(apkFile: File): Boolean {
        if (!apkFile.exists()) return false
        if (!canInstallPackages()) {
            OpenBahnDebugLog.w("AppUpdate", "Install blocked: unknown sources not allowed")
            openInstallPermissionSettings()
            return false
        }
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrElse {
            OpenBahnDebugLog.w("AppUpdate", "Install intent failed: ${it.message}", it)
            false
        }
    }
}
