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
///**
// * Optimized Other Replies Analyzer Service - Simple date-based filtering
// * No window logic - just fetch until we get replies outside the target date range
// */
//@Service
//public class OtherRepliesAnalyzerService {
//
//    // ---- CONFIG ----
//    private static final String BASE_URL = API.BASE_URL;
//    private static final String API_KEY = API.API_KEY;
//    private static final String EXCEL_FILE_PATH = System.getProperty("java.io.tmpdir") + "other_replies_esp_report.xlsx";
//    private static final int EMAIL_LIMIT_PER_REQUEST = 30;
//
//    // Rate limiting configuration
//    private static final int MAX_RETRIES = 3;
//    private static final long INITIAL_DELAY_MS = 1000;
//    private static final long RATE_LIMIT_DELAY_MS = 5000;
//    private static final long BATCH_DELAY_MS = 500; // Reduced since no ESP lookup
//
//    // Date formats
//    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
//    private static final DateTimeFormatter ISO_Z = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]'Z'")
//            .withZone(ZoneOffset.UTC);
//    private static final DateTimeFormatter IST_OUT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
//    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
//
//    // Constructor for debugging
//    public OtherRepliesAnalyzerService() {
//        System.out.println("✅ OtherRepliesAnalyzerService initialized successfully!");
//    }
//
//    // ==== Data types ====
//    private static class EmailRow {
//        String leadEmail;
//        String subject;
//        String contentPreview;
//        String fromAddress;
//        String formattedDateIST;
//        String timestampUTC;
//        String messageId;
//        String threadId;
//    }
//
//    // Rate limit tracking class
//    private static class RateLimitTracker {
//        private boolean hasHitRateLimit = false;
//        private int totalRateLimitHits = 0;
//
//        public void recordRateLimitHit() {
//            hasHitRateLimit = true;
//            totalRateLimitHits++;
//        }
//
//        public boolean hasHitRateLimit() {
//            return hasHitRateLimit;
//        }
//
//        public int getTotalRateLimitHits() {
//            return totalRateLimitHits;
//        }
//    }
//
//    public String analyzeOtherRepliesByDateRange(String fromDateStr, String toDateStr) {
//        System.out.println("🔍 Other Replies endpoint called with dates: " + fromDateStr + " to " + toDateStr);
//
//        try {
//            StringBuilder log = new StringBuilder();
//            log.append("🎯 Target date range: ").append(fromDateStr).append(" to ").append(toDateStr).append("\n");
//            log.append("🚀 Starting OPTIMIZED Other Replies Analysis (no window logic)...\n\n");
//
//            LocalDate fromDate = LocalDate.parse(fromDateStr, DATE_FORMATTER);
//            LocalDate toDate = LocalDate.parse(toDateStr, DATE_FORMATTER);
//
//            // Global rate limit tracker
//            RateLimitTracker rateLimitTracker = new RateLimitTracker();
//
//            // Fetch OTHER emails with simple logic
//            log.append("📧 Fetching OTHER replies (mode=emode_others) until date range exceeded...\n");
//            List<EmailRow> otherRows = fetchOtherEmailsSimple(fromDate, toDate, rateLimitTracker, log);
//
//            log.append("\n📊 Analysis Summary:\n");
//            log.append("Other replies found: ").append(otherRows.size()).append("\n");
//            log.append("Rate limit incidents: ").append(rateLimitTracker.getTotalRateLimitHits()).append("\n\n");
//
//            if (otherRows.isEmpty()) {
//                log.append("❌ No other replies found in the given date range.\n");
//                writeExcel(fromDateStr, toDateStr, otherRows, log);
//                return log.toString();
//            }
//
//            // Export results (no ESP lookup needed for other replies)
//            writeExcel(fromDateStr, toDateStr, otherRows, log);
//            log.append("✅ Other Replies Excel report generated successfully!\n");
//            log.append("📁 File ready for download.\n");
//
//            return log.toString();
//
//        } catch (Exception e) {
//            System.err.println("❌ Error in other replies analysis: " + e.getMessage());
//            e.printStackTrace();
//            return "❌ Other Replies Analysis failed: " + e.getMessage();
//        }
//    }
//
//    /**
//     * Simplified email fetching logic:
//     * 1. Call API sequentially
//     * 2. Check if email date is in target range - if yes, add it
//     * 3. If we encounter emails older than fromDate, stop
//     * 4. No complex window logic needed
//     */
//    private List<EmailRow> fetchOtherEmailsSimple(LocalDate fromDateIST, LocalDate toDateIST,
//                                                  RateLimitTracker rateLimitTracker, StringBuilder log) {
//        List<EmailRow> allEmails = new ArrayList<>();
//        Set<String> seenEmailIds = new HashSet<>();
//
//        log.append("📅 Simple date filtering: ").append(fromDateIST).append(" to ").append(toDateIST).append("\n");
//
//        String pageTrail = null;
//        int batch = 1;
//        int consecutiveFailures = 0;
//        final int MAX_CONSECUTIVE_FAILURES = 3;
//        boolean shouldStop = false;
//
//        try {
//            while (!shouldStop) {
//                log.append("📡 OTHER batch ").append(batch);
//                if (pageTrail != null) {
//                    log.append(" | page_trail: ").append(pageTrail.substring(0, Math.min(15, pageTrail.length()))).append("...");
//                }
//                log.append("\n");
//
//                Response response = fetchEmailsBatchWithRetry(pageTrail, "emode_others", rateLimitTracker, log);
//
//                if (response == null) {
//                    consecutiveFailures++;
//                    log.append("❌ Failed to fetch OTHER batch ").append(batch)
//                            .append(" (").append(consecutiveFailures).append("/").append(MAX_CONSECUTIVE_FAILURES).append(")\n");
//
//                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
//                        log.append("❌ Too many consecutive failures. Stopping analysis.\n");
//                        break;
//                    }
//
//                    long failureDelay = 2000 * consecutiveFailures; // Reduced delay
//                    log.append("⏳ Waiting ").append(failureDelay / 1000).append(" seconds after failure...\n");
//                    safeSleep(failureDelay, log);
//                    continue;
//                }
//
//                if (response.getStatusCode() != 200) {
//                    consecutiveFailures++;
//                    log.append("❌ OTHER API failed with status: ").append(response.getStatusCode()).append("\n");
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
//                    log.append("🏁 No more OTHER data available.\n");
//                    break;
//                }
//
//                LocalDate oldestInBatch = null;
//                int addedFromBatch = 0;
//                int outsideRangeCount = 0;
//
//                for (JsonNode email : data) {
//                    String id = asText(email, "id");
//                    if (id != null) {
//                        pageTrail = id;
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
//                    // Track oldest date in this batch
//                    if (oldestInBatch == null || istDay.isBefore(oldestInBatch)) {
//                        oldestInBatch = istDay;
//                    }
//
//                    // Check if email is in target date range
//                    if (!istDay.isBefore(fromDateIST) && !istDay.isAfter(toDateIST)) {
//                        // Email is in target range - add it
//                        String lead = asText(email, "lead");
//                        if (lead != null && !lead.isBlank()) {
//                            EmailRow r = new EmailRow();
//                            r.leadEmail = lead.trim();
//                            r.subject = nz(asText(email, "subject"));
//                            r.contentPreview = nz(asText(email, "content_preview"));
//                            r.fromAddress = nz(asText(email, "from_address_email"));
//                            r.formattedDateIST = istPretty;
//                            r.timestampUTC = ts;
//                            r.messageId = nz(asText(email, "message_id"));
//                            r.threadId = nz(asText(email, "thread_id"));
//                            allEmails.add(r);
//                            addedFromBatch++;
//                        }
//                    } else if (istDay.isBefore(fromDateIST)) {
//                        // Email is older than our target range
//                        outsideRangeCount++;
//                    }
//                    // If istDay.isAfter(toDateIST), we just skip it (newer than target)
//                }
//
//                log.append("✅ Batch ").append(batch).append(": added=").append(addedFromBatch)
//                        .append(", total=").append(allEmails.size())
//                        .append(", oldest=").append(oldestInBatch)
//                        .append(", outside_range=").append(outsideRangeCount).append("\n");
//
//                // Stop condition: if we have a significant number of emails older than our target range
//                if (outsideRangeCount >= 5) {
//                    log.append("⏹️ Found ").append(outsideRangeCount).append(" emails older than target range. Stopping.\n");
//                    shouldStop = true;
//                }
//
//                // Also stop if we've reached much older dates
//                if (oldestInBatch != null && oldestInBatch.isBefore(fromDateIST.minusDays(3))) {
//                    log.append("⏹️ Reached emails from ").append(oldestInBatch).append(" (3+ days before target). Stopping.\n");
//                    shouldStop = true;
//                }
//
//                // Stop if we got less than full batch (end of data)
//                if (pageTrail == null || data.size() < EMAIL_LIMIT_PER_REQUEST) {
//                    log.append("🏁 Reached end of OTHER data (incomplete batch).\n");
//                    shouldStop = true;
//                }
//
//                batch++;
//
//                // Reduced delay since no ESP lookup
//                if (!shouldStop) {
//                    safeSleep(BATCH_DELAY_MS, log);
//                }
//            }
//
//        } catch (Exception e) {
//            log.append("❌ Error in OTHER fetch: ").append(e.getMessage()).append("\n");
//            e.printStackTrace();
//        }
//
//        log.append("📊 OTHER fetch complete: ").append(allEmails.size()).append(" emails\n");
//        return allEmails;
//    }
//
//    private Response fetchEmailsBatchWithRetry(String pageTrail, String mode,
//                                               RateLimitTracker rateLimitTracker, StringBuilder log) {
//        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
//            try {
//                Response response = fetchEmailsBatch(pageTrail, mode);
//
//                if (response.getStatusCode() == 429) {
//                    rateLimitTracker.recordRateLimitHit();
//                    log.append("⚠️ RATE LIMIT HIT (429) - Attempt ").append(attempt).append("/").append(MAX_RETRIES).append("\n");
//
//                    if (attempt < MAX_RETRIES) {
//                        long delay = RATE_LIMIT_DELAY_MS * attempt;
//                        log.append("⏳ Waiting ").append(delay / 1000).append(" seconds for rate limit recovery...\n");
//                        safeSleep(delay, log);
//                        continue;
//                    }
//                } else if (response.getStatusCode() >= 500) {
//                    log.append("⚠️ Server error (").append(response.getStatusCode()).append(") - Attempt ").append(attempt).append("\n");
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
//                if (attempt < MAX_RETRIES) {
//                    safeSleep(INITIAL_DELAY_MS * attempt, log);
//                }
//            }
//        }
//        return null;
//    }
//
//    private Response fetchEmailsBatch(String pageTrail, String mode) {
//        String endpoint = "/backend-alt/api/v1/unibox/emails?limit=" + EMAIL_LIMIT_PER_REQUEST
//                + "&preview_only=true&mode=" + mode + "&latest_of_thread=false";
//        if (pageTrail != null) endpoint += "&page_trail_id=" + pageTrail;
//
//        RequestSpecification req = RestAssured.given()
//                .baseUri(BASE_URL)
//                .header("X-org-auth", API_KEY)
//                .header("Content-Type", "application/json")
//                .header("Connection", "keep-alive")
//                .relaxedHTTPSValidation();
//
//        return req.when().get(endpoint).then().extract().response();
//    }
//
//    private void safeSleep(long millis, StringBuilder log) {
//        try {
//            Thread.sleep(millis);
//        } catch (InterruptedException e) {
//            log.append("⚠️ Sleep interrupted\n");
//            Thread.currentThread().interrupt();
//        }
//    }
//
//    private void writeExcel(String fromDate, String toDate, List<EmailRow> rows, StringBuilder log) {
//        log.append("\n📈 OTHER Replies Summary:\n");
//        log.append("Total Other Replies: ").append(rows.size()).append("\n\n");
//
//        try (Workbook workbook = new XSSFWorkbook()) {
//            createOtherReportSheet(workbook, fromDate, toDate, rows.size());
//            createEmailDataSheet(workbook, rows);
//
//            try (FileOutputStream fileOut = new FileOutputStream(EXCEL_FILE_PATH)) {
//                workbook.write(fileOut);
//            }
//            log.append("📁 Excel file saved: ").append(EXCEL_FILE_PATH).append("\n");
//        } catch (IOException e) {
//            log.append("❌ Error writing Excel file: ").append(e.getMessage()).append("\n");
//            throw new RuntimeException("Error creating Excel file", e);
//        }
//    }
//
//    private void createOtherReportSheet(Workbook workbook, String fromDate, String toDate, int totalOtherReplies) {
//        Sheet sheet = workbook.createSheet("Other Replies Report");
//
//        CellStyle header = headerStyle(workbook, IndexedColors.LIGHT_GREEN.getIndex());
//        CellStyle data = borderStyle(workbook);
//
//        Row h = sheet.createRow(0);
//        String[] cols = {"Date", "Total Other Replies"};
//        for (int i = 0; i < cols.length; i++) {
//            Cell c = h.createCell(i);
//            c.setCellValue(cols[i]);
//            c.setCellStyle(header);
//        }
//
//        Row r = sheet.createRow(1);
//        r.createCell(0).setCellValue(fromDate.equals(toDate) ? fromDate : (fromDate + " to " + toDate));
//        r.createCell(1).setCellValue(totalOtherReplies);
//
//        for (int i = 0; i <= 1; i++) {
//            r.getCell(i).setCellStyle(data);
//            sheet.autoSizeColumn(i);
//            if (sheet.getColumnWidth(i) < 4000) sheet.setColumnWidth(i, 4000);
//        }
//    }
//
//    private void createEmailDataSheet(Workbook workbook, List<EmailRow> rows) {
//        Sheet sheet = workbook.createSheet("Other Email Data");
//
//        CellStyle header = headerStyle(workbook, IndexedColors.LIGHT_YELLOW.getIndex());
//        CellStyle data = wrapBorderStyle(workbook);
//
//        String[] headers = {
//                "Lead Email", "Subject", "Content Preview", "From Address",
//                "Formatted Date", "Timestamp", "Message ID", "Thread ID"
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
//            set(r, 0, er.leadEmail, data);
//            set(r, 1, er.subject, data);
//            set(r, 2, er.contentPreview, data);
//            set(r, 3, er.fromAddress, data);
//            set(r, 4, er.formattedDateIST, data);
//            set(r, 5, er.timestampUTC, data);
//            set(r, 6, er.messageId, data);
//            set(r, 7, er.threadId, data);
//        }
//
//        sheet.setColumnWidth(0, 6000);
//        sheet.setColumnWidth(1, 9000);
//        sheet.setColumnWidth(2, 12000);
//        sheet.setColumnWidth(3, 7000);
//        sheet.setColumnWidth(4, 5000);
//        sheet.setColumnWidth(5, 8000);
//        sheet.setColumnWidth(6, 8000);
//        sheet.setColumnWidth(7, 6000);
//    }
//
//    // Helper methods
//    private static String asText(JsonNode n, String field) {
//        JsonNode v = n.get(field);
//        return (v != null && !v.isNull()) ? v.asText() : null;
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
//    public File getLatestOtherRepliesExcelFile() {
//        File file = new File(EXCEL_FILE_PATH);
//        return file.exists() ? file : null;
//    }
//}
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
 * Optimized Other Replies Analyzer Service - Simple date-based filtering
 * No window logic - just fetch until we get replies outside the target date range
 */
