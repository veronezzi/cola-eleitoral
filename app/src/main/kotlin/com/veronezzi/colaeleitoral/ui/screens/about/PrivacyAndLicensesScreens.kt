package com.veronezzi.colaeleitoral.ui.screens.about

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.veronezzi.colaeleitoral.BuildConfig
import com.veronezzi.colaeleitoral.R
import com.veronezzi.colaeleitoral.ui.common.openInBrowser
import com.veronezzi.colaeleitoral.ui.common.writeEmail
import com.veronezzi.colaeleitoral.ui.components.AppTopBar
import com.veronezzi.colaeleitoral.ui.components.ListSkeleton
import com.veronezzi.colaeleitoral.ui.components.ScreenPreviews
import com.veronezzi.colaeleitoral.ui.components.SectionHeader
import com.veronezzi.colaeleitoral.ui.theme.ColaEleitoralTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Privacy policy, in full inside the app (LGPD art. 9º; Google Play requires it in the app and at
 * a public URL). The text is docs/privacidade.md itself, embedded at build time as
 * `res/raw/privacy_policy.md`, so the app and the published page never diverge. The URL, the
 * contact and the developer name come from BuildConfig.
 */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val mailSubject = stringResource(R.string.about_contact_subject)
    val blocks by produceState<List<PolicyBlock>?>(initialValue = null, resources) {
        value = withContext(Dispatchers.IO) {
            PolicyMarkdown.parse(resources.openRawResource(R.raw.privacy_policy).bufferedReader().use { it.readText() })
        }
    }
    PrivacyPolicyContent(
        blocks = blocks,
        onBack = onBack,
        onOpenLink = { target ->
            if (target.startsWith(MAILTO)) context.writeEmail(target.removePrefix(MAILTO), mailSubject) else context.openInBrowser(target)
        },
        modifier = modifier,
    )
}

