package com.veronezzi.meusantinho.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** Light and dark previews (fallback palette, see [com.veronezzi.meusantinho.ui.theme.MeuSantinhoTheme]). */
@Preview(name = "Claro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Escuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class ThemePreviews

/** Light, dark and 200% font scale, for full screens. */
@Preview(name = "Claro", showBackground = true, showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Escuro", showBackground = true, showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Fonte 200%", showBackground = true, showSystemUi = true, fontScale = 2f)
annotation class ScreenPreviews

/** Surface with padding for component previews. */
@Composable
fun PreviewSurface(content: @Composable () -> Unit) {
    Surface {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}
