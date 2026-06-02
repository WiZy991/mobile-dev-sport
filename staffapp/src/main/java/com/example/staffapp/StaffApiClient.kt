package com.example.staffapp

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.text.Charsets

class StaffApiClient(private val baseUrl: String) {
    private data class HttpResult(val code: Int, val body: String)

    fun register(email: String, name: String, password: String, role: String): StaffSession {
        val payload = JSONObject()
            .put("email", email)
            .put("name", name)
            .put("password", password)
            .put("role", role)
        return authRequest("/api/v1/staff/auth/register", payload)
    }

    fun login(email: String, password: String): StaffSession {
        val payload = JSONObject()
            .put("email", email)
            .put("password", password)
        return authRequest("/api/v1/staff/auth/login", payload)
    }

    fun refresh(refreshToken: String): StaffSession {
        val conn = openConnection("/api/v1/staff/auth/refresh", "POST")
        conn.setRequestProperty("Authorization", "Bearer $refreshToken")
        val json = requireJson(execute(conn))
        return StaffSession(
            accessToken = json.getString("token"),
            refreshToken = json.getString("refresh_token"),
            userEmail = json.getJSONObject("user").optString("email"),
        )
    }

    fun loadConfig(token: String): RoleConfig {
        val conn = openConnection("/api/v1/staff/config", "GET")
        conn.setRequestProperty("Authorization", "Bearer $token")
        val json = requireJson(execute(conn))
        return RoleConfig(
            roles = json.optJSONArray("roles").toStringList(),
            appSections = json.optJSONArray("appSections").toStringList(),
            adminSections = json.optJSONArray("adminSections").toStringList(),
            adminActions = json.optJSONArray("adminActions").toStringList(),
            featureFlags = json.optJSONObject("featureFlags").toBooleanMap(),
        )
    }

    fun checkAdminAction(token: String, section: String, action: String): Boolean {
        val conn = openConnection("/api/v1/staff/admin/action-check", "POST")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.doOutput = true
        val payload = JSONObject().put("section", section).put("action", action)
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
        return execute(conn).code in 200..299
    }

    fun loadAppData(token: String): StaffAppData {
        val conn = openConnection("/api/v1/staff/app/data", "GET")
        conn.setRequestProperty("Authorization", "Bearer $token")
        val json = requireJson(execute(conn))
        val employee = json.getJSONObject("employee")
        val metricsJson = json.getJSONObject("metrics")
        return StaffAppData(
            employeeName = employee.optString("name"),
            employeeEmail = employee.optString("email"),
            sections = json.optJSONArray("sections").toStringList(),
            metrics = metricsJson.toIntMap(),
        )
    }

    fun loadAdminData(token: String): StaffAdminData {
        val conn = openConnection("/api/v1/staff/admin/data", "GET")
        conn.setRequestProperty("Authorization", "Bearer $token")
        val json = requireJson(execute(conn))
        return StaffAdminData(
            adminSections = json.optJSONArray("adminSections").toStringList(),
            adminMenu = json.optJSONObject("adminMenu").toStringMap(),
            widgets = json.optJSONArray("widgets").toWidgetMap(),
            canWrite = json.optBoolean("canWrite", false),
        )
    }

    fun loadSectionData(token: String, mode: String, section: String): SectionData {
        val conn = openConnection("/api/v1/staff/section-data?mode=$mode&section=$section", "GET")
        conn.setRequestProperty("Authorization", "Bearer $token")
        val json = requireJson(execute(conn))
        return SectionData(
            mode = json.optString("mode"),
            section = json.optString("section"),
            cards = json.optJSONArray("cards").toWidgetMap(),
        )
    }

