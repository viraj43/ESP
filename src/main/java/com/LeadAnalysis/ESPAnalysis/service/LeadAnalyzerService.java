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
import java.util.*;

@Service
public class LeadAnalyzerService {

    // ---- CONFIG: set your own values ----
    private static final String BASE_URL = API.BASE_URL;
    private static final String API_KEY = API.API_KEY;
    private static final int LIMIT_PER_REQUEST = 1000;
    private static final String LEAD_EXCEL_FILE_PATH = System.getProperty("java.io.tmpdir") + "lead_esp_analysis_report.xlsx";

    // ESP Code to Name mapping
    private static final Map<Integer, String> ESP_CODE_MAPPING = new HashMap<>();

    static {
        ESP_CODE_MAPPING.put(1, "Google");
        ESP_CODE_MAPPING.put(2, "Microsoft");
        ESP_CODE_MAPPING.put(3, "Zoho");
        ESP_CODE_MAPPING.put(9, "Yahoo");
        ESP_CODE_MAPPING.put(10, "Yandex");
    }

    // Helper class for batch results
    private static class BatchResult {
        Map<String, Integer> espCounts;
        int totalLeadsInBatch;
        int leadsWithReplies;
        int actualReplies;
        int autoReplies;
        int noReplies;
        String lastItemId;

        BatchResult(Map<String, Integer> espCounts, int totalLeadsInBatch, int leadsWithReplies,
                    int actualReplies, int autoReplies, int noReplies, String lastItemId) {
            this.espCounts = espCounts;
            this.totalLeadsInBatch = totalLeadsInBatch;
            this.leadsWithReplies = leadsWithReplies;
            this.actualReplies = actualReplies;
            this.autoReplies = autoReplies;
            this.noReplies = noReplies;
            this.lastItemId = lastItemId;
        }
    }

    public String analyzeLeadESP(int totalRecordsNeeded) {
        try {
            StringBuilder log = new StringBuilder();
            log.append("🚀 Starting Lead ESP Analysis for ").append(totalRecordsNeeded).append(" records...\n");

            int maxApiCalls = (int) Math.ceil((double) totalRecordsNeeded / LIMIT_PER_REQUEST);
            log.append("📊 Will make up to ").append(maxApiCalls).append(" API calls with ").append(LIMIT_PER_REQUEST).append(" records each\n\n");

            Map<String, Integer> espCounts = new HashMap<>();
            Map<Integer, List<String>> unmappedEspCodes = new HashMap<>();
            Map<Integer, List<String>> notFoundEspCodes = new HashMap<>();
            List<String> actualRepliesEmails = new ArrayList<>();
            List<String> autoRepliesEmails = new ArrayList<>();
            List<String> noRepliesEmails = new ArrayList<>();
            String pageTrail = null;
            int totalLeadsProcessed = 0;
            int totalLeadsWithReplies = 0;
            int totalActualReplies = 0;
            int totalAutoReplies = 0;
            int totalNoReplies = 0;

            // Make API calls until we reach the target or no more data
            for (int callNumber = 1; callNumber <= maxApiCalls; callNumber++) {
                log.append("📡 API Call ").append(callNumber).append("/").append(maxApiCalls);
                if (pageTrail != null) {
                    log.append(" | page_trail: ").append(pageTrail.substring(0, Math.min(20, pageTrail.length()))).append("...");
                }
                log.append("\n");

                Response response = fetchLeadsWithReplies(pageTrail);

                if (response.getStatusCode() != 200) {
                    log.append("❌ API call failed with status: ").append(response.getStatusCode()).append("\n");
                    break;
                }

                BatchResult result = parseAndCountESPsBatch(response.getBody().asString(), callNumber,
                        unmappedEspCodes, notFoundEspCodes, actualRepliesEmails, autoRepliesEmails, noRepliesEmails, log);

                // Merge counts from this batch
                for (Map.Entry<String, Integer> entry : result.espCounts.entrySet()) {
                    espCounts.put(entry.getKey(), espCounts.getOrDefault(entry.getKey(), 0) + entry.getValue());
                }

                totalLeadsProcessed += result.totalLeadsInBatch;
                totalLeadsWithReplies += result.leadsWithReplies;
                totalActualReplies += result.actualReplies;
                totalAutoReplies += result.autoReplies;
                totalNoReplies += result.noReplies;

                pageTrail = result.lastItemId;

                log.append("✅ Call ").append(callNumber).append(" complete | Batch: ").append(result.totalLeadsInBatch)
                        .append(" leads, ").append(result.leadsWithReplies).append(" with replies (")
                        .append(result.actualReplies).append(" actual, ").append(result.autoReplies).append(" auto), ")
                        .append(result.noReplies).append(" no replies | Total so far: ").append(totalLeadsProcessed).append("\n");

                // Break if we've reached our target or no more data
                if (totalLeadsProcessed >= totalRecordsNeeded || pageTrail == null || result.totalLeadsInBatch < LIMIT_PER_REQUEST) {
                    if (totalLeadsProcessed >= totalRecordsNeeded) {
                        log.append("🎯 Target reached! Stopping at ").append(totalLeadsProcessed).append(" leads\n");
                    } else {
                        log.append("🏁 No more data available. Stopping at call ").append(callNumber).append("\n");
                    }
                    break;
                }

                Thread.sleep(500);
            }

            // Generate final report
            String reportResult = generateLeadESPReport(espCounts, totalLeadsProcessed, totalLeadsWithReplies,
                    totalActualReplies, totalAutoReplies, totalNoReplies, unmappedEspCodes, notFoundEspCodes,
                    actualRepliesEmails, autoRepliesEmails, noRepliesEmails);

            log.append(reportResult);
            log.append("✅ Lead analysis completed successfully!\n");
            log.append("📁 Excel report ready for download.\n");

            return log.toString();

        } catch (Exception e) {
            return "❌ Lead analysis failed: " + e.getMessage();
        }
    }

