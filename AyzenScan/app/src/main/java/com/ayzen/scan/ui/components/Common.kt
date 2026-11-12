package com.ayzen.scan.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayzen.scan.R
import com.ayzen.scan.data.ContentType

object Brand {
    val Violet = Color(0xFF8B6BFF)
    val Cyan = Color(0xFF22D3EE)
    val Pink = Color(0xFFFF5C9D)
    val Danger = Color(0xFFFF5470)
    val Star = Color(0xFFFACC15)
    val gradient: Brush = Brush.linearGradient(listOf(Violet, Cyan))
}

fun ContentType.color(): Color = when (this) {
    ContentType.URL -> Color(0xFF22D3EE)
    ContentType.TEXT -> Color(0xFF8B6BFF)
    ContentType.WIFI -> Color(0xFF34D399)
    ContentType.EMAIL -> Color(0xFFFB923C)
    ContentType.PHONE -> Color(0xFF60A5FA)
    ContentType.SMS -> Color(0xFF2DD4BF)
    ContentType.GEO -> Color(0xFFF87171)
    ContentType.CONTACT -> Color(0xFFF472B6)
    ContentType.EVENT -> Color(0xFFFACC15)
    ContentType.PRODUCT -> Color(0xFFA3E635)
    ContentType.BOOK -> Color(0xFFC084FC)
}

fun ContentType.icon(): ImageVector = when (this) {
    ContentType.URL -> Icons.Rounded.Link
    ContentType.TEXT -> Icons.Rounded.TextFields
    ContentType.WIFI -> Icons.Rounded.Wifi
    ContentType.EMAIL -> Icons.Rounded.Email
    ContentType.PHONE -> Icons.Rounded.Call
    ContentType.SMS -> Icons.Rounded.Sms
    ContentType.GEO -> Icons.Rounded.LocationOn
    ContentType.CONTACT -> Icons.Rounded.Person
    ContentType.EVENT -> Icons.Rounded.Event
    ContentType.PRODUCT -> Icons.Rounded.ShoppingCart
    ContentType.BOOK -> Icons.Rounded.AutoStories
}

@StringRes
fun ContentType.labelRes(): Int = when (this) {
    ContentType.URL -> R.string.type_url
    ContentType.TEXT -> R.string.type_text
    ContentType.WIFI -> R.string.type_wifi
    ContentType.EMAIL -> R.string.type_email
    ContentType.PHONE -> R.string.type_phone
    ContentType.SMS -> R.string.type_sms
    ContentType.GEO -> R.string.type_geo
    ContentType.CONTACT -> R.string.type_contact
    ContentType.EVENT -> R.string.type_event
    ContentType.PRODUCT -> R.string.type_product
    ContentType.BOOK -> R.string.type_book
}

/** Text filled with the brand gradient. */
@Composable
fun GradientText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = style,
        color = Color.White,
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val brush = Brush.horizontalGradient(listOf(Brand.Violet, Brand.Cyan))
                onDrawWithContent {
                    drawContent()
                    drawRect(brush, blendMode = BlendMode.SrcIn)
                }
            }
    )
}

@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(18.dp))
            .background(Brand.gradient)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = Color.White)
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun TypeBadge(type: ContentType, size: Dp = 44.dp) {
    val c = type.color()
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(c.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(type.icon(), contentDescription = null, tint = c, modifier = Modifier.size(size * 0.54f))
    }
}

@Composable
fun SoftCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

/** Dark gradient background with soft colored glows. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val bg = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .drawBehind {
                val r = size.width * 0.95f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Brand.Violet.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = r
                    ),
                    radius = r,
                    center = Offset(0f, 0f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Brand.Cyan.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width, size.height),
                        radius = r
                    ),
                    radius = r,
                    center = Offset(size.width, size.height)
                )
            },
        content = content
    )
}
