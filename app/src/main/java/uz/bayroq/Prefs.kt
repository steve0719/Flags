package uz.bayroq

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("app", Context.MODE_PRIVATE)

    fun id(c: Context): String? = sp(c).getString("app_id", null)
    fun setId(c: Context, v: String) = sp(c).edit().putString("app_id", v).apply()

    fun lang(c: Context): String = sp(c).getString("lang", "uz") ?: "uz"
    fun sound(c: Context): Boolean = sp(c).getBoolean("sound", true)
    fun vib(c: Context): Boolean = sp(c).getBoolean("vib", true)

    fun set(c: Context, lang: String, sound: Boolean, vib: Boolean) {
        sp(c).edit().putString("lang", lang).putBoolean("sound", sound).putBoolean("vib", vib).apply()
    }

    // ---------- O'yinni 24 soatga ochish ----------

    // Telefon soatini orqaga surib vaqtni uzaytirishdan himoya:
    // oxirgi ko'rilgan vaqt saqlanadi va soat undan orqaga ketmaydi.
    private fun now(c: Context): Long {
        val real = System.currentTimeMillis()
        val seen = sp(c).getLong("seen", 0L)
        val cur = maxOf(real, seen)
        if (cur != seen) sp(c).edit().putLong("seen", cur).apply()
        return cur
    }

    // Ochiq qolgan soniyalar (0 bo'lsa, o'yin yopiq)
    fun unlockLeft(c: Context, game: String): Int {
        val exp = sp(c).getLong("unlock_$game", 0L)
        return maxOf(0L, (exp - now(c)) / 1000).toInt()
    }

    fun unlock(c: Context, game: String, hours: Long = 24) {
        sp(c).edit().putLong("unlock_$game", now(c) + hours * 3600_000L).apply()
    }
}
