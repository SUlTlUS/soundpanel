package dev.glass.soundbar

import android.content.Context
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.database.ContentObserver
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import org.json.JSONArray

/** Same provider contract as ColorOS EarphoneController/NoiseReductionDetailTile.
 * All IPC runs off the SystemUI main thread. Unknown capabilities fail closed.
 */
internal class EarphoneModes(context: Context, private val changed: (State?) -> Unit) {
    data class State(
        val name: String, val address: String, val mode: Int, val supported: List<Int>,
        val airpods: Boolean = false, val pending: Boolean = false
    ) {
        val next: Int get() = supported[(supported.indexOf(mode) + 1) % supported.size]
        val title: String get() = title(mode)
        val icon: String get() = when (mode) {
            5 -> "qs_detail_tile_noise_reduction_open"
            10 -> "qs_detail_tile_noise_reduction_adaptive"
            2 -> "qs_detail_tile_noise_reduction_transparent"
            else -> "qs_detail_tile_earphne"
        }
    }

    companion object {
        private const val PROVIDER = "content://com.oplus.melody.provider.EarphoneControlProvider"
        private const val METHOD = "melody_method_noise_reduction"
        private val workerLooper by lazy {
            HandlerThread("SoundbarEarphone").apply { start() }.looper
        }
        // Exact order/values used by NoiseReductionDetailTile.getNextNoiseReductionMode.
        private val order = listOf(1, 5, 10, 2)
        fun title(mode: Int): String = when (mode) {
            5 -> "降噪"
            10 -> "自适应"
            2 -> "通透"
            else -> "降噪关闭"
        }
    }

    private val resolver = context.contentResolver
    private val worker = Handler(workerLooper)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var generation = 0
    @Volatile private var running = false
    private var registered = false
    private var lastPublished: State? = null
    // Worker-thread state only. Vendor setters update their cache before the
    // earbud acknowledgement; a late old-mode report must not repaint the icon.
    private var confirmed: State? = null
    private var candidate: State? = null
    private var candidateSince = 0L
    private var pendingAddress: String? = null
    private var pendingMode = -1
    private var pendingSince = 0L
    private val observer = object : ContentObserver(worker) {
        override fun onChange(selfChange: Boolean) { requestRead() }
    }
    private val poll = object : Runnable {
        override fun run() {
            if (!running) return
            val token = generation
            settle(readState(), token)
            worker.postDelayed(this, 250)
        }
    }

    fun start() {
        if (running) return
        running = true
        val token = ++generation
        worker.post {
            if (!running || token != generation) return@post
            if (!registered) registered = runCatching {
                resolver.registerContentObserver(Uri.parse(PROVIDER), true, observer)
                true
            }.getOrDefault(false)
            worker.removeCallbacks(poll)
            poll.run()
        }
    }

    fun stop() {
        running = false
        generation++
        worker.post {
            worker.removeCallbacks(poll)
            if (registered) runCatching { resolver.unregisterContentObserver(observer) }
            registered = false
        }
    }

