package dev.fuju.feature.profile.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * フォロー / アンフォロー切替ボタン。React 版 `ui/components/FollowButton.tsx` と同じく
 * `following == true` のときは outlined（解除候補を示唆）、false のときは solid primary
 * （行動を促す）で表示を切り替える。
 *
 * `pending` と `disabled` は別引数。pending は optimistic 中の一時 disable 用で、UI 的には
 * 小さなスピナーに置き換える。`disabled` は呼び出し側が「そもそも押せない」と判定した
 * 場合（未認証 / 自分自身 / follow 情報未確定）に true にする。
 */
@Composable
fun FollowButton(
    following: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    pending: Boolean = false,
    disabled: Boolean = false,
) {
    val label = if (following) "フォロー解除" else "フォロー"
    val isEnabled = !disabled && !pending
    if (following) {
        OutlinedButton(
            onClick = onToggle,
            modifier = modifier,
            enabled = isEnabled,
        ) {
            FollowButtonContent(text = label, pending = pending)
        }
    } else {
        Button(
            onClick = onToggle,
            modifier = modifier,
            enabled = isEnabled,
            shape = ButtonDefaults.shape,
        ) {
            FollowButtonContent(text = label, pending = pending)
        }
    }
}

@Composable
private fun FollowButtonContent(
    text: String,
    pending: Boolean,
) {
    if (pending) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
    } else {
        Text(text)
    }
}
