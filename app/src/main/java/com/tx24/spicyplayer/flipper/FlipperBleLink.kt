package com.tx24.spicyplayer.flipper

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.UUID

/**
 * The phone's way to the Flipper: its Bluetooth serial service, the one the Flipper's own app uses.
 *
 * A Flipper paired with the phone is used first (pairing through the Flipper app counts); otherwise
 * it looks for one nearby, and the Flipper shows a PIN to pair with. The Flipper offers its serial
 * service whenever Bluetooth is on, to its own remote control as much as to Spicy Lyrics, so the
 * link counts as connected only once the Spicy Lyrics app on the Flipper has the channel: then the
 * Flipper reports its remote control as off. Otherwise it lets go and looks again a little later.
 *
 * Needs the nearby devices permission ([FlipperService.permissions]). Callbacks on the main thread.
 */
@SuppressLint("MissingPermission")
internal class FlipperBleLink(context: Context) : FlipperLink {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? = (app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var onLine: (String) -> Unit = {}
    private var onConnected: (Boolean) -> Unit = {}
    private var running = false
    private var gatt: BluetoothGatt? = null
    private var rx: BluetoothGattCharacteristic? = null
    private var ready = false
    private var scanning = false
    private var device: BluetoothDevice? = null
    private var chunk = 20
    private val writes = ArrayDeque<ByteArray>()
    private var writing = false
    private val incoming = StringBuilder()
    private var retryMs = RETRY_MIN_MS

    private val retry = Runnable { connect() }
    private val stopScan = Runnable { endScan(); scheduleRetry() }

    override fun start(onLine: (String) -> Unit, onConnected: (Boolean) -> Unit) {
        this.onLine = onLine
        this.onConnected = onConnected
        running = true
        main.post { connect() }
    }

    override fun stop() {
        running = false
        main.removeCallbacksAndMessages(null)
        endScan()
        close()
    }

    override fun send(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        main.post {
            if (!ready) return@post
            var off = 0
            while (off < bytes.size) {
                val end = minOf(bytes.size, off + chunk)
                writes.addLast(bytes.copyOfRange(off, end))
                off = end
            }
            // Lyrics only matter now: drop a backlog rather than fall behind.
            while (writes.size > MAX_QUEUED) writes.removeFirst()
            pump()
        }
    }

    // Finding the Flipper ---------------------------------------------------------------------

    private fun connect() {
        if (!running || gatt != null) return
        val bt = adapter
        if (bt == null || !bt.isEnabled) {
            scheduleRetry()
            return
        }
        val paired = runCatching { bt.bondedDevices.orEmpty().firstOrNull { it.name.orEmpty().startsWith(NAME_PREFIX) } }.getOrNull()
        val target = device ?: paired
        if (target != null) open(target) else scan()
    }

    private fun scan() {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null || scanning) {
            scheduleRetry()
            return
        }
        scanning = true
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        if (runCatching { scanner.startScan(null, settings, scanCallback) }.isFailure) {
            scanning = false
            scheduleRetry()
            return
        }
        main.postDelayed(stopScan, SCAN_MS)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull()
            if (name?.startsWith(NAME_PREFIX) != true) return
            main.post {
                if (!scanning) return@post
                main.removeCallbacks(stopScan)
                endScan()
                device = result.device
                open(result.device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            main.post { endScan(); scheduleRetry() }
        }
    }

    private fun endScan() {
        if (!scanning) return
        scanning = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
    }

    private fun open(target: BluetoothDevice) {
        val opened = runCatching {
            if (Build.VERSION.SDK_INT >= 23) target.connectGatt(app, false, callback, BluetoothDevice.TRANSPORT_LE)
            else target.connectGatt(app, false, callback)
        }.getOrNull()
        if (opened == null) scheduleRetry() else gatt = opened
    }

    private fun scheduleRetry() {
        if (!running) return
        main.removeCallbacks(retry)
        main.postDelayed(retry, retryMs)
        retryMs = (retryMs * 2).coerceAtMost(RETRY_MAX_MS)
    }

    private fun close() {
        val wasReady = ready
        ready = false
        rx = null
        writes.clear()
        writing = false
        incoming.setLength(0)
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        if (wasReady) onConnected(false)
    }

    /** Lets go of a Flipper that isn't running Spicy Lyrics, and looks again a little later. */
    private fun letGo(slowly: Boolean) {
        close()
        if (slowly) retryMs = RETRY_IDLE_MS
        scheduleRetry()
    }

    // The connection ---------------------------------------------------------------------------

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            main.post {
                if (g != gatt) return@post
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (!g.requestMtu(MTU)) g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    letGo(slowly = false)
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            main.post {
                if (g != gatt) return@post
                if (status == BluetoothGatt.GATT_SUCCESS) chunk = (mtu - 3).coerceIn(20, MAX_CHUNK)
                g.discoverServices()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            main.post {
                if (g != gatt) return@post
                val service = g.getService(SERVICE)
                val tx = service?.getCharacteristic(TX)
                rx = service?.getCharacteristic(RX)
                if (tx == null || rx == null) {
                    Log.w(TAG, "No Flipper serial service")
                    letGo(slowly = true)
                    return@post
                }
                g.setCharacteristicNotification(tx, true)
                val cccd = tx.getDescriptor(CCCD)
                if (cccd == null) {
                    checkOwner(g)
                } else {
                    writeDescriptor(g, cccd, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                }
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            main.post {
                if (g != gatt) return@post
                // The Flipper hands the channel to Spicy Lyrics a moment after connecting.
                main.postDelayed({ if (g == gatt) checkOwner(g) }, OWNER_CHECK_MS)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            @Suppress("DEPRECATION")
            val value = characteristic.value
            main.post { ownerRead(g, characteristic, value, status) }
        }

        override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            main.post { ownerRead(g, characteristic, value, status) }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            main.post { changed(g, characteristic, value) }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            main.post { changed(g, characteristic, value) }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            main.post {
                if (g != gatt) return@post
                writing = false
                pump()
            }
        }
    }

    private fun checkOwner(g: BluetoothGatt) {
        val status = g.getService(SERVICE)?.getCharacteristic(RPC_STATUS)
        if (status == null || !g.readCharacteristic(status)) {
            // An older Flipper without the status: assume it's Spicy Lyrics and say hello.
            becomeReady()
        }
    }

    private fun ownerRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray?, status: Int) {
        if (g != gatt || characteristic.uuid != RPC_STATUS) return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            letGo(slowly = false)
            return
        }
        val remoteControlOn = value != null && value.isNotEmpty() && value.any { it.toInt() != 0 }
        if (remoteControlOn) letGo(slowly = true) else becomeReady()
    }

    private fun becomeReady() {
        if (ready) return
        ready = true
        retryMs = RETRY_MIN_MS
        onConnected(true)
    }

    private fun changed(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (g != gatt || characteristic.uuid != TX) return
        incoming.append(String(value, Charsets.UTF_8))
        while (true) {
            val end = incoming.indexOf("\n")
            if (end < 0) break
            val line = incoming.substring(0, end).trimEnd('\r')
            incoming.delete(0, end + 1)
            if (line.isNotEmpty()) onLine(line)
        }
        if (incoming.length > 2_000) incoming.setLength(0)
    }

    private fun pump() {
        val g = gatt ?: return
        val characteristic = rx ?: return
        if (writing || writes.isEmpty()) return
        val next = writes.removeFirst()
        writing = writeCharacteristic(g, characteristic, next)
        if (!writing) main.postDelayed({ pump() }, 30L)
    }

    @Suppress("DEPRECATION")
    private fun writeCharacteristic(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, bytes: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(characteristic, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS
        } else {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.value = bytes
            g.writeCharacteristic(characteristic)
        }

    @Suppress("DEPRECATION")
    private fun writeDescriptor(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray) {
        val ok = if (Build.VERSION.SDK_INT >= 33) {
            g.writeDescriptor(descriptor, value) == BluetoothGatt.GATT_SUCCESS
        } else {
            descriptor.value = value
            g.writeDescriptor(descriptor)
        }
        if (!ok) main.postDelayed({ if (g == gatt) checkOwner(g) }, OWNER_CHECK_MS)
    }

    private companion object {
        const val TAG = "FlipperBle"
        const val NAME_PREFIX = "Flipper"
        const val MTU = 247
        const val MAX_CHUNK = 240
        const val MAX_QUEUED = 64
        const val SCAN_MS = 12_000L
        const val OWNER_CHECK_MS = 900L
        const val RETRY_MIN_MS = 3_000L
        const val RETRY_MAX_MS = 20_000L
        /** A Flipper on but not running Spicy Lyrics is looked at again this often. */
        const val RETRY_IDLE_MS = 15_000L
        val SERVICE: UUID = UUID.fromString("8fe5b3d5-2e7f-4a98-2a48-7acc60fe0000")
        /** From the Flipper (indications). */
        val TX: UUID = UUID.fromString("19ed82ae-ed21-4c9d-4145-228e61fe0000")
        /** To the Flipper. */
        val RX: UUID = UUID.fromString("19ed82ae-ed21-4c9d-4145-228e62fe0000")
        /** Whether the Flipper's own remote control has the channel (non-zero) or an app does. */
        val RPC_STATUS: UUID = UUID.fromString("19ed82ae-ed21-4c9d-4145-228e64fe0000")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
