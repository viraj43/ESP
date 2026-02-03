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
//import java.time.LocalDate;
//import java.time.format.DateTimeFormatter;
//import java.util.*;
//
///**
// * Campaign Sent Analytics Service - Analyze campaign email sent counts using Instantly API
// * Generates Excel report with campaign-level email sent breakdown
// */
//@Service
//public class CampaignSentAnalyticsService {
//
//    // ---- CONFIG ----
//    private static final String BASE_URL = API.BASE_URL;
//    private static final String API_KEY = API.API_KEY;
//    private static final String X_Workspace_Id = API.X_WorkSpace_Id;
//    private static final String Cookie = API.Cookie;
//    private static final String EXCEL_FILE_PATH = System.getProperty("java.io.tmpdir") + "campaign_sent_analytics_report.xlsx";
//
//    // Date formats
//    private static final DateTimeFormatter INPUT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
//    private static final DateTimeFormatter API_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
//
//    // Data class for campaign analytics
//    private static class CampaignAnalytics {
//        String campaignName;
//        String campaignId;
//        int campaignStatus;
//        boolean isEvergreen;
//        int leadsCount;
//        int contactedCount;
//        int openCount;
//        int replyCount;
//        int linkClickCount;
//        int bouncedCount;
//        int unsubscribedCount;
//        int completedCount;
//        int emailsSentCount;
//        int newLeadsContactedCount;
//        int totalOpportunities;
//        int totalOpportunityValue;
//
//        CampaignAnalytics() {}
//    }
//
//    /**
//     * Main method to analyze campaign sent analytics by date range
//     */
//    public String analyzeCampaignSentByDateRange(String fromDateStr, String toDateStr) {
//        try {
//            StringBuilder log = new StringBuilder();
//            log.append("Campaign Sent Analytics for date range: ").append(fromDateStr).append(" to ").append(toDateStr).append("\n");
//            log.append("Starting Campaign Sent Analytics using Instantly API...\n\n");
//
//            // Parse and validate input dates
//            LocalDate fromDate = LocalDate.parse(fromDateStr, INPUT_DATE_FORMATTER);
//            LocalDate toDate = LocalDate.parse(toDateStr, INPUT_DATE_FORMATTER);
//
//            log.append("Parsed Date Range: ").append(fromDate).append(" to ").append(toDate).append("\n\n");
//
//            // Convert to API format
//            String apiFromDate = fromDate.format(API_DATE_FORMATTER) + "T00:00:00.000Z";
//            String apiToDate = toDate.format(API_DATE_FORMATTER) + "T23:59:59.999Z";
//
//            log.append("API Date Range: ").append(apiFromDate).append(" to ").append(apiToDate).append("\n\n");
//
//            // Fetch campaign analytics data
//            List<CampaignAnalytics> campaignData = fetchCampaignAnalytics(apiFromDate, apiToDate, log);
//
//            if (campaignData.isEmpty()) {
//                log.append("No campaign data found for the specified date range.\n");
//                writeExcel(fromDateStr, toDateStr, campaignData, log);
//                return log.toString();
//            }
//
//            log.append("Analysis Summary:\n");
//            log.append("Total campaigns found: ").append(campaignData.size()).append("\n");
//
//            // Calculate totals
//            int totalEmailsSent = campaignData.stream().mapToInt(c -> c.emailsSentCount).sum();
//            log.append("Total emails sent across all campaigns: ").append(totalEmailsSent).append("\n\n");
//
//            // Generate Excel report
//            writeExcel(fromDateStr, toDateStr, campaignData, log);
//
//            log.append("Campaign Sent Analytics completed successfully!\n");
//            log.append("Excel report ready for download.\n");
//
//            return log.toString();
//
//        } catch (Exception e) {
//            return "Campaign Sent Analytics failed: " + e.getMessage() + "\nStack trace: " + Arrays.toString(e.getStackTrace());
//        }
//    }
//
//    /**
//     * Fetch campaign analytics data from Instantly API
//     */
//    private List<CampaignAnalytics> fetchCampaignAnalytics(String startDate, String endDate, StringBuilder log) {
//        List<CampaignAnalytics> campaignList = new ArrayList<>();
//
//        try {
//            log.append("Fetching campaign analytics from Instantly API...\n");
//
//            String endpoint = "/backend/api/v2/campaigns/analytics?start_date=" + startDate + "&end_date=" + endDate;
//            log.append("API Endpoint: ").append(endpoint).append("\n");
//
//            RequestSpecification req = RestAssured.given()
//                    .baseUri(BASE_URL)
//                    .header("X-Workspace-Id",X_Workspace_Id)
//                    .header("Cookie", Cookie)
//                    .header("Content-Type", "application/json")
//                    .header("Connection", "keep-alive")
//                    .relaxedHTTPSValidation();
//
//            Response response = req.when().get(endpoint).then().extract().response();
//
//            log.append("API Response Status: ").append(response.getStatusCode()).append("\n");
//
//            if (response.getStatusCode() != 200) {
//                log.append("API Error: ").append(response.getBody().asString()).append("\n");
//                return campaignList;
//            }
//
//            // Parse response
//            ObjectMapper mapper = new ObjectMapper();
//            JsonNode rootArray = mapper.readTree(response.getBody().asString());
//
//            if (!rootArray.isArray()) {
//                log.append("Invalid response format - expected array\n");
//                return campaignList;
//            }
//
//            log.append("Processing ").append(rootArray.size()).append(" campaigns...\n");
//
//            // Process each campaign
//            for (JsonNode campaignNode : rootArray) {
//                CampaignAnalytics campaign = parseCampaignNode(campaignNode);
//                if (campaign != null) {
//                    campaignList.add(campaign);
//                }
//            }
//
//            log.append("Successfully parsed ").append(campaignList.size()).append(" campaigns\n\n");
//
//        } catch (Exception e) {
//            log.append("Error fetching campaign analytics: ").append(e.getMessage()).append("\n");
//            e.printStackTrace();
//        }
//
//        return campaignList;
//    }
//
//    /**
//     * Parse individual campaign node from API response
//     */
//    private CampaignAnalytics parseCampaignNode(JsonNode campaignNode) {
//        try {
//            CampaignAnalytics campaign = new CampaignAnalytics();
//
//            campaign.campaignName = getTextValue(campaignNode, "campaign_name");
//            campaign.campaignId = getTextValue(campaignNode, "campaign_id");
//            campaign.campaignStatus = getIntValue(campaignNode, "campaign_status");
//            campaign.isEvergreen = getBooleanValue(campaignNode, "campaign_is_evergreen");
//            campaign.leadsCount = getIntValue(campaignNode, "leads_count");
//            campaign.contactedCount = getIntValue(campaignNode, "contacted_count");
//            campaign.openCount = getIntValue(campaignNode, "open_count");
//            campaign.replyCount = getIntValue(campaignNode, "reply_count");
//            campaign.linkClickCount = getIntValue(campaignNode, "link_click_count");
//            campaign.bouncedCount = getIntValue(campaignNode, "bounced_count");
//            campaign.unsubscribedCount = getIntValue(campaignNode, "unsubscribed_count");
//            campaign.completedCount = getIntValue(campaignNode, "completed_count");
//            campaign.emailsSentCount = getIntValue(campaignNode, "emails_sent_count");
//            campaign.newLeadsContactedCount = getIntValue(campaignNode, "new_leads_contacted_count");
//            campaign.totalOpportunities = getIntValue(campaignNode, "total_opportunities");
//            campaign.totalOpportunityValue = getIntValue(campaignNode, "total_opportunity_value");
//
//            return campaign;
//
//        } catch (Exception e) {
//            System.err.println("Error parsing campaign node: " + e.getMessage());
//            return null;
//        }
//    }
//
//    /**
//     * Generate Excel report with campaign sent data
//     */
//    private void writeExcel(String fromDate, String toDate, List<CampaignAnalytics> campaignData, StringBuilder log) {
//        try (Workbook workbook = new XSSFWorkbook()) {
//            createCampaignSentSheet(workbook, fromDate, toDate, campaignData);
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
//     * Create the campaign_level_sent sheet
//     */
//    private void createCampaignSentSheet(Workbook workbook, String fromDate, String toDate, List<CampaignAnalytics> campaignData) {
//        Sheet sheet = workbook.createSheet("campaign_level_sent");
//
//        // Create styles
//        CellStyle headerStyle = createHeaderStyle(workbook);
//        CellStyle dataStyle = createDataStyle(workbook);
//        CellStyle totalStyle = createTotalStyle(workbook);
//
//        // Create title row
//        Row titleRow = sheet.createRow(0);
//        Cell titleCell = titleRow.createCell(0);
//        titleCell.setCellValue("Campaign Sent Analytics (" + fromDate + " to " + toDate + ")");
//        titleCell.setCellStyle(headerStyle);
//
//        // Create header row
//        Row headerRow = sheet.createRow(2);
//        Cell headerCell1 = headerRow.createCell(0);
//        headerCell1.setCellValue("Campaign Name");
//        headerCell1.setCellStyle(headerStyle);
//
//        Cell headerCell2 = headerRow.createCell(1);
//        headerCell2.setCellValue("emails_sent_count");
//        headerCell2.setCellStyle(headerStyle);
//
//        // Fill data rows
//        int rowIndex = 3;
//        int totalEmailsSent = 0;
//
//        for (CampaignAnalytics campaign : campaignData) {
//            Row dataRow = sheet.createRow(rowIndex++);
//
//            Cell nameCell = dataRow.createCell(0);
//            nameCell.setCellValue(campaign.campaignName != null ? campaign.campaignName : "");
//            nameCell.setCellStyle(dataStyle);
//
//            Cell sentCell = dataRow.createCell(1);
//            sentCell.setCellValue(campaign.emailsSentCount);
//            sentCell.setCellStyle(dataStyle);
//
//            totalEmailsSent += campaign.emailsSentCount;
//        }
//
//        // Add total row
//        Row totalRow = sheet.createRow(rowIndex + 1);
//        Cell totalLabelCell = totalRow.createCell(0);
//        totalLabelCell.setCellValue("TOTAL");
//        totalLabelCell.setCellStyle(totalStyle);
//
//        Cell totalValueCell = totalRow.createCell(1);
//        totalValueCell.setCellValue(totalEmailsSent);
//        totalValueCell.setCellStyle(totalStyle);
//
//        // Auto-size columns
//        sheet.setColumnWidth(0, 20000); // Campaign name - wider
//        sheet.autoSizeColumn(1);
//        if (sheet.getColumnWidth(1) < 5000) {
//            sheet.setColumnWidth(1, 5000);
//        }
//
//        // Merge title cell across columns
//        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 1));
//    }
//
//    /**
//     * Helper methods for JSON parsing
//     */
//    private String getTextValue(JsonNode node, String fieldName) {
//        JsonNode field = node.get(fieldName);
//        return (field != null && !field.isNull()) ? field.asText() : "";
//    }
//
//    private int getIntValue(JsonNode node, String fieldName) {
//        JsonNode field = node.get(fieldName);
//        return (field != null && !field.isNull()) ? field.asInt() : 0;
//    }
//
//    private boolean getBooleanValue(JsonNode node, String fieldName) {
//        JsonNode field = node.get(fieldName);
//        return (field != null && !field.isNull()) ? field.asBoolean() : false;
//    }
//
//    /**
//     * Style creation methods
//     */
//    private CellStyle createHeaderStyle(Workbook workbook) {
//        CellStyle style = workbook.createCellStyle();
//        Font font = workbook.createFont();
//        font.setBold(true);
//        font.setFontHeightInPoints((short) 12);
//        style.setFont(font);
//        style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
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
//    private CellStyle createTotalStyle(Workbook workbook) {
//        CellStyle style = workbook.createCellStyle();
//        Font font = workbook.createFont();
//        font.setBold(true);
//        font.setFontHeightInPoints((short) 11);
//        style.setFont(font);
//        style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
//        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
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
//    public File getLatestCampaignSentExcelFile() {
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
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Campaign Sent Analytics Service - Enhanced to analyze campaign email sent counts by ESP
 * Generates Excel report with campaign-level email sent breakdown by Google, Microsoft, and Others
 */
