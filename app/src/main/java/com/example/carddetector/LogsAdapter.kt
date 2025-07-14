package com.example.carddetector

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class LogsAdapter(
    private val responses: List<ServerResponse>,
    private val context: Context
) : RecyclerView.Adapter<LogsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardContainer: MaterialCardView = view.findViewById(R.id.cardContainer)
        val cardImageView: ImageView = view.findViewById(R.id.cardImageView)
        val imageLoadingProgress: View = view.findViewById(R.id.imageLoadingProgress)
        val companyNameText: TextView = view.findViewById(R.id.companyNameText)
        val personNameText: TextView = view.findViewById(R.id.personNameText)
        val jobTitleText: TextView = view.findViewById(R.id.jobTitleText)
        val timestampText: TextView = view.findViewById(R.id.timestampText)
        val qualityText: TextView = view.findViewById(R.id.qualityText)
        val shareButton: MaterialButton = view.findViewById(R.id.shareButton)
        val moreButton: MaterialButton = view.findViewById(R.id.moreButton)
        val expandButton: MaterialButton = view.findViewById(R.id.expandButton)
        val expandableSection: View = view.findViewById(R.id.expandableSection)
        val phoneLayout: LinearLayout = view.findViewById(R.id.phoneLayout)
        val emailLayout: LinearLayout = view.findViewById(R.id.emailLayout)
        val websiteLayout: LinearLayout = view.findViewById(R.id.websiteLayout)
        val phoneText: TextView = view.findViewById(R.id.phoneText)
        val emailText: TextView = view.findViewById(R.id.emailText)
        val websiteText: TextView = view.findViewById(R.id.websiteText)

        var isExpanded = false
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.log_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val response = responses[position]

        // Load and display image
        loadImage(holder, response.imageBase64)

        // Parse and display card data
        val cardData = parseCardData(response.response)
        populateCardInfo(holder, cardData)

        // Set timestamp
        setTimestamp(holder, response.timestamp)

        // Set quality indicator
        setQualityIndicator(holder, cardData)

        // Setup click listeners
        setupClickListeners(holder, response, cardData)

        // Setup expand/collapse functionality
        setupExpandableSection(holder)
    }

    private fun loadImage(holder: ViewHolder, base64Image: String) {
        try {
            holder.imageLoadingProgress.visibility = View.VISIBLE

            val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            holder.cardImageView.setImageBitmap(bitmap)
            holder.imageLoadingProgress.visibility = View.GONE
        } catch (e: Exception) {
            e.printStackTrace()
            holder.imageLoadingProgress.visibility = View.GONE
        }
    }

    private fun parseCardData(jsonString: String): JSONObject {
        return try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            // Try to extract JSON from the string if it's wrapped in other text
            val jsonStart = jsonString.indexOf("{")
            val jsonEnd = jsonString.lastIndexOf("}") + 1
            if (jsonStart != -1 && jsonEnd > jsonStart) {
                JSONObject(jsonString.substring(jsonStart, jsonEnd))
            } else {
                JSONObject()
            }
        }
    }

    private fun populateCardInfo(holder: ViewHolder, data: JSONObject) {
        // Company name
        val companyName = data.optString("company_name", "Unknown Company")
        holder.companyNameText.text = if (companyName.isEmpty()) "Unknown Company" else companyName

        // Person name
        val firstName = data.optString("first_name", "")
        val lastName = data.optString("last_name", "")
        val fullName = "$firstName $lastName".trim()
        holder.personNameText.text = if (fullName.isEmpty()) "Unknown Person" else fullName

        // Job title
        val jobTitle = data.optString("job_title", "")
        holder.jobTitleText.text = if (jobTitle.isEmpty()) "No title" else jobTitle

        // Contact details for expandable section
        val phone = data.optString("phone", "")
        val mobilePhone = data.optString("mobile_phone", "")
        val displayPhone = if (phone.isNotEmpty()) phone else mobilePhone
        holder.phoneText.text = if (displayPhone.isEmpty()) "No phone" else displayPhone

        val email = data.optString("email_address", "")
        holder.emailText.text = if (email.isEmpty()) "No email" else email

        val website = data.optString("website_link", "")
        holder.websiteText.text = if (website.isEmpty()) "No website" else website

        // Show/hide contact sections based on availability
        holder.phoneLayout.visibility = if (displayPhone.isEmpty()) View.GONE else View.VISIBLE
        holder.emailLayout.visibility = if (email.isEmpty()) View.GONE else View.VISIBLE
        holder.websiteLayout.visibility = if (website.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun setTimestamp(holder: ViewHolder, timestamp: Long) {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        val timeText = when {
            diff < 60 * 1000 -> "Just now"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} min ago"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} hours ago"
            diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)} days ago"
            else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
        }

        holder.timestampText.text = timeText
    }

    private fun setQualityIndicator(holder: ViewHolder, data: JSONObject) {
        // Simple quality assessment based on available data
        val fields = listOf("company_name", "first_name", "last_name", "email_address", "phone")
        val filledFields = fields.count { data.optString(it, "").isNotEmpty() }

        val qualityText = when {
            filledFields >= 4 -> "High"
            filledFields >= 2 -> "Medium"
            else -> "Low"
        }

        holder.qualityText.text = qualityText
    }

    private fun setupClickListeners(holder: ViewHolder, response: ServerResponse, data: JSONObject) {
        // Share button
        holder.shareButton.setOnClickListener {
            shareCard(data)
        }

        // More button
        holder.moreButton.setOnClickListener {
            Toast.makeText(context, "More options coming soon", Toast.LENGTH_SHORT).show()
        }

        // Contact action clicks
        holder.phoneLayout.setOnClickListener {
            val phone = holder.phoneText.text.toString()
            if (phone != "No phone") {
                dialPhone(phone)
            }
        }

        holder.emailLayout.setOnClickListener {
            val email = holder.emailText.text.toString()
            if (email != "No email") {
                sendEmail(email)
            }
        }

        holder.websiteLayout.setOnClickListener {
            val website = holder.websiteText.text.toString()
            if (website != "No website") {
                openWebsite(website)
            }
        }

        // Card click to expand/collapse
        holder.cardContainer.setOnClickListener {
            toggleExpansion(holder)
        }
    }

    private fun setupExpandableSection(holder: ViewHolder) {
        // Initially collapsed
        holder.expandableSection.visibility = View.GONE
        holder.expandableSection.alpha = 0f
        holder.expandButton.text = "Show Details"

        holder.expandButton.setOnClickListener {
            toggleExpansion(holder)
        }
    }

    private fun toggleExpansion(holder: ViewHolder) {
        val isExpanding = !holder.isExpanded
        holder.isExpanded = isExpanding

        if (isExpanding) {
            // Expand
            holder.expandableSection.visibility = View.VISIBLE
            holder.expandableSection.alpha = 1f
            holder.expandButton.text = "Hide Details"
        } else {
            // Collapse
            holder.expandableSection.visibility = View.GONE
            holder.expandableSection.alpha = 0f
            holder.expandButton.text = "Show Details"
        }
    }

    private fun shareCard(data: JSONObject) {
        val shareText = buildString {
            val name = "${data.optString("first_name", "")} ${data.optString("last_name", "")}".trim()
            val company = data.optString("company_name", "")
            val title = data.optString("job_title", "")
            val email = data.optString("email_address", "")
            val phone = data.optString("phone", "")

            if (name.isNotEmpty()) append("Name: $name\n")
            if (company.isNotEmpty()) append("Company: $company\n")
            if (title.isNotEmpty()) append("Title: $title\n")
            if (email.isNotEmpty()) append("Email: $email\n")
            if (phone.isNotEmpty()) append("Phone: $phone\n")
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "Business Card Information")
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share Business Card"))
    }

    private fun dialPhone(phone: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phone")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            copyToClipboard("Phone", phone)
            Toast.makeText(context, "Phone number copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendEmail(email: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$email")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            copyToClipboard("Email", email)
            Toast.makeText(context, "Email copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWebsite(website: String) {
        try {
            val url = if (!website.startsWith("http://") && !website.startsWith("https://")) {
                "https://$website"
            } else {
                website
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            copyToClipboard("Website", website)
            Toast.makeText(context, "Website URL copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    override fun getItemCount() = responses.size
}