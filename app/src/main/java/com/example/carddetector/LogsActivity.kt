package com.example.carddetector

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class LogsActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var responsesDao: ResponsesDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(createSimpleLayout())
            responsesDao = ResponsesDao(this)
            setupRecyclerView()
            loadData()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error loading logs: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun createSimpleLayout(): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.WHITE)
        }

        // Add title
        val title = TextView(this).apply {
            text = "Scan History"
            textSize = 24f
            setPadding(0, 0, 0, 32)
            setTextColor(Color.BLACK)
            setTypeface(null, Typeface.BOLD)
        }
        layout.addView(title)

        // Add back button
        val backButton = Button(this).apply {
            text = "← Back"
            setOnClickListener { finish() }
        }
        layout.addView(backButton)

        // Add RecyclerView
        recyclerView = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                topMargin = 32
            }
        }
        layout.addView(recyclerView)

        return layout
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun loadData() {
        try {
            val responses = responsesDao.getAllResponses()

            if (responses.isEmpty()) {
                showEmptyMessage()
            } else {
                showData(responses)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEmptyMessage() {
        val emptyView = TextView(this).apply {
            text = "No scanned cards yet.\n\nGo back and scan some business cards to see them here!"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(32, 64, 32, 64)
            setTextColor(Color.GRAY)
        }

        recyclerView.visibility = View.GONE
        (recyclerView.parent as LinearLayout).addView(emptyView)
    }

    private fun showData(responses: List<ServerResponse>) {
        try {
            val adapter = SimpleLogsAdapter(responses, this)
            recyclerView.adapter = adapter

            Toast.makeText(this, "Found ${responses.size} scanned cards", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error displaying data: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

class SimpleLogsAdapter(
    private val responses: List<ServerResponse>,
    private val context: LogsActivity
) : RecyclerView.Adapter<SimpleLogsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: LinearLayout = view as LinearLayout
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            setBackgroundColor(Color.parseColor("#F5F5F5"))

            val margin = 16
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(margin, margin/2, margin, margin/2)
            }
        }

        return ViewHolder(cardLayout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val response = responses[position]
        holder.container.removeAllViews()

        try {
            // Parse the card data
            val data = try {
                JSONObject(response.response)
            } catch (e: Exception) {
                JSONObject().apply { put("error", "Could not parse data") }
            }

            // Add company name
            val companyName = data.optString("company_name", "Unknown Company")
            val companyView = TextView(context).apply {
                text = companyName
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.BLACK)
            }
            holder.container.addView(companyView)

            // Add person name
            val firstName = data.optString("first_name", "")
            val lastName = data.optString("last_name", "")
            val fullName = "$firstName $lastName".trim()
            if (fullName.isNotEmpty()) {
                val nameView = TextView(context).apply {
                    text = fullName
                    textSize = 16f
                    setTextColor(Color.DKGRAY)
                }
                holder.container.addView(nameView)
            }

            // Add job title
            val jobTitle = data.optString("job_title", "")
            if (jobTitle.isNotEmpty()) {
                val titleView = TextView(context).apply {
                    text = jobTitle
                    textSize = 14f
                    setTextColor(Color.GRAY)
                }
                holder.container.addView(titleView)
            }

            // Add email
            val email = data.optString("email_address", "")
            if (email.isNotEmpty()) {
                val emailView = TextView(context).apply {
                    text = "📧 $email"
                    textSize = 14f
                    setTextColor(Color.BLUE)
                    setPadding(0, 8, 0, 0)
                    setOnClickListener {
                        try {
                            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                this.data = Uri.parse("mailto:$email")
                            }


                            context.startActivity(emailIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open email app", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                holder.container.addView(emailView)
            }

            // Add phone
            val phone = data.optString("phone", "")
            val mobilePhone = data.optString("mobile_phone", "")
            val displayPhone = if (phone.isNotEmpty()) phone else mobilePhone
            if (displayPhone.isNotEmpty()) {
                val phoneView = TextView(context).apply {
                    text = "📞 $displayPhone"
                    textSize = 14f
                    setTextColor(Color.rgb(0, 150, 0))
                    setPadding(0, 4, 0, 0)
                    setOnClickListener {
                        try {
                            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                                this.data = Uri.parse("tel:$displayPhone")
                            }

                            context.startActivity(dialIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open dialer", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                holder.container.addView(phoneView)
            }

            // Add timestamp
            val timeText = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
                .format(Date(response.timestamp))
            val timestampView = TextView(context).apply {
                text = "⏰ $timeText"
                textSize = 12f
                setTextColor(Color.GRAY)
                setPadding(0, 12, 0, 0)
            }
            holder.container.addView(timestampView)

        } catch (e: Exception) {
            e.printStackTrace()
            val errorView = TextView(context).apply {
                text = "Error displaying card data"
                textSize = 14f
                setTextColor(Color.RED)
            }
            holder.container.addView(errorView)
        }
    }

    override fun getItemCount() = responses.size
}