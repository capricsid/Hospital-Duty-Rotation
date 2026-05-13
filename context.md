# Hospital Duty Rotation Context

## Purpose

This project generates a monthly pediatric duty roster that follows the same operational structure and visual layout seen in the existing Excel rosters.

The roster cycle runs from the `24th` of one month through the `23rd` of the next month.

This document rewrites the original rule draft so it matches the attached roster files more closely:

- `NEW ROSTER FOR APRIL MAY26.xlsx`
- `ROSTER FOR JAN FEB25.xlsx`
- `ROSTER FOR OCT NOV 25.xlsx`

## Design Principle

The app should be:

- rule-based, not score-based
- configurable where patterns vary month to month
- strict on hard constraints
- flexible on load balancing targets
- faithful to the existing Excel roster structure

## What Is Verified From Existing Rosters

The current Excel files consistently show:

- one main worksheet, not a multi-sheet workbook
- date columns from `24 -> 23`
- sections for `WARD TMOS`, `NURSERY TMOS`, and `HOUSE OFFICER`
- duty codes `M`, `E`, `N`, `CT1`, `CT2`, `O`, `L`
- an `OPD ROSTER` block on the same sheet
- free-text operational notes on the same sheet
- occasional merged multi-day rotation blocks such as `ROTATION` or `PROJECT 2 ROTATION`
- no visible consecutive night duties for the same TMO in the sample files

The files also show that some assumptions in the original draft are not always true:

- `CT2` is not always limited to one per TMO
- total hard-duty load is not always `8`; some months are closer to `9-10`
- HO scheduling differs between 2-HO and 3-HO months
- weekday and weekend morning coverage depends on available staffing, not one fixed number for every month

## Core Data Model

### Staff Types

There are two scheduling groups:

- `TMO`
- `HO`

TMOs are visually grouped in the sheet as:

- `WARD TMOS`
- `NURSERY TMOS`

These section labels are part of the exported format and should be preserved.

### Staff Persistence

Staff should be stored as a persistent master list inside the app, not re-entered from scratch each month.

Required behavior:

- once a TMO or HO is entered, the record remains available for future rosters
- new monthly rosters should start by loading the saved staff list
- the user can add new staff when needed
- the user can temporarily exclude a staff member from a specific month without deleting them from the master list
- the user can edit staff details such as name, employee code, section, and eligibility flags
- the user can mark staff as inactive if they should no longer appear in future rosters by default

Recommended stored fields for persistent staff records:

- `id`
- `name`
- `employeeCode`
- `staffType`: `TMO` or `HO`
- `section` if TMO
- default eligibility flags
- default OPD category
- active/inactive status

Monthly roster generation should then use:

- master staff list
- month-specific inclusion/exclusion
- month-specific leave and rotation data
- month-specific overrides

### Current Confirmed Staff List

As of the May 2026 app update, the canonical in-app staff list should contain only these doctors unless the user manually adds more later:

- `WARD TMOS`: `ISMAIL`, `IHSAN`, `AIMAN`, `SULAIMAN`, `IZAZ`, `ASIM`, `IHTESHAM`
- `NURSERY TMOS`: `ABBAS`, `ROMAN`, `WASEEM`, `UMER`, `HASSAN`, `ATEEQ`
- `HOUSE OFFICERS`: `GOHAR`, `OWAIS`

Default rule flags from the latest explanation:

- `ISMAIL`, `IHSAN`, `AIMAN`, `ABBAS`, `ROMAN`, and `WASEEM` are treated as senior TMOs.
- `SULAIMAN` and `UMER` are treated as mid-level TMOs.
- `IZAZ`, `HASSAN`, `ASIM`, `IHTESHAM`, and `ATEEQ` are treated as junior or reduced-night TMOs.
- `AIMAN` is not eligible for `CT2` by default and has reduced nights.
- `ABBAS` and `ROMAN` prefer weekend off because of Saturday/Sunday clinic duties.
- Extra legacy seeded doctors should be removed by migration so the master list starts from the confirmed list.

### TMO Input

Each TMO record should support:

