package com.focusguard.blocker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
 * Home-screen widget for the schedule master switch.
 *
 * One tap flips [BlockConfig.schedulesEnabled]. Per-schedule switches, the general block and the
 * overall blocking switch keep their own state. Tapping the card itself opens the app.
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
        if (intent.action != ACTION_TOGGLE_SCHEDULES) {
            super.onReceive(context, intent)
            return
        }
        val appContext = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                // BlockRepo.update re-renders the widget once the new value is stored.
                BlockRepo(appContext).update { it.copy(schedulesEnabled = !it.schedulesEnabled) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_SCHEDULES = "com.focusguard.blocker.action.TOGGLE_SCHEDULES"

        private const val REQUEST_TOGGLE = 1
        private const val REQUEST_OPEN_APP = 2

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

            val status = context.getString(
                when {
                    !schedulesOn -> R.string.widget_schedule_paused
                    total == 0 -> R.string.widget_schedule_empty
                    else -> R.string.widget_schedule_on
                }
            )
            val detail = when {
                total == 0 -> context.getString(R.string.widget_schedule_no_schedules)
                schedulesOn -> context.getString(R.string.widget_schedule_counts, active, total)
                else -> context.getString(R.string.widget_schedule_counts_paused, active, total)
            } + if (config.masterEnabled) "" else
                " • " + context.getString(R.string.widget_schedule_blocking_paused)

            val views = RemoteViews(context.packageName, R.layout.widget_schedule)
            views.setTextViewText(R.id.widget_status, status)
            views.setTextViewText(R.id.widget_detail, detail)
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
            views.setOnClickPendingIntent(R.id.widget_toggle, toggleIntent(context))
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            return views
        }

        private fun toggleIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_TOGGLE,
            Intent(context, ScheduleWidgetProvider::class.java).setAction(ACTION_TOGGLE_SCHEDULES),
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