@Service
public class CampaignSentAnalyticsService {

    // ---- CONFIG ----
    private static final String BASE_URL = API.BASE_URL;
    private static final String API_KEY = API.API_KEY;
    private static final String X_Workspace_Id = API.X_WorkSpace_Id;
    private static final String Cookie = API.Cookie;
    private static final String EXCEL_FILE_PATH = System.getProperty("java.io.tmpdir") + "campaign_sent_analytics_report.xlsx";

    // Date formats
    private static final DateTimeFormatter INPUT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter API_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ESP mapping constants
    private static final int ESP_GOOGLE = 1;
    private static final int ESP_MICROSOFT = 2;

    // Enhanced data class for campaign analytics with ESP breakdown
    private static class CampaignAnalytics {
        String campaignName;
        String campaignId;
        int campaignStatus;
        boolean isEvergreen;
        int leadsCount;
        int contactedCount;
        int openCount;
        int replyCount;
        int linkClickCount;
        int bouncedCount;
        int unsubscribedCount;
        int completedCount;
        int emailsSentCount;
        int newLeadsContactedCount;
        int totalOpportunities;
        int totalOpportunityValue;

        // ESP breakdown
        int googleCount = 0;
        int microsoftCount = 0;
        int othersCount = 0;

        CampaignAnalytics() {}