    private Response fetchLeadsWithReplies(String pageTrail) {
        String requestBody = "{\n" +
                "  \"limit\": " + LIMIT_PER_REQUEST + ",\n" +
                "  \"page_trail\": " + (pageTrail != null ? "\"" + pageTrail + "\"" : "null") + ",\n" +
                "  \"with_campaign_name\": true,\n" +
                "  \"with_list_name\": true,\n" +
                "  \"assigned_to\": null,\n" +
                "  \"is_website_visitor\": false,\n" +
                "  \"queries\": []\n" +
                "}";

        RequestSpecification request = RestAssured.given()
                .baseUri(BASE_URL)
                .header("X-org-auth", API_KEY)
                .header("Content-Type", "application/json")
                .body(requestBody);

        return request.when()
                .post("/backend-alt/api/v1/lead/list")
                .then()
                .extract()
                .response();
    }

    private BatchResult parseAndCountESPsBatch(String responseBody, int callNumber,
                                               Map<Integer, List<String>> unmappedEspCodes, Map<Integer, List<String>> notFoundEspCodes,
                                               List<String> actualRepliesEmails, List<String> autoRepliesEmails, List<String> noRepliesEmails,
                                               StringBuilder log) throws Exception {

        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(responseBody);
        JsonNode itemsNode = rootNode.get("items");

        Map<String, Integer> espCounts = new HashMap<>();
        int leadsWithReplies = 0;
        int actualReplies = 0;
        int autoReplies = 0;
        int noReplies = 0;
        String lastItemId = null;

        if (itemsNode != null && itemsNode.isArray()) {
            int batchSize = itemsNode.size();
            log.append("📊 Batch ").append(callNumber).append(": ").append(batchSize).append(" leads received\n");

            Iterator<JsonNode> items = itemsNode.elements();
            while (items.hasNext()) {
                JsonNode lead = items.next();

                JsonNode idNode = lead.get("id");
                if (idNode != null) {
                    lastItemId = idNode.asText();
                }

                // Get email from contact field
                String email = "N/A";
                JsonNode contactNode = lead.get("contact");
                if (contactNode != null && !contactNode.isNull()) {
                    email = contactNode.asText();
                }

                // Get ESP for this email
                String espName = getESPForEmail(lead, contactNode, email, unmappedEspCodes, notFoundEspCodes);

                // Check reply status
                JsonNode timestampLastReply = lead.get("timestamp_last_reply");
                JsonNode timestampAddedSubsequence = lead.get("timestamp_added_subsequence");

                boolean hasReply = false;

                if (timestampLastReply != null && !timestampLastReply.isNull()) {
                    // ACTUAL REPLY
                    hasReply = true;
                    actualReplies++;
                    actualRepliesEmails.add(email + "|" + espName);
                } else if (timestampAddedSubsequence != null && !timestampAddedSubsequence.isNull()) {
                    // AUTO REPLY
                    hasReply = true;
                    autoReplies++;
                    autoRepliesEmails.add(email + "|" + espName);
                } else {
                    // NO REPLY
                    noReplies++;
                    noRepliesEmails.add(email + "|" + espName);
                }

                // Count ESP for leads with replies
                if (hasReply) {
                    leadsWithReplies++;
                    espCounts.put(espName, espCounts.getOrDefault(espName, 0) + 1);
                }
            }

            log.append("🔍 Batch ").append(callNumber).append(" analysis: ").append(leadsWithReplies)
                    .append(" leads with replies (").append(actualReplies).append(" actual, ")
                    .append(autoReplies).append(" auto), ").append(noReplies).append(" no replies\n");

            return new BatchResult(espCounts, batchSize, leadsWithReplies, actualReplies, autoReplies, noReplies, lastItemId);
        }

        return new BatchResult(espCounts, 0, 0, 0, 0, 0, null);
    }

