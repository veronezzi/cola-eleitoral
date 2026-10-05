package com.veronezzi.colaeleitoral.ui.screenshots

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.veronezzi.colaeleitoral.domain.model.BallotPick
import com.veronezzi.colaeleitoral.domain.model.Candidate
import com.veronezzi.colaeleitoral.domain.model.CandidateDetail
import com.veronezzi.colaeleitoral.domain.model.CandidateStatus
import com.veronezzi.colaeleitoral.domain.model.DataSource
import com.veronezzi.colaeleitoral.domain.model.Election
import com.veronezzi.colaeleitoral.domain.model.ElectionScope
import com.veronezzi.colaeleitoral.domain.model.FilterOptions
import com.veronezzi.colaeleitoral.domain.model.Office
import com.veronezzi.colaeleitoral.domain.model.OfficeRules
import com.veronezzi.colaeleitoral.domain.model.Party
import com.veronezzi.colaeleitoral.domain.model.Round
import com.veronezzi.colaeleitoral.domain.model.RunningMate
import com.veronezzi.colaeleitoral.domain.model.VoterLocation
import com.veronezzi.colaeleitoral.ui.common.Freshness
import com.veronezzi.colaeleitoral.ui.common.LoadState
import com.veronezzi.colaeleitoral.ui.common.buildBallotSlots
import com.veronezzi.colaeleitoral.ui.navigation.AppNavigationLayout
import com.veronezzi.colaeleitoral.ui.navigation.NavigationChrome
import com.veronezzi.colaeleitoral.ui.navigation.TopLevelDestination
import com.veronezzi.colaeleitoral.ui.screens.ballot.BallotActions
import com.veronezzi.colaeleitoral.ui.screens.ballot.BallotEntry
import com.veronezzi.colaeleitoral.ui.screens.ballot.BallotScreen
import com.veronezzi.colaeleitoral.ui.screens.ballot.BallotUiState
import com.veronezzi.colaeleitoral.ui.screens.candidates.CandidateFiltersContent
import com.veronezzi.colaeleitoral.ui.screens.candidates.CandidateItem
import com.veronezzi.colaeleitoral.ui.screens.candidates.CandidateListActions
import com.veronezzi.colaeleitoral.ui.screens.candidates.CandidateListScreen
import com.veronezzi.colaeleitoral.ui.screens.candidates.CandidateListUiState
import com.veronezzi.colaeleitoral.ui.screens.candidates.ListContent
import com.veronezzi.colaeleitoral.ui.screens.cola.ColaExportScreen
import com.veronezzi.colaeleitoral.ui.screens.cola.ColaUiState
import com.veronezzi.colaeleitoral.ui.screens.detail.CandidateDetailScreen
import com.veronezzi.colaeleitoral.ui.screens.detail.CandidateDetailUiState
import com.veronezzi.colaeleitoral.ui.screens.detail.DetailContent
import com.veronezzi.colaeleitoral.ui.screens.detail.PickState
import com.veronezzi.colaeleitoral.ui.screens.home.BallotSection
import com.veronezzi.colaeleitoral.ui.screens.home.ElectionSummary
import com.veronezzi.colaeleitoral.ui.screens.home.HomeActions
import com.veronezzi.colaeleitoral.ui.screens.home.HomeScreen
import com.veronezzi.colaeleitoral.ui.screens.home.HomeUiState
import com.veronezzi.colaeleitoral.ui.screens.onboarding.OnboardingScreen
import com.veronezzi.colaeleitoral.ui.theme.ColaEleitoralTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.awt.image.BufferedImage
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import javax.imageio.ImageIO

