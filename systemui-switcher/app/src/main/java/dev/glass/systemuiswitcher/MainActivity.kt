package dev.glass.systemuiswitcher

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var engine: SwitchEngine
    private val executor = worker
    companion object { private val worker = Executors.newSingleThreadExecutor() }
    private val handler = Handler(Looper.getMainLooper())
    private var snapshot: Snapshot? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var detail: TextView
    private lateinit var outcome: TextView
    private lateinit var progress: ProgressBar
    private lateinit var inspectButton: Button
    private lateinit var op15Button: Button
    private lateinit var aceButton: Button
    private lateinit var rebootButton: Button
    private lateinit var cancelButton: Button
    private val blue = Color.rgb(50, 106, 232)
    private val ink = Color.rgb(22, 33, 54)
    private val secondary = Color.rgb(97, 108, 131)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        engine = SwitchEngine(applicationContext)
        makeUi()
    }
    override fun onResume() {
        super.onResume()
        if (::engine.isInitialized && engine.rootPreviouslyGranted && !busy) runJob("正在核验当前版本…") { engine.inspect() }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float = 15f, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setPadding(0, dp(5), 0, dp(5))
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private fun round(color: Int, radius: Int = 22) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun card(parent: LinearLayout): LinearLayout = column().also {
        it.background = round(Color.WHITE); it.setPadding(dp(20), dp(17), dp(20), dp(18))
        parent.addView(it, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(15) })
    }
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f; minHeight = dp(50)
        backgroundTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(blue, Color.rgb(220, 225, 236)))
        setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(Color.WHITE, Color.rgb(140, 148, 166))))
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(9) }
    }
    private fun makeUi() {
        window.decorView.setBackgroundColor(Color.rgb(242, 245, 251))
        val root = column().apply { setPadding(dp(22), dp(18), dp(22), dp(24)) }
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(dp(22) + bars.left, dp(18) + bars.top, dp(22) + bars.right, dp(24) + bars.bottom)
            insets
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(root) }
        setContentView(scroll)
        root.addView(text("SystemUI 切换器", 28f, bold = true))
        root.addView(text("Ace 6 ↔ 一加 15  ·  17.99.02", 14f, secondary).apply { setPadding(0, dp(3), 0, dp(24)) })
        val current = card(root)
        current.addView(text("当前正在使用", 12f, secondary))
        status = text("尚未检测", 23f, bold = true).also { current.addView(it) }
        detail = text("点击下方检测，并在 root 管理器中授权本应用。", 13f, secondary).also { current.addView(it) }
        inspectButton = button("检测 root 与当前版本") { runJob("正在申请 root 并检测…") { engine.inspect() } }
        current.addView(inspectButton)
        val variants = card(root)
        variants.addView(text("切换版本", 18f, bold = true))
        variants.addView(text("每次切换都先校验 APK，重启后才生效。", 13f, secondary))
        op15Button = button("切换到一加 15 版") {
            confirm("切换到一加 15 版", "安装应用内附带的 SystemUI 17.99.02。系统分区的 Ace 6 原版会保留，安装提交后可重启生效或撤销。") {
                runJob("正在准备并校验 APK…") { engine.installOnePlus15 { percent ->
                    handler.post { if (!isDestroyed) {
                        progress.isIndeterminate = percent < 0
                        if (percent >= 0) progress.progress = percent
                        outcome.text = if (percent < 0) "正在安装并等待系统校验…" else "正在准备 APK · $percent%"
                    } }
                } }
            }
        }
        variants.addView(op15Button)
        aceButton = button("恢复 Ace 6 原版") {
            confirm("恢复 Ace 6 原版", "备份并移出已安装的一加 15 更新，重启后加载 ROM 自带原版。待生效的 SystemUI 安装将先取消。") {
                runJob("正在校验、备份并恢复…") { engine.restoreAce6() }
            }
        }
        variants.addView(aceButton)
        val result = card(root)
        outcome = text(engine.lastMessage.ifBlank { "等待检测。此应用需要 root，不需要 LSPosed。" }, 14f).also { result.addView(it) }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { visibility = View.GONE }
        result.addView(progress, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(12) })
        rebootButton = button("立即重启并生效") {
            confirm("重启手机", "重启后重新打开本应用，会核对实际加载的 APK。") {
                runJob("正在请求重启…") { engine.reboot(); null }
            }
        }.also { result.addView(it) }
        cancelButton = button("撤销待重启操作") { runJob("正在撤销待生效操作…") { engine.cancelPending() } }.also { result.addView(it) }
        result.addView(button("查看操作记录") {
            val body = text(engine.logs.ifBlank { "暂无操作记录" }, 12f).apply { typeface = Typeface.MONOSPACE; setPadding(dp(20), dp(10), dp(20), dp(10)) }
            AlertDialog.Builder(this).setTitle("操作记录").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("关闭", null).show()
        })
        root.addView(text("适用范围\n仅支持 API 37，且 ROM 自带 SystemUI 与所提供的 Ace 6 APK 完全一致的设备。使用 APK 哈希判断版本，不以相同版本号代替校验。\n\n已有音量模块会继续影响界面样式。", 12f, secondary))
        render()
    }
    private fun confirm(title: String, message: String, action: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setNegativeButton("取消", null).setPositiveButton("继续") { _, _ -> action() }.show()
    }
    private fun runJob(message: String, action: () -> Snapshot?) {
        if (busy) return
        busy = true; outcome.text = message; progress.visibility = View.VISIBLE; progress.isIndeterminate = true; render()
        executor.execute {
            val result = runCatching(action)
            handler.post {
                if (isDestroyed || isFinishing) return@post
                busy = false; progress.visibility = View.GONE
                result.onSuccess { next ->
                    if (next != null) snapshot = next
                    outcome.text = engine.lastMessage.ifBlank { "检测完成，请选择需要的版本。" }
                }.onFailure { error ->
                    engine.note("错误：${error.message}")
                    snapshot = null
                    outcome.text = "操作未确认完成\n${error.message ?: error.javaClass.simpleName}\n请重新检测后查看操作记录。"
                }
                render()
            }
        }
    }
    private fun render() {
        val s = snapshot
        val p = engine.pending()
        val supported = s != null && Safety.supported(s)
        val allowed = supported && Safety.currentAllowed(s!!)
        status.text = when {
            s == null -> "尚未检测"
            p?.kind == "restore" && s.currentHash.isEmpty() -> "Ace 6 · 待重启"
            else -> s.variant.label
        }
        detail.text = s?.let {
            "${it.model} · API ${it.sdk}\n${it.rom}\n" +
                if (supported) "root 已授权 · 系统原版校验一致" else "当前 ROM 不在适用范围，切换已禁用"
        } ?: "请检测并授权 root。"
        inspectButton.isEnabled = !busy
        op15Button.isEnabled = !busy && allowed && p == null && s!!.variant != Variant.OP15
        aceButton.isEnabled = !busy && allowed && p == null && (s!!.variant != Variant.ACE6 || s.stagedSessions.isNotEmpty())
        rebootButton.visibility = if (p != null) View.VISIBLE else View.GONE
        rebootButton.isEnabled = !busy && supported && p != null && s!!.boot == p.boot
        cancelButton.visibility = if (p != null || s?.stagedSessions?.isNotEmpty() == true) View.VISIBLE else View.GONE
        cancelButton.isEnabled = !busy && supported && (p == null || s!!.boot == p.boot)
    }
}