    fun loadSchedule(token: String): ScheduleData {
        val conn = openConnection("/api/v1/staff/schedule", "GET")
        conn.setRequestProperty("Authorization", "Bearer $token")
        val json = requireJson(execute(conn))
        val dayRows = json.optJSONArray("days") ?: JSONArray()
        val days = mutableListOf<ScheduleDay>()
        for (i in 0 until dayRows.length()) {
            val row = dayRows.optJSONObject(i) ?: continue
            days += ScheduleDay(
                date = row.optString("date"),
                label = row.optString("label"),
                count = row.optInt("count", 0),
            )
        }
        val rows = json.optJSONArray("items") ?: JSONArray()
        val items = mutableListOf<ScheduleItem>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            items += ScheduleItem(
                title = row.optString("title"),
                trainer = row.optString("trainer"),
                type = row.optString("type"),
                date = row.optString("date"),
                dayLabel = row.optString("dayLabel"),
                startTime = row.optString("startTime"),
                endTime = row.optString("endTime"),
                startAt = row.optString("startAt"),
                endAt = row.optString("endAt"),
                room = row.optString("room"),
                clientNames = row.optJSONArray("clientNames").toStringList(),
                participants = row.optString("participants"),
            )
        }
        return ScheduleData(days = days, items = items)
    }

    fun loadList(token: String, section: String): List<FeedListItem> {
        val conn = openConnection("/api/v1/staff/list?section=$section", "GET")
        conn.setRequestProperty("Authorization", "Bearer $token")
        val json = requireJson(execute(conn))
        val rows = json.optJSONArray("items") ?: JSONArray()
        val out = mutableListOf<FeedListItem>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            out += FeedListItem(
                title = row.optString("title"),
                subtitle = row.optString("subtitle"),
                meta = row.optString("meta"),
                id = row.optInt("id").takeIf { it > 0 },
                refType = row.optString("refType").takeIf { it.isNotBlank() },
            )
        }
        return out
    }

    fun loadSupportTickets(token: String, status: String? = null): SupportTicketsData {
        val path = if (status.isNullOrBlank()) {
            "/api/v1/staff/support/tickets"
        } else {
            "/api/v1/staff/support/tickets?status=$status"
        }
        val conn = openConnection(path, "GET")
        conn.setRequestProperty("Authorization", "Bearer $token")
        val json = requireJson(execute(conn))
        val rows = json.optJSONArray("items") ?: JSONArray()
        val items = mutableListOf<SupportTicketItem>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            items += SupportTicketItem(
                id = row.optInt("id"),
                subject = row.optString("subject"),
                message = row.optString("message"),
                category = row.optString("category"),
                status = row.optString("status"),
                contactEmail = row.optString("contactEmail"),
                clientName = row.optString("clientName"),
                clientPhone = row.optString("clientPhone"),
                clientId = row.optInt("clientId").takeIf { it > 0 },
                createdAt = row.optString("createdAt"),
            )
        }
        return SupportTicketsData(items = items, newCount = json.optInt("newCount", 0))
    }

    fun loadClients(token: String, query: String = ""): List<ClientSummary> {
        val q = query.trim()
        val path = if (q.isBlank()) {
            "/api/v1/staff/clients"
        } else {
            "/api/v1/staff/clients?q=${java.net.URLEncoder.encode(q, Charsets.UTF_8.name())}"
        }
        val conn = openConnection(path, "GET")
        conn.setRequestProperty("Authorization", "Bearer $token")
        val json = requireJson(execute(conn))
        val rows = json.optJSONArray("items") ?: JSONArray()
        val out = mutableListOf<ClientSummary>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            out += ClientSummary(
                id = row.optInt("id"),
                name = row.optString("name"),
                email = row.optString("email"),
                phone = row.optString("phone"),
            )
        }
        return out
    }

    fun loadClientDetail(token: String, clientId: Int): ClientDetail {
        val conn = openConnection("/api/v1/staff/clients/$clientId", "GET")
        conn.setRequestProperty("Authorization", "Bearer $token")
        val json = requireJson(execute(conn))
        val row = json.getJSONObject("client")
        val subJson = row.optJSONObject("subscription")
        val subscription = if (subJson != null) {
            ClientSubscription(
                plan = subJson.optString("plan"),
                status = subJson.optString("status"),
                endDate = subJson.optString("endDate").takeIf { it.isNotBlank() },
                visitsUsed = subJson.optInt("visitsUsed", 0),
                visitsTotal = subJson.optInt("visitsTotal", 0),
            )
        } else {
            null
        }
        return ClientDetail(
            id = row.optInt("id"),
            name = row.optString("name"),
            email = row.optString("email"),
            phone = row.optString("phone"),
            bonusPoints = row.optInt("bonusPoints", 0),
            isBlocked = row.optBoolean("isBlocked", false),
            subscription = subscription,
            recentBookings = row.optJSONArray("recentBookings").toDetailRows(),
            recentTickets = row.optJSONArray("recentTickets").toTicketRows(),
        )
    }

    fun registerPushToken(token: String, pushToken: String, platform: String = "android"): Boolean {
        val conn = openConnection("/api/v1/staff/push-token", "POST")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.doOutput = true
        val payload = JSONObject()
            .put("token", pushToken)
            .put("platform", platform)
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
        return execute(conn).code in 200..299
    }

    fun updateSupportTicketStatus(token: String, ticketId: Int, status: String): Boolean {
        val conn = openConnection("/api/v1/staff/support/tickets/$ticketId/status", "POST")
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.doOutput = true
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
            it.write(JSONObject().put("status", status).toString())
        }
        return execute(conn).code in 200..299
    }

    fun loadStaffNotifications(token: String): StaffNotificationsData {
        val conn = openConnection("/api/v1/staff/notifications", "GET")
        conn.setRequestProperty("Authorization", "Bearer $token")
        val json = requireJson(execute(conn))
        val rows = json.optJSONArray("items") ?: JSONArray()
        val items = mutableListOf<StaffNotificationItem>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            items += StaffNotificationItem(
                id = row.optInt("id"),
                type = row.optString("type"),
                title = row.optString("title"),
                body = row.optString("body"),
                referenceId = row.optString("referenceId"),
                createdAt = row.optString("createdAt"),
                isRead = row.optBoolean("isRead", false),
            )
        }
        return StaffNotificationsData(items = items, unreadCount = json.optInt("unreadCount", 0))
    }

    fun markAllStaffNotificationsRead(token: String): Boolean {
        val conn = openConnection("/api/v1/staff/notifications/read-all", "POST")
        conn.setRequestProperty("Authorization", "Bearer $token")
        return execute(conn).code in 200..299
    }

    private fun authRequest(path: String, payload: JSONObject): StaffSession {
        val conn = openConnection(path, "POST")
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.doOutput = true

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
        val json = requireJson(execute(conn))
        return StaffSession(
            accessToken = json.getString("token"),
            refreshToken = json.getString("refresh_token"),
            userEmail = json.getJSONObject("user").optString("email"),
        )
    }

    private fun openConnection(path: String, method: String): HttpURLConnection {
        val url = URL(baseUrl.trimEnd('/') + path)
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Charset", "utf-8")
        }
    }

    /** Один вызов getResponseCode + чтение тела (важно для Android). */
    private fun execute(conn: HttpURLConnection): HttpResult {
        val code = conn.responseCode
        return HttpResult(code, readBody(conn, code))
    }

    private fun requireJson(result: HttpResult): JSONObject {
        if (result.code !in 200..299) {
            val detail = try {
                parseJson(result.body).optString("error").ifBlank { result.body.take(120) }
            } catch (_: Exception) {
                result.body.take(120).ifBlank { "пустой ответ, код ${result.code}" }
            }
            throw IllegalStateException("HTTP ${result.code}: $detail")
        }
        return parseJson(result.body)
    }

    private fun readBody(conn: HttpURLConnection, code: Int): String {
        val stream = (if (code >= 400) conn.errorStream else conn.inputStream)
            ?: conn.inputStream
            ?: return ""
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    fun pingServer(): Boolean {
        return try {
            val conn = openConnection("/api/v1/staff/config", "GET")
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            execute(conn).code in 200..499
        } catch (_: Exception) {
            false
        }
    }

    fun baseUrlLabel(): String = baseUrl.trimEnd('/')

    private fun parseJson(response: String): JSONObject {
        val trimmed = response.trimStart('\uFEFF').trim()
        if (trimmed.isBlank()) {
            throw IllegalStateException("Empty response body")
        }
        if (trimmed.startsWith("<")) {
            throw IllegalStateException("HTML response instead of JSON")
        }
        return try {
            JSONObject(trimmed)
        } catch (e: JSONException) {
            throw IllegalStateException("JSON parse failed: ${e.message}")
        }
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    val out = mutableListOf<String>()
    for (i in 0 until length()) {
        out += optString(i)
    }
    return out
}

private fun JSONObject?.toBooleanMap(): Map<String, Boolean> {
    if (this == null) return emptyMap()
    val out = mutableMapOf<String, Boolean>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        out[key] = optBoolean(key, false)
    }
    return out
}

