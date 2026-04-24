package dev.fuju.feature.admin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fuju.core.domain.Badge
import dev.fuju.core.ui.theme.FujuDimens

@Composable
fun AdminBadgesList(badges: List<Badge>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(badges, key = { it.id }) { badge ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(FujuDimens.SpaceL),
                verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceXS),
            ) {
                Text(badge.label, style = MaterialTheme.typography.titleMedium)
                Text("#${badge.key}", style = MaterialTheme.typography.bodySmall)
                if (badge.description.isNotBlank()) {
                    Text(badge.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
            HorizontalDivider()
        }
    }
}
