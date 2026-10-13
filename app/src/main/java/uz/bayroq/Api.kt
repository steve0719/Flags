package uz.bayroq

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Api {
    // Server manzili (oxirida / bo'lmasin)
    val BASE: String = "https://6accab91353f6.xvest5.ru"

    fun register(): String? {
        if (BASE.isEmpty()) return testId()
        val c = URL("$BASE/register.php").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.doOutput = true
            c.outputStream.use { it.write("a=1".toByteArray()) }
            if (c.responseCode != 200) return null
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val id = JSONObject(body).optString("id")
            return if (Regex("\\d{11}").matches(id)) id else null
        } finally {
            c.disconnect()
        }
    }

    // Faqat BASE bo'sh bo'lganda, sinov uchun
    private fun testId(): String =
        (1..9).random().toString() + (1..10).joinToString("") { (0..9).random().toString() }
}
