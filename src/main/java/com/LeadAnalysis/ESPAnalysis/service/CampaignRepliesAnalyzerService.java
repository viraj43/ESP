package com.LeadAnalysis.ESPAnalysis.service;

import com.LeadAnalysis.ESPAnalysis.config.API;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLOutput;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Campaign Replies Analyzer Service - Based on Primary Replies structure
 * Exports two sheets:
 *  1) Campaign Replies Report: Total Replies | Google | Microsoft | Others | Not Found
 *  2) Campaign Breakdown: Campaign Name | Google | Microsoft | Others | Not Found
 */
@Service
public class CampaignRepliesAnalyzerService {

    // ---- CONFIG ----
    private static final String BASE_URL = API.BASE_URL;
    private static final String API_KEY = API.API_KEY;
    private static final String EXCEL_FILE_PATH = System.getProperty("java.io.tmpdir") + "campaign_replies_analysis_report.xlsx";
    private static final int EMAIL_LIMIT_PER_REQUEST = 30;

    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    // Rate limiting configuration
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_DELAY_MS = 1000;
    private static final long RATE_LIMIT_DELAY_MS = 5000;
    private static final long BATCH_DELAY_MS = 300;
    private static final long ESP_LOOKUP_DELAY_MS = 300;

    // ESP codes
    private static final int CODE_GOOGLE = 1;
    private static final int CODE_MICROSOFT = 2;

    // Date formats
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]'Z'")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter IST_OUT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // Month mapping for campaign name parsing
    private static final Map<String, Integer> MONTH_MAP = new HashMap<>();
    static {
        MONTH_MAP.put("Jan", 1); MONTH_MAP.put("Feb", 2); MONTH_MAP.put("Mar", 3);
        MONTH_MAP.put("April", 4); MONTH_MAP.put("May", 5); MONTH_MAP.put("June", 6);
        MONTH_MAP.put("July", 7); MONTH_MAP.put("Aug", 8); MONTH_MAP.put("Sep", 9);
        MONTH_MAP.put("Oct", 10); MONTH_MAP.put("Nov", 11); MONTH_MAP.put("Dec", 12);
    }

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
        String campaignName;
        boolean isNotFound = false; // New field for tracking deleted leads
    }

    private static class Campaign {
        String id, name;
        LocalDate timestampDate, campaignDate;
        int year;

        Campaign(String id, String name, LocalDate timestampDate, LocalDate campaignDate, int year) {
            this.id = id; this.name = name; this.timestampDate = timestampDate;
            this.campaignDate = campaignDate; this.year = year;
        }
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

        public void reset() {
            hasHitRateLimit = false;
        }
    }

    // Instance variables for statistics
    private Map<String, CampaignStats> campaignStatsMap = new HashMap<>();
    private OverallStats overallStats = new OverallStats();

    private static class CampaignStats {
        String campaignName;
        int totalReplies = 0, googleReplies = 0, microsoftReplies = 0, othersReplies = 0, notFoundReplies = 0;

        CampaignStats(String campaignName) {
            this.campaignName = campaignName;
        }
    }

    private static class OverallStats {
        int totalReplies = 0, googleReplies = 0, microsoftReplies = 0, othersReplies = 0, notFoundReplies = 0;
    }

    public String analyzeCampaignRepliesByDateRange(String fromDateStr, String toDateStr) {
        try {
            StringBuilder log = new StringBuilder();
            log.append("Campaign Replies Analysis for date range: ").append(fromDateStr).append(" to ").append(toDateStr).append("\n");
            log.append("Starting Campaign Level Replies Analysis (based on Primary Replies structure)...\n\n");

            LocalDate fromDate = LocalDate.parse(fromDateStr, DATE_FORMATTER);
            LocalDate toDate = LocalDate.parse(toDateStr, DATE_FORMATTER);

            log.append("Target Date Range: ").append(fromDate).append(" to ").append(toDate).append("\n");
            log.append("Filtering Method: Campaign name date + timestamp year (status ≠ 0)\n\n");

            // Clear previous data
            campaignStatsMap.clear();
            overallStats = new OverallStats();

            // Global rate limit tracker
            RateLimitTracker rateLimitTracker = new RateLimitTracker();

            // Step 1: Fetch campaigns filtered by name-based date
            List<Campaign> campaigns = fetchCampaignsWithNameBasedFiltering(fromDate, toDate, log);

            if (campaigns.isEmpty()) {
                log.append("No campaigns found matching the date criteria.\n");
                writeExcel(fromDateStr, toDateStr, new ArrayList<>(), log);
                return log.toString();
            }

            log.append("Found ").append(campaigns.size()).append(" campaigns matching date criteria\n\n");

            // Step 2: Fetch replies for all campaigns
            List<EmailRow> allReplies = fetchRepliesForAllCampaigns(campaigns, rateLimitTracker, log);

            log.append("\nAnalysis Summary:\n");
            log.append("Campaign replies found: ").append(allReplies.size()).append("\n");
            log.append("Rate limit incidents: ").append(rateLimitTracker.getTotalRateLimitHits()).append("\n\n");

            if (allReplies.isEmpty()) {
                log.append("No campaign replies found in the given date range.\n");
                writeExcel(fromDateStr, toDateStr, allReplies, log);
                return log.toString();
            }

            // Step 3: ESP code lookup using the same method as Primary Replies
            log.append("Starting ESP code lookup for ").append(allReplies.size()).append(" emails...\n");
            Map<String, Integer> espByLead = fetchEspCodesWithEnhancedRateLimit(allReplies, rateLimitTracker, log);

            // Apply ESP codes and handle not found cases
            applyEspCodesToReplies(allReplies, espByLead, log);

            // Step 4: Process statistics and export
            processCampaignStatistics(allReplies, log);
            writeExcel(fromDateStr, toDateStr, allReplies, log);

            log.append("Campaign Replies Analysis completed successfully!\n");
            log.append("Excel report ready for download.\n");

            return log.toString();

        } catch (Exception e) {
            return "Campaign Replies Analysis failed: " + e.getMessage() + "\nStack trace: " + Arrays.toString(e.getStackTrace());
        }
    }

    private List<Campaign> fetchCampaignsWithNameBasedFiltering(LocalDate fromDate, LocalDate toDate, StringBuilder log) {
        List<Campaign> matchingCampaigns = new ArrayList<>();
        int skip = 0;
        int limit = 50;
        int batch = 1;
        boolean foundAnyMatching = false;

        try {
            log.append("Starting campaign fetch with name-based date filtering...\n");

            while (true) {
                log.append("Fetching campaigns batch ").append(batch).append(" (skip: ").append(skip).append(")\n");

                String requestBody = "{\n" +
                        "  \"limit\": " + limit + ",\n" +
                        "  \"skip\": " + skip + ",\n" +
                        "  \"search\": \"\",\n" +
                        "  \"status\": null,\n" +
                        "  \"include_tags\": false,\n" +
                        "  \"tag\": null,\n" +
                        "  \"sortColumn\": \"timestamp_created\",\n" +
                        "  \"sortOrder\": \"desc\"\n" +
                        "}";

                Response response = RestAssured.given()
                        .baseUri(BASE_URL)
                        .header("X-org-auth", API_KEY)
                        .header("Content-Type", "application/json")
                        .body(requestBody)
                        .when().post("/backend-alt/api/v1/campaign/list")
                        .then().extract().response();

                if (response.getStatusCode() != 200) {
                    log.append("Campaign API call failed with status: ").append(response.getStatusCode()).append("\n");
                    if (response.getStatusCode() == 429) {
                        log.append("Rate limit hit. Waiting 5 seconds...\n");
                        safeSleep(5000, log);
                        continue;
                    }
                    break;
                }

                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(response.getBody().asString());

                if (!rootNode.isArray() || rootNode.size() == 0) {
                    log.append("No more campaigns to fetch.\n");
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

                    // Filter out campaigns with status = 0
                    int status = statusNode != null && !statusNode.isNull() ? statusNode.asInt() : -1;
                    if (status == 0) continue;

                    // Get year from timestamp
                    int year;
                    LocalDate timestampDate;
                    try {
                        Instant instant = Instant.from(ISO_FORMATTER.parse(timestamp));
                        timestampDate = instant.atZone(ZoneOffset.UTC).toLocalDate();
                        year = timestampDate.getYear();
                    } catch (Exception e) {
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
                        }

                        // Check if we've reached campaigns much earlier than our target
                        if (campaignDate.isBefore(fromDate.minusMonths(2))) {
                            reachedEarlierThanTarget = true;
                        }
                    }
                }

                log.append("Batch ").append(batch).append(" processed: ")
                        .append(foundInBatch ? "found matching campaigns" : "no matches")
                        .append(", total matches so far: ").append(matchingCampaigns.size()).append("\n");

                // Stopping logic
                if (foundAnyMatching && reachedEarlierThanTarget) {
                    log.append("Stopping search as we've reached campaigns much earlier than target range.\n");
                    break;
                }

                if (rootNode.size() < limit) {
                    log.append("Received less than limit, no more data.\n");
                    break;
                }

                skip += limit;
                batch++;
                safeSleep(BATCH_DELAY_MS, log);
            }

            log.append("\nCampaign filtering summary:\n");
            log.append("Campaigns matching date criteria (status≠0): ").append(matchingCampaigns.size()).append("\n\n");

        } catch (Exception e) {
            log.append("Error fetching campaigns: ").append(e.getMessage()).append("\n");
        }

        return matchingCampaigns;
    }

    private List<EmailRow> fetchRepliesForAllCampaigns(List<Campaign> campaigns, RateLimitTracker rateLimitTracker, StringBuilder log) {
        List<EmailRow> allReplies = new ArrayList<>();

        for (int i = 0; i < campaigns.size(); i++) {
            Campaign campaign = campaigns.get(i);
            log.append("Processing campaign ").append(i + 1).append("/").append(campaigns.size()).append("\n");
            log.append("Campaign: ").append(campaign.name).append("\n");

            List<EmailRow> campaignReplies = fetchRepliesForCampaign(campaign, rateLimitTracker, log);
            allReplies.addAll(campaignReplies);

            log.append("Campaign replies: ").append(campaignReplies.size()).append(", Total so far: ").append(allReplies.size()).append("\n\n");

            // Rate limiting delay between campaigns
            if (i < campaigns.size() - 1) {
                safeSleep(BATCH_DELAY_MS, log);
            }
        }

        return allReplies;
    }
