package dev.fuju.composeApp.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * 軽量 back stack ベースの Navigator。`androidx.navigation` や Voyager の導入を
 * フェーズ 4 で検討するが、現状は依存を増やさず KMP common で動くものに留める。
 *
 * - push/pop/replace の 3 操作
 * - `current` 以外は表示しない単純 stack
 * - saveable にする余地（Bundle 保存）は未導入。再起動時は Login へ戻る。
 */
@Stable
class Navigator(
    initial: FujuDestination,
) {
    private val backstack: SnapshotStateList<FujuDestination> = mutableStateListOf(initial)

    val current: FujuDestination get() = backstack.last()
    val canPop: Boolean get() = backstack.size > 1

    fun push(destination: FujuDestination) {
        backstack.add(destination)
    }

    fun replace(destination: FujuDestination) {
        if (backstack.isNotEmpty()) backstack[backstack.lastIndex] = destination else backstack.add(destination)
    }

    fun pop() {
        if (canPop) backstack.removeAt(backstack.lastIndex)
    }

    fun popToRoot() {
        while (backstack.size > 1) backstack.removeAt(backstack.lastIndex)
    }
}

@Composable
fun rememberNavigator(initial: FujuDestination): Navigator = remember { Navigator(initial) }
