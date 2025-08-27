package com.day.capacitornfc

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED
import android.nfc.NfcAdapter.ACTION_TAG_DISCOVERED
import android.nfc.NfcAdapter.getDefaultAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcBarcode
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.os.Build
import android.util.Log
import android.util.Base64
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import org.json.JSONObject
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.nio.charset.Charset

@CapacitorPlugin(name = "NFC")
class NFCPlugin : Plugin() {
    private var writeMode = false
    private var recordsBuffer: JSArray? = null

    private val techListsArray = arrayOf(
        arrayOf<String>(
            IsoDep::class.java.name,
            MifareClassic::class.java.name,
            MifareUltralight::class.java.name,
            Ndef::class.java.name,
            NdefFormatable::class.java.name,
            NfcBarcode::class.java.name,
            NfcA::class.java.name,
            NfcB::class.java.name,
            NfcF::class.java.name,
            NfcV::class.java.name
        )
    )

    public override fun handleOnNewIntent(intent: Intent?) {
        super.handleOnNewIntent(intent)

        if (intent == null || intent.action.isNullOrBlank()) {
            return
        }

        if (writeMode) {
            Log.d("NFC", "WRITE MODE START")
            handleWriteTag(intent)
            writeMode = false
            recordsBuffer = null
        } else if (ACTION_NDEF_DISCOVERED == intent.action || ACTION_TAG_DISCOVERED == intent.action) {
            Log.d("NFC", "READ MODE START")
            handleReadTag(intent)
        }
    }

    @PluginMethod
    fun isSupported(call: PluginCall) {
        val adapter = NfcAdapter.getDefaultAdapter(this.activity)
        val ret = JSObject()
        ret.put("supported", adapter != null)
        call.resolve(ret)
    }

    @PluginMethod
    fun cancelWriteAndroid(call: PluginCall) {
        this.writeMode = false
        call.resolve()
    }

    @PluginMethod
    fun startScan(call: PluginCall) {
        print("startScan called")
        call.reject("Android NFC scanning does not require 'startScan' method.")
    }

    @PluginMethod
    fun writeNDEF(call: PluginCall) {
        print("writeNDEF called")

        writeMode = true
        recordsBuffer = call.getArray("records")

        call.resolve()
    }

    override fun handleOnPause() {
        super.handleOnPause()
        getDefaultAdapter(this.activity)?.disableForegroundDispatch(this.activity)
    }

