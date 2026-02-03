//
////// Below one  is optimized code
//
//
//package com.LeadAnalysis.ESPAnalysis.service;
//
//import com.LeadAnalysis.ESPAnalysis.config.API;
//import io.restassured.RestAssured;
//import io.restassured.response.Response;
//import io.restassured.specification.RequestSpecification;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.apache.poi.ss.usermodel.*;
//import org.apache.poi.xssf.usermodel.XSSFWorkbook;
//import org.springframework.stereotype.Service;
//
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.time.*;
//import java.time.format.DateTimeFormatter;
//import java.util.*;
//
//import static io.restassured.RestAssured.given;
//
///**
// * Optimized Primary Replies Analyzer Service - Starts from end date for efficient fetching
// * Exports two sheets:
// * 1) Primary Replies Report: Date | Total Primary Replies | Google | Microsoft | Others
// * 2) Email Data: Lead Email | Subject | Content Preview | From Address | Formatted Date | Timestamp | ESP Code | Message ID | Thread ID
// */
//@Service
//public class PrimaryRepliesAnalyzerService {
//
//    // ---- CONFIG ----
//    private static final String BASE_URL = API.BASE_URL;
//    private static final String API_KEY = API.API_KEY;
//    private static final String EXCEL_FILE_PATH = System.getProperty("java.io.tmpdir") + "primary_replies_esp_report.xlsx";
//    private static final int EMAIL_LIMIT_PER_REQUEST = 30;
//
//    // Rate limiting configuration
//    private static final int MAX_RETRIES = 3;
//    private static final long INITIAL_DELAY_MS = 1000;
//    private static final long RATE_LIMIT_DELAY_MS = 5000;
//    private static final long BATCH_DELAY_MS = 800;
//    private static final long ESP_LOOKUP_DELAY_MS = 300;
//
//    // ESP codes
//    private static final int CODE_GOOGLE = 1;
//    private static final int CODE_MICROSOFT = 2;
//
//    // Domain Reply Rate
//    private Map<String,Integer> DomainsReplyRate = new HashMap<>();
//
//    // MailIds Reply Rate
//    private Map<String,Integer> MailIdsReplyRate = new HashMap<>();
//
//    // Date formats
//    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
//    private static final DateTimeFormatter ISO_Z = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]'Z'")
//            .withZone(ZoneOffset.UTC);
//    private static final DateTimeFormatter IST_OUT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
//    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
//
//    // ==== Data types ====
//    private static class EmailRow {
//        String leadEmail;
//        String subject;
//        String MessageText;
//        String fromAddress;
//        String toAddress;
//        String formattedDateIST;
//        String timestampUTC;
//        Integer espCode;
//        String messageId;
//        String threadId;
//    }
//
//    // Rate limit tracking class
//    private static class RateLimitTracker {
//        private boolean hasHitRateLimit = false;
//        private int totalRateLimitHits = 0;
//        private long lastRateLimitTime = 0;
//
//        public void recordRateLimitHit() {
//            hasHitRateLimit = true;
//            totalRateLimitHits++;
//            lastRateLimitTime = System.currentTimeMillis();
//        }
//
//        public boolean hasHitRateLimit() {
//            return hasHitRateLimit;
//        }
//
//        public int getTotalRateLimitHits() {
//            return totalRateLimitHits;
//        }
//
//        public void reset() {
//            hasHitRateLimit = false;
//        }
//    }
//
//    public String analyzePrimaryRepliesByDateRange(String fromDateStr, String toDateStr) {
//        try {
//            StringBuilder log = new StringBuilder();
//            log.append("🎯 Target date range: ").append(fromDateStr).append(" to ").append(toDateStr).append("\n");
//            System.out.println("🎯 Target date range: " + fromDateStr + " to " + toDateStr);
//            log.append("🚀 Starting OPTIMIZED PRIMARY Replies ESP Analysis...\n\n");
//            System.out.println("🚀 Starting OPTIMIZED PRIMARY Replies ESP Analysis...");
//            System.out.println();
//
//            LocalDate fromDate = LocalDate.parse(fromDateStr, DATE_FORMATTER);
//            LocalDate toDate = LocalDate.parse(toDateStr, DATE_FORMATTER);
//
//            // Global rate limit tracker
//            RateLimitTracker rateLimitTracker = new RateLimitTracker();
//
//            // Fetch PRIMARY emails using optimized approach
//            log.append("📧 Fetching PRIMARY replies with optimized approach (starting from end date)...\n");
//            System.out.println("📧 Fetching PRIMARY replies with optimized approach (starting from end date)...");
//            List<EmailRow> primaryRows = fetchPrimaryEmailsOptimized(fromDate, toDate, rateLimitTracker, log);
//
//            log.append("\n📊 Analysis Summary:\n");
//            System.out.println();
//            System.out.println("📊 Analysis Summary:");
//            log.append("Primary replies found: ").append(primaryRows.size()).append("\n");
//            System.out.println("Primary replies found: " + primaryRows.size());
//            log.append("Rate limit incidents: ").append(rateLimitTracker.getTotalRateLimitHits()).append("\n\n");
//            System.out.println("Rate limit incidents: " + rateLimitTracker.getTotalRateLimitHits());
//            System.out.println();
//
//            if (primaryRows.isEmpty()) {
//                log.append("❌ No primary replies found in the given date range.\n");
//                System.out.println("❌ No primary replies found in the given date range.");
//                writeExcel(fromDateStr, toDateStr, primaryRows, log);
//                return log.toString();
//            }
//
//            // ESP code lookup with rate limiting
//            log.append("👤 Starting ESP code lookup for ").append(primaryRows.size()).append(" emails...\n");
//            System.out.println("👤 Starting ESP code lookup for " + primaryRows.size() + " emails...");
//            Map<String, Integer> espByLead = fetchEspCodesWithEnhancedRateLimit(primaryRows, rateLimitTracker, log);
//
//            for (EmailRow r : primaryRows) {
//                r.espCode = espByLead.get(r.leadEmail);
//            }
//
//            // Export results
//            writeExcel(fromDateStr, toDateStr, primaryRows, log);
//            log.append("✅ Primary Replies Excel report generated successfully!\n");
//            System.out.println("✅ Primary Replies Excel report generated successfully!");
//            log.append("📁 File ready for download.\n");
//            System.out.println("📁 File ready for download.");
//
//            return log.toString();
//
//        } catch (Exception e) {
//            System.err.println("❌ Primary Replies Analysis failed: " + e.getMessage());
//            e.printStackTrace();
//            return "❌ Primary Replies Analysis failed: " + e.getMessage();
//        }
//    }
//
//    /**
//     * Optimized approach: Start from end date + 1 day to get end date's latest replies first,
//     * then continue backwards until start date - 2 days
//     */
//    private List<EmailRow> fetchPrimaryEmailsOptimized(LocalDate fromDateIST, LocalDate toDateIST,
//                                                       RateLimitTracker rateLimitTracker, StringBuilder log) {
//        List<EmailRow> allEmails = new ArrayList<>();
//        Set<String> seenEmailIds = new HashSet<>();
//
//        // Calculate optimized window
//        LocalDate windowStart = fromDateIST.minusDays(2);
//        LocalDate windowEnd = toDateIST;
//
//        // Start fetching from end date + 1 to get end date's latest replies
//        LocalDate startFetchDate = toDateIST.plusDays(1);
//
//        log.append("🎯 Optimized fetch strategy:\n");
//        System.out.println("🎯 Optimized fetch strategy:");
//        log.append("   • Start fetching from: ").append(startFetchDate).append(" (endDate + 1)\n");
//        System.out.println("   • Start fetching from: " + startFetchDate + " (endDate + 1)");
//        log.append("   • Target date range: ").append(fromDateIST).append(" to ").append(toDateIST).append("\n");
//        System.out.println("   • Target date range: " + fromDateIST + " to " + toDateIST);
//        log.append("   • Window range: ").append(windowStart).append(" to ").append(windowEnd).append("\n");
//        System.out.println("   • Window range: " + windowStart + " to " + windowEnd);
//        log.append("   • Expected to find most relevant emails quickly!\n\n");
//        System.out.println("   • Expected to find most relevant emails quickly!");
//        System.out.println();
//
//        // Create initial page_trail from start fetch date
//        String initialPageTrail = startFetchDate.atStartOfDay(ZoneOffset.UTC)
//                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
//
//        String pageTrailId = null;
//        int batch = 1;
//        int consecutiveFailures = 0;
//        final int MAX_CONSECUTIVE_FAILURES = 3;
//        boolean isFirstCall = true;
//
//        try {
//            log.append("🚀 Starting from initial page_trail: ").append(initialPageTrail).append("\n");
//            System.out.println("🚀 Starting from initial page_trail: " + initialPageTrail);
//
//            while (true) {
//                String batchLog = "📡 OPTIMIZED batch " + batch;
//                if (isFirstCall) {
//                    batchLog += " | page_trail: " + initialPageTrail;
//                } else if (pageTrailId != null) {
//                    batchLog += " | page_trail_id: " + pageTrailId.substring(0, Math.min(15, pageTrailId.length())) + "...";
//                }
//                batchLog += "\n";
//
//                log.append(batchLog);
//                System.out.print(batchLog);
//
//                Response response = fetchEmailsBatchOptimized(
//                        isFirstCall ? initialPageTrail : null,
//                        pageTrailId,
//                        "emode_focused",
//                        isFirstCall,
//                        rateLimitTracker,
//                        log
//                );
//
//                if (response == null) {
//                    consecutiveFailures++;
//                    log.append("❌ Failed batch ").append(batch)
//                            .append(" (").append(consecutiveFailures).append("/").append(MAX_CONSECUTIVE_FAILURES).append(")\n");
//                    System.out.println("❌ Failed batch " + batch + " (" + consecutiveFailures + "/" + MAX_CONSECUTIVE_FAILURES + ")");
//
//                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
//                        log.append("❌ Too many consecutive failures. Stopping analysis.\n");
//                        System.out.println("❌ Too many consecutive failures. Stopping analysis.");
//                        break;
//                    }
//
//                    long failureDelay = 5000 * consecutiveFailures;
//                    log.append("⏳ Waiting ").append(failureDelay / 1000).append(" seconds after failure...\n");
//                    System.out.println("⏳ Waiting " + (failureDelay / 1000) + " seconds after failure...");
//                    safeSleep(failureDelay, log);
//                    continue;
//                }
//
//                if (response.getStatusCode() != 200) {
//                    consecutiveFailures++;
//                    log.append("❌ API failed with status: ").append(response.getStatusCode()).append("\n");
//                    System.out.println("❌ API failed with status: " + response.getStatusCode());
//                    continue;
//                }
//
//                consecutiveFailures = 0;
//
//                ObjectMapper mapper = new ObjectMapper();
//                JsonNode root = mapper.readTree(response.getBody().asString());
//                JsonNode data = root.get("data");
//
//                if (data == null || !data.isArray() || data.size() == 0) {
//                    log.append("🏁 No more data available.\n");
//                    System.out.println("🏁 No more data available.");
//                    break;
//                }
//
//                LocalDate oldestInBatch = null;
//                LocalDate newestInBatch = null;
//                int addedFromBatch = 0;
//                String lastEmailId = null;
//
//                for (JsonNode email : data) {
//                    String id = asText(email, "id");
//                    if (id != null) {
//                        lastEmailId = id;
//                        if (seenEmailIds.contains(id)) continue;
//                        seenEmailIds.add(id);
//                    }
//
//                    String ts = asText(email, "timestamp_email");
//                    if (ts == null || ts.isBlank()) continue;
//
//                    LocalDate istDay;
//                    String istPretty;
//                    try {
//                        Instant inst = Instant.from(ISO_Z.parse(ts));
//                        ZonedDateTime zIST = inst.atZone(IST);
//                        istDay = zIST.toLocalDate();
//                        istPretty = zIST.format(IST_OUT);
//                    } catch (Exception e) {
//                        continue;
//                    }
//
//                    // Track batch date range
//                    if (newestInBatch == null || istDay.isAfter(newestInBatch)) {
//                        newestInBatch = istDay;
//                    }
//                    if (oldestInBatch == null || istDay.isBefore(oldestInBatch)) {
//                        oldestInBatch = istDay;
//                    }
//
//                    // Check if email falls within our target date range
//                    if (!istDay.isBefore(fromDateIST) && !istDay.isAfter(toDateIST)) {
//                        String lead = asText(email, "lead");
//                        if (lead != null && !lead.isBlank()) {
//                            EmailRow r = new EmailRow();
//                            r.leadEmail = lead.trim();
//                            r.subject = nz(asText(email, "subject"));
//                            r.MessageText = nz(asText(email, "body.text"));
//                            r.fromAddress = nz(asText(email, "from_address_email"));
//                            String mailId = nz(asText(email, "to_address_json"));
//                            r.toAddress = mailId;
//                            r.formattedDateIST = istPretty;
//                            r.timestampUTC = ts;
//                            r.messageId = nz(asText(email, "message_id"));
//                            r.threadId = nz(asText(email, "thread_id"));
//                            allEmails.add(r);
//                            addedFromBatch++;
//
//                            // Process reply rate only if mailId is valid
//                            if (mailId != null && !mailId.isEmpty() && mailId.contains("@")) {
//                                String[] splitData = mailId.split("@");
//                                if (splitData.length == 2) {
//                                    String domain = splitData[1];
//                                    MailIdsReplyRate.put(mailId, MailIdsReplyRate.getOrDefault(mailId, 0) + 1);
//                                    DomainsReplyRate.put(domain, DomainsReplyRate.getOrDefault(domain, 0) + 1);
//                                }
//                            }
//                        }
//                    }
//                }
//
//                String batchSummary = "✅ Batch " + batch + ": added=" + addedFromBatch +
//                        ", total=" + allEmails.size() +
//                        ", batch_range=[" + oldestInBatch + " to " + newestInBatch + "]\n";
//                log.append(batchSummary);
//                System.out.print(batchSummary);
//
//                // Check if we've gone too far back
//                if (oldestInBatch != null && oldestInBatch.isBefore(windowStart)) {
//                    log.append("⏹️ Reached emails before window start (").append(windowStart).append("). Stopping.\n");
//                    System.out.println("⏹️ Reached emails before window start (" + windowStart + "). Stopping.");
//                    break;
//                }
//
//                // Pagination logic
//                if (data.size() < EMAIL_LIMIT_PER_REQUEST) {
//                    log.append("🏁 Reached end of available data.\n");
//                    System.out.println("🏁 Reached end of available data.");
//                    break;
//                } else {
//                    pageTrailId = lastEmailId;
//                    isFirstCall = false;
//
//                    String nextCall = "📤 Next call: page_trail_id=" + (pageTrailId != null ? pageTrailId : "NULL") + "\n";
//                    log.append(nextCall);
//                    System.out.print(nextCall);
//                }
//
//                batch++;
//                long delay = rateLimitTracker.hasHitRateLimit() ? BATCH_DELAY_MS * 3 : BATCH_DELAY_MS;
//                safeSleep(delay, log);
//            }
//
//        } catch (Exception e) {
//            log.append("❌ Error in optimized fetch: ").append(e.getMessage()).append("\n");
//            System.err.println("❌ Error in optimized fetch: " + e.getMessage());
//            e.printStackTrace();
//        }
//
//        log.append("📊 Optimized fetch complete: ").append(allEmails.size()).append(" emails found in target range\n");
//        System.out.println("📊 Optimized fetch complete: " + allEmails.size() + " emails found in target range");
//        return allEmails;
//    }
//
//    private Response fetchEmailsBatchOptimized(String pageTrail, String pageTrailId, String mode,
//                                               boolean isFirstCall, RateLimitTracker rateLimitTracker, StringBuilder log) {
//        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
//            try {
//                Response response = fetchEmailsBatchWithCorrectParams(pageTrail, pageTrailId, mode, isFirstCall);
//
//                if (response.getStatusCode() == 429) {
//                    rateLimitTracker.recordRateLimitHit();
//                    log.append("⚠️ RATE LIMIT HIT (429) - Attempt ").append(attempt).append("/").append(MAX_RETRIES);
//                    System.err.println("⚠️ RATE LIMIT HIT (429) - Attempt " + attempt + "/" + MAX_RETRIES);
//                    log.append(" (Total hits: ").append(rateLimitTracker.getTotalRateLimitHits()).append(")\n");
//                    System.err.println(" (Total hits: " + rateLimitTracker.getTotalRateLimitHits() + ")");
//
//                    if (attempt < MAX_RETRIES) {
//                        long delay = RATE_LIMIT_DELAY_MS * attempt * 2;
//                        log.append("⏳ EXTENDED WAIT: ").append(delay / 1000).append(" seconds for rate limit recovery...\n");
//                        System.err.println("⏳ EXTENDED WAIT: " + (delay / 1000) + " seconds for rate limit recovery...");
//                        safeSleep(delay, log);
//                        continue;
//                    }
//                } else if (response.getStatusCode() >= 500) {
//                    log.append("⚠️ Server error (").append(response.getStatusCode()).append(") - Attempt ").append(attempt).append("\n");
//                    System.err.println("⚠️ Server error (" + response.getStatusCode() + ") - Attempt " + attempt);
//                    if (attempt < MAX_RETRIES) {
//                        safeSleep(INITIAL_DELAY_MS * attempt, log);
//                        continue;
//                    }
//                }
//
//                return response;
//
//            } catch (Exception e) {
//                log.append("⚠️ Network error - Attempt ").append(attempt).append(": ").append(e.getMessage()).append("\n");
//                System.err.println("⚠️ Network error - Attempt " + attempt + ": " + e.getMessage());
//                if (attempt < MAX_RETRIES) {
//                    safeSleep(INITIAL_DELAY_MS * attempt, log);
//                }
//            }
//        }
//        return null;
//    }
//
//    private Response fetchEmailsBatchWithCorrectParams(String pageTrail, String pageTrailId,
//                                                       String mode, boolean isFirstCall) {
//        String endpoint = "/backend-alt/api/v1/unibox/emails?limit=" + EMAIL_LIMIT_PER_REQUEST
//                + "&preview_only=false&mode=" + mode + "&latest_of_thread=false";
//
//        if (isFirstCall && pageTrail != null && !pageTrail.trim().isEmpty()) {
//            endpoint += "&page_trail=" + pageTrail;
//            System.out.println("DEBUG - First Call Endpoint: " + endpoint);
//        } else if (!isFirstCall && pageTrailId != null && !pageTrailId.trim().isEmpty()) {
//            endpoint += "&page_trail_id=" + pageTrailId;
//            System.out.println("DEBUG - Subsequent Call Endpoint: " + endpoint);
//        } else {
//            System.out.println("DEBUG - No Pagination Endpoint: " + endpoint);
//        }
//
//        RequestSpecification req = given()
//                .baseUri(BASE_URL)
//                .header("X-org-auth", API_KEY)
//                .header("Content-Type", "application/json")
//                .header("Connection", "keep-alive")
//                .relaxedHTTPSValidation();
//
//        return req.when().get(endpoint).then().extract().response();
//    }
//
//    private Map<String, Integer> fetchEspCodesWithEnhancedRateLimit(List<EmailRow> primaryRows,
//                                                                    RateLimitTracker rateLimitTracker, StringBuilder log) {
//        Map<String, Integer> out = new HashMap<>();
//        Set<String> unique = new HashSet<>();
//        for (EmailRow r : primaryRows) {
//            if (r.leadEmail != null && !r.leadEmail.isBlank()) unique.add(r.leadEmail);
//        }
//
//        log.append("👤 Getting ESP data for ").append(unique.size()).append(" unique leads...\n");
//        System.out.println("👤 Getting ESP data for " + unique.size() + " unique leads...");
//
//        int i = 0, total = unique.size();
//        int consecutiveFailures = 0;
//
//        for (String lead : unique) {
//            i++;
//            if (i % 5 == 0 || i == total) {
//                log.append("🔎 ESP lookup progress: ").append(i).append("/").append(total).append("\n");
//                System.out.println("🔎 ESP lookup progress: " + i + "/" + total);
//            }
//
//            Integer code = getEspCodeForLeadWithEnhancedRetry(lead, rateLimitTracker, log);
//            out.put(lead, code);
//
//            if (code == null) {
//                consecutiveFailures++;
//                if (consecutiveFailures >= 5) {
//                    log.append("⚠️ Multiple ESP failures. Extended delay...\n");
//                    System.err.println("⚠️ Multiple ESP failures. Extended delay...");
//                    safeSleep(ESP_LOOKUP_DELAY_MS * 3, log);
//                    consecutiveFailures = 0;
//                }
//            } else {
//                consecutiveFailures = 0;
//            }
//
//            long delay = ESP_LOOKUP_DELAY_MS;
//            if (rateLimitTracker.hasHitRateLimit()) {
//                delay *= 2;
//            }
//            safeSleep(delay, log);
//        }
//        return out;
//    }
//
//    private Integer getEspCodeForLeadWithEnhancedRetry(String leadEmail, RateLimitTracker rateLimitTracker, StringBuilder log) {
//        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
//            try {
//                Integer result = getEspCodeForLead(leadEmail);
//                if (result != null || attempt == MAX_RETRIES) {
//                    return result;
//                }
//                safeSleep(INITIAL_DELAY_MS * attempt, log);
//            } catch (RuntimeException e) {
//                if (e.getMessage().contains("Rate limit")) {
//                    rateLimitTracker.recordRateLimitHit();
//                    log.append("⚠️ Rate limit during ESP lookup for ").append(leadEmail).append("\n");
//                    System.err.println("⚠️ Rate limit during ESP lookup for " + leadEmail);
//                    safeSleep(RATE_LIMIT_DELAY_MS * attempt, log);
//                } else {
//                    safeSleep(INITIAL_DELAY_MS * attempt, log);
//                }
//            } catch (Exception e) {
//                if (attempt == MAX_RETRIES) {
//                    log.append("⚠️ Failed ESP lookup for ").append(leadEmail).append("\n");
//                    System.err.println("⚠️ Failed ESP lookup for " + leadEmail);
//                }
//                safeSleep(INITIAL_DELAY_MS * attempt, log);
//            }
//        }
//        return null;
//    }
//
//    private Integer getEspCodeForLead(String leadEmail) {
//        String body = "{\n" +
//                "  \"limit\": 10,\n" +
//                "  \"page_trail\": null,\n" +
//                "  \"with_campaign_name\": true,\n" +
//                "  \"with_list_name\": true,\n" +
//                "  \"search\": \"" + leadEmail.replace("\"", "\\\"") + "\",\n" +
//                "  \"assigned_to\": null,\n" +
//                "  \"is_website_visitor\": false,\n" +
//                "  \"queries\": []\n" +
//                "}";
//
//        RequestSpecification req = RestAssured.given()
//                .baseUri(BASE_URL)
//                .header("X-org-auth", API_KEY)
//                .header("Content-Type", "application/json")
//                .relaxedHTTPSValidation()
//                .body(body);
//
//        Response resp = req.when().post("/backend-alt/api/v1/lead/list").then().extract().response();
//
//        if (resp.getStatusCode() == 429) {
//            throw new RuntimeException("Rate limit hit during ESP lookup");
//        }
//
//        if (resp.getStatusCode() != 200) return null;
//
//        try {
//            ObjectMapper mapper = new ObjectMapper();
//            JsonNode root = mapper.readTree(resp.getBody().asString());
//            JsonNode items = root.get("items");
//            if (items != null && items.isArray() && items.size() > 0) {
//                JsonNode lead = items.get(0);
//                JsonNode codeNode = lead.get("esp_code");
//                if (codeNode != null && !codeNode.isNull()) return codeNode.asInt();
//            }
//        } catch (Exception ignored) {}
//        return null;
//    }
//
//    private void safeSleep(long millis, StringBuilder log) {
//        try {
//            Thread.sleep(millis);
//        } catch (InterruptedException e) {
//            log.append("⚠️ Sleep interrupted\n");
//            System.err.println("⚠️ Sleep interrupted");
//            Thread.currentThread().interrupt();
//        }
//    }
//
//    private void writeExcel(String fromDate, String toDate, List<EmailRow> rows, StringBuilder log) {
//        // Count ESP buckets
//        int google = 0, microsoft = 0, others = 0;
//        for (EmailRow r : rows) {
//            Integer c = r.espCode;
//            if (c == null) {
//                others++;
//                continue;
//            }
//            if (c == CODE_GOOGLE) google++;
//            else if (c == CODE_MICROSOFT) microsoft++;
//            else others++;
//        }
//
//        log.append("\n📈 PRIMARY Replies ESP Breakdown:\n");
//        System.out.println("\n📈 PRIMARY Replies ESP Breakdown:");
//        log.append("Google: ").append(google).append("\n");
//        System.out.println("Google: " + google);
//        log.append("Microsoft: ").append(microsoft).append("\n");
//        System.out.println("Microsoft: " + microsoft);
//        log.append("Others: ").append(others).append("\n");
//        System.out.println("Others: " + others);
//        log.append("Total Primary Replies: ").append(rows.size()).append("\n\n");
//        System.out.println("Total Primary Replies: " + rows.size());
//
//        // Debug reply rate data
//        log.append("\n📊 Reply Rate Statistics:\n");
//        System.out.println("\n📊 Reply Rate Statistics:");
//        log.append("Unique Domains: ").append(DomainsReplyRate.size()).append("\n");
//        System.out.println("Unique Domains: " + DomainsReplyRate.size());
//        log.append("Unique Mail IDs: ").append(MailIdsReplyRate.size()).append("\n");
//        System.out.println("Unique Mail IDs: " + MailIdsReplyRate.size());
//
//        int totalDomainReplies = DomainsReplyRate.values().stream().mapToInt(Integer::intValue).sum();
//        int totalMailIdReplies = MailIdsReplyRate.values().stream().mapToInt(Integer::intValue).sum();
//        log.append("Total Domain Replies: ").append(totalDomainReplies).append("\n");
//        System.out.println("Total Domain Replies: " + totalDomainReplies);
//        log.append("Total Mail ID Replies: ").append(totalMailIdReplies).append("\n\n");
//        System.out.println("Total Mail ID Replies: " + totalMailIdReplies + "\n");
//
//        try (Workbook workbook = new XSSFWorkbook()) {
//            createPrimaryReportSheet(workbook, fromDate, toDate, rows.size(), google, microsoft, others);
//            createEmailDataSheet(workbook, rows);
//            createReplyRateDataSheet(workbook);
//
//            try (FileOutputStream fileOut = new FileOutputStream(EXCEL_FILE_PATH)) {
//                workbook.write(fileOut);
//            }
//            log.append("📁 Excel file saved: ").append(EXCEL_FILE_PATH).append("\n");
//            System.out.println("📁 Excel file saved: " + EXCEL_FILE_PATH);
//        } catch (IOException e) {
//            log.append("❌ Error writing Excel file: ").append(e.getMessage()).append("\n");
//            System.err.println("❌ Error writing Excel file: " + e.getMessage());
//            throw new RuntimeException("Error creating Excel file", e);
//        }
//    }
//
//    private void createPrimaryReportSheet(Workbook workbook, String fromDate, String toDate,
//                                          int totalPrimaryReplies, int googleCount, int microsoftCount, int othersCount) {
//        Sheet sheet = workbook.createSheet("Primary Replies Report");
//
//        CellStyle header = headerStyle(workbook, IndexedColors.LIGHT_BLUE.getIndex());
//        CellStyle data = borderStyle(workbook);
//
//        Row h = sheet.createRow(0);
//        String[] cols = {"Date", "Total Primary Replies", "Google", "Microsoft", "Others"};
//        for (int i = 0; i < cols.length; i++) {
//            Cell c = h.createCell(i);
//            c.setCellValue(cols[i]);
//            c.setCellStyle(header);
//        }
//
//        Row r = sheet.createRow(1);
//        r.createCell(0).setCellValue(fromDate.equals(toDate) ? fromDate : (fromDate + " to " + toDate));
//        r.createCell(1).setCellValue(totalPrimaryReplies);
//        r.createCell(2).setCellValue(googleCount);
//        r.createCell(3).setCellValue(microsoftCount);
//        r.createCell(4).setCellValue(othersCount);
//
//        for (int i = 0; i <= 4; i++) {
//            r.getCell(i).setCellStyle(data);
//            sheet.autoSizeColumn(i);
//            if (sheet.getColumnWidth(i) < 4000) sheet.setColumnWidth(i, 4000);
//        }
//    }
//
//    private void createEmailDataSheet(Workbook workbook, List<EmailRow> rows) {
//        Sheet sheet = workbook.createSheet("Primary Email Data");
//        final int EXCEL_CELL_LIMIT = 32767;
//        CellStyle header = headerStyle(workbook, IndexedColors.ORANGE.getIndex());
//        CellStyle data = wrapBorderStyle(workbook);
//
//        String[] headers = {
//                "Lead Email", "Subject", "Message Text", "From Address", "To Address",
//                "Formatted Date", "Timestamp", "ESP Code", "Message ID", "Thread ID"
//        };
//
//        Row h = sheet.createRow(0);
//        for (int i = 0; i < headers.length; i++) {
//            Cell c = h.createCell(i);
//            c.setCellValue(headers[i]);
//            c.setCellStyle(header);
//        }
//        int rowIdx = 1;
//        for (EmailRow er : rows) {
//            Row r = sheet.createRow(rowIdx++);
//            String Message_Text = er.MessageText;
//            if(Message_Text!=null && Message_Text.length()>EXCEL_CELL_LIMIT){
//                Message_Text = Message_Text.substring(0,EXCEL_CELL_LIMIT);
//            }
//            set(r, 0, er.leadEmail, data);
//            set(r, 1, er.subject, data);
//            set(r, 2, Message_Text, data);
//            set(r, 3, er.fromAddress, data);
//            set(r, 4, er.toAddress, data);
//            set(r, 5, er.formattedDateIST, data);
//            set(r, 6, er.timestampUTC, data);
//            set(r, 7, (er.espCode == null) ? "N/A" : String.valueOf(er.espCode), data);
//            set(r, 8, er.messageId, data);
//            set(r, 9, er.threadId, data);
//        }
//
//        sheet.setColumnWidth(0, 6000);
//        sheet.setColumnWidth(1, 9000);
//        sheet.setColumnWidth(2, 12000);
//        sheet.setColumnWidth(3, 7000);
//        sheet.setColumnWidth(4, 7000);
//        sheet.setColumnWidth(5, 5000);
//        sheet.setColumnWidth(6, 8000);
//        sheet.setColumnWidth(7, 3000);
//        sheet.setColumnWidth(8, 8000);
//        sheet.setColumnWidth(9, 6000);
//    }
//
//    private void createReplyRateDataSheet(Workbook workbook){
//        Sheet sheet = workbook.createSheet("Reply Rate Data");
//
//        CellStyle header = headerStyle(workbook, IndexedColors.LIGHT_BLUE.getIndex());
//        CellStyle data = borderStyle(workbook);
//
//        // Create headers
//        Row headerRow = sheet.createRow(0);
//
//        // Domain headers (columns 0-1)
//        Cell domainHeader1 = headerRow.createCell(0);
//        domainHeader1.setCellValue("Domains");
//        domainHeader1.setCellStyle(header);
//
//        Cell domainHeader2 = headerRow.createCell(1);
//        domainHeader2.setCellValue("Reply_Count");
//        domainHeader2.setCellStyle(header);
//
//        // Empty column (column 2)
//        Cell emptyHeader = headerRow.createCell(2);
//        emptyHeader.setCellStyle(header);
//
//        // MailId headers (columns 3-4)
//        Cell mailIdHeader1 = headerRow.createCell(3);
//        mailIdHeader1.setCellValue("MailIds");
//        mailIdHeader1.setCellStyle(header);
//
//        Cell mailIdHeader2 = headerRow.createCell(4);
//        mailIdHeader2.setCellValue("Reply_Count");
//        mailIdHeader2.setCellStyle(header);
//
//        // Fill domain data
//        int rowIndex = 1;
//        for(Map.Entry<String, Integer> entry : DomainsReplyRate.entrySet()){
//            Row r = sheet.createRow(rowIndex++);
//            set(r, 0, entry.getKey(), data);
//            set(r, 1, entry.getValue().toString(), data);
//            // Add empty cell in column 2
//            Cell emptyCell = r.createCell(2);
//            emptyCell.setCellStyle(data);
//        }
//
//        // Fill mailId data
//        int mailIdRowIndex = 1;
//        for(Map.Entry<String, Integer> entry : MailIdsReplyRate.entrySet()){
//            Row row;
//            if(mailIdRowIndex < rowIndex) {
//                // Row already exists, get it
//                row = sheet.getRow(mailIdRowIndex);
//            } else {
//                // Create new row
//                row = sheet.createRow(mailIdRowIndex);
//                // Add styled cells for columns 0-2 if this is a new row
//                Cell c0 = row.createCell(0);
//                c0.setCellStyle(data);
//                Cell c1 = row.createCell(1);
//                c1.setCellStyle(data);
//                Cell c2 = row.createCell(2);
//                c2.setCellStyle(data);
//            }
//
//            set(row, 3, entry.getKey(), data);
//            set(row, 4, entry.getValue().toString(), data);
//            mailIdRowIndex++;
//        }
//
//        // Set column widths
//        sheet.setColumnWidth(0, 7000);  // Domains column
//        sheet.setColumnWidth(1, 3000);  // Domain Reply_Count column
//        sheet.setColumnWidth(2, 1000);  // Empty separator column
//        sheet.setColumnWidth(3, 9000);  // MailIds column
//        sheet.setColumnWidth(4, 3000);  // MailId Reply_Count column
//    }
//
//    // Helper methods
//    private static String asText(JsonNode n, String field) {
//        JsonNode v = null;
//        if(field.equals("body.text")){
//            v = n.path("body").path("text");
//        }
//        else if(field.equals("to_address_json")){
//            JsonNode toAddressArray = n.path("to_address_json");
//            // Check if it's an array and has at least one element
//            if (toAddressArray.isArray() && toAddressArray.size() > 0) {
//                JsonNode firstAddress = toAddressArray.get(0);
//                v = firstAddress.path("address");
//            }
//        }
//        else{
//            v = n.get(field);
//        }
//        return (v != null && !v.isNull() && !v.isMissingNode()) ? v.asText() : null;
//    }
//
//    private static String nz(String s) { return (s == null) ? "" : s; }
//
//    private static CellStyle headerStyle(Workbook wb, short color) {
//        CellStyle cs = wb.createCellStyle();
//        Font f = wb.createFont();
//        f.setBold(true);
//        f.setFontHeightInPoints((short) 12);
//        cs.setFont(f);
//        cs.setFillForegroundColor(color);
//        cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
//        addBorder(cs);
//        return cs;
//    }
//
//    private static CellStyle borderStyle(Workbook wb) {
//        CellStyle cs = wb.createCellStyle();
//        addBorder(cs);
//        return cs;
//    }
//
//    private static CellStyle wrapBorderStyle(Workbook wb) {
//        CellStyle cs = wb.createCellStyle();
//        addBorder(cs);
//        cs.setWrapText(true);
//        return cs;
//    }
//
//    private static void addBorder(CellStyle cs) {
//        cs.setBorderBottom(BorderStyle.THIN);
//        cs.setBorderTop(BorderStyle.THIN);
//        cs.setBorderLeft(BorderStyle.THIN);
//        cs.setBorderRight(BorderStyle.THIN);
//    }
//
//    private static void set(Row r, int idx, String val, CellStyle st) {
//        Cell c = r.createCell(idx);
//        c.setCellValue(val == null ? "" : val);
//        c.setCellStyle(st);
//    }
//
//    public File getLatestPrimaryRepliesExcelFile() {
//        File file = new File(EXCEL_FILE_PATH);
//        return file.exists() ? file : null;
//    }
//}