- `name`
- `employeeCode`
- `section`: `WARD` or `NURSERY`
- `experienceLevel`: optional app setting such as `senior`, `mid`, `junior`
- `nightCapacity`: normal or reduced
- `ct2Eligibility`: allowed / restricted
- `weekendPreferenceOff`: optional preference
- `opdEligibility`: `senior`, `new_tmo`, `general`, or custom tags

Note:
The sample Excel files do not explicitly encode seniority. The app may still use this as input, but it must be treated as configurable policy rather than an Excel-proven rule.

### HO Input

Each HO record should support:

- `name`
- `opdEligibility`
- `rotationGroup`: optional, useful when there are 3 HOs

### Availability / Restrictions

For any doctor and date, support:

- `available`
- `leaveCode`
- `blockedReason`
- `rotationBlockLabel`

Examples:

- `L`
- `ROTATION`
- `PROJECT 2 ROTATION`
- custom blocked labels

### Monthly Roster Screen Behavior

The monthly roster screen should distinguish clearly between:

- doctors included in the roster for that month
- doctors who have leave entries for that month

Required behavior:

- active staff may be included in the monthly roster by default
- leave should not appear as pre-selected for every doctor
- each doctor should have an explicit leave toggle or leave checkbox
- leave date input should only be shown or enabled when leave is selected for that doctor
- the user should be able to enter leave as day numbers, explicit dates, or ranges
- excluding a doctor from a month is separate from marking that doctor on leave

## Calendar Rules

Generate a date range from the `24th` through the `23rd`.

For each date store:

- day name
- numeric day
- whether it is `weekday` or `weekend`
- month label for header rendering
- whether the date is an OPD day

Do not hardcode `Emergency Week` or `Cold Week` into the base engine unless the product owner explicitly wants them as an additional planning layer. Those labels were not visible in the attached files.

## Duty Codes

### TMO Duty Codes

- `M` = Morning
- `E` = Evening
- `N` = Night
- `CT1`
- `CT2`
- `O` = Off
- `L` = Leave
- `ROTATION` or other text block labels

### HO Duty Codes

The sample files show HOs mainly using:

- `M`
- `E`
- `CT1`
- `O`
- `L`

No HO night duties were visible in the attached files.

## Hard Constraints

These should be treated as non-negotiable unless the user manually overrides them:

1. A doctor cannot receive more than one duty code on the same date.
2. A doctor cannot be assigned on a leave or blocked date.
3. A doctor inside a rotation block cannot receive normal duties for the blocked span.
4. Consecutive night duties for the same TMO should not be generated.
5. Exported layout must preserve section ordering and same-sheet OPD placement.

## Soft Constraints

These should be optimized where possible, but may be relaxed when staffing is tight:

1. Balance nights across eligible TMOs.
2. Spread CT1 and CT2 fairly.
3. Prefer weekend offs for doctors who request them.
4. Avoid heavy back-to-back combinations when alternatives exist.
5. Keep morning coverage stable across the month.

## Scheduling Strategy

Use a staged constraint-based generator:

1. Pre-block leave and rotation spans.
2. Assign night duties first.
3. Assign CT2 duties.
4. Assign CT1 duties.
5. Assign morning coverage.
6. Assign evening coverage.
7. Fill remaining blanks with `O`.
8. Generate OPD rows.
9. Run cleanup and balancing.

## TMO Scheduling Rules

### 1. Night Duty Assignment

Night duty is the highest-priority TMO assignment.

Observed pattern from the files:

- most days have `1-2` TMOs on `N`
- many days have `2` night duties
- no sample showed the same TMO on consecutive nights

Night assignment rules:

- assign only on available, non-rotation dates
- avoid previous-day night assignment
- prefer TMOs below their target night band
- respect reduced-night settings

Night targets should be configurable bands, not fixed constants. Suggested defaults:

- high-capacity TMO: `5-6` nights
- standard TMO: `4-5` nights
- reduced-night TMO: `3-4` nights

These are starting points only and should scale with:

- number of active TMOs that month
- leave load
- rotation blocks

### 2. CT2 Assignment

