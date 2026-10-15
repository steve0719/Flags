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

    // ---------- Vaqt (soatni orqaga surishdan himoya) ----------

    // Oxirgi ko'rilgan vaqt saqlanadi va soat undan orqaga ketmaydi.
    private fun now(c: Context): Long {
        val real = System.currentTimeMillis()
        val seen = sp(c).getLong("seen", 0L)
        val cur = maxOf(real, seen)
        if (cur != seen) sp(c).edit().putLong("seen", cur).apply()
        return cur
    }

    // ---------- O'yinni 24 soatga ochish (reklama orqali) ----------

    // Ochiq qolgan soniyalar (0 bo'lsa, o'yin yopiq)
    fun unlockLeft(c: Context, game: String): Int {
        val exp = sp(c).getLong("unlock_$game", 0L)
        return maxOf(0L, (exp - now(c)) / 1000).toInt()
    }

    fun unlock(c: Context, game: String, hours: Long = 24) {
        sp(c).edit().putLong("unlock_$game", now(c) + hours * 3600_000L).apply()
    }

    // ---------- Premium ----------

    // Serverdan kelgan tugash vaqti (unix soniya) va serverning hozirgi vaqti saqlanadi.
    // Serverning haqiqiy vaqti "ko'rilgan vaqt"ni ham to'g'rilaydi.
    fun setPremium(c: Context, until: Long, serverNow: Long) {
        val e = sp(c).edit().putLong("premium_until", until)
        if (serverNow > 0L) e.putLong("seen", serverNow * 1000L)
        e.apply()
    }

    fun premiumUntil(c: Context): Long = sp(c).getLong("premium_until", 0L)

    // Premium tugashiga qolgan soniyalar (0 bo'lsa, Premium yo'q)
    fun premiumLeft(c: Context): Int {
        val left = premiumUntil(c) - now(c) / 1000L
        return if (left > 0L) minOf(left, Int.MAX_VALUE.toLong()).toInt() else 0
    }

    fun isPremium(c: Context): Boolean = premiumLeft(c) > 0

    // Botning username i (serverdan keladi)
    fun setBot(c: Context, v: String) = sp(c).edit().putString("bot", v).apply()
    fun bot(c: Context): String = sp(c).getString("bot", "") ?: ""

    // ---------- Faol o'yin vaqti (Rank tizimi) ----------
    // Jami vaqt = serverdagi asos + yuborilayotgan paket + hali yuborilmaganlar.
    private val playLock = Any()

    fun playTotal(c: Context): Long {
        val s = sp(c)
        return s.getLong("p_base", 0L) + s.getLong("p_inflight", 0L) + s.getLong("p_pend", 0L)
    }

    // Faol o'yin soniyalarini qo'shadi
    fun addPlay(c: Context, sec: Int) {
        if (sec <= 0) return
        synchronized(playLock) {
            val s = sp(c)
            s.edit().putLong("p_pend", s.getLong("p_pend", 0L) + sec).apply()
        }
    }

    // Serverga yuboriladigan paket: (raqami, soniyalar). Soniya 0 bo'lsa, yuboradigan narsa yo'q.
    fun playBatch(c: Context): Pair<Int, Long> {
        synchronized(playLock) {
            val s = sp(c)
            var infl = s.getLong("p_inflight", 0L)
            if (infl == 0L) {
                val pend = s.getLong("p_pend", 0L)
                if (pend > 0L) {
                    infl = pend
                    s.edit()
                        .putLong("p_inflight", infl)
                        .putLong("p_pend", 0L)
                        .putInt("p_iseq", s.getInt("p_nextseq", 1))
                        .apply()
                }
            }
            return Pair(s.getInt("p_iseq", s.getInt("p_nextseq", 1)), infl)
        }
    }

    // Server javobi: jami soniya va oxirgi qabul qilingan paket raqami
    fun playAck(c: Context, serverSec: Long, serverSeq: Int) {
        synchronized(playLock) {
            val s = sp(c)
            val e = s.edit()
            e.putLong("p_base", serverSec).putInt("p_nextseq", serverSeq + 1)
            val infl = s.getLong("p_inflight", 0L)
            val iseq = s.getInt("p_iseq", 1)
            if (infl > 0L) {
                if (serverSeq >= iseq) e.putLong("p_inflight", 0L)
                else e.putInt("p_iseq", serverSeq + 1)
            }
            e.apply()
        }
    }

    // Profil (reyting jadvali uchun serverga yuboriladi)
    fun setProfile(c: Context, name: String, country: String) {
        sp(c).edit().putString("pname", name).putString("pcountry", country).apply()
    }
    fun pname(c: Context): String = sp(c).getString("pname", "") ?: ""
    fun pcountry(c: Context): String = sp(c).getString("pcountry", "XX") ?: "XX"
}
