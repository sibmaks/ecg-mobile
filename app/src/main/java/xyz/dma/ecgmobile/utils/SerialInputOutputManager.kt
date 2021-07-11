package xyz.dma.ecgmobile.utils

import android.os.Process
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import java.io.IOException

private const val TAG = "EM-SerialInputOutputM"
private const val DEBUG = false
private const val BUFFER_SIZE = 1024 * 4

class SerialInputOutputManager(private val serialPort: UsbSerialPort, private val listener: Listener) : Runnable {
    /**
     * default read timeout is infinite, to avoid data loss with bulkTransfer API
     */
    private var readBuffer = ByteArray(BUFFER_SIZE)
    private var mThreadPriority = Process.THREAD_PRIORITY_URGENT_AUDIO

    enum class State {
        STOPPED, RUNNING, STOPPING
    }

    var state = State.STOPPED
        private set


    interface Listener {
        /**
         * Called when new incoming data is available.
         */
        fun onNewData(data: ByteArray, len: Int)

        /**
         * Called when [SerialInputOutputManager.run] aborts due to an error.
         */
        fun onStop()
    }

    /**
     * setThreadPriority. By default use higher priority than UI thread to prevent data loss
     *
     * @param threadPriority  see [Process.setThreadPriority]
     */
    private fun setThreadPriority(threadPriority: Int) {
        check(state == State.STOPPED) { "threadPriority only configurable before SerialInputOutputManager is started" }
        mThreadPriority = threadPriority
    }// when set if already running, read already blocks and the new value will not become effective now

    @Synchronized
    fun stop() {
        if (state == State.RUNNING) {
            Log.i(TAG, "Stop requested")
            state = State.STOPPING
        }
    }

    /**
     * Continuously services the read buffer until [.stop] is
     * called, or until a driver exception is raised.
     */
    override fun run() {
        if (mThreadPriority != Process.THREAD_PRIORITY_DEFAULT) setThreadPriority(mThreadPriority)
        synchronized(this) {
            check(state == State.STOPPED) { "Already running" }
            state = State.RUNNING
        }
        Log.i(TAG, "Running ...")
        try {
            while (!Thread.interrupted()) {
                if (state != State.RUNNING) {
                    Log.i(TAG, "Stopping mState=$state")
                    break
                }
                step()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Run ending due to exception: " + e.message, e)
        } finally {
            synchronized(this) {
                state = State.STOPPED
                Log.i(TAG, "Stopped")
                listener.onStop()
            }
        }
    }

    @Throws(IOException::class)
    private fun step() {
        // Handle incoming data.
        val len = serialPort.read(readBuffer, 0)
        if (len > 0) {
            if (DEBUG) {
                Log.d(TAG, "Read data len=$len")
            }
            this.listener.onNewData(readBuffer, len)
        }
    }
}