The original rule `CT2 <= 1` does not match every roster file.

Corrected rule:

- each TMO has a configurable `CT2 target range`
- the default may be `0-1`
- the generator may assign `2` when staffing and historical pattern require it

Observed sample behavior:

- some months have many TMOs with `1` CT2
- Jan-Feb 2025 includes several TMOs with `2` CT2 assignments

CT2 assignment rules:

- assign only to eligible TMOs
- prefer dates without adjacent overload
- prefer dates where day coverage benefits from CT2
- do not place inside leave or rotation blocks

### 3. CT1 Assignment

CT1 is a regular counted TMO duty and appears frequently.

Observed sample behavior:

- many TMOs receive `1-3` CT1 duties
- CT1 volume rises when nights are lower for a given TMO

CT1 assignment rules:

- assign after nights and CT2
- favor TMOs whose hard-duty totals are still below target
- avoid stacking with adjacent heavy-duty dates when possible

### 4. Morning Assignment

Morning coverage must be dynamic.

The attached files do not support one universal rule like:

- `Mon-Fri always 5-7`
- `Weekend always 4 or 5`

Instead, morning coverage changes by month depending on available staff.

Use dynamic coverage targets:

- weekday morning coverage: aim for a configurable range, usually around `4-7`
- weekend morning coverage: aim for a configurable range, usually around `3-5`
- allow a wider range when multiple TMOs are on leave or rotation

For assignment priority:

- prefer available TMOs not already on `N`, `CT1`, or `CT2`
- prefer lower hard-duty totals
- maintain section balance where practical

### 5. Evening Assignment

Evening is lower priority than `N`, `CT1`, `CT2`, and minimum morning coverage.

Assign `E` to remaining available TMOs:

- after minimum day coverage is satisfied
- with fair spread across the month
- while avoiding overload where possible

### 6. Off Assignment

After required duties are assigned, remaining empty cells become `O`.

Do not assign `O` over:

- `L`
- rotation blocks
- manually blocked labels

## HO Scheduling Rules

HO scheduling should be driven by actual roster patterns, not by the original emergency/cold-week model.

### Verified HO Patterns

From the sample files:

- HOs mainly rotate among `M`, `E`, `CT1`, `O`, and `L`
- in 2-HO months, weekend `CT1/O` alternation is common
- in 3-HO months, CT1 rotation is more distributed and less predictable
- some weekends have no HO on `M` because one HO is `CT1` and the other is `O`

### Corrected HO Rules

1. At least one HO should usually be on daytime coverage, but `CT1` may itself satisfy operational daytime presence.
2. Do not require a separate HO `M` on every single day if roster style intentionally uses `CT1/O` on some weekends.
3. Use distinct templates based on HO count:

- 2-HO template:
  - alternate weekend `CT1` between the two HOs
  - the non-CT1 HO is usually `O`
  - fill weekdays with a mix of `M` and `E`

- 3-HO template:
  - rotate `CT1` among the HOs
  - preserve fairness over the full cycle
  - permit one HO leave block without collapsing the pattern

4. HO night duties are not part of the base generator unless explicitly introduced later.

## OPD Scheduling Rules

OPD should be exported on the same worksheet below the HO section.

Observed formats include:

- `OPD (HOS)`
- `OPD (TMOS)`
- `OPD (HOS/NEW TMO)`
- `OPD (SENIOR TMOS)`

The app should support configurable OPD tracks instead of a single fixed layout.

Recommended model:

- each OPD track has:
  - label
  - eligible staff pool
  - date list
  - whether split assignments like `ARSHAD/UMER` are allowed

Rules:

1. OPD dates are explicitly generated and stored.
2. OPD assignments should come from eligible pools only.
3. OPD rows are part of export formatting, not part of the main duty grid counts unless product rules say otherwise.

## Rotation Blocks

Rotation periods must be modeled as blocks, not just repeated daily strings.

Behavior:

- a rotation block can span multiple consecutive dates
- the exported sheet should render it as a merged cell where appropriate
- the generator must treat the whole span as unavailable for normal duties

Examples seen in the attached files:

