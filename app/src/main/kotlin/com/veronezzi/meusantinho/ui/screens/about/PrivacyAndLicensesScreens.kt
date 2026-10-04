package com.veronezzi.meusantinho.ui.screens.about

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.veronezzi.meusantinho.BuildConfig
import com.veronezzi.meusantinho.R
import com.veronezzi.meusantinho.ui.common.openInBrowser
import com.veronezzi.meusantinho.ui.common.writeEmail
import com.veronezzi.meusantinho.ui.components.AppTopBar
import com.veronezzi.meusantinho.ui.components.ScreenPreviews
import com.veronezzi.meusantinho.ui.components.SectionHeader
import com.veronezzi.meusantinho.ui.theme.MeuSantinhoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Privacy policy, in full inside the app (Google Play requires it in the app and at a public URL;
 * the URL and the contact come from BuildConfig).
 */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mailSubject = stringResource(R.string.about_contact_subject)
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
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                PRIVACY_SECTIONS.forEach { (title, body) ->
                    SectionHeader(text = stringResource(title))
                    Paragraphs(stringResource(body))
                }
                LinkRow(
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    title = stringResource(R.string.privacy_online),
                    supporting = BuildConfig.PRIVACY_POLICY_URL,
                    onClick = { context.openInBrowser(BuildConfig.PRIVACY_POLICY_URL) },
                )
                LinkRow(
                    icon = Icons.Outlined.Mail,
                    title = stringResource(R.string.about_contact),
                    supporting = BuildConfig.CONTACT_EMAIL,
                    onClick = { context.writeEmail(BuildConfig.CONTACT_EMAIL, mailSubject) },
                )
            }
        }
    }
}

private val PRIVACY_SECTIONS = listOf(
    R.string.privacy_who_title to R.string.privacy_who_body,
    R.string.privacy_collect_title to R.string.privacy_collect_body,
    R.string.privacy_tse_title to R.string.privacy_tse_body,
    R.string.privacy_picks_title to R.string.privacy_picks_body,
    R.string.privacy_permissions_title to R.string.privacy_permissions_body,
    R.string.privacy_delete_title to R.string.privacy_delete_body,
    R.string.privacy_public_data_title to R.string.privacy_public_data_body,
)

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
    MeuSantinhoTheme(dynamicColor = false) {
        PrivacyPolicyScreen(onBack = {})
    }
}

@ScreenPreviews
@Composable
private fun LicensesPreview() {
    MeuSantinhoTheme(dynamicColor = false) {
        LicensesScreen(onBack = {})
    }
}
