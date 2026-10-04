package com.veronezzi.meusantinho.data.local.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Public, rebuildable TSE data only (ARCHITECTURE.md 2.8). The user's picks never go here: they
 * live encrypted in noBackupFilesDir (data/local/secure).
 */

@Entity(tableName = "elections")
data class ElectionEntity(
    @PrimaryKey val id: Long,
    val year: Int,
    val name: String,
    /** [com.veronezzi.meusantinho.domain.model.ElectionScope] name. */
    val scope: String,
    val round: Int?,
    /** ISO date of the first round. */
    val date: String?,
)

@Entity(tableName = "municipalities", indices = [Index("uf")])
data class MunicipalityEntity(
    /** TSE code, text with leading zeros ("01120"). */
    @PrimaryKey val code: String,
    val uf: String,
    val name: String,
    val sourceElectionId: Long,
)

@Entity(tableName = "offices", primaryKeys = ["electionId", "ueCode", "code"])
data class OfficeEntity(
    val electionId: Long,
    val ueCode: String,
    val code: Int,
    val name: String,
    val candidateCount: Int?,
)

/** Candidate fields shared by the list rows and the detail rows. */
data class CandidateColumns(
    val ueCode: String,
    val officeCode: Int,
    val number: Int,
    val ballotName: String,
    val fullName: String?,
    val partyAcronym: String,
    val partyNumber: Int?,
    val partyName: String?,
    val coalition: String?,
    val registrationStatus: String,
    val totalizationStatus: String?,
    val onBallotStatus: String?,
    val isFit: Boolean?,
    val photoUrl: String?,
)

@Entity(
    tableName = "candidates",
    primaryKeys = ["electionId", "id"],
    indices = [Index(value = ["electionId", "ueCode", "officeCode"])],
)
data class CandidateEntity(
    val electionId: Long,
    val id: Long,
    @Embedded val columns: CandidateColumns,
)

/**
 * The detail keeps its own copy of the candidate fields: its status can be newer than the list's
 * (ARCHITECTURE.md 2.2, E5), and the detail screen shows the detail's.
 */
@Entity(tableName = "candidate_details", primaryKeys = ["electionId", "candidateId"])
data class CandidateDetailEntity(
    val electionId: Long,
    val candidateId: Long,
    @Embedded val columns: CandidateColumns,
    val coalitionType: String?,
    val coalitionComposition: String?,
    val officialPageUrl: String,
    val photoPublishable: Boolean?,
    /** ISO local date-time (Brasília). */
    val lastUpdate: String?,
)

@Entity(tableName = "running_mates", primaryKeys = ["electionId", "candidateId", "position"])
data class RunningMateEntity(
    val electionId: Long,
    val candidateId: Long,
    val position: Int,
    val mateId: Long?,
    val role: String,
    val ballotName: String,
    val fullName: String?,
    val partyAcronym: String?,
    val photoUrl: String?,
    val status: String?,
)

/**
 * Freshness of one cached key (see `FetchKeys`). [fetchedAt] is the last success, [lastAttemptAt]
 * the last try, [lastError] an `AppErrorCodec` code when the last try failed, [source] the
 * [com.veronezzi.meusantinho.domain.model.DataSource] name of the stored rows. Times in epoch ms.
 */
@Entity(tableName = "fetch_state")
data class FetchStateEntity(
    @PrimaryKey val fetchKey: String,
    val fetchedAt: Long?,
    val lastAttemptAt: Long?,
    val lastError: String?,
    val source: String?,
)
