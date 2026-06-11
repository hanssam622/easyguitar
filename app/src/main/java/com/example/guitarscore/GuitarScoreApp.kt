package com.example.guitarscore

import android.app.Application
import com.example.guitarscore.data.AppDatabase
import com.example.guitarscore.data.ScoreRepository

class GuitarScoreApp : Application() {
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { ScoreRepository(database.scoreDao()) }
}
