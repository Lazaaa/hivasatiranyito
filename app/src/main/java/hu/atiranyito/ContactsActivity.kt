package hu.atiranyito

import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

/** Becenevek kezelése: becenév → telefonszám. */
class ContactsActivity : AppCompatActivity() {

    private val adapter = ContactAdapter()
    private lateinit var empty: TextView

    // A dialógus mezői, amiket a névjegyválasztó kitölt
    private var dlgNick: EditText? = null
    private var dlgNumber: EditText? = null

    /** Rendszer névjegyválasztó – nem kell hozzá READ_CONTACTS engedély. */
    private val pickPhone =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val uri: Uri = res.data?.data ?: return@registerForActivityResult
            contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    dlgNumber?.setText(c.getString(0)?.replace(" ", "")?.replace("-", ""))
                    if (dlgNick?.text.isNullOrBlank()) dlgNick?.setText(c.getString(1))
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        empty = findViewById(R.id.empty)
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@ContactsActivity)
            adapter = this@ContactsActivity.adapter
        }
        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { editDialog(null) }
        refresh()
    }

    private fun refresh() {
        adapter.items = Store.contacts(this)
        adapter.notifyDataSetChanged()
        empty.visibility = if (adapter.items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun editDialog(existing: Contact?) {
        val v = layoutInflater.inflate(R.layout.dialog_contact, null)
        val nick = v.findViewById<EditText>(R.id.edit_nick)
        val num = v.findViewById<EditText>(R.id.edit_number)
        dlgNick = nick; dlgNumber = num
        existing?.let { nick.setText(it.nickname); num.setText(it.number) }
        v.findViewById<Button>(R.id.btn_pick).setOnClickListener {
            pickPhone.launch(
                android.content.Intent(android.content.Intent.ACTION_PICK)
                    .setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE)
            )
        }

        val dlg = AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.new_nickname else R.string.edit_nickname)
            .setView(v)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val n = nick.text.toString().trim()
                val p = num.text.toString().filter { it.isDigit() || it == '+' }
                if (n.isEmpty() || p.length < 3) {
                    Toast.makeText(this, R.string.err_contact, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                Store.saveContact(this, Contact(existing?.id ?: Store.newId(), n, p))
                Forwarding.applyCurrentState(this) // ha épp aktív és a szám változott
                refresh()
                dlg.dismiss()
            }
        }
        dlg.setOnDismissListener { dlgNick = null; dlgNumber = null }
        dlg.show()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    inner class ContactAdapter : RecyclerView.Adapter<ContactAdapter.VH>() {
        var items: List<Contact> = emptyList()

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.title)
            val subtitle: TextView = v.findViewById(R.id.subtitle)
        }

        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_contact, p, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val c = items[pos]
            h.title.text = c.nickname
            h.subtitle.text = c.number
            h.itemView.setOnClickListener { editDialog(c) }
            h.itemView.setOnLongClickListener {
                AlertDialog.Builder(this@ContactsActivity)
                    .setMessage(getString(R.string.confirm_delete_contact, c.nickname))
                    .setPositiveButton(R.string.delete) { _, _ ->
                        Store.schedules(this@ContactsActivity)
                            .filter { it.contactId == c.id }
                            .forEach { Scheduler.cancel(this@ContactsActivity, it.id) }
                        Store.deleteContact(this@ContactsActivity, c.id)
                        Forwarding.applyCurrentState(this@ContactsActivity)
                        refresh()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                true
            }
        }
    }
}
