package fr.channelfinder.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Secondary text that stays readable on both the resting and the light focused surface. */
@Composable
fun secondaryColor(): Color = LocalContentColor.current.copy(alpha = 0.74f)

/**
 * The single focus treatment used by rows, buttons and chips: the surface turns light with dark content
 * and grows slightly, so the focused element is obvious from the sofa.
 */
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(14.dp),
    container: Color = Palette.Surface1,
    content: Color = Palette.OnSurface,
    focusedScale: Float = 1.05f,
    outline: Color? = null,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    body: @Composable BoxScope.() -> Unit
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = content,
            focusedContainerColor = Palette.FocusFill,
            focusedContentColor = Palette.OnFocus,
            pressedContainerColor = Palette.Muted,
            pressedContentColor = Palette.OnFocus,
            disabledContainerColor = container.copy(alpha = 0.5f),
            disabledContentColor = content.copy(alpha = 0.5f)
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (outline != null) Border(BorderStroke(1.5.dp, outline), shape = shape) else Border.None
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale, pressedScale = 1f),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Palette.Accent.copy(alpha = 0.35f), 12.dp)),
        interactionSource = interactionSource,
        content = body
    )
}

/**
 * Artwork tiles keep their own background when focused and gain a white outline instead,
 * following the TV focus system for cards.
 */
@Composable
fun CardSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    container: Color = Palette.Surface1,
    interactionSource: MutableInteractionSource? = null,
    body: @Composable BoxScope.() -> Unit
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = Palette.OnSurface,
            focusedContainerColor = container,
            focusedContentColor = Palette.OnSurface,
            pressedContainerColor = container,
            pressedContentColor = Palette.OnSurface
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Palette.Outline), shape = shape),
            focusedBorder = Border(BorderStroke(3.dp, Palette.FocusFill), inset = 3.dp, shape = shape)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f, pressedScale = 1.02f),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Palette.Accent.copy(alpha = 0.3f), 18.dp)),
        interactionSource = interactionSource,
        content = body
    )
}

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    primary: Boolean = false,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    FocusSurface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.heightIn(min = 42.dp),
        shape = CircleShape,
        container = if (primary) Palette.AccentDeep else Palette.Surface2,
        outline = if (primary) Palette.Accent.copy(alpha = 0.6f) else Palette.Outline,
        enabled = enabled
    ) {
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = 20.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = if (focused || iconTint == null) LocalContentColor.current else iconTint)
            Text(text, style = Type.Label, maxLines = 1)
        }
    }
}

@Composable
fun TvChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.height(if (compact) 32.dp else 38.dp),
        shape = CircleShape,
        container = if (selected) Palette.AccentDeep else Color.Transparent,
        content = if (selected) Palette.OnSurface else Palette.Muted,
        outline = if (selected) Palette.Accent else Palette.Outline,
        focusedScale = 1.06f
    ) {
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = if (compact) 12.dp else 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(if (compact) 14.dp else 16.dp))
            Text(text, style = if (compact) Type.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) else Type.Label)
        }
    }
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier, tone: Color = LocalContentColor.current) {
    Box(
        modifier
            .border(1.dp, tone.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, style = Type.Badge, color = tone)
    }
}

