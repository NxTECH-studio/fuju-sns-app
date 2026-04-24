package dev.fuju.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fuju.core.domain.ProfileUser
import dev.fuju.core.ui.theme.FujuDimens

/**
 * シンプルなプロフィール画面。フォロー/フォロワー数の UI は次フェーズで拡張する。
 */
@Composable
fun ProfileHeader(user: ProfileUser, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(FujuDimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceS),
    ) {
        Text(user.displayName, style = MaterialTheme.typography.headlineMedium)
        Text("@${user.displayId}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (user.bio.isNotBlank()) {
            Text(user.bio, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
