package dev.fuju.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import dev.fuju.core.domain.Me
import dev.fuju.core.domain.UpdateProfileInput
import dev.fuju.core.ui.components.ErrorFallback
import dev.fuju.core.ui.components.FujuPrimaryButton
import dev.fuju.core.ui.components.FujuSecondaryButton
import dev.fuju.core.ui.components.FujuTextField
import dev.fuju.core.ui.theme.FujuDimens
import dev.fuju.feature.profile.domain.ProfileViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 自分のプロフィール編集画面。React 版 `routes/MyProfileEditRoute.tsx` に相当。
 *
 * 編集できるのは `bio` と `banner_url` の 2 項目のみ。swagger の `UpdateUserProfileRequest`
 * に合わせて、display_name / display_id / icon_url は AuthCore 側の管理画面に委譲する旨を
 * 画面上に案内する。
 *
 * アイコン / バナー画像のアップロード UI は本 PR には含めない（`PUT /v1/user/icon` の multipart
 * 送信は expect/actual の file picker が必要なため別タスク）。
 *
 * ViewModel は状態を集約する 1 箇所だけで扱い、内部の form コンポーネントには plain 型で
 * 値と callback だけを渡す（Compose Rules の "hoist all the things"）。
 */
@Composable
fun ProfileEditScreen(
    viewModel: ProfileViewModel,
    onSave: (Me) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val me = state.me

    when {
        state.loading && me == null ->
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        me == null ->
            ErrorFallback(
                title = "プロフィールを取得できませんでした",
                message = state.error ?: "未ログインです。",
                onRetry = viewModel::reload,
                modifier = modifier.fillMaxSize(),
            )
        else ->
            ProfileEditForm(
                me = me,
                remoteError = state.error,
                onSubmit = { input -> viewModel.updateProfile(input) },
                onSave = onSave,
                onCancel = onCancel,
                modifier = modifier,
            )
    }
}

/**
 * 編集フォーム本体。ViewModel には触れず、外から渡された [onSubmit] を suspend で呼び、
 * 成功 / 失敗で state を切り替える。
 */
@Composable
private fun ProfileEditForm(
    me: Me,
    remoteError: String?,
    onSubmit: suspend (UpdateProfileInput) -> Me,
    onSave: (Me) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // rememberSaveable でプロセス death を跨いだ入力復元を許す。me.sub が変わったら seed しなおす。
    var bio by rememberSaveable(me.sub) { mutableStateOf(me.bio) }
    var bannerUrl by rememberSaveable(me.sub) { mutableStateOf(me.bannerUrl) }
    var busy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // me が後から更新された（editor を開いた後に reload が走った等）場合に値を同期する。
    LaunchedEffect(me.sub, me.bio, me.bannerUrl) {
        if (!busy) {
            bio = me.bio
            bannerUrl = me.bannerUrl
        }
    }

    val scroll = rememberScrollState()
    val displayedError = localError ?: remoteError

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(FujuDimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceM),
    ) {
        Text(
            text = "プロフィール編集",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "display_name / display_id / アイコンは AuthCore 側で編集してください。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FujuTextField(
            label = "自己紹介 (bio, 最大 500 文字)",
            value = bio,
            onValueChange = { next -> if (next.length <= BIO_MAX_LEN) bio = next },
            singleLine = false,
            enabled = !busy,
        )
        FujuTextField(
            label = "バナー画像 URL",
            value = bannerUrl,
            onValueChange = { next -> if (next.length <= BANNER_MAX_LEN) bannerUrl = next },
            placeholder = "https://...",
            keyboardType = KeyboardType.Uri,
            enabled = !busy,
        )
        if (displayedError != null) {
            Text(
                text = displayedError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FujuDimens.SpaceS, Alignment.End),
        ) {
            FujuSecondaryButton(text = "キャンセル", onClick = onCancel, enabled = !busy)
            FujuPrimaryButton(
                text = if (busy) "保存中..." else "保存",
                loading = busy,
                enabled = !busy,
                onClick = {
                    localError = null
                    busy = true
                    coroutineScope.launch {
                        try {
                            val next = onSubmit(UpdateProfileInput(bio = bio, bannerUrl = bannerUrl))
                            onSave(next)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            localError = t.message ?: "保存に失敗しました。"
                        } finally {
                            busy = false
                        }
                    }
                },
            )
        }
    }
}

private const val BIO_MAX_LEN = 500
private const val BANNER_MAX_LEN = 1024
