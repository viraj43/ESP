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

/**
 * Primary Replies Analyzer Service - Focused on emode_focused analysis only
 * Exports two sheets:
 *  1) Primary Replies Report: Date | Total Primary Replies | Google | Microsoft | Others
 *  2) Email Data: Lead Email | Subject | Content Preview | From Address | Formatted Date | Timestamp | ESP Code | Message ID | Thread ID
 */
@Service
public class PrimaryRepliesTestAnalyzerService {

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

    // Date formats
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter ISO_Z = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]'Z'")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter IST_OUT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // ==== Data types ====
    private static class EmailRow {
        String leadEmail;
        String subject;
        String contentPreview;
        String fromAddress;
        String formattedDateIST;
        String timestampUTC;
        Integer espCode;
        String messageId;
        String threadId;
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

    public String analyzePrimaryRepliesByDateRange(String fromDateStr, String toDateStr) {
        try {
            StringBuilder log = new StringBuilder();
            log.append("🎯 Target date range: ").append(fromDateStr).append(" to ").append(toDateStr).append("\n");
            log.append("🚀 Starting PRIMARY Replies ESP Analysis with enhanced rate limiting...\n\n");

            LocalDate fromDate = LocalDate.parse(fromDateStr, DATE_FORMATTER);
            LocalDate toDate = LocalDate.parse(toDateStr, DATE_FORMATTER);

            // Global rate limit tracker
            RateLimitTracker rateLimitTracker = new RateLimitTracker();

            // Fetch PRIMARY emails only
            log.append("📧 Fetching PRIMARY replies (mode=emode_focused)...\n");
            List<EmailRow> primaryRows = fetchPrimaryEmailsByTimeWindow(fromDate, toDate, rateLimitTracker, log);

            log.append("\n📊 Analysis Summary:\n");
            log.append("Primary replies found: ").append(primaryRows.size()).append("\n");
            log.append("Rate limit incidents: ").append(rateLimitTracker.getTotalRateLimitHits()).append("\n\n");

            if (primaryRows.isEmpty()) {
                log.append("❌ No primary replies found in the given date range.\n");
                writeExcel(fromDateStr, toDateStr, primaryRows, log);
                return log.toString();
            }

            // ESP code lookup with rate limiting
            log.append("👤 Starting ESP code lookup for ").append(primaryRows.size()).append(" emails...\n");
            Map<String, Integer> espByLead = fetchEspCodesWithEnhancedRateLimit(primaryRows, rateLimitTracker, log);

            for (EmailRow r : primaryRows) {
                r.espCode = espByLead.get(r.leadEmail);
            }

            // Export results
            writeExcel(fromDateStr, toDateStr, primaryRows, log);
            log.append("✅ Primary Replies Excel report generated successfully!\n");
            log.append("📁 File ready for download.\n");

            return log.toString();

        } catch (Exception e) {
            return "❌ Primary Replies Analysis failed: " + e.getMessage();
        }
    }

    private List<EmailRow> fetchPrimaryEmailsByTimeWindow(LocalDate fromDateIST, LocalDate toDateIST,
                                                          RateLimitTracker rateLimitTracker, StringBuilder log) {
        List<EmailRow> allEmails = new ArrayList<>();
        Set<String> seenEmailIds = new HashSet<>();

        // Create time windows
        LocalDate windowStart = fromDateIST.minusDays(2);
        LocalDate today = LocalDate.now();
        LocalDate windowEnd = toDateIST.equals(today) ? toDateIST : toDateIST.plusDays(2);

        log.append("🕐 Time-window for PRIMARY replies: ")
                .append(windowStart).append(" to ").append(windowEnd).append("\n");

        String pageTrail = null;
        int batch = 1;
        int consecutiveFailures = 0;
        final int MAX_CONSECUTIVE_FAILURES = 3;

        try {
            while (true) {
                log.append("📡 PRIMARY batch ").append(batch);
                if (pageTrail != null) {
                    log.append(" | page_trail: ").append(pageTrail.substring(0, Math.min(15, pageTrail.length()))).append("...");
                }
                log.append("\n");

                Response response = fetchEmailsBatchWithEnhancedRetry(pageTrail, "emode_focused", rateLimitTracker, log);

                if (response == null) {
                    consecutiveFailures++;
                    log.append("❌ Failed to fetch PRIMARY batch ").append(batch)
                            .append(" (").append(consecutiveFailures).append("/").append(MAX_CONSECUTIVE_FAILURES).append(")\n");

                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        log.append("❌ Too many consecutive failures. Stopping analysis.\n");
                        break;
                    }

                    long failureDelay = 5000 * consecutiveFailures;
                    log.append("⏳ Waiting ").append(failureDelay / 1000).append(" seconds after failure...\n");
                    safeSleep(failureDelay, log);
                    continue;
                }

                if (response.getStatusCode() != 200) {
                    consecutiveFailures++;
                    log.append("❌ PRIMARY API failed with status: ").append(response.getStatusCode()).append("\n");
                    continue;
                }

                consecutiveFailures = 0;

                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.getBody().asString());
                JsonNode data = root.get("data");

                if (data == null || !data.isArray() || data.size() == 0) {
                    log.append("🏁 No more PRIMARY data.\n");
                    break;
                }

                LocalDate oldestInBatch = null;
                int addedFromBatch = 0;

                for (JsonNode email : data) {
                    String id = asText(email, "id");
                    if (id != null) {
                        pageTrail = id;
                        if (seenEmailIds.contains(id)) continue;
                        seenEmailIds.add(id);
                    }

                    String ts = asText(email, "timestamp_email");
                    if (ts == null || ts.isBlank()) continue;

                    LocalDate istDay;
                    String istPretty;
                    try {
                        Instant inst = Instant.from(ISO_Z.parse(ts));
                        ZonedDateTime zIST = inst.atZone(IST);
                        istDay = zIST.toLocalDate();
                        istPretty = zIST.format(IST_OUT);
                    } catch (Exception e) {
                        continue;
                    }

                    if (oldestInBatch == null || istDay.isBefore(oldestInBatch)) {
                        oldestInBatch = istDay;
                    }

                    if (!istDay.isBefore(windowStart) && !istDay.isAfter(windowEnd)) {
                        if (!istDay.isBefore(fromDateIST) && !istDay.isAfter(toDateIST)) {
                            String lead = asText(email, "lead");
                            if (lead != null && !lead.isBlank()) {
                                EmailRow r = new EmailRow();
                                r.leadEmail = lead.trim();
                                r.subject = nz(asText(email, "subject"));
                                r.contentPreview = nz(asText(email, "content_preview"));
                                r.fromAddress = nz(asText(email, "from_address_email"));
                                r.formattedDateIST = istPretty;
                                r.timestampUTC = ts;
                                r.messageId = nz(asText(email, "message_id"));
                                r.threadId = nz(asText(email, "thread_id"));
                                allEmails.add(r);
                                addedFromBatch++;
                            }
                        }
                    }
                }

                log.append("✅ PRIMARY batch ").append(batch).append(": added=").append(addedFromBatch)
                        .append(", total=").append(allEmails.size()).append(", oldest=").append(oldestInBatch).append("\n");

                if (oldestInBatch != null && oldestInBatch.isBefore(windowStart.minusDays(5))) {
                    log.append("⏹️ Reached emails well before window. Stopping.\n");
                    break;
                }

                if (pageTrail == null || data.size() < EMAIL_LIMIT_PER_REQUEST) {
                    log.append("🏁 Reached end of PRIMARY data.\n");
                    break;
                }

                batch++;
                long delay = rateLimitTracker.hasHitRateLimit() ? BATCH_DELAY_MS * 3 : BATCH_DELAY_MS;
                safeSleep(delay, log);
            }

        } catch (Exception e) {
            log.append("❌ Error in PRIMARY fetch: ").append(e.getMessage()).append("\n");
        }

        log.append("📊 PRIMARY fetch complete: ").append(allEmails.size()).append(" emails\n");
        return allEmails;
    }

    private Response fetchEmailsBatchWithEnhancedRetry(String pageTrail, String mode,
                                                       RateLimitTracker rateLimitTracker, StringBuilder log) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Response response = fetchEmailsBatch(pageTrail, mode);

                if (response.getStatusCode() == 429) {
                    rateLimitTracker.recordRateLimitHit();
                    log.append("⚠️ RATE LIMIT HIT (429) - Attempt ").append(attempt).append("/").append(MAX_RETRIES);
                    log.append(" (Total hits: ").append(rateLimitTracker.getTotalRateLimitHits()).append(")\n");

                    if (attempt < MAX_RETRIES) {
                        long delay = RATE_LIMIT_DELAY_MS * attempt * 2;
                        log.append("⏳ EXTENDED WAIT: ").append(delay / 1000).append(" seconds for rate limit recovery...\n");
                        safeSleep(delay, log);
                        continue;
                    }
                } else if (response.getStatusCode() >= 500) {
                    log.append("⚠️ Server error (").append(response.getStatusCode()).append(") - Attempt ").append(attempt).append("\n");
                    if (attempt < MAX_RETRIES) {
                        safeSleep(INITIAL_DELAY_MS * attempt, log);
                        continue;
                    }
                }

                return response;

            } catch (Exception e) {
                log.append("⚠️ Network error - Attempt ").append(attempt).append(": ").append(e.getMessage()).append("\n");
                if (attempt < MAX_RETRIES) {
                    safeSleep(INITIAL_DELAY_MS * attempt, log);
                }
            }
        }
        return null;
    }

    private Response fetchEmailsBatch(String pageTrail, String mode) {
        String endpoint = "/backend-alt/api/v1/unibox/emails?limit=" + EMAIL_LIMIT_PER_REQUEST
                + "&preview_only=true&mode=" + mode + "&latest_of_thread=true";
        if (pageTrail != null) endpoint += "&page_trail_id=" + pageTrail;

        RequestSpecification req = RestAssured.given()
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

        log.append("👤 Getting ESP data for ").append(unique.size()).append(" unique leads...\n");

        int i = 0, total = unique.size();
        int consecutiveFailures = 0;

        for (String lead : unique) {
            i++;
            if (i % 5 == 0 || i == total) {
                log.append("🔎 ESP lookup progress: ").append(i).append("/").append(total).append("\n");
            }

            Integer code = getEspCodeForLeadWithEnhancedRetry(lead, rateLimitTracker, log);
            out.put(lead, code);

            if (code == null) {
                consecutiveFailures++;
                if (consecutiveFailures >= 5) {
                    log.append("⚠️ Multiple ESP failures. Extended delay...\n");
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
                    log.append("⚠️ Rate limit during ESP lookup for ").append(leadEmail).append("\n");
                    safeSleep(RATE_LIMIT_DELAY_MS * attempt, log);
                } else {
                    safeSleep(INITIAL_DELAY_MS * attempt, log);
                }
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) {
                    log.append("⚠️ Failed ESP lookup for ").append(leadEmail).append("\n");
                }
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
        } catch (Exception ignored) {}
        return null;
    }

    private void safeSleep(long millis, StringBuilder log) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            log.append("⚠️ Sleep interrupted\n");
            Thread.currentThread().interrupt();
        }
    }

    private void writeExcel(String fromDate, String toDate, List<EmailRow> rows, StringBuilder log) {
        // Count ESP buckets
        int google = 0, microsoft = 0, others = 0;
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

        log.append("\n📈 PRIMARY Replies ESP Breakdown:\n");
        log.append("Google: ").append(google).append("\n");
        log.append("Microsoft: ").append(microsoft).append("\n");
        log.append("Others: ").append(others).append("\n");
        log.append("Total Primary Replies: ").append(rows.size()).append("\n\n");

        try (Workbook workbook = new XSSFWorkbook()) {
            createPrimaryReportSheet(workbook, fromDate, toDate, rows.size(), google, microsoft, others);
            createEmailDataSheet(workbook, rows);

            try (FileOutputStream fileOut = new FileOutputStream(EXCEL_FILE_PATH)) {
                workbook.write(fileOut);
            }
            log.append("📁 Excel file saved: ").append(EXCEL_FILE_PATH).append("\n");
        } catch (IOException e) {
            log.append("❌ Error writing Excel file: ").append(e.getMessage()).append("\n");
            throw new RuntimeException("Error creating Excel file", e);
        }
    }

    private void createPrimaryReportSheet(Workbook workbook, String fromDate, String toDate,
                                          int totalPrimaryReplies, int googleCount, int microsoftCount, int othersCount) {
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
        r.createCell(2).setCellValue(googleCount);
        r.createCell(3).setCellValue(microsoftCount);
        r.createCell(4).setCellValue(othersCount);

        for (int i = 0; i <= 4; i++) {
            r.getCell(i).setCellStyle(data);
            sheet.autoSizeColumn(i);
            if (sheet.getColumnWidth(i) < 4000) sheet.setColumnWidth(i, 4000);
        }
    }

    private void createEmailDataSheet(Workbook workbook, List<EmailRow> rows) {
        Sheet sheet = workbook.createSheet("Primary Email Data");

        CellStyle header = headerStyle(workbook, IndexedColors.ORANGE.getIndex());
        CellStyle data = wrapBorderStyle(workbook);

        String[] headers = {
                "Lead Email", "Subject", "Content Preview", "From Address",
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
            set(r, 0, er.leadEmail, data);
            set(r, 1, er.subject, data);
            set(r, 2, er.contentPreview, data);
            set(r, 3, er.fromAddress, data);
            set(r, 4, er.formattedDateIST, data);
            set(r, 5, er.timestampUTC, data);
            set(r, 6, (er.espCode == null) ? "N/A" : String.valueOf(er.espCode), data);
            set(r, 7, er.messageId, data);
            set(r, 8, er.threadId, data);
        }

        sheet.setColumnWidth(0, 6000);
        sheet.setColumnWidth(1, 9000);
        sheet.setColumnWidth(2, 12000);
        sheet.setColumnWidth(3, 7000);
        sheet.setColumnWidth(4, 5000);
        sheet.setColumnWidth(5, 8000);
        sheet.setColumnWidth(6, 3000);
        sheet.setColumnWidth(7, 8000);
        sheet.setColumnWidth(8, 6000);
    }

    // Helper methods
    private static String asText(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return (v != null && !v.isNull()) ? v.asText() : null;
    }

    private static String nz(String s) { return (s == null) ? "" : s; }

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

    public File getLatestPrimaryRepliesExcelFile() {
        File file = new File(EXCEL_FILE_PATH);
        return file.exists() ? file : null;
    }
}