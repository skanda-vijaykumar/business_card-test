package com.example.carddetector

import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.carddetector.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import android.Manifest

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var responsesDao: ResponsesDao
    private var isFlashOn = false
    private var isProcessing = false
    private var currentCardData: JSONObject? = null

    // API Configuration
    private val API_KEY = "eKQduRkL3J0Fc2hvtJPbRjirNj26nIclgEIVNd"
    private val API_URL = "https://api.nicomind.com/api/chat"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        responsesDao = ResponsesDao(this)
        setupClickListeners()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun setupClickListeners() {
        binding.captureButton.setOnClickListener {
            if (!isProcessing) {
                takePhoto()
            }
        }

        binding.logsButton.setOnClickListener {
            showLogs()
        }

        binding.flashButton.setOnClickListener {
            toggleFlash()
        }

        binding.shareButton.setOnClickListener {
            shareResult()
        }

        binding.saveButton.setOnClickListener {
            saveResult()
        }

        // Quick actions panel toggle
        binding.captureButton.setOnLongClickListener {
            toggleQuickActions()
            true
        }
    }

    private fun showLoadingState() {
        isProcessing = true
        binding.loadingOverlay.visibility = View.VISIBLE
        binding.captureButton.isEnabled = false

        // Simple loading animation
        val loadingTexts = arrayOf(
            "Processing card.",
            "Processing card..",
            "Processing card..."
        )
        var index = 0
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (isProcessing) {
                    binding.loadingText.text = loadingTexts[index % loadingTexts.size]
                    index++
                    handler.postDelayed(this, 500)
                }
            }
        }
        handler.post(runnable)
    }

    private fun hideLoadingState() {
        isProcessing = false
        binding.loadingOverlay.visibility = View.GONE
        binding.captureButton.isEnabled = true
    }

    private fun showResultsCard(data: JSONObject) {
        // Store current card data for sharing
        currentCardData = data
        
        // Clear previous results
        binding.resultContainer.removeAllViews()

        // Populate with new data
        populateResults(data)

        // Show result card
        binding.resultBottomSheet.visibility = View.VISIBLE

        // Hide after 10 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            binding.resultBottomSheet.visibility = View.GONE
        }, 10000)
    }

    private fun populateResults(data: JSONObject) {
        val container = binding.resultContainer

        val fields = mapOf(
            "company_name" to "Company",
            "first_name" to "First Name",
            "last_name" to "Last Name",
            "job_title" to "Job Title",
            "email_address" to "Email",
            "phone" to "Phone",
            "mobile_phone" to "Mobile",
            "website_link" to "Website",
            "complete_address" to "Address",
            "street" to "Street",
            "state" to "State",
            "country" to "Country",
            "postal_code" to "Postal Code",
            "fax_detail" to "Fax"
        )

        fields.forEach { (key, label) ->
            val value = data.optString(key, "")
            if (value.isNotEmpty()) {
                val resultItem = createResultItem(label, value)
                container.addView(resultItem)
            }
        }
    }

    private fun createResultItem(label: String, value: String): View {
        val linearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 12, 16, 12)
        }

        val labelView = TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val valueView = TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
        }

        linearLayout.addView(labelView)
        linearLayout.addView(valueView)

        // Add click listener for copying
        linearLayout.setOnClickListener {
            copyToClipboard(label, value)
            Toast.makeText(this, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        return linearLayout
    }

    private fun copyToClipboard(label: String, value: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(label, value)
        clipboard.setPrimaryClip(clip)
    }

    private fun toggleFlash() {
        camera?.let { camera ->
            val flashMode = if (isFlashOn) {
                ImageCapture.FLASH_MODE_OFF
            } else {
                ImageCapture.FLASH_MODE_ON
            }

            imageCapture?.flashMode = flashMode
            isFlashOn = !isFlashOn

            // Update flash button icon
            val iconRes = if (isFlashOn) {
                R.drawable.ic_flash_on_24
            } else {
                R.drawable.ic_flash_off_24
            }
            binding.flashButton.setIconResource(iconRes)
        }
    }

    private fun toggleQuickActions() {
        val isVisible = binding.quickActionsPanel.visibility == View.VISIBLE
        if (isVisible) {
            binding.quickActionsPanel.visibility = View.GONE
        } else {
            binding.quickActionsPanel.visibility = View.VISIBLE
            binding.quickActionsPanel.alpha = 1f
        }
    }

    private fun shareResult() {
        currentCardData?.let { data ->
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
                
                append("\n---\nExtracted by Nicomatic Cards")
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
                putExtra(Intent.EXTRA_SUBJECT, "Business Card - ${data.optString("company_name", "Contact Information")}")
            }

            startActivity(Intent.createChooser(shareIntent, "Share Business Card"))
        } ?: run {
            Toast.makeText(this, "No card data to share", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveResult() {
        Toast.makeText(this, "Results saved successfully", Toast.LENGTH_SHORT).show()
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        showLoadingState()

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)

                    val compressedBytes = compressImageOptimized(bytes)
                    val base64Image = Base64.encodeToString(compressedBytes, Base64.DEFAULT)

                    // Check payload size (1MB = 1,048,576 bytes)
                    val payloadSize = base64Image.toByteArray().size
                    Log.d(TAG, "Payload size: ${payloadSize / 1024}KB")
                    
                    if (payloadSize > 1_048_576) {
                        // If still too large, compress more aggressively
                        val recompressedBytes = compressImageAggressively(bytes)
                        val recompressedBase64 = Base64.encodeToString(recompressedBytes, Base64.DEFAULT)
                        saveImageToGallery(bytes)
                        sendImageToAPI(recompressedBase64)
                    } else {
                        saveImageToGallery(bytes)
                        sendImageToAPI(base64Image)
                    }
                    
                    image.close()
                }

                override fun onError(exc: ImageCaptureException) {
                    hideLoadingState()
                    handleError("Failed to capture image: ${exc.message}")
                }
            }
        )
    }

    private fun compressImageOptimized(imageBytes: ByteArray): ByteArray {
        return try {
            val originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            
            // Higher max dimension for better quality
            val maxDimension = 1024
            val scale = minOf(
                maxDimension.toFloat() / originalBitmap.width,
                maxDimension.toFloat() / originalBitmap.height,
                1.0f
            )

            val newWidth = (originalBitmap.width * scale).toInt()
            val newHeight = (originalBitmap.height * scale).toInt()

            val resizedBitmap = Bitmap.createScaledBitmap(
                originalBitmap, newWidth, newHeight, true
            )

            val outputStream = ByteArrayOutputStream()
            // Higher quality compression
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)

            val finalBytes = outputStream.toByteArray()
            
            // Check if base64 size would exceed 1MB
            val base64Size = (finalBytes.size * 4 / 3) + 4 // Approximate base64 size
            if (base64Size > 900_000) { // Leave some buffer
                // Reduce quality if too large
                outputStream.reset()
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                val reducedBytes = outputStream.toByteArray()
                
                if (resizedBitmap != originalBitmap) {
                    resizedBitmap.recycle()
                }
                originalBitmap.recycle()
                
                reducedBytes
            } else {
                if (resizedBitmap != originalBitmap) {
                    resizedBitmap.recycle()
                }
                originalBitmap.recycle()
                
                finalBytes
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image: ${e.message}")
            imageBytes
        }
    }

    private fun compressImageAggressively(imageBytes: ByteArray): ByteArray {
        return try {
            val originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            
            // More aggressive compression
            val maxDimension = 800
            val scale = minOf(
                maxDimension.toFloat() / originalBitmap.width,
                maxDimension.toFloat() / originalBitmap.height,
                1.0f
            )

            val newWidth = (originalBitmap.width * scale).toInt()
            val newHeight = (originalBitmap.height * scale).toInt()

            val resizedBitmap = Bitmap.createScaledBitmap(
                originalBitmap, newWidth, newHeight, true
            )

            val outputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)

            val finalBytes = outputStream.toByteArray()

            if (resizedBitmap != originalBitmap) {
                resizedBitmap.recycle()
            }
            originalBitmap.recycle()

            finalBytes
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image aggressively: ${e.message}")
            imageBytes
        }
    }

    private fun saveImageToGallery(imageBytes: ByteArray) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let { imageUri ->
            try {
                contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                    outputStream.write(imageBytes)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun sendImageToAPI(base64Image: String) {
        val client = OkHttpClient.Builder()
            .connectTimeout(150, TimeUnit.SECONDS)
            .readTimeout(150, TimeUnit.SECONDS)
            .writeTimeout(150, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val prompt = """
                    You are an expert OCR AI specializing in extracting structured data from business cards.
                    Extract the following information in JSON format:
                    {
                        "company_name": "string",
                        "first_name": "string",
                        "last_name": "string",
                        "job_title": "string",
                        "email_address": "string",
                        "complete_address": "string",
                        "street": "string",
                        "state": "string",
                        "country": "string",
                        "postal_code": "string",
                        "fax_detail": "string",
                        "mobile_phone": "string",
                        "phone": "string",
                        "website_link": "string"
                    }
                    If any field is not available, use an empty string.
                """.trimIndent()

                val messages = JSONArray()
                val userMessage = JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                    put("images", JSONArray().put(base64Image))
                }
                messages.put(userMessage)

                val requestBody = JSONObject().apply {
                    put("model", "llama3.2-vision")
                    put("messages", messages)
                    put("stream", false)
                }

                val request = Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer $API_KEY")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("API error: ${response.code} - ${response.message}")
                    }

                    val result = response.body?.string()
                    withContext(Dispatchers.Main) {
                        parseAndDisplayResult(result, base64Image)
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideLoadingState()
                    handleError("API error: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun parseAndDisplayResult(result: String?, base64Image: String) {
        hideLoadingState()

        if (result.isNullOrEmpty()) {
            handleError("No response from API")
            return
        }

        try {
            val jsonResponse = JSONObject(result)
            val message = jsonResponse.optJSONObject("message")
            val content = message?.optString("content") ?: ""

            if (content.isEmpty()) {
                handleError("Empty AI response")
                return
            }

            // Extract JSON from response
            val jsonStart = content.indexOf("{")
            val jsonEnd = content.lastIndexOf("}") + 1

            if (jsonStart != -1 && jsonEnd > jsonStart) {
                val jsonString = content.substring(jsonStart, jsonEnd)
                val businessCardData = JSONObject(jsonString)

                // Save to database
                responsesDao.insertResponse(ServerResponse(
                    response = businessCardData.toString(),
                    imageBase64 = base64Image
                ))

                // Show results
                showResultsCard(businessCardData)

            } else {
                handleError("Could not parse business card data")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing API response: ${e.message}")
            handleError("Parse Error: ${e.message}")
        }
    }

    private fun handleError(message: String) {
        hideLoadingState()
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showLogs() {
        val intent = Intent(this, LogsActivity::class.java)
        startActivity(intent)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setJpegQuality(95) // Higher quality for better OCR
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        private const val TAG = "CardDetector"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        } else {
            arrayOf(Manifest.permission.CAMERA)
        }
    }
}
