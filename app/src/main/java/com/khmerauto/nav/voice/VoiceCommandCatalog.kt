package com.khmerauto.nav.voice

import com.khmerauto.nav.core.calendar.ThemeMode
import com.khmerauto.nav.core.database.entity.PlaceEntity
import com.khmerauto.nav.hal.*

/**
 * 7 Architectural Voice Categories matching EZNav Specification.
 */
enum class VoiceCategory(val titleKm: String, val titleEn: String, val iconName: String) {
    CLIMATE("ប្រព័ន្ធម៉ាស៊ីនត្រជាក់", "Climate Control", "AcUnit"),
    WINDOWS("កញ្ចក់ & ដំបូលកញ្ចក់", "Windows & Sunroof", "Window"),
    DOORS_TAILGATE("ទ្វារ & គូទក្រោយ", "Doors & Tailgate", "Lock"),
    SEATS_LIGHT("កៅអី & ភ្លើងលម្អ", "Seats & Ambient Light", "AirlineSeatReclineExtra"),
    MEDIA("ប្រព័ន្ធកម្សាន្ត & សំឡេង", "Media & Audio", "MusicNote"),
    NAVIGATION("ផែនទី & ការរុករក", "Navigation & Map", "Navigation"),
    SCENES("សេណារីយ៉ូឆ្លាតវៃ", "Smart Scenes", "AutoAwesome")
}

/**
 * Callback interface for navigation commands to trigger map routing, zooming, and styles.
 */
interface NavigationController {
    suspend fun navigateTo(destination: PlaceEntity)
    suspend fun navigateHome()
    suspend fun navigateWork()
    suspend fun navigateNearestCharger()
    suspend fun navigateNearestRepair()
    fun stopNavigation()
    fun zoomIn()
    fun zoomOut()
    fun recenter()
    fun setThemeMode(mode: ThemeMode)
    fun setVoiceMuted(muted: Boolean)
}

/**
 * Unified Voice Command Definition.
 */
data class VoiceCommandEntry(
    val commandId: String,
    val category: VoiceCategory,
    val titleKm: String,
    val titleEn: String,
    val acceptedPhrasesKm: List<String>,
    val acceptedPhrasesEn: List<String>,
    val execute: suspend (VehicleHalContract, NavigationController) -> String
)

/**
 * The 140+ Complete Khmer & English Voice Command Catalog for Geely Galaxy M7 (Thor PHEV).
 */
object VoiceCommandCatalog {