//package com.LeadAnalysis.ESPAnalysis.service;
//
//import com.LeadAnalysis.ESPAnalysis.config.API;
//import io.restassured.RestAssured;
//import io.restassured.response.Response;
//import io.restassured.specification.RequestSpecification;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.apache.poi.ss.usermodel.*;
//import org.apache.poi.xssf.usermodel.XSSFWorkbook;
//import org.springframework.stereotype.Service;
//
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.time.*;
//import java.time.format.DateTimeFormatter;
//import java.util.*;
//
//import static io.restassured.RestAssured.given;
//
///**
// * Primary Replies Analyzer Service v10 - Optimized with Optional ESP Analysis
// *
// * Features:
// * - Processes each date individually to avoid pagination issues (985 replies accuracy)
// * - Smart stopping logic (3 consecutive empty batches)
// * - Processes ALL emails in batch before stopping
// * - Optional ESP analysis (can be disabled for faster results)
// * - Reply rate tracking by domain and email
// *
// * Exports three sheets:
// * 1) Primary Replies Report: Date | Total Primary Replies | Google | Microsoft | Others
// * 2) Email Data: Lead Email | Subject | Content | From | To | Date | Timestamp | ESP Code | Message ID | Thread ID
// * 3) Reply Rate Data: Domains & MailIds with reply counts
// */
//@Service
//public class PrimaryRepliesAnalyzerService {
//
//    // ---- CONFIG ----
//    private static final String BASE_URL = API.BASE_URL;
//    private static final String API_KEY = API.API_KEY;
//    private static final String EXCEL_FILE_PATH = System.getProperty("java.io.tmpdir") + "primary_replies_esp_report.xlsx";
//    private static final int EMAIL_LIMIT_PER_REQUEST = 30;
//
//    // Rate limiting configuration
//    private static final int MAX_RETRIES = 3;
//    private static final long INITIAL_DELAY_MS = 1000;
//    private static final long RATE_LIMIT_DELAY_MS = 5000;
//    private static final long BATCH_DELAY_MS = 800;
//    private static final long ESP_LOOKUP_DELAY_MS = 300;
//
//    // ESP codes
//    private static final int CODE_GOOGLE = 1;
//    private static final int CODE_MICROSOFT = 2;
//
//    // Domain Reply Rate
//    private Map<String, Integer> DomainsReplyRate = new HashMap<>();
//
//    // MailIds Reply Rate
//    private Map<String, Integer> MailIdsReplyRate = new HashMap<>();
//
//    // Date formats
//    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
//    private static final DateTimeFormatter ISO_Z = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]'Z'")
//            .withZone(ZoneOffset.UTC);
//    private static final DateTimeFormatter IST_OUT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
//    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
//
//    // ==== Data types ====
//    private static class EmailRow {
//        String leadEmail;
//        String subject;
//        String MessageText;
//        String fromAddress;
//        String toAddress;
//        String formattedDateIST;
//        String timestampUTC;
//        Integer espCode;
//        String messageId;
//        String threadId;
//    }
//
//    // Rate limit tracking class
//    private static class RateLimitTracker {
//        private boolean hasHitRateLimit = false;
//        private int totalRateLimitHits = 0;
//        private long lastRateLimitTime = 0;
//
//        public void recordRateLimitHit() {
//            hasHitRateLimit = true;
//            totalRateLimitHits++;
//            lastRateLimitTime = System.currentTimeMillis();
//        }
//
//        public boolean hasHitRateLimit() {
//            return hasHitRateLimit;
//        }
//
//        public int getTotalRateLimitHits() {
//            return totalRateLimitHits;
//        }
//
//        public void reset() {
//            hasHitRateLimit = false;
//        }
//    }
//
//    /**
//     * Main analysis method - processes dates individually with optional ESP analysis
//     */
//    public String analyzePrimaryRepliesByDateRange(String fromDateStr, String toDateStr, boolean includeEspAnalysis) {
//        try {
//            StringBuilder log = new StringBuilder();
//            log.append("🎯 Target date range: ").append(fromDateStr).append(" to ").append(toDateStr).append("\n");
//            System.out.println("🎯 Target date range: " + fromDateStr + " to " + toDateStr);
//            log.append("🔍 ESP Analysis: ").append(includeEspAnalysis ? "ENABLED" : "DISABLED").append("\n");
//            System.out.println("🔍 ESP Analysis: " + (includeEspAnalysis ? "ENABLED" : "DISABLED"));
//            log.append("🚀 Starting OPTIMIZED PRIMARY Replies Analysis...\n\n");
//            System.out.println("🚀 Starting OPTIMIZED PRIMARY Replies Analysis...");
//            System.out.println();
//
//            LocalDate fromDate = LocalDate.parse(fromDateStr, DATE_FORMATTER);
//            LocalDate toDate = LocalDate.parse(toDateStr, DATE_FORMATTER);
//
//            RateLimitTracker rateLimitTracker = new RateLimitTracker();
//            List<EmailRow> allPrimaryRows = new ArrayList<>();
//            Set<String> seenEmailIds = new HashSet<>();
//
//            log.append("📅 Processing dates individually to ensure complete data collection...\n\n");
//            System.out.println("📅 Processing dates individually to ensure complete data collection...\n");
//
//            LocalDate currentDate = toDate;
//            while (!currentDate.isBefore(fromDate)) {
//                log.append("📆 Fetching replies for: ").append(currentDate.format(DATE_FORMATTER)).append("\n");
//                System.out.println("📆 Fetching replies for: " + currentDate.format(DATE_FORMATTER));
//
//                List<EmailRow> dailyRows = fetchPrimaryEmailsForSingleDate(currentDate, rateLimitTracker, log, seenEmailIds);
//
//                log.append("   ✅ Found ").append(dailyRows.size()).append(" new replies for ")
//                        .append(currentDate.format(DATE_FORMATTER)).append("\n");
//                System.out.println("   ✅ Found " + dailyRows.size() + " new replies for " + currentDate.format(DATE_FORMATTER));
//
//                allPrimaryRows.addAll(dailyRows);
//                currentDate = currentDate.minusDays(1);
//
//                if (!currentDate.isBefore(fromDate)) {
//                    safeSleep(1000, log);
//                }
//            }
//
//            log.append("\n📊 Analysis Summary:\n");
//            System.out.println("\n📊 Analysis Summary:");
//            log.append("Primary replies found: ").append(allPrimaryRows.size()).append("\n");
//            System.out.println("Primary replies found: " + allPrimaryRows.size());
//            log.append("Rate limit incidents: ").append(rateLimitTracker.getTotalRateLimitHits()).append("\n\n");
//            System.out.println("Rate limit incidents: " + rateLimitTracker.getTotalRateLimitHits());
//            System.out.println();
//
//            if (allPrimaryRows.isEmpty()) {
//                log.append("❌ No primary replies found in the given date range.\n");
//                System.out.println("❌ No primary replies found in the given date range.");
//                writeExcel(fromDateStr, toDateStr, allPrimaryRows, log, includeEspAnalysis);
//                return log.toString();
//            }
//
//            if (includeEspAnalysis) {
//                log.append("👤 Starting ESP code lookup for ").append(allPrimaryRows.size()).append(" emails...\n");
//                System.out.println("👤 Starting ESP code lookup for " + allPrimaryRows.size() + " emails...");
//                Map<String, Integer> espByLead = fetchEspCodesWithEnhancedRateLimit(allPrimaryRows, rateLimitTracker, log);
//
//                for (EmailRow r : allPrimaryRows) {
//                    r.espCode = espByLead.get(r.leadEmail);
//                }
//            } else {
//                log.append("⚡ Skipping ESP code lookup (disabled by user)\n");
//                System.out.println("⚡ Skipping ESP code lookup (disabled by user)");
//            }
//
//            writeExcel(fromDateStr, toDateStr, allPrimaryRows, log, includeEspAnalysis);
//            log.append("✅ Primary Replies Excel report generated successfully!\n");
//            System.out.println("✅ Primary Replies Excel report generated successfully!");
//            log.append("📁 File ready for download.\n");
//            System.out.println("📁 File ready for download.");
//
//            return log.toString();
//
//        } catch (Exception e) {
//            System.err.println("❌ Primary Replies Analysis failed: " + e.getMessage());
//            e.printStackTrace();
//            return "❌ Primary Replies Analysis failed: " + e.getMessage();
//        }
//    }
//
//    private List<EmailRow> fetchPrimaryEmailsForSingleDate(LocalDate targetDate,
//                                                           RateLimitTracker rateLimitTracker,
//                                                           StringBuilder log,
//                                                           Set<String> globalSeenIds) {
//        List<EmailRow> dateEmails = new ArrayList<>();
//        LocalDate startFetchDate = targetDate.plusDays(1);
//        String initialPageTrail = startFetchDate.atStartOfDay(ZoneOffset.UTC)
//                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
//
//        String pageTrailId = null;
//        int batch = 1;
//        int consecutiveFailures = 0;
//        final int MAX_CONSECUTIVE_FAILURES = 3;
//        boolean isFirstCall = true;
//        int consecutiveOutOfRangeBatches = 0;
//        final int MAX_OUT_OF_RANGE_BATCHES = 3;
//
//        try {
//            while (true) {
//                Response response = fetchEmailsBatchOptimized(
//                        isFirstCall ? initialPageTrail : null,
//                        pageTrailId,
//                        "emode_focused",
//                        isFirstCall,
//                        rateLimitTracker,
//                        log
//                );
//
//                if (response == null) {
//                    consecutiveFailures++;
//                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
//                        break;
//                    }
//                    safeSleep(2000 * consecutiveFailures, log);
//                    continue;
//                }
//
//                if (response.getStatusCode() != 200) {
//                    consecutiveFailures++;
//                    continue;
//                }
//
//                consecutiveFailures = 0;
//
//                ObjectMapper mapper = new ObjectMapper();
//                JsonNode root = mapper.readTree(response.getBody().asString());
//                JsonNode data = root.get("data");
//
//                if (data == null || !data.isArray() || data.size() == 0) {
//                    break;
//                }
//
//                LocalDate oldestInBatch = null;
//                LocalDate newestInBatch = null;
//                int addedFromBatch = 0;
//                int outOfRangeCount = 0;
//                int withinRangeCount = 0;
//                String lastEmailId = null;
//
//                for (JsonNode email : data) {
//                    String id = asText(email, "id");
//                    if (id != null) {
//                        lastEmailId = id;
//                        if (globalSeenIds.contains(id)) continue;
//                    }
//
//                    String ts = asText(email, "timestamp_email");
//                    if (ts == null || ts.isBlank()) continue;
//
//                    LocalDate istDay;
//                    String istPretty;
//                    try {
//                        Instant inst = Instant.from(ISO_Z.parse(ts));
//                        ZonedDateTime zIST = inst.atZone(IST);
//                        istDay = zIST.toLocalDate();
//                        istPretty = zIST.format(IST_OUT);
//                    } catch (Exception e) {
//                        continue;
//                    }
//
//                    if (oldestInBatch == null || istDay.isBefore(oldestInBatch)) {
//                        oldestInBatch = istDay;
//                    }
//                    if (newestInBatch == null || istDay.isAfter(newestInBatch)) {
//                        newestInBatch = istDay;
//                    }
//
//                    if (istDay.isBefore(targetDate)) {
//                        outOfRangeCount++;
//                        continue;
//                    } else if (istDay.isAfter(targetDate)) {
//                        continue;
//                    }
//
//                    withinRangeCount++;
//                    String lead = asText(email, "lead");
//                    if (lead != null && !lead.isBlank()) {
//                        EmailRow r = new EmailRow();
//                        r.leadEmail = lead.trim();
//                        r.subject = nz(asText(email, "subject"));
//                        r.MessageText = nz(asText(email, "body.text"));
//                        r.fromAddress = nz(asText(email, "from_address_email"));
//                        String mailId = nz(asText(email, "to_address_json"));
//                        r.toAddress = mailId;
//                        r.formattedDateIST = istPretty;
//                        r.timestampUTC = ts;
//                        r.messageId = nz(asText(email, "message_id"));
//                        r.threadId = nz(asText(email, "thread_id"));
//                        dateEmails.add(r);
//                        addedFromBatch++;
//
//                        if (id != null) {
//                            globalSeenIds.add(id);
//                        }
//
//                        if (mailId != null && !mailId.isEmpty() && mailId.contains("@")) {
//                            String[] splitData = mailId.split("@");
//                            if (splitData.length == 2) {
//                                String domain = splitData[1];
//                                MailIdsReplyRate.put(mailId, MailIdsReplyRate.getOrDefault(mailId, 0) + 1);
//                                DomainsReplyRate.put(domain, DomainsReplyRate.getOrDefault(domain, 0) + 1);
//                            }
//                        }
//                    }
//                }
//
//                System.out.println("      Batch " + batch + ": added=" + addedFromBatch +
//                        ", within_range=" + withinRangeCount +
//                        ", out_of_range=" + outOfRangeCount +
//                        ", batch_dates=[" + oldestInBatch + " to " + newestInBatch + "]");
//
//                if (withinRangeCount == 0 && outOfRangeCount > 0) {
//                    consecutiveOutOfRangeBatches++;
//                    System.out.println("      ⚠️ No valid emails in this batch (" + consecutiveOutOfRangeBatches + "/" + MAX_OUT_OF_RANGE_BATCHES + ")");
//
//                    if (consecutiveOutOfRangeBatches >= MAX_OUT_OF_RANGE_BATCHES) {
//                        System.out.println("      ⏹️ " + MAX_OUT_OF_RANGE_BATCHES + " consecutive batches with no valid data, stopping for this date");
//                        break;
//                    }
//                } else if (withinRangeCount > 0) {
//                    consecutiveOutOfRangeBatches = 0;
//                }
//
//                if (data.size() < EMAIL_LIMIT_PER_REQUEST) {
//                    System.out.println("      🏁 Reached end of available data (batch size < limit)");
//                    break;
//                } else {
//                    pageTrailId = lastEmailId;
//                    isFirstCall = false;
//                }
//
//                batch++;
//                long delay = rateLimitTracker.hasHitRateLimit() ? BATCH_DELAY_MS * 3 : BATCH_DELAY_MS;
//                safeSleep(delay, log);
//            }
//
//        } catch (Exception e) {
//            System.err.println("      ❌ Error fetching date " + targetDate + ": " + e.getMessage());
//        }
//
//        return dateEmails;
//    }
//
//    private Response fetchEmailsBatchOptimized(String pageTrail, String pageTrailId, String mode,
//                                               boolean isFirstCall, RateLimitTracker rateLimitTracker, StringBuilder log) {
//        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
//            try {
//                Response response = fetchEmailsBatchWithCorrectParams(pageTrail, pageTrailId, mode, isFirstCall);
//
//                if (response.getStatusCode() == 429) {
//                    rateLimitTracker.recordRateLimitHit();
//                    if (attempt < MAX_RETRIES) {
//                        long delay = RATE_LIMIT_DELAY_MS * attempt * 2;
//                        safeSleep(delay, log);
//                        continue;
//                    }
//                } else if (response.getStatusCode() >= 500) {
//                    if (attempt < MAX_RETRIES) {
//                        safeSleep(INITIAL_DELAY_MS * attempt, log);
//                        continue;
//                    }
//                }
//
//                return response;
//
//            } catch (Exception e) {
//                if (attempt < MAX_RETRIES) {
//                    safeSleep(INITIAL_DELAY_MS * attempt, log);
//                }
//            }
//        }
//        return null;
//    }
//
//    private Response fetchEmailsBatchWithCorrectParams(String pageTrail, String pageTrailId,
//                                                       String mode, boolean isFirstCall) {
//        String endpoint = "/backend-alt/api/v1/unibox/emails?limit=" + EMAIL_LIMIT_PER_REQUEST
//                + "&preview_only=false&mode=" + mode + "&latest_of_thread=false";
//
//        if (isFirstCall && pageTrail != null && !pageTrail.trim().isEmpty()) {
//            endpoint += "&page_trail=" + pageTrail;
//        } else if (!isFirstCall && pageTrailId != null && !pageTrailId.trim().isEmpty()) {
//            endpoint += "&page_trail_id=" + pageTrailId;
//        }
//
//        RequestSpecification req = given()
//                .baseUri(BASE_URL)
//                .header("X-org-auth", API_KEY)
//                .header("Content-Type", "application/json")
//                .header("Connection", "keep-alive")
//                .relaxedHTTPSValidation();
//
//        return req.when().get(endpoint).then().extract().response();
//    }
//
//    private Map<String, Integer> fetchEspCodesWithEnhancedRateLimit(List<EmailRow> primaryRows,
//                                                                    RateLimitTracker rateLimitTracker, StringBuilder log) {
//        Map<String, Integer> out = new HashMap<>();
//        Set<String> unique = new HashSet<>();
//        for (EmailRow r : primaryRows) {
//            if (r.leadEmail != null && !r.leadEmail.isBlank()) unique.add(r.leadEmail);
//        }
//
//        int i = 0, total = unique.size();
//        int consecutiveFailures = 0;
//
//        for (String lead : unique) {
//            i++;
//            if (i % 5 == 0 || i == total) {
//                System.out.println("🔎 ESP lookup progress: " + i + "/" + total);
//            }
//
//            Integer code = getEspCodeForLeadWithEnhancedRetry(lead, rateLimitTracker, log);
//            out.put(lead, code);
//
//            if (code == null) {
//                consecutiveFailures++;
//                if (consecutiveFailures >= 5) {
//                    safeSleep(ESP_LOOKUP_DELAY_MS * 3, log);
//                    consecutiveFailures = 0;
//                }
//            } else {
//                consecutiveFailures = 0;
//            }
//
//            long delay = ESP_LOOKUP_DELAY_MS;
//            if (rateLimitTracker.hasHitRateLimit()) {
//                delay *= 2;
//            }
//            safeSleep(delay, log);
//        }
//        return out;
//    }
//
//    private Integer getEspCodeForLeadWithEnhancedRetry(String leadEmail, RateLimitTracker rateLimitTracker, StringBuilder log) {
//        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
//            try {
//                Integer result = getEspCodeForLead(leadEmail);
//                if (result != null || attempt == MAX_RETRIES) {
//                    return result;
//                }
//                safeSleep(INITIAL_DELAY_MS * attempt, log);
//            } catch (RuntimeException e) {
//                if (e.getMessage().contains("Rate limit")) {
//                    rateLimitTracker.recordRateLimitHit();
//                    safeSleep(RATE_LIMIT_DELAY_MS * attempt, log);
//                } else {
//                    safeSleep(INITIAL_DELAY_MS * attempt, log);
//                }
//            } catch (Exception e) {
//                safeSleep(INITIAL_DELAY_MS * attempt, log);
//            }
//        }
//        return null;
//    }
//
//    private Integer getEspCodeForLead(String leadEmail) {
//        String body = "{\n" +
//                "  \"limit\": 10,\n" +
//                "  \"page_trail\": null,\n" +
//                "  \"with_campaign_name\": true,\n" +
//                "  \"with_list_name\": true,\n" +
//                "  \"search\": \"" + leadEmail.replace("\"", "\\\"") + "\",\n" +
//                "  \"assigned_to\": null,\n" +
//                "  \"is_website_visitor\": false,\n" +
//                "  \"queries\": []\n" +
//                "}";
//
//        RequestSpecification req = RestAssured.given()
//                .baseUri(BASE_URL)
//                .header("X-org-auth", API_KEY)
//                .header("Content-Type", "application/json")
//                .relaxedHTTPSValidation()
//                .body(body);
//
//        Response resp = req.when().post("/backend-alt/api/v1/lead/list").then().extract().response();
//
//        if (resp.getStatusCode() == 429) {
//            throw new RuntimeException("Rate limit hit during ESP lookup");
//        }
//
//        if (resp.getStatusCode() != 200) return null;
//
//        try {
//            ObjectMapper mapper = new ObjectMapper();
//            JsonNode root = mapper.readTree(resp.getBody().asString());
//            JsonNode items = root.get("items");
//            if (items != null && items.isArray() && items.size() > 0) {
//                JsonNode lead = items.get(0);
//                JsonNode codeNode = lead.get("esp_code");
//                if (codeNode != null && !codeNode.isNull()) return codeNode.asInt();
//            }
//        } catch (Exception ignored) {
//        }
//        return null;
//    }
//
//    private void safeSleep(long millis, StringBuilder log) {
//        try {
//            Thread.sleep(millis);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
//    }
//
//    private void writeExcel(String fromDate, String toDate, List<EmailRow> rows, StringBuilder log, boolean includeEspAnalysis) {
//        int google = 0, microsoft = 0, others = 0;
//
//        if (includeEspAnalysis) {
//            for (EmailRow r : rows) {
//                Integer c = r.espCode;
//                if (c == null) {
//                    others++;
//                    continue;
//                }
//                if (c == CODE_GOOGLE) google++;
//                else if (c == CODE_MICROSOFT) microsoft++;
//                else others++;
//            }
//        } else {
//            others = rows.size();
//        }
//
//        System.out.println("\n📈 PRIMARY Replies ESP Breakdown:");
//        if (includeEspAnalysis) {
//            System.out.println("Google: " + google);
//            System.out.println("Microsoft: " + microsoft);
//            System.out.println("Others: " + others);
//        } else {
//            System.out.println("ESP Analysis: DISABLED (All marked as N/A)");
//        }
//        System.out.println("Total Primary Replies: " + rows.size());
//
//        try (Workbook workbook = new XSSFWorkbook()) {
//            createPrimaryReportSheet(workbook, fromDate, toDate, rows.size(), google, microsoft, others, includeEspAnalysis);
//            createEmailDataSheet(workbook, rows, includeEspAnalysis);
//            createReplyRateDataSheet(workbook);
//
//            try (FileOutputStream fileOut = new FileOutputStream(EXCEL_FILE_PATH)) {
//                workbook.write(fileOut);
//            }
//        } catch (IOException e) {
//            throw new RuntimeException("Error creating Excel file", e);
//        }
//    }
//
//    private void createPrimaryReportSheet(Workbook workbook, String fromDate, String toDate,
//                                          int totalPrimaryReplies, int googleCount, int microsoftCount, int othersCount,
//                                          boolean includeEspAnalysis) {
//        Sheet sheet = workbook.createSheet("Primary Replies Report");
//        CellStyle header = headerStyle(workbook, IndexedColors.LIGHT_BLUE.getIndex());
//        CellStyle data = borderStyle(workbook);
//
//        Row h = sheet.createRow(0);
//        String[] cols = {"Date", "Total Primary Replies", "Google", "Microsoft", "Others"};
//        for (int i = 0; i < cols.length; i++) {
//            Cell c = h.createCell(i);
//            c.setCellValue(cols[i]);
//            c.setCellStyle(header);
//        }
//
//        Row r = sheet.createRow(1);
//        r.createCell(0).setCellValue(fromDate.equals(toDate) ? fromDate : (fromDate + " to " + toDate));
//        r.createCell(1).setCellValue(totalPrimaryReplies);
//
//        if (includeEspAnalysis) {
//            r.createCell(2).setCellValue(googleCount);
//            r.createCell(3).setCellValue(microsoftCount);
//            r.createCell(4).setCellValue(othersCount);
//        } else {
//            r.createCell(2).setCellValue("N/A");
//            r.createCell(3).setCellValue("N/A");
//            r.createCell(4).setCellValue("N/A");
//        }
//
//        for (int i = 0; i <= 4; i++) {
//            r.getCell(i).setCellStyle(data);
//            sheet.autoSizeColumn(i);
//            if (sheet.getColumnWidth(i) < 4000) sheet.setColumnWidth(i, 4000);
//        }
//    }
//
//    private void createEmailDataSheet(Workbook workbook, List<EmailRow> rows, boolean includeEspAnalysis) {
//        Sheet sheet = workbook.createSheet("Primary Email Data");
//        final int EXCEL_CELL_LIMIT = 32767;
//        CellStyle header = headerStyle(workbook, IndexedColors.ORANGE.getIndex());
//        CellStyle data = wrapBorderStyle(workbook);
//
//        String[] headers = {
//                "Lead Email", "Subject", "Message Text", "From Address", "To Address",
//                "Formatted Date", "Timestamp", "ESP Code", "Message ID", "Thread ID"
//        };
//
//        Row h = sheet.createRow(0);
//        for (int i = 0; i < headers.length; i++) {
//            Cell c = h.createCell(i);
//            c.setCellValue(headers[i]);
//            c.setCellStyle(header);
//        }
//
//        int rowIdx = 1;
//        for (EmailRow er : rows) {
//            Row r = sheet.createRow(rowIdx++);
//            String Message_Text = er.MessageText;
//            if (Message_Text != null && Message_Text.length() > EXCEL_CELL_LIMIT) {
//                Message_Text = Message_Text.substring(0, EXCEL_CELL_LIMIT);
//            }
//            set(r, 0, er.leadEmail, data);
//            set(r, 1, er.subject, data);
//            set(r, 2, Message_Text, data);
//            set(r, 3, er.fromAddress, data);
//            set(r, 4, er.toAddress, data);
//            set(r, 5, er.formattedDateIST, data);
//            set(r, 6, er.timestampUTC, data);
//            set(r, 7, includeEspAnalysis ? ((er.espCode == null) ? "N/A" : String.valueOf(er.espCode)) : "N/A", data);
//            set(r, 8, er.messageId, data);
//            set(r, 9, er.threadId, data);
//        }
//
//        sheet.setColumnWidth(0, 6000);
//        sheet.setColumnWidth(1, 9000);
//        sheet.setColumnWidth(2, 12000);
//        sheet.setColumnWidth(3, 7000);
//        sheet.setColumnWidth(4, 7000);
//        sheet.setColumnWidth(5, 5000);
//        sheet.setColumnWidth(6, 8000);
//        sheet.setColumnWidth(7, 3000);
//        sheet.setColumnWidth(8, 8000);
//        sheet.setColumnWidth(9, 6000);
//    }
//
//    private void createReplyRateDataSheet(Workbook workbook) {
//        Sheet sheet = workbook.createSheet("Reply Rate Data");
//        CellStyle header = headerStyle(workbook, IndexedColors.LIGHT_BLUE.getIndex());
//        CellStyle data = borderStyle(workbook);
//
//        Row headerRow = sheet.createRow(0);
//        headerRow.createCell(0).setCellValue("Domains");
//        headerRow.getCell(0).setCellStyle(header);
//        headerRow.createCell(1).setCellValue("Reply_Count");
//        headerRow.getCell(1).setCellStyle(header);
//        headerRow.createCell(2).setCellStyle(header);
//        headerRow.createCell(3).setCellValue("MailIds");
//        headerRow.getCell(3).setCellStyle(header);
//        headerRow.createCell(4).setCellValue("Reply_Count");
//        headerRow.getCell(4).setCellStyle(header);
//
//        int rowIndex = 1;
//        for (Map.Entry<String, Integer> entry : DomainsReplyRate.entrySet()) {
//            Row r = sheet.createRow(rowIndex++);
//            set(r, 0, entry.getKey(), data);
//            setNumeric(r, 1, entry.getValue(), data);
//            r.createCell(2).setCellStyle(data);
//        }
//
//        // Fill mailId data
//        int mailIdRowIndex = 1;
//        for (Map.Entry<String, Integer> entry : MailIdsReplyRate.entrySet()) {
//            Row row;
//            if (mailIdRowIndex < rowIndex) {
//                // Row already exists, get it
//                row = sheet.getRow(mailIdRowIndex);
//            } else {
//                // Create new row
//                row = sheet.createRow(mailIdRowIndex);
//                // Add styled cells for columns 0-2 if this is a new row
//                Cell c0 = row.createCell(0);
//                c0.setCellStyle(data);
//                Cell c1 = row.createCell(1);
//                c1.setCellStyle(data);
//                Cell c2 = row.createCell(2);
//                c2.setCellStyle(data);
//            }
//
//            set(row, 3, entry.getKey(), data);
//            setNumeric(row, 4, entry.getValue(), data);
//            mailIdRowIndex++;
//        }
//
//        // Set column widths
//        sheet.setColumnWidth(0, 7000);  // Domains column
//        sheet.setColumnWidth(1, 3000);  // Domain Reply_Count column
//        sheet.setColumnWidth(2, 1000);  // Empty separator column
//        sheet.setColumnWidth(3, 9000);  // MailIds column
//        sheet.setColumnWidth(4, 3000);  // MailId Reply_Count column
//    }
//
//    // Helper methods
//    private static String asText(JsonNode n, String field) {
//        JsonNode v = null;
//        if (field.equals("body.text")) {
//            v = n.path("body").path("text");
//        } else if (field.equals("to_address_json")) {
//            JsonNode toAddressArray = n.path("to_address_json");
//            // Check if it's an array and has at least one element
//            if (toAddressArray.isArray() && toAddressArray.size() > 0) {
//                JsonNode firstAddress = toAddressArray.get(0);
//                v = firstAddress.path("address");
//            }
//        } else {
//            v = n.get(field);
//        }
//        return (v != null && !v.isNull() && !v.isMissingNode()) ? v.asText() : null;
//    }
//
//    private static String nz(String s) {
//        return (s == null) ? "" : s;
//    }
//
//    private static CellStyle headerStyle(Workbook wb, short color) {
//        CellStyle cs = wb.createCellStyle();
//        Font f = wb.createFont();
//        f.setBold(true);
//        f.setFontHeightInPoints((short) 12);
//        cs.setFont(f);
//        cs.setFillForegroundColor(color);
//        cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
//        addBorder(cs);
//        return cs;
//    }
//
//    private static CellStyle borderStyle(Workbook wb) {
//        CellStyle cs = wb.createCellStyle();
//        addBorder(cs);
//        return cs;
//    }
//
//    private static CellStyle wrapBorderStyle(Workbook wb) {
//        CellStyle cs = wb.createCellStyle();
//        addBorder(cs);
//        cs.setWrapText(true);
//        return cs;
//    }
//
//    private static void addBorder(CellStyle cs) {
//        cs.setBorderBottom(BorderStyle.THIN);
//        cs.setBorderTop(BorderStyle.THIN);
//        cs.setBorderLeft(BorderStyle.THIN);
//        cs.setBorderRight(BorderStyle.THIN);
//    }
//
//    private static void set(Row r, int idx, String val, CellStyle st) {
//        Cell c = r.createCell(idx);
//        c.setCellValue(val == null ? "" : val);
//        c.setCellStyle(st);
//    }
//
//    private static void setNumeric(Row r, int idx, Integer val, CellStyle st) {
//        Cell c = r.createCell(idx);
//        c.setCellValue(val == null ? 0 : val.intValue()); // Set as numeric value, not string
//        c.setCellStyle(st);
//    }
//
//    public File getLatestPrimaryRepliesExcelFile() {
//        File file = new File(EXCEL_FILE_PATH);
//        return file.exists() ? file : null;
//    }
//}

