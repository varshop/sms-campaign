package com.example.sms_campaign

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/**
 * إرسال SMS عبر SmsManager مباشرة، مع تأكيد الراديو لكل جزء من الرسالة.
 * القناة: sms_campaign/sms  ·  الدالة: sendSms(phone, message)
 */
class MainActivity : FlutterActivity() {

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "sendSms" -> {
                        val phone = call.argument<String>("phone")
                        val message = call.argument<String>("message")
                        if (phone.isNullOrBlank() || message.isNullOrBlank()) {
                            result.error("INVALID_ARGS", "Empty phone or message", null)
                        } else {
                            sendSms(phone, message, result)
                        }
                    }
                    else -> result.notImplemented()
                }
            }
    }

    @Suppress("DEPRECATION")
    private fun smsManager(): SmsManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }

    private fun sendSms(phone: String, message: String, result: MethodChannel.Result) {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            result.error("NO_TELEPHONY", "Device cannot send SMS", null)
            return
        }
        val manager = smsManager()
        if (manager == null) {
            result.error("NO_SMS_MANAGER", "SmsManager unavailable", null)
            return
        }

        val action = "$packageName.SMS_SENT_${System.nanoTime()}"
        val parts = manager.divideMessage(message)
        val tracker = SendTracker(this, parts.size, result)

        try {
            val filter = IntentFilter(action)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(tracker, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(tracker, filter)
            }

            val flags = PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            val intent = Intent(action).setPackage(packageName)
            val sentIntents = ArrayList<PendingIntent>()
            for (i in parts.indices) {
                sentIntents.add(PendingIntent.getBroadcast(this, i, intent, flags))
            }

            tracker.start()
            manager.sendMultipartTextMessage(phone, null, parts, sentIntents, null)
        } catch (e: SecurityException) {
            tracker.fail("PERMISSION", e.message)
        } catch (e: Exception) {
            tracker.fail("SEND_FAILED", e.message)
        }
    }

    companion object {
        private const val CHANNEL = "sms_campaign/sms"
    }
}

/** يجمع نتائج كل أجزاء الرسالة ثم يردّ على Flutter مرة واحدة. */
private class SendTracker(
    private val context: Context,
    private val total: Int,
    private val result: MethodChannel.Result
) : BroadcastReceiver() {

    private val handler = Handler(Looper.getMainLooper())
    private var received = 0
    private var failure: String? = null
    private var finished = false
    private val timeout = Runnable { complete("TIMEOUT", "No confirmation from radio") }

    fun start() {
        handler.postDelayed(timeout, 90_000L)
    }

    fun fail(code: String, message: String?) {
        complete(code, message)
    }

    override fun onReceive(c: Context?, intent: Intent?) {
        if (finished) return
        received++
        if (resultCode != Activity.RESULT_OK && failure == null) {
            failure = codeFor(resultCode)
        }
        if (received >= total) {
            val code = failure
            complete(code, if (code == null) null else "Radio error: $code")
        }
    }

    private fun complete(code: String?, message: String?) {
        if (finished) return
        finished = true
        handler.removeCallbacks(timeout)
        try {
            context.unregisterReceiver(this)
        } catch (e: Exception) {
            // تم إلغاء التسجيل مسبقاً
        }
        if (code == null) result.success(true) else result.error(code, message, null)
    }

    private fun codeFor(code: Int): String = when (code) {
        SmsManager.RESULT_ERROR_RADIO_OFF -> "RADIO_OFF"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "NO_SERVICE"
        SmsManager.RESULT_ERROR_NULL_PDU -> "NULL_PDU"
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "LIMIT"
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "GENERIC"
        else -> "OTHER_$code"
    }
}
