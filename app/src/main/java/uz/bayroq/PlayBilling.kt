package uz.bayroq

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Google Play Billing orqali Premium sotib olish.
 * Mahsulotlar Play Console'da "bir martalik, iste'mol qilinadigan" (consumable) sifatida yaratiladi:
 * premium_1m, premium_3m, premium_6m, premium_25m.
 * Xariddan keyin: server Google bilan tekshiradi -> Premium qo'shiladi -> xarid "iste'mol qilinadi" (qayta sotib olish mumkin).
 */
class PlayBilling(private val act: Activity, private val js: (String) -> Unit) : PurchasesUpdatedListener {
    private val months = linkedMapOf("premium_1m" to 1, "premium_3m" to 3, "premium_6m" to 6, "premium_25m" to 25)
    private var details: Map<String, ProductDetails> = emptyMap()

    private val client: BillingClient = BillingClient.newBuilder(act)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private fun ui(code: String) {
        act.runOnUiThread { js(code) }
    }

    private fun connect(then: () -> Unit, fail: () -> Unit) {
        if (client.isReady) {
            then()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) {
                if (r.responseCode == BillingClient.BillingResponseCode.OK) then() else fail()
            }

            override fun onBillingServiceDisconnected() {}
        })
    }

    // Narxlarni Google Play'dan oladi (foydalanuvchining valyutasida)
    fun loadProducts() {
        connect({
            val list = months.keys.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            }
            val params = QueryProductDetailsParams.newBuilder().setProductList(list).build()
            client.queryProductDetailsAsync(params) { r, pds ->
                val m = HashMap<String, ProductDetails>()
                val arr = JSONArray()
                if (r.responseCode == BillingClient.BillingResponseCode.OK) {
                    for (id in months.keys) {
                        val pd = pds.firstOrNull { it.productId == id } ?: continue
                        val price = pd.oneTimePurchaseOfferDetails?.formattedPrice ?: continue
                        m[id] = pd
                        arr.put(JSONObject().put("id", id).put("months", months[id]).put("price", price))
                    }
                }
                details = m
                ui("window.onProducts&&window.onProducts($arr)")
            }
        }, { ui("window.onProducts&&window.onProducts([])") })
    }

    fun buy(productId: String) {
        connect({
            val pd = details[productId]
            if (pd == null) {
                ui("window.onBuy&&window.onBuy('unavail')")
            } else {
                val pp = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd).build()
                val b = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(pp))
                Prefs.id(act)?.let { b.setObfuscatedAccountId(it) }
                val r = client.launchBillingFlow(act, b.build())
                if (r.responseCode != BillingClient.BillingResponseCode.OK) {
                    ui("window.onBuy&&window.onBuy('fail')")
                }
            }
        }, { ui("window.onBuy&&window.onBuy('fail')") })
    }

    override fun onPurchasesUpdated(r: BillingResult, purchases: MutableList<Purchase>?) {
        when (r.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach { handle(it, true) }
            BillingClient.BillingResponseCode.USER_CANCELED -> ui("window.onBuy&&window.onBuy('cancel')")
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restore(true)
            else -> ui("window.onBuy&&window.onBuy('fail')")
        }
    }

    // Ilova ochilganda: avval to'langan, lekin serverga yetib bormagan xaridlarni qayta yuboradi
    fun restore(announce: Boolean = false) {
        connect({
            val q = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
            client.queryPurchasesAsync(q) { r, list ->
                if (r.responseCode == BillingClient.BillingResponseCode.OK) {
                    list.forEach { handle(it, announce) }
                }
            }
        }, {})
    }

    private fun handle(p: Purchase, announce: Boolean) {
        if (p.purchaseState == Purchase.PurchaseState.PENDING) {
            if (announce) ui("window.onBuy&&window.onBuy('pending')")
            return
        }
        if (p.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val pid = p.products.firstOrNull() ?: return
        Thread {
            val code = verify(pid, p.purchaseToken)
            if (code == 1) {
                act.runOnUiThread {
                    client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { _, _ -> }
                }
                Premium.sync(act)
                ui("window.onPremium&&window.onPremium()")
                if (announce) ui("window.onBuy&&window.onBuy('ok')")
            } else if (announce) {
                ui("window.onBuy&&window.onBuy('" + (if (code == -1) "fail" else "verify") + "')")
            }
        }.start()
    }

    // 1 = server tasdiqladi, -1 = xarid yaroqsiz, 0 = server bilan bog'lanib bo'lmadi (keyin qayta uriniladi)
    private fun verify(pid: String, token: String): Int {
        val id = Prefs.id(act) ?: return 0
        val txt = Records.post(
            "/play_verify.php",
            "id=" + id + "&pid=" + URLEncoder.encode(pid, "UTF-8") +
                "&token=" + URLEncoder.encode(token, "UTF-8") + "&pkg=" + act.packageName
        ) ?: return 0
        return try {
            val j = JSONObject(txt)
            if (j.optBoolean("ok")) 1 else if (j.optString("error") == "invalid") -1 else 0
        } catch (e: Exception) {
            0
        }
    }

    fun close() {
        try {
            client.endConnection()
        } catch (e: Exception) {
        }
    }
}
