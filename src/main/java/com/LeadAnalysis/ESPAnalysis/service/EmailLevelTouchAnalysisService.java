//package com.LeadAnalysis.ESPAnalysis.service;
//
//import com.LeadAnalysis.ESPAnalysis.config.API;
//import io.restassured.RestAssured;
//import io.restassured.response.Response;
//import io.restassured.specification.RequestSpecification;
//import io.restassured.config.HttpClientConfig;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.apache.poi.ss.usermodel.*;
//import org.apache.poi.xssf.usermodel.XSSFWorkbook;
//import org.springframework.stereotype.Service;
//
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.time.LocalDate;
//import java.time.format.DateTimeFormatter;
//import java.util.*;
//
///**
// * Email Level Touch Analysis Service
// * Analyzes email sent counts by custom tags using account and campaign analytics APIs
// */
//@Service
//public class EmailLevelTouchAnalysisService {
//
//    // ---- CONFIG ----
//    private static final String BASE_URL = API.BASE_URL;
//    private static final String API_KEY = API.API_KEY;
//    private static final String ANALYTICS_BASE_URL = API.ANALYTICS_BASE_URL;
//    private static final String EXCEL_FILE_PATH = System.getProperty("java.io.tmpdir") + "email_touch_analysis_report.xlsx";
//
//    // Date formats
//    private static final DateTimeFormatter INPUT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
//    private static final DateTimeFormatter API_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
//
//    // Data classes
//    private static class CustomTag {
//        String id;
//        String label;
//        String description;
//
//        CustomTag(String id, String label, String description) {
//            this.id = id;
//            this.label = label;
//            this.description = description;
//        }
//    }
//
//    private static class EmailAccount {
//        String email;
//        Integer dailyLimit;
//        Integer healthScore;
//        Integer sentCount;
//
//        EmailAccount(String email, Integer dailyLimit, Integer healthScore) {
//            this.email = email;
//            this.dailyLimit = dailyLimit;
//            this.healthScore = healthScore;
//            this.sentCount = 0; // Initialize with 0
//        }
//    }
//
//    /**
//     * Main analysis method
//     */
//    public String analyzeEmailTouchByTagAndDate(String tagSearch, String dateStr) {
//        try {
//            StringBuilder log = new StringBuilder();
//            log.append("Email Level Touch Analysis\n");
//            log.append("Tag Search: ").append(tagSearch).append("\n");
//            log.append("Date: ").append(dateStr).append("\n\n");
//
//            LocalDate analysisDate = LocalDate.parse(dateStr, INPUT_DATE_FORMATTER);
//            String apiDateStr = analysisDate.format(API_DATE_FORMATTER) + "T00:00:00.000Z";
//
//            log.append("API Date Format: ").append(apiDateStr).append("\n\n");
//
//            // Step 1: Get custom tags
//            List<CustomTag> tags = fetchCustomTags(tagSearch, log);
//            if (tags.isEmpty()) {
//                log.append("No custom tags found matching: ").append(tagSearch).append("\n");
//                return log.toString();
//            }
//
//            // Step 2: Get accounts for each tag
//            Map<String, EmailAccount> emailAccounts = new HashMap<>();
//            for (CustomTag tag : tags) {
//                log.append("Processing tag: ").append(tag.label).append(" (").append(tag.id).append(")\n");
//                List<EmailAccount> tagAccounts = fetchAccountsByTag(tag.id, log);
//                for (EmailAccount account : tagAccounts) {
//                    emailAccounts.put(account.email, account);
//                }
//                log.append("Found ").append(tagAccounts.size()).append(" accounts for tag ").append(tag.label).append("\n");
//            }
//
//            log.append("\nTotal unique email accounts: ").append(emailAccounts.size()).append("\n\n");
//
//            if (emailAccounts.isEmpty()) {
//                log.append("No email accounts found for the specified tags.\n");
//                return log.toString();
//            }
//
//            // Step 3: Get campaign analytics for the specified date
//            log.append("Fetching campaign analytics for date: ").append(apiDateStr).append("\n");
//            analyzeCampaignData(emailAccounts, apiDateStr, log);
//
//            // Step 4: Generate Excel report
//            generateExcelReport(emailAccounts, dateStr, tagSearch, log);
//
//            log.append("\nEmail Level Touch Analysis completed successfully!\n");
//            log.append("Excel report ready for download.\n");
//
//            return log.toString();
//
//        } catch (Exception e) {
//            return "Email Level Touch Analysis failed: " + e.getMessage() + "\nStack trace: " + Arrays.toString(e.getStackTrace());
//        }
//    }
//
//    /**
//     * Fetch custom tags based on search term
//     */
//    private List<CustomTag> fetchCustomTags(String search, StringBuilder log) {
//        List<CustomTag> tags = new ArrayList<>();
//        String[] listOfTags = search.split(",");
//        Set<String> setOfTags = new HashSet<>(Arrays.asList(listOfTags));
//        try {
//            String endpoint = "/backend-alt/api/v1/custom-tag?limit=50&skip=0";
//
//            RequestSpecification req = RestAssured.given()
//                    .baseUri(BASE_URL)
//                    .header("X-org-auth", API_KEY)
//                    .header("Content-Type", "application/json")
//                    .relaxedHTTPSValidation();
//
//            Response response = req.when().get(endpoint).then().extract().response();
//
//            if (response.getStatusCode() != 200) {
//                log.append("Custom tags API failed with status: ").append(response.getStatusCode()).append("\n");
//                return tags;
//            }
//
//            ObjectMapper mapper = new ObjectMapper();
//            JsonNode root = mapper.readTree(response.getBody().asString());
//            JsonNode data = root.get("data");
//
//            if (data != null && data.isArray()){
//                for (JsonNode tagNode : data) {
//                    String id = getTextValue(tagNode, "id");
//                    String label = getTextValue(tagNode, "label");
//                    String description = getTextValue(tagNode, "description");
//
//                    if (id != null && label != null && setOfTags.contains(label)) {
//                        tags.add(new CustomTag(id, label, description));
//                        log.append("Found tag: ").append(label).append(" (ID: ").append(id).append(")\n");
//                    }
//                }
//            }
//
//        } catch (Exception e) {
//            log.append("Error fetching custom tags: ").append(e.getMessage()).append("\n");
//        }
//
//        return tags;
//    }
//
//    /**
//     * Fetch accounts by tag ID with pagination
//     */
//    private List<EmailAccount> fetchAccountsByTag(String tagId, StringBuilder log) {
//        List<EmailAccount> accounts = new ArrayList<>();
//        int skip = 0;
//        int limit = 1000;
//        boolean hasMore = true;
//
//        try {
//            while (hasMore) {
//                String requestBody = "{\n" +
//                        "    \"search\": \"\",\n" +
//                        "    \"limit\": " + limit + ",\n" +
//                        "    \"filter\": {\n" +
//                        "        \"tag_id\": \"" + tagId + "\"\n" +
//                        "    },\n" +
//                        "    \"skip\": " + skip + ",\n" +
//                        "    \"include_tags\": true,\n" +
//                        "    \"sort_options\": null\n" +
//                        "}";
//
//                RequestSpecification req = RestAssured.given()
//                        .baseUri(BASE_URL)
//                        .header("X-org-auth", API_KEY)
//                        .header("Content-Type", "application/json")
//                        .body(requestBody)
//                        .relaxedHTTPSValidation();
//
//                Response response = req.when().post("/backend-alt/api/v1/account/list").then().extract().response();
//
//                if (response.getStatusCode() != 200) {
//                    log.append("Account list API failed with status: ").append(response.getStatusCode()).append("\n");
//                    break;
//                }
//
//                ObjectMapper mapper = new ObjectMapper();
//                JsonNode root = mapper.readTree(response.getBody().asString());
//                JsonNode accountsArray = root.get("accounts");
//
//                if (accountsArray != null && accountsArray.isArray()) {
//                    int currentBatchSize = accountsArray.size();
//
//                    for (JsonNode accountNode : accountsArray) {
//                        String email = getTextValue(accountNode, "email");
//
//                        if (email != null) {
//                            // Get daily limit and health score from payload
//                            Integer dailyLimit = null;
//                            Integer healthScore = getIntValue(accountNode, "stat_warmup_score");
//
//                            JsonNode payload = accountNode.get("payload");
//                            if (payload != null) {
//                                String dailyLimitStr = getTextValue(payload, "daily_limit");
//                                if (dailyLimitStr != null && !dailyLimitStr.isEmpty()) {
//                                    try {
//                                        dailyLimit = Integer.parseInt(dailyLimitStr);
//                                    } catch (NumberFormatException e) {
//                                        // Keep dailyLimit as null if parsing fails
//                                    }
//                                }
//                            }
//
//                            accounts.add(new EmailAccount(email, dailyLimit, healthScore));
//                        }
//                    }
//
//                    // Check if we need to fetch more
//                    if (currentBatchSize < limit) {
//                        hasMore = false;
//                    } else {
//                        skip += limit;
//                    }
//                } else {
//                    hasMore = false;
//                }
//            }
//
//        } catch (Exception e) {
//            log.append("Error fetching accounts for tag ").append(tagId).append(": ").append(e.getMessage()).append("\n");
//        }
//
//        return accounts;
//    }
//
//    /**
//     * Analyze campaign data for sent emails
//     */
//    private void analyzeCampaignData(Map<String, EmailAccount> emailAccounts, String dateStr, StringBuilder log) {
//        try {
//            log.append("Preparing campaign analytics request for large dataset...\n");
//
//            String requestBody = "{\n" +
//                    "    \"time_period\": {\n" +
//                    "        \"custom_analytics_range\": true,\n" +
//                    "        \"end_analytics_range\": \"" + dateStr + "\",\n" +
//                    "        \"start_analytics_range\": \"" + dateStr + "\"\n" +
//                    "    }\n" +
//                    "}";
//
//            log.append("Making campaign analytics API call (may take 10-15 seconds)...\n");
//
//            RequestSpecification req = RestAssured.given()
//                    .baseUri(ANALYTICS_BASE_URL)
//                    .header("X-org-auth", API_KEY)
//                    .header("Content-Type", "application/json")
//                    .body(requestBody)
//                    .relaxedHTTPSValidation()
//                    .config(RestAssured.config()
//                            .httpClient(HttpClientConfig.httpClientConfig()
//                                    .setParam("http.connection.timeout", 30000)
//                                    .setParam("http.socket.timeout", 30000)));
//
//            Response response = req.when().post("/api/v1/analytics/get_campaign_analytics").then().extract().response();
//
//            if (response.getStatusCode() != 200) {
//                log.append("Campaign analytics API failed with status: ").append(response.getStatusCode()).append("\n");
//                return;
//            }
//
//            log.append("Campaign analytics response received, parsing data...\n");
//
//            ObjectMapper mapper = new ObjectMapper();
//            JsonNode root = mapper.readTree(response.getBody().asString());
//
//            // Handle the new response structure: {"campaign_data_mongo": {"data": [...]}}
//            JsonNode campaignDataMongo = root.get("campaign_data_mongo");
//            if (campaignDataMongo == null) {
//                log.append("campaign_data_mongo not found in response\n");
//                return;
//            }
//
//            JsonNode dataArray = campaignDataMongo.get("data");
//            if (dataArray == null || !dataArray.isArray()) {
//                log.append("data array not found in campaign_data_mongo\n");
//                return;
//            }
//
//            log.append("Processing ").append(dataArray.size()).append(" campaigns...\n");
//
//            // Process each campaign in the response
//            int processedCampaigns = 0;
//            for (JsonNode campaign : dataArray) {
//                processCampaignForDate(campaign, dateStr, emailAccounts, log);
//                processedCampaigns++;
//
//                // Log progress every 10 campaigns
//                if (processedCampaigns % 10 == 0) {
//                    log.append("Processed ").append(processedCampaigns).append("/").append(dataArray.size()).append(" campaigns\n");
//                }
//            }
//
//            log.append("Campaign data analysis complete. Processed ").append(processedCampaigns).append(" campaigns.\n");
//
//        } catch (Exception e) {
//            log.append("Error analyzing campaign data: ").append(e.getMessage()).append("\n");
//            e.printStackTrace();
//        }
//    }
//
//    /**
//     * Process individual campaign data for the specified date
//     */
//    private void processCampaignForDate(JsonNode campaign, String dateStr, Map<String, EmailAccount> emailAccounts, StringBuilder log) {
//        try {
//            // Extract date from dateStr (format: yyyy-MM-ddT00:00:00.000Z)
//            String dateKey = dateStr.substring(0, 10); // Get yyyy-MM-dd part
//
//            JsonNode dateNode = campaign.get(dateKey);
//            if (dateNode == null) return;
//
//            JsonNode sentNode = dateNode.get("sent");
//            if (sentNode == null) return;
//
//            // Process all subsequence levels (0_3_0, 0_4_0, 0_5_0, etc.)
//            Iterator<String> fieldNames = sentNode.fieldNames();
//            while (fieldNames.hasNext()) {
//                String subsequence = fieldNames.next();
//                JsonNode subsequenceNode = sentNode.get(subsequence);
//
//                if (subsequenceNode != null) {
//                    Iterator<String> emailFields = subsequenceNode.fieldNames();
//                    while (emailFields.hasNext()) {
//                        String encodedEmail = emailFields.next();
//                        // Decode email: rupesh@frugal%2Ecom -> rupesh@frugal.com
//                        String decodedEmail = decodeEmail(encodedEmail);
//
//                        if (emailAccounts.containsKey(decodedEmail)) {
//                            int sentCount = subsequenceNode.get(encodedEmail).asInt();
//                            EmailAccount account = emailAccounts.get(decodedEmail);
//                            account.sentCount += sentCount;
//
//                            log.append("Updated ").append(decodedEmail).append(": +").append(sentCount)
//                                    .append(" (total: ").append(account.sentCount).append(")\n");
//                        }
//                    }
//                }
//            }
//
//        } catch (Exception e) {
//            log.append("Error processing campaign data: ").append(e.getMessage()).append("\n");
//        }
//    }
//
//    /**
//     * Decode email address (replace %2E with .)
//     */
//    private String decodeEmail(String encodedEmail) {
//        return encodedEmail.replace("%2E", ".").replace("%40", "@");
//    }
//
//    /**
//     * Generate Excel report
//     */
//    private void generateExcelReport(Map<String, EmailAccount> emailAccounts, String dateStr, String tagSearch, StringBuilder log) {
//        try (Workbook workbook = new XSSFWorkbook()) {
//            createEmailTouchSheet(workbook, emailAccounts, dateStr, tagSearch);
//
//            try (FileOutputStream fileOut = new FileOutputStream(EXCEL_FILE_PATH)) {
//                workbook.write(fileOut);
//            }
//
//            log.append("Excel file saved: ").append(EXCEL_FILE_PATH).append("\n");
//
//        } catch (IOException e) {
//            log.append("Error writing Excel file: ").append(e.getMessage()).append("\n");
//            throw new RuntimeException("Error creating Excel file", e);
//        }
//    }
//
//    /**
//     * Create the email touch analysis sheet
//     */
//    private void createEmailTouchSheet(Workbook workbook, Map<String, EmailAccount> emailAccounts, String dateStr, String tagSearch) {
//        Sheet sheet = workbook.createSheet("Email Touch Analysis");
//
//        // Create styles
//        CellStyle headerStyle = createHeaderStyle(workbook);
//        CellStyle dataStyle = createDataStyle(workbook);
//
//        // Create title row
//        Row titleRow = sheet.createRow(0);
//        Cell titleCell = titleRow.createCell(0);
//        titleCell.setCellValue("Email Level Touch Analysis - Tag: " + tagSearch + " | Date: " + dateStr);
//        titleCell.setCellStyle(headerStyle);
//
//        // Create header row
//        Row headerRow = sheet.createRow(2);
//        String[] headers = {"Email", "Sent (" + dateStr + ")", "Total Daily Limit", "Health Score"};
//        for (int i = 0; i < headers.length; i++) {
//            Cell cell = headerRow.createCell(i);
//            cell.setCellValue(headers[i]);
//            cell.setCellStyle(headerStyle);
//        }
//
//        // Fill data rows
//        int rowIndex = 3;
//        int totalSent = 0;
//
//        // Sort emails alphabetically for better readability
//        List<String> sortedEmails = new ArrayList<>(emailAccounts.keySet());
//        sortedEmails.sort(String::compareTo);
//
//        for (String email : sortedEmails) {
//            EmailAccount account = emailAccounts.get(email);
//            Row dataRow = sheet.createRow(rowIndex++);
//
//            dataRow.createCell(0).setCellValue(account.email);
//            dataRow.createCell(1).setCellValue(account.sentCount);
//            dataRow.createCell(2).setCellValue(account.dailyLimit != null ? account.dailyLimit : 0);
//            dataRow.createCell(3).setCellValue(account.healthScore != null ? account.healthScore : 0);
//
//            // Apply styles
//            for (int i = 0; i < 4; i++) {
//                dataRow.getCell(i).setCellStyle(dataStyle);
//            }
//
//            totalSent += account.sentCount;
//        }
//
//        // Add summary row
//        Row summaryRow = sheet.createRow(rowIndex + 1);
//        Cell summaryLabelCell = summaryRow.createCell(0);
//        summaryLabelCell.setCellValue("TOTAL SENT");
//        summaryLabelCell.setCellStyle(headerStyle);
//
//        Cell summaryValueCell = summaryRow.createCell(1);
//        summaryValueCell.setCellValue(totalSent);
//        summaryValueCell.setCellStyle(headerStyle);
//
//        // Auto-size columns
//        sheet.setColumnWidth(0, 8000); // Email column - wider
//        for (int i = 1; i < 4; i++) {
//            sheet.autoSizeColumn(i);
//            if (sheet.getColumnWidth(i) < 3500) {
//                sheet.setColumnWidth(i, 3500);
//            }
//        }
//
//        // Merge title cell across columns
//        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 3));
//    }
//
//    /**
//     * Helper methods for JSON parsing
//     */
//    private String getTextValue(JsonNode node, String fieldName) {
//        JsonNode field = node.get(fieldName);
//        return (field != null && !field.isNull()) ? field.asText() : null;
//    }
//
//    private int getIntValue(JsonNode node, String fieldName) {
//        JsonNode field = node.get(fieldName);
//        return (field != null && !field.isNull()) ? field.asInt() : 0;
//    }
//
//    /**
//     * Style creation methods
//     */
//    private CellStyle createHeaderStyle(Workbook workbook) {
//        CellStyle style = workbook.createCellStyle();
//        Font font = workbook.createFont();
//        font.setBold(true);
//        font.setFontHeightInPoints((short) 11);
//        style.setFont(font);
//        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
//        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
//        addBorders(style);
//        return style;
//    }
//
//    private CellStyle createDataStyle(Workbook workbook) {
//        CellStyle style = workbook.createCellStyle();
//        addBorders(style);
//        return style;
//    }
//
//    private void addBorders(CellStyle style) {
//        style.setBorderBottom(BorderStyle.THIN);
//        style.setBorderTop(BorderStyle.THIN);
//        style.setBorderLeft(BorderStyle.THIN);
//        style.setBorderRight(BorderStyle.THIN);
//    }
//
//    /**
//     * Get the latest generated Excel file
//     */
//    public File getLatestEmailTouchExcelFile() {
//        File file = new File(EXCEL_FILE_PATH);
//        return file.exists() ? file : null;
//    }
//}