@Service
public class OtherRepliesAnalyzerService {

    // ---- CONFIG ----
    private static final String BASE_URL = API.BASE_URL;
    private static final String API_KEY = API.API_KEY;
    private static final String EXCEL_FILE_PATH = System.getProperty("java.io.tmpdir") + "other_replies_esp_report.xlsx";
    private static final int EMAIL_LIMIT_PER_REQUEST = 30;
    private static final String JWT_Token = API.JWT_Token;

    // Rate limiting configuration
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_DELAY_MS = 1000;
    private static final long RATE_LIMIT_DELAY_MS = 5000;
    private static final long BATCH_DELAY_MS = 500; // Reduced since no ESP lookup

    // Date formats
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter ISO_Z = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]'Z'")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter IST_OUT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // Constructor for debugging
    public OtherRepliesAnalyzerService() {
        System.out.println("✅ OtherRepliesAnalyzerService initialized successfully!");
    }

    // ==== Data types ====
    private static class EmailRow {
        String leadEmail;
        String subject;
        String MessageText;
        String fromAddress;
        String toAddress;
        String formattedDateIST;
        String timestampUTC;
        String messageId;
        String threadId;
    }

    // Rate limit tracking class
    private static class RateLimitTracker {
        private boolean hasHitRateLimit = false;
        private int totalRateLimitHits = 0;

        public void recordRateLimitHit() {
            hasHitRateLimit = true;
            totalRateLimitHits++;
        }

        public boolean hasHitRateLimit() {
            return hasHitRateLimit;
        }

        public int getTotalRateLimitHits() {
            return totalRateLimitHits;
        }
    }

    public String analyzeOtherRepliesByDateRange(String fromDateStr, String toDateStr) {
        System.out.println("🔍 Other Replies endpoint called with dates: " + fromDateStr + " to " + toDateStr);

        try {
            StringBuilder log = new StringBuilder();
            log.append("🎯 Target date range: ").append(fromDateStr).append(" to ").append(toDateStr).append("\n");
            log.append("🚀 Starting OPTIMIZED Other Replies Analysis (no window logic)...\n\n");

            LocalDate fromDate = LocalDate.parse(fromDateStr, DATE_FORMATTER);
            LocalDate toDate = LocalDate.parse(toDateStr, DATE_FORMATTER);

            // Global rate limit tracker
            RateLimitTracker rateLimitTracker = new RateLimitTracker();

            // Fetch OTHER emails with simple logic
            log.append("📧 Fetching OTHER replies (mode=emode_others) until date range exceeded...\n");
            List<EmailRow> otherRows = fetchOtherEmailsSimple(fromDate, toDate, rateLimitTracker, log);

            log.append("\n📊 Analysis Summary:\n");
            log.append("Other replies found: ").append(otherRows.size()).append("\n");
            log.append("Rate limit incidents: ").append(rateLimitTracker.getTotalRateLimitHits()).append("\n\n");

            if (otherRows.isEmpty()) {
                log.append("❌ No other replies found in the given date range.\n");
                writeExcel(fromDateStr, toDateStr, otherRows, log);
                return log.toString();
            }

            // Export results (no ESP lookup needed for other replies)
            writeExcel(fromDateStr, toDateStr, otherRows, log);
            log.append("✅ Other Replies Excel report generated successfully!\n");
            log.append("📁 File ready for download.\n");

            return log.toString();

        } catch (Exception e) {
            System.err.println("❌ Error in other replies analysis: " + e.getMessage());
            e.printStackTrace();
            return "❌ Other Replies Analysis failed: " + e.getMessage();
        }
    }

    /**
     * Simplified email fetching logic:
     * 1. Call API sequentially
     * 2. Check if email date is in target range - if yes, add it
     * 3. If we encounter emails older than fromDate, stop
     * 4. No complex window logic needed
     */
    private List<EmailRow> fetchOtherEmailsSimple(LocalDate fromDateIST, LocalDate toDateIST,
                                                  RateLimitTracker rateLimitTracker, StringBuilder log) {
        List<EmailRow> allEmails = new ArrayList<>();
        Set<String> seenEmailIds = new HashSet<>();
       // System.out.println("fromDateIST "+fromDateIST);
     //   System.out.println("toDateIST "+toDateIST);
        log.append("📅 Simple date filtering: ").append(fromDateIST).append(" to ").append(toDateIST).append("\n");
        LocalDate fromDate = LocalDate.now().minusDays(1);
        String pageTrail = null;
        int skipValue = 0; // Add skip tracking
        int batch = 1;
        LocalDate startFetchDate = toDateIST.plusDays(1);
        boolean isFirstCall= true;
        int consecutiveFailures = 0;
        final int MAX_CONSECUTIVE_FAILURES = 3;
        boolean shouldStop = false;

        try {
            while (!shouldStop) {
                log.append("📡 OTHER batch ").append(batch);
                if (pageTrail != null) {
                    log.append(" | page_trail: ").append(pageTrail.substring(0, Math.min(15, pageTrail.length()))).append("...");
                }
                log.append(" | skip: ").append(skipValue).append("\n"); // Add skip to logging

                Response response = fetchEmailsBatchWithRetry(isFirstCall,startFetchDate,pageTrail, "emode_others", rateLimitTracker, log);

                if (response == null) {
                    consecutiveFailures++;
                    log.append("❌ Failed to fetch OTHER batch ").append(batch)
                            .append(" (").append(consecutiveFailures).append("/").append(MAX_CONSECUTIVE_FAILURES).append(")\n");

                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        log.append("❌ Too many consecutive failures. Stopping analysis.\n");
                        break;
                    }

                    long failureDelay = 2000 * consecutiveFailures; // Reduced delay
                    log.append("⏳ Waiting ").append(failureDelay / 1000).append(" seconds after failure...\n");
                    safeSleep(failureDelay, log);
                    continue;
                }

                if (response.getStatusCode() != 200) {
                    consecutiveFailures++;
                    log.append("❌ OTHER API failed with status: ").append(response.getStatusCode()).append("\n");
                    continue;
                }

                consecutiveFailures = 0;

                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.getBody().asString());
                JsonNode data = root.get("data");

                if (data == null || !data.isArray() || data.size() == 0) {
                    log.append("🏁 No more OTHER data available.\n");
                    break;
                }

                LocalDate oldestInBatch = null;
                int addedFromBatch = 0;
                int outsideRangeCount = 0;
                String lastEmailId = null; // Track last email ID for page_trail

                for (JsonNode email : data) {
                    String id = asText(email, "id");
                    if (id != null) {
                        lastEmailId = id;// Always update to get the latest ID
                        isFirstCall = false;
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

                    // Track oldest date in this batch
                    if (oldestInBatch == null || istDay.isBefore(oldestInBatch)) {
                        oldestInBatch = istDay;
                    }
                    System.out.println("istDay.isBefore(fromDateIST) "+istDay.isBefore(fromDateIST));
                    System.out.println("istDay.isAfter(toDateIST) "+istDay.isAfter(toDateIST));
                    // Check if email is in target date range
                    if (!istDay.isBefore(fromDateIST) && !istDay.isAfter(toDateIST)) {
                        // Email is in target range - add it
                        //System.out.println("Response: "+response.getBody().prettyPrint());
                        String lead = asText(email, "lead");
                        if (lead != null && !lead.isBlank()) {
                            EmailRow r = new EmailRow();
                            r.leadEmail = lead.trim();
                            r.subject = nz(asText(email, "subject"));
                            r.MessageText = nz(asText(email, "text"));
                            r.fromAddress = nz(asText(email, "from_address_email"));
                            r.toAddress = nz(asText(email,"to_address_email_list"));
                            r.formattedDateIST = istPretty;
                            r.timestampUTC = ts;
                            r.messageId = nz(asText(email, "message_id"));
                            r.threadId = nz(asText(email, "thread_id"));
                            allEmails.add(r);
                            addedFromBatch++;
                        }
                    } else if (istDay.isBefore(fromDateIST)) {
                        // Email is older than our target range
                        outsideRangeCount++;
                    }
                    // If istDay.isAfter(toDateIST), we just skip it (newer than target)
                }

                log.append("✅ Batch ").append(batch).append(": added=").append(addedFromBatch)
                        .append(", total=").append(allEmails.size())
                        .append(", oldest=").append(oldestInBatch)
                        .append(", outside_range=").append(outsideRangeCount).append("\n");

                // Stop condition: if we have a significant number of emails older than our target range
                if (outsideRangeCount >= 5) {
                    log.append("⏹️ Found ").append(outsideRangeCount).append(" emails older than target range. Stopping.\n");
                    shouldStop = true;
                }

                // Also stop if we've reached much older dates
                if (oldestInBatch != null && oldestInBatch.isBefore(fromDateIST.minusDays(3))) {
                    log.append("⏹️ Reached emails from ").append(oldestInBatch).append(" (3+ days before target). Stopping.\n");
                    shouldStop = true;
                }

                // Updated pagination logic matching primary service
                if (data.size() < EMAIL_LIMIT_PER_REQUEST) {
                    log.append("🏁 Reached end of OTHER data (incomplete batch).\n");
                    shouldStop = true;
                } else {
                    // Update both skip and pageTrail for next call
                    skipValue += EMAIL_LIMIT_PER_REQUEST;
                    pageTrail = lastEmailId;
                    log.append("📤 Next call: skip=").append(skipValue)
                            .append(", page_trail_id=").append(pageTrail != null ? pageTrail : "NULL").append("\n");
                }

                batch++;

                // Reduced delay since no ESP lookup
                if (!shouldStop) {
                    safeSleep(BATCH_DELAY_MS, log);
                }
            }

        } catch (Exception e) {
            log.append("❌ Error in OTHER fetch: ").append(e.getMessage()).append("\n");
            e.printStackTrace();
        }

        log.append("📊 OTHER fetch complete: ").append(allEmails.size()).append(" emails\n");
        return allEmails;
    }

    private Response fetchEmailsBatchWithRetry(boolean isFirstCall,LocalDate page_trail,String pageTrail, String mode,
                                               RateLimitTracker rateLimitTracker, StringBuilder log) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Response response = fetchEmailsBatch(isFirstCall,page_trail,pageTrail, mode);

                if (response.getStatusCode() == 429) {
                    rateLimitTracker.recordRateLimitHit();
                    log.append("⚠️ RATE LIMIT HIT (429) - Attempt ").append(attempt).append("/").append(MAX_RETRIES).append("\n");

                    if (attempt < MAX_RETRIES) {
                        long delay = RATE_LIMIT_DELAY_MS * attempt;
                        log.append("⏳ Waiting ").append(delay / 1000).append(" seconds for rate limit recovery...\n");
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

    private Response fetchEmailsBatch(boolean isFirstCall,LocalDate page_trail,String pageTrail, String mode) {
        String endpoint = "/backend-alt/api/v1/unibox/emails?limit=" + EMAIL_LIMIT_PER_REQUEST
                + "&preview_only=false&mode=" + mode + "&latest_of_thread=false";

        if(isFirstCall && pageTrail==null){
            endpoint+="&page_trail="+page_trail;
        }
        if (pageTrail != null && !pageTrail.trim().isEmpty()) {
            endpoint += "&page_trail_id=" + pageTrail;
        }

        // Add debug logging to see the actual endpoint being called
        System.out.println("DEBUG - Full Endpoint: " + endpoint);

        RequestSpecification req = RestAssured.given()
                .baseUri(BASE_URL)
                .header("X-org-auth", API_KEY)
               // .header("Authorization","Bearer "+JWT_Token)
                .header("Content-Type", "application/json")
                .header("Connection", "keep-alive")
                .relaxedHTTPSValidation();

        return req.when().get(endpoint).then().extract().response();
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
        log.append("\n📈 OTHER Replies Summary:\n");
        log.append("Total Other Replies: ").append(rows.size()).append("\n\n");

        try (Workbook workbook = new XSSFWorkbook()) {
            createOtherReportSheet(workbook, fromDate, toDate, rows.size());
            createEmailDataSheet(workbook, rows); // Add email data sheet

            try (FileOutputStream fileOut = new FileOutputStream(EXCEL_FILE_PATH)) {
                workbook.write(fileOut);
            }
            log.append("📁 Excel file saved: ").append(EXCEL_FILE_PATH).append("\n");
        } catch (IOException e) {
            log.append("❌ Error writing Excel file: ").append(e.getMessage()).append("\n");
            throw new RuntimeException("Error creating Excel file", e);
        }
    }

    private void createOtherReportSheet(Workbook workbook, String fromDate, String toDate, int totalOtherReplies) {
        Sheet sheet = workbook.createSheet("Other Replies Report");

        CellStyle header = headerStyle(workbook, IndexedColors.LIGHT_GREEN.getIndex());
        CellStyle data = borderStyle(workbook);

        Row h = sheet.createRow(0);
        String[] cols = {"Date", "Total Other Replies"};
        for (int i = 0; i < cols.length; i++) {
            Cell c = h.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(header);
        }

        Row r = sheet.createRow(1);
        r.createCell(0).setCellValue(fromDate.equals(toDate) ? fromDate : (fromDate + " to " + toDate));
        r.createCell(1).setCellValue(totalOtherReplies);

        for (int i = 0; i <= 1; i++) {
            r.getCell(i).setCellStyle(data);
            sheet.autoSizeColumn(i);
            if (sheet.getColumnWidth(i) < 4000) sheet.setColumnWidth(i, 4000);
        }
    }

    private void createEmailDataSheet(Workbook workbook, List<EmailRow> rows) {
        Sheet sheet = workbook.createSheet("Other Email Data");
        final int EXCEL_CELL_LIMIT = 32767;
        CellStyle header = headerStyle(workbook, IndexedColors.LIGHT_YELLOW.getIndex());
        CellStyle data = wrapBorderStyle(workbook);

        String[] headers = {
                "Lead Email", "Subject", "Message Text", "From Address",
                "Formatted Date", "Timestamp", "Message ID", "Thread ID"
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
            String messageText = er.MessageText;
            if(messageText != null && messageText.length()>EXCEL_CELL_LIMIT){
                messageText = messageText.substring(0,EXCEL_CELL_LIMIT);
            }

            set(r, 0, er.leadEmail, data);
            set(r, 1, er.subject, data);
            set(r, 2, messageText, data);
            set(r, 3,er.toAddress,data);
            set(r, 4, er.fromAddress, data);
            set(r, 5, er.formattedDateIST, data);
            set(r, 6, er.timestampUTC, data);
            set(r, 7, er.messageId, data);
            set(r, 8, er.threadId, data);
        }

        sheet.setColumnWidth(0, 6000);
        sheet.setColumnWidth(1, 9000);
        sheet.setColumnWidth(2, 12000);
        sheet.setColumnWidth(3, 7000);
        sheet.setColumnWidth(4, 5000);
        sheet.setColumnWidth(5, 8000);
        sheet.setColumnWidth(6, 8000);
        sheet.setColumnWidth(7, 6000);
    }

    // Helper methods
    private static String asText(JsonNode n, String field) {
        JsonNode v =null;
        if(field.equals("text")){
            v = n.get("body").get("text");
        }
        else{
            v = n.get(field);
        }
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

    public File getLatestOtherRepliesExcelFile() {
        File file = new File(EXCEL_FILE_PATH);
        return file.exists() ? file : null;
    }
}