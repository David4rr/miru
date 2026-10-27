package id.miru.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import id.miru.app.data.AppRelease
import id.miru.app.ui.components.update.ChangelogModalBottomSheet as ImplChangelogModalBottomSheet
import id.miru.app.ui.components.update.FormattedChangelogContent as ImplFormattedChangelogContent
import id.miru.app.ui.components.update.UpdateAvailableBottomSheet as ImplUpdateAvailableBottomSheet
import id.miru.app.ui.components.update.UpdateBanner as ImplUpdateBanner

@Composable
fun UpdateBanner(
    release: AppRelease,
    onViewUpdateClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ImplUpdateBanner(
        release = release,
        onViewUpdateClick = onViewUpdateClick,
        onDismissClick = onDismissClick,
        modifier = modifier
    )
}

@Composable
fun UpdateAvailableBottomSheet(
    release: AppRelease,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    ImplUpdateAvailableBottomSheet(
        release = release,
        onDismissRequest = onDismissRequest,
        modifier = modifier
    )
}

@Composable
fun ChangelogModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    ImplChangelogModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier
    )
}

@Composable
fun FormattedChangelogContent(
    body: String,
    modifier: Modifier = Modifier
) {
    ImplFormattedChangelogContent(
        body = body,
        modifier = modifier
    )
}