package com.LeadAnalysis.ESPAnalysis.service;

import com.LeadAnalysis.ESPAnalysis.config.API;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.restassured.config.HttpClientConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Email Level Touch Analysis Service
 * Analyzes email sent counts by custom tags using account and campaign analytics APIs
 */
@Service
public class EmailLevelTouchAnalysisService {
    public static class SimpleCustomTag {
        public String id;
        public String label;
        public String description; // Include description if available

        public SimpleCustomTag(String id, String label, String description) {
            this.id = id;
            this.label = label;
            this.description = description;
        }
    }

    // ---- CONFIG ----
    private static final String BASE_URL = API.BASE_URL;
    private static final String API_KEY = API.API_KEY;
    private static final String ANALYTICS_BASE_URL = API.ANALYTICS_BASE_URL;
    private static final String EXCEL_FILE_PATH = System.getProperty("java.io.tmpdir") + "email_touch_analysis_report.xlsx";

    // Date formats
    private static final DateTimeFormatter INPUT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter API_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Data classes
    private static class CustomTag {
        String id;
        String label;
        String description;

        CustomTag(String id, String label, String description) {
            this.id = id;
            this.label = label;
            this.description = description;
        }
    }

    private static class EmailAccount {
        String email;
        Integer dailyLimit;
        Integer healthScore;
        Integer sentCount;

