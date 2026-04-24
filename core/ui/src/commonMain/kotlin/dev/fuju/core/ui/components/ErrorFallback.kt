package dev.fuju.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.fuju.core.ui.theme.FujuDimens

/**
 * 画面単位のエラー表示。`../auth-component/src/components/AuthErrorFallback.tsx` の
 * 最小相当。ErrorCode 辞書からのメッセージ変換は上位レイヤ（feature:auth:ui）で行う。
 */
@Composable
fun ErrorFallback(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(FujuDimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceS),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (onRetry != null) {
            FujuSecondaryButton(text = "再試行", onClick = onRetry)
        }
    }
}