        void addESPCount(String espType) {
            switch (espType.toLowerCase()) {
                case "google":
                    googleCount++;
                    break;
                case "microsoft":
                    microsoftCount++;
                    break;
                default:
                    othersCount++;
                    break;
            }
        }
    }

    /**
     * Main method to analyze campaign sent analytics by date range (Enhanced with ESP breakdown)
     */
    public String analyzeCampaignSentByDateRange(String fromDateStr, String toDateStr) {
        long startTime = System.currentTimeMillis();
        System.out.println("=== STARTING ESP CAMPAIGN ANALYTICS ===");
        System.out.println("Start Time: " + new java.util.Date());
        System.out.println("Date Range: " + fromDateStr + " to " + toDateStr);

        try {
            StringBuilder log = new StringBuilder();
            log.append("Campaign Sent Analytics for date range: ").append(fromDateStr).append(" to ").append(toDateStr).append("\n");
            log.append("Starting Campaign Sent Analytics using Instantly API...\n\n");

            // Parse and validate input dates
            long parseStartTime = System.currentTimeMillis();
            System.out.println(">> Parsing input dates...");

            LocalDate fromDate = LocalDate.parse(fromDateStr, INPUT_DATE_FORMATTER);
            LocalDate toDate = LocalDate.parse(toDateStr, INPUT_DATE_FORMATTER);

            log.append("Parsed Date Range: ").append(fromDate).append(" to ").append(toDate).append("\n\n");
            System.out.println("   Date parsing completed in " + (System.currentTimeMillis() - parseStartTime) + "ms");

            // Convert to API format
            String apiFromDate = fromDate.format(API_DATE_FORMATTER) + "T00:00:00.000Z";
            String apiToDate = toDate.format(API_DATE_FORMATTER) + "T23:59:59.999Z";

            log.append("API Date Range: ").append(apiFromDate).append(" to ").append(apiToDate).append("\n\n");
            System.out.println("   API date format: " + apiFromDate + " to " + apiToDate);

            // Fetch campaign analytics data with ESP analysis
            long fetchStartTime = System.currentTimeMillis();
            System.out.println(">> Starting campaign analytics fetch...");

            List<CampaignAnalytics> campaignData = fetchCampaignAnalytics(apiFromDate, apiToDate, log);

            long fetchEndTime = System.currentTimeMillis();
            System.out.println("   Campaign analytics fetch completed in " + (fetchEndTime - fetchStartTime) + "ms");
            System.out.println("   Found " + campaignData.size() + " campaigns");

            if (campaignData.isEmpty()) {
                log.append("No campaign data found for the specified date range.\n");
                System.out.println(">> No campaigns found, generating empty Excel report...");
                writeExcel(fromDateStr, toDateStr, campaignData, log);
                System.out.println("=== ANALYSIS COMPLETED (NO DATA) ===");
                return log.toString();
            }

            log.append("Analysis Summary:\n");
            log.append("Total campaigns found: ").append(campaignData.size()).append("\n");

            // Calculate totals
            System.out.println(">> Calculating totals...");
            long totalsStartTime = System.currentTimeMillis();

            int totalEmailsSent = campaignData.stream().mapToInt(c -> c.emailsSentCount).sum();
            int totalGoogle = campaignData.stream().mapToInt(c -> c.googleCount).sum();
            int totalMicrosoft = campaignData.stream().mapToInt(c -> c.microsoftCount).sum();
            int totalOthers = campaignData.stream().mapToInt(c -> c.othersCount).sum();

            System.out.println("   Totals calculated in " + (System.currentTimeMillis() - totalsStartTime) + "ms");
            System.out.println("   Total emails sent: " + totalEmailsSent);
            System.out.println("   ESP Breakdown - Google: " + totalGoogle + ", Microsoft: " + totalMicrosoft + ", Others: " + totalOthers);

            log.append("Total emails sent across all campaigns: ").append(totalEmailsSent).append("\n");
            log.append("ESP Breakdown - Google: ").append(totalGoogle)
                    .append(", Microsoft: ").append(totalMicrosoft)
                    .append(", Others: ").append(totalOthers).append("\n\n");

            // Generate Excel report
            System.out.println(">> Generating Excel report...");
            long excelStartTime = System.currentTimeMillis();

            writeExcel(fromDateStr, toDateStr, campaignData, log);

            System.out.println("   Excel report generated in " + (System.currentTimeMillis() - excelStartTime) + "ms");

            log.append("Campaign Sent Analytics completed successfully!\n");
            log.append("Excel report ready for download.\n");

            long totalTime = System.currentTimeMillis() - startTime;
            System.out.println("=== ANALYSIS COMPLETED SUCCESSFULLY ===");
            System.out.println("Total execution time: " + totalTime + "ms (" + (totalTime/1000.0) + " seconds)");
            System.out.println("End Time: " + new java.util.Date());

            return log.toString();

        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            System.out.println("=== ANALYSIS FAILED ===");
            System.out.println("Error occurred after: " + totalTime + "ms");
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
            return "Campaign Sent Analytics failed: " + e.getMessage() + "\nStack trace: " + Arrays.toString(e.getStackTrace());
        }
    }

