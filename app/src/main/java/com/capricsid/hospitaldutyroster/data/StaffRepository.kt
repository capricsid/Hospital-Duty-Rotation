package com.capricsid.hospitaldutyroster.data

import android.content.Context
import com.capricsid.hospitaldutyroster.model.ExperienceLevel
import com.capricsid.hospitaldutyroster.model.OpdCategory
import com.capricsid.hospitaldutyroster.model.StaffMember
import com.capricsid.hospitaldutyroster.model.StaffType
import com.capricsid.hospitaldutyroster.model.TmoSection
import com.capricsid.hospitaldutyroster.model.sortedForRoster
import org.json.JSONArray
import org.json.JSONObject

class StaffRepository(context: Context) {
    private val preferences = context.getSharedPreferences("hospital_duty_roster", Context.MODE_PRIVATE)

    fun loadStaff(): List<StaffMember> {
        val raw = preferences.getString(KEY_STAFF, null) ?: return canonicalStaff().also { saveStaff(it) }
        val loaded = runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toStaffMember())
                }
            }
        }.getOrElse { canonicalStaff() }

        return if (preferences.getInt(KEY_STAFF_VERSION, 0) < CURRENT_STAFF_VERSION) {
            migrateToCurrentStaff(loaded).also { saveStaff(it) }
        } else {
            loaded.sortedForRoster()
        }
    }

    fun saveStaff(staff: List<StaffMember>) {
        val json = JSONArray()
        staff.forEach { member ->
            json.put(member.toJson())
        }
        preferences.edit()
            .putString(KEY_STAFF, json.toString())
            .putInt(KEY_STAFF_VERSION, CURRENT_STAFF_VERSION)
            .apply()
    }

    private fun migrateToCurrentStaff(existing: List<StaffMember>): List<StaffMember> {
        val existingByName = existing.associateBy { it.name.uppercase() }
        return canonicalStaff().map { canonical ->
            val saved = existingByName[canonical.name]
            if (saved == null) {
                canonical
            } else {
                canonical.copy(
                    id = saved.id,
                    employeeCode = saved.employeeCode.ifBlank { canonical.employeeCode },
                    displayOrder = saved.displayOrder.takeIf { it != Int.MAX_VALUE } ?: canonical.displayOrder,
                    active = saved.active
                )
            }
        }.sortedForRoster()
    }

    private fun canonicalStaff(): List<StaffMember> = listOf(
        StaffMember(
            name = "ISMAIL",
            employeeCode = "11215",
            staffType = StaffType.TMO,
            section = TmoSection.WARD,
            displayOrder = 0,
            experienceLevel = ExperienceLevel.SENIOR,
            opdCategory = OpdCategory.SENIOR
        ),
        StaffMember(
            name = "IHSAN",
            employeeCode = "11180",
            staffType = StaffType.TMO,
            section = TmoSection.WARD,
            displayOrder = 1,
            experienceLevel = ExperienceLevel.SENIOR,
            opdCategory = OpdCategory.SENIOR
        ),
        StaffMember(
            name = "AIMAN",
            employeeCode = "11071",
            staffType = StaffType.TMO,
            section = TmoSection.WARD,
            displayOrder = 2,
            experienceLevel = ExperienceLevel.SENIOR,
            ct2Eligible = false,
            reducedNights = true,
            opdCategory = OpdCategory.SENIOR
        ),
        StaffMember(
            name = "SULAIMAN",
            employeeCode = "12191",
            staffType = StaffType.TMO,
            section = TmoSection.WARD,
            displayOrder = 3,
            experienceLevel = ExperienceLevel.MID
        ),
        StaffMember(
            name = "IZAZ",
            employeeCode = "12634",
            staffType = StaffType.TMO,
            section = TmoSection.WARD,
            displayOrder = 4,
            experienceLevel = ExperienceLevel.JUNIOR,
            reducedNights = true,
            opdCategory = OpdCategory.NEW_TMO
        ),
        StaffMember(
            name = "ASIM",
            employeeCode = "13108",
            staffType = StaffType.TMO,
            section = TmoSection.WARD,
            displayOrder = 5,
            experienceLevel = ExperienceLevel.JUNIOR,
            reducedNights = true,
            opdCategory = OpdCategory.NEW_TMO
        ),
        StaffMember(
            name = "IHTESHAM",
            employeeCode = "13014",
            staffType = StaffType.TMO,
            section = TmoSection.WARD,
            displayOrder = 6,
            experienceLevel = ExperienceLevel.JUNIOR,
            reducedNights = true,
            opdCategory = OpdCategory.NEW_TMO
        ),
        StaffMember(
            name = "ABBAS",
            employeeCode = "11219",
            staffType = StaffType.TMO,
            section = TmoSection.NURSERY,
            displayOrder = 7,
            experienceLevel = ExperienceLevel.SENIOR,
            weekendPreferenceOff = true,
            opdCategory = OpdCategory.SENIOR
        ),
        StaffMember(
            name = "ROMAN",
            employeeCode = "11220",
            staffType = StaffType.TMO,
            section = TmoSection.NURSERY,
            displayOrder = 8,
            experienceLevel = ExperienceLevel.SENIOR,
            weekendPreferenceOff = true,
            opdCategory = OpdCategory.SENIOR
        ),
        StaffMember(
            name = "WASEEM",
            employeeCode = "11179",
            staffType = StaffType.TMO,
            section = TmoSection.NURSERY,
            displayOrder = 9,
            experienceLevel = ExperienceLevel.SENIOR,
            opdCategory = OpdCategory.SENIOR
        ),
        StaffMember(
            name = "UMER",
            employeeCode = "12613",
            staffType = StaffType.TMO,
            section = TmoSection.NURSERY,
            displayOrder = 10,
            experienceLevel = ExperienceLevel.MID
        ),
        StaffMember(
            name = "HASSAN",
            employeeCode = "12635",
            staffType = StaffType.TMO,
            section = TmoSection.NURSERY,
            displayOrder = 11,
            experienceLevel = ExperienceLevel.JUNIOR,
            reducedNights = true,
            opdCategory = OpdCategory.NEW_TMO
        ),
        StaffMember(
            name = "ATEEQ",
            employeeCode = "13020",
            staffType = StaffType.TMO,
            section = TmoSection.NURSERY,
            displayOrder = 12,
            experienceLevel = ExperienceLevel.JUNIOR,
            reducedNights = true,
            opdCategory = OpdCategory.NEW_TMO
        ),
        StaffMember(
            name = "GOHAR",
            displayOrder = 13,
            staffType = StaffType.HO,
            opdCategory = OpdCategory.NEW_TMO
        ),
        StaffMember(
            name = "OWAIS",
            displayOrder = 14,
            staffType = StaffType.HO,
            opdCategory = OpdCategory.NEW_TMO
        )
    ).sortedForRoster()

    private fun StaffMember.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("employeeCode", employeeCode)
        put("staffType", staffType.name)
        put("section", section?.name)
        put("displayOrder", displayOrder)
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
        displayOrder = optInt("displayOrder", Int.MAX_VALUE),
        experienceLevel = ExperienceLevel.valueOf(optString("experienceLevel", ExperienceLevel.MID.name)),
        ct2Eligible = optBoolean("ct2Eligible", true),
        reducedNights = optBoolean("reducedNights", false),
        weekendPreferenceOff = optBoolean("weekendPreferenceOff", false),
        opdCategory = OpdCategory.valueOf(optString("opdCategory", OpdCategory.GENERAL.name)),
        active = optBoolean("active", true)
    )

    private companion object {
        const val KEY_STAFF = "master_staff"
        const val KEY_STAFF_VERSION = "master_staff_version"
        const val CURRENT_STAFF_VERSION = 3
    }
}