/** Stateless policy: [blocks] null while the text loads. [onOpenLink] gets an https or mailto target. */
@Composable
fun PrivacyPolicyContent(
    blocks: List<PolicyBlock>?,
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { AppTopBar(title = stringResource(R.string.privacy_title), onBack = onBack) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (blocks == null) {
                ListSkeleton(description = stringResource(R.string.privacy_loading), rows = 4)
                return@Box
            }
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .testTag(PRIVACY_POLICY_TEST_TAG),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item(key = "developer", contentType = "developer") {
                    Text(
                        text = stringResource(R.string.about_developer, BuildConfig.DEVELOPER_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                // Static text: the position is a stable key.
                itemsIndexed(blocks, key = { index, _ -> index }, contentType = { _, block -> block::class }) { _, block ->
                    PolicyBlockView(block = block, onOpenLink = onOpenLink)
                }
                item(key = "online", contentType = "link") {
                    LinkRow(
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        title = stringResource(R.string.privacy_online),
                        supporting = BuildConfig.PRIVACY_POLICY_URL,
                        onClick = { onOpenLink(BuildConfig.PRIVACY_POLICY_URL) },
                    )
                }
                item(key = "contact", contentType = "link") {
                    LinkRow(
                        icon = Icons.Outlined.Mail,
                        title = stringResource(R.string.about_contact),
                        supporting = BuildConfig.CONTACT_EMAIL,
                        onClick = { onOpenLink(MAILTO + BuildConfig.CONTACT_EMAIL) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PolicyBlockView(block: PolicyBlock, onOpenLink: (String) -> Unit) {
    when (block) {
        is PolicyBlock.Title -> Text(
            text = block.text,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { heading() },
        )
        is PolicyBlock.Heading -> SectionHeader(text = block.text, modifier = Modifier.padding(top = 12.dp))
        is PolicyBlock.Paragraph -> Text(
            text = rememberPolicyText(block.inlines, onOpenLink),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
        is PolicyBlock.BulletList -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                // TalkBack announces "lista, N itens" and where it ends.
                .semantics { collectionInfo = CollectionInfo(rowCount = block.items.size, columnCount = 1) },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            block.items.forEachIndexed { index, item ->
                Row {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .clearAndSetSemantics { },
                    )
                    Text(
                        text = rememberPolicyText(item, onOpenLink),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .weight(1f)
                            .semantics {
                                collectionItemInfo = CollectionItemInfo(rowIndex = index, rowSpan = 1, columnIndex = 0, columnSpan = 1)
                            },
                    )
                }
            }
        }
    }
}

/** Bold, monospace code and links (focusable and announced as links by TalkBack). */
@Composable
private fun rememberPolicyText(inlines: List<PolicyInline>, onOpenLink: (String) -> Unit): AnnotatedString {
    val currentOnOpenLink by rememberUpdatedState(onOpenLink)
    val colors = MaterialTheme.colorScheme
    return remember(inlines, colors) {
        val linkStyles = TextLinkStyles(SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline))
        buildAnnotatedString {
            inlines.forEach { inline ->
                val weight = if (inline.bold) FontWeight.Bold else null
                when (inline) {
                    is PolicyInline.Plain -> withStyle(SpanStyle(fontWeight = weight)) { append(inline.text) }
                    is PolicyInline.Code -> withStyle(
                        SpanStyle(fontWeight = weight, fontFamily = FontFamily.Monospace, background = colors.surfaceContainerHigh),
                    ) { append(inline.text) }
                    is PolicyInline.Link -> withLink(
                        LinkAnnotation.Url(inline.target, linkStyles) { currentOnOpenLink(inline.target) },
                    ) { withStyle(SpanStyle(fontWeight = weight)) { append(inline.text) } }
                }
            }
        }
    }
}

private const val MAILTO = "mailto:"
const val PRIVACY_POLICY_TEST_TAG = "privacy_policy"

/** An open-source library shipped in the app (runtime dependencies of gradle/libs.versions.toml). */
data class OpenSourceLibrary(val name: String, val artifacts: String)

/**
 * Static list, kept in sync with gradle/libs.versions.toml by hand (no plugin that pulls Play
 * Services, ARCHITECTURE.md 4.9). Every library is Apache-2.0; test-only libraries are not shipped.
 * Names are proper nouns, not translatable text.
 */
val OPEN_SOURCE_LIBRARIES = listOf(
    OpenSourceLibrary("AndroidX Activity", "androidx.activity:activity-compose"),
    OpenSourceLibrary("AndroidX Biometric", "androidx.biometric:biometric"),
    OpenSourceLibrary("AndroidX Core", "androidx.core:core-ktx, core-splashscreen"),
    OpenSourceLibrary("AndroidX DataStore", "androidx.datastore:datastore-preferences"),
    OpenSourceLibrary("AndroidX Hilt", "androidx.hilt:hilt-navigation-compose, hilt-work"),
    OpenSourceLibrary("AndroidX Lifecycle", "androidx.lifecycle:lifecycle-runtime-compose, viewmodel-compose, process"),
    OpenSourceLibrary("AndroidX Navigation", "androidx.navigation:navigation-compose"),
    OpenSourceLibrary("AndroidX Room", "androidx.room:room-runtime, room-ktx"),
    OpenSourceLibrary("AndroidX WorkManager", "androidx.work:work-runtime-ktx"),
    OpenSourceLibrary("Jetpack Compose", "androidx.compose.ui, foundation, material3, material3-adaptive, material-icons-extended"),
    OpenSourceLibrary("Dagger / Hilt", "com.google.dagger:hilt-android"),
    OpenSourceLibrary("Kotlin", "org.jetbrains.kotlin:kotlin-stdlib"),
    OpenSourceLibrary("kotlinx.coroutines", "org.jetbrains.kotlinx:kotlinx-coroutines-android"),
    OpenSourceLibrary("kotlinx.serialization", "org.jetbrains.kotlinx:kotlinx-serialization-json"),
    OpenSourceLibrary("OkHttp / Okio", "com.squareup.okhttp3:okhttp"),
    OpenSourceLibrary("Retrofit", "com.squareup.retrofit2:retrofit, converter-kotlinx-serialization"),
    OpenSourceLibrary("Coil", "io.coil-kt.coil3:coil-compose, coil-network-okhttp"),
)

@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var showApache by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = { AppTopBar(title = stringResource(R.string.licenses_title), onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item(key = "data") {
                SectionHeader(text = stringResource(R.string.licenses_data_section))
                Paragraphs(stringResource(R.string.about_attribution))
            }
            item(key = "libraries") {
                SectionHeader(text = stringResource(R.string.licenses_libraries_section))
                Paragraphs(stringResource(R.string.licenses_intro))
            }
            items(OPEN_SOURCE_LIBRARIES, key = { it.name }) { library ->
                ListItem(
                    headlineContent = { Text(library.name) },
                    supportingContent = {
                        Text("${library.artifacts}\n${stringResource(R.string.licenses_apache)}")
                    },
                )
                HorizontalDivider()
            }
            item(key = "apache") {
                TextButton(onClick = { showApache = true }, modifier = Modifier.padding(8.dp)) {
                    Text(stringResource(R.string.licenses_read_apache))
                }
            }
        }
    }
    if (showApache) {
        val context = LocalContext.current
        val text by produceState(initialValue = "") { value = readApacheLicense(context) }
        AlertDialog(
            onDismissRequest = { showApache = false },
            title = { Text(stringResource(R.string.licenses_apache)) },
            text = {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { showApache = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

private suspend fun readApacheLicense(context: Context): String = withContext(Dispatchers.IO) {
    context.resources.openRawResource(R.raw.apache_license_2_0).bufferedReader().use { it.readText() }
}

@ScreenPreviews
@Composable
private fun PrivacyPolicyPreview() {
    ColaEleitoralTheme(dynamicColor = false) {
        PrivacyPolicyContent(
            blocks = PolicyMarkdown.parse(
                """
                # Política de privacidade do app Cola Eleitoral

                ## Resumo

                - **O desenvolvedor não coleta dados pessoais.** O app não tem conta
                  nem anúncios.
                - O app só se conecta a `divulgacandcontas.tse.jus.br`. Contato: contato@example.com

                Versão publicada: <https://example.com/privacidade/>.
                """.trimIndent(),
            ),
            onBack = {},
            onOpenLink = {},
        )
    }
}

@ScreenPreviews
@Composable
private fun LicensesPreview() {
    ColaEleitoralTheme(dynamicColor = false) {
        LicensesScreen(onBack = {})
    }
}