// new one
package com.LeadAnalysis.ESPAnalysis.service;

import com.LeadAnalysis.ESPAnalysis.config.API;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static io.restassured.RestAssured.given;

/**
 * Primary Replies Analyzer Service v11 - Date-wise Reply Rate Analysis
 *
 * Features:
 * - Date-wise domain reply tracking
 * - Date-wise mail ID reply tracking
 * - Separate sheets for domains and mail IDs
 *
 * Exports four sheets:
 * 1) Primary Replies Report
 * 2) Email Data
 * 3) Domain Reply Rate (Date-wise)
 * 4) MailIds Reply Rate (Date-wise)
 */
@Service
public class PrimaryRepliesAnalyzerService {

    // ---- CONFIG ----
    private static final String BASE_URL = API.BASE_URL;
    private static final String API_KEY = API.API_KEY;
    private static final String EXCEL_FILE_PATH = System.getProperty("java.io.tmpdir") + "primary_replies_esp_report.xlsx";
    private static final int EMAIL_LIMIT_PER_REQUEST = 30;

    // Rate limiting configuration
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_DELAY_MS = 1000;
    private static final long RATE_LIMIT_DELAY_MS = 5000;
    private static final long BATCH_DELAY_MS = 800;
    private static final long ESP_LOOKUP_DELAY_MS = 300;

