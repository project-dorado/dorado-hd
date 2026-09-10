package com.heretek.dorado_hd.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.heretek.dorado_hd.DoradoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Alarm-clock fire receiver: schedules the chosen alarm ref through the
 * MediaController (canon §8 — the device "wakes you up" with your own
 * playlist or radio station).
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra("alarmId", -1)
        val alarmKind = intent.getIntExtra("alarmKind", 0)
        val refId = intent.getLongExtra("refId", -1)
        if (alarmId < 0) return
        val graph = (context.applicationContext as DoradoApp).graph
        val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        scope.launch {
            // Phase 5 wires radio streams; for now, track alarms play a random
            // recently-added track; radio alarms are a stub.
            if (alarmKind == 0) {
                val track = graph.library.recentlyAdded(1).firstOrNull()
                if (track != null) graph.controller.play(listOf(track))
            }
        }

        scope.launch {
            val alarm = graph.alarms.byId(alarmId) ?: return@launch
            if (alarm.enabled) {
                val next = AlarmScheduler.nextFireMs(alarm.hour, alarm.minute, System.currentTimeMillis(), alarm.daysOfWeek)
                val pi = android.app.PendingIntent.getBroadcast(
                    context,
                    alarm.id.toInt(),
                    Intent(context, AlarmReceiver::class.java).apply {
                        putExtra("alarmId", alarm.id)
                        putExtra("alarmKind", alarm.alarmKind)
                        putExtra("refId", alarm.refId)
                    },
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                )
                am.set(android.app.AlarmManager.RTC_WAKEUP, next, pi)
            }
        }
    }
}
