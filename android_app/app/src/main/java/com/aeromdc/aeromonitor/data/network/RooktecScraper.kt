package com.aeromdc.aeromonitor.data.network

import android.util.Log
import com.aeromdc.aeromonitor.data.model.ScrapedTurbine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * RooktecScraper — Kotlin port of Backend/ingestion/collector.py
 *
 * Replicates the same multi-stage login + HTML card parsing flow:
 *   1. GET https://www.rooktec.in/wmapp  → capture cookies + CSRF token
 *   2. POST credentials + CSRF          → validate redirect URL ≠ login page
 *   3. GET reload_status_tst.php         → parse div.runningdiv cards
 *   4. Return list<ScrapedTurbine>
 *
 * Uses OkHttp (cookiejar handles session) + Jsoup (equivalent of BeautifulSoup).
 */
class RooktecScraper(
    private val baseUrl: String = "https://www.rooktec.in/wmapp",
    private val username: String = "smb",
    private val password: String = "wind@smb",
) {
    companion object {
        private const val TAG = "RooktecScraper"
        private val CSRF_NAMES = listOf(
            "csrf_token", "_token", "csrfmiddlewaretoken", "__RequestVerificationToken"
        )
        private val WS_PATTERN = Pattern.compile("Ws\\s*:\\s*([0-9.,]+)", Pattern.CASE_INSENSITIVE)
        private val KW_PATTERN = Pattern.compile("Kw\\s*:\\s*([0-9.,]+)", Pattern.CASE_INSENSITIVE)
        private val KWH_PATTERN = Pattern.compile("^\\d+(\\.\\d+)?$")
        private val DT_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2},\\s*\\d{1,2}:\\d{2}$")
        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd,HH:mm")
    }

    private val cookieJar = InMemoryCookieJar()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor(
            HttpLoggingInterceptor { msg -> Log.d(TAG, msg) }
                .apply { level = HttpLoggingInterceptor.Level.BASIC }
        )
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
                )
                .build()
            chain.proceed(request)
        }
        .build()

    /**
     * Full scrape cycle: login → fetch dashboard → parse cards.
     * Returns an empty list on failure (caller handles error state).
     */
    suspend fun scrape(): Result<List<ScrapedTurbine>> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Authenticate
            if (!login()) {
                return@withContext Result.failure(Exception("Authentication failed"))
            }
            // Step 2: Fetch dashboard HTML
            val dashboardHtml = fetchDashboard()
                ?: return@withContext Result.failure(Exception("Failed to fetch dashboard"))

            // Step 3: Parse turbine cards
            val cards = parseCards(dashboardHtml)
            if (cards.isEmpty()) {
                Log.w(TAG, "No turbine cards found in HTML response")
            }
            Result.success(cards)
        } catch (e: Exception) {
            Log.e(TAG, "Scrape failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun login(): Boolean {
        return try {
            // GET login page — captures cookies
            val loginReq = Request.Builder().url(baseUrl).get().build()
            val loginResp = client.newCall(loginReq).execute()
            val loginHtml = loginResp.body?.string() ?: return false
            val loginDoc = Jsoup.parse(loginHtml)

            // Extract CSRF token
            var csrfName: String? = null
            var csrfValue: String? = null
            for (name in CSRF_NAMES) {
                val input = loginDoc.selectFirst("input[name=$name]")
                if (input != null && input.attr("value").isNotEmpty()) {
                    csrfName = name
                    csrfValue = input.attr("value")
                    break
                }
            }
            // Also check meta tag
            if (csrfName == null) {
                val meta = loginDoc.selectFirst("meta[name=csrf-token]")
                if (meta != null) {
                    csrfName = "csrf-token"
                    csrfValue = meta.attr("content")
                }
            }

            // Find form action URL
            val formTag = loginDoc.selectFirst("form")
            val actionPath = formTag?.attr("action") ?: ""
            val postUrl = when {
                actionPath.startsWith("http") -> actionPath
                actionPath.startsWith("/") -> {
                    val parsed = okhttp3.HttpUrl.parse(baseUrl)!!
                    "${parsed.scheme()}://${parsed.host()}$actionPath"
                }
                actionPath.isNotEmpty() -> "$baseUrl/$actionPath"
                else -> baseUrl
            }

            // Build form body
            val formBody = FormBody.Builder()
                .add("user_nameTxt", username)
                .add("pass_wordTxt", password)
                .add("submit", "Log in")
                .apply {
                    if (csrfName != null && csrfValue != null) {
                        add(csrfName, csrfValue)
                    }
                }
                .build()

            val authReq = Request.Builder()
                .url(postUrl)
                .post(formBody)
                .header("Referer", baseUrl)
                .build()

            val authResp = client.newCall(authReq).execute()
            val finalUrl = authResp.request.url.toString()

            if ("login" in finalUrl.lowercase() || "signin" in finalUrl.lowercase()) {
                Log.e(TAG, "Still on login page after POST: $finalUrl")
                false
            } else {
                Log.i(TAG, "Successfully authenticated. Final URL: $finalUrl")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login exception: ${e.message}", e)
            false
        }
    }

    private fun fetchDashboard(): String? {
        return try {
            val dashUrl = "$baseUrl/reload_status_tst.php"
            val req = Request.Builder().url(dashUrl).get().build()
            val resp = client.newCall(req).execute()

            // Check for session expiry redirect
            val finalUrl = resp.request.url.toString()
            if ("login" in finalUrl.lowercase()) {
                Log.w(TAG, "Session expired — redirected to login")
                return null
            }
            resp.body?.string()
        } catch (e: Exception) {
            Log.e(TAG, "Dashboard fetch failed: ${e.message}", e)
            null
        }
    }

    private fun parseCards(html: String): List<ScrapedTurbine> {
        val doc = Jsoup.parse(html)
        val allCards = doc.select("div.runningdiv")
        val cards = allCards.filter { !it.classNames().contains("runningdivdimmed") }

        if (cards.isEmpty()) {
            Log.w(TAG, "No div.runningdiv cards found. Page may require JS rendering.")
            return emptyList()
        }

        val collectedAt = LocalDateTime.now().toString()
        val results = mutableListOf<ScrapedTurbine>()

        cards.forEachIndexed { index, card ->
            try {
                results.add(parseCard(card, index, collectedAt))
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing card $index: ${e.message}")
            }
        }

        Log.i(TAG, "Parsed ${results.size} turbine cards")
        return results
    }

    private fun parseCard(card: Element, index: Int, collectedAt: String): ScrapedTurbine {
        val directDivs = card.children().filter { it.tagName() == "div" }

        // Turbine name — first <p> in first child div
        val turbineName = directDivs.getOrNull(0)
            ?.selectFirst("p")
            ?.text()?.clean()
            ?: "WT-${String.format("%02d", index + 1)}"

        // Status — title attr or text of <p> in second child div
        val statusEl = directDivs.getOrNull(1)?.selectFirst("p")
        val status = (statusEl?.attr("title")?.takeIf { it.isNotBlank() }
            ?: statusEl?.text())?.clean()?.uppercase() ?: "UNKNOWN"

        // Today kWh — span inside element containing "Today" and "Kwh"
        var todayKwh: Double? = null
        card.select("span").forEach { span ->
            val parentText = span.parent()?.text()?.clean() ?: ""
            if ("Today" in parentText && "Kwh" in parentText) {
                val val_ = span.text().clean()
                if (KWH_PATTERN.matcher(val_).matches()) {
                    todayKwh = val_.toDoubleOrNull()
                    return@forEach
                }
            }
        }

        // Wind speed + kW via regex on div text
        var windSpeed: Double? = null
        var kw: Double? = null
        card.select("div").forEach { div ->
            val text = div.text().clean()
            if (windSpeed == null) {
                val m = WS_PATTERN.matcher(text)
                if (m.find()) windSpeed = m.group(1).replace(",", ".").toDoubleOrNull()
            }
            if (kw == null) {
                val m = KW_PATTERN.matcher(text)
                if (m.find()) kw = m.group(1).replace(",", ".").toDoubleOrNull()
            }
        }

        // Turbine datetime — <p> matching YYYY-MM-DD,HH:MM
        var turbineDatetime: String? = null
        card.select("p").forEach { p ->
            val text = p.text().clean()
            if (DT_PATTERN.matcher(text).matches()) {
                turbineDatetime = text.replace(" ", "")
                return@forEach
            }
        }

        return ScrapedTurbine(
            cardNo = "${index + 1}",
            turbineName = turbineName,
            status = status,
            todayKwh = todayKwh,
            windSpeed = windSpeed,
            kw = kw,
            turbineDatetime = turbineDatetime,
            rawText = card.text().clean(),
            collectedAt = collectedAt,
        )
    }

    private fun String.clean(): String =
        replace("\u00a0", " ").replace(Regex("\\s+"), " ").trim()
}
