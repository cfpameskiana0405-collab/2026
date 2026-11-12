package com.ayzen.scan.ui.create

import android.graphics.Bitmap
import android.util.Patterns
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayzen.scan.MainViewModel
import com.ayzen.scan.R
import com.ayzen.scan.data.ContentType
import com.ayzen.scan.data.Source
import com.ayzen.scan.logic.Actions
import com.ayzen.scan.logic.ContentBuilder
import com.ayzen.scan.logic.ContentParser
import com.ayzen.scan.logic.GenFormat
import com.ayzen.scan.logic.QrGenerator
import com.ayzen.scan.logic.barcodeFormatName
import com.ayzen.scan.ui.components.GradientButton
import com.ayzen.scan.ui.components.GradientText
import com.ayzen.scan.ui.components.color
import com.google.mlkit.vision.barcode.common.Barcode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------- model

private enum class Kind(
    @StringRes val label: Int,
    val icon: ImageVector,
    val color: Color
) {
    TEXT(R.string.type_text, Icons.Rounded.TextFields, ContentType.TEXT.color()),
    URL(R.string.type_url, Icons.Rounded.Link, ContentType.URL.color()),
    WIFI(R.string.type_wifi, Icons.Rounded.Wifi, ContentType.WIFI.color()),
    WHATSAPP(R.string.type_whatsapp, Icons.Rounded.Chat, Color(0xFF25D366)),
    EMAIL(R.string.type_email, Icons.Rounded.Email, ContentType.EMAIL.color()),
    PHONE(R.string.type_phone, Icons.Rounded.Call, ContentType.PHONE.color()),
    SMS(R.string.type_sms, Icons.Rounded.Sms, ContentType.SMS.color()),
    GEO(R.string.type_geo, Icons.Rounded.LocationOn, ContentType.GEO.color()),
    CONTACT(R.string.type_contact, Icons.Rounded.Person, ContentType.CONTACT.color()),
    EVENT(R.string.type_event, Icons.Rounded.Event, ContentType.EVENT.color())
}

private data class FieldSpec(
    val key: String,
    @StringRes val label: Int,
    val keyboard: KeyboardType = KeyboardType.Text,
    val required: Boolean = false,
    val multiline: Boolean = false
)

private fun fieldsFor(kind: Kind): List<FieldSpec> = when (kind) {
    Kind.TEXT -> listOf(FieldSpec("text", R.string.f_text, required = true, multiline = true))
    Kind.URL -> listOf(FieldSpec("url", R.string.f_url, KeyboardType.Uri, required = true))
    Kind.WIFI -> listOf(FieldSpec("ssid", R.string.f_ssid, required = true))
    Kind.WHATSAPP -> listOf(
        FieldSpec("phone", R.string.f_phone_wa, KeyboardType.Phone, required = true),
        FieldSpec("message", R.string.f_message, multiline = true)
    )
    Kind.EMAIL -> listOf(
        FieldSpec("email", R.string.f_email, KeyboardType.Email, required = true),
        FieldSpec("subject", R.string.f_subject),
        FieldSpec("message", R.string.f_message, multiline = true)
    )
    Kind.PHONE -> listOf(FieldSpec("phone", R.string.f_phone, KeyboardType.Phone, required = true))
    Kind.SMS -> listOf(
        FieldSpec("phone", R.string.f_phone, KeyboardType.Phone, required = true),
        FieldSpec("message", R.string.f_message, multiline = true)
    )
    Kind.GEO -> listOf(
        FieldSpec("lat", R.string.f_lat, required = true),
        FieldSpec("lon", R.string.f_lon, required = true)
    )
    Kind.CONTACT -> listOf(
        FieldSpec("name", R.string.f_name, required = true),
        FieldSpec("phone", R.string.f_phone, KeyboardType.Phone),
        FieldSpec("email", R.string.f_email, KeyboardType.Email),
        FieldSpec("org", R.string.f_org),
        FieldSpec("address", R.string.f_address)
    )
    Kind.EVENT -> listOf(
        FieldSpec("title", R.string.f_title, required = true),
        FieldSpec("location", R.string.f_location),
        FieldSpec("description", R.string.f_description, multiline = true),
        FieldSpec("start", R.string.f_start, required = true),
        FieldSpec("end", R.string.f_end)
    )
}

private sealed class Built {
    class Ok(val content: String) : Built()
    object Missing : Built()
    object Invalid : Built()
}

private class Generated(val bitmap: Bitmap, val content: String)

