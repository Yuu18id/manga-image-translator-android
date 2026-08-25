package com.yuu18id.mangatranslator.domain.model

enum class Language(val displayName: String, val code: String, val nativeName: String) {
    ENG("English", "ENG", "English"),
    JPN("Japanese", "JPN", "日本語"),
    CHS("Simplified Chinese", "CHS", "简体中文"),
    CHT("Traditional Chinese", "CHT", "繁體中文"),
    KOR("Korean", "KOR", "한국어"),
    FRA("French", "FRA", "Français"),
    DEU("German", "DEU", "Deutsch"),
    ESP("Spanish", "ESP", "Español"),
    IND("Indonesian", "IND", "Bahasa Indonesia"),
    ITA("Italian", "ITA", "Italiano"),
    POR("Portuguese", "POR", "Português"),
    RUS("Russian", "RUS", "Русский"),
    VIE("Vietnamese", "VIE", "Tiếng Việt"),
    ARA("Arabic", "ARA", "العربية"),
    THA("Thai", "THA", "ไทย"),
    TUR("Turkish", "TUR", "Türkçe"),
    UKR("Ukrainian", "UKR", "Українська"),
    POL("Polish", "POL", "Polski"),
    NLD("Dutch", "NLD", "Nederlands"),
    CSY("Czech", "CSY", "Čeština"),
    HUN("Hungarian", "HUN", "Magyar"),
    ELL("Greek", "ELL", "Ελληνικά"),
    SWE("Swedish", "SWE", "Svenska"),
    ROM("Romanian", "ROM", "Română"),
    HRV("Croatian", "HRV", "Hrvatski")
}
