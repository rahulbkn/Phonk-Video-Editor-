package dev.phonk.editor.crash

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import dev.phonk.editor.R
import java.io.File

/**
 * Lists every persisted crash report, newest first. Tap a row to open
 * [CrashDetailsActivity]. Purely a viewer — it never modifies logs.
 */
class CrashHistoryActivity : Activity() {

    private val repo by lazy { CrashLogRepository(this) }
    private val formatter by lazy { CrashFormatter(CrashFormatter.Strings.from(this)) }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(dev.phonk.editor.settings.SettingsManager.wrapLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash_history)

        val entries = repo.loadAll()

        val list = findViewById<ListView>(R.id.lv_crash_history)
        val empty = findViewById<TextView>(R.id.tv_history_empty)
        list.emptyView = empty

        val adapter = object : ArrayAdapter<Pair<File, CrashInfo>>(this, R.layout.item_crash_row, entries) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val item = getItem(position) ?: return@getView layoutInflater.inflate(R.layout.item_crash_row, parent, false)
                val file = item.first
                val info = item.second
                val view = convertView
                    ?: layoutInflater.inflate(R.layout.item_crash_row, parent, false)
                val title = view.findViewById<TextView>(R.id.tv_row_title)
                val time = view.findViewById<TextView>(R.id.tv_row_time)
                title.text = getString(R.string.crash_history_row_title, entries.size - position, info.exceptionType)
                val stamp = formatter.time(info.timestamp)
                time.text = info.message?.take(64)?.let { snippet ->
                    getString(R.string.crash_history_row_time, stamp, snippet)
                } ?: stamp
                // keep the file name reachable for the details screen
                view.tag = file
                return view
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, view, _, _ ->
            val file = view.tag as? File ?: return@setOnItemClickListener
            startActivity(
                Intent(this, CrashDetailsActivity::class.java)
                    .putExtra(CrashDetailsActivity.EXTRA_CRASH_FILENAME, file.name),
            )
        }

        findViewById<Button>(R.id.btn_history_back).setOnClickListener { finish() }
        empty.text = getString(R.string.crash_no_logs)
    }
}