/**
 * Phone screenshots for the Google Play listing, rendered on the JVM from the real screens with
 * Robolectric's native graphics: 1080 x 1920 px, pt-BR, light theme with the neutral palette.
 * Every name, party and number is FICTITIOUS ("Candidata Exemplo", "PEX: Partido Exemplo"; party
 * numbers in the 90s, which no Brazilian party uses), so no real candidate or party is shown.
 *
 * Excluded from the normal test run. To record them again:
 * `./gradlew :app:testDebugUnitTest --tests '*.StoreScreenshots' -PcolaEleitoral.storeScreenshots=true`
 * (writes fastlane/metadata/android/pt-BR/images/phoneScreenshots/0N-*.png, 24-bit PNG without alpha).
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "pt-rBR-w360dp-h640dp-port-xxhdpi")
class StoreScreenshots {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var outputDir: File

    @Before
    fun onlyWhenRecording() {
        val dir = System.getProperty("colaEleitoral.screenshotsDir")
        assumeTrue("Run with -PcolaEleitoral.storeScreenshots=true", dir != null)
        outputDir = File(checkNotNull(dir)).apply { mkdirs() }
        System.setProperty("java.awt.headless", "true")
    }

    @Test
    fun s01FirstRun() = record("01-aviso-inicial") {
        OnboardingScreen(isSaving = false, onAccept = {})
    }

    @Test
    fun s02Home() = record("02-inicio-cedula") {
        InApp(TopLevelDestination.HOME) {
            HomeScreen(
                state = HomeUiState(
                    loadState = LoadState.Loaded,
                    election = ElectionSummary(
                        id = Sample.election.id,
                        name = Sample.election.name,
                        year = 2026,
                        scope = ElectionScope.GENERAL,
                        round = Round.FIRST,
                        roundDate = Sample.election.date,
                        daysUntil = 3,
                        isRoundOpen = true,
                    ),
                    location = VoterLocation("SP"),
                    ballot = BallotSection.Slots(buildBallotSlots(Sample.offices, Sample.picks)),
                    freshness = Sample.freshness,
                    reminderEnabled = true,
                ),
                actions = HomeActions({ _, _, _, _ -> }, { _, _ -> }, {}, {}),
                onRefresh = {},
                onElectionSelected = {},
                onEnableReminder = {},
            )
        }
    }

    /**
     * The list with "Filtros e ordem" open. The real sheet is a separate window, which Robolectric
     * does not draw, so its real content ([CandidateFiltersContent]) is drawn on a Material 3
     * bottom-sheet surface with the default scrim, shape and drag handle.
     */
    @Test
    fun s03ListWithFilters() = record("03-lista-filtros") {
        val state = CandidateListUiState(
            officeCode = OfficeRules.SENATOR,
            officeName = "Senador",
            unitName = "São Paulo",
            slot = 1,
            maxPicks = 2,
            isSecondRound = false,
            options = FilterOptions(
                parties = Sample.senators.map { it.party },
                registrationStatuses = Sample.senators.map { it.status.registration }.distinct().sorted(),
            ),
            items = Sample.senators.map { candidate ->
                CandidateItem(
                    candidate = candidate,
                    numberText = candidate.number.toString(),
                    isSaved = candidate.ballotName == "Candidata Exemplo",
                    isInOtherSlot = candidate.ballotName == "Candidata Modelo",
                )
            },
            totalCount = Sample.senators.size,
            content = ListContent.Items,
            freshness = Sample.freshness,
            showPhotos = true,
        )
        val actions = CandidateListActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        Box(modifier = Modifier.fillMaxSize()) {
            InApp(TopLevelDestination.HOME) { CandidateListScreen(state = state, query = "", actions = actions) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BottomSheetDefaults.ScrimColor),
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                shape = BottomSheetDefaults.ExpandedShape,
                color = BottomSheetDefaults.ContainerColor,
                tonalElevation = BottomSheetDefaults.Elevation,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BottomSheetDefaults.DragHandle()
                    CandidateFiltersContent(state = state, actions = actions, onDismiss = {})
                }
            }
        }
    }

    @Test
    fun s04Detail() = record("04-detalhe") {
        InApp(TopLevelDestination.HOME) {
            CandidateDetailScreen(
                state = CandidateDetailUiState(
                    content = DetailContent.Loaded,
                    candidate = Sample.detail.candidate,
                    detail = Sample.detail,
                    officeName = "Senador",
                    unitName = "São Paulo",
                    digitCount = 3,
                    numberText = Sample.detail.candidate.number.toString(),
                    slot = 1,
                    maxPicks = 2,
                    officialPageUrl = Sample.detail.officialPageUrl,
                    freshness = Sample.freshness,
                    pickState = PickState.SavedHere,
                ),
                onBack = {},
                onSave = {},
                onRemove = {},
                onConfirmReplace = {},
                onDismissReplace = {},
                onUndoRemove = {},
                onMessageShown = {},
                onRetry = {},
                onOpenBallot = {},
            )
        }
    }

    @Test
    fun s05MinhaCola() = record("05-minha-cola") {
        InApp(TopLevelDestination.BALLOT) {
            BallotScreen(
                state = BallotUiState(
                    isLoading = false,
                    electionId = Sample.election.id,
                    electionYear = 2026,
                    electionName = Sample.election.name,
                    roundDate = Sample.election.date,
                    entries = buildBallotSlots(Sample.offices, Sample.picks).map { BallotEntry(it) },
                ),
                actions = BallotActions({ _, _, _, _ -> }, {}, { _, _ -> }),
                onRoundSelected = {},
                onRemove = {},
                onUndoRemove = {},
                onClearRequested = {},
                onClearConfirmed = {},
                onClearDismissed = {},
                onMessageShown = {},
            )
        }
    }

    @Test
    fun s06ColaExport() {
        show {
            InApp(TopLevelDestination.BALLOT) {
                ColaExportScreen(
                    state = ColaUiState(
                        isLoading = false,
                        electionName = Sample.election.name,
                        date = Sample.election.date,
                        slots = buildBallotSlots(Sample.offices, Sample.picks),
                    ),
                    onBack = {},
                    onShowNamesChange = {},
                    onShareConfirmed = {},
                    onShareHandled = {},
                    onMessageShown = {},
                )
            }
        }
        // The preview bitmap is drawn off the main thread by the same renderer as the PDF.
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodes(hasContentDescription("Prévia da cola", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        save("06-cola-para-imprimir")
    }

    /** The bottom bar of the app around a first-level or sub-screen, as on a phone. */
    @Composable
    private fun InApp(tab: TopLevelDestination, content: @Composable () -> Unit) {
        AppNavigationLayout(chrome = NavigationChrome.BAR, selected = tab, ballotEnabled = true, onSelect = {}, content = content)
    }

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { ColaEleitoralTheme(dynamicColor = false, darkTheme = false) { content() } }
        composeRule.waitForIdle()
    }

    private fun record(name: String, content: @Composable () -> Unit) {
        show(content)
        save(name)
    }

    /** The window, flattened onto an opaque 1080 x 1920 canvas and written as a 24-bit PNG. */
    private fun save(name: String) {
        composeRule.waitForIdle()
        val roots = composeRule.onAllNodes(isRoot())
        // Dialogs and sheets are separate windows that Robolectric does not draw: screens only.
        check(roots.fetchSemanticsNodes().size == 1) { "$name: expected one window" }
        val window = roots[0].captureToImage().asAndroidBitmap()
        val opaque = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        Canvas(opaque).apply {
            drawColor(android.graphics.Color.WHITE)
            drawBitmap(window, 0f, 0f, null)
        }
        val pixels = IntArray(WIDTH * HEIGHT)
        opaque.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        // 24-bit RGB: Google Play rejects screenshots with an alpha channel.
        val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, WIDTH, HEIGHT, pixels, 0, WIDTH)
        check(ImageIO.write(image, "png", File(outputDir, "$name.png"))) { "No PNG writer" }
    }

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1920
    }
}

