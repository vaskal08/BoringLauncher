package org.boringreport.boringlauncher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SCREEN_PADDING = 24.dp
private val ROW_SPACING = 18.dp

/**
 * Lets the user build the home screen: create folders, name them, and check which apps belong
 * in each. Anything left unchecked simply does not appear on the home screen.
 *
 * Edits are written straight through to [FolderStore], so there is nothing to save or discard.
 */
private sealed interface Editing {
    data class UserFolder(val index: Int) : Editing
    data object EverythingElse : Editing
}

@Composable
fun CustomizeScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { LauncherConfigStore(context) }
    val allApps = remember { loadAllApps(context) }

    var config by remember { mutableStateOf(store.load()) }
    var editing by remember { mutableStateOf<Editing?>(null) }

    fun update(newConfig: LauncherConfig) {
        config = newConfig
        store.save(newConfig)
    }

    when (val target = editing) {
        is Editing.UserFolder -> {
            val folder = config.folders.getOrNull(target.index)
            if (folder == null) {
                editing = null
                return
            }

            BackHandler { editing = null }
            FolderEditor(
                folder = folder,
                allApps = allApps,
                onChange = { updated ->
                    val folders = config.folders.toMutableList()
                    folders[target.index] = updated
                    update(config.copy(folders = folders))
                },
                onBack = { editing = null },
                modifier = modifier
            )
        }

        Editing.EverythingElse -> {
            BackHandler { editing = null }
            EverythingElseEditor(
                apps = remainderApps(allApps, config.folders),
                hidden = config.hidden,
                onToggle = { packageName ->
                    val hidden = if (packageName in config.hidden) {
                        config.hidden - packageName
                    } else {
                        config.hidden + packageName
                    }
                    update(config.copy(hidden = hidden))
                },
                onBack = { editing = null },
                modifier = modifier
            )
        }

        null -> {
            BackHandler { onDone() }
            FolderList(
                folders = config.folders,
                onOpen = { editing = Editing.UserFolder(it) },
                onOpenEverythingElse = { editing = Editing.EverythingElse },
                onRemove = { index ->
                    update(config.copy(folders = config.folders.filterIndexed { i, _ -> i != index }))
                },
                onAdd = {
                    val newIndex = config.folders.size
                    update(
                        config.copy(
                            folders = config.folders + Folder("new folder", emptyList())
                        )
                    )
                    editing = Editing.UserFolder(newIndex)
                },
                onDone = onDone,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun FolderList(
    folders: List<Folder>,
    onOpen: (Int) -> Unit,
    onOpenEverythingElse: () -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING)
    ) {
        item {
            Spacer(Modifier.height(60.dp))
            ScreenTitle("folders")
        }

        itemsIndexed(folders) { index, folder ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpen(index) }
                ) {
                    Text(
                        text = folder.name.lowercase(),
                        fontSize = 26.sp,
                        fontFamily = FontManager.fontFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = appCount(folder.packages.size),
                        fontSize = 13.sp,
                        fontFamily = FontManager.fontFamily,
                        color = Color.Gray
                    )
                }

                Text(
                    text = "remove",
                    fontSize = 13.sp,
                    fontFamily = FontManager.fontFamily,
                    color = Color.Gray,
                    modifier = Modifier.clickable { onRemove(index) }
                )
            }
        }

        item {
            // Always present and always last, so it has no remove affordance - but its
            // contents can still be pruned.
            Column(modifier = Modifier.clickable { onOpenEverythingElse() }) {
                Text(
                    text = EVERYTHING_ELSE,
                    fontSize = 26.sp,
                    fontFamily = FontManager.fontFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "it's... well it's everything else",
                    fontSize = 13.sp,
                    fontFamily = FontManager.fontFamily,
                    color = Color.Gray
                )
            }
        }

        item {
            Spacer(Modifier.height(ROW_SPACING))
            ActionText(text = "+ new folder", onClick = onAdd)
            Spacer(Modifier.height(ROW_SPACING))
            ActionText(text = "done", onClick = onDone)
            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
private fun FolderEditor(
    folder: Folder,
    allApps: List<LauncherApp>,
    onChange: (Folder) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val checked = folder.packages.toSet()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING)
    ) {
        item {
            Spacer(Modifier.height(60.dp))
            ActionText(text = "← folders", onClick = onBack)
            Spacer(Modifier.height(8.dp))

            // The folder name doubles as the heading the home screen shows for this group.
            BasicTextField(
                value = folder.name,
                onValueChange = { onChange(folder.copy(name = it)) },
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 30.sp,
                    fontFamily = FontManager.fontFamily,
                    fontWeight = FontWeight.Bold
                ),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = appCount(checked.size),
                fontSize = 13.sp,
                fontFamily = FontManager.fontFamily,
                color = Color.Gray
            )
        }

        items(allApps, key = { it.packageName }) { app ->
            AppCheckRow(
                label = app.label,
                checked = app.packageName in checked,
                onClick = {
                    val packages = if (app.packageName in checked) {
                        folder.packages - app.packageName
                    } else {
                        folder.packages + app.packageName
                    }
                    onChange(folder.copy(packages = packages))
                }
            )
        }

        item { Spacer(Modifier.height(60.dp)) }
    }
}

/**
 * Prunes "everything else". Every app it covers starts checked, so this screen only ever adds
 * to the hidden set - unchecking an app is the one way something disappears from the launcher
 * entirely.
 */
@Composable
private fun EverythingElseEditor(
    apps: List<LauncherApp>,
    hidden: Set<String>,
    onToggle: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = SCREEN_PADDING),
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING)
    ) {
        item {
            Spacer(Modifier.height(60.dp))
            ActionText(text = "← folders", onClick = onBack)
            Spacer(Modifier.height(8.dp))
            Text(
                text = EVERYTHING_ELSE,
                fontSize = 30.sp,
                fontFamily = FontManager.fontFamily,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "uncheck to hide",
                fontSize = 13.sp,
                fontFamily = FontManager.fontFamily,
                color = Color.Gray
            )
        }

        items(apps, key = { it.packageName }) { app ->
            AppCheckRow(
                label = app.label,
                checked = app.packageName !in hidden,
                onClick = { onToggle(app.packageName) }
            )
        }

        item { Spacer(Modifier.height(60.dp)) }
    }
}

@Composable
private fun AppCheckRow(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Text(
            text = if (checked) "✓" else "",
            fontSize = 20.sp,
            fontFamily = FontManager.fontFamily,
            color = Color.White,
            modifier = Modifier.width(32.dp)
        )
        Text(
            text = label.lowercase(),
            fontSize = 20.sp,
            fontFamily = FontManager.fontFamily,
            color = if (checked) Color.White else Color.Gray
        )
    }
}

@Composable
private fun ScreenTitle(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontFamily = FontManager.fontFamily,
        color = Color.LightGray
    )
}

@Composable
private fun ActionText(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 20.sp,
        fontFamily = FontManager.fontFamily,
        color = Color.LightGray,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private fun appCount(count: Int): String = if (count == 1) "1 app" else "$count apps"
