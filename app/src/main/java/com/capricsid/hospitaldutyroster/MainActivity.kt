package com.capricsid.hospitaldutyroster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.capricsid.hospitaldutyroster.data.StaffRepository
import com.capricsid.hospitaldutyroster.ui.HospitalDutyRosterApp
import com.capricsid.hospitaldutyroster.ui.theme.HospitalDutyRosterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = StaffRepository(applicationContext)

        setContent {
            HospitalDutyRosterTheme {
                HospitalDutyRosterApp(repository = repository)
            }
        }
    }
}

