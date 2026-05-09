package dev.fuju.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import dev.fuju.core.domain.UpdateProfileInput
import dev.fuju.core.ui.components.ErrorFallback
import dev.fuju.core.ui.components.FujuPrimaryButton
import dev.fuju.core.ui.components.FujuSecondaryButton
import dev.fuju.core.ui.components.FujuTextField
import dev.fuju.core.ui.theme.FujuDimens
import dev.fuju.feature.profile.domain.ProfileViewModel
import dev.fuju.feature.profile.domain.sanitizeError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val BIO_MAX_LEN = 500
private const val BANNER_MAX_LEN = 1024

/**
 * 設定 > プロフィール編集セクション。
 * frontend `routes/settings/SettingsProfileSection.tsx` を写経。
 *
 * `bio` (max 500) と `bannerUrl` (max 1024) のみ編集可能。display_name / display_id /
 * アイコンは AuthCore 側で管理する旨を明記する。
 *
 * 保存成功時は [onSave] を経由してユーザーページへ navigate する想定（呼び出し側で
 * `/users/{sub}` に飛ばす）。
 */
@Composable
fun SettingsProfileSection(
    viewModel: ProfileViewModel,
    onSave: (sub: String) -> Unit,
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
            SettingsProfileForm(
                seedSub = me.sub,
                seedBio = me.bio,
                seedBannerUrl = me.bannerUrl,
                remoteError = state.error,
                onSubmit = { input ->
                    viewModel.updateProfile(input)
                    onSave(me.sub)
                },
                onCancel = onCancel,
                modifier = modifier,
            )
    }
}

@Composable
private fun SettingsProfileForm(
    seedSub: String,
    seedBio: String,
    seedBannerUrl: String,
    remoteError: String?,
    onSubmit: suspend (UpdateProfileInput) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var bio by rememberSaveable(seedSub) { mutableStateOf(seedBio) }
    var bannerUrl by rememberSaveable(seedSub) { mutableStateOf(seedBannerUrl) }
    var busy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(seedSub, seedBio, seedBannerUrl) {
        if (!busy) {
            bio = seedBio
            bannerUrl = seedBannerUrl
        }
    }

    val scroll = rememberScrollState()
    val displayedError = localError ?: remoteError

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(FujuDimens.SpaceM),
    ) {
        Text(
            text = "プロフィール編集",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "display_name / display_id / アイコンは AuthCore 側で編集してください。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FujuTextField(
            label = "自己紹介 (bio, 最大 $BIO_MAX_LEN 文字)",
            value = bio,
            onValueChange = { next -> if (next.length <= BIO_MAX_LEN) bio = next },
            singleLine = false,
            enabled = !busy,
        )
        FujuTextField(
            label = "バナー画像 URL (最大 $BANNER_MAX_LEN 文字)",
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
            val hasChanges = bio != seedBio || bannerUrl != seedBannerUrl
            FujuPrimaryButton(
                text = if (busy) "保存中..." else "保存",
                loading = busy,
                enabled = !busy && hasChanges,
                onClick = {
                    localError = null
                    busy = true
                    scope.launch {
                        try {
                            val input =
                                UpdateProfileInput(
                                    bio = bio,
                                    bannerUrl = bannerUrl.takeIf { it.isNotBlank() },
                                )
                            onSubmit(input)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            localError = sanitizeError(t)
                        } finally {
                            busy = false
                        }
                    }
                },
            )
        }
    }
}