/** Provider logo on a neutral plate, falling back to initials while loading or when the image fails. */
@Composable
fun ChannelLogo(channel: Channel, modifier: Modifier = Modifier, corner: Dp = 10.dp, initialsSize: Int = 15) {
    var loaded by remember(channel.logoUrl) { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(
        modifier.clip(RoundedCornerShape(corner)).background(Palette.LogoPlate),
        contentAlignment = Alignment.Center
    ) {
        if (!loaded) {
            Text(
                channel.initials,
                color = Palette.OnSurface,
                fontSize = initialsSize.sp,
                style = Type.Label,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
        if (channel.logoUrl.startsWith("http")) {
            AsyncImage(
                model = remember(channel.logoUrl) {
                    ImageRequest.Builder(context).data(channel.logoUrl).size(256).crossfade(true).build()
                },
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                onError = { loaded = false },
                modifier = Modifier.matchParentSize().padding(6.dp)
            )
        }
    }
}

/**
 * TV text field: focusing it does not open the keyboard. OK starts editing; Up/Down, Back or the
 * keyboard action stop editing and hand the D-pad back to the screen.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    password: Boolean = false,
    onImeAction: () -> Unit = {},
    outerFocusRequester: FocusRequester = remember { FocusRequester() }
) {
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var fieldFocused by remember { mutableStateOf(false) }

    fun stopEditing() {
        keyboard?.hide()
        onEditingChange(false)
        runCatching { outerFocusRequester.requestFocus() }
    }

    LaunchedEffect(editing) {
        if (editing) {
            delay(30)
            runCatching { fieldFocus.requestFocus() }
            keyboard?.show()
        }
    }

    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = { onEditingChange(true) },
        modifier = modifier.focusRequester(outerFocusRequester).heightIn(min = 48.dp),
        interactionSource = interaction,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.Surface1,
            contentColor = Palette.OnSurface,
            focusedContainerColor = Palette.FocusFill,
            focusedContentColor = Palette.OnFocus,
            pressedContainerColor = Palette.FocusFill,
            pressedContentColor = Palette.OnFocus
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(if (editing) 2.dp else 1.dp, if (editing) Palette.Accent else Palette.Outline), shape = shape)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = if (editing) Palette.Accent else LocalContentColor.current)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = Type.Body, color = secondaryColor(), maxLines = 1)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = Type.Body.copy(color = if (focused) Palette.OnFocus else Palette.OnSurface),
                    cursorBrush = SolidColor(Palette.Accent),
                    visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onAny = { stopEditing(); onImeAction() }),
                    modifier = Modifier.fillMaxWidth()
                        .focusRequester(fieldFocus)
                        .focusProperties { canFocus = editing }
                        .onFocusChanged {
                            if (fieldFocused && !it.isFocused && editing) onEditingChange(false)
                            fieldFocused = it.isFocused
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionDown, Key.DirectionUp -> {
                                    stopEditing()
                                    focusManager.moveFocus(if (event.key == Key.DirectionDown) FocusDirection.Down else FocusDirection.Up)
                                    true
                                }
                                Key.Back, Key.Escape -> { stopEditing(); true }
                                else -> false
                            }
                        }
                )
            }
        }
    }
}

@Composable
fun Spinner(modifier: Modifier = Modifier.size(40.dp), color: Color = Palette.OnSurface, stroke: Dp = 4.dp) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "angle"
    )
    Canvas(modifier) {
        val width = stroke.toPx()
        drawArc(color.copy(alpha = 0.18f), 0f, 360f, false, style = Stroke(width), topLeft = Offset(width / 2, width / 2), size = Size(size.width - width, size.height - width))
        drawArc(color, angle, 100f, false, style = Stroke(width, cap = StrokeCap.Round), topLeft = Offset(width / 2, width / 2), size = Size(size.width - width, size.height - width))
    }
}

@Composable
fun Clock(modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = Type.Title) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    Text(SimpleDateFormat("HH:mm", Locale.FRANCE).format(now), style = style, color = Palette.OnSurface, modifier = modifier)
}

/** Key cap + label pairs, e.g. ("OK", "Regarder"). */
@Composable
fun HintBar(vararg hints: Pair<String, String>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) {
        hints.forEach { (key, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.border(1.dp, Palette.Faint, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 2.dp)
                ) { Text(key, style = Type.Badge, color = Palette.Muted) }
                Text(label, style = Type.Caption, color = Palette.Muted)
            }
        }
    }
}

@Composable
fun ScreenHeader(eyebrow: String, title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.Gutter)) {
        Column(Modifier.weight(1f)) {
            Text(eyebrow.uppercase(), style = Type.Eyebrow, color = Palette.Accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(title, style = Type.Display, color = Palette.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(modifier.widthIn(max = 520.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).background(Palette.Surface2, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp), tint = Palette.Muted)
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = Type.Headline, color = Palette.OnSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, style = Type.Body, color = Palette.Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        action()
    }
}

/** Short confirmation shown at the bottom of the screen, e.g. after a long press adds a favorite. */
@Composable
fun BoxScope.NoticeHost(notice: Notice?) {
    AnimatedVisibility(
        visible = notice != null,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp)
    ) {
        val current = remember { mutableStateOf(notice) }
        if (notice != null) current.value = notice
        current.value?.let { shown ->
            Row(
                Modifier.background(Palette.FocusFill, CircleShape).padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (shown.icon != null) Icon(shown.icon, contentDescription = null, tint = shown.tint ?: Palette.OnFocus, modifier = Modifier.size(22.dp))
                Text(shown.text, style = Type.Label, color = Palette.OnFocus)
            }
        }
    }
}

data class Notice(val text: String, val icon: ImageVector? = null, val tint: Color? = null, val id: Long = System.nanoTime())


/** Status of an event feed: live now, start time, generic match feed, or nothing scheduled. */
@Composable
fun EventTag(channel: Channel, modifier: Modifier = Modifier) {
    val info = channel.event ?: return
    val status = info.status()
    val label = ChannelText.eventTimeLabel(info).uppercase()
    when (status) {
        ChannelText.EventStatus.LIVE -> Row(
            modifier.background(Palette.Live, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(Modifier.size(6.dp).background(androidx.compose.ui.graphics.Color.White, CircleShape))
            Text(label, style = Type.Badge, color = androidx.compose.ui.graphics.Color.White)
        }
        ChannelText.EventStatus.UPCOMING -> Badge(label, modifier, tone = Palette.Accent)
        else -> Badge(label, modifier, tone = secondaryColor())
    }
}

/** Shown on channels whose stream failed in the last hours. */
@Composable
fun UnavailableTag(modifier: Modifier = Modifier) {
    Badge("INDISPONIBLE", modifier, tone = Palette.Error)
}