    private String getESPForEmail(JsonNode lead, JsonNode contactNode, String email,
                                  Map<Integer, List<String>> unmappedEspCodes, Map<Integer, List<String>> notFoundEspCodes) {

        JsonNode espCodeNode = lead.get("esp_code");
        String espName = "Others"; // Default

        if (espCodeNode != null && !espCodeNode.isNull()) {
            int espCode = espCodeNode.asInt();

            if (ESP_CODE_MAPPING.containsKey(espCode)) {
                espName = ESP_CODE_MAPPING.get(espCode);
            } else if (espCode >= 1000) {
                espName = "Not Found";
                notFoundEspCodes.computeIfAbsent(espCode, k -> new ArrayList<>()).add(email);
            } else {
                espName = "Others";
                unmappedEspCodes.computeIfAbsent(espCode, k -> new ArrayList<>()).add(email);
            }
        } else {
            // Fallback: extract from email domain
            if (contactNode != null && !contactNode.isNull()) {
                String emailFromContact = contactNode.asText();
                espName = extractESPFromEmailDomain(emailFromContact);
            }
        }

        return espName;
    }

    private String extractESPFromEmailDomain(String email) {
        if (email == null || !email.contains("@")) {
            return "Others";
        }

        String domain = email.toLowerCase().split("@")[1];

        Map<String, String> domainToESP = new HashMap<>();
        domainToESP.put("gmail.com", "Google");
        domainToESP.put("googlemail.com", "Google");
        domainToESP.put("outlook.com", "Microsoft");
        domainToESP.put("hotmail.com", "Microsoft");
        domainToESP.put("live.com", "Microsoft");
        domainToESP.put("msn.com", "Microsoft");
        domainToESP.put("yahoo.com", "Yahoo");
        domainToESP.put("yahoo.co.uk", "Yahoo");

        return domainToESP.getOrDefault(domain, "Others");
    }

