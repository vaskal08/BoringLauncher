package org.boringreport.boringlauncher

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle

/** One launchable app, with its display name already resolved. */
data class LauncherApp(
    val packageName: String,
    val label: String,
    val componentName: ComponentName,
    val user: UserHandle
)

/**
 * A named group of apps on the home screen, holding only the ones actually installed.
 *
 * [isEverythingElse] marks the built-in drawer described in [loadFolderedApps]. The home
 * screen keys its collapsing behaviour off this flag rather than off the folder's name, so a
 * user folder that happens to be called "everything else" cannot impersonate it.
 */
data class AppFolder(
    val name: String,
    val apps: List<LauncherApp>,
    val isEverythingElse: Boolean = false
)

/** Every launcher-visible app across all user profiles, keyed by package name. */
fun loadAppsByPackage(context: Context): Map<String, LauncherApp> {
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    val labels = AppLabelResolver(context)

    return launcherApps.profiles
        .flatMap { profile -> launcherApps.getActivityList(null, profile) }
        .filter { it.activityInfo.packageName != context.packageName }
        .associate { info ->
            val packageName = info.activityInfo.packageName
            packageName to LauncherApp(
                packageName = packageName,
                label = labels.resolve(info),
                componentName = info.componentName,
                user = info.user
            )
        }
}

/** Every installed app, in the order the customization screen lists them. */
fun loadAllApps(context: Context): List<LauncherApp> =
    loadAppsByPackage(context).values.sortedBy { it.label.lowercase() }

/** The apps "everything else" covers: installed and not in any of [folders]. */
fun remainderApps(allApps: List<LauncherApp>, folders: List<Folder>): List<LauncherApp> {
    val filed = folders.flatMap { it.packages }.toSet()
    return allApps.filterNot { it.packageName in filed }
}

/**
 * The home screen's folders: the user's own, in their order, followed by "everything else".
 *
 * That last one's contents are computed rather than stored: whatever is installed, not already
 * in a folder, and not unchecked by the user. It behaves as an app drawer that stays complete
 * on its own as apps are installed and removed.
 */
fun loadFolderedApps(context: Context): List<AppFolder> {
    val appsByPackage = loadAppsByPackage(context)
    val config = LauncherConfigStore(context).load()

    val folders = config.folders.map { folder ->
        AppFolder(
            name = folder.name,
            apps = folder.packages.mapNotNull { appsByPackage[it] }
        )
    }

    val filed = folders.flatMap { folder -> folder.apps.map { it.packageName } }.toSet()
    val everythingElse = AppFolder(
        name = EVERYTHING_ELSE,
        apps = appsByPackage.values
            .filterNot { it.packageName in filed }
            .filterNot { it.packageName in config.hidden }
            .sortedBy { it.label.lowercase() },
        isEverythingElse = true
    )

    // A folder with nothing installed in it would render as a bare heading, which is how a
    // brand new folder starts out. User folders stay editable on the customization screen.
    return (folders + everythingElse).filter { it.apps.isNotEmpty() }
}
