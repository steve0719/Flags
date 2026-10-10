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
}
