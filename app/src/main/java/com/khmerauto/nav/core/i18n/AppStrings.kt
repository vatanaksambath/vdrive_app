package com.khmerauto.nav.core.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLanguage {
    KHMER,
    ENGLISH
}

/**
 * Reactive Language Manager allowing seamless bilingual switching (Khmer / English)
 * across the entire Geely Galaxy M7 Flyme Auto UI without activity recreation.
 */
object LanguageManager {
    private val _currentLanguage = MutableStateFlow(AppLanguage.KHMER)
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    fun setLanguage(lang: AppLanguage) {
        _currentLanguage.value = lang
    }

    fun toggleLanguage() {
        _currentLanguage.value = if (_currentLanguage.value == AppLanguage.KHMER) {
            AppLanguage.ENGLISH
        } else {
            AppLanguage.KHMER
        }
    }
}

val LocalAppStrings = compositionLocalOf<AppStrings> { AppStrings.Khmer }

sealed class AppStrings(
    val appTitle: String,
    val cockpit: String,
    val cabin: String,
    val map: String,
    val voice: String,
    val spending: String,
    val weather: String,
    val settings: String,
    val diagnostics: String,
    
    // Vehicle Telemetry
    val battery: String,
    val usableKwh: String,
    val evRange: String,
    val fuelRange: String,
    val combinedRange: String,
    val odometer: String,
    val speed: String,
    val speedUnit: String,
    val gearP: String,
    val gearR: String,
    val gearN: String,
    val gearD: String,
    val readyToDrive: String,
    val parked: String,
    val charging: String,
    val chargePower: String,

    // Climate & Cabin
    val climateControl: String,
    val power: String,
    val driver: String,
    val passenger: String,
    val fanSpeed: String,
    val ac: String,
    val auto: String,
    val maxAc: String,
    val recirc: String,
    val frontDefrost: String,
    val rearDefrost: String,
    val syncTemp: String,

    // Windows & Roof
    val windowsAndRoof: String,
    val openAll: String,
    val closeAll: String,
    val ventAll: String,
    val windowFl: String,
    val windowFr: String,
    val windowRl: String,
    val windowRr: String,
    val sunroof: String,
    val roofShade: String,

    // Doors & Tailgate
    val doorsAndTailgate: String,
    val centralLock: String,
    val locked: String,
    val unlocked: String,
    val tailgate: String,
    val openTailgate: String,
    val closeTailgate: String,
    val tailgateMotionWarning: String,

    // Seats & Ambient Light
    val seatClimate: String,
    val seatVentilation: String,
    val seatHeating: String,
    val levelOff: String,
    val levelLow: String,
    val levelMed: String,
    val levelHigh: String,
    val ambientLight: String,

    // TPMS
    val tpmsTitle: String,
    val frontLeftTire: String,
    val frontRightTire: String,
    val rearLeftTire: String,
    val rearRightTire: String,
    val tirePressureNormal: String,
    val tirePressureWarning: String,

    // Calendar & Holy Day
    val holyDayBadge: String,
    val holyDayDesc: String,
    val solarDay: String,
    val solarNight: String,
    val solarAuto: String,
    
    // Spending & Units
    val spendingInRiel: String,
    val rielUnit: String,
    val kwhPer100Km: String,
    val todayCost: String,
    val monthCost: String,

    // Actions & Modes
    val apply: String,
    val cancel: String,
    val save: String,
    val connect: String,
    val connected: String,
    val disconnected: String,
    val modeSimulator: String,
    val modeTablet: String,
    val modeCarHost: String
) {
    object Khmer : AppStrings(
        appTitle = "VDrive (វីដ្រាយ)",
        cockpit = "ផ្ទាំងបញ្ជា",
        cabin = "បន្ទប់រថយន្ត",
        map = "ផែនទី",
        voice = "បញ្ជាសម្លេង",
        spending = "ការចំណាយ",
        weather = "អាកាសធាតុ",
        settings = "ការកំណត់",
        diagnostics = "ត្រួតពិនិត្យ VHAL",

        battery = "អាគុយ",
        usableKwh = "ថាមពលប្រើប្រាស់",
        evRange = "ចម្ងាយអគ្គិសនី",
        fuelRange = "ចម្ងាយសាំង",
        combinedRange = "ចម្ងាយសរុប",
        odometer = "ចម្ងាយរត់បាន",
        speed = "ល្បឿន",
        speedUnit = "គ.ម/ម៉",
        gearP = "P",
        gearR = "R",
        gearN = "N",
        gearD = "D",
        readyToDrive = "រួចរាល់សម្រាប់បើកបរ",
        parked = "ចតរថយន្ត",
        charging = "កំពុងសាកថ្ម",
        chargePower = "កម្លាំងសាក",

        climateControl = "ប្រព័ន្ធត្រជាក់ & កម្តៅ",
        power = "ថាមពល",
        driver = "អ្នកបើកបរ",
        passenger = "អ្នករួមដំណើរ",
        fanSpeed = "កម្លាំងកង្ហារ",
        ac = "A/C",
        auto = "AUTO",
        maxAc = "MAX A/C",
        recirc = "ខ្យល់វិលជុំ",
        frontDefrost = "កំចាត់អ័ព្ទមុខ",
        rearDefrost = "កំចាត់អ័ព្ទក្រោយ",
        syncTemp = "ធ្វើស៊ីសង្វាក់",

        windowsAndRoof = "កញ្ចក់ទ្វារ & ដំបូលកញ្ចក់",
        openAll = "បើកទាំងអស់",
        closeAll = "បិទទាំងអស់",
        ventAll = "ចំហខ្យល់ (១៥%)",
        windowFl = "មុខឆ្វេង",
        windowFr = "មុខស្តាំ",
        windowRl = "ក្រោយឆ្វេង",
        windowRr = "ក្រោយស្តាំ",
        sunroof = "ដំបូលកញ្ចក់ Sunroof",
        roofShade = "វាំងននដំបូល",

        doorsAndTailgate = "សោរទ្វារ & គូទក្រោយ",
        centralLock = "សោរកណ្តាល",
        locked = "ចាក់សោរ",
        unlocked = "ដោះសោរ",
        tailgate = "គូទក្រោយឆ្លាតវៃ",
        openTailgate = "បើកគូទក្រោយ",
        closeTailgate = "បិទគូទក្រោយ",
        tailgateMotionWarning = "មិនអាចបើកគូទក្រោយពេលរថយន្តកំពុងបើកបរបានទេ! សូមចតរថយន្តជាមុនសិន។",

        seatClimate = "កៅអីត្រជាក់ & កម្តៅ",
        seatVentilation = "ខ្យល់ត្រជាក់កៅអី",
        seatHeating = "កម្តៅកៅអី",
        levelOff = "បិទ",
        levelLow = "កម្រិត ១",
        levelMed = "កម្រិត ២",
        levelHigh = "កម្រិត ៣",
        ambientLight = "ភ្លើងបំភ្លឺក្នុងឡាន ២៥៦ ពណ៌",

        tpmsTitle = "សម្ពាធកង់រថយន្ត (TPMS)",
        frontLeftTire = "កង់មុខឆ្វេង",
        frontRightTire = "កង់មុខស្តាំ",
        rearLeftTire = "កង់ក្រោយឆ្វេង",
        rearRightTire = "កង់ក្រោយស្តាំ",
        tirePressureNormal = "សម្ពាធប្រក្រតី",
        tirePressureWarning = "សម្ពាធខុសប្រក្រតី",

        holyDayBadge = "ថ្ងៃសីល",
        holyDayDesc = "ថ្ងៃនេះជាថ្ងៃសីល (ឧបោសថសីល)",
        solarDay = "ពេលថ្ងៃ",
        solarNight = "ពេលយប់",
        solarAuto = "ស្វ័យប្រវត្តិ",

        spendingInRiel = "ការចំណាយជារៀល",
        rielUnit = "៛",
        kwhPer100Km = "kWh / ១០០ គ.ម",
        todayCost = "ចំណាយថ្ងៃនេះ",
        monthCost = "ចំណាយប្រចាំខែ",

        apply = "យល់ព្រម",
        cancel = "បោះបង់",
        save = "រក្សាទុក",
        connect = "ភ្ជាប់",
        connected = "បានភ្ជាប់",
        disconnected = "មិនទាន់ភ្ជាប់",
        modeSimulator = "ម៉ាស៊ីនសាកល្បង",
        modeTablet = "ឧបករណ៍ Tablet ពីចម្ងាយ",
        modeCarHost = "ដំណើរការលើរថយន្តផ្ទាល់"
    )

    object English : AppStrings(
        appTitle = "VDrive",
        cockpit = "Cockpit",
        cabin = "Cabin",
        map = "Map",
        voice = "Voice",
        spending = "Spending",
        weather = "Weather",
        settings = "Settings",
        diagnostics = "VHAL Diagnostic",

        battery = "Battery",
        usableKwh = "Usable Energy",
        evRange = "EV Range",
        fuelRange = "Fuel Range",
        combinedRange = "Combined Range",
        odometer = "Odometer",
        speed = "Speed",
        speedUnit = "km/h",
        gearP = "P",
        gearR = "R",
        gearN = "N",
        gearD = "D",
        readyToDrive = "Ready to Drive",
        parked = "Parked",
        charging = "Charging",
        chargePower = "Charge Power",

        climateControl = "Climate Control",
        power = "Power",
        driver = "Driver",
        passenger = "Passenger",
        fanSpeed = "Fan Speed",
        ac = "A/C",
        auto = "AUTO",
        maxAc = "MAX A/C",
        recirc = "RECIRC",
        frontDefrost = "Front Defrost",
        rearDefrost = "Rear Defrost",
        syncTemp = "Sync",

        windowsAndRoof = "Windows & Roof",
        openAll = "Open All",
        closeAll = "Close All",
        ventAll = "Vent All (15%)",
        windowFl = "Front Left",
        windowFr = "Front Right",
        windowRl = "Rear Left",
        windowRr = "Rear Right",
        sunroof = "Panoramic Sunroof",
        roofShade = "Electric Shade",

        doorsAndTailgate = "Doors & Tailgate",
        centralLock = "Central Lock",
        locked = "Locked",
        unlocked = "Unlocked",
        tailgate = "Smart Tailgate",
        openTailgate = "Open Tailgate",
        closeTailgate = "Close Tailgate",
        tailgateMotionWarning = "Cannot open tailgate while vehicle is in motion! Please shift to Park first.",

        seatClimate = "Seat Climate",
        seatVentilation = "Seat Ventilation",
        seatHeating = "Seat Heating",
        levelOff = "Off",
        levelLow = "Level 1",
        levelMed = "Level 2",
        levelHigh = "Level 3",
        ambientLight = "256-Color Ambient Light",

        tpmsTitle = "Tire Pressure (TPMS)",
        frontLeftTire = "Front Left",
        frontRightTire = "Front Right",
        rearLeftTire = "Rear Left",
        rearRightTire = "Rear Right",
        tirePressureNormal = "Normal",
        tirePressureWarning = "Warning",

        holyDayBadge = "Holy Day",
        holyDayDesc = "Today is a Buddhist Holy Day",
        solarDay = "Day",
        solarNight = "Night",
        solarAuto = "Auto",

        spendingInRiel = "Spending (KHR)",
        rielUnit = "KHR",
        kwhPer100Km = "kWh / 100 km",
        todayCost = "Today's Cost",
        monthCost = "Monthly Cost",

        apply = "Apply",
        cancel = "Cancel",
        save = "Save",
        connect = "Connect",
        connected = "Connected",
        disconnected = "Disconnected",
        modeSimulator = "Simulator",
        modeTablet = "Tablet Remote",
        modeCarHost = "Car Native Host"
    )

    companion object {
        fun get(lang: AppLanguage): AppStrings = when (lang) {
            AppLanguage.KHMER -> Khmer
            AppLanguage.ENGLISH -> English
        }
    }
}
