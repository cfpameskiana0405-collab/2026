package com.ayzen.scan.logic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Environment
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.ayzen.scan.R
import java.io.File
import java.io.FileOutputStream

object Actions {

    private fun start(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent)
        true
    } catch (e: Exception) {
        Toast.makeText(ctx, R.string.no_app_found, Toast.LENGTH_SHORT).show()
        false
    }

    // ---------------------------------------------------------------- clipboard / share

    /** Copies text without showing our own toast (used for auto copy). */
    fun copyQuiet(ctx: Context, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("AYZEN SCAN", text))
    }

    fun copy(ctx: Context, text: String) {
        copyQuiet(ctx, text)
        Toast.makeText(ctx, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    fun shareText(ctx: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        start(ctx, Intent.createChooser(send, null))
    }

    fun shareBitmap(ctx: Context, bitmap: Bitmap) {
        try {
            val dir = File(ctx.cacheDir, "qr").apply { mkdirs() }
            val file = File(dir, "ayzen_code.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            start(ctx, Intent.createChooser(send, null))
        } catch (e: Exception) {
            Toast.makeText(ctx, R.string.create_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /** Saves the image to Pictures/AYZEN SCAN using MediaStore (no permission needed on Android 10+). */
    fun saveToGallery(ctx: Context, bitmap: Bitmap): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "AYZEN_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AYZEN SCAN")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }

    // ---------------------------------------------------------------- open things

    fun openUrl(ctx: Context, raw: String) {
        start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(ContentParser.normalizeUrl(raw))))
    }

    fun webSearch(ctx: Context, query: String) {
        start(
            ctx,
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
        )
    }

    fun dial(ctx: Context, number: String) {
        start(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number.trim()))))
    }

    fun sendSms(ctx: Context, number: String, body: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number.trim()))).apply {
            putExtra("sms_body", body)
        }
        start(ctx, intent)
    }

    fun sendEmail(ctx: Context, to: String, subject: String, body: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        start(ctx, intent)
    }

    fun openMap(ctx: Context, geo: GeoInfo) {
        val uri = if (!geo.query.isNullOrBlank() && geo.lat == 0.0 && geo.lon == 0.0) {
            Uri.parse("geo:0,0?q=" + Uri.encode(geo.query))
        } else {
            Uri.parse("geo:${geo.lat},${geo.lon}?q=${geo.lat},${geo.lon}")
        }
        start(ctx, Intent(Intent.ACTION_VIEW, uri))
    }

    fun addContact(ctx: Context, info: ContactInfo) {
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.NAME, info.name)
            if (info.phone.isNotEmpty()) putExtra(ContactsContract.Intents.Insert.PHONE, info.phone)
            if (info.email.isNotEmpty()) putExtra(ContactsContract.Intents.Insert.EMAIL, info.email)
            if (info.org.isNotEmpty()) putExtra(ContactsContract.Intents.Insert.COMPANY, info.org)
            if (info.address.isNotEmpty()) putExtra(ContactsContract.Intents.Insert.POSTAL, info.address)
        }
        start(ctx, intent)
    }

    fun addEvent(ctx: Context, e: EventInfo) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, e.title)
            putExtra(CalendarContract.Events.EVENT_LOCATION, e.location)
            putExtra(CalendarContract.Events.DESCRIPTION, e.description)
            e.start?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
            e.end?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
        }
        start(ctx, intent)
    }

    /**
     * Android 11+ shows a system dialog to add the network.
     * On older versions (or WEP) the password is copied and Wi-Fi settings are opened.
     */
    fun connectWifi(ctx: Context, info: WifiInfo) {
        val sec = info.security.uppercase()
        val canSuggest = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !sec.contains("WEP")
        if (canSuggest) {
            try {
                val builder = WifiNetworkSuggestion.Builder()
                    .setSsid(info.ssid)
                    .setIsHiddenSsid(info.hidden)
                if (sec != "OPEN" && info.password.isNotEmpty()) {
                    if (sec.contains("WPA3") || sec.contains("SAE")) {
                        builder.setWpa3Passphrase(info.password)
                    } else {
                        builder.setWpa2Passphrase(info.password)
                    }
                }
                val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS).apply {
                    putParcelableArrayListExtra(
                        Settings.EXTRA_WIFI_NETWORK_LIST,
                        arrayListOf(builder.build())
                    )
                }
                if (start(ctx, intent)) return
            } catch (e: Exception) {
                // fall through to the manual flow
            }
        }
        if (info.password.isNotEmpty()) copyQuiet(ctx, info.password)
        Toast.makeText(ctx, R.string.wifi_fallback, Toast.LENGTH_LONG).show()
        start(ctx, Intent(Settings.ACTION_WIFI_SETTINGS))
    }
}
