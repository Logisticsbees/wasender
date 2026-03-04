package com.example.bulksender.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class WhatsAppAccessibilityService : AccessibilityService() {

    private val TAG = "WhatsAppService"
    private val WHATSAPP_PACKAGE = "com.whatsapp"
    private val handler = Handler(Looper.getMainLooper())

    // —————————————————————————————————————————
    //  State Machine
    // —————————————————————————————————————————
    private var isRunning = false
    private var contactQueue: ArrayDeque<String> = ArrayDeque()
    private var currentMessage: String = ""
    private var currentStep: Step = Step.IDLE

    enum class Step { IDLE, OPEN_CHAT, WAIT_FOR_CHAT, TYPE_MESSAGE, CLICK_SEND, DONE }

    companion object {
        // Expose a static reference so your Activity/ViewModel can control the service
        var instance: WhatsAppAccessibilityService? = null

        // Call this from your CampaignScreen "Start" button
        fun startCampaign(contacts: List<String>, message: String) {
            instance?.begin(contacts, message)
        }

        fun stopCampaign() {
            instance?.stopAll()
        }
    }

    // —————————————————————————————————————————
    //  Lifecycle
    // —————————————————————————————————————————
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Service Connected & Ready")

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            packageNames = arrayOf(WHATSAPP_PACKAGE)
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    // —————————————————————————————————————————
    //  Public Controls
    // —————————————————————————————————————————
    fun begin(contacts: List<String>, message: String) {
        contactQueue = ArrayDeque(contacts)
        currentMessage = message
        isRunning = true
        processNext()
    }

    fun stopAll() {
        isRunning = false
        contactQueue.clear()
        currentStep = Step.IDLE
    }

    // —————————————————————————————————————————
    //  Core Event Loop
    // —————————————————————————————————————————
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isRunning || event == null) return

        when (currentStep) {

            Step.WAIT_FOR_CHAT -> {
                // Wait until the chat window for this contact is fully loaded
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                    event.packageName?.toString() == WHATSAPP_PACKAGE
                ) {
                    // Delay slightly to let the UI fully render, then type
                    handler.postDelayed({ typeMessage() }, 1000)
                }
            }

            Step.TYPE_MESSAGE -> {
                // After typing is confirmed, wait for the send button to appear
            }

            else -> { /* Other steps handled manually, not event-driven */ }
        }
    }

    // —————————————————————————————————————————
    //  Step Processor
    // —————————————————————————————————————————
    private fun processNext() {
        if (!isRunning || contactQueue.isEmpty()) {
            currentStep = Step.DONE
            Log.d(TAG, "All contacts processed.")
            return
        }

        val phone = contactQueue.removeFirst()
        currentStep = Step.OPEN_CHAT
        openWhatsAppChat(phone)
    }

    // Step 1: Open WhatsApp directly in the correct chat using a deep link
    private fun openWhatsAppChat(phoneNumber: String) {
        Log.d(TAG, "Opening chat for: $phoneNumber")
        currentStep = Step.WAIT_FOR_CHAT

        // Format: wa.me/CountryCodeNumber, e.g., "919876543210" for India
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$phoneNumber")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(WHATSAPP_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            applicationContext.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open WhatsApp: ${e.message}")
            // Skip this number and move to the next
            handler.postDelayed({ processNext() }, 500)
        }
    }

    // Step 2: Find the text input box and type the message
    private fun typeMessage() {
        Log.d(TAG, "Attempting to type message...")
        currentStep = Step.TYPE_MESSAGE

        val root = rootInActiveWindow ?: run {
            Log.e(TAG, "No active window found")
            return
        }

        // ——— CUSTOMISE THIS ———
        // Find the message input field. You can use:
        //   - findAccessibilityNodeInfosByViewId("com.whatsapp:id/entry") for standard WA
        //   - findAccessibilityNodeInfosByText("Type a message") as fallback
        val inputField = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/entry")
            ?.firstOrNull()
            ?: root.findAccessibilityNodeInfosByText("Type a message")?.firstOrNull()

        if (inputField != null) {
            // Set the text programmatically
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    currentMessage
                )
            }
            inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            Log.d(TAG, "Message typed successfully.")

            // Wait a moment then click Send
            handler.postDelayed({ clickSend(root) }, 800)
        } else {
            Log.e(TAG, "Could not find message input field. Skipping.")
            handler.postDelayed({ processNext() }, 1500)
        }

        root.recycle()
    }

    // Step 3: Find and click the Send button
    private fun clickSend(root: AccessibilityNodeInfo) {
        Log.d(TAG, "Attempting to click Send...")
        currentStep = Step.CLICK_SEND

        // ——— CUSTOMISE THIS ———
        // The send button in standard WhatsApp has this resource ID.
        // Use the Layout Inspector in Android Studio to verify the exact ID on your target version.
        val sendButton = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
            ?.firstOrNull()

        if (sendButton != null && sendButton.isClickable) {
            sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "Send clicked! Moving to next contact.")

            // Delay before processing the next contact to appear natural
            val delayMs = (3000..6000).random().toLong() // 3–6 second random delay
            handler.postDelayed({ processNext() }, delayMs)
        } else {
            Log.e(TAG, "Send button not found or not clickable. Skipping.")
            handler.postDelayed({ processNext() }, 2000)
        }
    }
}