    override fun handleOnResume() {
        super.handleOnResume()
        if (getDefaultAdapter(this.activity) == null) return;

        val intent = Intent(context, this.activity.javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val pendingIntent =
            PendingIntent.getActivity(this.activity, 0, intent, PendingIntent.FLAG_MUTABLE)
        val ndefTextIntentFilter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            try {
                addDataType("*/*")
            } catch (e: IntentFilter.MalformedMimeTypeException) {
                throw RuntimeException("Failed to add MIME type.", e)
            }
        }
        val ndefIntentFilter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            addDataScheme("http")
            addDataScheme("https")
        }
        val intentFilters: Array<IntentFilter> = arrayOf(
            ndefIntentFilter,
            ndefTextIntentFilter,
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        )
        getDefaultAdapter(this.activity).enableForegroundDispatch(
            this.activity,
            pendingIntent,
            intentFilters,
            techListsArray
        )
    }

    private fun handleWriteTag(intent: Intent) {
        val records = recordsBuffer?.toList<JSONObject>()
        if (records != null) {
            val ndefRecords = mutableListOf<NdefRecord>()

            try {
                for (record in records) {
                    val payload = record.getJSONArray("payload")
                    val type: String? = record.getString("type")

                    if (payload.length() == 0 || type == null) {
                        notifyListeners(
                            "nfcError",
                            JSObject().put(
                                "error",
                                "Invalid record: payload or type is missing."
                            )
                        )
                        return
                    }

                    val typeBytes = type.toByteArray(Charsets.UTF_8)
                    val payloadBytes = ByteArray(payload.length())
                    for (i in 0 until payload.length()) {
                        payloadBytes[i] = payload.getInt(i).toByte()
                    }

                    ndefRecords.add(
                        NdefRecord(
                            NdefRecord.TNF_WELL_KNOWN,
                            typeBytes,
                            ByteArray(0),
                            payloadBytes
                        )
                    )
                }

                val ndefMessage = NdefMessage(ndefRecords.toTypedArray())
                val tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
                var ndef = Ndef.get(tag)

                if (ndef == null) {
                    val formatable = NdefFormatable.get(tag)
                    if (formatable != null) {
                        try {
                            formatable.connect()
                            val mimeRecord = NdefRecord.createMime(
                                "text/plain", "INIT".toByteArray(
                                    Charset.forName("US-ASCII")
                                )
                            )
                            val msg = NdefMessage(mimeRecord)
                            formatable.format(msg)
                            // Success!
                            // Emit event to Capacitor plugin for success
                            println("Successfully formatted and wrote NDEF message to tag!")
                        } catch (e: IOException) {
                            // Error connecting or formatting
                            // Emit event to Capacitor plugin for error
                            println("Error formatting or writing to NDEF-formatable tag: ${e.message}")
                        } catch (e: Exception) { // Catch other potential exceptions during format, like TagLostException
                            println("Error during NDEF formatting: ${e.message}")
                        } finally {
                            try {
                                formatable.close()
                            } catch (e: IOException) {
                                println("Error closing NdefFormatable connection: ${e.message}")
                            }
                        }

                        ndef = Ndef.get(formatable.tag)
                    } else {
                        notifyListeners(
                            "nfcError",
                            JSObject().put(
                                "error",
                                "Tag does not support NDEF writing."
                            )
                        )
                        return
                    }
                }

                ndef.use { // Use block ensures ndef.close() is called
                    ndef.connect()
                    if (!ndef.isWritable) {
                        notifyListeners(
                            "nfcError",
                            JSObject().put(
                                "error",
                                "NFC tag is not writable"
                            )
                        )
                        return
                    }
                    if (ndef.maxSize < ndefMessage.toByteArray().size) {
                        notifyListeners(
                            "nfcError",
                            JSObject().put(
                                "error",
                                "Message too large for this NFC Tag (max ${ndef.maxSize} bytes)."
                            )
                        )
                        return
                    }

                    ndef.writeNdefMessage(ndefMessage)
                    Log.d("NFC", "NDEF message successfully written to tag.")
                }

                notifyListeners("nfcWriteSuccess", JSObject().put("success", true))
            } catch (e: UnsupportedEncodingException) {
                Log.e("NFC", "Encoding error during NDEF record creation: ${e.message}")
                notifyListeners(
                    "nfcError",
                    JSObject().put(
                        "error",
                        "Encoding error: ${e.message}"
                    )
                )
            } catch (e: IOException) {
                Log.e("NFC", "I/O error during NFC write: ${e.message}")
                notifyListeners(
                    "nfcError",
                    JSObject().put(
                        "error",
                        "NFC I/O error: ${e.message}"
                    )
                )
            } catch (e: Exception) {
                Log.e("NFC", "Error writing NDEF message: ${e.message}", e)
                notifyListeners(
                    "nfcError",
                    JSObject().put(
                        "error",
                        "Failed to write NDEF message: ${e.message}"
                    )
                )
            }
        } else {
            notifyListeners("nfcError", JSObject().put("error", "Failed to write NFC tag"))
        }
    }

    private fun handleReadTag(intent: Intent) {
        val jsResponse = JSObject()

        val ndefMessages = JSArray()

        when (intent.action) {
//            NfcAdapter.ACTION_NDEF_DISCOVERED -> {
//                val receivedMessages = intent.getParcelableArrayExtra(
//                    EXTRA_NDEF_MESSAGES,
//                    NdefMessage::class.java
//                )
//
//                receivedMessages?.also { rawMessages ->
//                    for (message in rawMessages) {
//                        val ndefRecords = JSArray()
//                        for (record in message.records) {
//                            val rec = JSObject()
//                            rec.put("type", String(record.type, Charsets.UTF_8))
//                            rec.put("payload", Base64.getEncoder().encodeToString(record.payload))
//                            ndefRecords.put(rec)
//                        }
//
//                        val msg = JSObject()
//                        msg.put("records", ndefRecords)
//                        ndefMessages.put(msg)
//                    }
//                }
//            }
            NfcAdapter.ACTION_NDEF_DISCOVERED -> {
                val receivedMessages: Array<NdefMessage>? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayExtra(
                            NfcAdapter.EXTRA_NDEF_MESSAGES,
                            NdefMessage::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
                            ?.map { it as NdefMessage }?.toTypedArray()
                    }
                receivedMessages?.also { rawMessages ->
                    for (message in rawMessages) {
                        val ndefRecords = JSArray()
                        for (record in message.records) {
                            val rec = JSObject()
                            when {
                                record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(
                                    NdefRecord.RTD_URI
                                ) -> {
                                    val uri = parseUriRecord(record)
                                    rec.put("type", "string/url")
                                    rec.put("payload", Base64.encodeToString(uri.toByteArray(), Base64.DEFAULT))
                                }
                                record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(
                                    NdefRecord.RTD_TEXT
                                ) -> {
                                    rec.put("type", "string/text")
                                    rec.put("payload", Base64.encodeToString(parseTextRecord(record).toByteArray(), Base64.DEFAULT))
                                }

                                else -> {
                                    rec.put("type", "string/" + String(record.type, Charsets.UTF_8)+"-base64")
                                    rec.put(
                                        "payload",
                                        Base64.encodeToString(record.payload, Base64.DEFAULT)
                                    )
                                }
                            }
                            ndefRecords.put(rec)
                        }
                        val msg = JSObject()
                        msg.put("records", ndefRecords)
                        ndefMessages.put(msg)
                    }
                }
            }

            NfcAdapter.ACTION_TAG_DISCOVERED -> {
                val tagId = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID)
                val result = if (tagId != null) byteArrayToHexString(tagId) else ""

                val rec = JSObject()
                rec.put("type", "ID")
                rec.put("payload", Base64.encodeToString(result.toByteArray(), Base64.DEFAULT))

                val ndefRecords = JSArray()
                ndefRecords.put(rec)

                val msg = JSObject()
                msg.put("records", ndefRecords)
                ndefMessages.put(msg)
            }
        }

        jsResponse.put("messages", ndefMessages)
        this.notifyListeners("nfcTag", jsResponse)
    }

    private fun byteArrayToHexString(inarray: ByteArray): String {
        val hex =
            arrayOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F")
        var out = ""

        for (j in inarray.size - 1 downTo 0) {
            val `in` = inarray[j].toInt() and 0xff
            val i1 = (`in` shr 4) and 0x0f
            out += hex[i1]
            val i2 = `in` and 0x0f
            out += hex[i2]
        }
        return out
    }
    /**
     * 解析 NDEF URI 记录.
     * URI 记录的 payload 第一个字节是协议前缀，后面才是 URI 的剩余部分.
     */
    private fun parseUriRecord(record: NdefRecord): String {
        val payload = record.payload
        val prefixCode = payload[0].toInt() and 0xFF
        val prefix = URI_PREFIX_MAP[prefixCode] ?: ""
        val uriBody = String(payload, 1, payload.size - 1, Charsets.UTF_8)
        return prefix + uriBody
    }

    /**
     * 解析 NDEF Text 记录.
     * Text 记录的 payload 第一个字节是状态字节，包含了编码和语言代码长度信息.
     */
    private fun parseTextRecord(record: NdefRecord): String {
        val payload = record.payload
        val status = payload[0].toInt()
        val encoding = if ((status and 0x80) == 0) Charsets.UTF_8 else Charsets.UTF_16
        val languageCodeLength = status and 0x3F
        return String(
            payload,
            languageCodeLength + 1,
            payload.size - languageCodeLength - 1,
            encoding
        )
    }
    // NFC Forum URI 协议前缀映射表
    private val URI_PREFIX_MAP = mapOf(
        0x00 to "",
        0x03 to "http://",
        0x04 to "https://",
    )

}