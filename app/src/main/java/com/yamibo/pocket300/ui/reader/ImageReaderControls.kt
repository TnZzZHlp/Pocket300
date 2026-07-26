package com.yamibo.pocket300.ui.reader

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.WebStories
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.yamibo.pocket300.R

@Composable
internal fun ImageReaderBottomBar(
    currentPage: Int,
    pageCount: Int,
    preferences: ImageReaderPreferences,
    onCurrentPageChange: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onScaleTypeChange: (ImageReaderScaleType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val page = currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    val isRightToLeft = preferences.mode == ImageReaderMode.RIGHT_TO_LEFT
    val isVertical = preferences.mode == ImageReaderMode.VERTICAL ||
        preferences.mode == ImageReaderMode.WEBTOON
    val previousPage = {
        onCurrentPageChange(
            imageReaderPageAfterAction(page, pageCount, ImageReaderTapAction.PREVIOUS),
        )
    }
    val nextPage = {
        onCurrentPageChange(
            imageReaderPageAfterAction(page, pageCount, ImageReaderTapAction.NEXT),
        )
    }
    val leftAction = if (isRightToLeft) nextPage else previousPage
    val rightAction = if (isRightToLeft) previousPage else nextPage
    val leftEnabled = if (isRightToLeft) page < pageCount - 1 else page > 0
    val rightEnabled = if (isRightToLeft) page > 0 else page < pageCount - 1

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = leftAction, enabled = leftEnabled) {
                Icon(
                    imageVector = if (isVertical) {
                        Icons.Rounded.KeyboardArrowUp
                    } else {
                        Icons.Rounded.ChevronLeft
                    },
                    contentDescription = stringResource(
                        if (isRightToLeft) R.string.reader_next_image else R.string.reader_previous_image,
                    ),
                )
            }
            Text(
                text = (page + 1).toString(),
                style = MaterialTheme.typography.labelMedium,
            )
            CompositionLocalProvider(
                LocalLayoutDirection provides if (isRightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                Slider(
                    value = page.toFloat(),
                    onValueChange = {
                        onCurrentPageChange(imageReaderPageFromSlider(it, pageCount))
                    },
                    valueRange = 0f..(pageCount - 1).coerceAtLeast(0).toFloat(),
                    steps = (pageCount - 2).coerceAtLeast(0),
                    enabled = pageCount > 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                )
            }
            Text(
                text = pageCount.toString(),
                style = MaterialTheme.typography.labelMedium,
            )
            IconButton(onClick = rightAction, enabled = rightEnabled) {
                Icon(
                    imageVector = if (isVertical) {
                        Icons.Rounded.KeyboardArrowDown
                    } else {
                        Icons.Rounded.ChevronRight
                    },
                    contentDescription = stringResource(
                        if (isRightToLeft) R.string.reader_previous_image else R.string.reader_next_image,
                    ),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onOpenSettings) {
                Icon(preferences.mode.icon, contentDescription = null)
                Text(
                    text = stringResource(preferences.mode.labelResource),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            TextButton(
                onClick = {
                    onScaleTypeChange(
                        when (preferences.scaleType) {
                            ImageReaderScaleType.FIT_SCREEN -> ImageReaderScaleType.FIT_WIDTH
                            ImageReaderScaleType.FIT_WIDTH -> ImageReaderScaleType.FIT_SCREEN
                        },
                    )
                },
                enabled = preferences.mode != ImageReaderMode.WEBTOON,
            ) {
                Icon(Icons.Rounded.FitScreen, contentDescription = null)
                Text(
                    text = stringResource(preferences.scaleType.labelResource),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.reader_image_settings),
                )
            }
        }
    }
}

@Composable
internal fun ImageReaderSettingsSheet(
    preferences: ImageReaderPreferences,
    onChange: (ImageReaderPreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.reader_image_settings),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(20.dp))

        SettingsSectionTitle(R.string.reader_image_reading_mode)
        ImageReaderMode.entries.forEach { mode ->
            SettingsRadioOption(
                label = stringResource(mode.labelResource),
                selected = preferences.mode == mode,
                onClick = { onChange(preferences.copy(mode = mode)) },
            )
        }

        Spacer(Modifier.height(16.dp))
        SettingsSectionTitle(R.string.reader_image_scale_type)
        ImageReaderScaleType.entries.forEach { scaleType ->
            SettingsRadioOption(
                label = stringResource(scaleType.labelResource),
                selected = preferences.scaleType == scaleType,
                enabled = preferences.mode != ImageReaderMode.WEBTOON,
                onClick = { onChange(preferences.copy(scaleType = scaleType)) },
            )
        }

        Spacer(Modifier.height(16.dp))
        SettingsSectionTitle(R.string.reader_image_background)
        ImageReaderBackground.entries.forEach { background ->
            SettingsRadioOption(
                label = stringResource(background.labelResource),
                selected = preferences.background == background,
                onClick = { onChange(preferences.copy(background = background)) },
            )
        }

        Spacer(Modifier.height(16.dp))
        SettingsSwitchOption(
            label = stringResource(R.string.reader_image_tap_navigation),
            supportingText = stringResource(R.string.reader_image_tap_navigation_summary),
            checked = preferences.tapNavigation,
            onCheckedChange = { onChange(preferences.copy(tapNavigation = it)) },
        )
        SettingsSwitchOption(
            label = stringResource(R.string.reader_image_show_page_number),
            supportingText = stringResource(R.string.reader_image_show_page_number_summary),
            checked = preferences.showPageNumber,
            onCheckedChange = { onChange(preferences.copy(showPageNumber = it)) },
        )

        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = { onChange(ImageReaderPreferences()) },
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.reader_reset))
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SettingsSectionTitle(@StringRes label: Int) {
    Text(
        text = stringResource(label),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun SettingsRadioOption(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Text(
            text = label,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun SettingsSwitchOption(
    label: String,
    supportingText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(
                text = supportingText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private val ImageReaderMode.icon: ImageVector
    get() = when (this) {
        ImageReaderMode.LEFT_TO_RIGHT,
        ImageReaderMode.RIGHT_TO_LEFT,
        -> Icons.Rounded.SwapHoriz
        ImageReaderMode.VERTICAL -> Icons.Rounded.ViewDay
        ImageReaderMode.WEBTOON -> Icons.Rounded.WebStories
    }

private val ImageReaderMode.labelResource: Int
    @StringRes get() = when (this) {
        ImageReaderMode.LEFT_TO_RIGHT -> R.string.reader_image_mode_left_to_right
        ImageReaderMode.RIGHT_TO_LEFT -> R.string.reader_image_mode_right_to_left
        ImageReaderMode.VERTICAL -> R.string.reader_image_mode_vertical
        ImageReaderMode.WEBTOON -> R.string.reader_image_mode_webtoon
    }

private val ImageReaderScaleType.labelResource: Int
    @StringRes get() = when (this) {
        ImageReaderScaleType.FIT_SCREEN -> R.string.reader_image_scale_fit_screen
        ImageReaderScaleType.FIT_WIDTH -> R.string.reader_image_scale_fit_width
    }

private val ImageReaderBackground.labelResource: Int
    @StringRes get() = when (this) {
        ImageReaderBackground.BLACK -> R.string.reader_image_background_black
        ImageReaderBackground.GRAY -> R.string.reader_image_background_gray
        ImageReaderBackground.WHITE -> R.string.reader_image_background_white
    }