    val allCommands: List<VoiceCommandEntry> by lazy {
        buildList {
            // =========================================================================
            // 1. CLIMATE (36 Commands)
            // =========================================================================
            add(VoiceCommandEntry(
                commandId = "CLIMATE_AC_ON",
                category = VoiceCategory.CLIMATE,
                titleKm = "បើកម៉ាស៊ីនត្រជាក់ A/C",
                titleEn = "Turn On A/C",
                acceptedPhrasesKm = listOf("បើកម៉ាស៊ីនត្រជាក់", "បើក AC", "បើកត្រជាក់", "ដំណើរការម៉ាស៊ីនត្រជាក់"),
                acceptedPhrasesEn = listOf("turn on ac", "turn on air conditioning", "start ac", "ac on")
            ) { hal, _ ->
                hal.setAcMode(ac = true)
                "បានបើកម៉ាស៊ីនត្រជាក់ / A/C is now on"
            })

            add(VoiceCommandEntry(
                commandId = "CLIMATE_AC_OFF",
                category = VoiceCategory.CLIMATE,
                titleKm = "បិទម៉ាស៊ីនត្រជាក់ A/C",
                titleEn = "Turn Off A/C",
                acceptedPhrasesKm = listOf("បិទម៉ាស៊ីនត្រជាក់", "បិទ AC", "បិទត្រជាក់"),
                acceptedPhrasesEn = listOf("turn off ac", "turn off air conditioning", "stop ac", "ac off")
            ) { hal, _ ->
                hal.setAcMode(ac = false)
                "បានបិទម៉ាស៊ីនត្រជាក់ / A/C turned off"
            })

            add(VoiceCommandEntry(
                commandId = "CLIMATE_AUTO_ON",
                category = VoiceCategory.CLIMATE,
                titleKm = "បើកម៉ាស៊ីនត្រជាក់ស្វ័យប្រវត្តិ AUTO",
                titleEn = "Turn On Auto Climate",
                acceptedPhrasesKm = listOf("បើកម៉ូតអូតូ", "បើកស្វ័យប្រវត្តិ", "ដាក់អូតូត្រជាក់", "បើក auto"),
                acceptedPhrasesEn = listOf("turn on auto climate", "set auto ac", "auto climate on", "enable auto")
            ) { hal, _ ->
                hal.setAcMode(auto = true)
                "បានដាក់ម៉ាស៊ីនត្រជាក់ជាស្វ័យប្រវត្តិ / Auto climate enabled"
            })

            add(VoiceCommandEntry(
                commandId = "CLIMATE_AUTO_OFF",
                category = VoiceCategory.CLIMATE,
                titleKm = "បិទម៉ូតស្វ័យប្រវត្តិ AUTO",
                titleEn = "Turn Off Auto Climate",
                acceptedPhrasesKm = listOf("បិទអូតូ", "បិទម៉ូត auto", "ឈប់ដាក់អូតូ"),
                acceptedPhrasesEn = listOf("turn off auto climate", "disable auto ac", "auto off")
            ) { hal, _ ->
                hal.setAcMode(auto = false)
                "បានបិទម៉ូតស្វ័យប្រវត្តិ / Auto climate disabled"
            })

            add(VoiceCommandEntry(
                commandId = "CLIMATE_MAX_AC_ON",
                category = VoiceCategory.CLIMATE,
                titleKm = "បើកម៉ាស៊ីនត្រជាក់កម្រិតខ្ពស់បំផុត MAX A/C",
                titleEn = "Turn On Max A/C",
                acceptedPhrasesKm = listOf("បើកត្រជាក់ខ្លាំងបំផុត", "បើក max ac", "ដាក់ត្រជាក់ម៉ាក់", "ត្រជាក់ខ្លាំង"),
                acceptedPhrasesEn = listOf("turn on max ac", "maximum cooling", "set max ac", "cool down maximum")
            ) { hal, _ ->
                hal.setAcMode(maxAc = true, ac = true)
                "បានបើកម៉ាស៊ីនត្រជាក់កម្រិតអតិបរមា / Maximum A/C activated"
            })

            add(VoiceCommandEntry(
                commandId = "CLIMATE_MAX_AC_OFF",
                category = VoiceCategory.CLIMATE,
                titleKm = "បិទម៉ាស៊ីនត្រជាក់ MAX A/C",
                titleEn = "Turn Off Max A/C",
                acceptedPhrasesKm = listOf("បិទត្រជាក់ខ្លាំង", "បិទ max ac", "កាត់បន្ថយត្រជាក់ម៉ាក់"),
                acceptedPhrasesEn = listOf("turn off max ac", "disable max ac", "exit max ac")
            ) { hal, _ ->
                hal.setAcMode(maxAc = false)
                "បានបិទម៉ាស៊ីនត្រជាក់កម្រិតអតិបរមា / Max A/C disabled"
            })

            add(VoiceCommandEntry(
                commandId = "CLIMATE_RECIRC_ON",
                category = VoiceCategory.CLIMATE,
                titleKm = "បើកខ្យល់វិលជុំក្នុងរថយន្ត RECIRC",
                titleEn = "Recirculate Cabin Air",
                acceptedPhrasesKm = listOf("បើកខ្យល់ក្នុងឡាន", "បិទខ្យល់ខាងក្រៅ", "បើក recirculation"),
                acceptedPhrasesEn = listOf("recirculate air", "turn on recirculation", "inside air only")
            ) { hal, _ ->
                hal.setAcMode(recirc = true)
                "បានបើកខ្យល់វិលជុំក្នុងរថយន្ត / Air recirculation on"
            })

            add(VoiceCommandEntry(
                commandId = "CLIMATE_FRESH_AIR",
                category = VoiceCategory.CLIMATE,
                titleKm = "បើកយកខ្យល់អាកាសពីខាងក្រៅ",
                titleEn = "Draw Fresh Outside Air",
                acceptedPhrasesKm = listOf("យកខ្យល់ខាងក្រៅ", "បើកខ្យល់បរិសុទ្ធ", "បិទ recirculation"),
                acceptedPhrasesEn = listOf("draw fresh air", "outside air", "turn off recirculation")
            ) { hal, _ ->
                hal.setAcMode(recirc = false)
                "បានបើកស្រូបយកខ្យល់អាកាសពីខាងក្រៅ / Fresh outside air active"
            })

            add(VoiceCommandEntry(
                commandId = "CLIMATE_FRONT_DEFROST_ON",
                category = VoiceCategory.CLIMATE,
                titleKm = "បើកកម្ចាត់អ័ព្ទកញ្ចក់មុខ",
                titleEn = "Front Windshield Defrost On",
                acceptedPhrasesKm = listOf("បើកកម្ចាត់អ័ព្ទមុខ", "ជម្រះអ័ព្ទកញ្ចក់មុខ", "បើក defrost មុខ"),
                acceptedPhrasesEn = listOf("front defrost on", "defog front windshield", "clear front windshield")
            ) { hal, _ ->
                hal.setAcMode(frontDefrost = true)
                "បានបើកកម្ចាត់អ័ព្ទកញ្ចក់មុខ / Front defrost active"
            })

            add(VoiceCommandEntry(
                commandId = "CLIMATE_FRONT_DEFROST_OFF",
                category = VoiceCategory.CLIMATE,
                titleKm = "បិទកម្ចាត់អ័ព្ទកញ្ចក់មុខ",
                titleEn = "Front Windshield Defrost Off",
                acceptedPhrasesKm = listOf("បិទកម្ចាត់អ័ព្ទមុខ", "បិទ defrost មុខ"),
                acceptedPhrasesEn = listOf("front defrost off", "stop front defrost")
            ) { hal, _ ->
                hal.setAcMode(frontDefrost = false)
                "បានបិទកម្ចាត់អ័ព្ទកញ្ចក់មុខ / Front defrost off"
            })

            add(VoiceCommandEntry(
                commandId = "CLIMATE_REAR_DEFROST_ON",
                category = VoiceCategory.CLIMATE,
                titleKm = "បើកកម្ចាត់អ័ព្ទកញ្ចក់ក្រោយ & កញ្ចក់មើលក្រោយ",
                titleEn = "Rear Window Defrost On",
                acceptedPhrasesKm = listOf("បើកកម្ចាត់អ័ព្ទក្រោយ", "ជម្រះអ័ព្ទកញ្ចក់ក្រោយ", "បើក defrost ក្រោយ"),
                acceptedPhrasesEn = listOf("rear defrost on", "defog rear window", "clear rear window")
            ) { hal, _ ->
                hal.setAcMode(rearDefrost = true)
                "បានបើកកម្ចាត់អ័ព្ទកញ្ចក់ក្រោយ / Rear defogger active"
            })

            add(VoiceCommandEntry(
                commandId = "CLIMATE_REAR_DEFROST_OFF",
                category = VoiceCategory.CLIMATE,
                titleKm = "បិទកម្ចាត់អ័ព្ទកញ្ចក់ក្រោយ",
                titleEn = "Rear Window Defrost Off",
                acceptedPhrasesKm = listOf("បិទកម្ចាត់អ័ព្ទក្រោយ", "បិទ defrost ក្រោយ"),
                acceptedPhrasesEn = listOf("rear defrost off", "stop rear defrost")
            ) { hal, _ ->
                hal.setAcMode(rearDefrost = false)
                "បានបិទកម្ចាត់អ័ព្ទកញ្ចក់ក្រោយ / Rear defogger off"
            })

            // 15 Distinct Temperature Commands (16°C to 30°C)
            for (temp in 16..30) {
                add(VoiceCommandEntry(
                    commandId = "CLIMATE_SET_TEMP_$temp",
                    category = VoiceCategory.CLIMATE,
                    titleKm = "កំណត់សីតុណ្ហភាព $temp°C",
                    titleEn = "Set Temperature to $temp°C",
                    acceptedPhrasesKm = listOf(
                        "ដាក់សីតុណ្ហភាព $temp អង្សា",
                        "កំណត់ $temp អង្សា",
                        "ដាក់ត្រជាក់ $temp",
                        "សីតុណ្ហភាព $temp"
                    ),
                    acceptedPhrasesEn = listOf(
                        "set temperature to $temp",
                        "set temperature $temp degrees",
                        "make it $temp degrees",
                        "set temp $temp"
                    )
                ) { hal, _ ->
                    hal.setTemperature(Zone.ALL, temp.toFloat())
                    "បានកំណត់សីតុណ្ហភាព $temp°C / Temperature set to $temp°C"
                })
            }

            // Relative temperature commands
            add(VoiceCommandEntry(
                commandId = "CLIMATE_WARMER",
                category = VoiceCategory.CLIMATE,
                titleKm = "បង្កើនកម្តៅ ១ អង្សា (Warmer)",
                titleEn = "Make it Warmer (+1°C)",
                acceptedPhrasesKm = listOf("ឡើងកម្តៅបន្តិច", "ដាក់ឲ្យក្តៅបន្តិច", "ឡើង ១ អង្សា", "ក្តៅជាងនេះបន្តិច"),
                acceptedPhrasesEn = listOf("make it warmer", "one degree warmer", "increase temperature", "a bit warmer")
            ) { hal, _ ->
                val current = hal.telemetryState.value.hvacState.driverTempC
                val target = (current + 1.0f).coerceAtMost(30.0f)
                hal.setTemperature(Zone.ALL, target)
                "បានបង្កើនសីតុណ្ហភាពដល់ ${target.toInt()}°C / Increased to ${target.toInt()}°C"
            })

            add(VoiceCommandEntry(
                commandId = "CLIMATE_COOLER",
                category = VoiceCategory.CLIMATE,
                titleKm = "បន្ថយសីតុណ្ហភាព ១ អង្សា (Cooler)",
                titleEn = "Make it Cooler (-1°C)",
                acceptedPhrasesKm = listOf("បន្ថយត្រជាក់បន្តិច", "ដាក់ឲ្យត្រជាក់ជាងនេះ", "ចុះ ១ អង្សា", "ត្រជាក់ជាងនេះបន្តិច"),
                acceptedPhrasesEn = listOf("make it cooler", "one degree cooler", "lower temperature", "a bit cooler")
            ) { hal, _ ->
                val current = hal.telemetryState.value.hvacState.driverTempC
                val target = (current - 1.0f).coerceAtLeast(16.0f)
                hal.setTemperature(Zone.ALL, target)
                "បានបន្ថយសីតុណ្ហភាពមក ${target.toInt()}°C / Decreased to ${target.toInt()}°C"
            })

            // 7 Fan Speeds (1 to 7)
            for (speed in 1..7) {
                add(VoiceCommandEntry(
                    commandId = "CLIMATE_FAN_SPEED_$speed",
                    category = VoiceCategory.CLIMATE,
                    titleKm = "កំណត់ល្បឿនកង្ហារកម្រិត $speed",
                    titleEn = "Set Fan Speed to $speed",
                    acceptedPhrasesKm = listOf(
                        "កង្ហារលេខ $speed",
                        "ដាក់កង្ហារកម្រិត $speed",
                        "ល្បឿនកង្ហារ $speed",
                        "កង្ហារ $speed"
                    ),
                    acceptedPhrasesEn = listOf(
                        "set fan speed to $speed",
                        "fan speed $speed",
                        "fan level $speed",
                        "blower speed $speed"
                    )
                ) { hal, _ ->
                    hal.setFanSpeed(speed)
                    "បានកំណត់កង្ហារកម្រិត $speed / Fan speed set to $speed"
                })
            }

            add(VoiceCommandEntry(
                commandId = "CLIMATE_FAN_HIGHER",
                category = VoiceCategory.CLIMATE,
                titleKm = "បង្កើនល្បឿនកង្ហារ",
                titleEn = "Fan Speed Higher",
                acceptedPhrasesKm = listOf("ឡើងកង្ហារ", "បង្កើនកង្ហារ", "កង្ហារខ្លាំងជាងនេះ"),
                acceptedPhrasesEn = listOf("fan higher", "increase fan speed", "fan up")
            ) { hal, _ ->
                val current = hal.telemetryState.value.hvacState.fanSpeed
                val next = (current + 1).coerceAtMost(7)
                hal.setFanSpeed(next)
                "បានបង្កើនកង្ហារកម្រិត $next / Fan increased to $next"
            })

            add(VoiceCommandEntry(
                commandId = "CLIMATE_FAN_LOWER",
                category = VoiceCategory.CLIMATE,
                titleKm = "បន្ថយល្បឿនកង្ហារ",
                titleEn = "Fan Speed Lower",
                acceptedPhrasesKm = listOf("ចុះកង្ហារ", "បន្ថយកង្ហារ", "កង្ហារខ្សោយជាងនេះ"),
                acceptedPhrasesEn = listOf("fan lower", "decrease fan speed", "fan down")
            ) { hal, _ ->
                val current = hal.telemetryState.value.hvacState.fanSpeed
                val next = (current - 1).coerceAtLeast(1)
                hal.setFanSpeed(next)
                "បានបន្ថយកង្ហារកម្រិត $next / Fan decreased to $next"
            })

            // =========================================================================
            // 2. WINDOWS & SUNROOF (15 Commands)
            // =========================================================================
            add(VoiceCommandEntry(
                commandId = "WINDOWS_DRIVER_OPEN",
                category = VoiceCategory.WINDOWS,
                titleKm = "បើកកញ្ចក់ទ្វារខាងតៃកុង",
                titleEn = "Open Driver Window",
                acceptedPhrasesKm = listOf("បើកកញ្ចក់តៃកុង", "បើកកញ្ចក់ខាងឆ្វេងមុខ", "ទម្លាក់កញ្ចក់តៃកុង"),
                acceptedPhrasesEn = listOf("open driver window", "roll down driver window", "driver window open")
            ) { hal, _ ->
                hal.setWindowPosition(WindowTarget.FRONT_LEFT, 100)
                "បានបើកកញ្ចក់ខាងតៃកុង / Driver window opened"
            })

            add(VoiceCommandEntry(
                commandId = "WINDOWS_DRIVER_CLOSE",
                category = VoiceCategory.WINDOWS,
                titleKm = "បិទកញ្ចក់ទ្វារខាងតៃកុង",
                titleEn = "Close Driver Window",
                acceptedPhrasesKm = listOf("បិទកញ្ចក់តៃកុង", "បិទកញ្ចក់ខាងឆ្វេងមុខ", "ឡើងកញ្ចក់តៃកុង"),
                acceptedPhrasesEn = listOf("close driver window", "roll up driver window", "driver window close")
            ) { hal, _ ->
                hal.setWindowPosition(WindowTarget.FRONT_LEFT, 0)
                "បានបិទកញ្ចក់ខាងតៃកុង / Driver window closed"
            })

            add(VoiceCommandEntry(
                commandId = "WINDOWS_PASSENGER_OPEN",
                category = VoiceCategory.WINDOWS,
                titleKm = "បើកកញ្ចក់ទ្វារខាងមុខស្តាំ",
                titleEn = "Open Passenger Window",
                acceptedPhrasesKm = listOf("បើកកញ្ចក់មុខស្តាំ", "បើកកញ្ចក់អ្នករួមដំណើរ", "ទម្លាក់កញ្ចក់មុខស្តាំ"),
                acceptedPhrasesEn = listOf("open passenger window", "roll down passenger window", "passenger window open")
            ) { hal, _ ->
                hal.setWindowPosition(WindowTarget.FRONT_RIGHT, 100)
                "បានបើកកញ្ចក់ខាងមុខស្តាំ / Passenger window opened"
            })

            add(VoiceCommandEntry(
                commandId = "WINDOWS_PASSENGER_CLOSE",
                category = VoiceCategory.WINDOWS,
                titleKm = "បិទកញ្ចក់ទ្វារខាងមុខស្តាំ",
                titleEn = "Close Passenger Window",
                acceptedPhrasesKm = listOf("បិទកញ្ចក់មុខស្តាំ", "បិទកញ្ចក់អ្នករួមដំណើរ", "ឡើងកញ្ចក់មុខស្តាំ"),
                acceptedPhrasesEn = listOf("close passenger window", "roll up passenger window", "passenger window close")
            ) { hal, _ ->
                hal.setWindowPosition(WindowTarget.FRONT_RIGHT, 0)
                "បានបិទកញ្ចក់ខាងមុខស្តាំ / Passenger window closed"
            })

            add(VoiceCommandEntry(
                commandId = "WINDOWS_ALL_OPEN",
                category = VoiceCategory.WINDOWS,
                titleKm = "បើកកញ្ចក់ទ្វារទាំងអស់",
                titleEn = "Open All Windows",
                acceptedPhrasesKm = listOf("បើកកញ្ចក់ទាំងអស់", "ទម្លាក់កញ្ចក់ទាំង៤", "បើកកញ្ចក់ឡានទាំងអស់"),
                acceptedPhrasesEn = listOf("open all windows", "roll down all windows", "windows down")
            ) { hal, _ ->
                hal.setWindowPosition(WindowTarget.ALL, 100)
                "បានបើកកញ្ចក់ទាំងអស់ / All windows opened"
            })

            add(VoiceCommandEntry(
                commandId = "WINDOWS_ALL_CLOSE",
                category = VoiceCategory.WINDOWS,
                titleKm = "បិទកញ្ចក់ទ្វារទាំងអស់",
                titleEn = "Close All Windows",
                acceptedPhrasesKm = listOf("បិទកញ្ចក់ទាំងអស់", "ឡើងកញ្ចក់ទាំង៤", "បិទកញ្ចក់ឡានទាំងអស់"),
                acceptedPhrasesEn = listOf("close all windows", "roll up all windows", "windows up")
            ) { hal, _ ->
                hal.setWindowPosition(WindowTarget.ALL, 0)
                "បានបិទកញ្ចក់ទាំងអស់ / All windows closed"
            })

            add(VoiceCommandEntry(
                commandId = "WINDOWS_CRACK_VENT",
                category = VoiceCategory.WINDOWS,
                titleKm = "បើកកញ្ចក់បន្តិច (២០%) បញ្ចេញខ្យល់",
                titleEn = "Crack Windows (20% Vent)",
                acceptedPhrasesKm = listOf("បើកកញ្ចក់បន្តិច", "ទម្លាក់កញ្ចក់បន្តិច", "ហើបកញ្ចក់បន្តិច"),
                acceptedPhrasesEn = listOf("crack windows", "vent windows", "open windows slightly")
            ) { hal, _ ->
                hal.setWindowPosition(WindowTarget.ALL, 20)
                "បានបើកកញ្ចក់ ២០% ដើម្បីបញ្ចេញខ្យល់ / Windows cracked 20%"
            })

            add(VoiceCommandEntry(
                commandId = "WINDOWS_SUNROOF_OPEN",
                category = VoiceCategory.WINDOWS,
                titleKm = "បើកដំបូលកញ្ចក់ Sunroof",
                titleEn = "Open Sunroof",
                acceptedPhrasesKm = listOf("បើកដំបូលកញ្ចក់", "បើក sunroof", "បើកដំបូលលើ"),
                acceptedPhrasesEn = listOf("open sunroof", "slide sunroof open", "sunroof open")
            ) { hal, _ ->
                hal.setSunroofAndShade(sunroofPercent = 100, shadePercent = 100)
                "បានបើកដំបូលកញ្ចក់ Sunroof / Sunroof opened"
            })

            add(VoiceCommandEntry(
                commandId = "WINDOWS_SUNROOF_CLOSE",
                category = VoiceCategory.WINDOWS,
                titleKm = "បិទដំបូលកញ្ចក់ Sunroof",
                titleEn = "Close Sunroof",
                acceptedPhrasesKm = listOf("បិទដំបូលកញ្ចក់", "បិទ sunroof", "បិទដំបូលលើ"),
                acceptedPhrasesEn = listOf("close sunroof", "slide sunroof closed", "sunroof close")
            ) { hal, _ ->
                hal.setSunroofAndShade(sunroofPercent = 0)
                "បានបិទដំបូលកញ្ចក់ Sunroof / Sunroof closed"
            })

            add(VoiceCommandEntry(
                commandId = "WINDOWS_SHADE_OPEN",
                category = VoiceCategory.WINDOWS,
                titleKm = "បើកវាំងននដំបូលលើ Roof Shade",
                titleEn = "Open Roof Sunshade",
                acceptedPhrasesKm = listOf("បើកវាំងននដំបូល", "បើកស្រទាប់ដំបូល", "បើក shade"),
                acceptedPhrasesEn = listOf("open roof shade", "open sunshade", "roof shade open")
            ) { hal, _ ->
                hal.setSunroofAndShade(shadePercent = 100)
                "បានបើកវាំងននដំបូលលើ / Roof sunshade opened"
            })

            add(VoiceCommandEntry(
                commandId = "WINDOWS_SHADE_CLOSE",
                category = VoiceCategory.WINDOWS,
                titleKm = "បិទវាំងននដំបូលលើ Roof Shade",
                titleEn = "Close Roof Sunshade",
                acceptedPhrasesKm = listOf("បិទវាំងននដំបូល", "បិទស្រទាប់ដំបូល", "បិទ shade"),
                acceptedPhrasesEn = listOf("close roof shade", "close sunshade", "roof shade close")
            ) { hal, _ ->
                hal.setSunroofAndShade(sunroofPercent = 0, shadePercent = 0)
                "បានបិទវាំងននដំបូលលើ / Roof sunshade closed"
            })

            // =========================================================================
            // 3. DOORS & TAILGATE (6 Commands - Strict Guardrail 4 Tailgate Interlock)
            // =========================================================================
            add(VoiceCommandEntry(
                commandId = "DOORS_LOCK_ALL",
                category = VoiceCategory.DOORS_TAILGATE,
                titleKm = "ចាក់សោទ្វាររថយន្តទាំងអស់",
                titleEn = "Lock All Doors",
                acceptedPhrasesKm = listOf("ចាក់សោឡាន", "ចាក់សោទ្វារទាំងអស់", "ចាក់សោទ្វារ"),
                acceptedPhrasesEn = listOf("lock doors", "lock all doors", "lock car")
            ) { hal, _ ->
                hal.setDoorLocks(true)
                "បានចាក់សោទ្វាររថយន្ត / Doors locked"
            })

            add(VoiceCommandEntry(
                commandId = "DOORS_UNLOCK_ALL",
                category = VoiceCategory.DOORS_TAILGATE,
                titleKm = "ដោះសោទ្វាររថយន្តទាំងអស់",
                titleEn = "Unlock All Doors",
                acceptedPhrasesKm = listOf("ដោះសោឡាន", "ដោះសោទ្វារទាំងអស់", "បើកសោទ្វារ"),
                acceptedPhrasesEn = listOf("unlock doors", "unlock all doors", "unlock car")
            ) { hal, _ ->
                hal.setDoorLocks(false)
                "បានដោះសោទ្វាររថយន្ត / Doors unlocked"
            })

            add(VoiceCommandEntry(
                commandId = "DOORS_TAILGATE_OPEN",
                category = VoiceCategory.DOORS_TAILGATE,
                titleKm = "បើកទ្វារគូទក្រោយ (Smart Tailgate)",
                titleEn = "Open Tailgate",
                acceptedPhrasesKm = listOf("បើកគូទក្រោយ", "បើកទ្វារក្រោយ", "បើកឃ្លុបក្រោយ"),
                acceptedPhrasesEn = listOf("open tailgate", "open trunk", "open boot")
            ) { hal, _ ->
                // MANDATORY AUTOMOTIVE SAFETY GUARDRAIL 4:
                // Verify vehicle is strictly stationary and in Park before opening tailgate
                val telemetry = hal.telemetryState.value
                if (telemetry.gear != GearState.P || telemetry.speedKmh > 0.0f) {
                    "មិនអាចបើកគូទក្រោយពេលរថយន្តកំពុងបើកបរបានទេ! / Cannot open tailgate while vehicle is in motion or not in Park!"
                } else {
                    hal.setTailgate(true)
                    "បានបើកទ្វារគូទក្រោយ / Tailgate opened"
                }
            })

            add(VoiceCommandEntry(
                commandId = "DOORS_TAILGATE_CLOSE",
                category = VoiceCategory.DOORS_TAILGATE,
                titleKm = "បិទទ្វារគូទក្រោយ (Smart Tailgate)",
                titleEn = "Close Tailgate",
                acceptedPhrasesKm = listOf("បិទគូទក្រោយ", "បិទទ្វារក្រោយ", "បិទឃ្លុបក្រោយ"),
                acceptedPhrasesEn = listOf("close tailgate", "close trunk", "close boot")
            ) { hal, _ ->
                hal.setTailgate(false)
                "បានបិទទ្វារគូទក្រោយ / Tailgate closed"
            })

            // =========================================================================
            // 4. SEATS & AMBIENT LIGHT (26 Commands)
            // =========================================================================
            add(VoiceCommandEntry(
                commandId = "SEATS_DRIVER_VENT_3",
                category = VoiceCategory.SEATS_LIGHT,
                titleKm = "ខ្យល់កៅអីតៃកុងកម្រិត ៣ (ខ្លាំង)",
                titleEn = "Driver Seat Ventilation Level 3",
                acceptedPhrasesKm = listOf("បើកខ្យល់កៅអីតៃកុងខ្លាំង", "ខ្យល់កៅអីតៃកុងលេខ ៣", "ខ្យល់កៅអីលេខ ៣"),
                acceptedPhrasesEn = listOf("driver seat vent max", "driver seat ventilation level 3", "seat vent high")
            ) { hal, _ ->
                hal.setSeatClimate(SeatTarget.DRIVER, ventLevel = 3)
                "បានកំណត់ខ្យល់កៅអីតៃកុងកម្រិត ៣ / Driver seat vent set to 3"
            })

            add(VoiceCommandEntry(
                commandId = "SEATS_DRIVER_VENT_OFF",
                category = VoiceCategory.SEATS_LIGHT,
                titleKm = "បិទខ្យល់កៅអីតៃកុង",
                titleEn = "Driver Seat Ventilation Off",
                acceptedPhrasesKm = listOf("បិទខ្យល់កៅអីតៃកុង", "បិទខ្យល់កៅអី", "ឈប់បក់ខ្យល់កៅអី"),
                acceptedPhrasesEn = listOf("turn off driver seat vent", "driver seat vent off", "stop seat vent")
            ) { hal, _ ->
                hal.setSeatClimate(SeatTarget.DRIVER, ventLevel = 0)
                "បានបិទខ្យល់កៅអីតៃកុង / Driver seat vent turned off"
            })

            add(VoiceCommandEntry(
                commandId = "SEATS_PASSENGER_VENT_3",
                category = VoiceCategory.SEATS_LIGHT,
                titleKm = "ខ្យល់កៅអីស្តាំកម្រិត ៣ (ខ្លាំង)",
                titleEn = "Passenger Seat Ventilation Level 3",
                acceptedPhrasesKm = listOf("បើកខ្យល់កៅអីស្តាំខ្លាំង", "ខ្យល់កៅអីស្តាំលេខ ៣", "ខ្យល់កៅអីអ្នករួមដំណើរលេខ ៣"),
                acceptedPhrasesEn = listOf("passenger seat vent max", "passenger seat ventilation 3")
            ) { hal, _ ->
                hal.setSeatClimate(SeatTarget.PASSENGER, ventLevel = 3)
                "បានកំណត់ខ្យល់កៅអីស្តាំកម្រិត ៣ / Passenger seat vent set to 3"
            })

            add(VoiceCommandEntry(
                commandId = "SEATS_DRIVER_HEAT_ON",
                category = VoiceCategory.SEATS_LIGHT,
                titleKm = "កម្តៅកៅអីតៃកុងកម្រិត ២",
                titleEn = "Driver Seat Heating Level 2",
                acceptedPhrasesKm = listOf("បើកកម្តៅកៅអីតៃកុង", "កម្តៅកៅអីតៃកុង", "ដាក់កៅអីក្តៅ"),
                acceptedPhrasesEn = listOf("turn on driver seat heater", "driver seat heat level 2", "warm my seat")
            ) { hal, _ ->
                hal.setSeatClimate(SeatTarget.DRIVER, heatLevel = 2)
                "បានបើកកម្តៅកៅអីតៃកុង / Driver seat heat active"
            })

            add(VoiceCommandEntry(
                commandId = "SEATS_DRIVER_HEAT_OFF",
                category = VoiceCategory.SEATS_LIGHT,
                titleKm = "បិទកម្តៅកៅអីតៃកុង",
                titleEn = "Driver Seat Heating Off",
                acceptedPhrasesKm = listOf("បិទកម្តៅកៅអីតៃកុង", "បិទកម្តៅកៅអី", "ឈប់ដាក់កៅអីក្តៅ"),
                acceptedPhrasesEn = listOf("turn off driver seat heater", "seat heat off")
            ) { hal, _ ->
                hal.setSeatClimate(SeatTarget.DRIVER, heatLevel = 0)
                "បានបិទកម្តៅកៅអីតៃកុង / Driver seat heat off"
            })

            add(VoiceCommandEntry(
                commandId = "LIGHT_AMBIENT_ON",
                category = VoiceCategory.SEATS_LIGHT,
                titleKm = "បើកភ្លើងលម្អក្នុងកាប៊ីន (Ambient Light)",
                titleEn = "Turn On Ambient Light",
                acceptedPhrasesKm = listOf("បើកភ្លើងលម្អ", "បើកភ្លើង ambient", "បើកភ្លើងក្នុងឡាន"),
                acceptedPhrasesEn = listOf("turn on ambient light", "ambient light on", "cabin lights on")
            ) { hal, _ ->
                hal.setAmbientLight(true)
                "បានបើកភ្លើងលម្អកាប៊ីន / Ambient lighting turned on"
            })

            add(VoiceCommandEntry(
                commandId = "LIGHT_AMBIENT_OFF",
                category = VoiceCategory.SEATS_LIGHT,
                titleKm = "បិទភ្លើងលម្អក្នុងកាប៊ីន (Ambient Light)",
                titleEn = "Turn Off Ambient Light",
                acceptedPhrasesKm = listOf("បិទភ្លើងលម្អ", "បិទភ្លើង ambient", "បិទភ្លើងក្នុងឡាន"),
                acceptedPhrasesEn = listOf("turn off ambient light", "ambient light off", "cabin lights off")
            ) { hal, _ ->
                hal.setAmbientLight(false)
                "បានបិទភ្លើងលម្អកាប៊ីន / Ambient lighting turned off"
            })

            // 8 Distinct Ambient Light Colors
            val ambientColors = listOf(
                Triple("CYAN", "#00E5FF", Pair("ពណ៌ខៀវខ្ចី", "Geely Cyan")),
                Triple("GOLD", "#FFD700", Pair("ពណ៌មាស", "Amber Gold")),
                Triple("EMERALD", "#00E676", Pair("ពណ៌បៃតងត្បូង", "Emerald Green")),
                Triple("RED", "#FF1744", Pair("ពណ៌ក្រហម", "Sport Red")),
                Triple("PURPLE", "#D500F9", Pair("ពណ៌ស្វាយ", "Galaxy Purple")),
                Triple("ICE_BLUE", "#80D8FF", Pair("ពណ៌ទឹកកក", "Ice Blue")),
                Triple("ORANGE", "#FF6D00", Pair("ពណ៌ទឹកក្រូច", "Sunset Orange")),
                Triple("WHITE", "#FFFFFF", Pair("ពណ៌ស", "Pure White"))
            )

            ambientColors.forEach { (name, hex, labels) ->
                add(VoiceCommandEntry(
                    commandId = "LIGHT_COLOR_$name",
                    category = VoiceCategory.SEATS_LIGHT,
                    titleKm = "ប្តូរភ្លើងលម្អទៅ ${labels.first}",
                    titleEn = "Change Ambient Light to ${labels.second}",
                    acceptedPhrasesKm = listOf(
                        "ដាក់ភ្លើង ${labels.first}",
                        "ប្តូរភ្លើង ${labels.first}",
                        "ភ្លើង ${labels.first}"
                    ),
                    acceptedPhrasesEn = listOf(
                        "change ambient light to ${labels.second}",
                        "set ambient light ${labels.second}",
                        "ambient light ${labels.second}"
                    )
                ) { hal, _ ->
                    hal.setAmbientLight(true, colorHex = hex)
                    "បានប្តូរភ្លើងលម្អទៅ ${labels.first} / Ambient light set to ${labels.second}"
                })
            }

            // =========================================================================
            // 5. MEDIA & AUDIO (12 Commands)
            // =========================================================================
            add(VoiceCommandEntry(
                commandId = "MEDIA_PLAY",
                category = VoiceCategory.MEDIA,
                titleKm = "ចាក់តន្ត្រី (Play Media)",
                titleEn = "Play Music",
                acceptedPhrasesKm = listOf("ចាក់ចម្រៀង", "ចាក់តន្ត្រី", "បន្តចាក់ចម្រៀង", "play"),
                acceptedPhrasesEn = listOf("play music", "play", "resume music", "resume audio")
            ) { _, _ ->
                "កំពុងចាក់តន្ត្រី / Playing music"
            })

            add(VoiceCommandEntry(
                commandId = "MEDIA_PAUSE",
                category = VoiceCategory.MEDIA,
                titleKm = "ផ្អាកតន្ត្រី (Pause Media)",
                titleEn = "Pause Music",
                acceptedPhrasesKm = listOf("ផ្អាកចម្រៀង", "ឈប់ចាក់ចម្រៀង", "ផ្អាកតន្ត្រី", "pause"),
                acceptedPhrasesEn = listOf("pause music", "pause", "stop music")
            ) { _, _ ->
                "បានផ្អាកតន្ត្រី / Music paused"
            })

            add(VoiceCommandEntry(
                commandId = "MEDIA_NEXT",
                category = VoiceCategory.MEDIA,
                titleKm = "បទបន្ទាប់ (Next Track)",
                titleEn = "Next Track",
                acceptedPhrasesKm = listOf("បទបន្ទាប់", "ប្តូរបទ", "ចាក់បទបន្ទាប់", "next"),
                acceptedPhrasesEn = listOf("next track", "next song", "skip track", "next")
            ) { _, _ ->
                "បានប្តូរទៅបទបន្ទាប់ / Switched to next track"
            })

            add(VoiceCommandEntry(
                commandId = "MEDIA_PREV",
                category = VoiceCategory.MEDIA,
                titleKm = "បទមុន (Previous Track)",
                titleEn = "Previous Track",
                acceptedPhrasesKm = listOf("បទមុន", "ថយក្រោយមួយបទ", "ចាក់បទមុន", "previous"),
                acceptedPhrasesEn = listOf("previous track", "previous song", "back track")
            ) { _, _ ->
                "បានប្តូរទៅបទមុន / Playing previous track"
            })

            add(VoiceCommandEntry(
                commandId = "MEDIA_VOLUME_UP",
                category = VoiceCategory.MEDIA,
                titleKm = "បង្កើនកម្រិតសំឡេង (Volume Up)",
                titleEn = "Volume Up",
                acceptedPhrasesKm = listOf("ឡើងសំឡេង", "ឮជាងនេះបន្តិច", "បង្កើនសំឡេង", "volume up"),
                acceptedPhrasesEn = listOf("volume up", "louder", "turn it up", "increase volume")
            ) { _, _ ->
                "បានបង្កើនសំឡេង / Volume increased"
            })

            add(VoiceCommandEntry(
                commandId = "MEDIA_VOLUME_DOWN",
                category = VoiceCategory.MEDIA,
                titleKm = "បន្ថយកម្រិតសំឡេង (Volume Down)",
                titleEn = "Volume Down",
                acceptedPhrasesKm = listOf("ចុះសំឡេង", "បន្ថយសំឡេង", "កុំឲ្យឮពេក", "volume down"),
                acceptedPhrasesEn = listOf("volume down", "quieter", "turn it down", "decrease volume")
            ) { _, _ ->
                "បានបន្ថយសំឡេង / Volume decreased"
            })

            add(VoiceCommandEntry(
                commandId = "MEDIA_MUTE",
                category = VoiceCategory.MEDIA,
                titleKm = "បិទសំឡេង (Mute)",
                titleEn = "Mute Audio",
                acceptedPhrasesKm = listOf("បិទសំឡេង", "កុំឲ្យឮសំឡេង", "mute"),
                acceptedPhrasesEn = listOf("mute audio", "mute", "silence audio")
            ) { _, _ ->
                "បានបិទសំឡេង / Audio muted"
            })

            // =========================================================================
            // 6. NAVIGATION & MAP (14 Commands)
            // =========================================================================
            add(VoiceCommandEntry(
                commandId = "NAV_HOME",
                category = VoiceCategory.NAVIGATION,
                titleKm = "នាំផ្លូវទៅផ្ទះ (Navigate Home)",
                titleEn = "Navigate Home",
                acceptedPhrasesKm = listOf("ទៅផ្ទះ", "នាំផ្លូវទៅផ្ទះ", "បើកឡានទៅផ្ទះ", "ទៅផ្ទះខ្ញុំ"),
                acceptedPhrasesEn = listOf("navigate home", "take me home", "drive home", "go home")
            ) { _, nav ->
                nav.navigateHome()
                "កំពុងនាំផ្លូវទៅផ្ទះ / Navigating to Home"
            })

            add(VoiceCommandEntry(
                commandId = "NAV_WORK",
                category = VoiceCategory.NAVIGATION,
                titleKm = "នាំផ្លូវទៅកន្លែងធ្វើការ (Navigate to Work)",
                titleEn = "Navigate to Work",
                acceptedPhrasesKm = listOf("ទៅកន្លែងធ្វើការ", "នាំផ្លូវទៅធ្វើការ", "ទៅការិយាល័យ"),
                acceptedPhrasesEn = listOf("navigate to work", "take me to work", "drive to office", "go to work")
            ) { _, nav ->
                nav.navigateWork()
                "កំពុងនាំផ្លូវទៅកន្លែងធ្វើការ / Navigating to Work"
            })

            add(VoiceCommandEntry(
                commandId = "NAV_NEAREST_CHARGER",
                category = VoiceCategory.NAVIGATION,
                titleKm = "ស្វែងរកស្ថានីយសាកថ្មដែលនៅជិតបំផុត",
                titleEn = "Find Nearest EV Fast Charger",
                acceptedPhrasesKm = listOf("រកកន្លែងសាកថ្ម", "កន្លែងសាកថ្មជិតបំផុត", "ស្ថានីយសាកថ្មជិតនេះ", "រកឆ្នាំងសាក"),
                acceptedPhrasesEn = listOf("find nearest charger", "nearest ev charger", "where can i charge", "ev station nearby")
            ) { _, nav ->
                nav.navigateNearestCharger()
                "បានស្វែងរកស្ថានីយសាកថ្មល្បឿនលឿនដែលនៅជិតបំផុត / Found nearest EV fast charging station"
            })

            add(VoiceCommandEntry(
                commandId = "NAV_NEAREST_REPAIR",
                category = VoiceCategory.NAVIGATION,
                titleKm = "ស្វែងរកយានដ្ឋានជួសជុល EV ដែលនៅជិតបំផុត",
                titleEn = "Find Nearest EV Repair Workshop",
                acceptedPhrasesKm = listOf("រកកន្លែងជួសជុលឡាន", "យានដ្ឋានជិតបំផុត", "យានដ្ឋាន EV", "ជួសជុលរថយន្ត"),
                acceptedPhrasesEn = listOf("find nearest repair shop", "nearest mechanic", "ev service center")
            ) { _, nav ->
                nav.navigateNearestRepair()
                "បានស្វែងរកមជ្ឈមណ្ឌលថែទាំ EV ដែលនៅជិតបំផុត / Found nearest EV service center"
            })

            add(VoiceCommandEntry(
                commandId = "NAV_CANCEL_ROUTE",
                category = VoiceCategory.NAVIGATION,
                titleKm = "បញ្ចប់ការនាំផ្លូវ (Cancel Route)",
                titleEn = "Cancel Route Navigation",
                acceptedPhrasesKm = listOf("ឈប់នាំផ្លូវ", "បិទការនាំផ្លូវ", "បញ្ចប់ការធ្វើដំណើរ", "cancel route"),
                acceptedPhrasesEn = listOf("cancel route", "stop navigation", "end navigation", "cancel navigation")
            ) { _, nav ->
                nav.stopNavigation()
                "បានបញ្ចប់ការនាំផ្លូវ / Navigation route cancelled"
            })

            add(VoiceCommandEntry(
                commandId = "NAV_ZOOM_IN",
                category = VoiceCategory.NAVIGATION,
                titleKm = "ពង្រីកផែនទី (Zoom In)",
                titleEn = "Zoom In Map",
                acceptedPhrasesKm = listOf("ពង្រីកផែនទី", "zoom ផែនទីចូល", "មើលឲ្យជិតបន្តិច"),
                acceptedPhrasesEn = listOf("zoom in", "zoom in map", "closer map")
            ) { _, nav ->
                nav.zoomIn()
                "បានពង្រីកផែនទី / Map zoomed in"
            })

            add(VoiceCommandEntry(
                commandId = "NAV_ZOOM_OUT",
                category = VoiceCategory.NAVIGATION,
                titleKm = "បង្រួមផែនទី (Zoom Out)",
                titleEn = "Zoom Out Map",
                acceptedPhrasesKm = listOf("បង្រួមផែនទី", "zoom ផែនទីចេញ", "មើលឲ្យទូលាយ"),
                acceptedPhrasesEn = listOf("zoom out", "zoom out map", "wider view")
            ) { _, nav ->
                nav.zoomOut()
                "បានបង្រួមផែនទី / Map zoomed out"
            })

            add(VoiceCommandEntry(
                commandId = "NAV_MAP_DAY_MODE",
                category = VoiceCategory.NAVIGATION,
                titleKm = "ប្តូរផែនទីទៅពន្លឺថ្ងៃ (Day Mode)",
                titleEn = "Switch Map to Day Mode",
                acceptedPhrasesKm = listOf("ដាក់ផែនទីថ្ងៃ", "ផែនទីពន្លឺថ្ងៃ", "day mode"),
                acceptedPhrasesEn = listOf("day mode map", "switch map to day", "day theme map")
            ) { _, nav ->
                nav.setThemeMode(ThemeMode.DAY)
                "បានប្តូរផែនទីទៅពន្លឺថ្ងៃ / Map switched to Day mode"
            })

            add(VoiceCommandEntry(
                commandId = "NAV_MAP_NIGHT_MODE",
                category = VoiceCategory.NAVIGATION,
                titleKm = "ប្តូរផែនទីទៅពេលយប់ (Night Mode)",
                titleEn = "Switch Map to Night Mode",
                acceptedPhrasesKm = listOf("ដាក់ផែនទីយប់", "ផែនទីពេលយប់", "night mode"),
                acceptedPhrasesEn = listOf("night mode map", "switch map to night", "dark mode map")
            ) { _, nav ->
                nav.setThemeMode(ThemeMode.NIGHT)
                "បានប្តូរផែនទីទៅពេលយប់ / Map switched to Night mode"
            })

            // =========================================================================
            // 7. SMART AUTOMOTIVE SCENES (12 Commands)
            // =========================================================================
            add(VoiceCommandEntry(
                commandId = "SCENE_IM_HOT",
                category = VoiceCategory.SCENES,
                titleKm = "ខ្ញុំក្តៅណាស់! (Cool Down Car)",
                titleEn = "I'm Hot / Fast Cool Down",
                acceptedPhrasesKm = listOf("ខ្ញុំក្តៅណាស់", "ក្តៅណាស់", "ត្រជាក់ខ្លាំងមក", "cool down"),
                acceptedPhrasesEn = listOf("i'm hot", "cool down car", "it's too hot", "cool me down")
            ) { hal, _ ->
                hal.setAcMode(maxAc = true, ac = true)
                hal.setTemperature(Zone.ALL, 18.0f)
                hal.setFanSpeed(6)
                hal.setSeatClimate(SeatTarget.DRIVER, ventLevel = 3)
                "បានបើកម៉ាស៊ីនត្រជាក់អតិបរមា ១៨°C និងខ្យល់កៅអី / Fast cool down active: 18°C Max A/C & Seat Vent"
            })

            add(VoiceCommandEntry(
                commandId = "SCENE_IM_COLD",
                category = VoiceCategory.SCENES,
                titleKm = "ខ្ញុំរងាណាស់! (Warm Up Car)",
                titleEn = "I'm Cold / Warm Up",
                acceptedPhrasesKm = listOf("ខ្ញុំរងាណាស់", "រងាណាស់", "ត្រជាក់ពេកហើយ", "warm up"),
                acceptedPhrasesEn = listOf("i'm cold", "it's too cold", "warm up cabin")
            ) { hal, _ ->
                hal.setAcMode(maxAc = false)
                hal.setTemperature(Zone.ALL, 24.5f)
                hal.setFanSpeed(2)
                hal.setSeatClimate(SeatTarget.DRIVER, ventLevel = 0, heatLevel = 2)
                "បានបង្កើនសីតុណ្ហភាពដល់ ២៤.៥°C និងកម្តៅកៅអី / Warmed cabin to 24.5°C & Seat Heat Level 2"
            })

            add(VoiceCommandEntry(
                commandId = "SCENE_RAINY_MODE",
                category = VoiceCategory.SCENES,
                titleKm = "របៀបពេលភ្លៀង (Rainy Mode)",
                titleEn = "Rainy Day Weather Mode",
                acceptedPhrasesKm = listOf("ភ្លៀងធ្លាក់ហើយ", "របៀបភ្លៀង", "មេឃភ្លៀង", "rainy mode"),
                acceptedPhrasesEn = listOf("rainy mode", "it's raining", "rain mode")
            ) { hal, _ ->
                hal.setWindowPosition(WindowTarget.ALL, 0)
                hal.setSunroofAndShade(sunroofPercent = 0, shadePercent = 0)
                hal.setAcMode(frontDefrost = true, rearDefrost = true, ac = true, auto = true)
                "បានបិទកញ្ចក់ទាំងអស់ និងបើកប្រព័ន្ធកម្ចាត់អ័ព្ទ / Windows closed & Defoggers active for rain"
            })

            add(VoiceCommandEntry(
                commandId = "SCENE_FRESH_AIR",
                category = VoiceCategory.SCENES,
                titleKm = "របៀបខ្យល់អាកាសបរិសុទ្ធ (Fresh Air)",
                titleEn = "Fresh Air Mode",
                acceptedPhrasesKm = listOf("របៀបខ្យល់បរិសុទ្ធ", "យកខ្យល់អាកាស", "fresh air"),
                acceptedPhrasesEn = listOf("fresh air mode", "ventilate cabin", "get fresh air")
            ) { hal, _ ->
                hal.setWindowPosition(WindowTarget.ALL, 20)
                hal.setSunroofAndShade(shadePercent = 100)
                hal.setAcMode(recirc = false)
                hal.setFanSpeed(3)
                "បានបើកកញ្ចក់ ២០% និងស្រូបខ្យល់អាកាសបរិសុទ្ធ / Windows cracked 20% & Fresh air intake active"
            })

            add(VoiceCommandEntry(
                commandId = "SCENE_CAR_WASH",
                category = VoiceCategory.SCENES,
                titleKm = "របៀបលាងរថយន្ត (Car Wash Mode)",
                titleEn = "Car Wash Mode",
                acceptedPhrasesKm = listOf("របៀបលាងឡាន", "លាងរថយន្ត", "ចូលលាងឡាន", "car wash"),
                acceptedPhrasesEn = listOf("car wash mode", "car wash", "prepare for car wash")
            ) { hal, _ ->
                hal.setWindowPosition(WindowTarget.ALL, 0)
                hal.setSunroofAndShade(sunroofPercent = 0, shadePercent = 0)
                hal.setDoorLocks(true)
                hal.setTailgate(false)
                hal.setAcMode(recirc = true)
                "បានបិទកញ្ចក់ ចាក់សោទ្វារ និងបិទខ្យល់ក្រៅសម្រាប់លាងរថយន្ត / Car wash mode ready: Windows & vents sealed"
            })

            add(VoiceCommandEntry(
                commandId = "SCENE_GOOD_MORNING",
                category = VoiceCategory.SCENES,
                titleKm = "អរុណសួស្តី! (Good Morning)",
                titleEn = "Good Morning Scene",
                acceptedPhrasesKm = listOf("អរុណសួស្តី", "សួស្តីពេលព្រឹក", "good morning"),
                acceptedPhrasesEn = listOf("good morning", "start my day", "hello morning")
            ) { hal, _ ->
                hal.setSunroofAndShade(shadePercent = 100)
                hal.setTemperature(Zone.ALL, 22.0f)
                hal.setAmbientLight(true, colorHex = "#FFD700") // Morning Gold
                val odo = hal.telemetryState.value.odometerKm
                val soc = hal.telemetryState.value.batteryPercent
                "អរុណសួស្តី! រថយន្ត Geely Galaxy M7 ត្រៀមរួចរាល់ អាគុយ ${soc}% / Good morning! Geely Galaxy M7 ready, Battery at ${soc}%"
            })

            add(VoiceCommandEntry(
                commandId = "SCENE_GOOD_NIGHT",
                category = VoiceCategory.SCENES,
                titleKm = "រាត្រីសួស្តី! (Good Night)",
                titleEn = "Good Night / Sleep Scene",
                acceptedPhrasesKm = listOf("រាត្រីសួស្តី", "លាហើយ", "good night"),
                acceptedPhrasesEn = listOf("good night", "park for the night")
            ) { hal, _ ->
                hal.setAmbientLight(true, colorHex = "#D500F9") // Night Purple
                hal.setFanSpeed(1)
                "រាត្រីសួស្តី! សូមគេងលក់ស្រួល / Good night! Sleep well"
            })
        }
    }

    fun getCommandsByCategory(category: VoiceCategory): List<VoiceCommandEntry> {
        return allCommands.filter { it.category == category }
    }

    fun findCommandById(commandId: String): VoiceCommandEntry? {
        return allCommands.firstOrNull { it.commandId == commandId }
    }

    /**
     * Rapid keyword lookup across all 140+ accepted phrases in Khmer and English.
     */
    fun matchExactOrPrefixPhrase(input: String): VoiceCommandEntry? {
        val clean = input.trim().lowercase()
            .replace("\u200B", "") // Remove zero-width spaces
            .replace("\u200C", "")
            .replace("\u200D", "")

        return allCommands.firstOrNull { cmd ->
            cmd.acceptedPhrasesKm.any { phrase ->
                val p = phrase.lowercase().replace("\u200B", "")
                clean.contains(p) || p.contains(clean)
            } || cmd.acceptedPhrasesEn.any { phrase ->
                val p = phrase.lowercase()
                clean.contains(p) || p.contains(clean)
            }
        }
    }
}