private val palette = listOf(
    Color(0xFF000000), Color(0xFF5B3FE0), Color(0xFF0891B2),
    Color(0xFFDB2777), Color(0xFF059669), Color(0xFFEA580C)
)

private fun parseDate(s: String): Date? = runCatching {
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }.parse(s.trim())
}.getOrNull()

private fun build(
    kind: Kind,
    v: (String) -> String,
    security: String,
    hidden: Boolean
): Built {
    fun missing(vararg keys: String) = keys.any { v(it).isBlank() }
    val digits = { s: String -> s.count { it.isDigit() } }

    return when (kind) {
        Kind.TEXT -> if (missing("text")) Built.Missing else Built.Ok(v("text"))

        Kind.URL -> if (missing("url")) Built.Missing
        else Built.Ok(ContentParser.normalizeUrl(v("url").trim()))

        Kind.WIFI -> when {
            missing("ssid") -> Built.Missing
            security != "nopass" && missing("password") -> Built.Missing
            else -> Built.Ok(ContentBuilder.wifi(v("ssid").trim(), v("password"), security, hidden))
        }

        Kind.WHATSAPP -> when {
            missing("phone") -> Built.Missing
            digits(v("phone")) < 6 -> Built.Invalid
            else -> Built.Ok(ContentBuilder.whatsapp(v("phone"), v("message")))
        }

        Kind.EMAIL -> when {
            missing("email") -> Built.Missing
            !Patterns.EMAIL_ADDRESS.matcher(v("email").trim()).matches() -> Built.Invalid
            else -> Built.Ok(ContentBuilder.mailto(v("email"), v("subject"), v("message")))
        }

        Kind.PHONE -> when {
            missing("phone") -> Built.Missing
            digits(v("phone")) < 3 -> Built.Invalid
            else -> Built.Ok("tel:" + v("phone").trim().replace(" ", ""))
        }

        Kind.SMS -> when {
            missing("phone") -> Built.Missing
            digits(v("phone")) < 3 -> Built.Invalid
            else -> Built.Ok(ContentBuilder.sms(v("phone"), v("message")))
        }

        Kind.GEO -> {
            if (missing("lat", "lon")) return Built.Missing
            val lat = v("lat").trim().replace(',', '.').toDoubleOrNull()
            val lon = v("lon").trim().replace(',', '.').toDoubleOrNull()
            if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                Built.Invalid
            } else {
                Built.Ok("geo:$lat,$lon")
            }
        }

        Kind.CONTACT -> when {
            missing("name") -> Built.Missing
            v("email").isNotBlank() && !Patterns.EMAIL_ADDRESS.matcher(v("email").trim()).matches() ->
                Built.Invalid
            else -> Built.Ok(
                ContentBuilder.vcard(v("name"), v("phone"), v("email"), v("org"), v("address"))
            )
        }

        Kind.EVENT -> {
            if (missing("title", "start")) return Built.Missing
            val start = parseDate(v("start")) ?: return Built.Invalid
            val end = if (v("end").isBlank()) Date(start.time + 60 * 60 * 1000L)
            else parseDate(v("end")) ?: return Built.Invalid
            if (end.before(start)) Built.Invalid
            else Built.Ok(
                ContentBuilder.event(v("title").trim(), v("location"), v("description"), start, end)
            )
        }
    }
}