//    private List<EmailRow> fetchRepliesForCampaign(Campaign campaign, RateLimitTracker rateLimitTracker, StringBuilder log) {
//        List<EmailRow> replies = new ArrayList<>();
//        Set<String> seenEmailIds = new HashSet<>();
//        String pageTrail = null;
//        int skipValue = 0; // Start with skip=0
//        int apiCallCount = 0;
//        final int MAX_API_CALLS = 100;
//
//        try {
//            log.append("==========================================\n");
//            log.append("STARTING CAMPAIGN: ").append(campaign.name).append("\n");
//            log.append("Campaign ID: ").append(campaign.id).append("\n");
//            log.append("Pagination: skip increments by ").append(EMAIL_LIMIT_PER_REQUEST).append(" + page_trail_id\n");
//            log.append("==========================================\n");
//
//            while (apiCallCount < MAX_API_CALLS) {
//                apiCallCount++;
//
//                // Build URL for logging (same as what API actually receives)
//                String debugUrl = "/backend-alt/api/v1/unibox/emails?limit=" + EMAIL_LIMIT_PER_REQUEST
//                        + "&preview_only=true&campaign_id=" + campaign.id
//                        + "&mode=emode_focused&latest_of_thread=true&skip=" + skipValue;
//                if (pageTrail != null) {
//                    debugUrl += "&page_trail_id=" + pageTrail;
//                }
//
//                log.append("\n>>> API CALL #").append(apiCallCount).append(" <<<\n");
//                log.append("URL: ").append(debugUrl).append("\n");
//                log.append("Skip: ").append(skipValue).append("\n");
//                log.append("PageTrail: ").append(pageTrail != null ? pageTrail : "NULL").append("\n");
//
//                Response response = null;
//                boolean success = false;
//
//                // Make API call with retry logic
//                for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
//                    try {
//                        response = fetchCampaignEmailsBatch(campaign.id, pageTrail, skipValue);
//
//                        if (response != null && response.getStatusCode() == 200) {
//                            success = true;
//                            log.append("SUCCESS: Status 200\n");
//                            break;
//                        } else if (response != null && response.getStatusCode() == 429) {
//                            rateLimitTracker.recordRateLimitHit();
//                            log.append("RATE LIMIT (429) - Waiting 90 seconds\n");
//                            safeSleep(90000, log);
//                            continue; // Retry same call
//                        } else {
//                            log.append("FAILED: Status ").append(response != null ? response.getStatusCode() : "NULL").append("\n");
//                            if (attempt < MAX_RETRIES) {
//                                safeSleep(3000 * attempt, log);
//                                continue;
//                            }
//                            break;
//                        }
//                    } catch (Exception e) {
//                        log.append("EXCEPTION: ").append(e.getMessage()).append("\n");
//                        if (attempt < MAX_RETRIES) {
//                            safeSleep(3000 * attempt, log);
//                        }
//                    }
//                }
//
//                if (!success) {
//                    log.append("*** API CALL FAILED AFTER RETRIES - STOPPING CAMPAIGN ***\n");
//                    break;
//                }
//
//                // Parse response
//                ObjectMapper mapper = new ObjectMapper();
//                JsonNode root = mapper.readTree(response.getBody().asString());
//                JsonNode data = root.get("data");
//
//                if (data == null || !data.isArray()) {
//                    log.append("INVALID RESPONSE STRUCTURE - STOPPING\n");
//                    break;
//                }
//
//                int recordsReceived = data.size();
//                log.append("RECORDS RECEIVED: ").append(recordsReceived).append("\n");
//
//                // Process emails
//                int addedCount = 0;
//                int duplicateCount = 0;
//                String lastEmailId = null;
//
//                for (JsonNode email : data) {
//                    String id = asText(email, "id");
//                    if (id != null) {
//                        lastEmailId = id; // Always update to get the latest ID
//
//                        if (seenEmailIds.contains(id)) {
//                            duplicateCount++;
//                            continue;
//                        }
//                        seenEmailIds.add(id);
//                    }
//
//                    String lead = asText(email, "lead");
//                    String timestamp = asText(email, "timestamp_email");
//
//                    if (lead != null && timestamp != null) {
//                        try {
//                            Instant inst = Instant.from(ISO_FORMATTER.parse(timestamp));
//                            ZonedDateTime zIST = inst.atZone(IST);
//
//                            EmailRow row = new EmailRow();
//                            row.leadEmail = lead.trim();
//                            row.subject = nz(asText(email, "subject"));
//                            row.contentPreview = nz(asText(email, "content_preview"));
//                            row.fromAddress = nz(asText(email, "from_address_email"));
//                            row.formattedDateIST = zIST.format(IST_OUT);
//                            row.timestampUTC = timestamp;
//                            row.messageId = nz(asText(email, "message_id"));
//                            row.threadId = nz(asText(email, "thread_id"));
//                            row.campaignName = campaign.name;
//
//                            replies.add(row);
//                            addedCount++;
//                        } catch (Exception e) {
//                            log.append("Error parsing email: ").append(e.getMessage()).append("\n");
//                        }
//                    }
//                }
//
//                log.append("PROCESSED: +").append(addedCount).append(" new, ")
//                        .append(duplicateCount).append(" duplicates, ")
//                        .append("Total: ").append(replies.size()).append("\n");
//
//                // PAGINATION DECISION - Following the exact pattern from your network trace
//                log.append(">>> PAGINATION DECISION <<<\n");
//
//                if (recordsReceived == 0) {
//                    log.append("STOP: No records received - End of data\n");
//                    break;
//
//                } else if (recordsReceived < EMAIL_LIMIT_PER_REQUEST) {
//                    log.append("STOP: Partial batch (").append(recordsReceived)
//                            .append(" < ").append(EMAIL_LIMIT_PER_REQUEST).append(") - Final batch\n");
//                    break;
//
//                } else {
//                    // Full batch received - continue with incremented skip + new pageTrail
//                    log.append("CONTINUE: Full batch received - incrementing pagination\n");
//
//                    skipValue += EMAIL_LIMIT_PER_REQUEST; // Increment skip by limit (30)
//                    pageTrail = lastEmailId; // Update page trail to last email ID
//
//                    log.append("NEXT CALL: skip=").append(skipValue)
//                            .append(", page_trail_id=").append(pageTrail).append("\n");
//                }
//
//                // Brief delay between API calls
//                safeSleep(800, log);
//            }
//
//        } catch (Exception e) {
//            log.append("*** CRITICAL ERROR: ").append(e.getMessage()).append(" ***\n");
//            e.printStackTrace();
//        }
//
//        log.append("==========================================\n");
//        log.append("CAMPAIGN COMPLETE: ").append(campaign.name).append("\n");
//        log.append("Total API calls: ").append(apiCallCount).append("\n");
//        log.append("Total replies: ").append(replies.size()).append("\n");
//        log.append("Final skip value: ").append(skipValue).append("\n");
//        log.append("==========================================\n\n");
//
//        return replies;
//    }

    private List<EmailRow> fetchRepliesForCampaign(Campaign campaign, RateLimitTracker rateLimitTracker, StringBuilder log) {
        List<EmailRow> replies = new ArrayList<>();
        Set<String> seenEmailIds = new HashSet<>();
        String pageTrail = null;
        int skipValue = 0; // Start with skip=0
        int apiCallCount = 0;
        final int MAX_API_CALLS = 100;

        // Assuming these are defined in the global scope
        // private static final int EMAIL_LIMIT_PER_REQUEST = 30;
        // private static final int MAX_RETRIES = 3;
        // private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;
        // private static final DateTimeFormatter IST_OUT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        // private static final java.time.ZoneId IST = java.time.ZoneId.of("Asia/Kolkata");
        // private String nz(String s) { return s == null ? "" : s; }
        // private String asText(JsonNode node, String fieldName) { JsonNode field = node.get(fieldName); return (field != null) ? field.asText() : null; }
        // private void safeSleep(long millis, StringBuilder log) { ... }
        // private Response fetchCampaignEmailsBatch(String campaignId, String pageTrail, int skip) { ... }

        try {
            log.append("==========================================\n");
            System.out.println("==========================================");
            log.append("STARTING CAMPAIGN: ").append(campaign.name).append("\n");
            System.out.println("STARTING CAMPAIGN: " + campaign.name);
            log.append("Campaign ID: ").append(campaign.id).append("\n");
            System.out.println("Campaign ID: " + campaign.id);
            log.append("Pagination: skip increments by ").append(EMAIL_LIMIT_PER_REQUEST).append(" + page_trail_id\n");
            System.out.println("Pagination: skip increments by " + EMAIL_LIMIT_PER_REQUEST + " + page_trail_id");
            log.append("==========================================\n");
            System.out.println("==========================================");

            while (apiCallCount < MAX_API_CALLS) {
                apiCallCount++;

                // Build URL for logging (same as what API actually receives)
                String debugUrl = "/backend-alt/api/v1/unibox/emails?limit=" + EMAIL_LIMIT_PER_REQUEST
                        + "&preview_only=true&campaign_id=" + campaign.id
                        + "&mode=emode_focused&latest_of_thread=true&skip=" + skipValue;
                if (pageTrail != null) {
                    debugUrl += "&page_trail_id=" + pageTrail;
                }
                log.append("\n>>> API CALL #").append(apiCallCount).append(" <<<\n");
                System.out.println("\n>>> API CALL #" + apiCallCount + " <<<");
                log.append("URL: ").append(debugUrl).append("\n");
                System.out.println("URL: " + debugUrl);
                log.append("Skip: ").append(skipValue).append("\n");
                System.out.println("Skip: " + skipValue);
                log.append("PageTrail: ").append(pageTrail != null ? pageTrail : "NULL").append("\n");
                System.out.println("PageTrail: " + (pageTrail != null ? pageTrail : "NULL"));

                Response response = null;
                boolean success = false;

                // Make API call with retry logic
                for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                    try {
                        response = fetchCampaignEmailsBatch(campaign.id, pageTrail, skipValue);

                        if (response != null && response.getStatusCode() == 200) {
                            success = true;
                            log.append("SUCCESS: Status 200\n");
                            System.out.println("SUCCESS: Status 200");
                            break;
                        } else if (response != null && response.getStatusCode() == 429) {
                            rateLimitTracker.recordRateLimitHit();
                            log.append("RATE LIMIT (429) - Waiting 90 seconds\n");
                            System.out.println("RATE LIMIT (429) - Waiting 90 seconds");
                            safeSleep(90000, log);
                            continue; // Retry same call
                        } else {
                            log.append("FAILED: Status ").append(response != null ? response.getStatusCode() : "NULL").append("\n");
                            System.out.println("FAILED: Status " + (response != null ? response.getStatusCode() : "NULL"));
                            if (attempt < MAX_RETRIES) {
                                safeSleep(3000 * attempt, log);
                                continue;
                            }
                            break;
                        }
                    } catch (Exception e) {
                        log.append("EXCEPTION: ").append(e.getMessage()).append("\n");
                        System.out.println("EXCEPTION: " + e.getMessage());
                        if (attempt < MAX_RETRIES) {
                            safeSleep(3000 * attempt, log);
                        }
                    }
                }

                if (!success) {
                    log.append("*** API CALL FAILED AFTER RETRIES - STOPPING CAMPAIGN ***\n");
                    System.out.println("*** API CALL FAILED AFTER RETRIES - STOPPING CAMPAIGN ***");
                    break;
                }

                // Parse response
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.getBody().asString());
                JsonNode data = root.get("data");

                if (data == null || !data.isArray()) {
                    log.append("INVALID RESPONSE STRUCTURE - STOPPING\n");
                    System.out.println("INVALID RESPONSE STRUCTURE - STOPPING");
                    break;
                }

                int recordsReceived = data.size();
                log.append("RECORDS RECEIVED: ").append(recordsReceived).append("\n");
                System.out.println("RECORDS RECEIVED: " + recordsReceived);

                // Process emails
                int addedCount = 0;
                int duplicateCount = 0;
                String lastEmailId = null;
                for (JsonNode email : data) {
                    String id = asText(email, "id");
                    if (id != null) {
                        lastEmailId = id; // Always update to get the latest ID

                        if (seenEmailIds.contains(id)) {
                            duplicateCount++;
                            continue;
                        }
                        seenEmailIds.add(id);
                    }
                    String lead = asText(email, "lead");
                    String timestamp = asText(email, "timestamp_email");
                    if (lead != null && timestamp != null) {
                        try {
                            Instant inst = Instant.from(ISO_FORMATTER.parse(timestamp));
                            ZonedDateTime zIST = inst.atZone(IST);
                            EmailRow row = new EmailRow();
                            row.leadEmail = lead.trim();
                            row.subject = nz(asText(email, "subject"));
                            row.contentPreview = nz(asText(email, "content_preview"));
                            row.fromAddress = nz(asText(email, "from_address_email"));
                            row.formattedDateIST = zIST.format(IST_OUT);
                            row.timestampUTC = timestamp;
                            row.messageId = nz(asText(email, "message_id"));
                            row.threadId = nz(asText(email, "thread_id"));
                            row.campaignName = campaign.name;

                            replies.add(row);
                            addedCount++;
                        } catch (Exception e) {
                            log.append("Error parsing email: ").append(e.getMessage()).append("\n");
                            System.out.println("Error parsing email: " + e.getMessage());
                        }
                    }
                }

                log.append("PROCESSED: +").append(addedCount).append(" new, ")
                        .append(duplicateCount).append(" duplicates, ")
                        .append("Total: ").append(replies.size()).append("\n");
                System.out.println("PROCESSED: +" + addedCount + " new, "
                        + duplicateCount + " duplicates, "
                        + "Total: " + replies.size());

                // PAGINATION DECISION - Following the exact pattern from your network trace
                log.append(">>> PAGINATION DECISION <<<\n");
                System.out.println(">>> PAGINATION DECISION <<<");

                if (recordsReceived == 0) {
                    log.append("STOP: No records received - End of data\n");
                    System.out.println("STOP: No records received - End of data");
                    break;
                } else if (recordsReceived < EMAIL_LIMIT_PER_REQUEST) {
                    log.append("STOP: Partial batch (").append(recordsReceived)
                            .append(" < ").append(EMAIL_LIMIT_PER_REQUEST).append(") - Final batch\n");
                    System.out.println("STOP: Partial batch (" + recordsReceived
                            + " < " + EMAIL_LIMIT_PER_REQUEST + ") - Final batch");
                    break;
                } else {
                    // Full batch received - continue with incremented skip + new pageTrail
                    log.append("CONTINUE: Full batch received - incrementing pagination\n");
                    System.out.println("CONTINUE: Full batch received - incrementing pagination");

                    skipValue += EMAIL_LIMIT_PER_REQUEST; // Increment skip by limit (30)
                    pageTrail = lastEmailId; // Update page trail to last email ID

                    log.append("NEXT CALL: skip=").append(skipValue)
                            .append(", page_trail_id=").append(pageTrail).append("\n");
                    System.out.println("NEXT CALL: skip=" + skipValue
                            + ", page_trail_id=" + pageTrail);
                }

                // Brief delay between API calls
                safeSleep(800, log);
            }
        } catch (Exception e) {
            log.append("*** CRITICAL ERROR: ").append(e.getMessage()).append(" ***\n");
            System.out.println("*** CRITICAL ERROR: " + e.getMessage() + " ***");
            e.printStackTrace();
        }

        log.append("==========================================\n");
        System.out.println("==========================================");
        log.append("CAMPAIGN COMPLETE: ").append(campaign.name).append("\n");
        System.out.println("CAMPAIGN COMPLETE: " + campaign.name);
        log.append("Total API calls: ").append(apiCallCount).append("\n");
        System.out.println("Total API calls: " + apiCallCount);
        log.append("Total replies: ").append(replies.size()).append("\n");
        System.out.println("Total replies: " + replies.size());
        log.append("Final skip value: ").append(skipValue).append("\n");
        System.out.println("Final skip value: " + skipValue);
        log.append("==========================================\n\n");
        System.out.println("==========================================\n");

        return replies;
    }

    private Response fetchCampaignEmailsBatch(String campaignId, String pageTrail, int skipValue) {
        String endpoint = "/backend-alt/api/v1/unibox/emails?limit=" + EMAIL_LIMIT_PER_REQUEST
                + "&preview_only=true&campaign_id=" + campaignId
                + "&mode=emode_focused&latest_of_thread=true&skip=" + skipValue;

        if (pageTrail != null) {
            endpoint += "&page_trail_id=" + pageTrail;
        }

        RequestSpecification req = RestAssured.given()
                .baseUri(BASE_URL)
                .header("X-org-auth", API_KEY)
                .header("Content-Type", "application/json")
                .header("Connection", "keep-alive")
                .relaxedHTTPSValidation();

        return req.when().get(endpoint).then().extract().response();
    }

    // Enhanced ESP lookup with proper Not Found handling
    private Map<String, Integer> fetchEspCodesWithEnhancedRateLimit(List<EmailRow> emailRows, RateLimitTracker rateLimitTracker, StringBuilder log) {
        Map<String, Integer> out = new HashMap<>();
        Set<String> unique = new HashSet<>();
        for (EmailRow r : emailRows) {
            if (r.leadEmail != null && !r.leadEmail.isBlank()) unique.add(r.leadEmail);
        }

        log.append("Getting ESP data for ").append(unique.size()).append(" unique leads...\n");

        int i = 0, total = unique.size();
        int consecutiveFailures = 0;

        for (String lead : unique) {
            i++;
            if (i % 10 == 0 || i == total) {
                log.append("ESP lookup progress: ").append(i).append("/").append(total).append("\n");
            }

            Integer code = getEspCodeForLeadWithEnhancedRetry(lead, rateLimitTracker, log);

            if (code != null && code == -999) {
                // Special code for "Not Found" - don't put in main map
                out.put(lead + "_NOT_FOUND", code);
            } else {
                out.put(lead, code); // null for network errors (Others), actual ESP codes for found leads
            }

            if (code == null) {
                consecutiveFailures++;
                if (consecutiveFailures >= 5) {
                    log.append("Multiple ESP failures. Extended delay...\n");
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

    // Apply ESP codes and handle Not Found cases
    private void applyEspCodesToReplies(List<EmailRow> allReplies, Map<String, Integer> espByLead, StringBuilder log) {
        int notFoundCount = 0;
        int googleCount = 0;
        int microsoftCount = 0;
        int othersCount = 0;

        for (EmailRow r : allReplies) {
            // Check if this email was marked as "Not Found"
            if (espByLead.containsKey(r.leadEmail + "_NOT_FOUND")) {
                r.isNotFound = true;
                notFoundCount++;
            } else {
                Integer espCode = espByLead.get(r.leadEmail);
                r.espCode = espCode;

                if (espCode != null) {
                    if (espCode == CODE_GOOGLE) googleCount++;
                    else if (espCode == CODE_MICROSOFT) microsoftCount++;
                    else othersCount++;
                } else {
                    othersCount++; // Network errors go to Others
                }
            }
        }

        log.append("ESP application summary:\n");
        log.append("  Google: ").append(googleCount).append("\n");
        log.append("  Microsoft: ").append(microsoftCount).append("\n");
        log.append("  Others: ").append(othersCount).append("\n");
        log.append("  Not Found (deleted): ").append(notFoundCount).append("\n");
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

    // ESP lookup method with status-based Not Found detection
    private Integer getEspCodeForLead(String leadEmail) {
        // Add null safety
        if (leadEmail == null || leadEmail.trim().isEmpty()) {
            System.err.println("Lead email is null or empty");
            return null;
        }

        try {
            // Sanitize the email for JSON
            String sanitizedEmail = leadEmail.trim().replace("\"", "\\\"").replace("\n", "").replace("\r", "");

            String body = "{\n" +
                    "  \"limit\": 10,\n" +
                    "  \"page_trail\": null,\n" +
                    "  \"with_campaign_name\": true,\n" +
                    "  \"with_list_name\": true,\n" +
                    "  \"search\": \"" + sanitizedEmail + "\",\n" +
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

            // Network/server errors - treat as "Others" (not "Not Found")
            if (resp.getStatusCode() != 200) {
                System.err.println("ESP API returned status: " + resp.getStatusCode() + " for email: " + leadEmail);
                return null; // Will be classified as "Others"
            }

            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(resp.getBody().asString());
                JsonNode items = root.get("items");

                // Status 200 but empty items array = lead deleted/not found
                if (items == null || !items.isArray() || items.size() == 0) {
                    return -999; // Special code for "Not Found" (deleted leads)
                }

                JsonNode lead = items.get(0);
                JsonNode codeNode = lead.get("esp_code");
                if (codeNode != null && !codeNode.isNull()) {
                    return codeNode.asInt();
                }
            } catch (Exception e) {
                System.err.println("Error parsing ESP response for email: " + leadEmail + " - " + e.getMessage());
                return null;
            }

            return null; // Parsing errors = "Others"

        } catch (Exception e) {
            System.err.println("Unexpected error in ESP lookup for email: " + leadEmail + " - " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void processCampaignStatistics(List<EmailRow> allReplies, StringBuilder log) {
        // Group replies by campaign
        Map<String, List<EmailRow>> repliesByCampaign = new HashMap<>();
        for (EmailRow reply : allReplies) {
            repliesByCampaign.computeIfAbsent(reply.campaignName, k -> new ArrayList<>()).add(reply);
        }

        // Process each campaign
        for (Map.Entry<String, List<EmailRow>> entry : repliesByCampaign.entrySet()) {
            String campaignName = entry.getKey();
            List<EmailRow> campaignReplies = entry.getValue();

            CampaignStats stats = new CampaignStats(campaignName);

            for (EmailRow reply : campaignReplies) {
                stats.totalReplies++;
                overallStats.totalReplies++;

                if (reply.isNotFound) {
                    stats.notFoundReplies++;
                    overallStats.notFoundReplies++;
                } else if (reply.espCode != null) {
                    if (reply.espCode == CODE_GOOGLE) {
                        stats.googleReplies++;
                        overallStats.googleReplies++;
                    } else if (reply.espCode == CODE_MICROSOFT) {
                        stats.microsoftReplies++;
                        overallStats.microsoftReplies++;
                    } else {
                        stats.othersReplies++;
                        overallStats.othersReplies++;
                    }
                } else {
                    stats.othersReplies++;
                    overallStats.othersReplies++;
                }
            }

            campaignStatsMap.put(campaignName, stats);
        }

        log.append("OVERALL ANALYSIS SUMMARY:\n");
        log.append("Total campaigns processed: ").append(campaignStatsMap.size()).append("\n");
        log.append("Total replies: ").append(overallStats.totalReplies).append("\n");
        log.append("ESP breakdown:\n");
        log.append("  Google: ").append(overallStats.googleReplies).append("\n");
        log.append("  Microsoft: ").append(overallStats.microsoftReplies).append("\n");
        log.append("  Others: ").append(overallStats.othersReplies).append("\n");
        log.append("  Not Found: ").append(overallStats.notFoundReplies).append("\n\n");
    }

    private LocalDate parseDateFromCampaignName(String campaignName, int year) {
        Pattern datePattern = Pattern.compile("(\\d{1,2})_(Jan|Feb|Mar|April|May|June|July|Aug|Sep|Oct|Nov|Dec)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = datePattern.matcher(campaignName);

        if (matcher.find()) {
            try {
                int day = Integer.parseInt(matcher.group(1));
                String monthStr = normalizeMonthString(matcher.group(2));
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
        return switch (normalized) {
            case "january" -> "Jan"; case "february" -> "Feb"; case "march" -> "Mar";
            case "april" -> "April"; case "may" -> "May"; case "june" -> "June";
            case "july" -> "July"; case "august" -> "Aug"; case "september" -> "Sep";
            case "october" -> "Oct"; case "november" -> "Nov"; case "december" -> "Dec";
            default -> monthStr.substring(0, 1).toUpperCase() + monthStr.substring(1).toLowerCase();
        };
    }

    private void safeSleep(long millis, StringBuilder log) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void writeExcel(String fromDate, String toDate, List<EmailRow> rows, StringBuilder log) {
        // Count ESP buckets including Not Found
        int google = 0, microsoft = 0, others = 0, notFound = 0;
        for (EmailRow r : rows) {
            if (r.isNotFound) {
                notFound++;
                continue;
            }
            Integer c = r.espCode;
            if (c == null) {
                others++;
                continue;
            }
            if (c == CODE_GOOGLE) google++;
            else if (c == CODE_MICROSOFT) microsoft++;
            else others++;
        }

        log.append("\nCampaign Replies ESP Breakdown:\n");
        log.append("Google: ").append(google).append("\n");
        log.append("Microsoft: ").append(microsoft).append("\n");
        log.append("Others: ").append(others).append("\n");
        log.append("Not Found (deleted leads): ").append(notFound).append("\n");
        log.append("Total Campaign Replies: ").append(rows.size()).append("\n\n");

        try (Workbook workbook = new XSSFWorkbook()) {
            createCampaignReportSheet(workbook, fromDate, toDate, rows.size(), google, microsoft, others, notFound);
            createCampaignBreakdownSheet(workbook, fromDate, toDate);

            try (FileOutputStream fileOut = new FileOutputStream(EXCEL_FILE_PATH)) {
                workbook.write(fileOut);
            }
            log.append("Excel file saved: ").append(EXCEL_FILE_PATH).append("\n");
        } catch (IOException e) {
            log.append("Error writing Excel file: ").append(e.getMessage()).append("\n");
            throw new RuntimeException("Error creating Excel file", e);
        }
    }

    private void createCampaignReportSheet(Workbook workbook, String fromDate, String toDate,
                                           int totalReplies, int googleCount, int microsoftCount,
                                           int othersCount, int notFoundCount) {
        Sheet sheet = workbook.createSheet("Campaign Replies Report");

        CellStyle header = headerStyle(workbook, IndexedColors.LIGHT_BLUE.getIndex());
        CellStyle data = borderStyle(workbook);

        Row h = sheet.createRow(0);
        String[] cols = {"Date", "Total Replies", "Google", "Microsoft", "Others", "Not Found"};
        for (int i = 0; i < cols.length; i++) {
            Cell c = h.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(header);
        }

        Row r = sheet.createRow(1);
        r.createCell(0).setCellValue(fromDate.equals(toDate) ? fromDate : (fromDate + " to " + toDate));
        r.createCell(1).setCellValue(totalReplies);
        r.createCell(2).setCellValue(googleCount);
        r.createCell(3).setCellValue(microsoftCount);
        r.createCell(4).setCellValue(othersCount);
        r.createCell(5).setCellValue(notFoundCount);

        for (int i = 0; i <= 5; i++) {
            r.getCell(i).setCellStyle(data);
            sheet.autoSizeColumn(i);
            if (sheet.getColumnWidth(i) < 4000) sheet.setColumnWidth(i, 4000);
        }
    }

    private void createCampaignBreakdownSheet(Workbook workbook, String fromDate, String toDate) {
        Sheet sheet = workbook.createSheet("Campaign Breakdown");

        CellStyle header = headerStyle(workbook, IndexedColors.ORANGE.getIndex());
        CellStyle data = borderStyle(workbook);

        // Create title
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Campaign-wise Replies Analysis (" + fromDate + " to " + toDate + ")");
        titleCell.setCellStyle(header);

        // Create headers
        Row headerRow = sheet.createRow(2);
        String[] headers = {"Campaign Name", "Google", "Microsoft", "Others", "Not Found"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(header);
        }

        // Fill data rows
        int rowIndex = 3;
        for (CampaignStats stats : campaignStatsMap.values()) {
            Row dataRow = sheet.createRow(rowIndex++);

            dataRow.createCell(0).setCellValue(stats.campaignName);
            dataRow.createCell(1).setCellValue(stats.googleReplies);
            dataRow.createCell(2).setCellValue(stats.microsoftReplies);
            dataRow.createCell(3).setCellValue(stats.othersReplies);
            dataRow.createCell(4).setCellValue(stats.notFoundReplies);

            // Apply styles
            for (int i = 0; i < 5; i++) {
                dataRow.getCell(i).setCellStyle(data);
            }
        }

        // Add total row
        Row totalRow = sheet.createRow(rowIndex + 1);
        Cell totalLabelCell = totalRow.createCell(0);
        totalLabelCell.setCellValue("TOTAL");
        totalLabelCell.setCellStyle(header);

        totalRow.createCell(1).setCellValue(overallStats.googleReplies);
        totalRow.createCell(2).setCellValue(overallStats.microsoftReplies);
        totalRow.createCell(3).setCellValue(overallStats.othersReplies);
        totalRow.createCell(4).setCellValue(overallStats.notFoundReplies);

        // Apply styles to total row
        for (int i = 0; i < 5; i++) {
            totalRow.getCell(i).setCellStyle(header);
        }

        // Auto-size columns
        sheet.setColumnWidth(0, 15000); // Campaign name - wider
        for (int i = 1; i < 5; i++) {
            sheet.autoSizeColumn(i);
            if (sheet.getColumnWidth(i) < 3500) {
                sheet.setColumnWidth(i, 3500);
            }
        }
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

    private static void addBorder(CellStyle cs) {
        cs.setBorderBottom(BorderStyle.THIN);
        cs.setBorderTop(BorderStyle.THIN);
        cs.setBorderLeft(BorderStyle.THIN);
        cs.setBorderRight(BorderStyle.THIN);
    }

    public File getLatestCampaignRepliesExcelFile() {
        File file = new File(EXCEL_FILE_PATH);
        return file.exists() ? file : null;
    }
    /**
     * Enhanced streaming method - ADD this alongside your existing method
     */
    public void analyzeCampaignRepliesWithStreaming(String fromDateStr, String toDateStr,
                                                    OutputStream outputStream, String sessionId) {
        System.out.println("=== SERVICE METHOD CALLED ===");
        System.out.println("fromDateStr: " + fromDateStr);
        System.out.println("toDateStr: " + toDateStr);
        System.out.println("sessionId: " + sessionId);
        System.out.println("outputStream: " + (outputStream != null ? "NOT_NULL" : "NULL"));
        try {
            long analysisStartTime = System.currentTimeMillis();

            if (!sendSSEMessage(outputStream, "status", "Analysis STARTED for date range: " + fromDateStr + " to " + toDateStr)) {
                System.err.println("Failed to send initial SSE message, aborting analysis");
                return;
            }

            if (!sendSSEMessage(outputStream, "phase", "PHASE 1: Campaign Discovery")) {
                System.err.println("SSE connection lost during phase update");
                return;
            }

            LocalDate fromDate = LocalDate.parse(fromDateStr, DATE_FORMATTER);
            LocalDate toDate = LocalDate.parse(toDateStr, DATE_FORMATTER);

            // Clear previous data
            campaignStatsMap.clear();
            overallStats = new OverallStats();

            RateLimitTracker rateLimitTracker = new RateLimitTracker();

            // PHASE 1: Campaign Discovery
            if (!sendSSEMessage(outputStream, "progress", "Discovering campaigns in date range...")) {
                System.err.println("SSE connection lost during campaign discovery");
                return;
            }

            List<Campaign> campaigns = null;
            try {
                campaigns = fetchCampaignsWithNameBasedFiltering(fromDate, toDate, new StringBuilder());
            } catch (Exception e) {
                System.err.println("Error in campaign discovery: " + e.getMessage());
                e.printStackTrace();
                sendSSEMessage(outputStream, "error", "Campaign discovery failed: " + e.getMessage());
                return;
            }

            if (campaigns == null || campaigns.isEmpty()) {
                sendSSEMessage(outputStream, "error", "No campaigns found matching the date criteria");
                return;
            }

            if (!sendSSEMessage(outputStream, "milestone", "PHASE 1 COMPLETE: Found " + campaigns.size() + " campaigns")) {
                return;
            }

            // PHASE 2: Fetch replies with progress updates
            if (!sendSSEMessage(outputStream, "phase", "PHASE 2: Replies Collection")) {
                return;
            }

            List<EmailRow> allReplies = null;
            try {
                allReplies = fetchRepliesWithProgressUpdates(campaigns, rateLimitTracker, outputStream);
            } catch (Exception e) {
                System.err.println("Error in replies collection: " + e.getMessage());
                e.printStackTrace();
                sendSSEMessage(outputStream, "error", "Replies collection failed: " + e.getMessage());
                return;
            }

            if (!sendSSEMessage(outputStream, "milestone", "PHASE 2 COMPLETE: Collected " + (allReplies != null ? allReplies.size() : 0) + " replies")) {
                return;
            }

            if (allReplies == null || allReplies.isEmpty()) {
                sendSSEMessage(outputStream, "error", "No campaign replies found in the given date range");
                writeExcel(fromDateStr, toDateStr, new ArrayList<>(), new StringBuilder());
                return;
            }

            // PHASE 3: ESP lookup with progress
            if (!sendSSEMessage(outputStream, "phase", "PHASE 3: ESP Code Lookup")) {
                return;
            }

            Map<String, Integer> espByLead = null;
            try {
                espByLead = fetchEspCodesWithProgress(allReplies, rateLimitTracker, outputStream);
            } catch (Exception e) {
                System.err.println("Error in ESP lookup: " + e.getMessage());
                e.printStackTrace();
                sendSSEMessage(outputStream, "error", "ESP lookup failed: " + e.getMessage());
                return;
            }

            // Apply ESP codes
            if (!sendSSEMessage(outputStream, "progress", "Applying ESP codes to replies...")) {
                return;
            }

            try {
                applyEspCodesToReplies(allReplies, espByLead, new StringBuilder());
            } catch (Exception e) {
                System.err.println("Error applying ESP codes: " + e.getMessage());
                e.printStackTrace();
                sendSSEMessage(outputStream, "error", "Failed to apply ESP codes: " + e.getMessage());
                return;
            }

            // PHASE 4: Final processing
            if (!sendSSEMessage(outputStream, "phase", "PHASE 4: Final Processing")) {
                return;
            }

            try {
                processCampaignStatistics(allReplies, new StringBuilder());

                if (!sendSSEMessage(outputStream, "progress", "Generating Excel report...")) {
                    return;
                }

                writeExcel(fromDateStr, toDateStr, allReplies, new StringBuilder());

                if (!sendSSEMessage(outputStream, "progress", "Excel report completed")) {
                    return;
                }

                long totalAnalysisTime = System.currentTimeMillis() - analysisStartTime;
                sendSSEMessage(outputStream, "complete", "ANALYSIS COMPLETED in " + formatTime(totalAnalysisTime));

            } catch (Exception e) {
                System.err.println("Error in final processing: " + e.getMessage());
                e.printStackTrace();
                sendSSEMessage(outputStream, "error", "Final processing failed: " + e.getMessage());
            }

        }  catch (Exception e) {
            System.err.println("=== SERVICE METHOD EXCEPTION ===");
            System.err.println("Exception in analyzeCampaignRepliesWithStreaming: " + e.getClass().getName());
            System.err.println("Message: " + e.getMessage());
            e.printStackTrace();
            throw e; // Re-throw to be caught by controller
        }
    }
    private Map<String, Integer> fetchEspCodesWithProgress(List<EmailRow> emailRows, RateLimitTracker rateLimitTracker,
                                                           OutputStream outputStream) {
        Map<String, Integer> out = new HashMap<>();
        Set<String> unique = new HashSet<>();

        // Safely extract unique emails
        if (emailRows != null) {
            for (EmailRow r : emailRows) {
                if (r != null && r.leadEmail != null && !r.leadEmail.trim().isEmpty()) {
                    unique.add(r.leadEmail.trim());
                }
            }
        }

        if (unique.isEmpty()) {
            if (!sendSSEMessage(outputStream, "warning", "No valid email addresses found for ESP lookup")) {
                System.err.println("Failed to send warning message about empty email list");
            }
            return out;
        }

        if (!sendSSEMessage(outputStream, "progress", "Getting ESP data for " + unique.size() + " unique leads...")) {
            System.err.println("Failed to send initial ESP progress message");
            return out; // Return empty map rather than null
        }

        int i = 0;
        int total = unique.size();
        int consecutiveFailures = 0;
        int googleCount = 0, microsoftCount = 0, othersCount = 0, notFoundCount = 0;

        for (String lead : unique) {
            if (lead == null || lead.trim().isEmpty()) {
                continue; // Skip invalid emails
            }

            i++;

            try {
                // Progress updates every 10 ESP lookups
                if (i % 10 == 0 || i == total || i == 1) {
                    double progress = (i * 100.0) / total;
                    String progressMsg = String.format("ESP lookup progress: %d/%d (%.1f%%) - G:%d M:%d O:%d NF:%d",
                            i, total, progress, googleCount, microsoftCount, othersCount, notFoundCount);
                    if (!sendSSEMessage(outputStream, "progress", progressMsg)) {
                        System.err.println("SSE connection lost during ESP progress update");
                        break; // Stop processing if we can't send updates
                    }
                }

                // Safe ESP lookup with enhanced error handling
                Integer code = null;
                try {
                    code = getEspCodeForLeadWithEnhancedRetry(lead, rateLimitTracker, new StringBuilder());
                } catch (Exception e) {
                    System.err.println("Exception during ESP lookup for " + lead + ": " + e.getMessage());
                    code = null; // Will be counted as "Others"
                }

                if (code != null && code == -999) {
                    out.put(lead + "_NOT_FOUND", code);
                    notFoundCount++;
                } else {
                    out.put(lead, code);
                    if (code != null) {
                        if (code == CODE_GOOGLE) googleCount++;
                        else if (code == CODE_MICROSOFT) microsoftCount++;
                        else othersCount++;
                    } else {
                        othersCount++;
                    }
                }

                if (code == null) {
                    consecutiveFailures++;
                    if (consecutiveFailures >= 5) {
                        if (!sendSSEMessage(outputStream, "warning", "Multiple ESP failures. Extended delay...")) {
                            System.err.println("Failed to send ESP failure warning");
                        }
                        safeSleep(ESP_LOOKUP_DELAY_MS * 3, new StringBuilder());
                        consecutiveFailures = 0;
                    }
                } else {
                    consecutiveFailures = 0;
                }

                // Use your existing delay
                long delay = rateLimitTracker.hasHitRateLimit() ? ESP_LOOKUP_DELAY_MS * 2 : ESP_LOOKUP_DELAY_MS;
                safeSleep(delay, new StringBuilder());

            } catch (Exception e) {
                System.err.println("Unexpected error during ESP lookup for " + lead + ": " + e.getMessage());
                e.printStackTrace();

                if (!sendSSEMessage(outputStream, "warning", "ESP lookup error for lead: " + e.getClass().getSimpleName())) {
                    System.err.println("Failed to send ESP error warning");
                }
                othersCount++; // Count as Others if lookup fails
                continue;
            }
        }

        String finalStats = String.format("ESP lookup completed - Google:%d Microsoft:%d Others:%d NotFound:%d",
                googleCount, microsoftCount, othersCount, notFoundCount);
        if (!sendSSEMessage(outputStream, "progress", finalStats)) {
            System.err.println("Failed to send final ESP stats");
        }

        return out;
    }

    private List<EmailRow> fetchRepliesWithProgressUpdates(List<Campaign> campaigns, RateLimitTracker rateLimitTracker,
                                                           OutputStream outputStream) throws IOException {
        List<EmailRow> allReplies = new ArrayList<>();
        int totalCampaigns = campaigns.size();

        sendSSEMessage(outputStream, "progress", "Starting replies fetch for " + totalCampaigns + " campaigns...");

        for (int i = 0; i < campaigns.size(); i++) {
            Campaign campaign = campaigns.get(i);

            try {
                String campaignName = campaign.name.length() > 60 ?
                        campaign.name.substring(0, 60) + "..." : campaign.name;

                sendSSEMessage(outputStream, "progress", "Processing campaign " + (i + 1) + "/" + totalCampaigns + ": " + campaignName);

                // Call your existing method
                List<EmailRow> campaignReplies = fetchRepliesForCampaign(campaign, rateLimitTracker, new StringBuilder());
                allReplies.addAll(campaignReplies);

                sendSSEMessage(outputStream, "progress", "Campaign completed: " + campaignReplies.size() + " replies | Total: " + allReplies.size());

            } catch (Exception e) {
                sendSSEMessage(outputStream, "warning", "Error processing campaign " + (i + 1) + ": " + e.getMessage() + " - Continuing...");
                continue; // Skip this campaign and continue
            }

            // Rate limiting delay between campaigns
            if (i < campaigns.size() - 1) {
                safeSleep(BATCH_DELAY_MS, new StringBuilder());
            }
        }

        return allReplies;
    }

    /**
     * Enhanced version of your existing campaign fetch with streaming
     */
    private List<Campaign> fetchCampaignsWithStreamingUpdates(LocalDate fromDate, LocalDate toDate,
                                                              OutputStream outputStream, RateLimitTracker rateLimitTracker) throws IOException {
        List<Campaign> matchingCampaigns = new ArrayList<>();
        int skip = 0;
        int limit = 50;
        int batch = 1;
        boolean foundAnyMatching = false;

        sendSSEMessage(outputStream, "progress", "Starting campaign discovery...");

        while (true) {
            sendSSEMessage(outputStream, "progress", "Fetching campaigns batch " + batch + " (skip: " + skip + ")");

            // Use your existing campaign API call logic here
            String requestBody = "{\n" +
                    "  \"limit\": " + limit + ",\n" +
                    "  \"skip\": " + skip + ",\n" +
                    "  \"search\": \"\",\n" +
                    "  \"status\": null,\n" +
                    "  \"include_tags\": false,\n" +
                    "  \"tag\": null,\n" +
                    "  \"sortColumn\": \"timestamp_created\",\n" +
                    "  \"sortOrder\": \"desc\"\n" +
                    "}";

            Response response = RestAssured.given()
                    .baseUri(BASE_URL)
                    .header("X-org-auth", API_KEY)
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .when().post("/backend-alt/api/v1/campaign/list")
                    .then().extract().response();

            if (response.getStatusCode() != 200) {
                sendSSEMessage(outputStream, "warning", "Campaign API failed with status: " + response.getStatusCode());
                if (response.getStatusCode() == 429) {
                    rateLimitTracker.recordRateLimitHit();
                    sendSSEMessage(outputStream, "warning", "Rate limit hit. Waiting 5 seconds...");
                    safeSleep(5000, new StringBuilder()); // Use your existing safeSleep
                    continue;
                }
                break;
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(response.getBody().asString());

            if (!rootNode.isArray() || rootNode.size() == 0) {
                sendSSEMessage(outputStream, "progress", "No more campaigns to fetch");
                break;
            }

            boolean foundInBatch = false;
            boolean reachedEarlierThanTarget = false;
            int matchedInBatch = 0;

            // Use your existing campaign processing logic
            for (JsonNode campaignNode : rootNode) {
                String timestamp = asText(campaignNode, "timestamp_created");
                String name = asText(campaignNode, "name");
                String id = asText(campaignNode, "id");
                JsonNode statusNode = campaignNode.get("status");

                if (timestamp == null || name == null || id == null) continue;

                int status = statusNode != null && !statusNode.isNull() ? statusNode.asInt() : -1;
                if (status == 0) continue;

                int year;
                LocalDate timestampDate;
                try {
                    Instant instant = Instant.from(ISO_FORMATTER.parse(timestamp));
                    timestampDate = instant.atZone(ZoneOffset.UTC).toLocalDate();
                    year = timestampDate.getYear();
                } catch (Exception e) {
                    continue;
                }

                LocalDate campaignDate = parseDateFromCampaignName(name, year);

                if (campaignDate != null) {
                    if (!campaignDate.isBefore(fromDate) && !campaignDate.isAfter(toDate)) {
                        matchingCampaigns.add(new Campaign(id, name, timestampDate, campaignDate, year));
                        foundInBatch = true;
                        foundAnyMatching = true;
                        matchedInBatch++;
                    }

                    if (campaignDate.isBefore(fromDate.minusMonths(2))) {
                        reachedEarlierThanTarget = true;
                    }
                }
            }

            sendSSEMessage(outputStream, "progress", "Batch " + batch + " processed: " + matchedInBatch + " matched, total: " + matchingCampaigns.size());

            if (foundAnyMatching && reachedEarlierThanTarget) {
                sendSSEMessage(outputStream, "progress", "Stopping search - reached much earlier campaigns");
                break;
            }

            if (rootNode.size() < limit) {
                sendSSEMessage(outputStream, "progress", "Received less than limit, no more data");
                break;
            }

            skip += limit;
            batch++;
            safeSleep(BATCH_DELAY_MS, new StringBuilder());
        }

        return matchingCampaigns;
    }
    private List<EmailRow> fetchRepliesForAllCampaignsWithStreaming(List<Campaign> campaigns,
                                                                    RateLimitTracker rateLimitTracker,
                                                                    OutputStream outputStream) throws IOException {
        List<EmailRow> allReplies = new ArrayList<>();
        int totalCampaigns = campaigns.size();

        sendSSEMessage(outputStream, "progress", "Starting replies fetch for " + totalCampaigns + " campaigns...");

        for (int i = 0; i < campaigns.size(); i++) {
            Campaign campaign = campaigns.get(i);

            try {
                String campaignName = campaign.name.length() > 60 ?
                        campaign.name.substring(0, 60) + "..." : campaign.name;

                sendSSEMessage(outputStream, "progress", "Processing campaign " + (i + 1) + "/" + totalCampaigns + ": " + campaignName);

                // Use your existing method but with better error handling
                List<EmailRow> campaignReplies = fetchRepliesForCampaignSafe(campaign, rateLimitTracker, outputStream);
                allReplies.addAll(campaignReplies);

                sendSSEMessage(outputStream, "progress", "Campaign completed: " + campaignReplies.size() + " replies | Total: " + allReplies.size());

            } catch (Exception e) {
                sendSSEMessage(outputStream, "warning", "Error processing campaign " + (i + 1) + ": " + e.getMessage() + " - Continuing with next campaign");
                continue; // Skip this campaign and continue with others
            }


            // Rate limiting delay between campaigns
            if (i < campaigns.size() - 1) {
                safeSleep(BATCH_DELAY_MS, new StringBuilder());
            }
        }

        return allReplies;
    }
    private List<EmailRow> fetchRepliesForCampaignSafe(Campaign campaign, RateLimitTracker rateLimitTracker,
                                                       OutputStream outputStream) throws IOException {
        try {
            // Call your existing method but capture any errors
            return fetchRepliesForCampaign(campaign, rateLimitTracker, new StringBuilder());
        } catch (Exception e) {
            sendSSEMessage(outputStream, "warning", "Error in campaign " + campaign.name + ": " + e.getMessage());
            return new ArrayList<>(); // Return empty list to continue processing
        }
    }
    private Map<String, Integer> fetchEspCodesWithEnhancedRateLimitAndStreaming(List<EmailRow> emailRows,
                                                                                RateLimitTracker rateLimitTracker,
                                                                                OutputStream outputStream) throws IOException {
        Map<String, Integer> out = new HashMap<>();
        Set<String> unique = new HashSet<>();

        for (EmailRow r : emailRows) {
            if (r.leadEmail != null && !r.leadEmail.isBlank()) {
                unique.add(r.leadEmail);
            }
        }

        sendSSEMessage(outputStream, "progress", "Getting ESP data for " + unique.size() + " unique leads...");

        int i = 0;
        int total = unique.size();
        int consecutiveFailures = 0;
        int googleCount = 0, microsoftCount = 0, othersCount = 0, notFoundCount = 0;

        for (String lead : unique) {
            i++;

            try {
                // Progress updates every 10 ESP lookups or at milestones
                if (i % 10 == 0 || i == total || i == 1) {
                    double progress = (i * 100.0) / total;
                    String progressMsg = String.format("ESP lookup progress: %d/%d (%.1f%%) - G:%d M:%d O:%d NF:%d",
                            i, total, progress, googleCount, microsoftCount, othersCount, notFoundCount);
                    sendSSEMessage(outputStream, "progress", progressMsg);
                }

                Integer code = getEspCodeForLeadWithEnhancedRetry(lead, rateLimitTracker, new StringBuilder());

                if (code != null && code == -999) {
                    out.put(lead + "_NOT_FOUND", code);
                    notFoundCount++;
                } else {
                    out.put(lead, code);
                    if (code != null) {
                        if (code == CODE_GOOGLE) googleCount++;
                        else if (code == CODE_MICROSOFT) microsoftCount++;
                        else othersCount++;
                    } else {
                        othersCount++;
                    }
                }

                if (code == null) {
                    consecutiveFailures++;
                    if (consecutiveFailures >= 5) {
                        sendSSEMessage(outputStream, "warning", "Multiple ESP failures. Extended delay...");
                        safeSleep(ESP_LOOKUP_DELAY_MS * 3, new StringBuilder());
                        consecutiveFailures = 0;
                    }
                } else {
                    consecutiveFailures = 0;
                }

                // Reduced delay for better performance
                long delay = rateLimitTracker.hasHitRateLimit() ? ESP_LOOKUP_DELAY_MS * 2 : ESP_LOOKUP_DELAY_MS;
                safeSleep(delay, new StringBuilder());

            } catch (Exception e) {
                sendSSEMessage(outputStream, "warning", "ESP lookup error for lead: " + e.getMessage());
                othersCount++; // Count as Others if lookup fails
                continue;
            }
        }

        String finalStats = String.format("ESP lookup completed - Google:%d Microsoft:%d Others:%d NotFound:%d",
                googleCount, microsoftCount, othersCount, notFoundCount);
        sendSSEMessage(outputStream, "progress", finalStats);

        return out;
    }
    /**
     * Enhanced version of replies collection with streaming updates
     */
    private List<EmailRow> fetchRepliesWithStreamingUpdates(List<Campaign> campaigns, RateLimitTracker rateLimitTracker,
                                                            OutputStream outputStream) throws IOException {
        List<EmailRow> allReplies = new ArrayList<>();

        sendSSEMessage(outputStream, "progress", "Starting replies fetch for " + campaigns.size() + " campaigns...");

        for (int i = 0; i < campaigns.size(); i++) {
            Campaign campaign = campaigns.get(i);

            sendSSEMessage(outputStream, "progress", "Processing campaign " + (i + 1) + "/" + campaigns.size() + ": " +
                    campaign.name.substring(0, Math.min(50, campaign.name.length())) + "...");

            // Use your existing fetchRepliesForCampaign method
            List<EmailRow> campaignReplies = fetchRepliesForCampaign(campaign, rateLimitTracker, new StringBuilder());
            allReplies.addAll(campaignReplies);

            sendSSEMessage(outputStream, "progress", "Campaign completed: " + campaignReplies.size() + " replies | Total: " + allReplies.size());

            // Rate limiting delay between campaigns
            if (i < campaigns.size() - 1) {
                safeSleep(BATCH_DELAY_MS, new StringBuilder());
            }
        }

        return allReplies;
    }

    /**
     * Enhanced ESP lookup with streaming progress
     */
    private Map<String, Integer> fetchEspCodesWithStreaming(List<EmailRow> emailRows, RateLimitTracker rateLimitTracker,
                                                            OutputStream outputStream) throws IOException {
        Map<String, Integer> out = new HashMap<>();
        Set<String> unique = new HashSet<>();

        for (EmailRow r : emailRows) {
            if (r.leadEmail != null && !r.leadEmail.isBlank()) {
                unique.add(r.leadEmail);
            }
        }

        sendSSEMessage(outputStream, "progress", "Starting ESP lookup for " + unique.size() + " unique leads...");

        int i = 0;
        int total = unique.size();

        for (String lead : unique) {
            i++;

            // Progress updates every 10 lookups
            if (i % 10 == 0 || i == total) {
                double progress = (i * 100.0) / total;
                sendSSEMessage(outputStream, "progress",
                        String.format("ESP progress: %d/%d (%.1f%%)", i, total, progress));
            }

            // Use your existing ESP lookup method
            Integer code = getEspCodeForLeadWithEnhancedRetry(lead, rateLimitTracker, new StringBuilder());

            if (code != null && code == -999) {
                out.put(lead + "_NOT_FOUND", code);
            } else {
                out.put(lead, code);
            }

            // Reduced delay for better performance
            safeSleep(ESP_LOOKUP_DELAY_MS / 2, new StringBuilder()); // Half the original delay
        }

        sendSSEMessage(outputStream, "progress", "ESP lookup completed for " + i + " leads");
        return out;
    }

    /**
     * Apply ESP codes with streaming updates
     */
    private void applyEspCodesToRepliesWithStreaming(List<EmailRow> allReplies, Map<String, Integer> espByLead,
                                                     OutputStream outputStream) throws IOException {
        int notFoundCount = 0;
        int googleCount = 0;
        int microsoftCount = 0;
        int othersCount = 0;

        for (EmailRow r : allReplies) {
            if (espByLead.containsKey(r.leadEmail + "_NOT_FOUND")) {
                r.isNotFound = true;
                notFoundCount++;
            } else {
                Integer espCode = espByLead.get(r.leadEmail);
                r.espCode = espCode;

                if (espCode != null) {
                    if (espCode == CODE_GOOGLE) googleCount++;
                    else if (espCode == CODE_MICROSOFT) microsoftCount++;
                    else othersCount++;
                } else {
                    othersCount++;
                }
            }
        }

        String summary = String.format("ESP Summary - Google: %d, Microsoft: %d, Others: %d, Not Found: %d",
                googleCount, microsoftCount, othersCount, notFoundCount);
        sendSSEMessage(outputStream, "progress", summary);
    }

    /**
     * Process statistics with streaming updates
     */
    private void processCampaignStatisticsWithStreaming(List<EmailRow> allReplies, OutputStream outputStream) throws IOException {
        sendSSEMessage(outputStream, "progress", "Processing campaign statistics...");

        // Use your existing processCampaignStatistics logic here
        processCampaignStatistics(allReplies, new StringBuilder());

        sendSSEMessage(outputStream, "progress", "Campaign statistics completed");
    }

    /**
     * Utility method for SSE messages
     */

// Enhanced SSE message sending with better error handling
    private boolean sendSSEMessage(OutputStream outputStream, String event, String data) {
        if (outputStream == null) {
            System.err.println("SSE OutputStream is null");
            return false;
        }

        if (event == null) event = "message";
        if (data == null) data = "null";

        try {
            String message = "event: " + event + "\ndata: " + data + "\n\n";
            outputStream.write(message.getBytes("UTF-8"));
            outputStream.flush();
            return true;
        } catch (IOException e) {
            System.err.println("SSE connection lost: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Unexpected SSE error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Format time utility
     */
    private String formatTime(long millis) {
        if (millis < 60000) {
            return String.format("%.1fs", millis / 1000.0);
        } else {
            long minutes = millis / 60000;
            long seconds = (millis % 60000) / 1000;
            return String.format("%dm %ds", minutes, seconds);
        }
    }

    /**
     * Cleanup method
     */
    @PreDestroy
    public void cleanup() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}