    // ESP codes
    private static final int CODE_GOOGLE = 1;
    private static final int CODE_MICROSOFT = 2;

    // Date-wise Domain Reply Rate: Domain -> Date -> Count
    private Map<String, Map<String, Integer>> DomainsReplyRateByDate = new HashMap<>();

    // Date-wise MailIds Reply Rate: MailId -> Date -> Count
    private Map<String, Map<String, Integer>> MailIdsReplyRateByDate = new HashMap<>();

    // Set to track all unique dates
    private Set<String> allDates = new TreeSet<>();

    // Date formats
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter ISO_Z = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]'Z'")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter IST_OUT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // ==== Data types ====
    private static class EmailRow {
        String leadEmail;
        String subject;
        String MessageText;
        String fromAddress;
        String toAddress;
        String formattedDateIST;
        String timestampUTC;
        Integer espCode;
        String messageId;
        String threadId;
        String dateOnly; // Added for date-wise tracking
    }

    // Rate limit tracking class
    private static class RateLimitTracker {
        private boolean hasHitRateLimit = false;
        private int totalRateLimitHits = 0;
        private long lastRateLimitTime = 0;

        public void recordRateLimitHit() {
            hasHitRateLimit = true;
            totalRateLimitHits++;
            lastRateLimitTime = System.currentTimeMillis();
        }

        public boolean hasHitRateLimit() {
            return hasHitRateLimit;
        }

        public int getTotalRateLimitHits() {
            return totalRateLimitHits;
        }

        public void reset() {
            hasHitRateLimit = false;
        }
    }

    /**
     * Main analysis method - processes dates individually with optional ESP analysis
     */
    public String analyzePrimaryRepliesByDateRange(String fromDateStr, String toDateStr, boolean includeEspAnalysis) {
        try {
            // Clear previous data
            DomainsReplyRateByDate.clear();
            MailIdsReplyRateByDate.clear();
            allDates.clear();

            StringBuilder log = new StringBuilder();
            log.append("🎯 Target date range: ").append(fromDateStr).append(" to ").append(toDateStr).append("\n");
            System.out.println("🎯 Target date range: " + fromDateStr + " to " + toDateStr);
            log.append("🔍 ESP Analysis: ").append(includeEspAnalysis ? "ENABLED" : "DISABLED").append("\n");
            System.out.println("🔍 ESP Analysis: " + (includeEspAnalysis ? "ENABLED" : "DISABLED"));
            log.append("🚀 Starting OPTIMIZED PRIMARY Replies Analysis...\n\n");
            System.out.println("🚀 Starting OPTIMIZED PRIMARY Replies Analysis...");
            System.out.println();

            LocalDate fromDate = LocalDate.parse(fromDateStr, DATE_FORMATTER);
            LocalDate toDate = LocalDate.parse(toDateStr, DATE_FORMATTER);

            RateLimitTracker rateLimitTracker = new RateLimitTracker();
            List<EmailRow> allPrimaryRows = new ArrayList<>();
            Set<String> seenEmailIds = new HashSet<>();

            log.append("📅 Processing dates individually to ensure complete data collection...\n\n");
            System.out.println("📅 Processing dates individually to ensure complete data collection...\n");

            LocalDate currentDate = toDate;
            while (!currentDate.isBefore(fromDate)) {
                log.append("📆 Fetching replies for: ").append(currentDate.format(DATE_FORMATTER)).append("\n");
                System.out.println("📆 Fetching replies for: " + currentDate.format(DATE_FORMATTER));

                List<EmailRow> dailyRows = fetchPrimaryEmailsForSingleDate(currentDate, rateLimitTracker, log, seenEmailIds);

                log.append("   ✅ Found ").append(dailyRows.size()).append(" new replies for ")
                        .append(currentDate.format(DATE_FORMATTER)).append("\n");
                System.out.println("   ✅ Found " + dailyRows.size() + " new replies for " + currentDate.format(DATE_FORMATTER));

                allPrimaryRows.addAll(dailyRows);
                currentDate = currentDate.minusDays(1);

                if (!currentDate.isBefore(fromDate)) {
                    safeSleep(1000, log);
                }
            }

            log.append("\n📊 Analysis Summary:\n");
            System.out.println("\n📊 Analysis Summary:");
            log.append("Primary replies found: ").append(allPrimaryRows.size()).append("\n");
            System.out.println("Primary replies found: " + allPrimaryRows.size());
            log.append("Rate limit incidents: ").append(rateLimitTracker.getTotalRateLimitHits()).append("\n\n");
            System.out.println("Rate limit incidents: " + rateLimitTracker.getTotalRateLimitHits());
            System.out.println();

            if (allPrimaryRows.isEmpty()) {
                log.append("❌ No primary replies found in the given date range.\n");
                System.out.println("❌ No primary replies found in the given date range.");
                writeExcel(fromDateStr, toDateStr, allPrimaryRows, log, includeEspAnalysis);
                return log.toString();
            }

            if (includeEspAnalysis) {
                log.append("👤 Starting ESP code lookup for ").append(allPrimaryRows.size()).append(" emails...\n");
                System.out.println("👤 Starting ESP code lookup for " + allPrimaryRows.size() + " emails...");
                Map<String, Integer> espByLead = fetchEspCodesWithEnhancedRateLimit(allPrimaryRows, rateLimitTracker, log);

                for (EmailRow r : allPrimaryRows) {
                    r.espCode = espByLead.get(r.leadEmail);
                }
            } else {
                log.append("⚡ Skipping ESP code lookup (disabled by user)\n");
                System.out.println("⚡ Skipping ESP code lookup (disabled by user)");
            }

            writeExcel(fromDateStr, toDateStr, allPrimaryRows, log, includeEspAnalysis);
            log.append("✅ Primary Replies Excel report generated successfully!\n");
            System.out.println("✅ Primary Replies Excel report generated successfully!");
            log.append("📁 File ready for download.\n");
            System.out.println("📁 File ready for download.");

            return log.toString();

        } catch (Exception e) {
            System.err.println("❌ Primary Replies Analysis failed: " + e.getMessage());
            e.printStackTrace();
            return "❌ Primary Replies Analysis failed: " + e.getMessage();
        }
    }

    private List<EmailRow> fetchPrimaryEmailsForSingleDate(LocalDate targetDate,
                                                           RateLimitTracker rateLimitTracker,
                                                           StringBuilder log,
                                                           Set<String> globalSeenIds) {
        List<EmailRow> dateEmails = new ArrayList<>();
        LocalDate startFetchDate = targetDate.plusDays(1);
        String initialPageTrail = startFetchDate.atStartOfDay(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));

        String pageTrailId = null;
        int batch = 1;
        int consecutiveFailures = 0;
        final int MAX_CONSECUTIVE_FAILURES = 3;
        boolean isFirstCall = true;
        int consecutiveOutOfRangeBatches = 0;
        final int MAX_OUT_OF_RANGE_BATCHES = 3;

        try {
            while (true) {
                Response response = fetchEmailsBatchOptimized(
                        isFirstCall ? initialPageTrail : null,
                        pageTrailId,
                        "emode_focused",
                        isFirstCall,
                        rateLimitTracker,
                        log
                );

                if (response == null) {
                    consecutiveFailures++;
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        break;
                    }
                    safeSleep(2000 * consecutiveFailures, log);
                    continue;
                }

                if (response.getStatusCode() != 200) {
                    consecutiveFailures++;
                    continue;
                }

                consecutiveFailures = 0;

                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.getBody().asString());
                JsonNode data = root.get("data");

                if (data == null || !data.isArray() || data.size() == 0) {
                    break;
                }

                LocalDate oldestInBatch = null;
                LocalDate newestInBatch = null;
                int addedFromBatch = 0;
                int outOfRangeCount = 0;
                int withinRangeCount = 0;
                String lastEmailId = null;

                for (JsonNode email : data) {
                    String id = asText(email, "id");
                    if (id != null) {
                        lastEmailId = id;
                        if (globalSeenIds.contains(id)) continue;
                    }

                    String ts = asText(email, "timestamp_email");
                    if (ts == null || ts.isBlank()) continue;

                    LocalDate istDay;
                    String istPretty;
                    String dateOnlyStr;
                    try {
                        Instant inst = Instant.from(ISO_Z.parse(ts));
                        ZonedDateTime zIST = inst.atZone(IST);
                        istDay = zIST.toLocalDate();
                        istPretty = zIST.format(IST_OUT);
                        dateOnlyStr = zIST.format(DATE_ONLY);
                    } catch (Exception e) {
                        continue;
                    }

                    if (oldestInBatch == null || istDay.isBefore(oldestInBatch)) {
                        oldestInBatch = istDay;
                    }
                    if (newestInBatch == null || istDay.isAfter(newestInBatch)) {
                        newestInBatch = istDay;
                    }

                    if (istDay.isBefore(targetDate)) {
                        outOfRangeCount++;
                        continue;
                    } else if (istDay.isAfter(targetDate)) {
                        continue;
                    }

                    withinRangeCount++;
                    String lead = asText(email, "lead");
                    if (lead != null && !lead.isBlank()) {
                        EmailRow r = new EmailRow();
                        r.leadEmail = lead.trim();
                        r.subject = nz(asText(email, "subject"));
                        r.MessageText = nz(asText(email, "body.text"));
                        r.fromAddress = nz(asText(email, "from_address_email"));
                        String mailId = nz(asText(email, "to_address_json"));
                        r.toAddress = mailId;
                        r.formattedDateIST = istPretty;
                        r.timestampUTC = ts;
                        r.messageId = nz(asText(email, "message_id"));
                        r.threadId = nz(asText(email, "thread_id"));
                        r.dateOnly = dateOnlyStr;
                        dateEmails.add(r);
                        addedFromBatch++;

                        if (id != null) {
                            globalSeenIds.add(id);
                        }

                        // Track date
                        allDates.add(dateOnlyStr);

                        // Track domain and mailId reply rates by date
                        if (mailId != null && !mailId.isEmpty() && mailId.contains("@")) {
                            String[] splitData = mailId.split("@");
                            if (splitData.length == 2) {
                                String domain = splitData[1];

                                // Update domain reply rate by date
                                DomainsReplyRateByDate.putIfAbsent(domain, new HashMap<>());
                                Map<String, Integer> domainDateMap = DomainsReplyRateByDate.get(domain);
                                domainDateMap.put(dateOnlyStr, domainDateMap.getOrDefault(dateOnlyStr, 0) + 1);

                                // Update mailId reply rate by date
                                MailIdsReplyRateByDate.putIfAbsent(mailId, new HashMap<>());
                                Map<String, Integer> mailIdDateMap = MailIdsReplyRateByDate.get(mailId);
                                mailIdDateMap.put(dateOnlyStr, mailIdDateMap.getOrDefault(dateOnlyStr, 0) + 1);
                            }
                        }
                    }
                }

                System.out.println("      Batch " + batch + ": added=" + addedFromBatch +
                        ", within_range=" + withinRangeCount +
                        ", out_of_range=" + outOfRangeCount +
                        ", batch_dates=[" + oldestInBatch + " to " + newestInBatch + "]");

                if (withinRangeCount == 0 && outOfRangeCount > 0) {
                    consecutiveOutOfRangeBatches++;
                    System.out.println("      ⚠️ No valid emails in this batch (" + consecutiveOutOfRangeBatches + "/" + MAX_OUT_OF_RANGE_BATCHES + ")");

                    if (consecutiveOutOfRangeBatches >= MAX_OUT_OF_RANGE_BATCHES) {
                        System.out.println("      ⏹️ " + MAX_OUT_OF_RANGE_BATCHES + " consecutive batches with no valid data, stopping for this date");
                        break;
                    }
                } else if (withinRangeCount > 0) {
                    consecutiveOutOfRangeBatches = 0;
                }

                if (data.size() < EMAIL_LIMIT_PER_REQUEST) {
                    System.out.println("      🏁 Reached end of available data (batch size < limit)");
                    break;
                } else {
                    pageTrailId = lastEmailId;
                    isFirstCall = false;
                }

                batch++;
                long delay = rateLimitTracker.hasHitRateLimit() ? BATCH_DELAY_MS * 3 : BATCH_DELAY_MS;
                safeSleep(delay, log);
            }

        } catch (Exception e) {
            System.err.println("      ❌ Error fetching date " + targetDate + ": " + e.getMessage());
        }

        return dateEmails;
    }

    private Response fetchEmailsBatchOptimized(String pageTrail, String pageTrailId, String mode,
                                               boolean isFirstCall, RateLimitTracker rateLimitTracker, StringBuilder log) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Response response = fetchEmailsBatchWithCorrectParams(pageTrail, pageTrailId, mode, isFirstCall);

                if (response.getStatusCode() == 429) {
                    rateLimitTracker.recordRateLimitHit();
                    if (attempt < MAX_RETRIES) {
                        long delay = RATE_LIMIT_DELAY_MS * attempt * 2;
                        safeSleep(delay, log);
                        continue;
                    }
                } else if (response.getStatusCode() >= 500) {
                    if (attempt < MAX_RETRIES) {
                        safeSleep(INITIAL_DELAY_MS * attempt, log);
                        continue;
                    }
                }

                return response;

            } catch (Exception e) {
                if (attempt < MAX_RETRIES) {
                    safeSleep(INITIAL_DELAY_MS * attempt, log);
                }
            }
        }
        return null;
    }

    private Response fetchEmailsBatchWithCorrectParams(String pageTrail, String pageTrailId,
                                                       String mode, boolean isFirstCall) {
        String endpoint = "/backend-alt/api/v1/unibox/emails?limit=" + EMAIL_LIMIT_PER_REQUEST
                + "&preview_only=false&mode=" + mode + "&latest_of_thread=false";

        if (isFirstCall && pageTrail != null && !pageTrail.trim().isEmpty()) {
            endpoint += "&page_trail=" + pageTrail;
        } else if (!isFirstCall && pageTrailId != null && !pageTrailId.trim().isEmpty()) {
            endpoint += "&page_trail_id=" + pageTrailId;
        }

        RequestSpecification req = given()
                .baseUri(BASE_URL)
                .header("X-org-auth", API_KEY)
                .header("Content-Type", "application/json")
                .header("Connection", "keep-alive")
                .relaxedHTTPSValidation();

        return req.when().get(endpoint).then().extract().response();
    }

    private Map<String, Integer> fetchEspCodesWithEnhancedRateLimit(List<EmailRow> primaryRows,
                                                                    RateLimitTracker rateLimitTracker, StringBuilder log) {
        Map<String, Integer> out = new HashMap<>();
        Set<String> unique = new HashSet<>();
        for (EmailRow r : primaryRows) {
            if (r.leadEmail != null && !r.leadEmail.isBlank()) unique.add(r.leadEmail);
        }

        int i = 0, total = unique.size();
        int consecutiveFailures = 0;

        for (String lead : unique) {
            i++;
            if (i % 5 == 0 || i == total) {
                System.out.println("🔎 ESP lookup progress: " + i + "/" + total);
            }

            Integer code = getEspCodeForLeadWithEnhancedRetry(lead, rateLimitTracker, log);
            out.put(lead, code);

            if (code == null) {
                consecutiveFailures++;
                if (consecutiveFailures >= 5) {
                    safeSleep(ESP_LOOKUP_DELAY_MS * 3, log);
                    consecutiveFailures = 0;
                }
            } else {
                consecutiveFailures = 0;
            }

            long delay = ESP_LOOKUP_DELAY_MS;
            if (rateLimitTracker.hasHitRateLimit()) {
                delay *= 2;
            }
            safeSleep(delay, log);
        }
        return out;
    }

    private Integer getEspCodeForLeadWithEnhancedRetry(String leadEmail, RateLimitTracker rateLimitTracker, StringBuilder log) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Integer result = getEspCodeForLead(leadEmail);
                if (result != null || attempt == MAX_RETRIES) {
                    return result;
                }
                safeSleep(INITIAL_DELAY_MS * attempt, log);
            } catch (RuntimeException e) {
                if (e.getMessage().contains("Rate limit")) {
                    rateLimitTracker.recordRateLimitHit();
                    safeSleep(RATE_LIMIT_DELAY_MS * attempt, log);
                } else {
                    safeSleep(INITIAL_DELAY_MS * attempt, log);
                }
            } catch (Exception e) {
                safeSleep(INITIAL_DELAY_MS * attempt, log);
            }
        }
        return null;
    }

    private Integer getEspCodeForLead(String leadEmail) {
        String body = "{\n" +
                "  \"limit\": 10,\n" +
                "  \"page_trail\": null,\n" +
                "  \"with_campaign_name\": true,\n" +
                "  \"with_list_name\": true,\n" +
                "  \"search\": \"" + leadEmail.replace("\"", "\\\"") + "\",\n" +
                "  \"assigned_to\": null,\n" +
                "  \"is_website_visitor\": false,\n" +
                "  \"queries\": []\n" +
                "}";

        RequestSpecification req = RestAssured.given()
                .baseUri(BASE_URL)
                .header("X-org-auth", API_KEY)
                .header("Content-Type", "application/json")
                .relaxedHTTPSValidation()
                .body(body);

        Response resp = req.when().post("/backend-alt/api/v1/lead/list").then().extract().response();

        if (resp.getStatusCode() == 429) {
            throw new RuntimeException("Rate limit hit during ESP lookup");
        }

        if (resp.getStatusCode() != 200) return null;

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(resp.getBody().asString());
            JsonNode items = root.get("items");
            if (items != null && items.isArray() && items.size() > 0) {
                JsonNode lead = items.get(0);
                JsonNode codeNode = lead.get("esp_code");
                if (codeNode != null && !codeNode.isNull()) return codeNode.asInt();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void safeSleep(long millis, StringBuilder log) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void writeExcel(String fromDate, String toDate, List<EmailRow> rows, StringBuilder log, boolean includeEspAnalysis) {
        int google = 0, microsoft = 0, others = 0;

        if (includeEspAnalysis) {
            for (EmailRow r : rows) {
                Integer c = r.espCode;
                if (c == null) {
                    others++;
                    continue;
                }
                if (c == CODE_GOOGLE) google++;
                else if (c == CODE_MICROSOFT) microsoft++;
                else others++;
            }
        } else {
            others = rows.size();
        }

        System.out.println("\n📈 PRIMARY Replies ESP Breakdown:");
        if (includeEspAnalysis) {
            System.out.println("Google: " + google);
            System.out.println("Microsoft: " + microsoft);
            System.out.println("Others: " + others);
        } else {
            System.out.println("ESP Analysis: DISABLED (All marked as N/A)");
        }
        System.out.println("Total Primary Replies: " + rows.size());

        try (Workbook workbook = new XSSFWorkbook()) {
            createPrimaryReportSheet(workbook, fromDate, toDate, rows.size(), google, microsoft, others, includeEspAnalysis);
            createEmailDataSheet(workbook, rows, includeEspAnalysis);
            createDomainReplyRateSheet(workbook);
            createMailIdReplyRateSheet(workbook);

            try (FileOutputStream fileOut = new FileOutputStream(EXCEL_FILE_PATH)) {
                workbook.write(fileOut);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error creating Excel file", e);
        }
    }

    private void createPrimaryReportSheet(Workbook workbook, String fromDate, String toDate,
                                          int totalPrimaryReplies, int googleCount, int microsoftCount, int othersCount,
                                          boolean includeEspAnalysis) {
        Sheet sheet = workbook.createSheet("Primary Replies Report");
        CellStyle header = headerStyle(workbook, IndexedColors.LIGHT_BLUE.getIndex());
        CellStyle data = borderStyle(workbook);

        Row h = sheet.createRow(0);
        String[] cols = {"Date", "Total Primary Replies", "Google", "Microsoft", "Others"};
        for (int i = 0; i < cols.length; i++) {
            Cell c = h.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(header);
        }

        Row r = sheet.createRow(1);
        r.createCell(0).setCellValue(fromDate.equals(toDate) ? fromDate : (fromDate + " to " + toDate));
        r.createCell(1).setCellValue(totalPrimaryReplies);

        if (includeEspAnalysis) {
            r.createCell(2).setCellValue(googleCount);
            r.createCell(3).setCellValue(microsoftCount);
            r.createCell(4).setCellValue(othersCount);
        } else {
            r.createCell(2).setCellValue("N/A");
            r.createCell(3).setCellValue("N/A");
            r.createCell(4).setCellValue("N/A");
        }

        for (int i = 0; i <= 4; i++) {
            r.getCell(i).setCellStyle(data);
            sheet.autoSizeColumn(i);
            if (sheet.getColumnWidth(i) < 4000) sheet.setColumnWidth(i, 4000);
        }
    }

    private void createEmailDataSheet(Workbook workbook, List<EmailRow> rows, boolean includeEspAnalysis) {
        Sheet sheet = workbook.createSheet("Primary Email Data");
        final int EXCEL_CELL_LIMIT = 32767;
        CellStyle header = headerStyle(workbook, IndexedColors.ORANGE.getIndex());
        CellStyle data = wrapBorderStyle(workbook);

        String[] headers = {
                "Lead Email", "Subject", "Message Text", "From Address", "To Address",
                "Formatted Date", "Timestamp", "ESP Code", "Message ID", "Thread ID"
        };

        Row h = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = h.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(header);
        }

        int rowIdx = 1;
        for (EmailRow er : rows) {
            Row r = sheet.createRow(rowIdx++);
            String Message_Text = er.MessageText;
            if (Message_Text != null && Message_Text.length() > EXCEL_CELL_LIMIT) {
                Message_Text = Message_Text.substring(0, EXCEL_CELL_LIMIT);
            }
            set(r, 0, er.leadEmail, data);
            set(r, 1, er.subject, data);
            set(r, 2, Message_Text, data);
            set(r, 3, er.fromAddress, data);
            set(r, 4, er.toAddress, data);
            set(r, 5, er.formattedDateIST, data);
            set(r, 6, er.timestampUTC, data);
            set(r, 7, includeEspAnalysis ? ((er.espCode == null) ? "N/A" : String.valueOf(er.espCode)) : "N/A", data);
            set(r, 8, er.messageId, data);
            set(r, 9, er.threadId, data);
        }

        sheet.setColumnWidth(0, 6000);
        sheet.setColumnWidth(1, 9000);
        sheet.setColumnWidth(2, 12000);
        sheet.setColumnWidth(3, 7000);
        sheet.setColumnWidth(4, 7000);
        sheet.setColumnWidth(5, 5000);
        sheet.setColumnWidth(6, 8000);
        sheet.setColumnWidth(7, 3000);
        sheet.setColumnWidth(8, 8000);
        sheet.setColumnWidth(9, 6000);
    }

    private void createDomainReplyRateSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Domain_Reply_Rate");
        CellStyle header = headerStyle(workbook, IndexedColors.LIGHT_GREEN.getIndex());
        CellStyle data = borderStyle(workbook);

        // Convert dates to sorted list
        List<String> dateList = new ArrayList<>(allDates);

        // Create header row
        Row headerRow = sheet.createRow(0);
        Cell domainHeader = headerRow.createCell(0);
        domainHeader.setCellValue("Domain");
        domainHeader.setCellStyle(header);

        // Add date columns
        for (int i = 0; i < dateList.size(); i++) {
            Cell dateCell = headerRow.createCell(i + 1);
            dateCell.setCellValue(dateList.get(i));
            dateCell.setCellStyle(header);
        }

        // Add total column
        Cell totalHeader = headerRow.createCell(dateList.size() + 1);
        totalHeader.setCellValue("Total");
        totalHeader.setCellStyle(header);

        // Fill data rows
        int rowIndex = 1;
        for (Map.Entry<String, Map<String, Integer>> entry : DomainsReplyRateByDate.entrySet()) {
            Row row = sheet.createRow(rowIndex++);
            String domain = entry.getKey();
            Map<String, Integer> dateCountMap = entry.getValue();

            // Domain name
            set(row, 0, domain, data);

            // Reply counts for each date
            int total = 0;
            for (int i = 0; i < dateList.size(); i++) {
                String date = dateList.get(i);
                Integer count = dateCountMap.getOrDefault(date, 0);
                setNumeric(row, i + 1, count, data);
                total += count;
            }

            // Total column
            setNumeric(row, dateList.size() + 1, total, data);
        }

        // Auto-size columns
        sheet.setColumnWidth(0, 7000);
        for (int i = 1; i <= dateList.size() + 1; i++) {
            sheet.autoSizeColumn(i);
            if (sheet.getColumnWidth(i) < 3500) {
                sheet.setColumnWidth(i, 3500);
            }
        }
    }

    private void createMailIdReplyRateSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("MailIds_Reply_Rate");
        CellStyle header = headerStyle(workbook, IndexedColors.LIGHT_TURQUOISE.getIndex());
        CellStyle data = borderStyle(workbook);

        // Convert dates to sorted list
        List<String> dateList = new ArrayList<>(allDates);

        // Create header row
        Row headerRow = sheet.createRow(0);
        Cell mailIdHeader = headerRow.createCell(0);
        mailIdHeader.setCellValue("MailId");
        mailIdHeader.setCellStyle(header);

        // Add date columns
        for (int i = 0; i < dateList.size(); i++) {
            Cell dateCell = headerRow.createCell(i + 1);
            dateCell.setCellValue(dateList.get(i));
            dateCell.setCellStyle(header);
        }

        // Add total column
        Cell totalHeader = headerRow.createCell(dateList.size() + 1);
        totalHeader.setCellValue("Total");
        totalHeader.setCellStyle(header);

        // Fill data rows
        int rowIndex = 1;
        for (Map.Entry<String, Map<String, Integer>> entry : MailIdsReplyRateByDate.entrySet()) {
            Row row = sheet.createRow(rowIndex++);
            String mailId = entry.getKey();
            Map<String, Integer> dateCountMap = entry.getValue();

            // MailId
            set(row, 0, mailId, data);

            // Reply counts for each date
            int total = 0;
            for (int i = 0; i < dateList.size(); i++) {
                String date = dateList.get(i);
                Integer count = dateCountMap.getOrDefault(date, 0);
                setNumeric(row, i + 1, count, data);
                total += count;
            }

            // Total column
            setNumeric(row, dateList.size() + 1, total, data);
        }

        // Auto-size columns
        sheet.setColumnWidth(0, 9000);
        for (int i = 1; i <= dateList.size() + 1; i++) {
            sheet.autoSizeColumn(i);
            if (sheet.getColumnWidth(i) < 3500) {
                sheet.setColumnWidth(i, 3500);
            }
        }
    }

    // Helper methods
    private static String asText(JsonNode n, String field) {
        JsonNode v = null;
        if (field.equals("body.text")) {
            v = n.path("body").path("text");
        } else if (field.equals("to_address_json")) {
            JsonNode toAddressArray = n.path("to_address_json");
            // Check if it's an array and has at least one element
            if (toAddressArray.isArray() && toAddressArray.size() > 0) {
                JsonNode firstAddress = toAddressArray.get(0);
                v = firstAddress.path("address");
            }
        } else {
            v = n.get(field);
        }
        return (v != null && !v.isNull() && !v.isMissingNode()) ? v.asText() : null;
    }

    private static String nz(String s) {
        return (s == null) ? "" : s;
    }

    private static CellStyle headerStyle(Workbook wb, short color) {
        CellStyle cs = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 12);
        cs.setFont(f);
        cs.setFillForegroundColor(color);
        cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        addBorder(cs);
        return cs;
    }

    private static CellStyle borderStyle(Workbook wb) {
        CellStyle cs = wb.createCellStyle();
        addBorder(cs);
        return cs;
    }

    private static CellStyle wrapBorderStyle(Workbook wb) {
        CellStyle cs = wb.createCellStyle();
        addBorder(cs);
        cs.setWrapText(true);
        return cs;
    }

    private static void addBorder(CellStyle cs) {
        cs.setBorderBottom(BorderStyle.THIN);
        cs.setBorderTop(BorderStyle.THIN);
        cs.setBorderLeft(BorderStyle.THIN);
        cs.setBorderRight(BorderStyle.THIN);
    }

    private static void set(Row r, int idx, String val, CellStyle st) {
        Cell c = r.createCell(idx);
        c.setCellValue(val == null ? "" : val);
        c.setCellStyle(st);
    }

    private static void setNumeric(Row r, int idx, Integer val, CellStyle st) {
        Cell c = r.createCell(idx);
        c.setCellValue(val == null ? 0 : val.intValue());
        c.setCellStyle(st);
    }

    public File getLatestPrimaryRepliesExcelFile() {
        File file = new File(EXCEL_FILE_PATH);
        return file.exists() ? file : null;
    }
}


