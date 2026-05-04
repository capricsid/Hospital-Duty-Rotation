package com.capricsid.hospitaldutyroster.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

enum class StaffType {
    TMO,
    HO
}

enum class TmoSection {
    WARD,
    NURSERY
}

enum class ExperienceLevel {
    SENIOR,
    MID,
    JUNIOR
}

enum class OpdCategory(val label: String) {
    GENERAL("General"),
    NEW_TMO("New TMO"),
    SENIOR("Senior")
}

data class StaffMember(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val employeeCode: String = "",
    val staffType: StaffType,
    val section: TmoSection? = null,
    val experienceLevel: ExperienceLevel = ExperienceLevel.MID,
    val ct2Eligible: Boolean = true,
    val reducedNights: Boolean = false,
    val weekendPreferenceOff: Boolean = false,
    val opdCategory: OpdCategory = OpdCategory.GENERAL,
    val active: Boolean = true
)

data class RosterDay(
    val date: LocalDate,
    val dayLabel: String,
    val dayNumber: String
)

data class PreviewRow(
    val label: String,
    val badge: String = "",
    val cells: List<String>,
    val summary: String = ""
)

data class OpdTrack(
    val label: String,
    val dates: List<String>,
    val placeholder: String
)

data class RosterPreview(
    val title: String,
    val days: List<RosterDay>,
    val wardRows: List<PreviewRow>,
    val nurseryRows: List<PreviewRow>,
    val hoRows: List<PreviewRow>,
    val notes: List<String>,
    val opdTracks: List<OpdTrack>
)

fun buildRosterPreview(
    rosterMonth: YearMonth,
    activeStaff: List<StaffMember>
): RosterPreview {
    val start = rosterMonth.atDay(24)
    val end = rosterMonth.plusMonths(1).atDay(23)
    val days = generateSequence(start) { current ->
        if (current >= end) {
            null
        } else {
            current.plusDays(1)
        }
    }.toList().map { date ->
        RosterDay(
            date = date,
            dayLabel = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(Locale.ENGLISH),
            dayNumber = date.dayOfMonth.toString()
        )
    }

    val blankCells = List(days.size) { "--" }
    val activeTmos = activeStaff.filter { it.active && it.staffType == StaffType.TMO }
    val activeHos = activeStaff.filter { it.active && it.staffType == StaffType.HO }

    val wardRows = activeTmos
        .filter { it.section == TmoSection.WARD }
        .sortedBy { it.name }
        .map { staff ->
            PreviewRow(
                label = staff.name,
                badge = staff.employeeCode,
                cells = blankCells,
                summary = summarizeStaff(staff)
            )
        }

    val nurseryRows = activeTmos
        .filter { it.section == TmoSection.NURSERY }
        .sortedBy { it.name }
        .map { staff ->
            PreviewRow(
                label = staff.name,
                badge = staff.employeeCode,
                cells = blankCells,
                summary = summarizeStaff(staff)
            )
        }

    val hoRows = activeHos
        .sortedBy { it.name }
        .map { staff ->
            PreviewRow(
                label = staff.name,
                cells = blankCells,
                summary = staff.opdCategory.label
            )
        }

    val opdDates = days.filter { it.date.dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY) }
        .take(10)
        .map { "${it.dayLabel} ${it.dayNumber}" }

    val title = buildString {
        append("PAEDIATRIC HO'S & TMO'S ROSTER FROM ")
        append("${ordinal(start.dayOfMonth)} ${start.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).uppercase(Locale.ENGLISH)} ")
        append("TO ${ordinal(end.dayOfMonth)} ${end.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).uppercase(Locale.ENGLISH)} ")
        append(end.year)
    }

    return RosterPreview(
        title = title,
        days = days,
        wardRows = wardRows,
        nurseryRows = nurseryRows,
        hoRows = hoRows,
        notes = listOf(
            "After night duty, TMOs and HOs can leave the ward next morning after carrying out round orders till 1pm.",
            "On CT2 duty, TMOs stay in the ward till 3pm."
        ),
        opdTracks = listOf(
            OpdTrack(
                label = "OPD (HOS/NEW TMO)",
                dates = opdDates,
                placeholder = "Assign from HOs and new TMOs"
            ),
            OpdTrack(
                label = "OPD (SENIOR TMOS)",
                dates = opdDates,
                placeholder = "Assign from senior TMOs"
            )
        )
    )
}

private fun summarizeStaff(staff: StaffMember): String {
    val parts = mutableListOf<String>()
    if (staff.staffType == StaffType.TMO) {
        parts += staff.experienceLevel.name
        if (!staff.ct2Eligible) {
            parts += "NO CT2"
        }
        if (staff.reducedNights) {
            parts += "REDUCED N"
        }
        if (staff.weekendPreferenceOff) {
            parts += "WEEKEND OFF PREF"
        }
    }
    parts += staff.opdCategory.label
    return parts.joinToString(" • ")
}

private fun ordinal(day: Int): String = when {
    day % 100 in 11..13 -> "${day}TH"
    day % 10 == 1 -> "${day}ST"
    day % 10 == 2 -> "${day}ND"
    day % 10 == 3 -> "${day}RD"
    else -> "${day}TH"
}

