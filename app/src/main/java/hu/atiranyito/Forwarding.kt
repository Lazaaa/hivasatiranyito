package hu.atiranyito

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Hívásátirányítás be/kikapcsolása a szabványos GSM MMI-kódokkal.
 *
 *  - Feltétel nélküli átirányítás be:  **21*<szám>#
 *  - Feltétel nélküli átirányítás ki:  ##21#
 *
 * Ha minden feltételes átirányítást szeretnél (foglalt / nem fogadott / nem elérhető)
 * a feltétel nélküli helyett, írd át a kódokat: be "**004*<szám>#", ki "##004#".
 */
object Forwarding {
    private const val TAG = "Forwarding"
    private const val CHANNEL = "forwarding"

    fun enableCode(number: String) = "**21*${number.filter { it.isDigit() || it == '+' }}#"
    const val DISABLE_CODE = "##21#"

    fun hasCallPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Megnézi, melyik ütemezés aktív most, és ennek megfelelően
     * be-, át- vagy kikapcsolja az átirányítást. Csak akkor "tárcsáz",
     * ha az állapot ténylegesen változik.
     */
    fun applyCurrentState(ctx: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val active = Store.schedules(ctx)
            .filter { it.enabled && it.startMillis <= now && now < it.endMillis }
            .maxByOrNull { it.startMillis } // átfedés esetén a később kezdődő nyer
        val contact = active?.let { Store.contact(ctx, it.contactId) }
        val wanted = contact?.number
        val current = Store.activeNumber(ctx)

        if (!force && wanted == current) return

        if (contact != null && wanted != null) {
            if (dial(ctx, enableCode(wanted))) {
                Store.setActiveNumber(ctx, wanted)
                notify(ctx, ctx.getString(R.string.notif_on, contact.nickname, wanted))
            }
        } else {
            if (dial(ctx, DISABLE_CODE)) {
                Store.setActiveNumber(ctx, null)
                notify(ctx, ctx.getString(R.string.notif_off))
            }
        }
    }

    /** Kézi kikapcsolás, az ütemezésektől függetlenül. */
    fun disableNow(ctx: Context) {
        if (dial(ctx, DISABLE_CODE)) {
            Store.setActiveNumber(ctx, null)
            notify(ctx, ctx.getString(R.string.notif_off))
        }
    }

    /** MMI-kód "tárcsázása". Háttérből is működik a TelecomManager.placeCall-lal. */
    @SuppressLint("MissingPermission")
    private fun dial(ctx: Context, code: String): Boolean {
        if (!hasCallPermission(ctx)) {
            notify(ctx, ctx.getString(R.string.notif_no_permission))
            return false
        }
        val uri = Uri.fromParts("tel", code, null) // a '#'-et helyesen kódolja (%23)
        return try {
            val telecom = ctx.getSystemService(TelecomManager::class.java)
            telecom.placeCall(uri, null)
            Log.i(TAG, "placeCall: $code")
            true
        } catch (e: Exception) {
            Log.w(TAG, "placeCall nem sikerült, ACTION_CALL próbálása", e)
            try {
                ctx.startActivity(
                    Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            } catch (e2: Exception) {
                Log.e(TAG, "Nem sikerült a kód tárcsázása", e2)
                notify(ctx, ctx.getString(R.string.notif_failed))
                false
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun notify(ctx: Context, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL, ctx.getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_forward)
            .setContentTitle(ctx.getString(R.string.app_name))
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        nm.notify(1, n)
    }
}