// ---------------------------------------------------------------------------- screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(vm: MainViewModel) {
    val context = LocalContext.current

    var kindIndex by remember { mutableIntStateOf(0) }
    val kind = Kind.entries[kindIndex]
    val values = remember { mutableStateMapOf<String, String>() }
    var security by remember { mutableStateOf("WPA") }
    var hidden by remember { mutableStateOf(false) }
    var genFormat by remember { mutableStateOf(GenFormat.QR) }
    var colorIndex by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<Generated?>(null) }

    fun value(key: String) = values["${kind.name}.$key"] ?: ""

    val requiredMsg = stringResource(R.string.create_required)
    val invalidMsg = stringResource(R.string.create_invalid)
    val failedMsg = stringResource(R.string.create_failed)

    fun generate() {
        when (val built = build(kind, ::value, security, hidden)) {
            Built.Missing -> Toast.makeText(context, requiredMsg, Toast.LENGTH_SHORT).show()
            Built.Invalid -> Toast.makeText(context, invalidMsg, Toast.LENGTH_SHORT).show()
            is Built.Ok -> {
                val format = if (kind == Kind.TEXT) genFormat else GenFormat.QR
                val bitmap = QrGenerator.generate(
                    content = built.content,
                    format = format,
                    fg = palette[colorIndex].toArgb(),
                    bg = Color.White.toArgb()
                )
                if (bitmap == null) {
                    Toast.makeText(context, failedMsg, Toast.LENGTH_LONG).show()
                } else {
                    val mlFormat = if (format == GenFormat.QR) Barcode.FORMAT_QR_CODE else Barcode.FORMAT_CODE_128
                    vm.record(
                        content = built.content,
                        type = ContentParser.detect(built.content, mlFormat),
                        format = barcodeFormatName(mlFormat),
                        source = Source.CREATED
                    )
                    result = Generated(bitmap, built.content)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            GradientText(stringResource(R.string.create_title), MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.create_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Type picker
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Kind.entries.forEachIndexed { index, k ->
                KindChip(k, selected = index == kindIndex) { kindIndex = index }
            }
        }

        // Fields
        fieldsFor(kind).forEach { f ->
            Field(
                spec = f,
                value = value(f.key),
                onChange = { values["${kind.name}.${f.key}"] = it }
            )
        }

        if (kind == Kind.WIFI) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "WPA" to R.string.sec_wpa,
                    "WEP" to R.string.sec_wep,
                    "nopass" to R.string.sec_none
                ).forEach { (code, label) ->
                    FilterChip(
                        selected = security == code,
                        onClick = { security = code },
                        label = { Text(stringResource(label), fontSize = 12.sp, maxLines = 1) },
                        colors = chipColors()
                    )
                }
            }
            if (security != "nopass") {
                Field(
                    spec = FieldSpec("password", R.string.f_password, required = true),
                    value = value("password"),
                    onChange = { values["${kind.name}.password"] = it }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.f_hidden),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = hidden,
                    onCheckedChange = { hidden = it },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedThumbColor = Color.White
                    )
                )
            }
        }

        // Format: plain text can also become a barcode
        if (kind == Kind.TEXT) {
            SectionLabel(R.string.create_format)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = genFormat == GenFormat.QR,
                    onClick = { genFormat = GenFormat.QR },
                    label = { Text(stringResource(R.string.fmt_qr)) },
                    colors = chipColors()
                )
                FilterChip(
                    selected = genFormat == GenFormat.CODE128,
                    onClick = { genFormat = GenFormat.CODE128 },
                    label = { Text(stringResource(R.string.fmt_barcode)) },
                    colors = chipColors()
                )
            }
        }

        // Color
        SectionLabel(R.string.create_color)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            palette.forEachIndexed { index, c ->
                val selected = index == colorIndex
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                        .clickable { colorIndex = index },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        GradientButton(
            text = stringResource(R.string.create_generate),
            icon = Icons.Rounded.QrCode2,
            modifier = Modifier.fillMaxWidth(),
            onClick = ::generate
        )
        Spacer(Modifier.height(8.dp))
    }

    result?.let { g ->
        GeneratedSheet(g, onDismiss = { result = null })
    }
}

// ---------------------------------------------------------------------------- pieces

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
    selectedLabelColor = MaterialTheme.colorScheme.onBackground
)

@Composable
private fun SectionLabel(@StringRes res: Int) {
    Text(
        stringResource(res),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun KindChip(kind: Kind, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) kind.color.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface)
            .border(
                BorderStroke(
                    1.dp,
                    if (selected) kind.color else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                ),
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(kind.icon, contentDescription = null, tint = kind.color, modifier = Modifier.size(18.dp))
        Text(
            stringResource(kind.label),
            color = if (selected) MaterialTheme.colorScheme.onBackground
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun Field(spec: FieldSpec, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(spec.label) + if (spec.required) " *" else "") },
        singleLine = !spec.multiline,
        minLines = if (spec.multiline) 3 else 1,
        keyboardOptions = KeyboardOptions(keyboardType = spec.keyboard),
        shape = RoundedCornerShape(16.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneratedSheet(g: Generated, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val savedMsg = stringResource(R.string.create_saved)
    val saveFailedMsg = stringResource(R.string.create_save_failed)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                bitmap = g.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .fillMaxWidth()
                    .aspectRatio(g.bitmap.width.toFloat() / g.bitmap.height.toFloat())
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
            )
            Text(
                g.content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GradientButton(
                    text = stringResource(R.string.create_save),
                    icon = Icons.Rounded.Download,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val ok = Actions.saveToGallery(context, g.bitmap)
                        Toast.makeText(context, if (ok) savedMsg else saveFailedMsg, Toast.LENGTH_SHORT).show()
                    }
                )
                OutlinedButton(
                    onClick = { Actions.shareBitmap(context, g.bitmap) },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Icon(Icons.Rounded.IosShare, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.create_share), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
