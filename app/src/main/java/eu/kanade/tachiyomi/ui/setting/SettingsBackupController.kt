package eu.kanade.tachiyomi.ui.setting

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceScreen
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsMultiChoice
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.BackupConst
import eu.kanade.tachiyomi.data.backup.BackupCreateService
import eu.kanade.tachiyomi.data.backup.BackupCreatorJob
import eu.kanade.tachiyomi.data.backup.BackupRestoreService
import eu.kanade.tachiyomi.data.backup.full.FullBackupRestoreValidator
import eu.kanade.tachiyomi.data.backup.full.models.BackupFull
import eu.kanade.tachiyomi.data.backup.legacy.LegacyBackupRestoreValidator
import eu.kanade.tachiyomi.data.backup.legacy.models.Backup
import eu.kanade.tachiyomi.data.preference.asImmediateFlow
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.requestPermissionsSafe
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.editTextPreference
import eu.kanade.tachiyomi.util.preference.entriesRes
import eu.kanade.tachiyomi.util.preference.intListPreference
import eu.kanade.tachiyomi.util.preference.onChange
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.toast
import exh.log.xLogD
import exh.tachidesk.TachideskBackupHandler
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsBackupController : SettingsController() {

    /**
     * Flags containing information of what to backup.
     */
    private var backupFlags = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requestPermissionsSafe(arrayOf(WRITE_EXTERNAL_STORAGE), 500)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.backup

        preferenceCategory {
            titleRes = R.string.backup

            preference {
                key = "pref_create_backup"
                titleRes = R.string.pref_create_backup
                summaryRes = R.string.pref_create_backup_summ

                onClick { backup(context, BackupConst.BACKUP_TYPE_FULL) }
            }
            preference {
                key = "pref_create_legacy_backup"
                titleRes = R.string.pref_create_legacy_backup
                summaryRes = R.string.pref_create_legacy_backup_summary

                onClick { backup(context, BackupConst.BACKUP_TYPE_LEGACY) }
            }
            preference {
                key = "pref_restore_backup"
                titleRes = R.string.pref_restore_backup
                summaryRes = R.string.pref_restore_backup_summ

                onClick {
                    if (!BackupRestoreService.isRunning(context)) {
                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        val title = resources?.getString(R.string.file_select_backup)
                        val chooser = Intent.createChooser(intent, title)
                        startActivityForResult(chooser, CODE_BACKUP_RESTORE)
                    } else {
                        context.toast(R.string.restore_in_progress)
                    }
                }
            }
        }
        preferenceCategory {
            titleRes = R.string.pref_backup_service_category

            intListPreference {
                key = Keys.backupInterval
                titleRes = R.string.pref_backup_interval
                entriesRes = arrayOf(
                    R.string.update_never,
                    R.string.update_6hour,
                    R.string.update_12hour,
                    R.string.update_24hour,
                    R.string.update_48hour,
                    R.string.update_weekly
                )
                entryValues = arrayOf("0", "6", "12", "24", "48", "168")
                defaultValue = "0"
                summary = "%s"

                onChange { newValue ->
                    val interval = (newValue as String).toInt()
                    BackupCreatorJob.setupTask(context, interval)
                    true
                }
            }
            preference {
                key = Keys.backupDirectory
                titleRes = R.string.pref_backup_directory

                onClick {
                    try {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                        startActivityForResult(intent, CODE_BACKUP_DIR)
                    } catch (e: ActivityNotFoundException) {
                        activity?.toast(R.string.file_picker_error)
                    }
                }

                preferences.backupInterval().asImmediateFlow { isVisible = it > 0 }
                    .launchIn(viewScope)

                preferences.backupsDirectory().asFlow()
                    .onEach { path ->
                        val dir = UniFile.fromUri(context, path.toUri())
                        summary = dir.filePath + "/automatic"
                    }
                    .launchIn(viewScope)
            }
            intListPreference {
                key = Keys.numberOfBackups
                titleRes = R.string.pref_backup_slots
                entries = arrayOf("1", "2", "3", "4", "5")
                entryValues = entries
                defaultValue = "1"
                summary = "%s"

                preferences.backupInterval().asImmediateFlow { isVisible = it > 0 }
                    .launchIn(viewScope)
            }
            switchPreference {
                key = Keys.createLegacyBackup
                titleRes = R.string.pref_backup_auto_create_legacy
                defaultValue = true

                preferences.backupInterval().asImmediateFlow { isVisible = it > 0 }
                    .launchIn(viewScope)
            }
        }
        preferenceCategory {
            title = "Tachidesk"

            editTextPreference {
                key = Keys.tachideskUrl
                title = "Tachidesk Url"
                summary = "The url for a tachidesk server, example: \"http://192.168.0.2:4567\""
                defaultValue = ""
            }
            preference {
                key = "pref_create_legacy_backup_tachidesk"
                title = "Send legacy backup to Tachidesk"
                summary = "Create a new legacy backup and send it to a Tachidesk Server"

                onClick { backup(context, BackupConst.BACKUP_TYPE_LEGACY + 10) }
            }
            preference {
                key = "pref_restore_legacy_backup_tachidesk"
                title = "Restore backup from Tachidesk"
                summary = "Restore a new legacy backup from a Tachidesk Server"

                onClick {
                    GlobalScope.launch(CoroutineExceptionHandler { _, _ -> }) {
                        var ex: Exception? = null
                        val backup = try {
                            TachideskBackupHandler.getBackup(
                                File(context.cacheDir, Backup.getDefaultFilename()).absolutePath
                            )?.toUri()
                        } catch (e: Exception) {
                            ex = e
                            null
                        }

                        withContext(Dispatchers.Main) {
                            if (backup != null) {
                                onActivityResult(CODE_BACKUP_RESTORE, Activity.RESULT_OK, Intent().also { it.data = backup })
                            } else {
                                if (ex != null) {
                                    xLogD("Error getting backup", ex)
                                }

                                activity?.toast("Unable to get Tachidesk backup file")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (data != null && resultCode == Activity.RESULT_OK) {
            val activity = activity ?: return
            val uri = data.data

            when (requestCode) {
                CODE_BACKUP_DIR -> {
                    // Get UriPermission so it's possible to write files
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                    if (uri != null) {
                        activity.contentResolver.takePersistableUriPermission(uri, flags)
                    }

                    // Set backup Uri
                    preferences.backupsDirectory().set(uri.toString())
                }
                CODE_FULL_BACKUP_CREATE, CODE_LEGACY_BACKUP_CREATE, CODE_LEGACY_BACKUP_CREATE + 10 -> {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                    if (uri != null) {
                        activity.contentResolver.takePersistableUriPermission(uri, flags)
                    }

                    val file = UniFile.fromUri(activity, uri)

                    activity.toast(R.string.creating_backup)

                    BackupCreateService.start(
                        activity,
                        file.uri,
                        backupFlags,
                        when (requestCode) {
                            CODE_FULL_BACKUP_CREATE -> BackupConst.BACKUP_TYPE_FULL
                            CODE_LEGACY_BACKUP_CREATE -> BackupConst.BACKUP_TYPE_LEGACY
                            CODE_LEGACY_BACKUP_CREATE + 10 -> BackupConst.BACKUP_TYPE_LEGACY + 10
                            else -> throw RuntimeException("This should not happen")
                        }
                    )
                }
                CODE_BACKUP_RESTORE -> {
                    uri?.path?.let {
                        val fileName = DocumentFile.fromSingleUri(activity, uri)?.name ?: uri.toString()
                        when {
                            fileName.endsWith(".proto.gz") -> {
                                RestoreBackupDialog(
                                    uri,
                                    BackupConst.BACKUP_TYPE_FULL
                                ).showDialog(router)
                            }
                            fileName.endsWith(".json") -> {
                                RestoreBackupDialog(
                                    uri,
                                    BackupConst.BACKUP_TYPE_LEGACY
                                ).showDialog(router)
                            }
                            else -> {
                                activity.toast(activity.getString(R.string.invalid_backup_file_type, fileName))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun backup(context: Context, type: Int) {
        if (!BackupCreateService.isRunning(context)) {
            val ctrl = CreateBackupDialog(type)
            ctrl.targetController = this@SettingsBackupController
            ctrl.showDialog(router)
        } else {
            context.toast(R.string.backup_in_progress)
        }
    }

    fun createBackup(flags: Int, type: Int) {
        backupFlags = flags
        val code = when (type) {
            BackupConst.BACKUP_TYPE_FULL -> CODE_FULL_BACKUP_CREATE
            BackupConst.BACKUP_TYPE_LEGACY + 10 -> CODE_LEGACY_BACKUP_CREATE + 10
            else -> CODE_LEGACY_BACKUP_CREATE
        }
        val fileName = when (type) {
            BackupConst.BACKUP_TYPE_FULL -> BackupFull.getDefaultFilename()
            else -> Backup.getDefaultFilename()
        }

        try {
            // Use Android's built-in file creator
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/*")
                .putExtra(Intent.EXTRA_TITLE, fileName)

            startActivityForResult(intent, code)
        } catch (e: ActivityNotFoundException) {
            activity?.toast(R.string.file_picker_error)
        }
    }

    class CreateBackupDialog(bundle: Bundle? = null) : DialogController(bundle) {
        constructor(type: Int) : this(
            bundleOf(
                KEY_TYPE to type
            )
        )

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val type = args.getInt(KEY_TYPE)
            val activity = activity!!
            val options = arrayOf(
                R.string.manga,
                R.string.categories,
                R.string.chapters,
                R.string.track,
                R.string.history,
                // SY -->
                R.string.custom_manga_info
                // SY <--
            )
                .map { activity.getString(it) }

            return MaterialDialog(activity)
                .title(R.string.pref_create_backup)
                .message(R.string.backup_choice)
                .listItemsMultiChoice(
                    items = options,
                    disabledIndices = intArrayOf(0),
                    initialSelection = intArrayOf(0, 1, 2, 3, 4 /* SY --> */, 5 /* SY <-- */)
                ) { _, positions, _ ->
                    var flags = 0
                    for (i in 1 until positions.size) {
                        when (positions[i]) {
                            1 -> flags = flags or BackupCreateService.BACKUP_CATEGORY
                            2 -> flags = flags or BackupCreateService.BACKUP_CHAPTER
                            3 -> flags = flags or BackupCreateService.BACKUP_TRACK
                            4 -> flags = flags or BackupCreateService.BACKUP_HISTORY
                            // SY -->
                            5 -> flags = flags or BackupCreateService.BACKUP_CUSTOM_INFO
                            // SY <--
                        }
                    }

                    (targetController as? SettingsBackupController)?.createBackup(flags, type)
                }
                .positiveButton(R.string.action_create)
                .negativeButton(android.R.string.cancel)
        }

        private companion object {
            const val KEY_TYPE = "CreateBackupDialog.type"
        }
    }

    class RestoreBackupDialog(bundle: Bundle? = null) : DialogController(bundle) {
        constructor(uri: Uri, type: Int) : this(
            bundleOf(
                KEY_URI to uri,
                KEY_TYPE to type
            )
        )

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val activity = activity!!
            val uri: Uri = args.getParcelable(KEY_URI)!!
            val type: Int = args.getInt(KEY_TYPE)

            return try {
                var message = if (type == BackupConst.BACKUP_TYPE_FULL) {
                    activity.getString(R.string.backup_restore_content_full)
                } else {
                    activity.getString(R.string.backup_restore_content)
                }

                val validator = if (type == BackupConst.BACKUP_TYPE_FULL) {
                    FullBackupRestoreValidator()
                } else {
                    LegacyBackupRestoreValidator()
                }

                val results = validator.validate(activity, uri)
                if (results.missingSources.isNotEmpty()) {
                    message += "\n\n${activity.getString(R.string.backup_restore_missing_sources)}\n${results.missingSources.joinToString("\n") { "- $it" }}"
                }
                if (results.missingTrackers.isNotEmpty()) {
                    message += "\n\n${activity.getString(R.string.backup_restore_missing_trackers)}\n${results.missingTrackers.joinToString("\n") { "- $it" }}"
                }

                MaterialDialog(activity)
                    .title(R.string.pref_restore_backup)
                    .message(text = message)
                    .positiveButton(R.string.action_restore) {
                        BackupRestoreService.start(activity, uri, type)
                    }
            } catch (e: Exception) {
                MaterialDialog(activity)
                    .title(R.string.invalid_backup_file)
                    .message(text = e.message)
                    .positiveButton(android.R.string.cancel)
            }
        }

        private companion object {
            const val KEY_URI = "RestoreBackupDialog.uri"
            const val KEY_TYPE = "RestoreBackupDialog.type"
        }
    }

    private companion object {
        const val CODE_LEGACY_BACKUP_CREATE = 501
        const val CODE_BACKUP_DIR = 503
        const val CODE_FULL_BACKUP_CREATE = 504
        const val CODE_BACKUP_RESTORE = 505
    }
}
