package org.boringreport.boringlauncher

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.PackageManager

private const val LABEL_CACHE_PREFS = "app_label_cache"

/**
 * Resolves display names for launcher apps.
 *
 * [LauncherActivityInfo.getLabel] delegates to `ComponentInfo.loadLabel`, which returns the
 * package name as a last resort when the target app's label resource cannot be read — that
 * happens transiently while a package is being updated, while its storage volume is
 * unmounted, or when its resources have been evicted under memory pressure. The result is an
 * app row that intermittently reads "com.google.android.deskclock" instead of "clock".
 *
 * This resolver treats a label equal to the package name as "not loaded yet": it retries the
 * other load paths, and otherwise falls back to the last name that did resolve, which is
 * persisted so a cold start during a bad window still shows something readable.
 */
class AppLabelResolver(context: Context) {
    private val packageManager: PackageManager = context.packageManager
    private val cache = context.getSharedPreferences(LABEL_CACHE_PREFS, Context.MODE_PRIVATE)

    fun resolve(info: LauncherActivityInfo): String {
        val packageName = info.activityInfo.packageName

        val label = loadLabel(info, packageName)
        if (label != null) {
            cache.edit().putString(packageName, label).apply()
            return label
        }

        return cache.getString(packageName, null) ?: prettyPackageName(packageName)
    }

    /**
     * Tries every label source in turn, returning null if they all fall through to the
     * package-name sentinel. Each source can fail independently: the cached [info] may hold a
     * stale resource id, so the last attempt re-reads [android.content.pm.ApplicationInfo]
     * straight from the package manager.
     */
    private fun loadLabel(info: LauncherActivityInfo, packageName: String): String? {
        val candidates = sequence {
            yield(runCatching { info.label }.getOrNull())
            yield(runCatching { info.activityInfo.loadLabel(packageManager) }.getOrNull())
            yield(runCatching { info.applicationInfo.loadLabel(packageManager) }.getOrNull())
            yield(
                runCatching {
                    packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager)
                }.getOrNull()
            )
        }

        return candidates
            .map { it?.toString()?.trim() }
            .firstOrNull { !it.isNullOrEmpty() && it != packageName }
    }
}

/** "com.google.android.deskclock" -> "deskclock". Last-ditch, still better than a package id. */
private fun prettyPackageName(packageName: String): String =
    packageName.substringAfterLast('.').ifEmpty { packageName }