    fun cycle(displayed: State) {
        val token = generation
        worker.post {
            if (!running || token != generation) return@post
            // Recheck capability and active device at click time, not a stale Bluetooth cache.
            val current = readState()
            if (current != null && current.address == displayed.address && current.airpods == displayed.airpods) {
                // Rapid taps advance from the last accepted request, not an old
                // vendor readback. Every tap still issues a real native command.
                val baseMode = if (pendingAddress == current.address &&
                    SystemClock.uptimeMillis() - pendingSince < 2500) pendingMode else current.mode
                val nextMode = current.supported[(current.supported.indexOf(baseMode) + 1) % current.supported.size]
                val identity = Binder.clearCallingIdentity()
                try {
                    if (current.airpods) {
                        val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(current.address)
                        val native = oplusDevice(device)
                        // Same wear guard as MyDevices NoiseReductionCommand: ANC/adaptive
                        // require both ears, unless one-ear noise cancellation is enabled.
                        if (nextMode == 5 || nextMode == 10) {
                            val wear = Reflect.call(native, "getExtendFeatureStatus", 2048) as Int
                            val oneEar = Reflect.call(native, "getExtendFeatureStatus", 8192) as Int
                            if (wear == 3 || ((wear == 1 || wear == 2) && oneEar == 0)) {
                                main.post {
                                    if (running && token == generation) android.widget.Toast.makeText(
                                        contextForToast, "请佩戴双耳后切换降噪模式", android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                                return@post
                            }
                        }
                        val rawMode = when (nextMode) { 5 -> 2; 10 -> 4; 2 -> 3; else -> 1 }
                        if (Reflect.call(native, "setExtendFeatureStatus", 4, rawMode) != true) {
                            ModuleDebugLog.w("GlassSoundbar", "Native AirPods mode request rejected")
                            return@post
                        }
                    } else {
                        resolver.call(Uri.parse(PROVIDER), METHOD, null, Bundle().apply {
                            putString("name", current.name)
                            putString("address", current.address)
                            putInt("type", nextMode)
                        })
                    }
                    pendingAddress = current.address
                    pendingMode = nextMode
                    pendingSince = SystemClock.uptimeMillis()
                    candidate = null
                    // Immediate feedback is explicitly pending until readback settles.
                    publish(current.copy(mode = nextMode, pending = true), token)
                    ModuleDebugLog.i("GlassSoundbar", "Requested earphone mode $baseMode -> $nextMode")
                } catch (error: Exception) {
                    ModuleDebugLog.w("GlassSoundbar", "Earphone mode request failed", error)
                } finally {
                    Binder.restoreCallingIdentity(identity)
                }
            }
            // A successful call is not an acknowledgement. Confirm by stable readback.
            settle(readState(), token)
        }
    }

    private fun requestRead() {
        val token = generation
        if (running) settle(readState(), token)
    }

    private fun settle(state: State?, token: Int) {
        if (!running || token != generation) return
        val now = SystemClock.uptimeMillis()
        if (state == null) {
            candidate = null
            confirmed = null
            pendingAddress = null
            publish(null, token)
            return
        }
        if (pendingAddress != null) {
            if (pendingAddress != state.address || now - pendingSince >= 2500) {
                pendingAddress = null
            } else if (state.mode != pendingMode) {
                candidate = null
                return
            }
        }
        if (confirmed == null && pendingAddress == null) {
            confirmed = state
            candidate = null
            publish(state, token)
            return
        }
        if (state == confirmed) {
            candidate = null
            publish(state, token)
            return
        }
        if (state != candidate) {
            candidate = state
            candidateSince = now
            return
        }
        // Require consecutive readbacks across 400 ms, including physical-button
        // changes. Notification bursts alone cannot count as acknowledgement.
        if (now - candidateSince < 400) return
        confirmed = state
        candidate = null
        // Two stable reads acknowledge the command; after this, physical headset
        // button changes are followed immediately through the same state filter.
        pendingAddress = null
        publish(state, token)
    }

    private fun publish(state: State?, token: Int) {
        main.post {
            if (!running || token != generation) return@post
            if (state != lastPublished) {
                ModuleDebugLog.i("GlassSoundbar", "Earphone capability: mode=${state?.mode}, supported=${state?.supported}")
                lastPublished = state
            }
            changed(state)
        }
    }

    private fun readState(): State? {
        val identity = Binder.clearCallingIdentity()
        return try {
            val activeBluetooth = activeAudioDevice()
            val nativeState = activeBluetooth?.let { readAirpods(it) }
            if (nativeState != null) return nativeState
            val active = resolver.query(Uri.parse("$PROVIDER/melody_method_active_device"), null, null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val name = cursor.getColumnIndex("name")
                    val address = cursor.getColumnIndex("address")
                    if (name < 0 || address < 0 || cursor.isNull(name) || cursor.isNull(address)) null
                    else cursor.getString(name) to cursor.getString(address)
                } ?: return null
            if (activeBluetooth != null && activeBluetooth.address != active.second) return null
            resolver.query(Uri.parse("$PROVIDER/$METHOD"), null, "address", arrayOf(active.second), null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val supports = cursor.getColumnIndex("supports")
                    val type = cursor.getColumnIndex("type")
                    if (supports < 0 || type < 0 || cursor.isNull(supports) || cursor.isNull(type)) return@use null
                    val values = JSONArray(cursor.getString(supports))
                    val reported = (0 until values.length()).map { values.getInt(it) }.toSet()
                    val modes = order.filter { it in reported }
                    val mode = cursor.getInt(type)
                    if (modes.size < 2 || mode !in modes) null else State(active.first, active.second, mode, modes)
                }
        } catch (_: Exception) {
            null
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    private val contextForToast = context

    private fun activeAudioDevice(): BluetoothDevice? = runCatching {
        if (contextForToast.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) return@runCatching null
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@runCatching null
        if (!adapter.isEnabled) return@runCatching null
        for (profile in listOf(2, 22, 1)) {
            val devices = Reflect.call(adapter, "getActiveDevices", profile) as? List<*>
            val device = devices?.filterIsInstance<BluetoothDevice>()?.firstOrNull {
                Reflect.call(it, "isConnected") == true
            }
            if (device != null) return@runCatching device
        }
        null
    }.getOrNull()

    private fun oplusDevice(device: BluetoothDevice): Any =
        Class.forName("android.bluetooth.OplusBluetoothDevice")
            .getConstructor(BluetoothDevice::class.java).newInstance(device)

    // Runs in SystemUI, whose permission is checked below; the settings APK never uses Bluetooth.
    @android.annotation.SuppressLint("MissingPermission")
    private fun readAirpods(device: BluetoothDevice): State? = runCatching {
        val native = oplusDevice(device)
        if (Reflect.call(native, "checkIsAirpodsDevice") != true) return@runCatching null
        val mask = Reflect.call(native, "getDeviceExtendFeatureMask") as Int
        // Capability bits from MyDevices AirpodsUtils.FeatureSupportInfo, not model names.
        if (mask and 4 == 0) return@runCatching null
        val modes = buildList {
            add(5)
            if (mask and 8 != 0) add(10)
            if (mask and 512 != 0) add(1)
            if (mask and 1024 != 0) add(2)
        }
        val mode = when (Reflect.call(native, "getExtendFeatureStatus", 4) as Int) {
            1 -> 1; 2 -> 5; 3 -> 2; 4 -> 10; else -> return@runCatching null
        }
        if (modes.size < 2 || mode !in modes) return@runCatching null
        State(device.name.orEmpty(), device.address, mode, modes, airpods = true)
    }.getOrElse {
        ModuleDebugLog.w("GlassSoundbar", "Native AirPods capability query failed", it)
        null
    }
}
