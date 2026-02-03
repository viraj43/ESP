package com.LeadAnalysis.ESPAnalysis.service;

import com.LeadAnalysis.ESPAnalysis.config.API;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class EspAnalyzerServiceBackup {
}
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
///**
// * Uses only esp_code from Instantly API with proper rate limiting.
// * Exports two sheets:
// *  1) Daily ESP Report: Date | Total No. of Replies | Google | Microsoft | Others
// *  2) Email Data: Lead Email | Subject | Content Preview | From Address | Formatted Date | Timestamp | ESP Code | Message ID | Thread ID
// */
//@Service
//public class EspAnalyzerService {
//
//    // ---- CONFIG: set your own values ----
//    private static final String BASE_URL = API.BASE_URL;
//    private static final String API_KEY = API.API_KEY;
//    private static final String EXCEL_FILE_PATH = System.getProperty("java.io.tmpdir") + "daily_email_esp_report.xlsx";
//    private static final int EMAIL_LIMIT_PER_REQUEST = 30; // Instantly limit
//
//    // Rate limiting configuration
//    private static final int MAX_RETRIES = 3;
//    private static final long INITIAL_DELAY_MS = 1000; // 1 second
//    private static final long RATE_LIMIT_DELAY_MS = 5000; // 5 seconds for 429 errors
//    private static final long BATCH_DELAY_MS = 800; // Delay between batches
//    private static final long ESP_LOOKUP_DELAY_MS = 300; // Delay between ESP lookups
//
//    // We only care about 1 -> Google, 2 -> Microsoft. Others -> "Others"
//    private static final int CODE_GOOGLE = 1;
//    private static final int CODE_MICROSOFT = 2;
//
//    // Formats
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
//        String contentPreview;
//        String fromAddress;
//        String formattedDateIST;
//        String timestampUTC;
//        Integer espCode;
//        String messageId;
//        String threadId;
//    }
//
//    public String analyzeESPByDateRange(String fromDateStr, String toDateStr) {
//        try {
//            StringBuilder log = new StringBuilder();
//            log.append("🎯 Target date range: ").append(fromDateStr).append(" to ").append(toDateStr).append("\n");
//            log.append("🚀 Starting Date-Filtered ESP Analysis with enhanced rate limiting...\n");
//
//            LocalDate fromDate = LocalDate.parse(fromDateStr, DATE_FORMATTER);
//            LocalDate toDate = LocalDate.parse(toDateStr, DATE_FORMATTER);
//
//            // 1) Fetch emails within IST date range with rate limiting
//            List<EmailRow> rows = fetchEmailsByDateRange(fromDate, toDate, log);
//
//            if (rows.isEmpty()) {
//                log.append("❌ No emails found in the given date range.\n");
//                writeExcel(fromDateStr, toDateStr, rows, log);
//                return log.toString();
//            }
//
//            // 2) For each unique lead, fetch esp_code from API with rate limiting
//            Map<String, Integer> espByLead = fetchEspCodesWithRateLimit(rows, log);
//            for (EmailRow r : rows) {
//                r.espCode = espByLead.get(r.leadEmail);
//            }
//
//            // 3) Export
//            writeExcel(fromDateStr, toDateStr, rows, log);
//            log.append("✅ Excel report generated successfully!\n");
//            log.append("📁 File ready for download.\n");
//
//            return log.toString();
//
//        } catch (Exception e) {
//            return "❌ Analysis failed: " + e.getMessage();
//        }
//    }
//
//    private List<EmailRow> fetchEmailsByDateRange(LocalDate fromDateIST, LocalDate toDateIST, StringBuilder log) {
//        List<EmailRow> out = new ArrayList<>();
//        String pageTrail = null;
//        int batch = 1;
//        int consecutiveFailures = 0;
//
//        try {
//            while (true) {
//                log.append("📡 Fetching email batch ").append(batch);
//                if (pageTrail != null) {
//                    log.append(" | page_trail: ").append(pageTrail.substring(0, Math.min(15, pageTrail.length()))).append("...");
//                }
//                log.append("\n");
//
//                Response response = fetchEmailsBatchWithRetry(pageTrail, log);
//
//                if (response == null) {
//                    log.append("❌ Failed to fetch batch after retries. Stopping.\n");
//                    break;
//                }
//
//                if (response.getStatusCode() != 200) {
//                    consecutiveFailures++;
//                    log.append("❌ Email API call failed with status: ").append(response.getStatusCode()).append("\n");
//
//                    if (consecutiveFailures >= 3) {
//                        log.append("❌ Too many consecutive failures. Stopping.\n");
//                        break;
//                    }
//
//                    // Wait longer before next attempt
//                    safeSleep(RATE_LIMIT_DELAY_MS, log);
//                    continue;
//                }
//
//                consecutiveFailures = 0; // Reset on success
//
//                ObjectMapper mapper = new ObjectMapper();
//                JsonNode root = mapper.readTree(response.getBody().asString());
//                JsonNode data = root.get("data");
//
//                if (data == null || !data.isArray() || data.size() == 0) {
//                    log.append("🏁 No more data.\n");
//                    break;
//                }
//
//                boolean reachedOlder = false;
//                int received = data.size();
//                int addedFromBatch = 0;
//
//                for (JsonNode email : data) {
//                    String id = asText(email, "id");
//                    if (id != null) pageTrail = id;
//
//                    String ts = asText(email, "timestamp_email");
//                    if (ts == null || ts.isBlank()) continue;
//
//                    LocalDate istDay;
//                    String istPretty = ts;
//                    try {
//                        Instant inst = Instant.from(ISO_Z.parse(ts));
//                        ZonedDateTime zIST = inst.atZone(IST);
//                        istDay = zIST.toLocalDate();
//                        istPretty = zIST.format(IST_OUT);
//                    } catch (Exception e) {
//                        continue;
//                    }
//
//                    if (istDay.isBefore(fromDateIST)) {
//                        reachedOlder = true;
//                        break;
//                    }
//
//                    if (!istDay.isBefore(fromDateIST) && !istDay.isAfter(toDateIST)) {
//                        String lead = asText(email, "lead");
//                        if (lead == null || lead.isBlank()) continue;
//
//                        EmailRow r = new EmailRow();
//                        r.leadEmail = lead.trim();
//                        r.subject = nz(asText(email, "subject"));
//                        r.contentPreview = nz(asText(email, "content_preview"));
//                        r.fromAddress = nz(asText(email, "from_address_email"));
//                        r.formattedDateIST = istPretty;
//                        r.timestampUTC = ts;
//                        r.messageId = nz(asText(email, "message_id"));
//                        r.threadId = nz(asText(email, "thread_id"));
//                        out.add(r);
//                        addedFromBatch++;
//                    }
//                }
//
//                log.append("✅ Batch ").append(batch).append(" received=").append(received)
//                        .append(", added=").append(addedFromBatch)
//                        .append(", total so far=").append(out.size()).append("\n");
//
//                if (reachedOlder) {
//                    log.append("⏹️ Reached emails older than ").append(fromDateIST).append(" IST. Stopping.\n");
//                    break;
//                }
//                if (pageTrail == null || received < EMAIL_LIMIT_PER_REQUEST) break;
//
//                batch++;
//
//                // Progressive delay - longer delays for more batches
//                long delay = BATCH_DELAY_MS + (batch > 10 ? (batch - 10) * 200 : 0);
//                safeSleep(delay, log);
//            }
//        } catch (Exception e) {
//            log.append("❌ Error fetching emails: ").append(e.getMessage()).append("\n");
//        }
//
//        return out;
//    }
//
//    private Response fetchEmailsBatchWithRetry(String pageTrail, StringBuilder log) {
//        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
//            try {
//                Response response = fetchEmailsBatch(pageTrail);
//
//                if (response.getStatusCode() == 429) {
//                    log.append("⚠️ Rate limit hit (429) on attempt ").append(attempt).append("/").append(MAX_RETRIES).append("\n");
//                    if (attempt < MAX_RETRIES) {
//                        long delay = RATE_LIMIT_DELAY_MS * attempt; // Exponential backoff
//                        log.append("⏳ Waiting ").append(delay / 1000).append(" seconds before retry...\n");
//                        safeSleep(delay, log);
//                        continue;
//                    }
//                } else if (response.getStatusCode() >= 500) {
//                    log.append("⚠️ Server error (").append(response.getStatusCode()).append(") on attempt ").append(attempt).append("/").append(MAX_RETRIES).append("\n");
//                    if (attempt < MAX_RETRIES) {
//                        safeSleep(INITIAL_DELAY_MS * attempt, log);
//                        continue;
//                    }
//                }
//
//                return response;
//
//            } catch (Exception e) {
//                log.append("⚠️ Network error on attempt ").append(attempt).append(": ").append(e.getMessage()).append("\n");
//                if (attempt < MAX_RETRIES) {
//                    safeSleep(INITIAL_DELAY_MS * attempt, log);
//                }
//            }
//        }
//        return null;
//    }
//
//    private Response fetchEmailsBatch(String pageTrail) {
//        String endpoint = "/backend-alt/api/v1/unibox/emails?limit=" + EMAIL_LIMIT_PER_REQUEST
//                + "&preview_only=true&mode=emode_focused&latest_of_thread=true";
//        if (pageTrail != null) endpoint += "&page_trail_id=" + pageTrail;
//
//        RequestSpecification req = RestAssured.given()
//                .baseUri(BASE_URL)
//                .header("X-org-auth", API_KEY)
//                .header("Content-Type", "application/json")
//                .header("Connection", "keep-alive")
//                .relaxedHTTPSValidation(); // Add this to handle SSL issues if any
//
//        return req.when().get(endpoint).then().extract().response();
//    }
//
//    private Map<String, Integer> fetchEspCodesWithRateLimit(List<EmailRow> rows, StringBuilder log) {
//        Map<String, Integer> out = new HashMap<>();
//        Set<String> unique = new HashSet<>();
//        for (EmailRow r : rows) {
//            if (r.leadEmail != null && !r.leadEmail.isBlank()) unique.add(r.leadEmail);
//        }
//
//        log.append("👤 Getting ESP data for ").append(unique.size()).append(" unique leads with enhanced rate limiting...\n");
//
//        int i = 0, total = unique.size();
//        int consecutiveFailures = 0;
//
//        for (String lead : unique) {
//            i++;
//            if (i % 5 == 0 || i == total) { // More frequent progress updates
//                log.append("🔎 ESP lookup progress: ").append(i).append("/").append(total).append("\n");
//            }
//
//            Integer code = getEspCodeForLeadWithRetry(lead, log);
//            out.put(lead, code);
//
//            if (code == null) {
//                consecutiveFailures++;
//                if (consecutiveFailures >= 5) {
//                    log.append("⚠️ Multiple ESP lookup failures. Increasing delay...\n");
//                    safeSleep(ESP_LOOKUP_DELAY_MS * 2, log);
//                    consecutiveFailures = 0;
//                }
//            } else {
//                consecutiveFailures = 0;
//            }
//
//            // Progressive delay based on number of requests
//            long delay = ESP_LOOKUP_DELAY_MS + (i > 50 ? (i / 50) * 100 : 0);
//            safeSleep(delay, log);
//        }
//        return out;
//    }
//
//    private Integer getEspCodeForLeadWithRetry(String leadEmail, StringBuilder log) {
//        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
//            try {
//                Integer result = getEspCodeForLead(leadEmail);
//                if (result != null || attempt == MAX_RETRIES) {
//                    return result;
//                }
//
//                // If result is null but not the last attempt, retry with delay
//                safeSleep(INITIAL_DELAY_MS * attempt, log);
//
//            } catch (Exception e) {
//                if (attempt == MAX_RETRIES) {
//                    log.append("⚠️ Failed to get ESP code for ").append(leadEmail).append(" after ").append(MAX_RETRIES).append(" attempts\n");
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
//            Thread.currentThread().interrupt();
//        }
//    }
//
//    // ---------------------------------------
//    // Excel export (ONLY requested columns)
//    // ---------------------------------------
//    private void writeExcel(String fromDate, String toDate, List<EmailRow> rows, StringBuilder log) {
//        // Count ESP buckets (1=Google, 2=Microsoft, else Others)
//        int google = 0, microsoft = 0, others = 0;
//        for (EmailRow r : rows) {
//            Integer c = r.espCode;
//            if (c == null) { others++; continue; }
//            if (c == CODE_GOOGLE) google++;
//            else if (c == CODE_MICROSOFT) microsoft++;
//            else others++;
//        }
//
//        log.append("\n📈 ESP Breakdown:\n");
//        log.append("Google: ").append(google).append("\n");
//        log.append("Microsoft: ").append(microsoft).append("\n");
//        log.append("Others: ").append(others).append("\n");
//        log.append("Total Emails: ").append(rows.size()).append("\n\n");
//
//        try (Workbook workbook = new XSSFWorkbook()) {
//            createDailyReportSheet(workbook, fromDate, toDate, rows.size(), google, microsoft, others);
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
//    private void createDailyReportSheet(Workbook workbook, String fromDate, String toDate,
//                                        int totalReplies, int googleCount, int microsoftCount, int othersCount) {
//        Sheet sheet = workbook.createSheet("Daily ESP Report");
//
//        CellStyle header = headerStyle(workbook, IndexedColors.LIGHT_BLUE.getIndex());
//        CellStyle data = borderStyle(workbook);
//
//        Row h = sheet.createRow(0);
//        String[] cols = {"Date", "Total No. of Replies", "Google", "Microsoft", "Others"};
//        for (int i = 0; i < cols.length; i++) {
//            Cell c = h.createCell(i);
//            c.setCellValue(cols[i]);
//            c.setCellStyle(header);
//        }
//
//        Row r = sheet.createRow(1);
//        r.createCell(0).setCellValue(fromDate.equals(toDate) ? fromDate : (fromDate + " to " + toDate));
//        r.createCell(1).setCellValue(totalReplies);
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
//        Sheet sheet = workbook.createSheet("Email Data");
//
//        CellStyle header = headerStyle(workbook, IndexedColors.ORANGE.getIndex());
//        CellStyle data = wrapBorderStyle(workbook);
//
//        String[] headers = {
//                "Lead Email", "Subject", "Content Preview", "From Address",
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
//            set(r, 0, er.leadEmail, data);
//            set(r, 1, er.subject, data);
//            set(r, 2, er.contentPreview, data);
//            set(r, 3, er.fromAddress, data);
//            set(r, 4, er.formattedDateIST, data);
//            set(r, 5, er.timestampUTC, data);
//            set(r, 6, (er.espCode == null) ? "N/A" : String.valueOf(er.espCode), data);
//            set(r, 7, er.messageId, data);
//            set(r, 8, er.threadId, data);
//        }
//
//        sheet.setColumnWidth(0, 6000);
//        sheet.setColumnWidth(1, 9000);
//        sheet.setColumnWidth(2, 12000);
//        sheet.setColumnWidth(3, 7000);
//        sheet.setColumnWidth(4, 5000);
//        sheet.setColumnWidth(5, 8000);
//        sheet.setColumnWidth(6, 3000);
//        sheet.setColumnWidth(7, 8000);
//        sheet.setColumnWidth(8, 6000);
//    }
//
//    // ---- Helper methods (unchanged) ----
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
//    public File getLatestExcelFile() {
//        File file = new File(EXCEL_FILE_PATH);
//        return file.exists() ? file : null;
//    }
//}
// real backup latest one
//package com.LeadAnalysis.ESPAnalysis.service;
//
//import com.LeadAnalysis.ESPAnalysis.config.API;
//import io.restassured.RestAssured;
//import io.restassured.response.Response;
//import io.restassured.specification.RequestSpecification;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.apache.poi.ss.usermodel.*;
//        import org.apache.poi.xssf.usermodel.XSSFWorkbook;
//import org.springframework.stereotype.Service;
//
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.time.*;
//        import java.time.format.DateTimeFormatter;
//import java.util.*;
//
///**
// * Enhanced ESP Analyzer with Time Window strategy and Other Replies support.
// * Exports two sheets:
// *  1) Daily ESP Report: Date | Total No. of Replies | Google | Microsoft | Others | Other Replies
// *  2) Email Data: Lead Email | Subject | Content Preview | From Address | Formatted Date | Timestamp | ESP Code | Message ID | Thread ID | Reply Type
// */
//@Service
//public class EspAnalyzerService {
//
//    // ---- CONFIG ----
//    private static final String BASE_URL = API.BASE_URL;
//    private static final String API_KEY = API.API_KEY;
//    private static final String EXCEL_FILE_PATH = System.getProperty("java.io.tmpdir") + "daily_email_esp_report.xlsx";
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
//    // Reply types
//    private static final String REPLY_TYPE_PRIMARY = "Primary";
//    private static final String REPLY_TYPE_OTHER = "Other";
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
//        String contentPreview;
//        String fromAddress;
//        String formattedDateIST;
//        String timestampUTC;
//        Integer espCode;
//        String messageId;
//        String threadId;
//        String replyType; // "Primary" or "Other"
//    }
//
//    public String analyzeESPByDateRange(String fromDateStr, String toDateStr) {
//        try {
//            StringBuilder log = new StringBuilder();
//            log.append("🎯 Target date range: ").append(fromDateStr).append(" to ").append(toDateStr).append("\n");
//            log.append("🚀 Starting Enhanced ESP Analysis with Time Window strategy and Other Replies...\n\n");
//
//            LocalDate fromDate = LocalDate.parse(fromDateStr, DATE_FORMATTER);
//            LocalDate toDate = LocalDate.parse(toDateStr, DATE_FORMATTER);
//
//            // 1) Fetch PRIMARY emails (mode=emode_focused) with time window
//            log.append("📧 Phase 1: Fetching PRIMARY replies (mode=emode_focused)...\n");
//            List<EmailRow> primaryRows = fetchEmailsByTimeWindow(fromDate, toDate, "emode_focused", REPLY_TYPE_PRIMARY, log);
//
//            // 2) Fetch OTHER emails (mode=emode_others) with time window
//            log.append("\n📧 Phase 2: Fetching OTHER replies (mode=emode_others)...\n");
//            List<EmailRow> otherRows = fetchEmailsByTimeWindow(fromDate, toDate, "emode_others", REPLY_TYPE_OTHER, log);
//
//            // 3) Combine both lists
//            List<EmailRow> allRows = new ArrayList<>();
//            allRows.addAll(primaryRows);
//            allRows.addAll(otherRows);
//
//            log.append("\n📊 Summary:\n");
//            log.append("Primary replies found: ").append(primaryRows.size()).append("\n");
//            log.append("Other replies found: ").append(otherRows.size()).append("\n");
//            log.append("Total replies: ").append(allRows.size()).append("\n\n");
//
//            if (allRows.isEmpty()) {
//                log.append("❌ No emails found in the given date range.\n");
//                writeExcel(fromDateStr, toDateStr, allRows, 0, log);
//                return log.toString();
//            }
//
//            // 4) For each unique lead in PRIMARY emails only, fetch esp_code from API
//            Map<String, Integer> espByLead = fetchEspCodesWithRateLimit(primaryRows, log);
//
//            // Apply ESP codes to all rows (both primary and other)
//            for (EmailRow r : allRows) {
//                r.espCode = espByLead.get(r.leadEmail);
//            }
//
//            // 5) Export with separate counts
//            writeExcel(fromDateStr, toDateStr, allRows, otherRows.size(), log);
//            log.append("✅ Excel report generated successfully!\n");
//            log.append("📁 File ready for download.\n");
//
//            return log.toString();
//
//        } catch (Exception e) {
//            return "❌ Analysis failed: " + e.getMessage();
//        }
//    }
//
//    private List<EmailRow> fetchEmailsByTimeWindow(LocalDate fromDateIST, LocalDate toDateIST,
//                                                   String mode, String replyType, StringBuilder log) {
//        List<EmailRow> allEmails = new ArrayList<>();
//        Set<String> seenEmailIds = new HashSet<>(); // Deduplicate
//
//        // Create time windows - fetch more than needed to handle unsorted data
//        LocalDate windowStart = fromDateIST.minusDays(2); // Start 2 days earlier
//
//        // For current date, don't add buffer to end date
//        LocalDate today = LocalDate.now();
//        LocalDate windowEnd;
//        if (toDateIST.equals(today)) {
//            windowEnd = toDateIST; // No buffer for current date
//            log.append("🕐 Current date detected, using exact end date\n");
//        } else {
//            windowEnd = toDateIST.plusDays(2); // Add 2 days buffer for past dates
//        }
//
//        log.append("🕐 Using time-window strategy for ").append(replyType).append(" replies (").append(mode).append("): fetching ")
//                .append(windowStart).append(" to ").append(windowEnd)
//                .append(" to ensure complete coverage of ").append(fromDateIST).append(" to ").append(toDateIST).append("\n");
//
//        String pageTrail = null;
//        int batch = 1;
//        boolean foundAnyInWindow = false;
//
//        try {
//            while (true) {
//                log.append("📡 ").append(replyType).append(" batch ").append(batch);
//                if (pageTrail != null) {
//                    log.append(" | page_trail: ").append(pageTrail.substring(0, Math.min(15, pageTrail.length()))).append("...");
//                }
//                log.append("\n");
//
//                Response response = fetchEmailsBatchWithRetry(pageTrail, mode, log);
//                if (response == null || response.getStatusCode() != 200) {
//                    log.append("❌ Failed to fetch ").append(replyType).append(" emails. Stopping.\n");
//                    break;
//                }
//
//                ObjectMapper mapper = new ObjectMapper();
//                JsonNode root = mapper.readTree(response.getBody().asString());
//                JsonNode data = root.get("data");
//
//                if (data == null || !data.isArray() || data.size() == 0) {
//                    log.append("🏁 No more ").append(replyType).append(" data.\n");
//                    break;
//                }
//
//                boolean foundInWindowThisBatch = false;
//                LocalDate oldestInBatch = null;
//                int addedFromBatch = 0;
//
//                for (JsonNode email : data) {
//                    String id = asText(email, "id");
//                    if (id != null) {
//                        pageTrail = id;
//
//                        // Skip if we've already processed this email
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
//                    if (oldestInBatch == null || istDay.isBefore(oldestInBatch)) {
//                        oldestInBatch = istDay;
//                    }
//
//                    // Check if email is in extended window
//                    if (!istDay.isBefore(windowStart) && !istDay.isAfter(windowEnd)) {
//                        foundInWindowThisBatch = true;
//                        foundAnyInWindow = true;
//
//                        // Only add if it's in the actual target range
//                        if (!istDay.isBefore(fromDateIST) && !istDay.isAfter(toDateIST)) {
//                            String lead = asText(email, "lead");
//                            if (lead != null && !lead.isBlank()) {
//                                EmailRow r = new EmailRow();
//                                r.leadEmail = lead.trim();
//                                r.subject = nz(asText(email, "subject"));
//                                r.contentPreview = nz(asText(email, "content_preview"));
//                                r.fromAddress = nz(asText(email, "from_address_email"));
//                                r.formattedDateIST = istPretty;
//                                r.timestampUTC = ts;
//                                r.messageId = nz(asText(email, "message_id"));
//                                r.threadId = nz(asText(email, "thread_id"));
//                                r.replyType = replyType;
//                                allEmails.add(r);
//                                addedFromBatch++;
//                            }
//                        }
//                    }
//                }
//
//                log.append("✅ ").append(replyType).append(" batch ").append(batch).append(": ")
//                        .append(foundInWindowThisBatch ? "found window data" : "no window data")
//                        .append(", added=").append(addedFromBatch)
//                        .append(", oldest=").append(oldestInBatch)
//                        .append(", total ").append(replyType.toLowerCase()).append(" emails=").append(allEmails.size()).append("\n");
//
//                // Stop if we've gone well past our extended window
//                if (oldestInBatch != null && oldestInBatch.isBefore(windowStart.minusDays(5))) {
//                    log.append("⏹️ Reached emails well before extended window for ").append(replyType).append(". Stopping.\n");
//                    break;
//                }
//
//                if (pageTrail == null || data.size() < EMAIL_LIMIT_PER_REQUEST) {
//                    log.append("🏁 Reached end of ").append(replyType).append(" data.\n");
//                    break;
//                }
//
//                batch++;
//                safeSleep(BATCH_DELAY_MS, log);
//            }
//
//        } catch (Exception e) {
//            log.append("❌ Error in ").append(replyType).append(" time-window fetch: ").append(e.getMessage()).append("\n");
//        }
//
//        log.append("📊 ").append(replyType).append(" time-window fetch complete: ").append(allEmails.size())
//                .append(" emails found in target range ").append(fromDateIST).append(" to ").append(toDateIST).append("\n");
//
//        return allEmails;
//    }
//
//    private Response fetchEmailsBatchWithRetry(String pageTrail, String mode, StringBuilder log) {
//        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
//            try {
//                Response response = fetchEmailsBatch(pageTrail, mode);
//
//                if (response.getStatusCode() == 429) {
//                    log.append("⚠️ Rate limit hit (429) on attempt ").append(attempt).append("/").append(MAX_RETRIES).append("\n");
//                    if (attempt < MAX_RETRIES) {
//                        long delay = RATE_LIMIT_DELAY_MS * attempt;
//                        log.append("⏳ Waiting ").append(delay / 1000).append(" seconds before retry...\n");
//                        safeSleep(delay, log);
//                        continue;
//                    }
//                } else if (response.getStatusCode() >= 500) {
//                    log.append("⚠️ Server error (").append(response.getStatusCode()).append(") on attempt ").append(attempt).append("/").append(MAX_RETRIES).append("\n");
//                    if (attempt < MAX_RETRIES) {
//                        safeSleep(INITIAL_DELAY_MS * attempt, log);
//                        continue;
//                    }
//                }
//
//                return response;
//
//            } catch (Exception e) {
//                log.append("⚠️ Network error on attempt ").append(attempt).append(": ").append(e.getMessage()).append("\n");
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
//                + "&preview_only=true&mode=" + mode + "&latest_of_thread=true";
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
//    private Map<String, Integer> fetchEspCodesWithRateLimit(List<EmailRow> primaryRows, StringBuilder log) {
//        Map<String, Integer> out = new HashMap<>();
//        Set<String> unique = new HashSet<>();
//        for (EmailRow r : primaryRows) {
//            if (r.leadEmail != null && !r.leadEmail.isBlank()) unique.add(r.leadEmail);
//        }
//
//        log.append("👤 Getting ESP data for ").append(unique.size()).append(" unique leads from primary replies...\n");
//
//        int i = 0, total = unique.size();
//        int consecutiveFailures = 0;
//
//        for (String lead : unique) {
//            i++;
//            if (i % 5 == 0 || i == total) {
//                log.append("🔎 ESP lookup progress: ").append(i).append("/").append(total).append("\n");
//            }
//
//            Integer code = getEspCodeForLeadWithRetry(lead, log);
//            out.put(lead, code);
//
//            if (code == null) {
//                consecutiveFailures++;
//                if (consecutiveFailures >= 5) {
//                    log.append("⚠️ Multiple ESP lookup failures. Increasing delay...\n");
//                    safeSleep(ESP_LOOKUP_DELAY_MS * 2, log);
//                    consecutiveFailures = 0;
//                }
//            } else {
//                consecutiveFailures = 0;
//            }
//
//            long delay = ESP_LOOKUP_DELAY_MS + (i > 50 ? (i / 50) * 100 : 0);
//            safeSleep(delay, log);
//        }
//        return out;
//    }
//
//    private Integer getEspCodeForLeadWithRetry(String leadEmail, StringBuilder log) {
//        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
//            try {
//                Integer result = getEspCodeForLead(leadEmail);
//                if (result != null || attempt == MAX_RETRIES) {
//                    return result;
//                }
//
//                safeSleep(INITIAL_DELAY_MS * attempt, log);
//
//            } catch (Exception e) {
//                if (attempt == MAX_RETRIES) {
//                    log.append("⚠️ Failed to get ESP code for ").append(leadEmail).append(" after ").append(MAX_RETRIES).append(" attempts\n");
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
//            Thread.currentThread().interrupt();
//        }
//    }
//
//    // ---------------------------------------
//    // Excel export with Other Replies column
//    // ---------------------------------------
//    private void writeExcel(String fromDate, String toDate, List<EmailRow> rows, int otherRepliesCount, StringBuilder log) {
//        // Count ESP buckets for PRIMARY emails only
//        int google = 0, microsoft = 0, others = 0, totalPrimary = 0;
//        for (EmailRow r : rows) {
//            if (REPLY_TYPE_PRIMARY.equals(r.replyType)) {
//                totalPrimary++;
//                Integer c = r.espCode;
//                if (c == null) {
//                    others++;
//                    continue;
//                }
//                if (c == CODE_GOOGLE) google++;
//                else if (c == CODE_MICROSOFT) microsoft++;
//                else others++;
//            }
//        }
//
//        log.append("\n📈 ESP Breakdown (Primary Replies Only):\n");
//        log.append("Google: ").append(google).append("\n");
//        log.append("Microsoft: ").append(microsoft).append("\n");
//        log.append("Others: ").append(others).append("\n");
//        log.append("Total Primary Replies: ").append(totalPrimary).append("\n");
//        log.append("Total Other Replies: ").append(otherRepliesCount).append("\n");
//        log.append("Grand Total Replies: ").append(totalPrimary + otherRepliesCount).append("\n\n");
//
//        try (Workbook workbook = new XSSFWorkbook()) {
//            createDailyReportSheet(workbook, fromDate, toDate, totalPrimary, google, microsoft, others, otherRepliesCount);
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
//    private void createDailyReportSheet(Workbook workbook, String fromDate, String toDate,
//                                        int totalPrimaryReplies, int googleCount, int microsoftCount,
//                                        int othersCount, int otherRepliesCount) {
//        Sheet sheet = workbook.createSheet("Daily ESP Report");
//
//        CellStyle header = headerStyle(workbook, IndexedColors.LIGHT_BLUE.getIndex());
//        CellStyle data = borderStyle(workbook);
//
//        Row h = sheet.createRow(0);
//        String[] cols = {"Date", "Total No. of Replies", "Google", "Microsoft", "Others", "Other Replies"};
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
//        r.createCell(5).setCellValue(otherRepliesCount);
//
//        for (int i = 0; i <= 5; i++) {
//            r.getCell(i).setCellStyle(data);
//            sheet.autoSizeColumn(i);
//            if (sheet.getColumnWidth(i) < 4000) sheet.setColumnWidth(i, 4000);
//        }
//    }
//
//    private void createEmailDataSheet(Workbook workbook, List<EmailRow> rows) {
//        Sheet sheet = workbook.createSheet("Email Data");
//
//        CellStyle header = headerStyle(workbook, IndexedColors.ORANGE.getIndex());
//        CellStyle data = wrapBorderStyle(workbook);
//
//        String[] headers = {
//                "Lead Email", "Subject", "Content Preview", "From Address",
//                "Formatted Date", "Timestamp", "ESP Code", "Message ID", "Thread ID", "Reply Type"
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
//            set(r, 6, (er.espCode == null) ? "N/A" : String.valueOf(er.espCode), data);
//            set(r, 7, er.messageId, data);
//            set(r, 8, er.threadId, data);
//            set(r, 9, er.replyType, data);
//        }
//
//        sheet.setColumnWidth(0, 6000);
//        sheet.setColumnWidth(1, 9000);
//        sheet.setColumnWidth(2, 12000);
//        sheet.setColumnWidth(3, 7000);
//        sheet.setColumnWidth(4, 5000);
//        sheet.setColumnWidth(5, 8000);
//        sheet.setColumnWidth(6, 3000);
//        sheet.setColumnWidth(7, 8000);
//        sheet.setColumnWidth(8, 6000);
//        sheet.setColumnWidth(9, 4000);
//    }
//
//    // ---- Helper methods ----
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
//    public File getLatestExcelFile() {
//        File file = new File(EXCEL_FILE_PATH);
//        return file.exists() ? file : null;
//    }
//}