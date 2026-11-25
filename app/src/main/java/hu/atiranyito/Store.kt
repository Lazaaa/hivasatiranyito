package hu.atiranyito

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Egy becenév és a hozzá tartozó telefonszám. */
data class Contact(val id: Long, val nickname: String, val number: String)

/** Egy átirányítási időszak: [startMillis, endMillis) között a contactId számára. */
data class Schedule(
    val id: Long,
    val contactId: Long,
    val startMillis: Long,
    val endMillis: Long,
    val enabled: Boolean = true,
)

/** Egyszerű, SharedPreferences + JSON alapú tároló. */
object Store {
    private const val PREFS = "atiranyito"
    private const val KEY_CONTACTS = "contacts"
    private const val KEY_SCHEDULES = "schedules"
    private const val KEY_ACTIVE_NUMBER = "active_number"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- Becenevek ----------

    fun contacts(ctx: Context): List<Contact> {
        val arr = JSONArray(prefs(ctx).getString(KEY_CONTACTS, "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Contact(o.getLong("id"), o.getString("nickname"), o.getString("number"))
        }.sortedBy { it.nickname.lowercase() }
    }

    fun contact(ctx: Context, id: Long): Contact? = contacts(ctx).firstOrNull { it.id == id }

    fun saveContact(ctx: Context, c: Contact) {
        val list = contacts(ctx).filter { it.id != c.id } + c
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("nickname", it.nickname).put("number", it.number))
        }
        prefs(ctx).edit().putString(KEY_CONTACTS, arr.toString()).apply()
    }

    fun deleteContact(ctx: Context, id: Long) {
        val arr = JSONArray()
        contacts(ctx).filter { it.id != id }.forEach {
            arr.put(JSONObject().put("id", it.id).put("nickname", it.nickname).put("number", it.number))
        }
        prefs(ctx).edit().putString(KEY_CONTACTS, arr.toString()).apply()
        // A hozzá tartozó ütemezések is törlődnek
        schedules(ctx).filter { it.contactId == id }.forEach { deleteSchedule(ctx, it.id) }
    }

    // ---------- Ütemezések ----------

    fun schedules(ctx: Context): List<Schedule> {
        val arr = JSONArray(prefs(ctx).getString(KEY_SCHEDULES, "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Schedule(
                o.getLong("id"), o.getLong("contactId"),
                o.getLong("start"), o.getLong("end"), o.optBoolean("enabled", true),
            )
        }.sortedBy { it.startMillis }
    }

    fun schedule(ctx: Context, id: Long): Schedule? = schedules(ctx).firstOrNull { it.id == id }

    private fun writeSchedules(ctx: Context, list: List<Schedule>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("contactId", it.contactId)
                    .put("start", it.startMillis).put("end", it.endMillis).put("enabled", it.enabled)
            )
        }
        prefs(ctx).edit().putString(KEY_SCHEDULES, arr.toString()).apply()
    }

    fun saveSchedule(ctx: Context, s: Schedule) =
        writeSchedules(ctx, schedules(ctx).filter { it.id != s.id } + s)

    fun deleteSchedule(ctx: Context, id: Long) =
        writeSchedules(ctx, schedules(ctx).filter { it.id != id })

    // ---------- Állapot: melyik számra van épp átirányítva (az app szerint) ----------

    fun activeNumber(ctx: Context): String? = prefs(ctx).getString(KEY_ACTIVE_NUMBER, null)

    fun setActiveNumber(ctx: Context, number: String?) =
        prefs(ctx).edit().apply {
            if (number == null) remove(KEY_ACTIVE_NUMBER) else putString(KEY_ACTIVE_NUMBER, number)
        }.apply()

    fun newId(): Long = System.currentTimeMillis()
}
