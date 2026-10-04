package com.veronezzi.meusantinho.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.veronezzi.meusantinho.R
import com.veronezzi.meusantinho.ui.common.initialsOf
import com.veronezzi.meusantinho.ui.theme.MeuSantinhoTheme

/**
 * Candidate photo over a neutral initials avatar. The initials show while the photo loads, when it
 * fails and when there is no photo (proportional offices, open data), all with the same look for
 * every candidate. Photos go through Coil's singleton image loader (disk cache: seen photos work
 * offline).
 *
 * @param photoDescription "Foto de {nome}" where the photo adds information; null when the name is
 * already announced next to it (list items).
 */
@Composable
fun CandidateAvatar(
    name: String,
    photoUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    photoDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsOf(name),
            style = if (size >= 72.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics { },
        )
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = photoDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/**
 * Official status text exactly as the TSE publishes it, in the same neutral style for every value
 * (no green or red, ARCHITECTURE.md 4.4). Blank texts read "Situação não informada pelo TSE".
 */
@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    val label = text.ifBlank { stringResource(R.string.status_not_informed) }
    val spoken = stringResource(R.string.status_spoken, label)
    Surface(
        modifier = modifier.semantics { contentDescription = spoken },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@ThemePreviews
@Composable
private fun CandidateViewsPreview() {
    MeuSantinhoTheme(dynamicColor = false) {
        PreviewSurface {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CandidateAvatar(name = "Maria Exemplo", photoUrl = null)
                CandidateAvatar(name = "José", photoUrl = null, size = 72.dp)
                StatusChip(text = "Deferido")
                StatusChip(text = "")
            }
        }
    }
}
