package com.ayzen.scan.logic

import android.net.Uri
import android.util.Patterns
import com.ayzen.scan.R
import com.ayzen.scan.data.ContentType
import com.google.mlkit.vision.barcode.common.Barcode
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class Detail(val label: Int, val value: String)
data class WifiInfo(val ssid: String, val password: String, val security: String, val hidden: Boolean)
data class EmailInfo(val to: String, val subject: String, val body: String)
data class SmsInfo(val number: String, val body: String)
data class GeoInfo(val lat: Double, val lon: Double, val query: String?)
data class ContactInfo(
    val name: String,
    val phone: String,
    val email: String,
    val org: String,
    val address: String
)
data class EventInfo(
    val title: String,
    val location: String,
    val description: String,
    val start: Long?,
    val end: Long?
)

object ContentParser {

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
    private val INTL_PHONE_REGEX = Regex("^\\+[0-9 ()\\-]{7,20}$")
    private val SCHEME_URL_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*://\\S+$")

    // ------------------------------------------------------------------ detection

    fun detect(raw: String, format: Int): ContentType {
        val t = raw.trim()
        val lower = t.lowercase()
        return when {
            lower.startsWith("wifi:") -> ContentType.WIFI
            lower.startsWith("begin:vcard") || lower.startsWith("mecard:") ||
                lower.startsWith("bizcard:") -> ContentType.CONTACT
            lower.startsWith("begin:vevent") || lower.startsWith("begin:vcalendar") -> ContentType.EVENT
            lower.startsWith("mailto:") || lower.startsWith("matmsg:") ||
                EMAIL_REGEX.matches(t) -> ContentType.EMAIL
            lower.startsWith("tel:") || INTL_PHONE_REGEX.matches(t) -> ContentType.PHONE
            lower.startsWith("smsto:") || lower.startsWith("sms:") ||
                lower.startsWith("mmsto:") -> ContentType.SMS
            lower.startsWith("geo:") -> ContentType.GEO
            isUrl(t) -> ContentType.URL
            format == Barcode.FORMAT_EAN_13 && (t.startsWith("978") || t.startsWith("979")) -> ContentType.BOOK
            format == Barcode.FORMAT_EAN_13 || format == Barcode.FORMAT_EAN_8 ||
                format == Barcode.FORMAT_UPC_A || format == Barcode.FORMAT_UPC_E -> ContentType.PRODUCT
            else -> ContentType.TEXT
        }
    }

    fun isUrl(t: String): Boolean {
        if (t.isBlank() || t.contains(' ') || t.contains('\n')) return false
        if (SCHEME_URL_REGEX.matches(t)) return true
        if (t.lowercase().startsWith("www.")) return true
        return Patterns.WEB_URL.matcher(t).matches()
    }

