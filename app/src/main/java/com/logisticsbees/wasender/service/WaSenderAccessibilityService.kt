package com.logisticsbees.wasender.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * State-machine-driven AccessibilityService.
 *
 * Per-contact flow:
 *   IDLE → OPENING → WAITING_WINDOW → TYPING → CLICKING_SEND → DONE → IDLE
 */
class WaSenderAccessibilityService : AccessibilityService() {

    companion object {
        const val WA_PKG  = "com.whatsapp"
        const val WAB_PKG = "com.whatsapp.w4b"

        // Outbound broadcasts (→ MessageSenderService)
        const val ACTION_SENT   = "com.logisticsbees.wasender.SENT"
        const val ACTION_FAILED = "com.logisticsbees.wasender.FAILED"
        const val EXTRA_CID     = "contact_id"
        const val EXTRA_ERR     = "error"

        private var _instance: WaSenderAccessibilityService? = null
        fun getInstance() = _instance

        fun isEnabled(ctx: Context): Boolean {
            val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE)
                    as android.view.accessibility.AccessibilityManager
            return am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            ).any { it.resolveInfo.serviceInfo.packageName == ctx.packageName }
        }
    }

    private enum class State { IDLE, OPENING, WAITING_WINDOW, TYPING, CLICKING_SEND }

    private var state      = State.IDLE
    private var phone      = ""
    private var message    = ""
    private var contactId  = -1L
    private var useBiz     = false
    private var retries    = 0
    private val handler    = Handler(Looper.getMainLooper())

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        _instance = this
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            packageNames        = arrayOf(WA_PKG, WAB_PKG)
            feedbackType        = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags               = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
    }

    override fun onDestroy()   { _instance = null; super.onDestroy() }
    override fun onInterrupt() {}

    // ── Public API (called by MessageSenderService on main thread) ────────────

    fun send(p: String, msg: String, cId: Long, biz: Boolean = false) {
        if (state != State.IDLE) return
        phone = p; message = msg; contactId = cId; useBiz = biz; retries = 0
        state = State.OPENING
        openChat(p, biz)
    }

    val isBusy: Boolean get() = state != State.IDLE

    // ── Open WhatsApp chat ────────────────────────────────────────────────────

    private fun openChat(p: String, biz: Boolean) {
        val clean = p.replace(Regex("[^\\d+]"), "")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$clean")).apply {
            if (biz) setPackage(WAB_PKG)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        applicationContext.startActivity(intent)
        state = State.WAITING_WINDOW
    }

    // ── Accessibility event dispatcher ────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg != WA_PKG && pkg != WAB_PKG) return

        when (state) {
            State.WAITING_WINDOW -> {
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
                    handler.postDelayed({ tryType() }, 800)
            }
            State.TYPING -> {
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                    handler.postDelayed({ trySend() }, 400)
            }
            else -> {}
        }
    }

    // ── Step 1: type message ──────────────────────────────────────────────────

    private fun tryType() {
        if (state != State.WAITING_WINDOW && state != State.TYPING) return
        val root = rootInActiveWindow ?: return retry("No root window")

        dismissDialogs(root)

        val input = findInput(root) ?: return retry("Input not found (retry $retries)")
        state = State.TYPING
        input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        input.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    message
                )
            }
        )
        handler.postDelayed({ trySend() }, 600)
    }

    // ── Step 2: click send ────────────────────────────────────────────────────

    private fun trySend() {
        if (state != State.TYPING && state != State.CLICKING_SEND) return
        val root  = rootInActiveWindow ?: return retry("No root on send")
        val input = findInput(root)

        if (input?.text.isNullOrEmpty()) return retry("Text empty, retry $retries")

        val send = findSend(root) ?: return retry("Send btn not found")
        state = State.CLICKING_SEND
        send.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        handler.postDelayed({ finishSend() }, 600)
    }

    private fun finishSend() {
        broadcast(ACTION_SENT, contactId)
        handler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
            reset()
        }, 400)
    }

    // ── Retry / fail ──────────────────────────────────────────────────────────

    private fun retry(reason: String) {
        if (++retries >= 5) {
            broadcast(ACTION_FAILED, contactId, reason)
            performGlobalAction(GLOBAL_ACTION_BACK)
            reset()
            return
        }
        handler.postDelayed({ tryType() }, 900L * retries)
    }

    private fun reset() {
        state = State.IDLE; phone = ""; message = ""; contactId = -1L; retries = 0
    }

    // ── Node finders ──────────────────────────────────────────────────────────

    private fun findInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (pkg in listOf(WA_PKG, WAB_PKG)) {
            root.findAccessibilityNodeInfosByViewId("$pkg:id/entry")
                .firstOrNull()?.let { return it }
        }
        return findEditText(root)
    }

    private fun findSend(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (pkg in listOf(WA_PKG, WAB_PKG)) {
            root.findAccessibilityNodeInfosByViewId("$pkg:id/send")
                .firstOrNull { it.isClickable }?.let { return it }
        }
        return findClickableByDesc(root, "send")
    }

    private fun dismissDialogs(root: AccessibilityNodeInfo) {
        listOf("Continue", "OK", "CONTINUE", "Yes", "Allow").forEach { label ->
            root.findAccessibilityNodeInfosByText(label)
                .firstOrNull { it.isClickable }
                ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    private fun findEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className?.toString() == "android.widget.EditText") return node
        for (i in 0 until node.childCount) {
            findEditText(node.getChild(i) ?: continue)?.let { return it }
        }
        return null
    }

    private fun findClickableByDesc(node: AccessibilityNodeInfo, d: String): AccessibilityNodeInfo? {
        if (node.isClickable && node.contentDescription?.toString()?.lowercase()?.contains(d) == true) return node
        for (i in 0 until node.childCount) {
            findClickableByDesc(node.getChild(i) ?: continue, d)?.let { return it }
        }
        return null
    }

    // ── Broadcast helpers ─────────────────────────────────────────────────────

    private fun broadcast(action: String, cId: Long, err: String = "") {
        sendBroadcast(Intent(action).apply {
            putExtra(EXTRA_CID, cId)
            if (err.isNotEmpty()) putExtra(EXTRA_ERR, err)
            setPackage(packageName)
        })
    }
}