    private String generateLeadESPReport(Map<String, Integer> espCounts, int totalProcessed, int totalWithReplies,
                                         int totalActualReplies, int totalAutoReplies, int totalNoReplies,
                                         Map<Integer, List<String>> unmappedEspCodes, Map<Integer, List<String>> notFoundEspCodes,
                                         List<String> actualRepliesEmails, List<String> autoRepliesEmails, List<String> noRepliesEmails) {

        StringBuilder report = new StringBuilder();

        report.append("\n📈 LEAD ESP ANALYSIS REPORT\n");
        report.append("📊 Total leads processed: ").append(totalProcessed).append("\n");
        report.append("📧 Total leads with replies: ").append(totalWithReplies).append("\n");
        report.append("✅ Total actual replies: ").append(totalActualReplies).append("\n");
        report.append("🤖 Total auto replies: ").append(totalAutoReplies).append("\n");
        report.append("❌ Total no replies: ").append(totalNoReplies).append("\n");
        report.append("📈 Overall reply rate: ").append(String.format("%.2f%%", totalProcessed > 0 ? (totalWithReplies * 100.0) / totalProcessed : 0)).append("\n\n");

        // ESP breakdown
        report.append("ESP Breakdown:\n");
        espCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    String esp = entry.getKey();
                    int count = entry.getValue();
                    double percentage = totalWithReplies > 0 ? (count * 100.0) / totalWithReplies : 0;
                    report.append(String.format("%-15s: %5d (%6.1f%%)\n", esp, count, percentage));
                });

        // Export to Excel
        exportLeadAnalysisToExcel(actualRepliesEmails, autoRepliesEmails, noRepliesEmails);

        return report.toString();
    }

    private void exportLeadAnalysisToExcel(List<String> actualRepliesEmails, List<String> autoRepliesEmails, List<String> noRepliesEmails) {
        try (Workbook workbook = new XSSFWorkbook()) {

            // Sheet 1: Combined categorization
            createCombinedSheet(workbook, actualRepliesEmails, autoRepliesEmails, noRepliesEmails);

            // Sheet 2: Actual Replies with ESP
            createESPSheet(workbook, actualRepliesEmails, "Actual Replies with ESP", "ACTUAL REPLIES EMAILS", "ESP");

            // Sheet 3: Auto Replies with ESP
            createESPSheet(workbook, autoRepliesEmails, "Auto Replies with ESP", "AUTO REPLIES EMAILS", "ESP");

            // Sheet 4: No Replies with ESP
            createESPSheet(workbook, noRepliesEmails, "No Replies with ESP", "NO REPLIES EMAILS", "ESP");

            try (FileOutputStream fileOut = new FileOutputStream(LEAD_EXCEL_FILE_PATH)) {
                workbook.write(fileOut);
            }

        } catch (IOException e) {
            throw new RuntimeException("Error creating Lead Excel file", e);
        }
    }

    private void createCombinedSheet(Workbook workbook, List<String> actualRepliesEmails, List<String> autoRepliesEmails, List<String> noRepliesEmails) {
        Sheet sheet = workbook.createSheet("Email Categorization");

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 12);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        addBorder(headerStyle);

        CellStyle dataStyle = workbook.createCellStyle();
        addBorder(dataStyle);

        Row headerRow = sheet.createRow(0);
        String[] headers = {"ACTUAL REPLIES", "AUTO REPLIES", "NO REPLIES"};

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int maxRows = Math.max(Math.max(actualRepliesEmails.size(), autoRepliesEmails.size()), noRepliesEmails.size());

        for (int i = 0; i < maxRows; i++) {
            Row dataRow = sheet.createRow(i + 1);

            Cell actualCell = dataRow.createCell(0);
            if (i < actualRepliesEmails.size()) {
                String email = actualRepliesEmails.get(i).split("\\|")[0];
                actualCell.setCellValue(email);
            }
            actualCell.setCellStyle(dataStyle);

            Cell autoCell = dataRow.createCell(1);
            if (i < autoRepliesEmails.size()) {
                String email = autoRepliesEmails.get(i).split("\\|")[0];
                autoCell.setCellValue(email);
            }
            autoCell.setCellStyle(dataStyle);

            Cell noCell = dataRow.createCell(2);
            if (i < noRepliesEmails.size()) {
                String email = noRepliesEmails.get(i).split("\\|")[0];
                noCell.setCellValue(email);
            }
            noCell.setCellStyle(dataStyle);
        }

        for (int i = 0; i < 3; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createESPSheet(Workbook workbook, List<String> emailsWithESP, String sheetName, String emailHeader, String espHeader) {
        Sheet sheet = workbook.createSheet(sheetName);

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 12);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.ORANGE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        addBorder(headerStyle);

        CellStyle dataStyle = workbook.createCellStyle();
        addBorder(dataStyle);

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue(emailHeader);
        headerRow.createCell(1).setCellValue(espHeader);
        headerRow.getCell(0).setCellStyle(headerStyle);
        headerRow.getCell(1).setCellStyle(headerStyle);

        for (int i = 0; i < emailsWithESP.size(); i++) {
            Row dataRow = sheet.createRow(i + 1);
            String[] parts = emailsWithESP.get(i).split("\\|");
            String email = parts.length > 0 ? parts[0] : "";
            String esp = parts.length > 1 ? parts[1] : "Others";

            dataRow.createCell(0).setCellValue(email);
            dataRow.createCell(1).setCellValue(esp);
            dataRow.getCell(0).setCellStyle(dataStyle);
            dataRow.getCell(1).setCellStyle(dataStyle);
        }

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void addBorder(CellStyle cs) {
        cs.setBorderBottom(BorderStyle.THIN);
        cs.setBorderTop(BorderStyle.THIN);
        cs.setBorderLeft(BorderStyle.THIN);
        cs.setBorderRight(BorderStyle.THIN);
    }

    public File getLatestLeadExcelFile() {
        File file = new File(LEAD_EXCEL_FILE_PATH);
        return file.exists() ? file : null;
    }
}