    /**
     * Fetch campaign analytics data from Instantly API (Enhanced with ESP analysis)
     */
    private List<CampaignAnalytics> fetchCampaignAnalytics(String startDate, String endDate, StringBuilder log) {
        long methodStartTime = System.currentTimeMillis();
        System.out.println("   >> fetchCampaignAnalytics() started");

        List<CampaignAnalytics> campaignList = new ArrayList<>();

        try {
            log.append("Fetching campaign analytics from Instantly API...\n");

            String endpoint = "/backend/api/v2/campaigns/analytics?start_date=" + startDate + "&end_date=" + endDate;
            log.append("API Endpoint: ").append(endpoint).append("\n");
            System.out.println("      API Endpoint: " + endpoint);

            // API call timing
            long apiCallStart = System.currentTimeMillis();
            System.out.println("      Making API call to campaigns/analytics...");

            RequestSpecification req = RestAssured.given()
                    .baseUri(BASE_URL)
                    .header("X-Workspace-Id",X_Workspace_Id)
                    .header("Cookie",Cookie)
                    .header("X-Org-Auth", API_KEY)
                    .header("Content-Type", "application/json")
                    .header("Connection", "keep-alive")
                    .relaxedHTTPSValidation();

            Response response = req.when().get(endpoint).then().extract().response();

            long apiCallEnd = System.currentTimeMillis();
            System.out.println("      API call completed in " + (apiCallEnd - apiCallStart) + "ms");

            log.append("API Response Status: ").append(response.getStatusCode()).append("\n");
            System.out.println("      Response Status: " + response.getStatusCode());

            if (response.getStatusCode() != 200) {
                log.append("API Error: ").append(response.getBody().asString()).append("\n");
                System.out.println("      API Error: " + response.getBody().asString());
                return campaignList;
            }

            // Parse response timing
            long parseStart = System.currentTimeMillis();
            System.out.println("      Parsing JSON response...");

            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootArray = mapper.readTree(response.getBody().asString());

            if (!rootArray.isArray()) {
                log.append("Invalid response format - expected array\n");
                System.out.println("      ERROR: Invalid response format");
                return campaignList;
            }

            System.out.println("      JSON parsed in " + (System.currentTimeMillis() - parseStart) + "ms");
            log.append("Processing ").append(rootArray.size()).append(" campaigns...\n");
           // System.out.println("      Processing " + rootArray.size() + " campaigns with ESP analysis...");

            // Process each campaign with ESP analysis
            int campaignIndex = 0;
            for (JsonNode campaignNode : rootArray) {
                long campaignStart = System.currentTimeMillis();
                campaignIndex++;

                CampaignAnalytics campaign = parseCampaignNode(campaignNode);
                if (campaign != null &&(campaign.campaignStatus !=0 && campaign.campaignStatus !=3)) {

                    System.out.println("      [" + campaignIndex + "] Processing campaign: " + campaign.campaignName);

                    // Add ESP analysis for this campaign
                    performESPAnalysis(campaign, startDate, endDate, log);
                    campaignList.add(campaign);

                    long campaignTime = System.currentTimeMillis() - campaignStart;
                    System.out.println("         Campaign processed in " + campaignTime + "ms (Google: " +
                            campaign.googleCount + ", Microsoft: " + campaign.microsoftCount +
                            ", Others: " + campaign.othersCount + ")");
                } else {
                    System.out.println("      [" + campaignIndex + "/" + rootArray.size() + "] Skipped invalid campaign");
                }
            }

            long methodTime = System.currentTimeMillis() - methodStartTime;
            log.append("Successfully parsed ").append(campaignList.size()).append(" campaigns with ESP analysis\n\n");
            System.out.println("   >> fetchCampaignAnalytics() completed in " + methodTime + "ms");

        } catch (Exception e) {
            long methodTime = System.currentTimeMillis() - methodStartTime;
            log.append("Error fetching campaign analytics: ").append(e.getMessage()).append("\n");
            System.out.println("   >> ERROR in fetchCampaignAnalytics() after " + methodTime + "ms: " + e.getMessage());
            e.printStackTrace();
        }

        return campaignList;
    }