        EmailAccount(String email, Integer dailyLimit, Integer healthScore) {
            this.email = email;
            this.dailyLimit = dailyLimit;
            this.healthScore = healthScore;
            this.sentCount = 0; // Initialize with 0
        }
    }

    private static class TagAnalysisResult {
        CustomTag tag;
        Map<String, EmailAccount> emailAccounts;

        TagAnalysisResult(CustomTag tag, Map<String, EmailAccount> emailAccounts) {
            this.tag = tag;
            this.emailAccounts = emailAccounts;
        }
    }

    public List<SimpleCustomTag> fetchAllCustomTags() {
        List<SimpleCustomTag> allTags = new ArrayList<>();
        int skip = 0;
        int limit = 20; // Use 20 as per the API's default limit parameter in your URL
        boolean hasMore = true;

        try {
            while (hasMore) {
                String endpoint = "/backend-alt/api/v1/custom-tag?limit=" + limit + "&skip=" + skip;

                RequestSpecification req = RestAssured.given()
                        .baseUri(BASE_URL)
                        .header("X-org-auth", API_KEY)
                        .header("Content-Type", "application/json")
                        .relaxedHTTPSValidation();

                Response response = req.when().get(endpoint).then().extract().response();

                if (response.getStatusCode() != 200) {
                    // Log the failure to the backend console
                    System.err.println("External Tag API failed: Status " + response.getStatusCode());
                    return allTags; // Return what we have or an empty list
                }

                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.getBody().asString());
                JsonNode data = root.get("data");

                if (data != null && data.isArray()){
                    int currentBatchSize = data.size();

                    for (JsonNode tagNode : data) {
                        String id = getTextValue(tagNode, "id");
                        String label = getTextValue(tagNode, "label");
                        String description = getTextValue(tagNode, "description");

                        if (id != null && label != null) {
                            allTags.add(new SimpleCustomTag(id, label, description));
                        }
                    }

                    if (currentBatchSize < limit) {
                        hasMore = false;
                    } else {
                        skip += limit;
                    }
                } else {
                    hasMore = false; // No data or invalid response structure
                }
            }
        } catch (Exception e) {
            System.err.println("Error during tag fetching (backend): " + e.getMessage());
            e.printStackTrace();
        }