- `ROTATION`
- `PROJECT 2 ROTATION`

## Load Balancing Model

Do not force one universal `TotalUnits = 8`.

Use flexible targets:

- `hardDutyCount = N + CT1 + CT2`
- each TMO gets a target band based on active month capacity
- target bands should be recalculated after leave and rotation blocking

Suggested default hard-duty target bands:

- full-availability TMO in larger pool: `7-8`
- full-availability TMO in tighter month: `8-10`
- partial-availability TMO: prorated target

The engine should balance within a band instead of one fixed number.

## Summary Calculation

The app must calculate summary values itself from actual assignments.

Do not rely on copied workbook totals as source truth.

For each TMO compute:

- `NightCount`
- `CT1Count`
- `CT2Count`
- `OffCount`
- `HardDutyCount = N + CT1 + CT2`

Optional:

- `MorningCount`
- `EveningCount`
- `LeaveDays`

Note:
The attached files contain right-side summary numbers in some months, but they are not always consistent with the visible duty grid. App-generated counts should always be derived directly from assignments.

## Export Format

### Workbook Shape

Default export should match the sample style:

- one worksheet

The goal is not only to export roster data to Excel, but to reproduce the same practical layout style as the existing roster files as closely as possible.

### Sheet Structure

Top section:

- title row
- weekday header row
- date-number row

Main roster body:

- `WARD TMOS`
- TMO rows
- `NURSERY TMOS`
- TMO rows

Notes section:

- operational notes text

HO section:

- `HOUSE OFFICER`
- HO rows

OPD section:

- `OPD ROSTER`
- OPD date headers
- OPD assignment rows

Optional right-side summary columns:

- `N`
- `CT1`
- `CT2`
- `OFF`
- `L`
- `TOTAL DUTIES` or `HARD DUTIES`

Footer:

- signature labels if required

### Rendering Requirements

The export layer should support:

- merged title cell
- merged section labels
- merged multi-day rotation cells
- centered duty labels
- visible section separation
- preserved OPD block formatting

### Excel Fidelity Requirements

The generated roster should look like the current Excel files in structure and reading flow.

That means the export should preserve or reproduce:

- the same top-to-bottom section order
- weekday and numeric date headers across the `24 -> 23` span
- separate `WARD TMOS` and `NURSERY TMOS` labels
- `HOUSE OFFICER` section below the TMO roster
- `OPD ROSTER` block on the same worksheet
- operational note rows on the same sheet
- optional summary columns on the right where needed
- merged cells for title, section labels, and multi-day rotation spans

When summary columns are shown, they should be exported as separate right-side columns rather than one combined text field.

The order should remain close to the real files:

1. title
2. weekday header
3. date-number header
4. `WARD TMOS`
5. `NURSERY TMOS`
6. operational notes
7. `HOUSE OFFICER`
8. `OPD ROSTER`

The app should treat the existing roster files as the visual template baseline.

If formatting options are configurable, the default export must still produce a workbook that resembles the attached Excel rosters closely enough that staff can use it without changing their current workflow.

## APK Distribution

Android builds should produce a distributable APK as part of the project workflow.

Required behavior:

- keep a dedicated output folder inside the repo for generated APK files
- current APK output folder: `Hospital Duty Roster APK/`
- copy or place the generated APK there after a successful build
- use versioned APK filenames when practical so builds are easy to identify

Recommended filename style:

- `hospital-duty-roster-v1.0.0.apk`
- `hospital-duty-roster-v1.0.1.apk`

## Versioning And Git Workflow

This project should be managed as a Git-first codebase.

Required behavior:

- keep context updates in Git
- keep app code changes in Git
- bump the Android app version for each meaningful distributable APK build
- commit version changes together with the code that produced the APK

Recommended Android versioning:

- `versionName` for human-readable release number
- `versionCode` for internal Android build increment

Suggested release workflow:

1. update context and code
2. increment `versionCode`
3. update `versionName` when appropriate
4. build APK
5. copy APK into `Hospital Duty Roster APK/`
6. commit and push the release changes

## Manual Override Support

