package com.veronezzi.colaeleitoral.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ElectionDao {
    @Query("SELECT * FROM elections")
    fun observeAll(): Flow<List<ElectionEntity>>

    @Query("SELECT * FROM elections")
    suspend fun getAll(): List<ElectionEntity>

    @Query("SELECT * FROM elections WHERE id = :id")
    fun observe(id: Long): Flow<ElectionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(elections: List<ElectionEntity>)

    @Query("DELETE FROM elections")
    suspend fun deleteAll()
}

@Dao
interface MunicipalityDao {
    @Query("SELECT * FROM municipalities WHERE uf = :uf ORDER BY searchKey, name")
    fun observeByUf(uf: String): Flow<List<MunicipalityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(municipalities: List<MunicipalityEntity>)

    @Query("DELETE FROM municipalities WHERE uf = :uf")
    suspend fun deleteByUf(uf: String)
}

@Dao
interface OfficeDao {
    @Query("SELECT * FROM offices WHERE electionId = :electionId AND ueCode = :ueCode")
    fun observe(electionId: Long, ueCode: String): Flow<List<OfficeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(offices: List<OfficeEntity>)

    @Query("DELETE FROM offices WHERE electionId = :electionId AND ueCode = :ueCode")
    suspend fun delete(electionId: Long, ueCode: String)

    @Query("SELECT DISTINCT electionId FROM offices")
    suspend fun electionIds(): List<Long>

    @Query("DELETE FROM offices WHERE electionId = :electionId")
    suspend fun deleteElection(electionId: Long)
}

@Dao
interface CandidateDao {
    @Query(
        "SELECT * FROM candidates WHERE electionId = :electionId AND ueCode = :ueCode AND officeCode = :officeCode",
    )
    fun observe(electionId: Long, ueCode: String, officeCode: Int): Flow<List<CandidateEntity>>

    @Query("SELECT * FROM candidates WHERE electionId = :electionId AND id = :id")
    suspend fun get(electionId: Long, id: Long): CandidateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(candidates: List<CandidateEntity>)

    @Query("DELETE FROM candidates WHERE electionId = :electionId AND ueCode = :ueCode AND officeCode = :officeCode")
    suspend fun delete(electionId: Long, ueCode: String, officeCode: Int)

    @Query("SELECT DISTINCT electionId FROM candidates")
    suspend fun electionIds(): List<Long>

    @Query("DELETE FROM candidates WHERE electionId = :electionId")
    suspend fun deleteElection(electionId: Long)
}

@Dao
interface FetchStateDao {
    @Query("SELECT * FROM fetch_state WHERE fetchKey = :key")
    fun observe(key: String): Flow<FetchStateEntity?>

    @Query("SELECT * FROM fetch_state WHERE fetchKey = :key")
    suspend fun get(key: String): FetchStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: FetchStateEntity)

    /** A few dozen rows at most (one per list the user opened). */
    @Query("SELECT * FROM fetch_state")
    suspend fun getAll(): List<FetchStateEntity>

    @Query("DELETE FROM fetch_state WHERE substr(fetchKey, 1, length(:prefix)) = :prefix")
    suspend fun deleteWithPrefix(prefix: String)

    @Query("DELETE FROM fetch_state WHERE fetchKey = :key")
    suspend fun delete(key: String)
}
