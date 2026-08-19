package org.boringreport.boringlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.UserHandle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.gson.Gson
import kotlinx.coroutines.delay
import org.boringreport.boringlauncher.ui.theme.BoringLauncherTheme
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val CATEGORIES_ASSET = "whitelist.json"
private const val EVERYTHING_ELSE = "everything else"
private val APP_SPACING = 4.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BoringLauncherTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Black
                ) { innerPadding ->
                    AppList(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    // Deprecated APIs, kept because they still drive the launcher's behaviour.
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        overridePendingTransition(R.anim.exit_to_right, R.anim.enter_from_bottom)
    }

    /** The launcher is the home screen, so back is a no-op. */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() = Unit
}

/** One launchable app, with its display name already resolved. */
data class LauncherApp(
    val label: String,
    val componentName: ComponentName,
    val user: UserHandle
)

data class AppCategory(val name: String, val apps: List<LauncherApp>)

data class CategoryJson(val name: String, val packages: List<String>)
data class CategoriesWrapper(val categories: List<CategoryJson>)

/** Reads a file from assets, or returns null if it cannot be read. */
private fun loadJSONString(context: Context, fileName: String): String? = try {
    context.assets.open(fileName).bufferedReader().use { it.readText() }
} catch (e: IOException) {
    e.printStackTrace()
    null
}

/** All launcher-visible apps across every user profile, keyed by package name. */
private fun loadAppsByPackage(context: Context): Map<String, LauncherApp> {
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    val labels = AppLabelResolver(context)

    return launcherApps.profiles
        .flatMap { profile -> launcherApps.getActivityList(null, profile) }
        .filter { it.activityInfo.packageName != context.packageName }
        .associate { info ->
            info.activityInfo.packageName to LauncherApp(
                label = labels.resolve(info),
                componentName = info.componentName,
                user = info.user
            )
        }
}

/** Categories from the assets file, each holding only the apps installed on this device. */
private fun loadCategorizedApps(context: Context): List<AppCategory> {
    val jsonString = loadJSONString(context, CATEGORIES_ASSET) ?: return emptyList()
    val wrapper = Gson().fromJson(jsonString, CategoriesWrapper::class.java)
    val appsByPackage = loadAppsByPackage(context)

    return wrapper.categories.map { category ->
        AppCategory(
            name = category.name,
            apps = category.packages.mapNotNull { appsByPackage[it] }
        )
    }
}

@Composable
fun AppList(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // App labels can fail to load transiently (see AppLabelResolver), so the list is rebuilt
    // whenever the system reports a package change and whenever the launcher comes back to
    // the foreground. Without this a name that resolved badly once would stay wrong for the
    // lifetime of the process.
    var refreshKey by remember { mutableIntStateOf(0) }
    val categorizedApps = remember(refreshKey) { loadCategorizedApps(context) }

    RefreshOnPackageChanges { refreshKey++ }
    RefreshOnResume { refreshKey++ }

    var showEverythingElse by remember { mutableStateOf(false) }

    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Spacer(Modifier.height(50.dp))
            DateTimeDisplay()
            Spacer(Modifier.height(100.dp))
        }

        categorizedApps.forEach { category ->
            val categoryName = category.name.lowercase()
            val isEverythingElse = categoryName == EVERYTHING_ELSE

            item {
                CategoryHeader(
                    text = if (isEverythingElse) {
                        "${if (showEverythingElse) "▼" else "▶"} $categoryName"
                    } else {
                        categoryName
                    },
                    onClick = if (isEverythingElse) {
                        { showEverythingElse = !showEverythingElse }
                    } else {
                        null
                    }
                )
            }

            if (isEverythingElse) {
                item {
                    AnimatedVisibility(
                        visible = showEverythingElse,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            category.apps.forEach { app ->
                                AppItem(app)
                                Spacer(Modifier.height(APP_SPACING))
                            }
                        }
                    }
                }
            } else {
                category.apps.forEach { app ->
                    item {
                        AppItem(app)
                        Spacer(Modifier.height(APP_SPACING))
                    }
                }
            }

            item { Spacer(Modifier.height(42.dp)) } // Space between categories
        }

        item { Spacer(Modifier.height(75.dp)) }
    }
}

@Composable
private fun CategoryHeader(text: String, onClick: (() -> Unit)?) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontFamily = FontManager.fontFamily,
        color = Color.LightGray,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    )
    Spacer(Modifier.height(5.dp))
}

@Composable
fun DateTimeDisplay() {
    var currentTime by remember { mutableStateOf(getFormattedTime()) }
    var currentDate by remember { mutableStateOf(getFormattedDate()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = getFormattedTime()
            currentDate = getFormattedDate()
            delay(1_000)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(16.dp)
    ) {
        Text(
            text = currentDate,
            fontSize = 22.sp,
            fontFamily = FontManager.fontFamily,
            color = Color.LightGray
        )

        Text(
            text = currentTime,
            fontSize = 48.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontManager.fontFamily,
            color = Color.White
        )
    }
}

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a")
private val DATE_FORMATTER = DateTimeFormatter.ofPattern("EEEE, MMMM d")

fun getFormattedTime(): String =
    LocalTime.now().format(TIME_FORMATTER).replace(" ", "").lowercase()

fun getFormattedDate(): String =
    LocalDate.now().format(DATE_FORMATTER).lowercase()

@Composable
fun AppItem(app: LauncherApp) {
    val context = LocalContext.current

    Text(
        text = app.label.lowercase(),
        fontSize = 36.sp,
        fontFamily = FontManager.fontFamily,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.clickable {
            context.getSystemService(LauncherApps::class.java)
                .startMainActivity(app.componentName, app.user, null, Bundle())
        }
    )
}

/** Invokes [onChange] when a package is installed, removed, changed, or becomes (un)available. */
@Composable
private fun RefreshOnPackageChanges(onChange: () -> Unit) {
    val context = LocalContext.current

    DisposableEffect(context) {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) = onChange()
            override fun onPackageRemoved(packageName: String, user: UserHandle) = onChange()
            override fun onPackageChanged(packageName: String, user: UserHandle) = onChange()

            // Fired when a package's storage volume is mounted or unmounted - the case most
            // likely to have handed us a package name in place of a label.
            override fun onPackagesAvailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean
            ) = onChange()

            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean
            ) = onChange()
        }

        launcherApps.registerCallback(callback)
        onDispose { launcherApps.unregisterCallback(callback) }
    }
}

/** Invokes [onResume] each time the launcher returns to the foreground. */
@Composable
private fun RefreshOnResume(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
