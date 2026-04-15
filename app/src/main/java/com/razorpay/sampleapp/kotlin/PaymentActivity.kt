package com.razorpay.sampleapp.kotlin

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*
import com.bumptech.glide.Glide
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * DuitNow Pay A2A Test App — Custom Checkout (S2S API)
 *
 * Flow:
 * 1. Get session_token from devstack /v1/checkout/public
 * 2. Create payment via /v1/payments/create/ajax with session token
 * 3. Handle CIMB redirect URL → transform to app deep link
 * 4. Handle callback from CIMB app via rzpcurlectestapp:// URL scheme
 */
class PaymentActivity : Activity() {

    companion object {
        private const val TAG = "DuitNowPay"

        // Devstack configuration
        const val RAZORPAY_KEY = "rzp_test_1NHe5VeS1wt4NO"
        const val RAZORPAY_SECRET = "MpFmQdSmSGmopJDP1iFsRXbr"
        const val DEVSTACK_BASE_URL = "https://api-web-duitnowpay.ext.dev.razorpay.in"
        const val FORCE_TERMINAL_ID = "term_Q8aiqHEwFoCyHK"
        const val BANK_CODE = "CIMY"

        // Blade colors
        const val COLOR_SURFACE_BG = "#F7F8F9"
        const val COLOR_TEXT_PRIMARY = "#1B2533"
        const val COLOR_TEXT_SECONDARY = "#5F6D7E"
        const val COLOR_TEXT_SUBDUED = "#8B95A2"
        const val COLOR_FEEDBACK_INFO_BG = "#DDF4FF"
        const val COLOR_FEEDBACK_INFO_TEXT = "#0550AE"
        const val COLOR_FEEDBACK_SUCCESS_BG = "#DAFBE8"
        const val COLOR_FEEDBACK_SUCCESS_TEXT = "#1A7F37"
        const val COLOR_FEEDBACK_ERROR_BG = "#FFEBE9"
        const val COLOR_FEEDBACK_ERROR_TEXT = "#D1242F"
    }

    // UI references
    private lateinit var etAmount: EditText
    private lateinit var btnCimb: LinearLayout
    private lateinit var progressLoading: ProgressBar
    private lateinit var cardStatus: View
    private lateinit var statusDot: View
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvStatusMessage: TextView
    private lateinit var tvStatusLog: TextView
    private lateinit var cardResult: View
    private lateinit var resultContainer: LinearLayout
    private lateinit var btnNewPayment: Button

