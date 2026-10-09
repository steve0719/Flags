package uz.bayroq

data class Country(val code: String, val name: String) {
    val flag: String = code.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

val COUNTRIES = listOf(
    Country("UZ", "O'zbekiston"), Country("US", "AQSh"), Country("RU", "Rossiya"),
    Country("TR", "Turkiya"), Country("GB", "Buyuk Britaniya"), Country("DE", "Germaniya"),
    Country("FR", "Fransiya"), Country("JP", "Yaponiya"), Country("KR", "Janubiy Koreya"),
    Country("CN", "Xitoy"), Country("IT", "Italiya"), Country("ES", "Ispaniya"),
    Country("BR", "Braziliya"), Country("AR", "Argentina"), Country("CA", "Kanada"),
    Country("IN", "Hindiston"), Country("EG", "Misr"), Country("SA", "Saudiya Arabistoni"),
    Country("AE", "BAA"), Country("KZ", "Qozog'iston"), Country("KG", "Qirg'iziston"),
    Country("TJ", "Tojikiston"), Country("TM", "Turkmaniston"), Country("AZ", "Ozarbayjon"),
    Country("UA", "Ukraina"), Country("PL", "Polsha"), Country("NL", "Niderlandiya"),
    Country("SE", "Shvetsiya"), Country("CH", "Shveytsariya"), Country("AU", "Avstraliya"),
    Country("MX", "Meksika"), Country("PT", "Portugaliya"), Country("GR", "Gretsiya"),
    Country("ID", "Indoneziya"), Country("MY", "Malayziya")
)
