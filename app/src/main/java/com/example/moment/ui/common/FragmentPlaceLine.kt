package com.example.moment.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.moment.R
import com.example.moment.domain.model.FragmentLocation
import com.example.moment.domain.model.FragmentWeather
import com.example.moment.domain.model.fragmentPlaceLabel

@Composable
fun FragmentWeatherAndPlace(
    weather: FragmentWeather?,
    location: FragmentLocation?,
    modifier: Modifier = Modifier,
    onPlaceClick: (() -> Unit)? = null,
    placePlaceholder: String? = null,
    isPlaceLoading: Boolean = false
) {
    val placeText = when {
        location != null -> fragmentPlaceLabel(location)
        !placePlaceholder.isNullOrBlank() -> placePlaceholder
        else -> null
    }
    if (weather == null && placeText == null) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        weather?.caption()?.let { caption ->
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (placeText != null) {
            FragmentPlaceLine(
                placeLabel = placeText,
                onClick = onPlaceClick,
                isLoading = isPlaceLoading && location == null
            )
        }
    }
}

@Composable
fun FragmentPlaceLine(
    location: FragmentLocation,
    modifier: Modifier = Modifier,
    placeLabel: String = fragmentPlaceLabel(location),
    onClick: (() -> Unit)? = null,
    maxLines: Int = 2
) {
    FragmentPlaceLine(
        placeLabel = placeLabel,
        modifier = modifier,
        onClick = onClick,
        maxLines = maxLines
    )
}

@Composable
fun FragmentPlaceLine(
    placeLabel: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    maxLines: Int = 2,
    isLoading: Boolean = false
) {
    val color = if (onClick != null) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = if (onClick != null) {
            modifier.clickable(onClick = onClick)
        } else {
            modifier
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_place),
            contentDescription = "地点",
            modifier = Modifier.size(14.dp),
            tint = color
        )
        Text(
            placeLabel,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = color
            )
        }
    }
}