    fun normalizeUrl(s: String): String {
        val t = s.trim()
        return if (Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*://.*").matches(t)) t else "https://$t"
    }

    // ------------------------------------------------------------------ parsers

    /** Parses "KEY:value;KEY2:value2;;" strings (WIFI:, MECARD:, MATMSG:) honouring backslash escapes. */
    private fun parseFields(body: String): Map<String, String> {
        val parts = ArrayList<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '\\' && i + 1 < body.length) {
                sb.append(body[i + 1])
                i += 2
                continue
            }
            if (c == ';') {
                parts.add(sb.toString())
                sb.clear()
                i++
                continue
            }
            sb.append(c)
            i++
        }
        if (sb.isNotEmpty()) parts.add(sb.toString())
        val map = LinkedHashMap<String, String>()
        for (p in parts) {
            val idx = p.indexOf(':')
            if (idx > 0) map[p.substring(0, idx).trim().uppercase()] = p.substring(idx + 1)
        }
        return map
    }

    private fun parseQuery(q: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        q.split('&').forEach { p ->
            val i = p.indexOf('=')
            if (i > 0) map[p.substring(0, i).lowercase()] = Uri.decode(p.substring(i + 1))
        }
        return map
    }

    fun parseWifi(raw: String): WifiInfo? {
        val t = raw.trim()
        if (!t.startsWith("WIFI:", ignoreCase = true)) return null
        val f = parseFields(t.substring(5))
        val ssid = f["S"] ?: return null
        var security = (f["T"] ?: "").uppercase()
        if (security.isEmpty() || security == "NOPASS") security = "OPEN"
        return WifiInfo(
            ssid = ssid,
            password = f["P"].orEmpty(),
            security = security,
            hidden = f["H"].equals("true", ignoreCase = true)
        )
    }

    fun parseEmail(raw: String): EmailInfo? {
        val t = raw.trim()
        return when {
            t.startsWith("mailto:", ignoreCase = true) -> {
                val rest = t.substring(7)
                val q = rest.indexOf('?')
                val to = Uri.decode(if (q >= 0) rest.substring(0, q) else rest)
                val params = if (q >= 0) parseQuery(rest.substring(q + 1)) else emptyMap()
                EmailInfo(to, params["subject"].orEmpty(), params["body"].orEmpty())
            }
            t.startsWith("matmsg:", ignoreCase = true) -> {
                val f = parseFields(t.substring(7))
                EmailInfo(f["TO"].orEmpty(), f["SUB"].orEmpty(), f["BODY"].orEmpty())
            }
            EMAIL_REGEX.matches(t) -> EmailInfo(t, "", "")
            else -> null
        }
    }

    fun parsePhone(raw: String): String {
        val t = raw.trim()
        return if (t.startsWith("tel:", ignoreCase = true)) t.substring(4) else t
    }

    fun parseSms(raw: String): SmsInfo? {
        val t = raw.trim()
        val colon = t.indexOf(':')
        if (colon < 0) return null
        val rest = t.substring(colon + 1)
        val q = rest.indexOf('?')
        if (q >= 0) {
            val params = parseQuery(rest.substring(q + 1))
            return SmsInfo(Uri.decode(rest.substring(0, q)), params["body"].orEmpty())
        }
        val c = rest.indexOf(':')
        return if (c >= 0) SmsInfo(rest.substring(0, c), rest.substring(c + 1)) else SmsInfo(rest, "")
    }

    fun parseGeo(raw: String): GeoInfo? {
        val t = raw.trim()
        if (!t.startsWith("geo:", ignoreCase = true)) return null
        val rest = t.substring(4)
        val q = rest.indexOf('?')
        val coords = (if (q >= 0) rest.substring(0, q) else rest).split(',', ';')
        val lat = coords.getOrNull(0)?.toDoubleOrNull() ?: return null
        val lon = coords.getOrNull(1)?.toDoubleOrNull() ?: return null
        val query = if (q >= 0) parseQuery(rest.substring(q + 1))["q"] else null
        return GeoInfo(lat, lon, query)
    }

    fun parseContact(raw: String): ContactInfo? {
        val t = raw.trim()
        return when {
            t.startsWith("MECARD:", ignoreCase = true) || t.startsWith("BIZCARD:", ignoreCase = true) -> {
                val f = parseFields(t.substring(t.indexOf(':') + 1))
                val name = f["N"].orEmpty().split(',').map { it.trim() }
                    .filter { it.isNotEmpty() }.reversed().joinToString(" ")
                ContactInfo(name, f["TEL"].orEmpty(), f["EMAIL"].orEmpty(), f["ORG"].orEmpty(), f["ADR"].orEmpty())
            }
            t.startsWith("BEGIN:VCARD", ignoreCase = true) -> {
                var fn = ""
                var n = ""
                var tel = ""
                var email = ""
                var org = ""
                var adr = ""
                t.lineSequence().forEach { line ->
                    val l = line.trim()
                    val idx = l.indexOf(':')
                    if (idx > 0) {
                        val key = l.substring(0, idx).substringBefore(';').substringAfterLast('.').uppercase()
                        val v = l.substring(idx + 1).trim()
                        when (key) {
                            "FN" -> fn = v
                            "N" -> {
                                val p = v.split(';')
                                n = "${p.getOrNull(1).orEmpty()} ${p.getOrNull(0).orEmpty()}".trim()
                            }
                            "TEL" -> if (tel.isEmpty()) tel = v
                            "EMAIL" -> if (email.isEmpty()) email = v
                            "ORG" -> org = v.replace(";", " ").trim()
                            "ADR" -> adr = v.split(';').filter { it.isNotBlank() }.joinToString(", ")
                        }
                    }
                }
                ContactInfo(fn.ifEmpty { n }, tel, email, org, adr)
            }
            else -> null
        }
    }

    fun parseEvent(raw: String): EventInfo? {
        val t = raw.trim()
        if (!t.contains("BEGIN:VEVENT", ignoreCase = true)) return null
        var title = ""
        var location = ""
        var description = ""
        var start: Long? = null
        var end: Long? = null
        t.lineSequence().forEach { line ->
            val l = line.trim()
            val idx = l.indexOf(':')
            if (idx > 0) {
                val key = l.substring(0, idx).substringBefore(';').uppercase()
                val v = unescapeIcs(l.substring(idx + 1))
                when (key) {
                    "SUMMARY" -> title = v
                    "LOCATION" -> location = v
                    "DESCRIPTION" -> description = v
                    "DTSTART" -> start = parseIcsDate(v)
                    "DTEND" -> end = parseIcsDate(v)
                }
            }
        }
        return EventInfo(title, location, description, start, end)
    }

    private fun unescapeIcs(s: String): String =
        s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";")

    private fun parseIcsDate(v: String): Long? {
        val s = v.trim()
        for (p in listOf("yyyyMMdd'T'HHmmss'Z'", "yyyyMMdd'T'HHmmss", "yyyyMMdd")) {
            try {
                val f = SimpleDateFormat(p, Locale.US)
                f.isLenient = false
                if (p.endsWith("'Z'")) f.timeZone = TimeZone.getTimeZone("UTC")
                val d = f.parse(s)
                if (d != null) return d.time
            } catch (e: ParseException) {
                // try next pattern
            }
        }
        return null
    }

    // ------------------------------------------------------------------ presentation

    /** Short human friendly text used in history rows. */
    fun preview(content: String, type: ContentType): String {
        val p = when (type) {
            ContentType.WIFI -> parseWifi(content)?.ssid
            ContentType.CONTACT -> parseContact(content)?.let { it.name.ifEmpty { it.phone.ifEmpty { it.email } } }
            ContentType.EMAIL -> parseEmail(content)?.to
            ContentType.PHONE -> parsePhone(content)
            ContentType.SMS -> parseSms(content)?.let { if (it.body.isEmpty()) it.number else "${it.number}: ${it.body}" }
            ContentType.GEO -> parseGeo(content)?.let { "${it.lat}, ${it.lon}" }
            ContentType.EVENT -> parseEvent(content)?.title
            else -> content
        }
        return if (p.isNullOrBlank()) content else p
    }

    /** Label/value rows shown in the result sheet. */
    fun describe(content: String, type: ContentType): List<Detail> {
        val out = ArrayList<Detail>()
        fun add(label: Int, value: String?) {
            if (!value.isNullOrBlank()) out.add(Detail(label, value))
        }
        when (type) {
            ContentType.WIFI -> parseWifi(content)?.let {
                add(R.string.d_ssid, it.ssid)
                add(R.string.d_password, it.password)
                add(R.string.d_security, it.security)
            }
            ContentType.CONTACT -> parseContact(content)?.let {
                add(R.string.d_name, it.name)
                add(R.string.d_phone, it.phone)
                add(R.string.d_email, it.email)
                add(R.string.d_org, it.org)
                add(R.string.d_address, it.address)
            }
            ContentType.EMAIL -> parseEmail(content)?.let {
                add(R.string.d_to, it.to)
                add(R.string.d_subject, it.subject)
                add(R.string.d_message, it.body)
            }
            ContentType.PHONE -> add(R.string.d_phone, parsePhone(content))
            ContentType.SMS -> parseSms(content)?.let {
                add(R.string.d_phone, it.number)
                add(R.string.d_message, it.body)
            }
            ContentType.GEO -> parseGeo(content)?.let {
                add(R.string.d_latitude, it.lat.toString())
                add(R.string.d_longitude, it.lon.toString())
                add(R.string.d_title, it.query)
            }
            ContentType.EVENT -> parseEvent(content)?.let {
                add(R.string.d_title, it.title)
                add(R.string.d_location, it.location)
                add(R.string.d_description, it.description)
                it.start?.let { s -> add(R.string.d_start, formatTime(s)) }
                it.end?.let { e -> add(R.string.d_end, formatTime(e)) }
            }
            else -> Unit
        }
        return out
    }
}

