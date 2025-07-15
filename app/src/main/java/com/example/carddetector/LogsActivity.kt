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

            // Add divider
            val divider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 2
                ).apply {
                    setMargins(0, 16, 0, 16)
                }
                setBackgroundColor(Color.LTGRAY)
            }
            holder.container.addView(divider)

            // Add email
            val email = data.optString("email_address", "")
            if (email.isNotEmpty()) {
                val emailView = TextView(context).apply {
                    text = "📧 $email"
                    textSize = 14f
                    setTextColor(Color.BLUE)
                    setPadding(0, 4, 0, 4)
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

            // Add phone numbers
            val phone = data.optString("phone", "")
            val mobilePhone = data.optString("mobile_phone", "")
            
            if (phone.isNotEmpty()) {
                val phoneView = TextView(context).apply {
                    text = "📞 Phone: $phone"
                    textSize = 14f
                    setTextColor(Color.rgb(0, 150, 0))
                    setPadding(0, 4, 0, 4)
                    setOnClickListener {
                        try {
                            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                                this.data = Uri.parse("tel:$phone")
                            }
                            context.startActivity(dialIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open dialer", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                holder.container.addView(phoneView)
            }

            if (mobilePhone.isNotEmpty()) {
                val mobileView = TextView(context).apply {
                    text = "📱 Mobile: $mobilePhone"
                    textSize = 14f
                    setTextColor(Color.rgb(0, 150, 0))
                    setPadding(0, 4, 0, 4)
                    setOnClickListener {
                        try {
                            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                                this.data = Uri.parse("tel:$mobilePhone")
                            }
                            context.startActivity(dialIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open dialer", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                holder.container.addView(mobileView)
            }

            // Add website
            val website = data.optString("website_link", "")
            if (website.isNotEmpty()) {
                val websiteView = TextView(context).apply {
                    text = "🌐 $website"
                    textSize = 14f
                    setTextColor(Color.rgb(30, 144, 255))
                    setPadding(0, 4, 0, 4)
                    setOnClickListener {
                        try {
                            val url = if (!website.startsWith("http://") && !website.startsWith("https://")) {
                                "https://$website"
                            } else {
                                website
                            }
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open website", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                holder.container.addView(websiteView)
            }

            // Add fax
            val fax = data.optString("fax_detail", "")
            if (fax.isNotEmpty()) {
                val faxView = TextView(context).apply {
                    text = "📠 Fax: $fax"
                    textSize = 14f
                    setTextColor(Color.GRAY)
                    setPadding(0, 4, 0, 4)
                }
                holder.container.addView(faxView)
            }

            // Add address information
            val completeAddress = data.optString("complete_address", "")
            val street = data.optString("street", "")
            val state = data.optString("state", "")
            val country = data.optString("country", "")
            val postalCode = data.optString("postal_code", "")

            if (completeAddress.isNotEmpty()) {
                val addressView = TextView(context).apply {
                    text = "📍 $completeAddress"
                    textSize = 14f
                    setTextColor(Color.rgb(255, 140, 0))
                    setPadding(0, 4, 0, 4)
                    setOnClickListener {
                        try {
                            val geoUri = "geo:0,0?q=${Uri.encode(completeAddress)}"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open maps", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                holder.container.addView(addressView)
            } else {
                // Build address from components
                val addressParts = mutableListOf<String>()
                if (street.isNotEmpty()) addressParts.add(street)
                if (state.isNotEmpty()) addressParts.add(state)
                if (country.isNotEmpty()) addressParts.add(country)
                if (postalCode.isNotEmpty()) addressParts.add(postalCode)
                
                if (addressParts.isNotEmpty()) {
                    val constructedAddress = addressParts.joinToString(", ")
                    val addressView = TextView(context).apply {
                        text = "📍 $constructedAddress"
                        textSize = 14f
                        setTextColor(Color.rgb(255, 140, 0))
                        setPadding(0, 4, 0, 4)
                        setOnClickListener {
                            try {
                                val geoUri = "geo:0,0?q=${Uri.encode(constructedAddress)}"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Cannot open maps", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    holder.container.addView(addressView)
                }
            }

            // Add share button
            val shareButton = Button(context).apply {
                text = "Share Complete Info"
                textSize = 12f
                setPadding(16, 8, 16, 8)
                setBackgroundColor(Color.parseColor("#2196F3"))
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 16
                }
                setOnClickListener {
                    shareCompleteCardInfo(data)
                }
            }
            holder.container.addView(shareButton)

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

    private fun shareCompleteCardInfo(data: JSONObject) {
        val shareText = buildString {
            append("=== BUSINESS CARD INFORMATION ===\n\n")
            
            // Company & Personal Info
            val company = data.optString("company_name", "")
            val firstName = data.optString("first_name", "")
            val lastName = data.optString("last_name", "")
            val jobTitle = data.optString("job_title", "")
            
            if (company.isNotEmpty()) append("Company: $company\n")
            if (firstName.isNotEmpty() || lastName.isNotEmpty()) {
                append("Name: ${firstName.trim()} ${lastName.trim()}".trim() + "\n")
            }
            if (jobTitle.isNotEmpty()) append("Job Title: $jobTitle\n")
            
            append("\n=== CONTACT INFORMATION ===\n")
            
            // Contact Details
            val email = data.optString("email_address", "")
            val phone = data.optString("phone", "")
            val mobile = data.optString("mobile_phone", "")
            val website = data.optString("website_link", "")
            val fax = data.optString("fax_detail", "")
            
            if (email.isNotEmpty()) append("Email: $email\n")
            if (phone.isNotEmpty()) append("Phone: $phone\n")
            if (mobile.isNotEmpty()) append("Mobile: $mobile\n")
            if (website.isNotEmpty()) append("Website: $website\n")
            if (fax.isNotEmpty()) append("Fax: $fax\n")
            
            // Address Information
            val completeAddress = data.optString("complete_address", "")
            val street = data.optString("street", "")
            val state = data.optString("state", "")
            val country = data.optString("country", "")
            val postalCode = data.optString("postal_code", "")
            
            if (completeAddress.isNotEmpty() || street.isNotEmpty() || state.isNotEmpty() || 
                country.isNotEmpty() || postalCode.isNotEmpty()) {
                append("\n=== ADDRESS ===\n")
                if (completeAddress.isNotEmpty()) append("Address: $completeAddress\n")
                if (street.isNotEmpty()) append("Street: $street\n")
                if (state.isNotEmpty()) append("State: $state\n")
                if (country.isNotEmpty()) append("Country: $country\n")
                if (postalCode.isNotEmpty()) append("Postal Code: $postalCode\n")
            }
            
            append("\n---\nShared from Nicomatic Cards")
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "Business Card - ${data.optString("company_name", "Contact Information")}")
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share Business Card"))
    }

    override fun getItemCount() = responses.size
}
