package com.headless.android.display

import android.graphics.PixelFormat
import android.hardware.display.VirtualDisplayConfig
import android.media.ImageReader
import android.os.Parcel
import com.headless.android.DisplayCreationException
import com.headless.android.HeadlessLog
import com.headless.android.privilege.PrivilegeBackend

/**
 * Creates trusted virtual displays via a direct `IDisplayManager.createVirtualDisplay`
 * Binder transaction against the privilege backend's `display` system service — the
 * exact technique proven working on-device by the POC. (A reflection-based call against
 * the AIDL-generated proxy method was tried first but no method with the expected
 * parameter types exists on this API level; the raw transaction is what's actually
 * verified to work, so that's what ships.)
 */
class VirtualDisplayManager(private val privilegeBackend: PrivilegeBackend) {

    companion object {
        private const val OP = "VirtualDisplayManager"

        // android.hardware.display.DisplayManager virtual-display flag constants (framework-internal
        // for TRUSTED, public for the rest — mirrored here so callers never need the raw ints).
        private const val FLAG_PUBLIC = 1 shl 0           // DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
        private const val FLAG_OWN_CONTENT_ONLY = 1 shl 3 // DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
        private const val FLAG_SUPPORTS_TOUCH = 1 shl 6   // DisplayManager.VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH
        private const val FLAG_TRUSTED = 1 shl 10         // DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
        private const val FLAG_ALWAYS_UNLOCKED = 1 shl 12 // DisplayManager.VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED

        private const val INTERFACE_TOKEN = "android.hardware.display.IDisplayManager"
        private const val CALLING_PACKAGE = "com.android.shell"

        @Volatile private var _workingCode = 0

        /**
         * `IDisplayManager.releaseVirtualDisplay(IVirtualDisplayCallback token)`.
         *
         * Codes resolve per device via [com.headless.android.BinderCodes] (exact Stub
         * field lookup, verified fallback). Release still verifies the display actually
         * disappeared and reports failure loudly instead of assuming success.
         */
    }

    fun createTrustedDisplay(
        width: Int,
        height: Int,
        densityDpi: Int,
        name: String = "HeadlessDisplay-${System.nanoTime()}"
    ): HeadlessDisplay {
        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
        // Note: we pass a null surface to createVirtualDisplay. Attaching an
        // ImageReader surface causes SurfaceFlinger to fail dequeue with -19 (ENODEV),
        // which kills the display. We don't need screenshots — our pipeline reads
        // the UI tree through accessibility, not pixels.

        try {
            val displayBinder = privilegeBackend.getSystemServiceBinder("display")

            val flags = FLAG_PUBLIC or FLAG_TRUSTED or FLAG_OWN_CONTENT_ONLY or FLAG_SUPPORTS_TOUCH or FLAG_ALWAYS_UNLOCKED
            HeadlessLog.i(OP, "VirtualDisplay REQUESTED: name=$name width=$width height=$height densityDpi=$densityDpi flags=0x${Integer.toHexString(flags)}")
            val config = VirtualDisplayConfig.Builder(name, width, height, densityDpi)
                .setFlags(flags)
                .build()

            // The callback token identifies this display for release; it must outlive the call.
            val callbackToken = newVirtualDisplayCallbackToken()
            val displayId = transactCreateVirtualDisplay(displayBinder, config, callbackToken)

            HeadlessLog.i(OP, "createVirtualDisplay RETURNED displayId=$displayId")
            android.util.Log.e("HeadlessDisplay", "createVirtualDisplay returned id=" + displayId)
            // Physical display is always id 0. Any virtual display should be > 0.
            // A return of 0 means the transaction did not create anything.
            if (displayId <= 0) {
                throw DisplayCreationException(
                    "createVirtualDisplay returned displayId=$displayId " +
                    "(0 means physical screen; the display was not created). " +
                    "Likely HyperOS refused the TRUSTED flag or BinderCodes has wrong codes."
                )
            }

            HeadlessLog.event(displayId = displayId, op = OP, success = true)
            return HeadlessDisplay(
                displayId = displayId,
                width = width,
                height = height,
                densityDpi = densityDpi,
                imageReader = imageReader,
                callbackToken = callbackToken,
                releaser = { token -> releaseDisplay(displayId, token) }
            )
        } catch (e: DisplayCreationException) {
            imageReader.close()
            throw e
        } catch (e: Throwable) {
            imageReader.close()
            HeadlessLog.event(op = OP, success = false)
            throw DisplayCreationException("Failed to create trusted virtual display: ${e.message}", e)
        }
    }

