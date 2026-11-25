package hu.atiranyito

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Ébresztők beállítása minden ütemezés kezdetére és végére. */
object Scheduler {
    const val ACTION_TICK = "hu.atiranyito.TICK"

    fun rescheduleAll(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        Store.schedules(ctx).forEach { s ->
            cancel(ctx, s.id)
            if (!s.enabled) return@forEach
            if (s.startMillis > now) set(am, ctx, s.startMillis, requestCode(s.id, start = true))
            if (s.endMillis > now) set(am, ctx, s.endMillis, requestCode(s.id, start = false))
        }
    }

    fun cancel(ctx: Context, scheduleId: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        listOf(true, false).forEach { start ->
            pending(ctx, requestCode(scheduleId, start), PendingIntent.FLAG_NO_CREATE)?.let {
                am.cancel(it); it.cancel()
            }
        }
    }

    fun canScheduleExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    @SuppressLint("MissingPermission", "ScheduleExactAlarm")
    private fun set(am: AlarmManager, ctx: Context, at: Long, code: Int) {
        val pi = pending(ctx, code, 0)!!
        if (canScheduleExact(ctx)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            // Ha nincs engedély pontos ébresztőre, pár perc csúszás lehet
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun requestCode(id: Long, start: Boolean): Int =
        ((id xor (id ushr 32)).toInt() * 2) + if (start) 0 else 1

    private fun pending(ctx: Context, code: Int, extraFlags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            ctx, code,
            Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT or extraFlags
        )
}

/** Ébresztő érkezésekor kiértékeli az aktuális állapotot. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Forwarding.applyCurrentState(ctx)
    }
}

/** Újraindítás / időváltozás után visszaállítja az ébresztőket. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Scheduler.rescheduleAll(ctx)
        Forwarding.applyCurrentState(ctx)
    }
}
