package com.capricsid.hospitaldutyroster.data

import android.content.Context
import com.capricsid.hospitaldutyroster.model.ExperienceLevel
import com.capricsid.hospitaldutyroster.model.OpdCategory
import com.capricsid.hospitaldutyroster.model.StaffMember
import com.capricsid.hospitaldutyroster.model.StaffType
import com.capricsid.hospitaldutyroster.model.TmoSection
import org.json.JSONArray
import org.json.JSONObject

class StaffRepository(context: Context) {
    private val preferences = context.getSharedPreferences("hospital_duty_roster", Context.MODE_PRIVATE)

    fun loadStaff(): List<StaffMember> {
        val raw = preferences.getString(KEY_STAFF, null) ?: return seedStaff()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toStaffMember())
                }
            }
        }.getOrElse { seedStaff() }
    }

    fun saveStaff(staff: List<StaffMember>) {
        val json = JSONArray()
        staff.forEach { member ->
            json.put(member.toJson())
        }
        preferences.edit().putString(KEY_STAFF, json.toString()).apply()
    }

    private fun seedStaff(): List<StaffMember> = listOf(
        StaffMember(
            name = "ISMAIL",
            employeeCode = "11215",
            staffType = StaffType.TMO,
            section = TmoSection.WARD,
            experienceLevel = ExperienceLevel.SENIOR,
            opdCategory = OpdCategory.SENIOR
        ),
        StaffMember(
            name = "IHSAN",
            employeeCode = "11180",
            staffType = StaffType.TMO,
            section = TmoSection.WARD,
            experienceLevel = ExperienceLevel.MID,
            opdCategory = OpdCategory.SENIOR
        ),
        StaffMember(
            name = "ABBAS",
            employeeCode = "11219",
            staffType = StaffType.TMO,
            section = TmoSection.NURSERY,
            experienceLevel = ExperienceLevel.MID
        ),
        StaffMember(
            name = "GOHAR",
            staffType = StaffType.HO,
            opdCategory = OpdCategory.NEW_TMO
        )
    )

    private fun StaffMember.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("employeeCode", employeeCode)
        put("staffType", staffType.name)
        put("section", section?.name)
        put("experienceLevel", experienceLevel.name)
        put("ct2Eligible", ct2Eligible)
        put("reducedNights", reducedNights)
        put("weekendPreferenceOff", weekendPreferenceOff)
        put("opdCategory", opdCategory.name)
        put("active", active)
    }

    private fun JSONObject.toStaffMember(): StaffMember = StaffMember(
        id = optString("id"),
        name = optString("name"),
        employeeCode = optString("employeeCode"),
        staffType = StaffType.valueOf(optString("staffType", StaffType.TMO.name)),
        section = optString("section").takeIf { it.isNotBlank() }?.let { TmoSection.valueOf(it) },
        experienceLevel = ExperienceLevel.valueOf(optString("experienceLevel", ExperienceLevel.MID.name)),
        ct2Eligible = optBoolean("ct2Eligible", true),
        reducedNights = optBoolean("reducedNights", false),
        weekendPreferenceOff = optBoolean("weekendPreferenceOff", false),
        opdCategory = OpdCategory.valueOf(optString("opdCategory", OpdCategory.GENERAL.name)),
        active = optBoolean("active", true)
    )

    private companion object {
        const val KEY_STAFF = "master_staff"
    }
}

