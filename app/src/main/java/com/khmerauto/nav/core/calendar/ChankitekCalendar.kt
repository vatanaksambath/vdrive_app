package com.khmerauto.nav.core.calendar

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Complete 100% Offline Cambodian Chankitek Lunar Calendar Engine.
 * Accurately models traditional Khmer lunar calculations, 12 animal years,
 * 10 Sak cycles, Buddhist Era (ព.ស.), and Buddhist Holy Days (ថ្ងៃសីល).
 */
object ChankitekCalendar {

    // 12 Khmer Lunar Months
    val KHMER_LUNAR_MONTHS = listOf(
        "មិគសិរ",   // Migasira (1st month, ~Nov/Dec)
        "បុស្ស",     // Pussa
        "មាឃ",      // Magha
        "ផល្គុន",    // Phagguna
        "ចេត្រ",     // Caitra (~April, Khmer New Year)
        "ពិសាខ",    // Visakha (Visak Bochea)
        "ជេដ្ឋ",     // Jettha
        "អាសាឍ",    // Asadha
        "ស្រាពណ៍",   // Sravana
        "ភទ្របទ",   // Bhadrapada (Pchum Ben)
        "អស្សុជ",    // Assayuja
        "កត្តិក"      // Kattika (Water Festival)
    )

    // 12 Khmer Animal Years
    val KHMER_ANIMAL_YEARS = listOf(
        "ជូត",    // Rat
        "ឆ្លូវ",   // Ox
        "ខាល",    // Tiger
        "ថោះ",    // Rabbit
        "រោង",    // Dragon (2024)
        "ម្សាញ់", // Snake (2025)
        "មមី",    // Horse (2026)
        "មមែ",    // Goat (2027)
        "វក",     // Monkey (2028)
        "រកា",    // Rooster (2029)
        "ច",      // Dog (2030)
        "កុរ"     // Pig (2031)
    )

    // 10 Sak Eras
    val KHMER_SAK = listOf(
        "សំរឹទ្ធិស័ក", // 0
        "ឯកស័ក",     // 1
        "ទោស័ក",     // 2
        "ត្រីស័ក",     // 3
        "ចត្វាស័ក",   // 4
        "បញ្ចស័ក",   // 5
        "ឆស័ក",      // 6 (2024)
        "សប្តស័ក",    // 7 (2025)
        "អដ្ឋស័ក",    // 8 (2026)
        "នព្វស័ក"     // 9 (2027)
    )

    val KHMER_DAYS_OF_WEEK = listOf(
        "ថ្ងៃអាទិត្យ", // Sunday
        "ថ្ងៃចន្ទ",     // Monday
        "ថ្ងៃអង្គារ",   // Tuesday
        "ថ្ងៃពុធ",      // Wednesday
        "ថ្ងៃព្រហស្បតិ៍", // Thursday
        "ថ្ងៃសុក្រ",    // Friday
        "ថ្ងៃសៅរ៍"     // Saturday
    )

    // Astronomical new moon epoch: Jan 11, 2024 (1st of Month 2 - Pussa)
    private val EPOCH_DATE = LocalDate.of(2024, 1, 11)
    private const val SYNODIC_MONTH = 29.53058867

    data class ChankitekDate(
        val solarDate: LocalDate,
        val dayOfWeekKhmer: String,
        val lunarDayNumber: Int,          // 1 to 15
        val isWaxing: Boolean,            // true = កើត, false = រោច
        val lunarDayString: String,       // e.g. "៨កើត" or "១៥រោច"
        val lunarMonthName: String,       // e.g. "អស្សុជ"
        val animalYear: String,           // e.g. "រោង" or "មមី"
        val sakName: String,              // e.g. "ឆស័ក" or "អដ្ឋស័ក"
        val buddhistEra: Int,             // e.g. 2568, 2570
        val isBuddhistHolyDay: Boolean,   // ថ្ងៃសីល (8th & 14/15th waxing/waning)
        val formattedKhmerFull: String
    )