        return allTags;
    }

    /**
     * Main analysis method
     */
    public String analyzeEmailTouchByTagAndDate(String tagSearch, String dateStr) {
        try {
            StringBuilder log = new StringBuilder();
            log.append("Email Level Touch Analysis\n");
            log.append("Tag Search: ").append(tagSearch).append("\n");
            log.append("Date: ").append(dateStr).append("\n\n");

            LocalDate analysisDate = LocalDate.parse(dateStr, INPUT_DATE_FORMATTER);
            String apiDateStr = analysisDate.format(API_DATE_FORMATTER) + "T00:00:00.000Z";

            log.append("API Date Format: ").append(apiDateStr).append("\n\n");

            // Step 1: Get custom tags
            List<CustomTag> tags = fetchCustomTags(tagSearch, log);
            if (tags.isEmpty()) {
                log.append("No custom tags found matching: ").append(tagSearch).append("\n");
                return log.toString();
            }

            // Step 2: Process each tag separately to create individual sheets
            List<TagAnalysisResult> tagResults = new ArrayList<>();

            for (CustomTag tag : tags) {
                log.append("\n=== Processing tag: ").append(tag.label).append(" (").append(tag.id).append(") ===\n");

                // Get accounts for this specific tag
                Map<String, EmailAccount> tagEmailAccounts = new HashMap<>();
                List<EmailAccount> tagAccounts = fetchAccountsByTag(tag.id, log);

                for (EmailAccount account : tagAccounts) {
                    tagEmailAccounts.put(account.email, account);
                }

                log.append("Found ").append(tagAccounts.size()).append(" accounts for tag ").append(tag.label).append("\n");

                if (!tagEmailAccounts.isEmpty()) {
                    // Step 3: Get campaign analytics for this tag's accounts
                    log.append("Fetching campaign analytics for tag ").append(tag.label).append(" on date: ").append(apiDateStr).append("\n");
                    analyzeCampaignDataForTag(tagEmailAccounts, apiDateStr, tag.label, log);

                    // Store the result for this tag
                    tagResults.add(new TagAnalysisResult(tag, tagEmailAccounts));
                }
            }

            if (tagResults.isEmpty()) {
                log.append("No email accounts found for any of the specified tags.\n");
                return log.toString();
            }

            // Step 4: Generate Excel report with separate sheets for each tag
            generateMultiTagExcelReport(tagResults, dateStr, tagSearch, log);

            log.append("\nEmail Level Touch Analysis completed successfully!\n");
            log.append("Excel report with separate sheets for each tag is ready for download.\n");

            return log.toString();

        } catch (Exception e) {
            return "Email Level Touch Analysis failed: " + e.getMessage() + "\nStack trace: " + Arrays.toString(e.getStackTrace());
        }
    }

    /**
     * Fetch custom tags based on search term
     */
    private List<CustomTag> fetchCustomTags(String search, StringBuilder log) {
        List<CustomTag> tags = new ArrayList<>();
        String[] listOfTags = search.split(",");
        Set<String> setOfTags = new HashSet<>();

        // Trim whitespace from tag names
        for (String tag : listOfTags) {
            setOfTags.add(tag.trim());
        }

        try {
            String endpoint = "/backend-alt/api/v1/custom-tag?limit=50&skip=0";

            RequestSpecification req = RestAssured.given()
                    .baseUri(BASE_URL)
                    .header("X-org-auth", API_KEY)
                    .header("Content-Type", "application/json")
                    .relaxedHTTPSValidation();

            Response response = req.when().get(endpoint).then().extract().response();

            if (response.getStatusCode() != 200) {
                log.append("Custom tags API failed with status: ").append(response.getStatusCode()).append("\n");
                return tags;
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.getBody().asString());
            JsonNode data = root.get("data");

            if (data != null && data.isArray()){
                for (JsonNode tagNode : data) {
                    String id = getTextValue(tagNode, "id");
                    String label = getTextValue(tagNode, "label");
                    String description = getTextValue(tagNode, "description");

                    if (id != null && label != null && setOfTags.contains(label)) {
                        tags.add(new CustomTag(id, label, description));
                        log.append("Found tag: ").append(label).append(" (ID: ").append(id).append(")\n");
                    }
                }
            }

        } catch (Exception e) {
            log.append("Error fetching custom tags: ").append(e.getMessage()).append("\n");
        }

        return tags;
    }

    /**
     * Fetch accounts by tag ID with pagination
     */
    private List<EmailAccount> fetchAccountsByTag(String tagId, StringBuilder log) {
        List<EmailAccount> accounts = new ArrayList<>();
        int skip = 0;
        int limit = 1000;
        boolean hasMore = true;

        try {
            while (hasMore) {
                String requestBody = "{\n" +
                        "    \"search\": \"\",\n" +
                        "    \"limit\": " + limit + ",\n" +
                        "    \"filter\": {\n" +
                        "        \"tag_id\": \"" + tagId + "\"\n" +
                        "    },\n" +
                        "    \"skip\": " + skip + ",\n" +
                        "    \"include_tags\": true,\n" +
                        "    \"sort_options\": null\n" +
                        "}";

                RequestSpecification req = RestAssured.given()
                        .baseUri(BASE_URL)
                        .header("X-org-auth", API_KEY)
                        .header("Content-Type", "application/json")
                        .body(requestBody)
                        .relaxedHTTPSValidation();

                Response response = req.when().post("/backend-alt/api/v1/account/list").then().extract().response();

                if (response.getStatusCode() != 200) {
                    log.append("Account list API failed with status: ").append(response.getStatusCode()).append("\n");
                    break;
                }

                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.getBody().asString());
                JsonNode accountsArray = root.get("accounts");

                if (accountsArray != null && accountsArray.isArray()) {
                    int currentBatchSize = accountsArray.size();

                    for (JsonNode accountNode : accountsArray) {
                        String email = getTextValue(accountNode, "email");

                        if (email != null) {
                            // Get daily limit and health score from payload
                            Integer dailyLimit = null;
                            Integer healthScore = getIntValue(accountNode, "stat_warmup_score");

                            JsonNode payload = accountNode.get("payload");
                            if (payload != null) {
                                String dailyLimitStr = getTextValue(payload, "daily_limit");
                                if (dailyLimitStr != null && !dailyLimitStr.isEmpty()) {
                                    try {
                                        dailyLimit = Integer.parseInt(dailyLimitStr);
                                    } catch (NumberFormatException e) {
                                        // Keep dailyLimit as null if parsing fails
                                    }
                                }
                            }

                            accounts.add(new EmailAccount(email, dailyLimit, healthScore));
                        }
                    }

                    // Check if we need to fetch more
                    if (currentBatchSize < limit) {
                        hasMore = false;
                    } else {
                        skip += limit;
                    }
                } else {
                    hasMore = false;
                }
            }

        } catch (Exception e) {
            log.append("Error fetching accounts for tag ").append(tagId).append(": ").append(e.getMessage()).append("\n");
        }

        return accounts;
    }

    /**
     * Analyze campaign data for sent emails for a specific tag
     */
    private void analyzeCampaignDataForTag(Map<String, EmailAccount> emailAccounts, String dateStr, String tagLabel, StringBuilder log) {
        try {
            log.append("Preparing campaign analytics request for tag: ").append(tagLabel).append("...\n");

            String requestBody = "{\n" +
                    "    \"time_period\": {\n" +
                    "        \"custom_analytics_range\": true,\n" +
                    "        \"end_analytics_range\": \"" + dateStr + "\",\n" +
                    "        \"start_analytics_range\": \"" + dateStr + "\"\n" +
                    "    }\n" +
                    "}";

            log.append("Making campaign analytics API call for tag ").append(tagLabel).append(" (may take 10-15 seconds)...\n");

            RequestSpecification req = RestAssured.given()
                    .baseUri(ANALYTICS_BASE_URL)
                    .header("X-org-auth", API_KEY)
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .relaxedHTTPSValidation()
                    .config(RestAssured.config()
                            .httpClient(HttpClientConfig.httpClientConfig()
                                    .setParam("http.connection.timeout", 30000)
                                    .setParam("http.socket.timeout", 30000)));

            Response response = req.when().post("/api/v1/analytics/get_campaign_analytics").then().extract().response();

            if (response.getStatusCode() != 200) {
                log.append("Campaign analytics API failed with status: ").append(response.getStatusCode()).append(" for tag: ").append(tagLabel).append("\n");
                return;
            }

            log.append("Campaign analytics response received for tag ").append(tagLabel).append(", parsing data...\n");

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.getBody().asString());

            // Handle the new response structure: {"campaign_data_mongo": {"data": [...]}}
            JsonNode campaignDataMongo = root.get("campaign_data_mongo");
            if (campaignDataMongo == null) {
                log.append("campaign_data_mongo not found in response for tag: ").append(tagLabel).append("\n");
                return;
            }

            JsonNode dataArray = campaignDataMongo.get("data");
            if (dataArray == null || !dataArray.isArray()) {
                log.append("data array not found in campaign_data_mongo for tag: ").append(tagLabel).append("\n");
                return;
            }

            log.append("Processing ").append(dataArray.size()).append(" campaigns for tag ").append(tagLabel).append("...\n");

            // Process each campaign in the response
            int processedCampaigns = 0;
            for (JsonNode campaign : dataArray) {
                processCampaignForDate(campaign, dateStr, emailAccounts, log);
                processedCampaigns++;

                // Log progress every 10 campaigns
                if (processedCampaigns % 10 == 0) {
                    log.append("Processed ").append(processedCampaigns).append("/").append(dataArray.size()).append(" campaigns for tag ").append(tagLabel).append("\n");
                }
            }

            log.append("Campaign data analysis complete for tag ").append(tagLabel).append(". Processed ").append(processedCampaigns).append(" campaigns.\n");

        } catch (Exception e) {
            log.append("Error analyzing campaign data for tag ").append(tagLabel).append(": ").append(e.getMessage()).append("\n");
            e.printStackTrace();
        }
    }

    /**
     * Process individual campaign data for the specified date
     */
    private void processCampaignForDate(JsonNode campaign, String dateStr, Map<String, EmailAccount> emailAccounts, StringBuilder log) {
        try {
            // Extract date from dateStr (format: yyyy-MM-ddT00:00:00.000Z)
            String dateKey = dateStr.substring(0, 10); // Get yyyy-MM-dd part

            JsonNode dateNode = campaign.get(dateKey);
            if (dateNode == null) return;

            JsonNode sentNode = dateNode.get("sent");
            if (sentNode == null) return;

            // Process all subsequence levels (0_3_0, 0_4_0, 0_5_0, etc.)
            Iterator<String> fieldNames = sentNode.fieldNames();
            while (fieldNames.hasNext()) {
                String subsequence = fieldNames.next();
                JsonNode subsequenceNode = sentNode.get(subsequence);

                if (subsequenceNode != null) {
                    Iterator<String> emailFields = subsequenceNode.fieldNames();
                    while (emailFields.hasNext()) {
                        String encodedEmail = emailFields.next();
                        // Decode email: rupesh@frugal%2Ecom -> rupesh@frugal.com
                        String decodedEmail = decodeEmail(encodedEmail);

                        if (emailAccounts.containsKey(decodedEmail)) {
                            int sentCount = subsequenceNode.get(encodedEmail).asInt();
                            EmailAccount account = emailAccounts.get(decodedEmail);
                            account.sentCount += sentCount;

                            log.append("Updated ").append(decodedEmail).append(": +").append(sentCount)
                                    .append(" (total: ").append(account.sentCount).append(")\n");
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.append("Error processing campaign data: ").append(e.getMessage()).append("\n");
        }
    }

    /**
     * Decode email address (replace %2E with .)
     */
    private String decodeEmail(String encodedEmail) {
        return encodedEmail.replace("%2E", ".").replace("%40", "@");
    }

    /**
     * Generate Excel report with separate sheets for each tag
     */
    private void generateMultiTagExcelReport(List<TagAnalysisResult> tagResults, String dateStr, String tagSearch, StringBuilder log) {
        try (Workbook workbook = new XSSFWorkbook()) {

            // Create a summary sheet first
            createSummarySheet(workbook, tagResults, dateStr, tagSearch);

            // Create individual sheets for each tag
            for (TagAnalysisResult result : tagResults) {
                String sheetName = sanitizeSheetName(result.tag.label + "_Emails");
                createEmailTouchSheet(workbook, result.emailAccounts, dateStr, result.tag.label, sheetName);
                log.append("Created sheet: ").append(sheetName).append(" with ").append(result.emailAccounts.size()).append(" accounts\n");
            }

            try (FileOutputStream fileOut = new FileOutputStream(EXCEL_FILE_PATH)) {
                workbook.write(fileOut);
            }

            log.append("Excel file saved: ").append(EXCEL_FILE_PATH).append("\n");

        } catch (IOException e) {
            log.append("Error writing Excel file: ").append(e.getMessage()).append("\n");
            throw new RuntimeException("Error creating Excel file", e);
        }
    }

    /**
     * Create summary sheet with overview of all tags
     */
    private void createSummarySheet(Workbook workbook, List<TagAnalysisResult> tagResults, String dateStr, String tagSearch) {
        Sheet sheet = workbook.createSheet("Summary");

        // Create styles
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);

        // Create title row
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Email Touch Analysis Summary - Tags: " + tagSearch + " | Date: " + dateStr);
        titleCell.setCellStyle(headerStyle);

        // Create header row
        Row headerRow = sheet.createRow(2);
        String[] headers = {"Tag Name", "Total Accounts", "Total Emails Sent", "Average Sent per Account"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Fill data rows
        int rowIndex = 3;
        int grandTotalAccounts = 0;
        int grandTotalSent = 0;

        for (TagAnalysisResult result : tagResults) {
            Row dataRow = sheet.createRow(rowIndex++);

            int totalSent = result.emailAccounts.values().stream()
                    .mapToInt(account -> account.sentCount)
                    .sum();

            double averageSent = result.emailAccounts.size() > 0 ?
                    (double) totalSent / result.emailAccounts.size() : 0;

            dataRow.createCell(0).setCellValue(result.tag.label);
            dataRow.createCell(1).setCellValue(result.emailAccounts.size());
            dataRow.createCell(2).setCellValue(totalSent);
            dataRow.createCell(3).setCellValue(String.format("%.2f", averageSent));

            // Apply styles
            for (int i = 0; i < 4; i++) {
                dataRow.getCell(i).setCellStyle(dataStyle);
            }

            grandTotalAccounts += result.emailAccounts.size();
            grandTotalSent += totalSent;
        }

        // Add grand total row
        Row totalRow = sheet.createRow(rowIndex + 1);
        totalRow.createCell(0).setCellValue("GRAND TOTAL");
        totalRow.createCell(1).setCellValue(grandTotalAccounts);
        totalRow.createCell(2).setCellValue(grandTotalSent);
        double grandAverage = grandTotalAccounts > 0 ? (double) grandTotalSent / grandTotalAccounts : 0;
        totalRow.createCell(3).setCellValue(String.format("%.2f", grandAverage));

        // Apply header style to total row
        for (int i = 0; i < 4; i++) {
            totalRow.getCell(i).setCellStyle(headerStyle);
        }

        // Auto-size columns
        for (int i = 0; i < 4; i++) {
            sheet.autoSizeColumn(i);
            if (sheet.getColumnWidth(i) < 4000) {
                sheet.setColumnWidth(i, 4000);
            }
        }

        // Merge title cell across columns
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 3));
    }

    /**
     * Create the email touch analysis sheet for a specific tag
     */
    private void createEmailTouchSheet(Workbook workbook, Map<String, EmailAccount> emailAccounts,
                                       String dateStr, String tagLabel, String sheetName) {
        Sheet sheet = workbook.createSheet(sheetName);

        // Create styles
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);

        // Create title row
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Email Touch Analysis - Tag: " + tagLabel + " | Date: " + dateStr);
        titleCell.setCellStyle(headerStyle);

        // Create header row
        Row headerRow = sheet.createRow(2);
        String[] headers = {"Email", "Sent (" + dateStr + ")", "Total Daily Limit", "Health Score"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Fill data rows
        int rowIndex = 3;
        int totalSent = 0;

        // Sort emails alphabetically for better readability
        List<String> sortedEmails = new ArrayList<>(emailAccounts.keySet());
        sortedEmails.sort(String::compareTo);

        for (String email : sortedEmails) {
            EmailAccount account = emailAccounts.get(email);
            Row dataRow = sheet.createRow(rowIndex++);

            dataRow.createCell(0).setCellValue(account.email);
            dataRow.createCell(1).setCellValue(account.sentCount);
            dataRow.createCell(2).setCellValue(account.dailyLimit != null ? account.dailyLimit : 0);
            dataRow.createCell(3).setCellValue(account.healthScore != null ? account.healthScore : 0);

            // Apply styles
            for (int i = 0; i < 4; i++) {
                dataRow.getCell(i).setCellStyle(dataStyle);
            }

            totalSent += account.sentCount;
        }

        // Add summary row
        Row summaryRow = sheet.createRow(rowIndex + 1);
        Cell summaryLabelCell = summaryRow.createCell(0);
        summaryLabelCell.setCellValue("TOTAL SENT");
        summaryLabelCell.setCellStyle(headerStyle);

        Cell summaryValueCell = summaryRow.createCell(1);
        summaryValueCell.setCellValue(totalSent);
        summaryValueCell.setCellStyle(headerStyle);

        // Auto-size columns
        sheet.setColumnWidth(0, 8000); // Email column - wider
        for (int i = 1; i < 4; i++) {
            sheet.autoSizeColumn(i);
            if (sheet.getColumnWidth(i) < 3500) {
                sheet.setColumnWidth(i, 3500);
            }
        }

        // Merge title cell across columns
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 3));
    }

    /**
     * Sanitize sheet name to be valid for Excel
     */
    private String sanitizeSheetName(String sheetName) {
        // Excel sheet names cannot be longer than 31 characters and cannot contain certain characters
        String sanitized = sheetName.replaceAll("[\\[\\]\\*\\?/\\\\:]", "_");
        if (sanitized.length() > 31) {
            sanitized = sanitized.substring(0, 28) + "...";
        }
        return sanitized;
    }

    /**
     * Helper methods for JSON parsing
     */
    private String getTextValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return (field != null && !field.isNull()) ? field.asText() : null;
    }

    private int getIntValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return (field != null && !field.isNull()) ? field.asInt() : 0;
    }

    /**
     * Style creation methods
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        addBorders(style);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        addBorders(style);
        return style;
    }

    private void addBorders(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    /**
     * Get the latest generated Excel file
     */
    public File getLatestEmailTouchExcelFile() {
        File file = new File(EXCEL_FILE_PATH);
        return file.exists() ? file : null;
    }
}