    /**
     * Perform ESP analysis for a campaign
     */
    private void performESPAnalysis(CampaignAnalytics campaign, String startDate, String endDate, StringBuilder log) {
        long methodStart = System.currentTimeMillis();
        System.out.println("         >> performESPAnalysis() for: " + campaign.campaignName);

        try {
            log.append("Performing ESP analysis for campaign: ").append(campaign.campaignName).append("\n");

            // Step 1: Build ESP mapping for this campaign's leads
            long mappingStart = System.currentTimeMillis();
            System.out.println("            Building ESP mapping...");

            Map<String, String> espMapping = buildESPMapping(campaign.campaignId, log);

            long mappingTime = System.currentTimeMillis() - mappingStart;
            System.out.println("            ESP mapping built in " + mappingTime + "ms (" + espMapping.size() + " leads mapped)");

            // Step 2: Analyze activity data and count by ESP
            long activityStart = System.currentTimeMillis();
            System.out.println("            Analyzing activity data...");

            analyzeActivityByESP(campaign, espMapping, startDate, endDate, log);

            long activityTime = System.currentTimeMillis() - activityStart;
            System.out.println("            Activity analysis completed in " + activityTime + "ms");

            long methodTime = System.currentTimeMillis() - methodStart;
            log.append("ESP analysis complete for campaign: ").append(campaign.campaignName)
                    .append(" (Google: ").append(campaign.googleCount)
                    .append(", Microsoft: ").append(campaign.microsoftCount)
                    .append(", Others: ").append(campaign.othersCount).append(")\n");

            System.out.println("         >> performESPAnalysis() completed in " + methodTime + "ms");

        } catch (Exception e) {
            long methodTime = System.currentTimeMillis() - methodStart;
            log.append("Error in ESP analysis for campaign ").append(campaign.campaignName)
                    .append(": ").append(e.getMessage()).append("\n");
            System.out.println("         >> ERROR in performESPAnalysis() after " + methodTime + "ms: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Build ESP mapping for a campaign's leads
     */
    private Map<String, String> buildESPMapping(String campaignId, StringBuilder log) {
        long methodStart = System.currentTimeMillis();
        System.out.println("               >> buildESPMapping() started for campaign: " + campaignId);

        Map<String, String> espMapping = new HashMap<>();

        try {
            log.append("Building ESP mapping for campaign: ").append(campaignId).append("\n");

            boolean hasMoreData = true;
            String pageTrail = null;
            int totalLeads = 0;
            int pageCount = 0;

            while (hasMoreData) {
                pageCount++;
                long pageStart = System.currentTimeMillis();
                System.out.println("                  Page " + pageCount + ": Fetching leads...");

                // Prepare request payload
                Map<String, Object> payload = new HashMap<>();
                payload.put("campaign", campaignId);
                payload.put("search", "");
                payload.put("limit", 1000);
                payload.put("queries", new ArrayList<>());

                if (pageTrail != null) {
                    payload.put("page_trail", pageTrail);
                    System.out.println("                     Adding page_trail to payload: " + pageTrail);
                }

                System.out.println("                     Payload: " + payload);

                long apiStart = System.currentTimeMillis();
                RequestSpecification req = RestAssured.given()
                        .baseUri(BASE_URL)
                        .header("X-Org-Auth", API_KEY)
                        .header("Content-Type", "application/json")
                        .header("Connection", "keep-alive")
                        .body(payload)
                        .relaxedHTTPSValidation();

                Response response = req.when().post("/backend-alt/api/v1/lead/list").then().extract().response();

                long apiTime = System.currentTimeMillis() - apiStart;
                System.out.println("                     API call took " + apiTime + "ms");

                if (response.getStatusCode() != 200) {
                    log.append("Error fetching leads: ").append(response.getBody().asString()).append("\n");
                    System.out.println("                     ERROR: " + response.getStatusCode() + " - " + response.getBody().asString());
                    break;
                }

                long parseStart = System.currentTimeMillis();
                ObjectMapper mapper = new ObjectMapper();
                JsonNode responseNode = mapper.readTree(response.getBody().asString());
                JsonNode itemsArray = responseNode.get("items");

                if (itemsArray == null || !itemsArray.isArray() || itemsArray.size() == 0) {
                    System.out.println("                     No more data found");
                    break;
                }

                System.out.println("                     Processing " + itemsArray.size() + " leads...");

                // Process leads and build ESP mapping
                for (JsonNode leadNode : itemsArray) {
                    String contact = getTextValue(leadNode, "contact");
                    int espCode = getIntValue(leadNode, "esp_code");

                    String espType;
                    if (espCode == ESP_GOOGLE) {
                        espType = "Google";
                    } else if (espCode == ESP_MICROSOFT) {
                        espType = "Microsoft";
                    } else {
                        espType = "Others";
                    }

                    espMapping.put(contact, espType);
                    totalLeads++;
                }

                long parseTime = System.currentTimeMillis() - parseStart;
                long pageTime = System.currentTimeMillis() - pageStart;
                System.out.println("                     Processing took " + parseTime + "ms, page total: " + pageTime + "ms");

                // Check if we need to continue pagination
                if (itemsArray.size() < 1000) {
                    hasMoreData = false;
                    System.out.println("                     Last page reached (less than 1000 items)");
                } else {
                    // Get the ID of the last item for page_trail
                    JsonNode lastItem = itemsArray.get(itemsArray.size() - 1);
                    JsonNode idNode = lastItem.get("id");

                    if (idNode != null) {
                        pageTrail = idNode.asText(); // Get just the ID without quotes
                        System.out.println("                     Page Trail ID: " + pageTrail);
                    } else {
                        System.out.println("                     ERROR: Could not find 'id' field in last item");
                        hasMoreData = false;
                    }

                    System.out.println("                     Preparing next page...");
                }

                // Add small delay to avoid rate limiting
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }

            long methodTime = System.currentTimeMillis() - methodStart;
            log.append("Built ESP mapping for ").append(totalLeads).append(" leads\n");
            System.out.println("               >> buildESPMapping() completed in " + methodTime + "ms (" +
                    totalLeads + " leads, " + pageCount + " pages)");

        } catch (Exception e) {
            long methodTime = System.currentTimeMillis() - methodStart;
            log.append("Error building ESP mapping: ").append(e.getMessage()).append("\n");
            System.out.println("               >> ERROR in buildESPMapping() after " + methodTime + "ms: " + e.getMessage());
            e.printStackTrace();
        }

        return espMapping;
    }

    /**
     * Analyze activity data by ESP for a specific campaign
     */
    private void analyzeActivityByESP(CampaignAnalytics campaign, Map<String, String> espMapping,
                                      String startDate, String endDate, StringBuilder log) {
        long methodStart = System.currentTimeMillis();
        System.out.println("               >> analyzeActivityByESP() started for: " + campaign.campaignName);

        try {
            log.append("Analyzing activity for campaign: ").append(campaign.campaignName).append("\n");

            // Parse dates for filtering
            LocalDate fromDate = LocalDate.parse(startDate.substring(0, 10));
            LocalDate toDate = LocalDate.parse(endDate.substring(0, 10));

            boolean shouldContinue = true;
            String beforeId = null;
            boolean isFirstCall = true;
            int limit = 1000;
            int pageCount = 0;
            int totalProcessed = 0;
            int sentEmailsFound = 0;

            while (shouldContinue) {
                pageCount++;
                long pageStart = System.currentTimeMillis();
                System.out.println("                  Activity Page " + pageCount + ": Fetching activity data...");

                String endpoint = "/backend-alt/api/v1/activity/list?campaign_id=" + campaign.campaignId + "&limit=" + limit;

                if (!isFirstCall && beforeId != null) {
                    endpoint += "&before_id=" + beforeId;
                }

                long apiStart = System.currentTimeMillis();
                RequestSpecification req = RestAssured.given()
                        .baseUri(BASE_URL)
                        .header("X-Org-Auth", API_KEY)
                        .header("Content-Type", "application/json")
                        .relaxedHTTPSValidation();

                Response response = req.when().get(endpoint).then().extract().response();

                long apiTime = System.currentTimeMillis() - apiStart;
                System.out.println("                     Activity API call took " + apiTime + "ms");

                if (response.getStatusCode() != 200) {
                    log.append("Error fetching activity: ").append(response.getBody().asString()).append("\n");
                    System.out.println("                     ERROR: " + response.getStatusCode() + " - " + response.getBody().asString());
                    break;
                }

                long parseStart = System.currentTimeMillis();
                List<Map<String, Object>> activityHistory = response.jsonPath().getList("activity_history");

                if (activityHistory == null || activityHistory.isEmpty()) {
                    System.out.println("                     No activity data found");
                    break;
                }

                System.out.println("                     Processing " + activityHistory.size() + " activity records...");

                for (Map<String, Object> activity : activityHistory) {
                    String timestampStr = (String) activity.get("timestamp_created");

                    // Date validation
                    ZonedDateTime zonedDateTimeUTC = ZonedDateTime.parse(timestampStr);
                    ZonedDateTime zonedDateTimeIST = zonedDateTimeUTC.withZoneSameInstant(ZoneId.of("Asia/Kolkata"));
                    LocalDate recordDate = zonedDateTimeIST.toLocalDate();

                    if (recordDate.isBefore(fromDate)) {
                        System.out.println("                     Found record outside date range (" + recordDate + "), stopping");
                        log.append("Found record outside date range, stopping activity analysis for this campaign\n");
                        shouldContinue = false;
                        break;
                    }
                    // 3. APPLY THE 'TO DATE' CHECK AS A SKIP CONDITION
                    if(recordDate.isAfter(toDate)){
                        totalProcessed++;
                        continue; // Skip the rest of the loop for this record, but continue to the next (older) one.
                    }

                    // Check if this is a sent email (event_type = 1)
                    Integer eventType = (Integer) activity.get("event_type");
                    if (eventType != null && eventType == 1) {
                        String contact = (String) activity.get("contact");
                        String espType = espMapping.getOrDefault(contact, "Others");
                        campaign.addESPCount(espType);
                        sentEmailsFound++;
                    }
                    totalProcessed++;
                }

                long parseTime = System.currentTimeMillis() - parseStart;
                long pageTime = System.currentTimeMillis() - pageStart;
                System.out.println("                     Processing took " + parseTime + "ms, page total: " + pageTime + "ms");
                System.out.println("                     Found " + sentEmailsFound + " sent emails so far");

                if (!shouldContinue) {
                    break;
                }

                if (activityHistory.size() < limit) {
                    System.out.println("                     Last page reached (less than " + limit + " items)");
                    break;
                }

                beforeId = ((Map<String, Object>) activityHistory.get(activityHistory.size() - 1)).get("id").toString();
                isFirstCall = false;
                System.out.println("                     Preparing next activity page...");
            }

            long methodTime = System.currentTimeMillis() - methodStart;
            System.out.println("               >> analyzeActivityByESP() completed in " + methodTime + "ms");
            System.out.println("                  Total records processed: " + totalProcessed + ", Pages: " + pageCount);
            System.out.println("                  ESP Counts - Google: " + campaign.googleCount +
                    ", Microsoft: " + campaign.microsoftCount + ", Others: " + campaign.othersCount);

        } catch (Exception e) {
            long methodTime = System.currentTimeMillis() - methodStart;
            log.append("Error analyzing activity: ").append(e.getMessage()).append("\n");
            System.out.println("               >> ERROR in analyzeActivityByESP() after " + methodTime + "ms: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Parse individual campaign node from API response
     */
    private CampaignAnalytics parseCampaignNode(JsonNode campaignNode) {
        try {
            CampaignAnalytics campaign = new CampaignAnalytics();

            campaign.campaignName = getTextValue(campaignNode, "campaign_name");
            campaign.campaignId = getTextValue(campaignNode, "campaign_id");
            campaign.campaignStatus = getIntValue(campaignNode, "campaign_status");
            campaign.isEvergreen = getBooleanValue(campaignNode, "campaign_is_evergreen");
            campaign.leadsCount = getIntValue(campaignNode, "leads_count");
            campaign.contactedCount = getIntValue(campaignNode, "contacted_count");
            campaign.openCount = getIntValue(campaignNode, "open_count");
            campaign.replyCount = getIntValue(campaignNode, "reply_count");
            campaign.linkClickCount = getIntValue(campaignNode, "link_click_count");
            campaign.bouncedCount = getIntValue(campaignNode, "bounced_count");
            campaign.unsubscribedCount = getIntValue(campaignNode, "unsubscribed_count");
            campaign.completedCount = getIntValue(campaignNode, "completed_count");
            campaign.emailsSentCount = getIntValue(campaignNode, "emails_sent_count");
            campaign.newLeadsContactedCount = getIntValue(campaignNode, "new_leads_contacted_count");
            campaign.totalOpportunities = getIntValue(campaignNode, "total_opportunities");
            campaign.totalOpportunityValue = getIntValue(campaignNode, "total_opportunity_value");

            return campaign;

        } catch (Exception e) {
            System.err.println("Error parsing campaign node: " + e.getMessage());
            return null;
        }
    }

    /**
     * Generate Excel report with campaign sent data (Enhanced with ESP breakdown)
     */
    private void writeExcel(String fromDate, String toDate, List<CampaignAnalytics> campaignData, StringBuilder log) {
        long methodStart = System.currentTimeMillis();
        System.out.println("   >> writeExcel() started");

        try (Workbook workbook = new XSSFWorkbook()) {
            long sheetStart = System.currentTimeMillis();
            System.out.println("      Creating Excel sheet...");

            createCampaignSentSheet(workbook, fromDate, toDate, campaignData);

            System.out.println("      Sheet created in " + (System.currentTimeMillis() - sheetStart) + "ms");

            long writeStart = System.currentTimeMillis();
            System.out.println("      Writing to file...");

            try (FileOutputStream fileOut = new FileOutputStream(EXCEL_FILE_PATH)) {
                workbook.write(fileOut);
            }

            System.out.println("      File written in " + (System.currentTimeMillis() - writeStart) + "ms");

            log.append("Excel file saved: ").append(EXCEL_FILE_PATH).append("\n");

            long methodTime = System.currentTimeMillis() - methodStart;
            System.out.println("   >> writeExcel() completed in " + methodTime + "ms");

        } catch (IOException e) {
            long methodTime = System.currentTimeMillis() - methodStart;
            log.append("Error writing Excel file: ").append(e.getMessage()).append("\n");
            System.out.println("   >> ERROR in writeExcel() after " + methodTime + "ms: " + e.getMessage());
            throw new RuntimeException("Error creating Excel file", e);
        }
    }

    /**
     * Create the campaign_level_sent sheet (Enhanced with ESP breakdown)
     */
    private void createCampaignSentSheet(Workbook workbook, String fromDate, String toDate, List<CampaignAnalytics> campaignData) {
        Sheet sheet = workbook.createSheet("campaign_level_sent");

        // Create styles
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);
        CellStyle totalStyle = createTotalStyle(workbook);

        // Create title row
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Campaign Sent Analytics (" + fromDate + " to " + toDate + ")");
        titleCell.setCellStyle(headerStyle);

        // Create header row with ESP columns
        Row headerRow = sheet.createRow(2);
        String[] headers = {"Campaign Name", "Google", "Microsoft", "Others", "emails_sent_count"};

        for (int i = 0; i < headers.length; i++) {
            Cell headerCell = headerRow.createCell(i);
            headerCell.setCellValue(headers[i]);
            headerCell.setCellStyle(headerStyle);
        }

        // Fill data rows
        int rowIndex = 3;
        int totalGoogle = 0, totalMicrosoft = 0, totalOthers = 0, totalEmailsSent = 0;

        for (CampaignAnalytics campaign : campaignData) {
            Row dataRow = sheet.createRow(rowIndex++);

            Cell nameCell = dataRow.createCell(0);
            nameCell.setCellValue(campaign.campaignName != null ? campaign.campaignName : "");
            nameCell.setCellStyle(dataStyle);

            Cell googleCell = dataRow.createCell(1);
            googleCell.setCellValue(campaign.googleCount);
            googleCell.setCellStyle(dataStyle);

            Cell microsoftCell = dataRow.createCell(2);
            microsoftCell.setCellValue(campaign.microsoftCount);
            microsoftCell.setCellStyle(dataStyle);

            Cell othersCell = dataRow.createCell(3);
            othersCell.setCellValue(campaign.othersCount);
            othersCell.setCellStyle(dataStyle);

            Cell sentCell = dataRow.createCell(4);
            sentCell.setCellValue(campaign.emailsSentCount);
            sentCell.setCellStyle(dataStyle);

            totalGoogle += campaign.googleCount;
            totalMicrosoft += campaign.microsoftCount;
            totalOthers += campaign.othersCount;
            totalEmailsSent += campaign.emailsSentCount;
        }

        // Add total row
        Row totalRow = sheet.createRow(rowIndex + 1);
        totalRow.createCell(0).setCellValue("TOTAL");
        totalRow.createCell(1).setCellValue(totalGoogle);
        totalRow.createCell(2).setCellValue(totalMicrosoft);
        totalRow.createCell(3).setCellValue(totalOthers);
        totalRow.createCell(4).setCellValue(totalEmailsSent);

        // Apply total style to all cells in total row
        for (int i = 0; i <= 4; i++) {
            totalRow.getCell(i).setCellStyle(totalStyle);
        }

        // Auto-size columns
        sheet.setColumnWidth(0, 20000); // Campaign name - wider
        for (int i = 1; i <= 4; i++) {
            sheet.autoSizeColumn(i);
            if (sheet.getColumnWidth(i) < 3000) {
                sheet.setColumnWidth(i, 3000);
            }
        }

        // Merge title cell across columns
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 4));
    }

    /**
     * Helper methods for JSON parsing
     */
    private String getTextValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return (field != null && !field.isNull()) ? field.asText() : "";
    }

    private int getIntValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return (field != null && !field.isNull()) ? field.asInt() : 0;
    }

    private boolean getBooleanValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return (field != null && !field.isNull()) ? field.asBoolean() : false;
    }

    /**
     * Style creation methods
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        addBorders(style);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        addBorders(style);
        return style;
    }

    private CellStyle createTotalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
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
    public File getLatestCampaignSentExcelFile() {
        File file = new File(EXCEL_FILE_PATH);
        return file.exists() ? file : null;
    }
}