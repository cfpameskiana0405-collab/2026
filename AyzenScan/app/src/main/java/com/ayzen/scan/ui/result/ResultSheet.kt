package com.ayzen.scan.ui.result

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayzen.scan.MainViewModel
import com.ayzen.scan.R
import com.ayzen.scan.data.ContentType
import com.ayzen.scan.data.HistoryItem
import com.ayzen.scan.data.Source
import com.ayzen.scan.logic.Actions
import com.ayzen.scan.logic.ContentParser
import com.ayzen.scan.logic.formatTime
import com.ayzen.scan.ui.components.Brand
import com.ayzen.scan.ui.components.GradientButton
import com.ayzen.scan.ui.components.SoftCard
import com.ayzen.scan.ui.components.TypeBadge
import com.ayzen.scan.ui.components.labelRes
import kotlinx.coroutines.launch

private class ActionSpec(
    val icon: ImageVector,
    @StringRes val label: Int,
    val onClick: () -> Unit
)

private fun buildActions(ctx: Context, item: HistoryItem): List<ActionSpec> {
    val c = item.content
    val list = ArrayList<ActionSpec>()
    when (item.type) {
        ContentType.URL ->
            list.add(ActionSpec(Icons.Rounded.Public, R.string.act_open_link) { Actions.openUrl(ctx, c) })

        ContentType.WIFI -> ContentParser.parseWifi(c)?.let { w ->
            list.add(ActionSpec(Icons.Rounded.Wifi, R.string.act_connect_wifi) { Actions.connectWifi(ctx, w) })
            if (w.password.isNotEmpty()) {
                list.add(ActionSpec(Icons.Rounded.Key, R.string.act_copy_password) { Actions.copy(ctx, w.password) })
            }
        }

        ContentType.EMAIL -> ContentParser.parseEmail(c)?.let { e ->
            list.add(ActionSpec(Icons.Rounded.Email, R.string.act_send_email) {
                Actions.sendEmail(ctx, e.to, e.subject, e.body)
            })
        }

        ContentType.PHONE -> {
            val number = ContentParser.parsePhone(c)
            list.add(ActionSpec(Icons.Rounded.Call, R.string.act_call) { Actions.dial(ctx, number) })
            list.add(ActionSpec(Icons.Rounded.Sms, R.string.act_send_sms) { Actions.sendSms(ctx, number, "") })
            list.add(ActionSpec(Icons.Rounded.PersonAdd, R.string.act_add_contact) {
                Actions.addContact(ctx, com.ayzen.scan.logic.ContactInfo("", number, "", "", ""))
            })
        }

        ContentType.SMS -> ContentParser.parseSms(c)?.let { s ->
            list.add(ActionSpec(Icons.Rounded.Sms, R.string.act_send_sms) { Actions.sendSms(ctx, s.number, s.body) })
            list.add(ActionSpec(Icons.Rounded.Call, R.string.act_call) { Actions.dial(ctx, s.number) })
        }

        ContentType.GEO -> ContentParser.parseGeo(c)?.let { g ->
            list.add(ActionSpec(Icons.Rounded.LocationOn, R.string.act_open_map) { Actions.openMap(ctx, g) })
        }

        ContentType.CONTACT -> ContentParser.parseContact(c)?.let { p ->
            list.add(ActionSpec(Icons.Rounded.PersonAdd, R.string.act_add_contact) { Actions.addContact(ctx, p) })
            if (p.phone.isNotEmpty()) {
                list.add(ActionSpec(Icons.Rounded.Call, R.string.act_call) { Actions.dial(ctx, p.phone) })
            }
            if (p.email.isNotEmpty()) {
                list.add(ActionSpec(Icons.Rounded.Email, R.string.act_send_email) {
                    Actions.sendEmail(ctx, p.email, "", "")
                })
            }
        }

        ContentType.EVENT -> ContentParser.parseEvent(c)?.let { e ->
            list.add(ActionSpec(Icons.Rounded.Event, R.string.act_add_event) { Actions.addEvent(ctx, e) })
        }

        ContentType.TEXT, ContentType.PRODUCT, ContentType.BOOK ->
            list.add(ActionSpec(Icons.Rounded.Search, R.string.act_search_web) { Actions.webSearch(ctx, c) })
    }
    return list
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ResultSheet(item: HistoryItem, vm: MainViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history by vm.history.collectAsStateWithLifecycle()
    val live = history.firstOrNull { it.id == item.id } ?: item
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun closeSheet() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    val actions = buildActions(context, live)
    val details = ContentParser.describe(live.content, live.type)
    val deletedText = stringResource(R.string.deleted_toast)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TypeBadge(live.type, size = 52.dp)
                Column {
                    Text(
                        stringResource(live.type.labelRes()),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    val src = if (live.source == Source.CREATED) R.string.src_created else R.string.src_scanned
                    Text(
                        "${live.format} · ${stringResource(src)} · ${formatTime(live.timestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Primary action
            actions.firstOrNull()?.let { primary ->
                GradientButton(
                    text = stringResource(primary.label),
                    icon = primary.icon,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = primary.onClick
                )
            }

            // Secondary actions
            if (actions.size > 1) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    actions.drop(1).forEach { a ->
                        OutlinedButton(onClick = a.onClick, shape = RoundedCornerShape(14.dp)) {
                            Icon(a.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(a.label))
                        }
                    }
                }
            }

            // Details
            if (details.isNotEmpty()) {
                SoftCard(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.res_details),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            details.forEach { d ->
                                Column {
                                    Text(
                                        stringResource(d.label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        d.value,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Raw content
            SoftCard(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.res_content),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Text(
                        live.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Bottom quick actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickAction(Icons.Rounded.ContentCopy, stringResource(R.string.act_copy), Modifier.weight(1f)) {
                    Actions.copy(context, live.content)
                }
                QuickAction(Icons.Rounded.IosShare, stringResource(R.string.act_share), Modifier.weight(1f)) {
                    Actions.shareText(context, live.content)
                }
                QuickAction(
                    icon = if (live.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    label = stringResource(if (live.favorite) R.string.act_unfavorite else R.string.act_favorite),
                    modifier = Modifier.weight(1f),
                    tint = if (live.favorite) Brand.Star else MaterialTheme.colorScheme.onSurface
                ) { vm.toggleFavorite(live.id) }
                QuickAction(
                    Icons.Rounded.Delete,
                    stringResource(R.string.act_delete),
                    Modifier.weight(1f),
                    tint = Brand.Danger
                ) {
                    vm.delete(live.id)
                    Toast.makeText(context, deletedText, Toast.LENGTH_SHORT).show()
                    closeSheet()
                }
            }
            Spacer(Modifier.height(16.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
