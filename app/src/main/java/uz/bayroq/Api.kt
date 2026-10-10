package uz.bayroq

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Api {
    // Server tayyor bo'lgach, shu yerga HTTPS manzil yoziladi.
    // Masalan: "https://api.sizningdomen.uz"
    // Bo'sh turganda ilova sinov uchun vaqtinchalik ID yaratadi.
    val BASE: String = ""

    fun register(): String? {
        if (BASE.isEmpty()) return testId()
        val c = URL("$BASE/register.php").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 8000
            c.readTimeout = 8000
            if (c.responseCode != 200) return null
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val id = JSONObject(body).optString("id")
            return if (Regex("\\d{11}").matches(id)) id else null
        } finally {
            c.disconnect()
        }
    }

    private fun testId(): String =
        (1..9).random().toString() + (1..10).joinToString("") { (0..9).random().toString() }
}
