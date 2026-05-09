package dev.fuju.composeApp.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.fuju.core.ui.components.FujuSecondaryButton
import dev.fuju.core.ui.theme.FujuDimens

/**
 * 設定画面の 2 ペインシェル。
 * frontend `routes/SettingsRoute.tsx` + `ui/components/SettingsNav.tsx` を踏襲。
 *
 * 左カラムに項目リスト、右カラムに [content] を表示する。今のところ項目はプロフィール 1 つ
 * のみだが、将来追加用に [items] / [activeItem] / [onSelect] を外部化している。
 *
 * ナローな端末では縦に積む簡易レスポンシブを適用（しきい値 600.dp）。React 版は CSS Grid だが
 * KMP では BoxWithConstraints + 単純な分岐に寄せる。
 */
@Composable
fun SettingsShell(
    items: List<SettingsItem>,
    activeItemId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // content を分岐先で再利用するとスロットの内部 state が破棄される可能性があるため、
    // movableContentOf でラップして wide/narrow 切り替え時にも保持されるようにする。
    val movableContent = androidx.compose.runtime.remember { androidx.compose.runtime.movableContentOf(content) }
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp
        if (isWide) {
            Row(modifier = Modifier.fillMaxSize()) {
                SettingsNav(
                    items = items,
                    activeItemId = activeItemId,
                    onSelect = onSelect,
                    modifier =
                        Modifier
                            .width(220.dp)
                            .padding(FujuDimens.SpaceL),
                )
                HorizontalDivider(modifier = Modifier.width(1.dp))
                Column(modifier = Modifier.fillMaxSize().padding(FujuDimens.SpaceL)) {
                    movableContent()
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(FujuDimens.SpaceL)) {
                SettingsNav(
                    items = items,
                    activeItemId = activeItemId,
                    onSelect = onSelect,
                    modifier = Modifier.fillMaxWidth(),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = FujuDimens.SpaceS))
                movableContent()
            }
        }
    }
}

/**
 * 設定ハブの項目。`id` を URL の一部やナビゲーションキーに使う想定。
 */
data class SettingsItem(
    val id: String,
    val label: String,
)

@Composable
private fun SettingsNav(
    items: List<SettingsItem>,
    activeItemId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceXS)) {
        Text(
            text = "設定",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = FujuDimens.SpaceS),
        )
        items.forEach { item ->
            val active = item.id == activeItemId
            Surface(
                color =
                    if (active) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                modifier = Modifier.fillMaxWidth(),
            ) {
                FujuSecondaryButton(
                    text = item.label,
                    onClick = { onSelect(item.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
