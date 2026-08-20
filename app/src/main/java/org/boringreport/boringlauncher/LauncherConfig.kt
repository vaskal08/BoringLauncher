package org.boringreport.boringlauncher

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.File
import java.io.IOException

/**
 * Reserved: the home screen appends an "everything else" drawer itself, computed from whatever
 * is not in a user folder. A stored folder by that name is ignored, which also migrates the
 * one the seed asset used to define - the next save drops it from disk.
 */
const val EVERYTHING_ELSE = "everything else"

private const val CONFIG_FILE = "folders.json"
private const val SEED_ASSET = "default_folders.json"
private const val TAG = "LauncherConfigStore"

/** A folder as the launcher uses it: a name and the packages the user checked into it. */
data class Folder(val name: String, val packages: List<String>)

/**
 * Everything the customization screen can change.
 *
 * [hidden] holds packages the user unchecked inside "everything else". Membership is opt-out
 * rather than opt-in so that a newly installed app shows up on its own; only apps explicitly
 * unchecked stay out of the drawer.
 */
data class LauncherConfig(
    val folders: List<Folder> = emptyList(),
    val hidden: Set<String> = emptySet()
)

/**
 * The on-disk shape, which the seed asset also uses. Every field is nullable because Gson does
 * not enforce Kotlin nullability: a truncated or hand-edited file parses with missing fields
 * regardless of what the declared types say, so the nulls are real and modelled here.
 */
private data class ConfigFile(
    val categories: List<FolderJson>?,
    val hidden: List<String>?
)

private data class FolderJson(val name: String?, val packages: List<String>?)

/**
 * Reads and writes the user's folders and hidden apps.
 *
 * The bundled asset is only a seed, used the first time the launcher runs. From then on the
 * copy in internal storage is the truth, so edits made on the customization screen survive
 * restarts and app updates.
 */
class LauncherConfigStore(private val context: Context) {

    private val file = File(context.filesDir, CONFIG_FILE)

    fun load(): LauncherConfig {
        val json = readSaved() ?: readSeed() ?: return LauncherConfig()

        return try {
            val parsed = Gson().fromJson(json, ConfigFile::class.java)

            LauncherConfig(
                folders = parsed?.categories.orEmpty().mapNotNull { entry ->
                    val name = entry?.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    if (name.equals(EVERYTHING_ELSE, ignoreCase = true)) return@mapNotNull null
                    Folder(name = name, packages = entry.packages?.filterNotNull().orEmpty())
                },
                hidden = parsed?.hidden?.filterNotNull()?.toSet().orEmpty()
            )
        } catch (e: JsonSyntaxException) {
            Log.w(TAG, "Could not parse config, starting empty", e)
            LauncherConfig()
        }
    }

    fun save(config: LauncherConfig) {
        val contents = ConfigFile(
            categories = config.folders.map { FolderJson(it.name, it.packages) },
            hidden = config.hidden.toList()
        )
        try {
            file.writeText(Gson().toJson(contents))
        } catch (e: IOException) {
            Log.w(TAG, "Could not save config", e)
        }
    }

    private fun readSaved(): String? = try {
        if (file.exists()) file.readText() else null
    } catch (e: IOException) {
        Log.w(TAG, "Could not read saved config", e)
        null
    }

    private fun readSeed(): String? = try {
        context.assets.open(SEED_ASSET).bufferedReader().use { it.readText() }
    } catch (e: IOException) {
        Log.w(TAG, "Could not read seed config", e)
        null
    }
}