    // State
    private var lastResponseHeaders: Map<String, String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.razorpay.sampleapp.R.layout.activity_payment)

        initViews()
        loadImages()
        buildEnvInfo()

        btnNewPayment = findViewById(com.razorpay.sampleapp.R.id.btn_new_payment)

        btnCimb.setOnClickListener { startPayment() }
        btnNewPayment.setOnClickListener { resetPayment() }

        // Handle deep link callback if launched via URL
        handleDeepLinkCallback(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleDeepLinkCallback(it) }
    }

    private fun initViews() {
        etAmount = findViewById(com.razorpay.sampleapp.R.id.et_amount)
        btnCimb = findViewById(com.razorpay.sampleapp.R.id.btn_cimb)
        progressLoading = findViewById(com.razorpay.sampleapp.R.id.progress_loading)
        cardStatus = findViewById(com.razorpay.sampleapp.R.id.card_status)
        statusDot = findViewById(com.razorpay.sampleapp.R.id.status_dot)
        tvStatusTitle = findViewById(com.razorpay.sampleapp.R.id.tv_status_title)
        tvStatusMessage = findViewById(com.razorpay.sampleapp.R.id.tv_status_message)
        tvStatusLog = findViewById(com.razorpay.sampleapp.R.id.tv_status_log)
        cardResult = findViewById(com.razorpay.sampleapp.R.id.card_result)
        resultContainer = findViewById(com.razorpay.sampleapp.R.id.result_container)
    }

    private fun loadImages() {
        val ivRzpLogo: ImageView = findViewById(com.razorpay.sampleapp.R.id.iv_rzp_logo)
        val ivCimbLogo: ImageView = findViewById(com.razorpay.sampleapp.R.id.iv_cimb_logo)

        Glide.with(this)
            .load("https://rzp-1415-prod-dashboard-activation.s3.ap-south-1.amazonaws.com/org_KjWRtYXwpK6VfK/payment_apps_logo/phplelIPA")
            .into(ivRzpLogo)

        Glide.with(this)
            .load("https://cdn.razorpay.com/bank/CIBB.gif")
            .into(ivCimbLogo)
    }

    private fun buildEnvInfo() {
        val container: LinearLayout = findViewById(com.razorpay.sampleapp.R.id.env_info_container)
        container.removeAllViews()

        val titleTv = TextView(this).apply {
            text = "Environment"
            setTextColor(Color.parseColor(COLOR_TEXT_SUBDUED))
            textSize = 11f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        container.addView(titleTv)

        val rows = listOf(
            "Server" to "Devstack (UAT)",
            "Key" to RAZORPAY_KEY,
            "Terminal" to FORCE_TERMINAL_ID,
            "Bank Code" to BANK_CODE,
        )

        for ((key, value) in rows) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 4 }
            }

            val keyTv = TextView(this).apply {
                text = key
                setTextColor(Color.parseColor(COLOR_TEXT_SUBDUED))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val valueTv = TextView(this).apply {
                text = value
                setTextColor(Color.parseColor(COLOR_TEXT_SECONDARY))
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            row.addView(keyTv)
            row.addView(valueTv)
            container.addView(row)
        }
    }

    // MARK: - Reset

    private fun resetPayment() {
        cardStatus.visibility = View.GONE
        cardResult.visibility = View.GONE
        btnNewPayment.visibility = View.GONE
        resultContainer.removeAllViews()
        lastResponseHeaders = null
        setLoading(false)

        // Scroll to top
        (cardStatus.parent?.parent as? ScrollView)?.smoothScrollTo(0, 0)
    }

    // MARK: - Payment Flow

    private fun startPayment() {
        val amountText = etAmount.text.toString()
        val amountDouble = amountText.toDoubleOrNull()
        if (amountDouble == null) {
            showStatus(StatusType.ERROR, "Invalid Amount", "Please enter a valid amount.")
            return
        }

        val amountInCents = (amountDouble * 100).toInt()
        if (amountInCents < 10) {
            showStatus(StatusType.ERROR, "Amount Too Low", "Minimum amount is MYR 0.10")
            return
        }

        setLoading(true)
        showStatus(StatusType.INFO, "Initializing", "Getting checkout session from devstack...")

        // Step 1: Get session token (on background thread)
        Thread {
            try {
                val token = getSessionToken(amountInCents)
                runOnUiThread {
                    showStatus(StatusType.INFO, "Session Created", "Creating DuitNow Pay payment...")
                    // Step 2: Create payment
                    Thread {
                        try {
                            createPayment(token, amountInCents)
                        } catch (e: Exception) {
                            runOnUiThread {
                                setLoading(false)
                                showStatus(StatusType.ERROR, "Payment Failed", e.message ?: "Unknown error")
                            }
                        }
                    }.start()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setLoading(false)
                    showStatus(StatusType.ERROR, "Session Failed", e.message ?: "Unknown error")
                }
            }
        }.start()
    }

    /**
     * Step 1: Get session_token from /v1/checkout/public
     */
    private fun getSessionToken(amount: Int): String {
        val url = URL("$DEVSTACK_BASE_URL/v1/checkout/public?key_id=$RAZORPAY_KEY&amount=$amount&currency=MYR")
        Log.d(TAG, ">>> Fetching session token from: $url")

        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000

        val responseCode = conn.responseCode
        val reader = BufferedReader(InputStreamReader(
            if (responseCode in 200..299) conn.inputStream else conn.errorStream
        ))
        val html = reader.readText()
        reader.close()
        conn.disconnect()

        // Extract session_token from: window.session_token="..."
        val regex = """session_token="([^"]+)"""".toRegex()
        val match = regex.find(html)
        if (match != null) {
            val token = match.groupValues[1]
            Log.d(TAG, ">>> Got session token: ${token.take(20)}...")
            return token
        }

        throw Exception("Could not extract session token from checkout page (HTTP $responseCode)")
    }

    /**
     * Step 2: Create payment via /v1/payments/create/ajax
     */
    private fun createPayment(sessionToken: String, amount: Int) {
        val url = URL("$DEVSTACK_BASE_URL/v1/payments/create/ajax")
        Log.d(TAG, ">>> Creating payment: amount=$amount, bank=$BANK_CODE")

        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.setRequestProperty("X-Razorpay-SessionToken", sessionToken)
        conn.connectTimeout = 30000
        conn.readTimeout = 30000

        val params = listOf(
            "key_id" to RAZORPAY_KEY,
            "amount" to amount.toString(),
            "currency" to "MYR",
            "method" to "duitnow_pay",
            "bank" to BANK_CODE,
            "force_terminal_id" to FORCE_TERMINAL_ID,
            "email" to "test@curlec.com",
            "contact" to "+60123456789",
            "_[library]" to "custom",
            "_[platform]" to "mobile",
        )

        val body = params.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }

        conn.outputStream.use { it.write(body.toByteArray()) }

        val responseCode = conn.responseCode

        // Capture response headers
        val headers = mutableMapOf<String, String>()
        headers["Status"] = responseCode.toString()
        for (i in 0 until (conn.headerFields?.size ?: 0)) {
            val key = conn.getHeaderFieldKey(i) ?: continue
            val value = conn.getHeaderField(i) ?: continue
            headers[key] = value
        }

        val reader = BufferedReader(InputStreamReader(
            if (responseCode in 200..299) conn.inputStream else conn.errorStream
        ))
        val responseStr = reader.readText()
        reader.close()
        conn.disconnect()

        Log.d(TAG, ">>> Payment response: $responseStr")

        runOnUiThread {
            lastResponseHeaders = headers
            setLoading(false)
            handlePaymentResponse(responseStr)
        }
    }

    /**
     * Handle the payment API response
     */
    private fun handlePaymentResponse(responseStr: String) {
        try {
            val json = JSONObject(responseStr)
            val headerLog = buildHeaderLog()

            // Check for errors
            if (json.has("error")) {
                val error = json.getJSONObject("error")
                val desc = error.optString("description", "Unknown error")
                val code = error.optString("code", "")
                val paymentId = error.optJSONObject("metadata")?.optString("payment_id")

                var message = desc
                if (paymentId != null) {
                    message += "\n\nPayment ID: $paymentId"
                }

                showStatus(StatusType.ERROR, "Payment Error ($code)", message, headerLog)
                showResultDetails(json, isError = true)
                return
            }

            // Success — look for redirect URL
            val paymentId = json.optString("payment_id",
                json.optString("razorpay_payment_id", "N/A"))

            val request = json.optJSONObject("request")
            val redirectUrl = request?.optString("url")

            if (redirectUrl != null && redirectUrl.isNotEmpty()) {
                showStatus(StatusType.SUCCESS, "Payment Created!", "Payment ID: $paymentId\nRedirecting to CIMB...", headerLog)
                showResultDetails(json, isError = false)
                handleCIMBRedirect(redirectUrl, paymentId)
            } else {
                showStatus(StatusType.SUCCESS, "Payment Created",
                    "Payment ID: $paymentId\n\nNo redirect URL received — payment may have been processed directly.", headerLog)
                showResultDetails(json, isError = false)
            }

        } catch (e: Exception) {
            showStatus(StatusType.ERROR, "Parse Error", e.message ?: "Invalid response")
        }
    }

    // MARK: - Status Display

    enum class StatusType { INFO, SUCCESS, ERROR }

    private fun showStatus(type: StatusType, title: String, message: String, logText: String? = null) {
        runOnUiThread {
            cardStatus.visibility = View.VISIBLE

            val (bgColor, textColor, dotColor) = when (type) {
                StatusType.INFO -> Triple(COLOR_FEEDBACK_INFO_BG, COLOR_FEEDBACK_INFO_TEXT, COLOR_FEEDBACK_INFO_TEXT)
                StatusType.SUCCESS -> Triple(COLOR_FEEDBACK_SUCCESS_BG, COLOR_FEEDBACK_SUCCESS_TEXT, COLOR_FEEDBACK_SUCCESS_TEXT)
                StatusType.ERROR -> Triple(COLOR_FEEDBACK_ERROR_BG, COLOR_FEEDBACK_ERROR_TEXT, COLOR_FEEDBACK_ERROR_TEXT)
            }

            // Set card background via CardView
            (cardStatus as? androidx.cardview.widget.CardView)?.setCardBackgroundColor(Color.parseColor(bgColor))

            // Set dot color
            val dotDrawable = statusDot.background
            if (dotDrawable is GradientDrawable) {
                dotDrawable.setColor(Color.parseColor(dotColor))
            }

            tvStatusTitle.text = title
            tvStatusTitle.setTextColor(Color.parseColor(textColor))
            tvStatusMessage.text = message
            tvStatusMessage.setTextColor(Color.parseColor(textColor))

            if (logText != null && logText.isNotEmpty()) {
                tvStatusLog.text = logText
                tvStatusLog.visibility = View.VISIBLE
            } else {
                tvStatusLog.visibility = View.GONE
            }

            // Show reset button on non-transient states
            if (type != StatusType.INFO) {
                btnNewPayment.visibility = View.VISIBLE
            }
        }
    }

    private fun showResultDetails(json: JSONObject, isError: Boolean) {
        runOnUiThread {
            resultContainer.removeAllViews()

            val headerTv = TextView(this).apply {
                text = "Response Details"
                setTextColor(Color.parseColor(COLOR_TEXT_SECONDARY))
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            resultContainer.addView(headerTv)

            fun addRow(key: String, value: String) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 8 }
                }

                val keyTv = TextView(this).apply {
                    text = key
                    setTextColor(Color.parseColor(COLOR_TEXT_SUBDUED))
                    textSize = 12f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.35f)
                }

                val valueTv = TextView(this).apply {
                    text = value
                    setTextColor(Color.parseColor(COLOR_TEXT_PRIMARY))
                    textSize = 12f
                    gravity = Gravity.END
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.65f)
                }

                row.addView(keyTv)
                row.addView(valueTv)
                resultContainer.addView(row)
            }

            // Extract and display fields
            val paymentId = json.optString("payment_id", "")
            if (paymentId.isNotEmpty()) {
                addRow("Payment ID", paymentId)
            } else {
                val metaPid = json.optJSONObject("error")?.optJSONObject("metadata")?.optString("payment_id")
                if (metaPid != null) addRow("Payment ID", metaPid)
            }

            json.optString("amount", "").let { if (it.isNotEmpty()) addRow("Amount", it) }

            json.optJSONObject("error")?.let { error ->
                error.optString("code", "").let { if (it.isNotEmpty()) addRow("Error Code", it) }
                error.optString("description", "").let { if (it.isNotEmpty()) addRow("Description", it) }
                error.optString("source", "").let { if (it.isNotEmpty()) addRow("Source", it) }
                error.optString("step", "").let { if (it.isNotEmpty()) addRow("Step", it) }
                error.optString("reason", "").let { if (it.isNotEmpty()) addRow("Reason", it) }
            }

            json.optJSONObject("request")?.let { request ->
                request.optString("method", "").let { if (it.isNotEmpty()) addRow("Method", it) }
                request.optString("url", "").let { if (it.isNotEmpty()) addRow("Redirect URL", it.take(80) + "...") }
            }

            cardResult.visibility = View.VISIBLE
        }
    }

    private fun setLoading(loading: Boolean) {
        runOnUiThread {
            if (loading) {
                progressLoading.visibility = View.VISIBLE
                btnCimb.isEnabled = false
                btnCimb.alpha = 0.7f
            } else {
                progressLoading.visibility = View.GONE
                btnCimb.isEnabled = true
                btnCimb.alpha = 1.0f
            }
        }
    }

    private fun buildHeaderLog(): String {
        val headers = lastResponseHeaders ?: return ""
        val lines = mutableListOf<String>()
        headers["Status"]?.let { lines.add("HTTP $it") }
        val interestingKeys = listOf("X-Razorpay-Request-Id", "X-Request-Id", "Content-Type", "Date", "X-Razorpay-Mode")
        for (key in interestingKeys) {
            headers[key]?.let { lines.add("$key: $it") }
        }
        return lines.joinToString("\n")
    }

    // MARK: - CIMB Deep Link Handling

    private fun handleCIMBRedirect(webUrl: String, paymentId: String) {
        val appDeepLink = transformToAppDeepLink(webUrl)
        Log.d(TAG, ">>> A2A Deep Link: $appDeepLink")

        val uri = Uri.parse(appDeepLink)
        val intent = Intent(Intent.ACTION_VIEW, uri)

        if (intent.resolveActivity(packageManager) != null) {
            showStatus(StatusType.SUCCESS, "Opening CIMB App", "Payment ID: $paymentId\nRedirecting to CIMB UAT app...")
            startActivity(intent)
        } else {
            showStatus(StatusType.INFO, "CIMB App Not Installed",
                "The CIMB UAT app (novuscimboctouat://) is not installed.\n\nPayment ID: $paymentId\n\nDeep link:\n$appDeepLink")
        }
    }

    /**
     * Transform CIMB web URL → app deep link
     * Per Paynet A2A Framework Section 4.2:
     * Replace https://uat3.cimbclicks.com.my/dobb2c/ → novuscimboctouat://
     */
    private fun transformToAppDeepLink(webUrl: String): String {
        var appUrl = webUrl.replace(
            "https://uat3.cimbclicks.com.my/dobb2c/",
            "novuscimboctouat://"
        )
        if (!appUrl.contains("Callback=")) {
            val separator = if (appUrl.contains("?")) "&" else "?"
            val callback = Uri.encode("rzpcurlectestapp://payment/callback")
            appUrl += "${separator}Callback=$callback"
        }
        return appUrl
    }

    // MARK: - Deep Link Callback Handling

    private fun handleDeepLinkCallback(intent: Intent) {
        val uri = intent.data ?: return
        if (uri.scheme != "rzpcurlectestapp") return

        val status = uri.getQueryParameter("Sts")
        val endToEndId = uri.getQueryParameter("EndtoEndId") ?: "N/A"
        val dbtrAgt = uri.getQueryParameter("DbtrAgt") ?: "N/A"

        Log.d(TAG, ">>> Callback: Sts=$status, EndtoEndId=$endToEndId, DbtrAgt=$dbtrAgt")

        val callbackLog = "Callback URL: ${uri}\nSts: $status\nEndtoEndId: $endToEndId\nDbtrAgt: $dbtrAgt"

        when (status) {
            "00" -> {
                showStatus(StatusType.SUCCESS, "Payment Processed",
                    "Transaction has been processed.\n\nEndtoEndId: $endToEndId\nBank: $dbtrAgt\n\nVerify final status via transaction enquiry.",
                    callbackLog)
                showCallbackResult(status, endToEndId, dbtrAgt, uri)
            }
            "97" -> {
                showStatus(StatusType.ERROR, "Bank App Unavailable",
                    "CIMB app could not process this transaction.\nRetrying via web channel is recommended.",
                    callbackLog)
                showCallbackResult(status, endToEndId, dbtrAgt, uri)
            }
            "98" -> {
                showStatus(StatusType.ERROR, "PayNet Error",
                    "Transaction failed at PayNet level.\nPlease try another payment method.",
                    callbackLog)
                showCallbackResult(status, endToEndId, dbtrAgt, uri)
            }
            "99" -> {
                showStatus(StatusType.ERROR, "Issuer Error",
                    "Transaction failed at CIMB (issuer) level.\nPlease try another bank.",
                    callbackLog)
                showCallbackResult(status, endToEndId, dbtrAgt, uri)
            }
            else -> {
                showStatus(StatusType.INFO, "Callback Received",
                    "Status: ${status ?: "nil"}\nEndtoEndId: $endToEndId",
                    callbackLog)
                showCallbackResult(status ?: "unknown", endToEndId, dbtrAgt, uri)
            }
        }
    }

    private fun showCallbackResult(status: String, endToEndId: String, dbtrAgt: String, uri: Uri) {
        runOnUiThread {
            resultContainer.removeAllViews()

            val headerTv = TextView(this).apply {
                text = "Callback Details"
                setTextColor(Color.parseColor(COLOR_TEXT_SECONDARY))
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            resultContainer.addView(headerTv)

            val statusDesc = when (status) {
                "00" -> "Processed"
                "97" -> "App Unavailable"
                "98" -> "PayNet Error"
                "99" -> "Issuer Error"
                else -> "Unknown"
            }

            val rows = listOf(
                "Status Code" to status,
                "Status" to statusDesc,
                "EndtoEndId" to endToEndId,
                "Debtor Agent" to dbtrAgt,
                "Callback URL" to uri.toString(),
            )

            for ((key, value) in rows) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 8 }
                }

                val keyTv = TextView(this).apply {
                    text = key
                    setTextColor(Color.parseColor(COLOR_TEXT_SUBDUED))
                    textSize = 12f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.35f)
                }

                val valueTv = TextView(this).apply {
                    text = value
                    setTextColor(Color.parseColor(COLOR_TEXT_PRIMARY))
                    textSize = 12f
                    gravity = Gravity.END
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.65f)
                }

                row.addView(keyTv)
                row.addView(valueTv)
                resultContainer.addView(row)
            }

            cardResult.visibility = View.VISIBLE
        }
    }
}
