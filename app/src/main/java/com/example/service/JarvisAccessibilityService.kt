package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScreenUiElement(
    val type: String, // "BUTTON", "TEXT_FIELD", "LIST", "TEXT"
    val label: String,
    val viewId: String,
    val bounds: String,
    val centerX: Int,
    val centerY: Int,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean
)

data class ScreenSnapshot(
    val packageName: String,
    val capturedAt: Long,
    val visibleTextSummary: String,
    val buttons: List<ScreenUiElement>,
    val textFields: List<ScreenUiElement>,
    val lists: List<ScreenUiElement>,
    val allElementsCount: Int
) {
    fun toPromptSummary(): String {
        val sb = StringBuilder()
        sb.appendLine("Active App Package: $packageName")
        sb.appendLine("Interactive Buttons (${buttons.size}): ${buttons.take(15).joinToString { "'${it.label}' @(${it.centerX},${it.centerY})" }}")
        sb.appendLine("Input Text Fields (${textFields.size}): ${textFields.take(10).joinToString { "'${it.label}' @(${it.centerX},${it.centerY})" }}")
        sb.appendLine("Scrollable Lists (${lists.size}): ${lists.take(5).joinToString { it.viewId.ifBlank { "List" } }}")
        sb.appendLine("Visible Screen Text: ${visibleTextSummary.take(1200)}")
        return sb.toString()
    }
}

