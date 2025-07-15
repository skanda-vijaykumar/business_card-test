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

        // Add new views for complete address information
        val contactInfoGrid: LinearLayout = view.findViewById(R.id.contactInfoGrid)

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
        setupExpandableSection(holder, cardData)
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

        // Contact details for expandable section - moved to setupExpandableSection
    }

    private fun setupExpandableSection(holder: ViewHolder, data: JSONObject) {
        // Initially collapsed
        holder.expandableSection.visibility = View.GONE
        holder.expandableSection.alpha = 0f
        holder.expandButton.text = "Show Details"

        // Clear any existing content
        holder.contactInfoGrid.removeAllViews()

        // Create detailed contact information
        createDetailedContactInfo(holder, data)

        holder.expandButton.setOnClickListener {
            toggleExpansion(holder)
        }
    }

    private fun createDetailedContactInfo(holder: ViewHolder, data: JSONObject) {
        // Phone numbers
        val phone = data.optString("phone", "")
        val mobilePhone = data.optString("mobile_phone", "")
        
        if (phone.isNotEmpty()) {
            val phoneItem = createContactItem(
                context, "Phone", phone, R.drawable.ic_phone_24
            ) { dialPhone(phone) }
            holder.contactInfoGrid.addView(phoneItem)
        }
        
        if (mobilePhone.isNotEmpty()) {
            val mobileItem = createContactItem(
                context, "Mobile", mobilePhone, R.drawable.ic_phone_24
            ) { dialPhone(mobilePhone) }
            holder.contactInfoGrid.addView(mobileItem)
        }

        // Email
        val email = data.optString("email_address", "")
        if (email.isNotEmpty()) {
            val emailItem = createContactItem(
                context, "Email", email, R.drawable.ic_email_24
            ) { sendEmail(email) }
            holder.contactInfoGrid.addView(emailItem)
        }

        // Website
        val website = data.optString("website_link", "")
        if (website.isNotEmpty()) {
            val websiteItem = createContactItem(
                context, "Website", website, R.drawable.ic_language_24
            ) { openWebsite(website) }
            holder.contactInfoGrid.addView(websiteItem)
        }

        // Fax
        val fax = data.optString("fax_detail", "")
        if (fax.isNotEmpty()) {
            val faxItem = createContactItem(
                context, "Fax", fax, R.drawable.ic_phone_24
            ) { copyToClipboard("Fax", fax) }
            holder.contactInfoGrid.addView(faxItem)
        }

        // Address information
        val completeAddress = data.optString("complete_address", "")
        val street = data.optString("street", "")
        val state = data.optString("state", "")
        val country = data.optString("country", "")
        val postalCode = data.optString("postal_code", "")

        if (completeAddress.isNotEmpty()) {
            val addressItem = createContactItem(
                context, "Address", completeAddress, R.drawable.ic_location_on_24
            ) { openMaps(completeAddress) }
            holder.contactInfoGrid.addView(addressItem)
        } else {
            // If no complete address, build from components
            val addressParts = mutableListOf<String>()
            if (street.isNotEmpty()) addressParts.add(street)
            if (state.isNotEmpty()) addressParts.add(state)
            if (country.isNotEmpty()) addressParts.add(country)
            if (postalCode.isNotEmpty()) addressParts.add(postalCode)
            
            if (addressParts.isNotEmpty()) {
                val constructedAddress = addressParts.joinToString(", ")
                val addressItem = createContactItem(
                    context, "Address", constructedAddress, R.drawable.ic_location_on_24
                ) { openMaps(constructedAddress) }
                holder.contactInfoGrid.addView(addressItem)
            }
        }

        // Individual address components (if they exist and are different from complete address)
        if (street.isNotEmpty() && completeAddress.isEmpty()) {
            val streetItem = createContactItem(
                context, "Street", street, R.drawable.ic_location_on_24
            ) { copyToClipboard("Street", street) }
            holder.contactInfoGrid.addView(streetItem)
        }

        if (state.isNotEmpty() && completeAddress.isEmpty()) {
            val stateItem = createContactItem(
                context, "State", state, R.drawable.ic_location_on_24
            ) { copyToClipboard("State", state) }
            holder.contactInfoGrid.addView(stateItem)
        }

        if (country.isNotEmpty() && completeAddress.isEmpty()) {
            val countryItem = createContactItem(
                context, "Country", country, R.drawable.ic_location_on_24
            ) { copyToClipboard("Country", country) }
            holder.contactInfoGrid.addView(countryItem)
        }

        if (postalCode.isNotEmpty() && completeAddress.isEmpty()) {
            val postalItem = createContactItem(
                context, "Postal Code", postalCode, R.drawable.ic_location_on_24
            ) { copyToClipboard("Postal Code", postalCode) }
            holder.contactInfoGrid.addView(postalItem)
        }
    }

    private fun createContactItem(
        context: Context,
        label: String,
        value: String,
        iconRes: Int,
        onClickAction: () -> Unit
    ): LinearLayout {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 12, 16, 12)
            setBackgroundResource(android.R.drawable.list_selector_background)
            isClickable = true
            isFocusable = true
        }

        val icon = ImageView(context).apply {
            setImageResource(iconRes)
            layoutParams = LinearLayout.LayoutParams(60, 60).apply {
                marginEnd = 32
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val labelView = TextView(context).apply {
            text = label
            textSize = 12f
            setTextColor(context.getColor(android.R.color.darker_gray))
        }

        val valueView = TextView(context).apply {
            text = value
            textSize = 14f
            setTextColor(context.getColor(android.R.color.black))
        }

        val actionIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_open_in_new_24)
            layoutParams = LinearLayout.LayoutParams(48, 48)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            alpha = 0.5f
        }

        textContainer.addView(labelView)
        textContainer.addView(valueView)
        
        layout.addView(icon)
        layout.addView(textContainer)
        layout.addView(actionIcon)

        layout.setOnClickListener { onClickAction() }

        return layout
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
        // Enhanced quality assessment based on available data
        val allFields = listOf(
            "company_name", "first_name", "last_name", "job_title", "email_address", 
            "phone", "mobile_phone", "website_link", "complete_address", "street", 
            "state", "country", "postal_code", "fax_detail"
        )
        val filledFields = allFields.count { data.optString(it, "").isNotEmpty() }

        val qualityText = when {
            filledFields >= 8 -> "Excellent"
            filledFields >= 6 -> "High"
            filledFields >= 4 -> "Good"
            filledFields >= 2 -> "Medium"
            else -> "Low"
        }

        holder.qualityText.text = qualityText
    }

    private fun setupClickListeners(holder: ViewHolder, response: ServerResponse, data: JSONObject) {
        // Share button - enhanced sharing
        holder.shareButton.setOnClickListener {
            shareCard(data)
        }

        // More button
        holder.moreButton.setOnClickListener {
            showMoreOptions(data)
        }

        // Card click to expand/collapse
        holder.cardContainer.setOnClickListener {
            toggleExpansion(holder)
        }
    }

    private fun showMoreOptions(data: JSONObject) {
        // You can implement additional options here
        Toast.makeText(context, "Additional options coming soon", Toast.LENGTH_SHORT).show()
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

    private fun openMaps(address: String) {
        try {
            val geoUri = "geo:0,0?q=${Uri.encode(address)}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri))
            context.startActivity(intent)
        } catch (e: Exception) {
            copyToClipboard("Address", address)
            Toast.makeText(context, "Address copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    override fun getItemCount() = responses.size
}
