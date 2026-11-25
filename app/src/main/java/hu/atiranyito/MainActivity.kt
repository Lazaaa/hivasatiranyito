package hu.atiranyito

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var empty: TextView
    private val adapter = ScheduleAdapter()

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        empty = findViewById(R.id.empty)
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            if (Store.contacts(this).isEmpty()) {
                AlertDialog.Builder(this)
                    .setMessage(R.string.need_contact_first)
                    .setPositiveButton(R.string.nicknames) { _, _ ->
                        startActivity(Intent(this, ContactsActivity::class.java))
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            } else {
                startActivity(Intent(this, ScheduleEditActivity::class.java))
            }
        }
        findViewById<Button>(R.id.btn_off).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(R.string.confirm_off_now)
                .setPositiveButton(R.string.yes) { _, _ -> Forwarding.disableNow(this); refresh() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        findViewById<Button>(R.id.btn_apply).setOnClickListener {
            Forwarding.applyCurrentState(this, force = true); refresh()
        }

        askPermissions()
    }

    override fun onResume() {
        super.onResume()
        Scheduler.rescheduleAll(this)
        refresh()
    }

    private fun askPermissions() {
        val perms = mutableListOf(Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        permLauncher.launch(perms.toTypedArray())

        if (Build.VERSION.SDK_INT >= 31 && !Scheduler.canScheduleExact(this)) {
            AlertDialog.Builder(this)
                .setMessage(R.string.exact_alarm_needed)
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                    )
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun refresh() {
        val active = Store.activeNumber(this)
        val nick = Store.contacts(this).firstOrNull { it.number == active }?.nickname
        status.text = when {
            !Forwarding.hasCallPermission(this) -> getString(R.string.status_no_permission)
            active == null -> getString(R.string.status_off)
            else -> getString(R.string.status_on, nick ?: active, active)
        }
        val list = Store.schedules(this)
        adapter.items = list
        adapter.notifyDataSetChanged()
        empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_contacts) {
            startActivity(Intent(this, ContactsActivity::class.java)); return true
        }
        return super.onOptionsItemSelected(item)
    }

    // ---------- Lista ----------

    inner class ScheduleAdapter : RecyclerView.Adapter<ScheduleAdapter.VH>() {
        var items: List<Schedule> = emptyList()
        private val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.title)
            val subtitle: TextView = v.findViewById(R.id.subtitle)
            val toggle: SwitchCompat = v.findViewById(R.id.toggle)
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_schedule, p, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val s = items[pos]
            val c = Store.contact(this@MainActivity, s.contactId)
            val now = System.currentTimeMillis()
            val state = when {
                !s.enabled -> getString(R.string.state_disabled)
                now >= s.endMillis -> getString(R.string.state_past)
                now >= s.startMillis -> getString(R.string.state_running)
                else -> getString(R.string.state_waiting)
            }
            h.title.text = "${c?.nickname ?: "?"}  (${c?.number ?: "-"})"
            h.subtitle.text = "${fmt.format(Date(s.startMillis))} → ${fmt.format(Date(s.endMillis))}\n$state"
            h.itemView.alpha = if (now >= s.endMillis || !s.enabled) 0.5f else 1f

            h.toggle.setOnCheckedChangeListener(null)
            h.toggle.isChecked = s.enabled
            h.toggle.setOnCheckedChangeListener { _, checked ->
                Store.saveSchedule(this@MainActivity, s.copy(enabled = checked))
                Scheduler.rescheduleAll(this@MainActivity)
                Forwarding.applyCurrentState(this@MainActivity)
                refresh()
            }
            h.itemView.setOnClickListener {
                startActivity(
                    Intent(this@MainActivity, ScheduleEditActivity::class.java)
                        .putExtra(ScheduleEditActivity.EXTRA_ID, s.id)
                )
            }
            h.itemView.setOnLongClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(R.string.confirm_delete_schedule)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        Scheduler.cancel(this@MainActivity, s.id)
                        Store.deleteSchedule(this@MainActivity, s.id)
                        Forwarding.applyCurrentState(this@MainActivity)
                        refresh()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                true
            }
        }
    }
}