/** Fictitious sample data: invented people and parties, numbers no party uses. */
private object Sample {
    val election = Election(
        id = 20322002026,
        year = 2026,
        name = "Eleição Geral Federal 2026",
        round = null,
        scope = ElectionScope.GENERAL,
        date = LocalDate.of(2026, 10, 4),
    )

    val offices: List<Office> = listOfNotNull(
        OfficeRules.office(OfficeRules.FEDERAL_DEPUTY, "Deputado Federal", "SP", 2026, Round.FIRST),
        OfficeRules.office(OfficeRules.STATE_DEPUTY, "Deputado Estadual", "SP", 2026, Round.FIRST),
        OfficeRules.office(OfficeRules.SENATOR, "Senador", "SP", 2026, Round.FIRST),
        OfficeRules.office(OfficeRules.GOVERNOR, "Governador", "SP", 2026, Round.FIRST),
        OfficeRules.office(OfficeRules.PRESIDENT, "Presidente", "BR", 2026, Round.FIRST),
    )

    private val parties = mapOf(
        "PEX" to "Partido Exemplo",
        "PFI" to "Partido Fictício",
        "PMO" to "Partido Modelo",
        "PAM" to "Partido Amostra",
        "PTE" to "Partido Teste",
    )

    private fun candidate(id: Long, number: Int, name: String, party: String, status: String, officeCode: Int = OfficeRules.SENATOR) =
        Candidate(
            id = id,
            electionId = election.id,
            ueCode = "SP",
            officeCode = officeCode,
            number = number,
            ballotName = name,
            fullName = "$name da Silva",
            party = Party(acronym = party, number = Party.numberFromCandidateNumber(number), name = parties.getValue(party)),
            coalition = if (party == "PEX" || party == "PMO") "Federação Exemplo" else null,
            status = CandidateStatus(registration = status, totalization = "Concorrendo", onBallot = "Consta da urna"),
            photoUrl = null,
        )

