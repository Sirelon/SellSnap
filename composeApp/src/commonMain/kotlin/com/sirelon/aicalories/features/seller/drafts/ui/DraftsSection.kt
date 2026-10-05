package com.sirelon.sellsnap.features.seller.drafts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppSectionHeader
import com.sirelon.sellsnap.features.seller.currency.domain.OlxCurrency
import com.sirelon.sellsnap.features.seller.drafts.Draft
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.drafts_copy_hint
import com.sirelon.sellsnap.generated.resources.drafts_see_all
import com.sirelon.sellsnap.generated.resources.drafts_title
import org.jetbrains.compose.resources.stringResource

/**
 * The generate screen's Drafts section: the most recent draft and a way to the Drafts screen. The
 * "See all" button stays visible with a single draft, because removing a draft happens on the
 * Drafts screen.
 */
@Composable
internal fun DraftsSection(
    latestDraft: Draft,
    currency: OlxCurrency,
    onOpen: (Draft) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag("drafts_section"),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.l),
    ) {
        AppSectionHeader(
            title = stringResource(Res.string.drafts_title),
            subtitle = stringResource(Res.string.drafts_copy_hint),
            actions = {
                TextButton(onClick = onSeeAll) {
                    Text(text = stringResource(Res.string.drafts_see_all))
                }
            },
        )
        key(latestDraft.id) {
            DraftCard(
                draft = latestDraft,
                currency = currency,
                onOpen = { onOpen(latestDraft) },
            )
        }
    }
}