class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
        captureCurrentScreen()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            val pkg = event.packageName?.toString().orEmpty()
            if (pkg.isNotEmpty()) {
                _activePackage.value = pkg
            }
        }
    }

    override fun onInterrupt() {
        // Handled gracefully
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
            _isConnected.value = false
        }
    }

    fun captureCurrentScreen(): ScreenSnapshot? {
        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString() ?: _activePackage.value.ifBlank { "unknown" }
        val visibleTexts = mutableListOf<String>()
        val buttons = mutableListOf<ScreenUiElement>()
        val textFields = mutableListOf<ScreenUiElement>()
        val lists = mutableListOf<ScreenUiElement>()
        var totalNodes = 0

        fun traverse(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 28 || totalNodes > 350) return
            totalNodes++

            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val hint = node.hintText?.toString()?.trim().orEmpty()
            val label = when {
                text.isNotEmpty() -> text
                desc.isNotEmpty() -> desc
                hint.isNotEmpty() -> hint
                else -> ""
            }
            val viewId = node.viewIdResourceName?.substringAfterLast("/") ?: ""
            val rect = Rect()
            node.getBoundsInScreen(rect)

            if (label.isNotEmpty() && visibleTexts.size < 80) {
                if (!visibleTexts.contains(label)) {
                    visibleTexts.add(label)
                }
            }

            if (node.isEditable || node.className?.toString()?.contains("EditText", ignoreCase = true) == true) {
                textFields.add(
                    ScreenUiElement(
                        type = "TEXT_FIELD",
                        label = label.ifBlank { viewId.ifBlank { "Input Field" } },
                        viewId = viewId,
                        bounds = "[${rect.left},${rect.top}-${rect.right},${rect.bottom}]",
                        centerX = rect.centerX(),
                        centerY = rect.centerY(),
                        isClickable = node.isClickable,
                        isEditable = true,
                        isScrollable = false
                    )
                )
            } else if (node.isClickable && label.isNotEmpty()) {
                buttons.add(
                    ScreenUiElement(
                        type = "BUTTON",
                        label = label,
                        viewId = viewId,
                        bounds = "[${rect.left},${rect.top}-${rect.right},${rect.bottom}]",
                        centerX = rect.centerX(),
                        centerY = rect.centerY(),
                        isClickable = true,
                        isEditable = false,
                        isScrollable = false
                    )
                )
            }

            if (node.isScrollable) {
                lists.add(
                    ScreenUiElement(
                        type = "LIST",
                        label = label.ifBlank { viewId.ifBlank { "Scrollable Area" } },
                        viewId = viewId,
                        bounds = "[${rect.left},${rect.top}-${rect.right},${rect.bottom}]",
                        centerX = rect.centerX(),
                        centerY = rect.centerY(),
                        isClickable = node.isClickable,
                        isEditable = false,
                        isScrollable = true
                    )
                )
            }

            val childCount = node.childCount
            for (i in 0 until childCount) {
                traverse(node.getChild(i), depth + 1)
            }
        }

        traverse(root, 0)
        val snapshot = ScreenSnapshot(
            packageName = pkg,
            capturedAt = System.currentTimeMillis(),
            visibleTextSummary = visibleTexts.joinToString(" | "),
            buttons = buttons,
            textFields = textFields,
            lists = lists,
            allElementsCount = totalNodes
        )
        _latestSnapshot.value = snapshot
        return snapshot
    }

    fun clickByTextOrDescription(target: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val cleanTarget = target.trim()
        if (cleanTarget.isEmpty()) return false

        val matchedNode = findMatchingNode(root, cleanTarget) ?: return false
        var clickableCurrent: AccessibilityNodeInfo? = matchedNode
        while (clickableCurrent != null) {
            if (clickableCurrent.isClickable) {
                val clicked = clickableCurrent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) return true
            }
            clickableCurrent = clickableCurrent.parent
        }

        val rect = Rect()
        matchedNode.getBoundsInScreen(rect)
        if (rect.width() > 0 && rect.height() > 0) {
            return tapCoordinates(rect.centerX().toFloat(), rect.centerY().toFloat())
        }
        return false
    }

    fun tapCoordinates(x: Float, y: Float): Boolean {
        if (x < 0f || y < 0f) return false
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 90L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    fun focusAndTypeText(textToType: String, targetFieldHint: String? = null): Boolean {
        val root = rootInActiveWindow ?: return false
        val targetNode = findEditableNode(root, targetFieldHint) ?: return false

        targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                textToType
            )
        }
        return targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    fun selectTextInFocusedField(start: Int = 0, end: Int = 100): Boolean {
        val root = rootInActiveWindow ?: return false
        val targetNode = findEditableNode(root, null) ?: return false
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start.coerceAtLeast(0))
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end.coerceAtLeast(start))
        }
        return targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
    }

    fun scrollScreen(direction: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findFirstScrollableNode(root)
        val upperDir = direction.uppercase()
        if (scrollable != null) {
            val action = when (upperDir) {
                "UP", "BACKWARD", "RIGHT" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                else -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            }
            if (scrollable.performAction(action)) return true
        }

        val metrics = resources.displayMetrics
        val cx = metrics.widthPixels / 2f
        val cy = metrics.heightPixels / 2f
        val path = Path()
        when (upperDir) {
            "UP" -> {
                path.moveTo(cx, cy * 0.4f)
                path.lineTo(cx, cy * 1.5f)
            }
            "LEFT" -> {
                path.moveTo(cx * 1.6f, cy)
                path.lineTo(cx * 0.4f, cy)
            }
            "RIGHT" -> {
                path.moveTo(cx * 0.4f, cy)
                path.lineTo(cx * 1.6f, cy)
            }
            else -> { // DOWN
                path.moveTo(cx, cy * 1.5f)
                path.lineTo(cx, cy * 0.4f)
            }
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 320L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    fun performGlobalNavigation(action: String): Boolean {
        return when (action.uppercase().trim()) {
            "BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "HOME" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "RECENTS", "RECENT_APPS" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "NOTIFICATIONS" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "QUICK_SETTINGS" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            else -> false
        }
    }

    private fun findMatchingNode(node: AccessibilityNodeInfo?, query: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        val hint = node.hintText?.toString().orEmpty()
        val viewId = node.viewIdResourceName.orEmpty()

        if (text.contains(query, ignoreCase = true) ||
            desc.contains(query, ignoreCase = true) ||
            hint.contains(query, ignoreCase = true) ||
            viewId.contains(query, ignoreCase = true)
        ) {
            return node
        }
        for (i in 0 until node.childCount) {
            val found = findMatchingNode(node.getChild(i), query)
            if (found != null) return found
        }
        return null
    }

    private fun findEditableNode(node: AccessibilityNodeInfo?, hintQuery: String?): AccessibilityNodeInfo? {
        if (node == null) return null
        val isEdit = node.isEditable || node.className?.toString()?.contains("EditText", ignoreCase = true) == true
        if (isEdit) {
            if (hintQuery.isNullOrBlank()) return node
            val text = node.text?.toString().orEmpty()
            val desc = node.contentDescription?.toString().orEmpty()
            val hint = node.hintText?.toString().orEmpty()
            if (text.contains(hintQuery, ignoreCase = true) ||
                desc.contains(hintQuery, ignoreCase = true) ||
                hint.contains(hintQuery, ignoreCase = true)
            ) {
                return node
            }
        }
        for (i in 0 until node.childCount) {
            val found = findEditableNode(node.getChild(i), hintQuery)
            if (found != null) return found
        }
        if (!hintQuery.isNullOrBlank()) {
            return findEditableNode(node, null)
        }
        return null
    }

    private fun findFirstScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val found = findFirstScrollableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    companion object {
        @Volatile
        var instance: JarvisAccessibilityService? = null
            private set

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _activePackage = MutableStateFlow("")
        val activePackage: StateFlow<String> = _activePackage.asStateFlow()

        private val _latestSnapshot = MutableStateFlow<ScreenSnapshot?>(null)
        val latestSnapshot: StateFlow<ScreenSnapshot?> = _latestSnapshot.asStateFlow()
    }
}
