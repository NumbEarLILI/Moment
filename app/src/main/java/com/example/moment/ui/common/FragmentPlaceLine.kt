package com.example.moment.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
    onPlaceClick: (() -> Unit)? = null
) {
    if (weather == null && location == null) return
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
        location?.let { loc ->
            FragmentPlaceLine(
                location = loc,
                onClick = onPlaceClick
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
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}
