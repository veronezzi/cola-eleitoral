package com.veronezzi.colaeleitoral.ui.navigation

import com.veronezzi.colaeleitoral.domain.model.Round
import kotlinx.serialization.Serializable

/*
 * Type-safe routes (ARCHITECTURE.md 4.1). Only primitives: larger data comes from the repositories
 * through the ViewModel. No deep links (privacy and attack surface).
 */

/** First-run notice: the app is independent from the TSE. Shown before any network access. */
@Serializable
data object OnboardingRoute

/** Voting place (UF and, for municipal elections, município). */
@Serializable
data class LocationRoute(val fromSettings: Boolean = false)

/** Start destination after the first run. */
@Serializable
data object HomeRoute

/**
 * Candidates of one office. [round] is [Round.number]; [slot] is the 1-based vote the list fills
 * (the Senate has two votes in some years).
 */
@Serializable
data class CandidateListRoute(
    val electionId: Long,
    val year: Int,
    val ueCode: String,
    val officeCode: Int,
    val round: Int,
    val slot: Int = 1,
)

@Serializable
data class CandidateDetailRoute(
    val electionId: Long,
    val year: Int,
    val ueCode: String,
    val officeCode: Int,
    val candidateId: Long,
    val round: Int,
    val slot: Int = 1,
)

/** "Minha cola": the saved picks of one election round. */
@Serializable
data class BallotRoute(val electionId: Long, val round: Int)

/** Printable and shareable cola of one election round. */
@Serializable
data class ColaExportRoute(val electionId: Long, val round: Int)

@Serializable
data object SettingsRoute

@Serializable
data object AboutRoute

@Serializable
data object PrivacyPolicyRoute

@Serializable
data object LicensesRoute

/** [Round] of a route argument; unknown numbers fall back to the first round. */
fun roundOf(number: Int): Round = Round.fromNumber(number) ?: Round.FIRST

/** Detail route of [candidateId], in the same list context as this route. */
fun CandidateListRoute.detail(candidateId: Long): CandidateDetailRoute = CandidateDetailRoute(
    electionId = electionId,
    year = year,
    ueCode = ueCode,
    officeCode = officeCode,
    candidateId = candidateId,
    round = round,
    slot = slot,
)