    val senators = listOf(
        candidate(1, 915, "Candidata Teste", "PTE", "Deferido"),
        candidate(2, 924, "Candidato Amostra", "PAM", "Aguardando julgamento"),
        candidate(3, 943, "Candidata Modelo", "PMO", "Deferido"),
        candidate(4, 962, "Candidato Fictício", "PFI", "Deferido"),
        candidate(5, 981, "Candidata Exemplo", "PEX", "Deferido"),
    ).sortedBy { it.number }

    val detail = CandidateDetail(
        candidate = senators.first { it.ballotName == "Candidata Exemplo" },
        coalitionType = "Federação",
        coalitionComposition = "PEX / PMO",
        runningMates = listOf(
            RunningMate(11, "1º Suplente", "Suplente Exemplo", null, "PEX", null, null),
            RunningMate(12, "2º Suplente", "Suplente Modelo", null, "PMO", null, null),
        ),
        officialPageUrl = "https://divulgacandcontas.tse.jus.br/divulga/",
        photoPublishable = true,
        lastUpdate = LocalDateTime.of(2026, 9, 18, 14, 39),
    )

    private fun pick(officeCode: Int, slot: Int, number: String, name: String, party: String) =
        offices.first { it.code == officeCode }.let { office ->
            BallotPick(
                electionId = election.id,
                electionYear = 2026,
                round = Round.FIRST,
                officeCode = office.code,
                officeName = office.name,
                urnaOrder = office.urnaOrder,
                digitCount = office.digitCount,
                slot = slot,
                ueCode = office.ueCode,
                candidateId = number.toLong(),
                candidateNumber = number,
                ballotName = name,
                partyAcronym = party,
                coalition = null,
                runningMateNames = emptyList(),
                statusAtSave = "Deferido",
                savedAt = Instant.parse("2026-10-01T12:00:00Z"),
                source = DataSource.DIVULGA_CAND_CONTAS,
            )
        }

    /** The President vote is left empty: the cola keeps its boxes to fill by hand. */
    val picks = listOf(
        pick(OfficeRules.FEDERAL_DEPUTY, 1, "9801", "Deputada Exemplo", "PEX"),
        pick(OfficeRules.STATE_DEPUTY, 1, "96012", "Deputado Fictício", "PFI"),
        pick(OfficeRules.SENATOR, 1, "981", "Candidata Exemplo", "PEX"),
        pick(OfficeRules.SENATOR, 2, "943", "Candidata Modelo", "PMO"),
        pick(OfficeRules.GOVERNOR, 1, "92", "Candidato Amostra Neto", "PAM"),
    )

    val freshness = Freshness(
        fetchedAt = Instant.now().minus(Duration.ofMinutes(12)),
        isStale = false,
        lastError = null,
        source = DataSource.DIVULGA_CAND_CONTAS,
    )
}
