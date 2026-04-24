package dev.fuju.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.fuju.core.domain.ToastKind
import dev.fuju.core.ui.theme.FujuColors
import dev.fuju.core.ui.theme.FujuDimens

/**
 * ビジュアルだけの Toast。`../frontend/src/state/ToastProvider.tsx` が管理する
 * queue はより上位（composeApp の Navigation root）で購読する想定。
 */
@Composable
fun FujuToast(
    message: String,
    kind: ToastKind,
    modifier: Modifier = Modifier,
) {
    val bg = when (kind) {
        ToastKind.INFO -> FujuColors.Ink700
        ToastKind.SUCCESS -> FujuColors.Positive500
        ToastKind.ERROR -> FujuColors.Danger500
    }
    Text(
        text = message,
        color = MaterialTheme.colorScheme.surface,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .clip(RoundedCornerShape(FujuDimens.RadiusM))
            .background(bg)
            .padding(horizontal = FujuDimens.SpaceL, vertical = FujuDimens.SpaceM),
    )
}
