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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CampaignLeadAnalyzerService {

    // ---- CONFIG ----
    private static final String BASE_URL = API.BASE_URL;
    private static final String API_KEY = API.API_KEY;
    private static final String EXCEL_FILE_PATH = System.getProperty("java.io.tmpdir") + "campaign_lead_analysis_report.xlsx";

    // Rate limiting
    private static final long CAMPAIGN_DELAY_MS = 300;
    private static final long LEAD_DELAY_MS = 200;

    // Date formats
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]'Z'")
            .withZone(ZoneOffset.UTC);

    // ESP codes
    private static final int CODE_GOOGLE = 1;
    private static final int CODE_MICROSOFT = 2;

    // Month mapping for campaign name parsing
    private static final Map<String, Integer> MONTH_MAP = new HashMap<>();
    static {
        MONTH_MAP.put("Jan", 1);
        MONTH_MAP.put("Feb", 2);
        MONTH_MAP.put("Mar", 3);
        MONTH_MAP.put("April", 4);
        MONTH_MAP.put("May", 5);
        MONTH_MAP.put("June", 6);
        MONTH_MAP.put("July", 7);
        MONTH_MAP.put("Aug", 8);
        MONTH_MAP.put("Sep", 9);
        MONTH_MAP.put("Oct", 10);
        MONTH_MAP.put("Nov", 11);
        MONTH_MAP.put("Dec", 12);
    }

    // Data classes
    private static class Campaign {
        String id;
        String name;
        LocalDate timestampDate;
        LocalDate campaignDate;
        int year;

        Campaign(String id, String name, LocalDate timestampDate, LocalDate campaignDate, int year) {
            this.id = id;
            this.name = name;
            this.timestampDate = timestampDate;
            this.campaignDate = campaignDate;
            this.year = year;
        }
    }

    private static class LeadData {
        String id;
        String email;
        JsonNode statusSummary; // Changed to JsonNode to handle object
        int espCode;
        String campaignId;
        String campaignName;
        boolean isContacted;

        LeadData(String id, String email, JsonNode statusSummary, int espCode, String campaignId, String campaignName) {
            this.id = id;
            this.email = email;
            this.statusSummary = statusSummary;
            this.espCode = espCode;
            this.campaignId = campaignId;
            this.campaignName = campaignName;
            // FIXED: Properly check if status_summary is not null and not empty
            this.isContacted = statusSummary != null && !statusSummary.isNull() && !statusSummary.isEmpty();
        }
    }

    // Instance variables for Excel export
    private Map<String, CampaignStats> campaignStatsMap = new HashMap<>();
    private OverallStats overallStats = new OverallStats();

    private static class CampaignStats {
        String campaignName;
        int totalLeads = 0;
        int contactedLeads = 0;
        int notContactedLeads = 0;
        int googleContacted = 0;
        int microsoftContacted = 0;
        int othersContacted = 0;

        CampaignStats(String campaignName) {
            this.campaignName = campaignName;
        }
    }

    private static class OverallStats {
        int totalNotYetContacted = 0;
        int totalContacted = 0;
        int googleContacted = 0;
        int microsoftContacted = 0;
        int othersContacted = 0;
    }

    public String analyzeCampaignLeadsByDateRange(String fromDateStr, String toDateStr) {
        try {
            StringBuilder log = new StringBuilder();
            log.append("🎯 Campaign Lead Analysis for date range: ").append(fromDateStr).append(" to ").append(toDateStr).append("\n");
            log.append("🚀 Starting Campaign Lead Analysis with Contact Status tracking...\n\n");

            LocalDate fromDate = LocalDate.parse(fromDateStr, DATE_FORMATTER);
            LocalDate toDate = LocalDate.parse(toDateStr, DATE_FORMATTER);

            log.append("📅 Target Date Range: ").append(fromDate).append(" to ").append(toDate).append("\n");
            log.append("🔍 Filtering Method: Campaign name date + timestamp year (status ≠ 0)\n");
            log.append("📋 Contact Logic: status_summary is JSON object (not null and not empty)\n\n");

            // Clear previous data
            campaignStatsMap.clear();
            overallStats = new OverallStats();

            // Step 1: Fetch campaigns filtered by name-based date
            List<Campaign> campaigns = fetchCampaignsWithNameBasedFiltering(fromDate, toDate, log);

            if (campaigns.isEmpty()) {
                log.append("❌ No campaigns found matching the date criteria.\n");
                return log.toString();
            }

            log.append("✅ Found ").append(campaigns.size()).append(" campaigns matching date criteria\n\n");

            // Step 2: Fetch leads for all campaigns and analyze contact status
            for (int i = 0; i < campaigns.size(); i++) {
                Campaign campaign = campaigns.get(i);
                log.append("═══════════════════════════════════════════════════════════════\n");
                log.append("📊 Processing campaign ").append(i + 1).append("/").append(campaigns.size()).append("\n");
                log.append("Campaign ID: ").append(campaign.id).append("\n");
                log.append("Campaign Name: ").append(campaign.name).append("\n");
                log.append("Campaign Date: ").append(campaign.campaignDate).append("\n");
                log.append("═══════════════════════════════════════════════════════════════\n");

                List<LeadData> leads = fetchLeadsForCampaign(campaign, log);
                processCampaignLeads(campaign, leads, log);

                log.append("───────────────────────────────────────────────────────────────\n\n");

                // Rate limiting delay
                if (i < campaigns.size() - 1) {
                    safeSleep(CAMPAIGN_DELAY_MS);
                }
            }

            // Step 3: Generate summary and export
            generateSummary(log);
            exportCampaignLeadAnalysisToExcel(fromDateStr, toDateStr, log);

            log.append("✅ Campaign Lead Analysis completed successfully!\n");
            log.append("📁 Excel report ready for download.\n");

            return log.toString();

        } catch (Exception e) {
            return "❌ Campaign Lead Analysis failed: " + e.getMessage() + "\n" +
                    "Stack trace: " + Arrays.toString(e.getStackTrace());
        }
    }

    private List<Campaign> fetchCampaignsWithNameBasedFiltering(LocalDate fromDate, LocalDate toDate, StringBuilder log) {
        List<Campaign> matchingCampaigns = new ArrayList<>();
        int skip = 0;
        int limit = 100;
        int batch = 1;
        boolean foundAnyMatching = false;

        try {
            log.append("🔍 Starting campaign fetch with name-based date filtering...\n");
            log.append("Looking for campaigns with dates between ").append(fromDate).append(" and ").append(toDate).append("\n\n");

            while (true) {
                log.append("📡 Fetching campaigns batch ").append(batch).append(" (skip: ").append(skip).append(")\n");

                String requestBody = "{\n" +
                        "  \"limit\": " + limit + ",\n" +
                        "  \"skip\": " + skip + ",\n" +
                        "  \"search\": \"\",\n" +
                        "  \"status\": null,\n" +
                        "  \"include_tags\": true,\n" +
                        "  \"tag\": null,\n" +
                        "  \"sortColumn\": \"timestamp_created\",\n" +
                        "  \"sortOrder\": \"desc\"\n" +
                        "}";

                RequestSpecification request = RestAssured.given()
                        .baseUri(BASE_URL)
                        .header("X-org-auth", API_KEY)
                        .header("Content-Type", "application/json")
                        .body(requestBody);

                Response response = request.when()
                        .post("/backend-alt/api/v1/campaign/list")
                        .then()
                        .extract()
                        .response();

                if (response.getStatusCode() != 200) {
                    log.append("❌ Campaign API call failed with status: ").append(response.getStatusCode()).append("\n");
                    if (response.getStatusCode() == 429) {
                        log.append("⚠️ Rate limit hit. Waiting 5 seconds...\n");
                        safeSleep(5000);
                        continue;
                    }
                    break;
                }

                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(response.getBody().asString());

                if (!rootNode.isArray() || rootNode.size() == 0) {
                    log.append("🏁 No more campaigns to fetch.\n");
                    break;
                }

                boolean foundInBatch = false;
                boolean reachedEarlierThanTarget = false;

                for (JsonNode campaignNode : rootNode) {
                    String timestamp = asText(campaignNode, "timestamp_created");
                    String name = asText(campaignNode, "name");
                    String id = asText(campaignNode, "id");
                    JsonNode statusNode = campaignNode.get("status");

                    if (timestamp == null || name == null || id == null) continue;

                    // CRITICAL: Filter out campaigns with status = 0
                    int status = statusNode != null && !statusNode.isNull() ? statusNode.asInt() : -1;
                    if (status == 0) {
                        log.append("🚫 Skipping campaign with status=0: ").append(name.substring(0, Math.min(30, name.length()))).append("...\n");
                        continue;
                    }

                    // Get year from timestamp
                    int year;
                    LocalDate timestampDate;
                    try {
                        Instant instant = Instant.from(ISO_FORMATTER.parse(timestamp));
                        timestampDate = instant.atZone(ZoneOffset.UTC).toLocalDate();
                        year = timestampDate.getYear();
                    } catch (Exception e) {
                        log.append("⚠️ Could not parse timestamp for campaign: ").append(name).append("\n");
                        continue;
                    }

                    // Parse date from campaign name
                    LocalDate campaignDate = parseDateFromCampaignName(name, year);

                    if (campaignDate != null) {
                        // Check if campaign date is in our target range
                        if (!campaignDate.isBefore(fromDate) && !campaignDate.isAfter(toDate)) {
                            matchingCampaigns.add(new Campaign(id, name, timestampDate, campaignDate, year));
                            foundInBatch = true;
                            foundAnyMatching = true;

                            log.append("✅ Match found (status=").append(status).append("): ").append(campaignDate).append(" - ")
                                    .append(name.substring(0, Math.min(50, name.length()))).append("...\n");
                        } else {
                            log.append("📅 Campaign date ").append(campaignDate).append(" outside range (status=").append(status).append(") - ")
                                    .append(name.substring(0, Math.min(30, name.length()))).append("...\n");
                        }

                        // Check if we've reached campaigns much earlier than our target
                        if (campaignDate.isBefore(fromDate.minusMonths(2))) {
                            reachedEarlierThanTarget = true;
                            log.append("⏹️ Reached campaigns much earlier than target range (").append(campaignDate).append(")\n");
                        }
                    } else {
                        log.append("⚠️ Could not parse date from campaign name (status=").append(status).append("): ").append(name).append("\n");
                    }
                }

                log.append("✅ Batch ").append(batch).append(" processed: ")
                        .append(foundInBatch ? "found matching campaigns" : "no matches")
                        .append(", total matches so far: ").append(matchingCampaigns.size()).append("\n");

                // Stopping logic
                if (foundAnyMatching && reachedEarlierThanTarget) {
                    log.append("🏁 Stopping search as we've reached campaigns much earlier than target range.\n");
                    break;
                }

                if (rootNode.size() < limit) {
                    log.append("🏁 Received less than limit, no more data.\n");
                    break;
                }

                skip += limit;
                batch++;
                safeSleep(CAMPAIGN_DELAY_MS);
            }

            log.append("\n📊 Campaign filtering summary:\n");
            log.append("Total batches processed: ").append(batch - 1).append("\n");
            log.append("Campaigns matching date criteria (status≠0): ").append(matchingCampaigns.size()).append("\n");
            log.append("\n");

        } catch (Exception e) {
            log.append("❌ Error fetching campaigns: ").append(e.getMessage()).append("\n");
        }

        return matchingCampaigns;
    }

    private List<LeadData> fetchLeadsForCampaign(Campaign campaign, StringBuilder log) {
        List<LeadData> leads = new ArrayList<>();
        String pageTrailId = null;
        int limit = 1000;
        int batch = 1;
        int totalFetched = 0;

        try {
            log.append("🔍 Fetching leads for campaign: ").append(campaign.name).append("\n");

            while (true) {
                String requestBody = "{\n" +
                        "    \"campaign\": \"" + campaign.id + "\",\n" +
                        "    \"search\": \"\",\n" +
                        "    \"limit\": " + limit + ",\n" +
                        "    \"queries\": []";

                if (pageTrailId != null) {
                    requestBody += ",\n    \"page_trail\": \"" + pageTrailId + "\"";
                }

                requestBody += "\n}";

                log.append("   📡 Leads batch ").append(batch).append(" (limit: ").append(limit);
                if (pageTrailId != null) {
                    log.append(", page_trail: ").append(pageTrailId.substring(0, Math.min(15, pageTrailId.length()))).append("...");
                }
                log.append(")\n");

                RequestSpecification request = RestAssured.given()
                        .baseUri(BASE_URL)
                        .header("X-org-auth", API_KEY)
                        .header("Content-Type", "application/json")
                        .body(requestBody);

                Response response = request.when()
                        .post("/backend-alt/api/v1/lead/list")
                        .then()
                        .extract()
                        .response();

                if (response.getStatusCode() != 200) {
                    log.append("   ❌ Leads API call failed with status: ").append(response.getStatusCode()).append("\n");
                    if (response.getStatusCode() == 429) {
                        log.append("   ⚠️ Rate limit hit. Waiting 5 seconds...\n");
                        safeSleep(5000);
                        continue;
                    }
                    break;
                }

                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(response.getBody().asString());
                JsonNode itemsNode = rootNode.get("items");

                if (itemsNode == null || !itemsNode.isArray() || itemsNode.size() == 0) {
                    log.append("   🏁 No more leads for this campaign.\n");
                    break;
                }

                int batchSize = itemsNode.size();
                int processedInBatch = 0;
                int contactedInBatch = 0;
                int notContactedInBatch = 0;

                for (JsonNode leadNode : itemsNode) {
                    String id = asText(leadNode, "id");
                    String contact = asText(leadNode, "contact");
                    JsonNode statusSummaryNode = leadNode.get("status_summary"); // Get as JsonNode
                    JsonNode espCodeNode = leadNode.get("esp_code");

                    if (id != null && contact != null) {
                        int espCode = (espCodeNode != null && !espCodeNode.isNull()) ? espCodeNode.asInt() : 999;

                        LeadData leadData = new LeadData(id, contact, statusSummaryNode, espCode, campaign.id, campaign.name);
                        leads.add(leadData);
                        processedInBatch++;

                        // Debug logging for contact status
                        if (leadData.isContacted) {
                            contactedInBatch++;
                            log.append("   ✅ CONTACTED: ").append(contact).append(" - status_summary: ")
                                    .append(statusSummaryNode != null ? statusSummaryNode.toString().substring(0, Math.min(50, statusSummaryNode.toString().length())) : "null")
                                    .append("...\n");
                        } else {
                            notContactedInBatch++;
                        }

                        // Set pageTrailId for next iteration
                        pageTrailId = id;
                    }
                }

                totalFetched += batchSize;

                log.append("   ✅ Batch ").append(batch).append(": ").append(batchSize).append(" leads fetched, ")
                        .append(processedInBatch).append(" processed (").append(contactedInBatch).append(" contacted, ")
                        .append(notContactedInBatch).append(" not contacted), total: ").append(leads.size()).append("\n");

                if (itemsNode.size() < limit || pageTrailId == null) {
                    log.append("   🏁 Reached end of leads (received less than limit or no page_trail).\n");
                    break;
                }

                batch++;
                safeSleep(LEAD_DELAY_MS);
            }

            log.append("📊 Campaign leads summary: ").append(totalFetched).append(" raw leads fetched, ")
                    .append(leads.size()).append(" leads processed\n");

        } catch (Exception e) {
            log.append("   ❌ Error fetching leads for campaign ").append(campaign.id).append(": ").append(e.getMessage()).append("\n");
            e.printStackTrace();
        }

        return leads;
    }

    private void processCampaignLeads(Campaign campaign, List<LeadData> leads, StringBuilder log) {
        CampaignStats stats = new CampaignStats(campaign.name);

        int contactedCount = 0;
        int notContactedCount = 0;
        int googleContacted = 0;
        int microsoftContacted = 0;
        int othersContacted = 0;

        for (LeadData lead : leads) {
            stats.totalLeads++;

            if (lead.isContacted) {
                contactedCount++;
                stats.contactedLeads++;
                overallStats.totalContacted++;

                // Count ESP for contacted leads only
                if (lead.espCode == CODE_GOOGLE) {
                    googleContacted++;
                    stats.googleContacted++;
                    overallStats.googleContacted++;
                } else if (lead.espCode == CODE_MICROSOFT) {
                    microsoftContacted++;
                    stats.microsoftContacted++;
                    overallStats.microsoftContacted++;
                } else {
                    othersContacted++;
                    stats.othersContacted++;
                    overallStats.othersContacted++;
                }
            } else {
                notContactedCount++;
                stats.notContactedLeads++;
                overallStats.totalNotYetContacted++;
            }
        }

        campaignStatsMap.put(campaign.id, stats);

        log.append("\n📈 Campaign Lead Analysis:\n");
        log.append("Total leads: ").append(leads.size()).append("\n");
        log.append("Contacted leads (status_summary not null/empty): ").append(contactedCount).append("\n");
        log.append("Not yet contacted (status_summary null/empty): ").append(notContactedCount).append("\n");
        log.append("ESP breakdown for contacted leads:\n");
        log.append("  Google: ").append(googleContacted).append("\n");
        log.append("  Microsoft: ").append(microsoftContacted).append("\n");
        log.append("  Others: ").append(othersContacted).append("\n");
    }

    private void generateSummary(StringBuilder log) {
        log.append("📊 OVERALL ANALYSIS SUMMARY:\n");
        log.append("═══════════════════════════════════════════════════════════════\n");
        log.append("Total campaigns processed: ").append(campaignStatsMap.size()).append("\n");
        log.append("Total leads not yet contacted: ").append(overallStats.totalNotYetContacted).append("\n");
        log.append("Total leads contacted: ").append(overallStats.totalContacted).append("\n");
        log.append("ESP breakdown for contacted leads:\n");
        log.append("  Google: ").append(overallStats.googleContacted).append("\n");
        log.append("  Microsoft: ").append(overallStats.microsoftContacted).append("\n");
        log.append("  Others: ").append(overallStats.othersContacted).append("\n");
        log.append("═══════════════════════════════════════════════════════════════\n\n");
    }

    private LocalDate parseDateFromCampaignName(String campaignName, int year) {
        Pattern datePattern = Pattern.compile("(\\d{1,2})_(Jan|Feb|Mar|April|May|June|July|Aug|Sep|Oct|Nov|Dec)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = datePattern.matcher(campaignName);

        if (matcher.find()) {
            try {
                int day = Integer.parseInt(matcher.group(1));
                String monthStr = matcher.group(2);
                monthStr = normalizeMonthString(monthStr);
                Integer month = MONTH_MAP.get(monthStr);
                if (month != null) {
                    return LocalDate.of(year, month, day);
                }
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private String normalizeMonthString(String monthStr) {
        String normalized = monthStr.toLowerCase();
        switch (normalized) {
            case "january": return "Jan";
            case "february": return "Feb";
            case "march": return "Mar";
            case "april": return "April";
            case "may": return "May";
            case "june": return "June";
            case "july": return "July";
            case "august": return "Aug";
            case "september": return "Sep";
            case "october": return "Oct";
            case "november": return "Nov";
            case "december": return "Dec";
            default:
                return monthStr.substring(0, 1).toUpperCase() + monthStr.substring(1).toLowerCase();
        }
    }

    private void exportCampaignLeadAnalysisToExcel(String fromDate, String toDate, StringBuilder log) {
        try (Workbook workbook = new XSSFWorkbook()) {

            // Sheet 1: Overall Summary
            createOverallSummarySheet(workbook, fromDate, toDate);

            // Sheet 2: Campaign-wise breakdown
            createCampaignBreakdownSheet(workbook, fromDate, toDate);

            // Save file
            try (FileOutputStream fileOut = new FileOutputStream(EXCEL_FILE_PATH)) {
                workbook.write(fileOut);
            }

            log.append("📊 Excel report with 2 sheets generated successfully!\n");
            log.append("   📋 Sheet 1: Overall Summary\n");
            log.append("   📋 Sheet 2: Campaign Breakdown\n");
            log.append("📁 File saved: ").append(EXCEL_FILE_PATH).append("\n");

        } catch (IOException e) {
            log.append("❌ Error creating Excel file: ").append(e.getMessage()).append("\n");
            throw new RuntimeException("Error creating Excel file", e);
        }
    }

//    private void createOverallSummarySheet(Workbook workbook, String fromDate, String toDate) {
//        Sheet sheet = workbook.createSheet("Overall Summary");
//
//        CellStyle headerStyle = headerStyle(workbook, IndexedColors.LIGHT_BLUE.getIndex());
//        CellStyle dataStyle = borderStyle(workbook);
//
//        // Create title
//        Row titleRow = sheet.createRow(0);
//        Cell titleCell = titleRow.createCell(0);
//        titleCell.setCellValue("Campaign Lead Analysis Report (" + fromDate + " to " + toDate + ")");
//        titleCell.setCellStyle(headerStyle);
//
//        // Create headers
//        Row headerRow = sheet.createRow(2);
//        String[] headers = {"Total Leads Not Yet Contacted", "Total Leads Contacted", "Google", "Microsoft", "Others"};
//        for (int i = 0; i < headers.length; i++) {
//            Cell cell = headerRow.createCell(i);
//            cell.setCellValue(headers[i]);
//            cell.setCellStyle(headerStyle);
//        }
//
//        // Fill data
//        Row dataRow = sheet.createRow(3);
//        dataRow.createCell(0).setCellValue(overallStats.totalNotYetContacted);
//        dataRow.createCell(1).setCellValue(overallStats.totalContacted);
//        dataRow.createCell(2).setCellValue(overallStats.googleContacted);
//        dataRow.createCell(3).setCellValue(overallStats.microsoftContacted);
//        dataRow.createCell(4).setCellValue(overallStats.othersContacted);
//
//        // Apply styles
//        for (int i = 0; i < 5; i++) {
//            dataRow.getCell(i).setCellStyle(dataStyle);
//            sheet.autoSizeColumn(i);
//            if (sheet.getColumnWidth(i) < 4000) sheet.setColumnWidth(i, 4000);
//        }
//    }
        private void createOverallSummarySheet(Workbook workbook, String fromDate, String toDate) {
            Sheet sheet = workbook.createSheet("Overall Summary");

            CellStyle headerStyle = headerStyle(workbook, IndexedColors.LIGHT_BLUE.getIndex());
            CellStyle dataStyle = borderStyle(workbook);

            // Create title
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Campaign Lead Analysis Report (" + fromDate + " to " + toDate + ")");
            titleCell.setCellStyle(headerStyle);

            // Create headers - UPDATED: Removed "Total Leads Not Yet Contacted"
            Row headerRow = sheet.createRow(2);
            String[] headers = {"Total Leads Contacted", "Google", "Microsoft", "Others"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Fill data - UPDATED: Only contacted leads and ESP breakdown
            Row dataRow = sheet.createRow(3);
            dataRow.createCell(0).setCellValue(overallStats.totalContacted);
            dataRow.createCell(1).setCellValue(overallStats.googleContacted);
            dataRow.createCell(2).setCellValue(overallStats.microsoftContacted);
            dataRow.createCell(3).setCellValue(overallStats.othersContacted);

            // Apply styles
            for (int i = 0; i < 4; i++) {
                dataRow.getCell(i).setCellStyle(dataStyle);
                sheet.autoSizeColumn(i);
                if (sheet.getColumnWidth(i) < 4000) sheet.setColumnWidth(i, 4000);
            }
        }

//    private void createCampaignBreakdownSheet(Workbook workbook, String fromDate, String toDate) {
//        Sheet sheet = workbook.createSheet("Campaign Breakdown");
//
//        CellStyle headerStyle = headerStyle(workbook, IndexedColors.ORANGE.getIndex());
//        CellStyle dataStyle = borderStyle(workbook);
//
//        // Create title
//        Row titleRow = sheet.createRow(0);
//        Cell titleCell = titleRow.createCell(0);
//        titleCell.setCellValue("Campaign-wise Lead Analysis (" + fromDate + " to " + toDate + ")");
//        titleCell.setCellStyle(headerStyle);
//
//        // Create headers
//        Row headerRow = sheet.createRow(2);
//        String[] headers = {"Campaign Name", "Leads Contacted Count", "Google", "Microsoft", "Others"};
//        for (int i = 0; i < headers.length; i++) {
//            Cell cell = headerRow.createCell(i);
//            cell.setCellValue(headers[i]);
//            cell.setCellStyle(headerStyle);
//        }
//
//        // Fill data rows
//        int rowIndex = 3;
//        for (CampaignStats stats : campaignStatsMap.values()) {
//            Row dataRow = sheet.createRow(rowIndex++);
//
//            dataRow.createCell(0).setCellValue(stats.campaignName);
//            dataRow.createCell(1).setCellValue(stats.contactedLeads);
//            dataRow.createCell(2).setCellValue(stats.googleContacted);
//            dataRow.createCell(3).setCellValue(stats.microsoftContacted);
//            dataRow.createCell(4).setCellValue(stats.othersContacted);
//
//            // Apply styles
//            for (int i = 0; i < 5; i++) {
//                dataRow.getCell(i).setCellStyle(dataStyle);
//            }
//        }
//
//        // Auto-size columns
//        sheet.setColumnWidth(0, 15000); // Campaign name - wider
//        for (int i = 1; i < 5; i++) {
//            sheet.autoSizeColumn(i);
//            if (sheet.getColumnWidth(i) < 3000) {
//                sheet.setColumnWidth(i, 3000);
//            }
//        }
//    }
    private void createCampaignBreakdownSheet(Workbook workbook, String fromDate, String toDate) {
        Sheet sheet = workbook.createSheet("Campaign Breakdown");

        CellStyle headerStyle = headerStyle(workbook, IndexedColors.ORANGE.getIndex());
        CellStyle dataStyle = borderStyle(workbook);

        // Create title
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Campaign-wise Lead Analysis (" + fromDate + " to " + toDate + ")");
        titleCell.setCellStyle(headerStyle);

        // Create headers - UPDATED: Added Total Leads and Leads Not Yet Contacted
        Row headerRow = sheet.createRow(2);
        String[] headers = {"Campaign Name", "Total Leads", "Leads Not Yet Contacted", "Leads Contacted Count", "Google", "Microsoft", "Others"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Fill data rows - UPDATED: Include all required columns
        int rowIndex = 3;
        for (CampaignStats stats : campaignStatsMap.values()) {
            Row dataRow = sheet.createRow(rowIndex++);

            dataRow.createCell(0).setCellValue(stats.campaignName);
            dataRow.createCell(1).setCellValue(stats.totalLeads); // Total Leads
            dataRow.createCell(2).setCellValue(stats.notContactedLeads); // Leads Not Yet Contacted
            dataRow.createCell(3).setCellValue(stats.contactedLeads); // Leads Contacted Count
            dataRow.createCell(4).setCellValue(stats.googleContacted); // Google
            dataRow.createCell(5).setCellValue(stats.microsoftContacted); // Microsoft
            dataRow.createCell(6).setCellValue(stats.othersContacted); // Others

            // Apply styles
            for (int i = 0; i < 7; i++) {
                dataRow.getCell(i).setCellStyle(dataStyle);
            }
        }

        // Add total row at the bottom
        Row totalRow = sheet.createRow(rowIndex + 1);
        Cell totalLabelCell = totalRow.createCell(0);
        totalLabelCell.setCellValue("TOTAL");
        totalLabelCell.setCellStyle(headerStyle);

        // Calculate totals across all campaigns
        int totalLeadsSum = 0;
        int totalNotContactedSum = 0;
        int totalContactedSum = 0;
        int totalGoogleSum = 0;
        int totalMicrosoftSum = 0;
        int totalOthersSum = 0;

        for (CampaignStats stats : campaignStatsMap.values()) {
            totalLeadsSum += stats.totalLeads;
            totalNotContactedSum += stats.notContactedLeads;
            totalContactedSum += stats.contactedLeads;
            totalGoogleSum += stats.googleContacted;
            totalMicrosoftSum += stats.microsoftContacted;
            totalOthersSum += stats.othersContacted;
        }

        totalRow.createCell(1).setCellValue(totalLeadsSum);
        totalRow.createCell(2).setCellValue(totalNotContactedSum);
        totalRow.createCell(3).setCellValue(totalContactedSum);
        totalRow.createCell(4).setCellValue(totalGoogleSum);
        totalRow.createCell(5).setCellValue(totalMicrosoftSum);
        totalRow.createCell(6).setCellValue(totalOthersSum);

        // Apply styles to total row
        for (int i = 0; i < 7; i++) {
            totalRow.getCell(i).setCellStyle(headerStyle);
        }

        // Auto-size columns
        sheet.setColumnWidth(0, 15000); // Campaign name - wider
        for (int i = 1; i < 7; i++) {
            sheet.autoSizeColumn(i);
            if (sheet.getColumnWidth(i) < 3500) {
                sheet.setColumnWidth(i, 3500);
            }
        }
    }

    // Helper methods
    private void safeSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String asText(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        return (fieldNode != null && !fieldNode.isNull()) ? fieldNode.asText() : null;
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

    private static void addBorder(CellStyle cs) {
        cs.setBorderBottom(BorderStyle.THIN);
        cs.setBorderTop(BorderStyle.THIN);
        cs.setBorderLeft(BorderStyle.THIN);
        cs.setBorderRight(BorderStyle.THIN);
    }

    public File getLatestCampaignLeadExcelFile() {
        File file = new File(EXCEL_FILE_PATH);
        return file.exists() ? file : null;
    }
}