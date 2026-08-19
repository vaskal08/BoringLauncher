package org.boringreport.boringlauncher

//import android.R
import org.boringreport.boringlauncher.R
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.getSystemService
import com.google.gson.Gson
import kotlinx.coroutines.delay
import org.boringreport.boringlauncher.ui.theme.BoringLauncherTheme
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter


/**
 * Loads a JSON file from assets and returns it as a Map<String, Any>
 */
fun loadJSONString(context: Context, fileName: String): String? {
    var jsonString: String? = null
    try {
        // Open the JSON file from the assets folder
        val inputStream = context.assets.open(fileName)

        // Get the size of the file
        val size = inputStream.available()

        // Create a buffer with the size
        val buffer = ByteArray(size)

        // Read data into the buffer
        inputStream.read(buffer)

        // Close the input stream
        inputStream.close()

        // Convert buffer to string
        jsonString = String(buffer, Charsets.UTF_8)

        return jsonString

    } catch (e: IOException) {
        e.printStackTrace()
        return null
    }
}

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

    override fun onResume() {
        super.onResume()

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        overridePendingTransition(R.anim.exit_to_right, R.anim.enter_from_bottom)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
    }

    override fun onBackPressed() {

    }
}
//data class App(val name: String? = null, val packageName: String? = null)
data class AppCategory(val name: String, val apps: List<LauncherActivityInfo>)

data class CategoryJson(val name: String, val packages: List<String>)
data class CategoriesWrapper(val categories: List<CategoryJson>)

@Composable
fun AppList(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
//    val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager

    val profiles: List<UserHandle> = launcherApps.profiles

    val appList: MutableList<LauncherActivityInfo> = mutableListOf()

    for (profile in profiles) {
        val apps = launcherApps.getActivityList(null, profile)
        appList.addAll(apps)
        Log.d("VK", profile.toString())
    }


    val packageManager = context.packageManager
//    val appList:List<ResolveInfo> = packageManager
//        .queryIntentActivities(Intent(Intent.ACTION_MAIN,null)
//            .addCategory(Intent.CATEGORY_LAUNCHER),0)

    var showEverythingElse by remember {mutableStateOf(false)}

    val apps = ArrayList<LauncherActivityInfo>()

    val whitelist = arrayOf(
        "com.android.settings",
        "com.google.android.apps.maps",
        "com.google.android.apps.messaging",
        "com.google.android.apps.photos",
        "com.google.android.calculator",
        "com.google.android.calendar",
        "com.google.android.contacts",
        "com.google.android.deskclock",
        "com.google.android.dialer",
        "com.google.android.gm",
        "com.motorola.camera3",
        "com.google.android.apps.googleassistant",
        "com.google.android.apps.nbu.files",
        "com.chase.sig.android",
        "com.lastpass.lpandroid",
        "org.boringreport.app",
        "org.mozilla.focus",
        "com.facebook.orca",
        "je.fit",
        "com.microsoft.office.outlook",
        "com.whatsapp",
        "com.microsoft.teams",

        "com.lifetimefitness.interests.fitness",
        "com.rsa.securidapp",
        "com.google.android.keep",
        "com.google.android.GoogleCamera"
    )

    for (app in appList) {
        Log.d("VK-packages", app.activityInfo.packageName)
        if(app.activityInfo.packageName != context.packageName && whitelist.contains(app.activityInfo.packageName)) {
            apps.add(app)
//            apps.add(App(
//                app.label.toString(),
//                app.activityInfo.packageName,
//            ))
        }
    }

    val jsonString = loadJSONString(context, "whitelist.json")

    val gson = Gson()
    val categoriesWrapper = gson.fromJson(jsonString, CategoriesWrapper::class.java)

    val appsByPackage = apps.associateBy { it.activityInfo.packageName }

    val categorizedApps = categoriesWrapper.categories.map { category ->
        val matchingApps = category.packages.mapNotNull { packageName ->
            appsByPackage[packageName]
        }
        AppCategory(
            name = category.name,
            apps = matchingApps
        )
    }

    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Spacer(Modifier.height(50.dp))
//            Image(
//                modifier = Modifier
//                    .size(75.dp)
//                    .clickable {
//                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com/search?q=hello"))
//                        context.startActivity(intent)
//                    },
//                painter = painterResource(id = R.drawable.ic_launcher_foreground_transparent),
//                contentDescription = "",
//                colorFilter = ColorFilter.tint(Color.White
//            ))
            DateTimeDisplay()
            Spacer(Modifier.height(100.dp))
        }

        categorizedApps.forEach {category ->
            val categoryName = category.name.lowercase()
            var categoryHeader = category.name.lowercase()
            val isEverythingElse = categoryName == "everything else"

            if (isEverythingElse) {
                categoryHeader = "${if (showEverythingElse) "▼" else "▶"} $categoryHeader"
            }
            item {
                Text(
                    fontSize = 14.sp,
                    fontFamily = FontManager.fontFamily,
                    text = categoryHeader,
                    color = Color.LightGray,
                    modifier = Modifier.clickable {
                        if (isEverythingElse) {
                            showEverythingElse = !showEverythingElse
                        }
                    }
                )
                Spacer(Modifier.height(5.dp))
            }

            if (!isEverythingElse) {
                category.apps.forEach { app ->
                    item {
                        AppItem(app)
                        Spacer(Modifier.height(4.dp)) // Space between apps
                    }
                }
            } else {
                item {
                    AnimatedVisibility(
                        visible = showEverythingElse,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            category.apps.forEach { app ->
                                AppItem(app)
                                Spacer(Modifier.height(4.dp)) // Space between apps
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(42.dp)) } // Space between categories
        }

        item { Spacer(Modifier.height(75.dp)) }
    }
}

