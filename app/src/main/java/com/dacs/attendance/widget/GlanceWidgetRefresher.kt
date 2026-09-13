package com.dacs.attendance.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

private const val TAG = "GlanceWidgetRefresher"

/**
 * Re-reads today into every placed widget.
 *
 * The tick is written first, then the widget updated: a session that is
 * still running only recomposes on update, and the widget re-reads when
 * the tick it is keyed on changes (see DacsTimeWidget).
 */
@Singleton
class GlanceWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context
) : WidgetRefresher {

    override suspend fun refresh() {
        try {
            val ids = GlanceAppWidgetManager(context).getGlanceIds(DacsTimeWidget::class.java)
            // Most phones will never have the widget placed. Nothing to do.
            if (ids.isEmpty()) return

            val tick = System.currentTimeMillis()
            ids.forEach { id ->
                updateAppWidgetState(context, id) { prefs -> prefs[RefreshTick] = tick }
            }
            DacsTimeWidget().updateAll(context)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Logged, never thrown: see WidgetRefresher.
            Log.w(TAG, "widget refresh failed", error)
        }
    }
}