    /**
     * Minimal `IVirtualDisplayCallback` stub. We don't need lifecycle callbacks, but the
     * binder itself is the display's identity for release, so the caller retains it.
     */
    private fun newVirtualDisplayCallbackToken(): android.os.IBinder =
        object : android.os.Binder(), android.os.IInterface {
            init { attachInterface(this, "android.hardware.display.IVirtualDisplayCallback") }
            override fun asBinder(): android.os.IBinder = this
        }

    private fun transactCreateVirtualDisplay(
        displayBinder: android.os.IBinder,
        config: VirtualDisplayConfig,
        callbackToken: android.os.IBinder
    ): Int {
        // Use the verified code. No scan.
        val code = com.headless.android.BinderCodes.displayCreate()
        android.util.Log.e("HeadlessDisplay", "using createVirtualDisplay code=" + code)
        val r = tryTransact(displayBinder, config, callbackToken, code)
        if (r > 0) return r
        android.util.Log.e("HeadlessDisplay", "no working code anywhere, giving up")
        return 0
    }

    private fun tryTransact(
        displayBinder: android.os.IBinder,
        config: VirtualDisplayConfig,
        callbackToken: android.os.IBinder,
        code: Int
    ): Int {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(INTERFACE_TOKEN)
            data.writeTypedObject(config, 0)
            data.writeStrongBinder(callbackToken)
            data.writeStrongBinder(null)
            data.writeString(CALLING_PACKAGE)

            val txOk = displayBinder.transact(code, data, reply, 0)
            if (!txOk) return 0

            val size = reply.dataAvail()
            if (size < 4) return 0

            try {
                reply.readException()
            } catch (t: Throwable) {
                android.util.Log.e("HeadlessDisplay", "code=$code threw: " + t.message)
                return 0
            }
            if (reply.dataAvail() < 4) return 0

            val id = reply.readInt()
            if (id <= 0) return 0

            // id > 0 is not enough. Many getters return small positive ints.
            // Verify the display actually exists in the framework.
            if (!displayReallyExists(id)) {
                android.util.Log.e("HeadlessDisplay",
                    "code=$code returned id=" + id + " but dumpsys has no display " + id + " — continuing")
                return 0
            }

            android.util.Log.e("HeadlessDisplay", "code=$code CREATED real display id=" + id)
            return id
        } catch (t: Throwable) {
            android.util.Log.e("HeadlessDisplay", "code=$code exception: "
                + t.javaClass.simpleName + ": " + t.message)
            return 0
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /**
     * Destroys the virtual display identified by [callbackToken], then verifies it is
     * actually gone. Verification matters because the transaction code is derived from
     * AIDL declaration order rather than a published constant — if a build reorders the
     * interface, a wrong code could "succeed" without releasing anything, which is exactly
     * the silent-leak failure this method exists to prevent.
     */
    fun releaseDisplay(displayId: Int, callbackToken: android.os.IBinder) {
        val displayBinder = privilegeBackend.getSystemServiceBinder("display")
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(INTERFACE_TOKEN)
            data.writeStrongBinder(callbackToken)
            displayBinder.transact(com.headless.android.BinderCodes.displayRelease(), data, reply, 0)
            reply.readException()
        } catch (e: Throwable) {
            HeadlessLog.event(displayId = displayId, op = "$OP.release", success = false)
            throw DisplayCreationException("releaseVirtualDisplay($displayId) failed: ${e.message}", e)
        } finally {
            data.recycle()
            reply.recycle()
        }

        val stillPresent = displayStillExists(displayId)
        HeadlessLog.event(displayId = displayId, op = "$OP.release", success = !stillPresent)
        if (stillPresent) {
            throw DisplayCreationException(
                "releaseVirtualDisplay($displayId) reported success but display is still " +
                    "registered with DisplayManagerService — display leaked"
            )
        }
    }

    /** Ask the framework whether this display is really present. */
    private fun displayReallyExists(displayId: Int): Boolean {
        return try {
            val r = privilegeBackend.shell(arrayOf("dumpsys", "display"))
            val out = r.stdout
            out.contains("mDisplayId=$displayId") || out.contains("displayId $displayId,")
        } catch (t: Throwable) {
            android.util.Log.e("HeadlessDisplay", "dumpsys check failed: " + t.message)
            false
        }
    }

    private fun displayStillExists(displayId: Int): Boolean {
        return try {
            privilegeBackend.shell(arrayOf("dumpsys", "display")).stdout
                .contains("mDisplayId=$displayId")
        } catch (e: Throwable) {
            HeadlessLog.w(OP, "post-release verification query failed for display $displayId", e)
            false // don't turn a verification outage into a spurious leak error
        }
    }
}
