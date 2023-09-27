package com.neibus.teststep.module

import android.content.Context
import android.content.Context.MODE_PRIVATE
import org.json.JSONArray
import javax.inject.Inject

class PreferencesModule @Inject constructor(private val mContext: Context) {

    companion object {
        private const val CURRENT_WALK_TITLE = "currentTotalWalk"

        private const val TOTAL_DATA = "TOTAL_DATA"
        private const val DATE_TITLE = "TOTAL_DATE_WALK"
        private const val TODAY="today"
        private const val DATE_TOTAL_WALK="totalWalk"
        private const val DATE_CURRENT_WALK="currentWalk"
        
    }

    private val currentWalkPreferences by lazy {
        mContext.getSharedPreferences(CURRENT_WALK_TITLE,MODE_PRIVATE)
    }

    private val totalWalkPreferences by lazy {
        mContext.getSharedPreferences(TOTAL_DATA, MODE_PRIVATE)
    }

    fun saveCurrentWalkPreferences(totalWalk: Int) {
        currentWalkPreferences.edit().putInt(DATE_CURRENT_WALK,totalWalk).apply()
    }

    fun loadCurrentWalkPreferences(): Int {
        return currentWalkPreferences.getInt(DATE_CURRENT_WALK,0)
    }

    fun saveTodayTotalPreferences(jsonArray: JSONArray) {
        totalWalkPreferences.edit().putString(DATE_TITLE,jsonArray.toString()).apply()
    }

    fun loadTodayTotalPreferences(): String? {
        return totalWalkPreferences.getString(DATE_TITLE,null)
    }
}