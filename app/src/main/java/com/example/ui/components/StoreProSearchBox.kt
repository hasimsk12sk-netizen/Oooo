package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.StoreProCardBorder
import com.example.ui.theme.StoreProPrimaryGreen
import com.example.ui.theme.StoreProSecondaryCyan

private val SearchBoxShape = RoundedCornerShape(16.dp)
private val SearchBoxBackground = Color(0xFF131B2E)
private val SearchHintColor = Color(0xFF64748B)
private val SearchPlaceholderStyle = TextStyle(
    fontSize = 14.sp,
    lineHeight = 20.sp,
    color = SearchHintColor
)
private val SearchInputTextStyle = TextStyle(
    fontSize = 14.sp,
    lineHeight = 20.sp,
    color = Color.White
)
private val SearchUnfocusedBrush = Brush.horizontalGradient(listOf(StoreProCardBorder, Color(0xFF1E293B)))

/**
 * Unified STORE PRO Search Box shared by HomeScreen (display/launcher mode) and SearchScreen
 * (live editable mode) so height, width, corner radius, borders, spacing, icon size, font size,
 * single-line placeholder alignment, and animated RGB focus glow match identically.
 */
@Composable
fun StoreProSearchBar(
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    readOnlyLauncher: Boolean = false,
    isFocused: Boolean = false,
    onFocusChanged: (Boolean) -> Unit = {},
    onLauncherClick: () -> Unit = {},
    onSearchSubmit: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    placeholderText: String = "Search apps, developers, keywords...",
    glowTestTag: String? = null,
    fieldTestTag: String? = null,
    glowSeed: String = "search_input_focus",
    modifier: Modifier = Modifier
) {
    val shape = SearchBoxShape
    val unfocusedBrush = SearchUnfocusedBrush

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (glowTestTag != null) Modifier.testTag(glowTestTag) else Modifier)
            .then(
                if (isFocused) {
                    Modifier.rgbNeonGlowBorder(
                        seed = glowSeed,
                        cornerRadius = 16.dp,
                        borderWidth = 2.dp,
                        glowIntensity = 1.2f,
                        animate = true,
                        paletteColors = RgbNeonPalette.searchFocusColors,
                        durationMillis = 2600
                    )
                } else {
                    Modifier.border(BorderStroke(1.dp, unfocusedBrush), shape)
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(SearchBoxBackground, shape)
                .defaultMinSize(minHeight = 48.dp)
                .then(
                    if (readOnlyLauncher) {
                        Modifier
                            .onFocusChanged { onFocusChanged(it.isFocused) }
                            .focusable()
                            .clickable { onLauncherClick() }
                            .then(if (fieldTestTag != null) Modifier.testTag(fieldTestTag) else Modifier)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = if (!readOnlyLauncher && query.isNotEmpty()) 4.dp else 16.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = StoreProSecondaryCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))

                if (readOnlyLauncher) {
                    Text(
                        text = placeholderText,
                        style = SearchPlaceholderStyle,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 14.dp)
                    )
                } else {
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        maxLines = 1,
                        textStyle = SearchInputTextStyle,
                        cursorBrush = SolidColor(StoreProPrimaryGreen),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 14.dp)
                            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                            .onFocusChanged { onFocusChanged(it.isFocused) }
                            .then(if (fieldTestTag != null) Modifier.testTag(fieldTestTag) else Modifier),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (query.isEmpty()) {
                                    Text(
                                        text = placeholderText,
                                        style = SearchPlaceholderStyle,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("clear_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