@Composable
fun DateTimeDisplay() {
    val currentTime = remember { mutableStateOf(getFormattedTime()) }
    val currentDate = remember { mutableStateOf(getFormattedDate()) }

    // Update every minute
    LaunchedEffect(Unit) {
        while (true) {
            currentTime.value = getFormattedTime()
            currentDate.value = getFormattedDate()
            delay(1_000) // 1 minute
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(16.dp)
    ) {
        Text(
            text = currentDate.value,
            fontSize = 22.sp,
//            fontWeight = FontWeight.SemiBold,
            fontFamily = FontManager.fontFamily,
            color = Color.LightGray
        )

        Text(
            text = currentTime.value,
            fontSize = 48.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontManager.fontFamily,
            color = Color.White
        )

    }
}

// Helper functions
fun getFormattedTime(): String {
    val now = LocalTime.now()
    val formatter = DateTimeFormatter.ofPattern("h:mm a")
    return now.format(formatter).replace(" ", "").lowercase()
}

fun getFormattedDate(): String {
    val now = LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")
    return now.format(formatter).lowercase()
}

@Composable
fun AppItem(app: LauncherActivityInfo) {
    val rowHeight = 36
    val context = LocalContext.current
    val view = LocalView.current

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = app.label.toString().lowercase() ?: "",
            fontSize = rowHeight.sp,
            fontFamily = FontManager.fontFamily,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.clickable {
                val launcherApps = context.getSystemService(LauncherApps::class.java)
                launcherApps.startMainActivity(
                    app.componentName,
                    app.user,
                     null,
                    Bundle()
                )
//                val pm: PackageManager = context.packageManager
//
//                val intent = pm.getLaunchIntentForPackage(app.activityInfo.packageName ?: "")
//
//                if (intent != null) {
//                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//
//                    val options = ActivityOptions.makeScaleUpAnimation(
//                        view,
//                        view.width / 3,
//                        view.height / 3,
//                        view.width,
//                        view.height
//                    )
//
//                    context.startActivity(intent, options.toBundle())
//                }
            }
        )
    }

}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BoringLauncherTheme {
        AppList()
    }
}