/** Builds the text payloads that get encoded into QR codes. */
object ContentBuilder {

    fun wifi(ssid: String, password: String, security: String, hidden: Boolean): String {
        fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;")
            .replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")
        val open = security == "nopass"
        return buildString {
            append("WIFI:T:").append(security).append(";S:").append(esc(ssid)).append(";")
            if (!open) {
                append("P:").append(esc(password)).append(";")
            }
            if (hidden) {
                append("H:true;")
            }
            append(";")
        }
    }

    private fun escVCard(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n")
        .replace(",", "\\,").replace(";", "\\;")

    fun vcard(name: String, phone: String, email: String, org: String, address: String): String {
        val parts = name.trim().split(Regex("\\s+"))
        val first = parts.dropLast(1).joinToString(" ").ifEmpty { parts.firstOrNull().orEmpty() }
        val last = if (parts.size > 1) parts.last() else ""
        return buildString {
            append("BEGIN:VCARD\nVERSION:3.0\n")
            append("N:").append(escVCard(last)).append(";").append(escVCard(first)).append(";;;\n")
            append("FN:").append(escVCard(name.trim())).append("\n")
            if (org.isNotBlank()) append("ORG:").append(escVCard(org.trim())).append("\n")
            if (phone.isNotBlank()) append("TEL:").append(phone.trim()).append("\n")
            if (email.isNotBlank()) append("EMAIL:").append(email.trim()).append("\n")
            if (address.isNotBlank()) append("ADR:;;").append(escVCard(address.trim())).append(";;;;\n")
            append("END:VCARD")
        }
    }

    fun mailto(to: String, subject: String, body: String): String {
        val params = ArrayList<String>()
        if (subject.isNotBlank()) params.add("subject=" + Uri.encode(subject))
        if (body.isNotBlank()) params.add("body=" + Uri.encode(body))
        return "mailto:" + to.trim() + if (params.isEmpty()) "" else "?" + params.joinToString("&")
    }

    fun sms(number: String, message: String): String = "SMSTO:" + number.trim().replace(" ", "") + ":" + message

    fun whatsapp(number: String, message: String): String {
        val digits = number.filter { it.isDigit() }
        val text = if (message.isBlank()) "" else "?text=" + Uri.encode(message)
        return "https://wa.me/$digits$text"
    }

    private fun escIcs(s: String) = s.replace("\\", "\\\\").replace(";", "\\;")
        .replace(",", "\\,").replace("\n", "\\n")

    fun event(title: String, location: String, description: String, start: java.util.Date, end: java.util.Date): String {
        val f = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US)
        return buildString {
            append("BEGIN:VCALENDAR\nVERSION:2.0\nBEGIN:VEVENT\n")
            append("SUMMARY:").append(escIcs(title)).append("\n")
            append("DTSTART:").append(f.format(start)).append("\n")
            append("DTEND:").append(f.format(end)).append("\n")
            if (location.isNotBlank()) append("LOCATION:").append(escIcs(location)).append("\n")
            if (description.isNotBlank()) append("DESCRIPTION:").append(escIcs(description)).append("\n")
            append("END:VEVENT\nEND:VCALENDAR")
        }
    }
}