The app should support post-generation editing:

- override a duty cell
- block a date manually
- rerun balancing without deleting locked cells

Locked assignments should be treated as fixed constraints on regeneration.

## Validation Pass

Before export, run cleanup checks:

1. no duplicate same-day assignments
2. no duty on blocked dates
3. no night-to-night consecutive sequence
4. summary counts match actual cells
5. rotation spans are preserved correctly
6. OPD rows only use eligible staff

## Crash Fix Notes

### Version 1.0.3

- Fixed a crash that could happen immediately after tapping `Create roster`.
- The created roster preview no longer uses a nested vertical scroll container inside the main roster list.
- The `Create roster` action now catches generation errors and shows a toast instead of crashing the app.
- A new APK should be copied to `Hospital Duty Roster APK/` for this fix.

## Export Fix Notes

### Version 1.0.6

- Fixed a corrupted Excel export caused by a malformed root `Workbook` XML tag.
- The spreadsheet export should now open as a valid workbook instead of being rejected as damaged.
- A new APK should be copied to `Hospital Duty Roster APK/` for this fix.

### Version 1.0.7

- Added the confirmed TMO employee codes from the validated roster sample to the canonical staff list.
- Preserved saved roster ordering so the export no longer falls back to alphabetical ordering.
- Updated Excel export layout to better match the real roster sheet:
  - TMO summary columns now follow the sample shape more closely with `N`, `CT1`, `CT2`, `OFF`, and `TOTAL DUTIES`
  - HO rows no longer force the same summary values on the right
  - operational notes are exported as plain rows between TMOs and HOs instead of a separate labeled block
  - the OPD block stays on the same worksheet
  - footer signature lines are included again
- A new APK should be copied to `Hospital Duty Roster APK/` for this release.

### Version 1.0.8

- Updated the 2-HO scheduling template so both house officers receive a balanced weekend `CT1/O` rotation and a more natural weekday `M/E` split.
- Replaced generic exported rule notes with the two operational note lines used in the validated Excel roster style.
- Split OPD export into smaller same-sheet OPD blocks so the layout reads closer to the reference workbook instead of one long compact strip.
- A new APK should be copied to `Hospital Duty Roster APK/` for this release.

### Version 1.0.9

- Updated OPD export formatting to use merged date and assignment cells inside the same-sheet OPD blocks.
- The OPD section now visually follows the reference workbook more closely while preserving generated OPD assignments.
- A new APK should be copied to `Hospital Duty Roster APK/` for this release.

### Version 1.0.10

- Replaced the old XML Spreadsheet `.xls` export with a real `.xlsx` workbook package.
- Updated the Android document picker MIME type and default export filename to `.xlsx`.
- This should remove Excel's warning that the file format and extension do not match.
- A new APK should be copied to `Hospital Duty Roster APK/` for this release.

## Recommended Generation Flow

1. Load staff, sections, and eligibility.
2. Build the `24 -> 23` calendar.
3. Apply leave and rotation blocks.
4. Calculate active staffing capacity for the month.
5. Derive target bands for `N`, `CT1`, and `CT2`.
6. Assign TMO nights.
7. Assign CT2.
8. Assign CT1.
9. Fill required morning coverage.
10. Fill evening coverage.
11. Generate HO schedule using 2-HO or 3-HO template logic.
12. Generate OPD assignments.
13. Fill remaining blanks with `O`.
14. Run validation and balancing.
15. Export a single-sheet Excel roster in the same structure as the sample files.

## Implementation Notes

Good first implementation approach:

- deterministic rule engine
- small helper functions for each assignment phase
- explicit constraint checks before every placement
- export builder separated from scheduling engine

Suggested internal modules:

- `calendar`
- `staff`
- `availability`
- `assignment.nights`
- `assignment.ct`
- `assignment.day`
- `assignment.ho`
- `assignment.opd`
- `validation`
- `export.excel`

## Product Boundary

This document intentionally reflects the roster files that were reviewed.

Rules that may exist in real-world practice but were not visible in those files should remain configurable, not hardcoded, until confirmed by the product owner.
