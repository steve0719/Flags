package uz.bayroq

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** O'yin rekordini serverga yuboradi va reyting sahifalarini oladi. */
object Records {
    fun post(path: String, body: String): String? {
        if (Api.BASE.isEmpty()) return null
        return try {
            val conn = URL(Api.BASE + path).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode != 200) {
                conn.disconnect()
                null
            } else {
                val txt = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                txt
            }
        } catch (e: Exception) {
            null
        }
    }

    // 0 = bajarilmadi, 1 = yuborildi, 2 = admin rekordni 0 qilgan (telefondagi rekord o'chirildi)
    fun upload(c: Context, game: String = "Bayroq"): Int {
        val id = Prefs.id(c) ?: return 0
        val txt = post(
            "/record.php",
            "id=" + id + "&game=" + game + "&cnt=" + Prefs.recCnt(c) + "&ms=" + Prefs.recMs(c) + "&ver=" + Prefs.recVer(c)
        ) ?: return 0
        return try {
            val j = JSONObject(txt)
            if (!j.optBoolean("ok")) {
                0
            } else if (j.optBoolean("reset")) {
                Prefs.clearRec(c, j.optInt("ver"))
                2
            } else {
                Prefs.saveRec(c, j.optInt("cnt"), j.optLong("ms"))
                1
            }
        } catch (e: Exception) {
            0
        }
    }

    // Reyting sahifasi (JSON matn). type: "level" yoki o'yin nomi
    fun fetch(c: Context, type: String, offset: Int, limit: Int): String? {
        val id = Prefs.id(c) ?: return null
        val txt = post(
            "/rating.php",
            "id=" + id + "&type=" + URLEncoder.encode(type, "UTF-8") + "&offset=" + offset + "&limit=" + limit
        ) ?: return null
        return try {
            JSONObject(txt)
            txt
        } catch (e: Exception) {
            null
        }
    }

    // Boshqa o'yinchining reytingdagi o'rnini qidirish (ID bo'yicha)
    fun find(c: Context, type: String, target: String): String? {
        val id = Prefs.id(c) ?: return null
        val txt = post(
            "/rating.php",
            "id=" + id + "&type=" + URLEncoder.encode(type, "UTF-8") + "&find=" + target + "&offset=0&limit=1"
        ) ?: return null
        return try {
            JSONObject(txt)
            txt
        } catch (e: Exception) {
            null
        }
    }
}
