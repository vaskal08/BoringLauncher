package org.boringreport.boringlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.UserHandle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.boringreport.boringlauncher.ui.theme.BoringLauncherTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val APP_SPACING = 4.dp
private val CLOCK_TO_APPS_SPACING = 200.dp
private val DRAWER_TOP_PADDING = 24.dp
private const val DRAWER_EXPAND_MS = 200
private const val HOMECOMING_FROM_SCALE = 0.88f
private const val HOMECOMING_DURATION_MS = 300

class MainActivity : ComponentActivity() {

    /**
     * Held by the activity rather than the composition so that onNewIntent can reset it: the
     * launcher is already running when home is pressed, and that press means "show me home".
     */
    private var customizing by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BoringLauncherTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Black
                ) { innerPadding ->
                    val screenModifier = Modifier
                        .padding(innerPadding)
                        .homecoming()

                    if (customizing) {
                        CustomizeScreen(
                            onDone = { customizing = false },
                            modifier = screenModifier
                        )
                    } else {
                        AppList(
                            onCustomize = { customizing = true },
                            modifier = screenModifier
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        customizing = false
    }

    // Deprecated APIs, kept because they still drive the launcher's behaviour.
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        overridePendingTransition(R.anim.exit_to_right, R.anim.enter_from_bottom)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppList(onCustomize: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // The launcher is the home screen, so back has nowhere to go.
    BackHandler {}

    // App labels can fail to load transiently (see AppLabelResolver), so the list is rebuilt
    // whenever the system reports a package change and whenever the launcher comes back to
    // the foreground. Without this a name that resolved badly once would stay wrong for the
    // lifetime of the process.
    var refreshKey by remember { mutableIntStateOf(0) }
    // Re-reading on every entry also picks up folder edits on the way back from
    // CustomizeScreen, which removes this composable from the composition.
    val folders = remember(refreshKey) { loadFolderedApps(context) }

    RefreshOnPackageChanges { refreshKey++ }
    RefreshOnResume { refreshKey++ }

    var showEverythingElse by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val everythingElseIndex = folders.indexOfFirst { it.isEverythingElse }
    val drawerTopPadding = with(LocalDensity.current) { DRAWER_TOP_PADDING.roundToPx() }

    // Opening the drawer at the bottom of the screen otherwise leaves its heading where it
    // was, forcing a second scroll to read what just appeared. Bring it up to the top edge
    // instead; animateScrollToItem stops at the end of the list on its own when the content
    // is too short to get all the way there.
    LaunchedEffect(showEverythingElse) {
        if (!showEverythingElse || everythingElseIndex < 0) return@LaunchedEffect

        // The expansion has to finish adding its height first, or the scroll runs out of
        // list and stops short of the heading.
        delay(DRAWER_EXPAND_MS.toLong())
        listState.animateScrollToItem(
            index = everythingElseIndex + 1, // + 1 for the clock, which is item 0
            scrollOffset = -drawerTopPadding
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Long-pressing the empty space around the list opens customization. Rows handle
            // their own long press, since a tap there is claimed by the app launch.
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onCustomize() })
            }
    ) {
        LazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Spacer(Modifier.height(50.dp))
                DateTimeDisplay()
                Spacer(Modifier.height(CLOCK_TO_APPS_SPACING))
            }

            // One item per folder, so that scrolling to a folder is scrolling to an index.
            items(folders) { folder ->
                FolderSection(
                    folder = folder,
                    expanded = showEverythingElse,
                    onToggle = { showEverythingElse = !showEverythingElse },
                    onLongPress = onCustomize
                )
            }

            item { Spacer(Modifier.height(75.dp)) }
        }
    }
}

@Composable
private fun FolderSection(
    folder: AppFolder,
    expanded: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        val name = folder.name.lowercase()

        CategoryHeader(
            text = if (folder.isEverythingElse) {
                "${if (expanded) "▼" else "▶"} $name"
            } else {
                name
            },
            onClick = if (folder.isEverythingElse) onToggle else null
        )

        if (folder.isEverythingElse) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(DRAWER_EXPAND_MS)),
                exit = shrinkVertically(tween(DRAWER_EXPAND_MS))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    folder.apps.forEach { app ->
                        AppItem(app, onLongPress = onLongPress)
                        Spacer(Modifier.height(APP_SPACING))
                    }
                }
            }
        } else {
            folder.apps.forEach { app ->
                AppItem(app, onLongPress = onLongPress)
                Spacer(Modifier.height(APP_SPACING))
            }
        }

        Spacer(Modifier.height(42.dp)) // Space between folders
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppItem(app: LauncherApp, onLongPress: () -> Unit) {
    val context = LocalContext.current
    val rootView = LocalView.current

    // Where this row sits on screen, so the app can be launched as though it were unfolding
    // from it. See launchApp.
    var bounds by remember { mutableStateOf<Rect?>(null) }

    Text(
        text = app.label.lowercase(),
        fontSize = 36.sp,
        fontFamily = FontManager.fontFamily,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .combinedClickable(
                onClick = { launchApp(context, rootView, app, bounds) },
                onLongClick = onLongPress
            )
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

/**
 * Settles the launcher's contents up into place whenever it returns to the foreground, so
 * coming home reads as the launcher arriving rather than a screen sliding in beside the app.
 *
 * This only animates what is inside our own window. The app -> home transition itself belongs
 * to the system: the outgoing app's animation is not ours to set, and making it shrink into
 * the launcher needs remote animations, which are gated behind a signature-level permission
 * that only the system launcher holds.
 */
@Composable
private fun Modifier.homecoming(): Modifier {
    val scale = remember { Animatable(1f) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    scale.snapTo(HOMECOMING_FROM_SCALE)
                    scale.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = HOMECOMING_DURATION_MS,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
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
