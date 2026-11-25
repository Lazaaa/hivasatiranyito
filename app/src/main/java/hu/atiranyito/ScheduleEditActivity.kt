package hu.atiranyito

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.DateFormat
import java.util.Calendar

class ScheduleEditActivity : AppCompatActivity() {

    companion object { const val EXTRA_ID = "id" }

    private var editingId: Long? = null
    private val start = Calendar.getInstance()
    private val end = Calendar.getInstance()
    private lateinit var contacts: List<Contact>
    private lateinit var spinner: Spinner
    private lateinit var btnStart: Button
    private lateinit var btnEnd: Button
    private val fmt = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule_edit)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        spinner = findViewById(R.id.spinner_contact)
        btnStart = findViewById(R.id.btn_start)
        btnEnd = findViewById(R.id.btn_end)

        contacts = Store.contacts(this)
        spinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            contacts.map { "${it.nickname}  (${it.number})" }
        )

        // Alapértelmezés: a következő egész órától 1 órán át
        start.apply { add(Calendar.HOUR_OF_DAY, 1); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        end.timeInMillis = start.timeInMillis
        end.add(Calendar.HOUR_OF_DAY, 1)

        val id = intent.getLongExtra(EXTRA_ID, -1L)
        Store.schedule(this, id)?.let { s ->
            editingId = s.id
            title = getString(R.string.edit_schedule)
            start.timeInMillis = s.startMillis
            end.timeInMillis = s.endMillis
            spinner.setSelection(contacts.indexOfFirst { it.id == s.contactId }.coerceAtLeast(0))
        }

        btnStart.setOnClickListener { pick(start) }
        btnEnd.setOnClickListener { pick(end) }
        findViewById<Button>(R.id.btn_save).setOnClickListener { save() }
        updateLabels()
    }

    private fun pick(cal: Calendar) {
        DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            TimePickerDialog(this, { _, h, min ->
                cal.set(Calendar.HOUR_OF_DAY, h)
                cal.set(Calendar.MINUTE, min)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                updateLabels()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateLabels() {
        btnStart.text = getString(R.string.from_label, fmt.format(start.time))
        btnEnd.text = getString(R.string.to_label, fmt.format(end.time))
    }

    private fun save() {
        if (contacts.isEmpty()) return
        if (end.timeInMillis <= start.timeInMillis) {
            Toast.makeText(this, R.string.err_end_before_start, Toast.LENGTH_LONG).show(); return
        }
        if (end.timeInMillis <= System.currentTimeMillis()) {
            Toast.makeText(this, R.string.err_in_past, Toast.LENGTH_LONG).show(); return
        }
        val contact = contacts[spinner.selectedItemPosition]
        val s = Schedule(
            id = editingId ?: Store.newId(),
            contactId = contact.id,
            startMillis = start.timeInMillis,
            endMillis = end.timeInMillis,
        )
        Store.saveSchedule(this, s)
        Scheduler.rescheduleAll(this)
        Forwarding.applyCurrentState(this) // ha a kezdés már elmúlt, azonnal bekapcsol
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
