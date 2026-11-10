package com.focusguard.blocker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.focusguard.blocker.BlockConfig
import com.focusguard.blocker.BlockRepo
import com.focusguard.blocker.BlockStore
import com.focusguard.blocker.MainActivity
import com.focusguard.blocker.R
import com.focusguard.blocker.blockDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Home-screen widget with one switch per rule group.
 *
 * - The master pill flips [BlockConfig.schedulesEnabled]: every schedule pauses, while the general
 *   block and the overall blocking switch keep their own state.
 * - The schedule row flips the own switch of the schedule at the top of the Schedules list
 *   ([BlockConfig.primarySchedule]).
 * - The general row flips [BlockConfig.generalEnabled].
 *
 * Tapping the card itself opens the app.
 */
class ScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val appContext = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                val config = BlockStore.current(appContext.blockDataStore)
                appWidgetIds.forEach { id ->
                    appWidgetManager.updateAppWidget(id, buildViews(appContext, config))
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, NO_SCHEDULE_ID)
        val transform: ((BlockConfig) -> BlockConfig)? = when (intent.action) {
            ACTION_TOGGLE_SCHEDULES -> { config -> config.copy(schedulesEnabled = !config.schedulesEnabled) }
            ACTION_TOGGLE_SCHEDULE -> { config -> config.toggleSchedule(scheduleId) }
            ACTION_TOGGLE_GENERAL -> { config -> config.toggleGeneral() }
            else -> null
        }
        if (transform == null) {
            super.onReceive(context, intent)
            return
        }
        val appContext = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                // BlockRepo.update re-renders the widget once the new value is stored.
                BlockRepo(appContext).update(transform)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_SCHEDULES = "com.focusguard.blocker.action.TOGGLE_SCHEDULES"
        const val ACTION_TOGGLE_SCHEDULE = "com.focusguard.blocker.action.TOGGLE_SCHEDULE"
        const val ACTION_TOGGLE_GENERAL = "com.focusguard.blocker.action.TOGGLE_GENERAL"

        private const val EXTRA_SCHEDULE_ID = "com.focusguard.blocker.extra.SCHEDULE_ID"
        private const val NO_SCHEDULE_ID = -1L

        private const val REQUEST_TOGGLE_SCHEDULES = 1
        private const val REQUEST_OPEN_APP = 2
        private const val REQUEST_TOGGLE_GENERAL = 3
        private const val REQUEST_TOGGLE_SCHEDULE = 4

        // Widget work is short and rare, so one small background scope is enough.
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Re-renders every placed widget, for example after the config changed in the app. */
        fun refresh(context: Context) {
            val appContext = context.applicationContext
            scope.launch {
                runCatching {
                    val manager = AppWidgetManager.getInstance(appContext)
                    val ids = manager.getAppWidgetIds(
                        ComponentName(appContext, ScheduleWidgetProvider::class.java)
                    )
                    if (ids.isEmpty()) return@runCatching
                    val config = BlockStore.current(appContext.blockDataStore)
                    ids.forEach { id ->
                        manager.updateAppWidget(id, buildViews(appContext, config))
                    }
                }
            }
        }

        private fun buildViews(context: Context, config: BlockConfig): RemoteViews {
            val total = config.schedules.size
            val active = config.schedules.count { it.enabled }
            val schedulesOn = config.schedulesEnabled
            val topSchedule = config.primarySchedule()

            val status = when {
                total == 0 -> context.getString(R.string.widget_schedule_empty)
                schedulesOn -> context.getString(R.string.widget_schedule_status_on, active, total)
                else -> context.getString(R.string.widget_schedule_status_paused, active, total)
            }
            // The counts live in the status line, so the hint stays empty whenever it has nothing to add.
            val hint = when {
                total == 0 -> context.getString(R.string.widget_schedule_no_schedules)
                !config.masterEnabled -> context.getString(R.string.widget_schedule_blocking_paused)
                else -> null
            }

            val views = RemoteViews(context.packageName, R.layout.widget_schedule)
            views.setTextViewText(R.id.widget_status, status)
            views.setTextViewText(R.id.widget_detail, hint.orEmpty())
            views.setViewVisibility(
                R.id.widget_detail,
                if (hint == null) View.GONE else View.VISIBLE,
            )

            // Master switch for every schedule.
            views.setTextViewText(
                R.id.widget_toggle,
                context.getString(
                    if (schedulesOn) R.string.widget_schedule_pause else R.string.widget_schedule_resume
                )
            )
            views.setInt(
                R.id.widget_toggle,
                "setBackgroundResource",
                if (schedulesOn) R.drawable.widget_toggle_pause else R.drawable.widget_toggle_resume,
            )
            views.setOnClickPendingIntent(R.id.widget_toggle, toggleSchedulesIntent(context))
            views.setOnClickPendingIntent(R.id.widget_master_row, toggleSchedulesIntent(context))

            // Own switch of the schedule at the top of the list.
            if (topSchedule == null) {
                views.setViewVisibility(R.id.widget_schedule_row, View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_schedule_row, View.VISIBLE)
                views.setTextViewText(
                    R.id.widget_schedule_name,
                    topSchedule.name.ifBlank { context.getString(R.string.widget_schedule_unnamed) },
                )
                applyPill(context, views, R.id.widget_schedule_pill, topSchedule.enabled)
                views.setOnClickPendingIntent(
                    R.id.widget_schedule_row,
                    toggleScheduleIntent(context, topSchedule.id),
                )
            }

            // General block switch.
            applyPill(context, views, R.id.widget_general_pill, config.generalEnabled)
            views.setOnClickPendingIntent(R.id.widget_general_row, toggleGeneralIntent(context))

            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            return views
        }

        private fun applyPill(context: Context, views: RemoteViews, viewId: Int, on: Boolean) {
            views.setTextViewText(
                viewId,
                context.getString(if (on) R.string.widget_state_on else R.string.widget_state_off),
            )
            views.setInt(
                viewId,
                "setBackgroundResource",
                if (on) R.drawable.widget_pill_on else R.drawable.widget_pill_off,
            )
        }

        private fun actionIntent(context: Context, action: String): Intent =
            Intent(context, ScheduleWidgetProvider::class.java).setAction(action)

        private fun toggleSchedulesIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_TOGGLE_SCHEDULES,
            actionIntent(context, ACTION_TOGGLE_SCHEDULES),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** The data URI keeps one pending intent per schedule id, so the click follows the top row. */
        private fun toggleScheduleIntent(context: Context, scheduleId: Long): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                REQUEST_TOGGLE_SCHEDULE,
                actionIntent(context, ACTION_TOGGLE_SCHEDULE)
                    .setData(Uri.parse("focusguard://widget/schedule/$scheduleId"))
                    .putExtra(EXTRA_SCHEDULE_ID, scheduleId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun toggleGeneralIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_TOGGLE_GENERAL,
            actionIntent(context, ACTION_TOGGLE_GENERAL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
