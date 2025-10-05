package com.example.pelistrivia

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ScoreDao {
    @Insert
    suspend fun insert(score: ScoreEntity)

    @Query("SELECT * FROM scores ORDER BY score DESC, dateMillis ASC LIMIT :limit")
    suspend fun topScores(limit: Int): List<ScoreEntity>
}