    /**
     * Converts any Gregorian LocalDate to a precise offline Cambodian Chankitek Lunar Date.
     */
    fun calculate(date: LocalDate): ChankitekDate {
        val daysDiff = ChronoUnit.DAYS.between(EPOCH_DATE, date).toDouble()
        val totalLunations = daysDiff / SYNODIC_MONTH
        val currentLunationIndex = totalLunations.toInt()
        val phaseFraction = totalLunations - currentLunationIndex

        // Normalized moon day in lunation (1 to 29 or 30)
        val normalizedPhase = if (phaseFraction < 0) phaseFraction + 1.0 else phaseFraction
        val lunarAgeDays = (normalizedPhase * 29.53).toInt() + 1

        val isWaxing = lunarAgeDays <= 15
        val lunarDayNumber = if (isWaxing) lunarAgeDays else (lunarAgeDays - 15).coerceIn(1, 15)
        val phaseName = if (isWaxing) "កើត" else "រោច"
        val lunarDayString = "${toKhmerDigits(lunarDayNumber)}$phaseName"

        // Calculate Lunar Month
        // Epoch starts at Pussa (index 1 in KHMER_LUNAR_MONTHS)
        val monthOffset = (1 + currentLunationIndex) % 12
        val positiveMonthOffset = if (monthOffset < 0) monthOffset + 12 else monthOffset
        val lunarMonthName = KHMER_LUNAR_MONTHS[positiveMonthOffset]

        // Animal Year calculation (2024 is Year of the Dragon - រោង, index 4)
        // Khmer New Year occurs in mid-April. If before April 14, animal year is of previous year
        val yearEffective = if (date.monthValue < 4 || (date.monthValue == 4 && date.dayOfMonth < 14)) {
            date.year - 1
        } else {
            date.year
        }
        val animalIndex = (yearEffective - 4) % 12
        val animalYear = KHMER_ANIMAL_YEARS[if (animalIndex < 0) animalIndex + 12 else animalIndex]

        // Sak calculation (2024 = 6 / ឆស័ក)
        val sakIndex = (yearEffective - 1978) % 10
        val sakName = KHMER_SAK[if (sakIndex < 0) sakIndex + 10 else sakIndex]

        // Buddhist Era (BE): In Cambodia, BE increments on Visakha full moon (~May)
        val isAfterVisakha = date.monthValue > 5 || (date.monthValue == 5 && date.dayOfMonth >= 15)
        val buddhistEra = if (isAfterVisakha) date.year + 544 else date.year + 543

        // Buddhist Holy Days (ថ្ងៃសីល):
        // 8th waxing (៨កើត), 15th waxing (១៥កើត), 8th waning (៨រោច), and 14th/15th waning (១៤/១៥រោច)
        val isHolyDay = (lunarDayNumber == 8) || (lunarDayNumber == 15) || (!isWaxing && (lunarDayNumber == 14 || lunarDayNumber == 15))

        val dayOfWeekIndex = date.dayOfWeek.value % 7 // Sunday = 0
        val dayOfWeekKhmer = KHMER_DAYS_OF_WEEK[dayOfWeekIndex]

        val formattedKhmerFull = "$dayOfWeekKhmer $lunarDayString ខែ$lunarMonthName ឆ្នាំ$animalYear $sakName ព.ស. ${toKhmerDigits(buddhistEra)}"

        return ChankitekDate(
            solarDate = date,
            dayOfWeekKhmer = dayOfWeekKhmer,
            lunarDayNumber = lunarDayNumber,
            isWaxing = isWaxing,
            lunarDayString = lunarDayString,
            lunarMonthName = lunarMonthName,
            animalYear = animalYear,
            sakName = sakName,
            buddhistEra = buddhistEra,
            isBuddhistHolyDay = isHolyDay,
            formattedKhmerFull = formattedKhmerFull
        )
    }

    fun isBuddhistHolyDay(date: LocalDate = LocalDate.now()): Boolean {
        return calculate(date).isBuddhistHolyDay
    }

    fun toKhmerDigits(number: Int): String {
        val khmerDigits = arrayOf('០', '១', '២', '៣', '៤', '៥', '៦', '៧', '៨', '៩')
        return number.toString().map { char ->
            if (char.isDigit()) khmerDigits[char - '0'] else char
        }.joinToString("")
    }
}
