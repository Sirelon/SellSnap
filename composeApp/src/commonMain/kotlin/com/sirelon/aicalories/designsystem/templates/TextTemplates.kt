package com.sirelon.sellsnap.designsystem.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.sirelon.sellsnap.generated.resources.Res
import com.sirelon.sellsnap.generated.resources.or_divider
import com.sirelon.sellsnap.generated.resources.privacy_policy
import com.sirelon.sellsnap.generated.resources.terms_of_service
import com.sirelon.sellsnap.generated.resources.terms_privacy_prefix
import com.sirelon.sellsnap.designsystem.AppDimens
import com.sirelon.sellsnap.designsystem.AppTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun TitleWithSubtitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppDimens.Spacing.xl),
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = title,
            style = AppTheme.typography.headline,
            fontWeight = FontWeight.Bold,
            color = AppTheme.colors.onBackground,
            textAlign = TextAlign.Center,
        )

        Text(
            modifier = Modifier.fillMaxWidth(),
            text = subtitle,
            style = AppTheme.typography.title,
            color = AppTheme.colors.onSurfaceSoft,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TermsAndPrivacy(onTermsClick: () -> Unit, onPrivacyClick: () -> Unit) {
    // Terms and Privacy
    val termsOfService = stringResource(Res.string.terms_of_service)
    val privacyPolicy = stringResource(Res.string.privacy_policy)
    val annotatedString = buildAnnotatedString {
        append(stringResource(Res.string.terms_privacy_prefix))
        pushStringAnnotation(tag = "terms", annotation = "terms")
        withStyle(
            style = SpanStyle(
                color = AppTheme.colors.primary,
                textDecoration = TextDecoration.Underline
            )
        ) {
            append(termsOfService)
        }
        pop()
        append(" " + stringResource(Res.string.or_divider).replace("or", "and") + " ")
        pushStringAnnotation(tag = "privacy", annotation = "privacy")
        withStyle(
            style = SpanStyle(
                color = AppTheme.colors.primary,
                textDecoration = TextDecoration.Underline
            )
        ) {
            append(privacyPolicy)
        }
        pop()
    }

    ClickableText(
        text = annotatedString,
        style = AppTheme.typography.caption.copy(
            color = AppTheme.colors.onSurfaceSoft,
            textAlign = TextAlign.Center
        ),
        onClick = { offset ->
            annotatedString.getStringAnnotations(
                tag = "terms",
                start = offset,
                end = offset
            )
                .firstOrNull()?.let { onTermsClick() }
            annotatedString.getStringAnnotations(
                tag = "privacy",
                start = offset,
                end = offset
            )
                .firstOrNull()?.let { onPrivacyClick() }
        }
    )
}