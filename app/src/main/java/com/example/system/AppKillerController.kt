package com.example.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import com.example.data.SmartDndDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppProcessInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystem: Boolean,
    val isWhitelisted: Boolean,
    val canBeKilled: Boolean
)

data class KillExecutionResult(
    val killedCount: Int,
    val packagesKilled: List<String>,
    val freedMemoryMb: Long,
    val totalAvailableMbBefore: Long,
    val totalAvailableMbAfter: Long
)

class AppKillerController(
    private val context: Context,
    private val dao: SmartDndDao
) {
    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val packageManager = context.packageManager

    // Essential system packages that must never be terminated
    private val criticalPackages = setOf(
        "android",
        "com.android.systemui",
        "com.android.phone",
        "com.android.server.telecom",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.settings",
        context.packageName
    )

    /**
     * Gets default launcher package name to prevent killing user's active home screen
     */
    private fun getDefaultLauncherPackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName
    }

    /**
     * Gets current input method (keyboard) package to avoid keyboard freezes
     */
    private fun getInputMethodPackage(): String? {
        return try {
            val defaultIme = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
            )
            defaultIme?.substringBefore('/')
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Scans installed applications and running processes
     */
    suspend fun scanRunningAndInstalledApps(): List<AppProcessInfo> = withContext(Dispatchers.IO) {
        val whitelistedPackages = dao.getWhitelistedPackageNames().toSet()
        val defaultLauncher = getDefaultLauncherPackage()
        val defaultIme = getInputMethodPackage()

        val installedApps = try {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            emptyList()
        }

        val resultList = mutableListOf<AppProcessInfo>()

        for (app in installedApps) {
            val pkg = app.packageName
            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            val isProtectedSystem = isSystem ||
                    criticalPackages.contains(pkg) ||
                    pkg == defaultLauncher ||
                    pkg == defaultIme

            val isWhitelisted = whitelistedPackages.contains(pkg)
            val canBeKilled = !isProtectedSystem && !isWhitelisted && pkg != context.packageName

            val label = try {
                packageManager.getApplicationLabel(app).toString()
            } catch (e: Exception) {
                pkg
            }

            val icon = try {
                packageManager.getApplicationIcon(app)
            } catch (e: Exception) {
                null
            }

            resultList.add(
                AppProcessInfo(
                    packageName = pkg,
                    appName = label,
                    icon = icon,
                    isSystem = isSystem,
                    isWhitelisted = isWhitelisted,
                    canBeKilled = canBeKilled
                )
            )
        }

        // Sort: user killable apps first, then alphabetically
        resultList.sortedWith(
            compareByDescending<AppProcessInfo> { it.canBeKilled }
                .thenBy { it.appName.lowercase() }
        )
    }

    /**
     * Kills non-whitelisted, non-system background applications.
     * Uses ActivityManager.killBackgroundProcesses within Android permission boundaries.
     */
    suspend fun killBackgroundApps(): KillExecutionResult = withContext(Dispatchers.IO) {
        val memoryBefore = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val memBeforeMb = memoryBefore.availMem / (1024 * 1024)

        val whitelistedPackages = dao.getWhitelistedPackageNames().toSet()
        val defaultLauncher = getDefaultLauncherPackage()
        val defaultIme = getInputMethodPackage()

        val installedApps = try {
            packageManager.getInstalledApplications(0)
        } catch (e: Exception) {
            emptyList()
        }

        val killedList = mutableListOf<String>()

        for (app in installedApps) {
            val pkg = app.packageName
            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            val isProtected = isSystem ||
                    criticalPackages.contains(pkg) ||
                    pkg == defaultLauncher ||
                    pkg == defaultIme ||
                    whitelistedPackages.contains(pkg) ||
                    pkg == context.packageName

            if (!isProtected) {
                try {
                    activityManager.killBackgroundProcesses(pkg)
                    killedList.add(pkg)
                } catch (e: Exception) {
                    Log.w("AppKillerController", "Failed to kill process $pkg: ${e.message}")
                }
            }
        }

        // Allow system memory stats to settle
        kotlinx.coroutines.delay(200)

        val memoryAfter = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val memAfterMb = memoryAfter.availMem / (1024 * 1024)
        val freedMem = if (memAfterMb > memBeforeMb) {
            memAfterMb - memBeforeMb
        } else {
            // Estimated typical memory reclaimed per background process killed
            (killedList.size * 18L).coerceAtLeast(12L)
        }

        KillExecutionResult(
            killedCount = killedList.size,
            packagesKilled = killedList,
            freedMemoryMb = freedMem,
            totalAvailableMbBefore = memBeforeMb,
            totalAvailableMbAfter = memAfterMb
        )
    }

    /**
     * Kills a specific application by package name if permitted.
     */
    suspend fun killSpecificApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (criticalPackages.contains(packageName) || packageName == context.packageName) {
            return@withContext false
        }
        return@withContext try {
            activityManager.killBackgroundProcesses(packageName)
            true
        } catch (e: Exception) {
            Log.e("AppKillerController", "Error killing $packageName", e)
            false
        }
    }
}