private fun JSONObject?.toStringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    val out = mutableMapOf<String, String>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        out[key] = optString(key)
    }
    return out
}

private fun JSONObject.toIntMap(): Map<String, Int> {
    val out = mutableMapOf<String, Int>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        out[key] = optInt(key, 0)
    }
    return out
}

private fun JSONArray?.toWidgetMap(): Map<String, Int> {
    if (this == null) return emptyMap()
    val out = mutableMapOf<String, Int>()
    for (i in 0 until length()) {
        val row = optJSONObject(i) ?: continue
        out[row.optString("key")] = row.optInt("value", 0)
    }
    return out
}

private fun JSONArray?.toDetailRows(): List<ClientDetailRow> {
    if (this == null) return emptyList()
    val out = mutableListOf<ClientDetailRow>()
    for (i in 0 until length()) {
        val row = optJSONObject(i) ?: continue
        out += ClientDetailRow(
            title = row.optString("title"),
            meta = row.optString("meta"),
        )
    }
    return out
}

private fun JSONArray?.toTicketRows(): List<ClientDetailRow> {
    if (this == null) return emptyList()
    val out = mutableListOf<ClientDetailRow>()
    for (i in 0 until length()) {
        val row = optJSONObject(i) ?: continue
        val status = row.optString("status")
        out += ClientDetailRow(
            title = row.optString("subject"),
            meta = "${UiLabels.ticketStatus(status)} · ${row.optString("createdAt")}",
        )
    }
    return out
}

data class StaffSession(
    val accessToken: String,
    val refreshToken: String,
    val userEmail: String,
)
