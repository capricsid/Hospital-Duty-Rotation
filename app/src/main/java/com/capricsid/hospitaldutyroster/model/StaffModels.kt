package com.capricsid.hospitaldutyroster.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

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
    val displayOrder: Int = Int.MAX_VALUE,
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
) {
    val isWeekend: Boolean = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
}

data class PreviewRow(
    val staffId: String = "",
    val label: String,
    val badge: String = "",
    val cells: List<String>
)

data class OpdTrack(
    val label: String,
    val dates: List<String>,
    val assignments: List<String>
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

data class RosterRequest(
    val rosterMonth: YearMonth,
    val staff: List<StaffMember>,
    val includedStaffIds: Set<String>,
    val leavesByStaffId: Map<String, Set<LocalDate>>
)

fun buildRosterPreview(
    rosterMonth: YearMonth,
    activeStaff: List<StaffMember>
): RosterPreview = buildRoster(
    RosterRequest(
        rosterMonth = rosterMonth,
        staff = activeStaff,
        includedStaffIds = activeStaff.filter { it.active }.map { it.id }.toSet(),
        leavesByStaffId = emptyMap()
    )
)

fun buildRoster(request: RosterRequest): RosterPreview {
    val days = buildRosterDays(request.rosterMonth)
    val includedStaff = request.staff
        .filter { it.active && it.id in request.includedStaffIds }
        .sortedForRoster()
    val tmos = includedStaff.filter { it.staffType == StaffType.TMO }
    val hos = includedStaff.filter { it.staffType == StaffType.HO }
    val cellsByStaff = includedStaff.associate { it.id to MutableList(days.size) { "" } }.toMutableMap()

    applyLeaves(days, cellsByStaff, request.leavesByStaffId)
    assignTmoNights(days, tmos, cellsByStaff)
    assignTmoDutyTargets(days, tmos, cellsByStaff, "CT2") { member ->
        if (!member.ct2Eligible) 0 else 1
    }
    assignTmoDutyTargets(days, tmos, cellsByStaff, "CT1") { member ->
        when {
            member.experienceLevel == ExperienceLevel.SENIOR && !member.reducedNights -> 1
            member.experienceLevel == ExperienceLevel.MID -> 2
            else -> 3
        }
    }
    fillTmoMorningCoverage(days, tmos, cellsByStaff)
    fillTmoEvenings(days, tmos, cellsByStaff)
    fillRemaining(tmos, cellsByStaff)
    assignHouseOfficerDuties(days, hos, cellsByStaff)
    fillRemaining(hos, cellsByStaff)

    val wardRows = tmos
        .filter { it.section == TmoSection.WARD }
        .map { it.toPreviewRow(cellsByStaff.getValue(it.id)) }
    val nurseryRows = tmos
        .filter { it.section == TmoSection.NURSERY }
        .map { it.toPreviewRow(cellsByStaff.getValue(it.id)) }
    val hoRows = hos.map { it.toPreviewRow(cellsByStaff.getValue(it.id)) }
    val opdTracks = buildOpdTracks(days, includedStaff, cellsByStaff)

    return RosterPreview(
        title = rosterTitle(days.first().date, days.last().date),
        days = days,
        wardRows = wardRows,
        nurseryRows = nurseryRows,
        hoRows = hoRows,
        notes = listOf(
            "After night duty, TMOs and HOs can leave the ward next morning after carrying out round orders till 1pm.",
            "Same apply for sundays. On CT2 duty, TMOs has to stay in ward till 3pm."
        ),
        opdTracks = opdTracks
    )
}

fun parseLeaveDates(input: String, rosterMonth: YearMonth): Set<LocalDate> {
    val cycleDates = buildRosterDays(rosterMonth).map { it.date }.toSet()
    return input
        .split(',', '\n', ';')
        .flatMap { token ->
            val clean = token.trim()
            when {
                clean.isBlank() -> emptyList()
                clean.contains("..") -> parseRange(clean, "..", rosterMonth)
                clean.contains("-") && clean.count { it == '-' } == 1 -> parseRange(clean, "-", rosterMonth)
                else -> listOfNotNull(parseSingleDate(clean, rosterMonth))
            }
        }
        .filter { it in cycleDates }
        .toSet()
}

private fun parseRange(input: String, separator: String, rosterMonth: YearMonth): List<LocalDate> {
    val parts = input.split(separator).map { it.trim() }
    if (parts.size != 2) return emptyList()
    val start = parseSingleDate(parts[0], rosterMonth) ?: return emptyList()
    val end = parseSingleDate(parts[1], rosterMonth) ?: return emptyList()
    if (end < start) return emptyList()
    return generateSequence(start) { current -> if (current >= end) null else current.plusDays(1) }.toList()
}

private fun parseSingleDate(input: String, rosterMonth: YearMonth): LocalDate? {
    input.toIntOrNull()?.let { day ->
        val month = if (day >= 24) rosterMonth else rosterMonth.plusMonths(1)
        return runCatching { month.atDay(day) }.getOrNull()
    }
    return runCatching { LocalDate.parse(input) }.getOrNull()
}

fun defaultExportFileName(rosterMonth: YearMonth): String =
    "hospital-duty-roster-${rosterMonth.month.name.lowercase()}-${rosterMonth.year}.xlsx"

private fun buildRosterDays(rosterMonth: YearMonth): List<RosterDay> {
    val start = rosterMonth.atDay(24)
    val end = rosterMonth.plusMonths(1).atDay(23)
    return generateSequence(start) { current ->
        if (current >= end) null else current.plusDays(1)
    }.map { date ->
        RosterDay(
            date = date,
            dayLabel = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(Locale.ENGLISH),
            dayNumber = date.dayOfMonth.toString()
        )
    }.toList()
}

private fun applyLeaves(
    days: List<RosterDay>,
    cellsByStaff: Map<String, MutableList<String>>,
    leavesByStaffId: Map<String, Set<LocalDate>>
) {
    days.forEachIndexed { index, day ->
        leavesByStaffId.forEach { (staffId, leaves) ->
            if (day.date in leaves) {
                cellsByStaff[staffId]?.set(index, "L")
            }
        }
    }
}

private fun assignTmoNights(
    days: List<RosterDay>,
    tmos: List<StaffMember>,
    cellsByStaff: Map<String, MutableList<String>>
) {
    val nightCounts = tmos.associate { it.id to 0 }.toMutableMap()
    val slotsPerDay = if (tmos.size >= 8) 2 else 1
    days.indices.forEach { dayIndex ->
        repeat(slotsPerDay) {
            val candidate = tmos
                .filter { member -> canAssign(cellsByStaff, member.id, dayIndex) }
                .filterNot { member -> cellsByStaff[member.id]?.getOrNull(dayIndex - 1) == "N" }
                .minWithOrNull(
                    compareBy<StaffMember>(
                        { nightCounts.getValue(it.id).toDouble() / max(1, nightTarget(it)) },
                        { if (days[dayIndex].isWeekend && it.weekendPreferenceOff) 1 else 0 },
                        { dayIndexOffset(it, dayIndex) }
                    )
                )
            if (candidate != null && nightCounts.getValue(candidate.id) < nightTarget(candidate)) {
                cellsByStaff.getValue(candidate.id)[dayIndex] = "N"
                nightCounts[candidate.id] = nightCounts.getValue(candidate.id) + 1
            }
        }
    }
}

private fun assignTmoDutyTargets(
    days: List<RosterDay>,
    tmos: List<StaffMember>,
    cellsByStaff: Map<String, MutableList<String>>,
    code: String,
    targetFor: (StaffMember) -> Int
) {
    tmos.sortedBy { dayIndexOffset(it, 0) }.forEach { member ->
        var assigned = 0
        val target = targetFor(member)
        while (assigned < target) {
            val dayIndex = days.indices
                .filter { index -> canAssign(cellsByStaff, member.id, index) }
                .filterNot { index -> hasHeavyDutyNear(cellsByStaff.getValue(member.id), index) }
                .minWithOrNull(compareBy({ codeCountForDay(cellsByStaff, tmos, code, it) }, { dayIndexOffset(member, it) }))
                ?: days.indices
                    .filter { index -> canAssign(cellsByStaff, member.id, index) }
                    .minWithOrNull(compareBy({ codeCountForDay(cellsByStaff, tmos, code, it) }, { dayIndexOffset(member, it) }))
            if (dayIndex == null) {
                break
            }
            cellsByStaff.getValue(member.id)[dayIndex] = code
            assigned++
        }
    }
}

private fun fillTmoMorningCoverage(
    days: List<RosterDay>,
    tmos: List<StaffMember>,
    cellsByStaff: Map<String, MutableList<String>>
) {
    days.forEachIndexed { index, day ->
        val target = min(
            tmos.count { member -> cellsByStaff.getValue(member.id)[index] != "L" },
            when {
                day.date.dayOfWeek == DayOfWeek.WEDNESDAY -> 7
                day.isWeekend -> 4
                else -> 6
            }
        )
        while (morningCoverage(cellsByStaff, tmos, index) < target) {
            val candidate = tmos
                .filter { member -> canAssign(cellsByStaff, member.id, index) }
                .minWithOrNull(compareBy({ countCode(cellsByStaff.getValue(it.id), "M") }, { dayIndexOffset(it, index) }))
                ?: break
            cellsByStaff.getValue(candidate.id)[index] = "M"
        }
    }
}

private fun fillTmoEvenings(
    days: List<RosterDay>,
    tmos: List<StaffMember>,
    cellsByStaff: Map<String, MutableList<String>>
) {
    days.indices.forEach { index ->
        val candidate = tmos
            .filter { member -> canAssign(cellsByStaff, member.id, index) }
            .minWithOrNull(compareBy({ countCode(cellsByStaff.getValue(it.id), "E") }, { dayIndexOffset(it, index) }))
        if (candidate != null) {
            cellsByStaff.getValue(candidate.id)[index] = "E"
        }
    }
}

private fun assignHouseOfficerDuties(
    days: List<RosterDay>,
    hos: List<StaffMember>,
    cellsByStaff: Map<String, MutableList<String>>
) {
    if (hos.isEmpty()) return
    if (hos.size == 2) {
        assignTwoHouseOfficerTemplate(days, hos, cellsByStaff)
        return
    }

    val ct1Counts = hos.associate { it.id to 0 }.toMutableMap()
    val ct1Target = 4
    val preferredCt1Days = days.indices.filter { days[it].isWeekend } + days.indices.filterNot { days[it].isWeekend }

    preferredCt1Days.forEach { index ->
        if (ct1Counts.values.sum() >= ct1Target) return@forEach
        val candidate = hos
            .filter { member -> canAssign(cellsByStaff, member.id, index) }
            .minWithOrNull(compareBy({ ct1Counts.getValue(it.id) }, { dayIndexOffset(it, index) }))
        if (candidate != null) {
            cellsByStaff.getValue(candidate.id)[index] = "CT1"
            ct1Counts[candidate.id] = ct1Counts.getValue(candidate.id) + 1
        }
    }

    days.indices.forEach { index ->
        val available = hos.filter { member -> cellsByStaff.getValue(member.id)[index].isBlank() }
        available.forEachIndexed { offset, member ->
            cellsByStaff.getValue(member.id)[index] = when {
                days[index].isWeekend && offset > 0 -> "O"
                offset == 0 -> "M"
                else -> "E"
            }
        }
    }
}

private fun assignTwoHouseOfficerTemplate(
    days: List<RosterDay>,
    hos: List<StaffMember>,
    cellsByStaff: Map<String, MutableList<String>>
) {
    val first = hos[0]
    val second = hos[1]
    val weekendDays = days.indices.filter { days[it].isWeekend }
    val ct1WeekendDays = weekendDays.take(8)
    var weekdayRotation = 0
    var extraWeekendRotation = 0

    days.indices.forEach { index ->
        val firstCell = cellsByStaff.getValue(first.id)
        val secondCell = cellsByStaff.getValue(second.id)

        if (firstCell[index].isNotBlank() || secondCell[index].isNotBlank()) {
            return@forEach
        }

        if (index in ct1WeekendDays) {
            val ct1Owner = if (ct1WeekendDays.indexOf(index) % 2 == 0) first else second
            val offOwner = if (ct1Owner.id == first.id) second else first
            cellsByStaff.getValue(ct1Owner.id)[index] = "CT1"
            cellsByStaff.getValue(offOwner.id)[index] = "O"
            return@forEach
        }

        if (days[index].isWeekend) {
            val morningOwner = if (extraWeekendRotation % 2 == 0) first else second
            val offOwner = if (morningOwner.id == first.id) second else first
            cellsByStaff.getValue(morningOwner.id)[index] = "M"
            cellsByStaff.getValue(offOwner.id)[index] = "O"
            extraWeekendRotation++
            return@forEach
        }

        val morningOwner = if (weekdayRotation % 2 == 0) first else second
        val eveningOwner = if (morningOwner.id == first.id) second else first
        cellsByStaff.getValue(morningOwner.id)[index] = "M"
        cellsByStaff.getValue(eveningOwner.id)[index] = "E"
        weekdayRotation++
    }
}

private fun fillRemaining(staff: List<StaffMember>, cellsByStaff: Map<String, MutableList<String>>) {
    staff.forEach { member ->
        cellsByStaff.getValue(member.id).replaceAll { cell -> cell.ifBlank { "O" } }
    }
}

private fun buildOpdTracks(
    days: List<RosterDay>,
    staff: List<StaffMember>,
    cellsByStaff: Map<String, MutableList<String>>
): List<OpdTrack> {
    val opdDayIndexes = days.indices.filter { days[it].date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }
    val seniorTmos = staff.filter { it.staffType == StaffType.TMO && it.opdCategory == OpdCategory.SENIOR }.sortedForRoster()
    val hoAndNew = staff.filter { it.staffType == StaffType.HO || it.opdCategory == OpdCategory.NEW_TMO }.sortedForRoster()

    fun assignmentFor(pool: List<StaffMember>, index: Int): String {
        if (pool.isEmpty()) return ""
        val assignablePool = pool.filter { member ->
            cellsByStaff.getValue(member.id)[index] !in setOf("L", "N", "CT2")
        }.ifEmpty { pool }
        return assignablePool[index % assignablePool.size].name
    }

    return listOf(
        OpdTrack(
            label = "OPD (HOS/NEW TMO)",
            dates = opdDayIndexes.map { "${days[it].dayLabel} ${days[it].dayNumber}" },
            assignments = opdDayIndexes.map { assignmentFor(hoAndNew, it) }
        ),
        OpdTrack(
            label = "OPD (SENIOR TMOS)",
            dates = opdDayIndexes.map { "${days[it].dayLabel} ${days[it].dayNumber}" },
            assignments = opdDayIndexes.map { assignmentFor(seniorTmos, it) }
        )
    )
}

private fun StaffMember.toPreviewRow(cells: List<String>): PreviewRow {
    return PreviewRow(
        staffId = id,
        label = name,
        badge = if (staffType == StaffType.TMO) employeeCode else "",
        cells = cells
    )
}

fun List<StaffMember>.sortedForRoster(): List<StaffMember> = sortedWith(
    compareBy<StaffMember>(
        { if (it.staffType == StaffType.TMO) 0 else 1 },
        { when (it.section) {
            TmoSection.WARD -> 0
            TmoSection.NURSERY -> 1
            null -> 2
        } },
        { it.displayOrder },
        { it.name }
    )
)

private fun canAssign(cellsByStaff: Map<String, MutableList<String>>, staffId: String, dayIndex: Int): Boolean =
    cellsByStaff.getValue(staffId)[dayIndex].isBlank()

private fun nightTarget(member: StaffMember): Int = when {
    member.reducedNights -> 4
    member.experienceLevel == ExperienceLevel.SENIOR -> 6
    member.experienceLevel == ExperienceLevel.MID -> 5
    else -> 4
}

private fun hasHeavyDutyNear(cells: List<String>, index: Int): Boolean =
    listOf(index - 1, index + 1).any { it in cells.indices && cells[it] in setOf("N", "CT1", "CT2") }

private fun morningCoverage(cellsByStaff: Map<String, MutableList<String>>, tmos: List<StaffMember>, dayIndex: Int): Int =
    tmos.count { member -> cellsByStaff.getValue(member.id)[dayIndex] in setOf("M", "CT1", "CT2") }

private fun codeCountForDay(cellsByStaff: Map<String, MutableList<String>>, staff: List<StaffMember>, code: String, dayIndex: Int): Int =
    staff.count { member -> cellsByStaff.getValue(member.id)[dayIndex] == code }

private fun countCode(cells: List<String>, code: String): Int = cells.count { it == code }

private fun dayIndexOffset(member: StaffMember, dayIndex: Int): Int =
    kotlin.math.abs((member.name.hashCode() + dayIndex * 37) % 10_000)

private fun rosterTitle(start: LocalDate, end: LocalDate): String = buildString {
    append("PAEDIATRIC HO'S & TMO'S ROSTER FROM ")
    append("${ordinal(start.dayOfMonth)} ${start.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).uppercase(Locale.ENGLISH)} ")
    append("TO ${ordinal(end.dayOfMonth)} ${end.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).uppercase(Locale.ENGLISH)} ")
    append(end.year)
}

private fun ordinal(day: Int): String = when {
    day % 100 in 11..13 -> "${day}TH"
    day % 10 == 1 -> "${day}ST"
    day % 10 == 2 -> "${day}ND"
    day % 10 == 3 -> "${day}RD"
    else -> "${day}TH"
}
