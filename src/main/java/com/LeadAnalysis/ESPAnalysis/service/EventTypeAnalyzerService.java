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
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
//@Service
//public class EventTypeAnalyzerService {
//
//    // ---- CONFIG ----
//    private static final String BASE_URL = API.BASE_URL;
//    private static final String API_KEY = API.API_KEY;
//    private static final String EVENT_EXCEL_FILE_PATH = System.getProperty("java.io.tmpdir") + "event_type_analysis_report.xlsx";
//
//    // Rate limiting
//    private static final long CAMPAIGN_DELAY_MS = 300;
//    private static final long ACTIVITY_DELAY_MS = 150;
//
//    // Date formats
//    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
//    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]'Z'")
//            .withZone(ZoneOffset.UTC);
//
//    // Instance variables to store data for Excel export
//    private Map<String, Integer> campaignActivityCounts = new HashMap<>();
//    private Set<Integer> allDiscoveredEventTypes = new TreeSet<>(); // Global event types tracker
//
//    // Month mapping for campaign name parsing
//    private static final Map<String, Integer> MONTH_MAP = new HashMap<>();
//    static {
//        MONTH_MAP.put("Jan", 1);
//        MONTH_MAP.put("Feb", 2);
//        MONTH_MAP.put("Mar", 3);
//        MONTH_MAP.put("April", 4);
//        MONTH_MAP.put("May", 5);
//        MONTH_MAP.put("June", 6);
//        MONTH_MAP.put("July", 7);
//        MONTH_MAP.put("Aug", 8);
//        MONTH_MAP.put("Sep", 9);
//        MONTH_MAP.put("Oct", 10);
//        MONTH_MAP.put("Nov", 11);
//        MONTH_MAP.put("Dec", 12);
//    }
//
//    // Data classes
//    private static class Campaign {
//        String id;
//        String name;
//        LocalDate timestampDate; // Date from timestamp_created
//        LocalDate campaignDate;  // Date parsed from campaign name
//        int year;
//
//        Campaign(String id, String name, LocalDate timestampDate, LocalDate campaignDate, int year) {
//            this.id = id;
//            this.name = name;
//            this.timestampDate = timestampDate;
//            this.campaignDate = campaignDate;
//            this.year = year;
//        }
//    }
//
//    private static class ActivityData {
//        String step;
//        int eventType;
//        String contact;
//        String timestamp;
//
//        ActivityData(String step, int eventType, String contact, String timestamp) {
//            this.step = step;
//            this.eventType = eventType;
//            this.contact = contact;
//            this.timestamp = timestamp;
//        }
//    }
//
//    public String analyzeEventTypeByDateRange(String fromDateStr, String toDateStr) {
//        try {
//            StringBuilder log = new StringBuilder();
//            log.append("🎯 Event Type Analysis for date range: ").append(fromDateStr).append(" to ").append(toDateStr).append("\n");
//            log.append("🚀 Starting Event Type Analysis with Name-Based Date Filtering...\n\n");
//
//            LocalDate fromDate = LocalDate.parse(fromDateStr, DATE_FORMATTER);
//            LocalDate toDate = LocalDate.parse(toDateStr, DATE_FORMATTER);
//
//            log.append("📅 Target Date Range: ").append(fromDate).append(" to ").append(toDate).append("\n");
//            log.append("🔍 Filtering Method: Campaign name date + timestamp year\n\n");
//
//            // Step 1: Fetch campaigns and filter by name-based date
//            List<Campaign> campaigns = fetchCampaignsWithNameBasedFiltering(fromDate, toDate, log);
//
//            if (campaigns.isEmpty()) {
//                log.append("❌ No campaigns found matching the date criteria.\n");
//                return log.toString();
//            }
//
//            log.append("✅ Found ").append(campaigns.size()).append(" campaigns matching date criteria\n\n");
//
//            // Step 2: Fetch activity data for all campaigns with detailed logging
//            Map<String, Map<Integer, Integer>> stepEventCounts = new HashMap<>();
//            Set<Integer> allEventTypes = new TreeSet<>();
//            Set<String> allSteps = new TreeSet<>();
//
//            int totalActivities = 0;
//            campaignActivityCounts.clear(); // Clear previous data
//            allDiscoveredEventTypes.clear(); // Clear previous event types
//
//            for (int i = 0; i < campaigns.size(); i++) {
//                Campaign campaign = campaigns.get(i);
//                log.append("═══════════════════════════════════════════════════════════════\n");
//                log.append("📊 Processing campaign ").append(i + 1).append("/").append(campaigns.size()).append("\n");
//                log.append("Campaign ID: ").append(campaign.id).append("\n");
//                log.append("Campaign Name: ").append(campaign.name).append("\n");
//                log.append("Timestamp Date: ").append(campaign.timestampDate).append(" (Year: ").append(campaign.year).append(")\n");
//                log.append("Campaign Date (from name): ").append(campaign.campaignDate).append("\n");
//                log.append("═══════════════════════════════════════════════════════════════\n");
//
//                List<ActivityData> activities = fetchActivityDataForCampaignDetailed(campaign.id, campaign.name, log);
//                totalActivities += activities.size();
//                campaignActivityCounts.put(campaign.name, activities.size());
//
//                // Track step counts for this campaign
//                Map<String, Map<Integer, Integer>> campaignStepCounts = new HashMap<>();
//
//                // Process activities and count event types per step
//                for (ActivityData activity : activities) {
//                    allSteps.add(activity.step);
//                    allEventTypes.add(activity.eventType);
//                    allDiscoveredEventTypes.add(activity.eventType); // Add to global tracker
//
//                    // Global counts
//                    stepEventCounts.computeIfAbsent(activity.step, k -> new HashMap<>())
//                            .merge(activity.eventType, 1, Integer::sum);
//
//                    // Campaign-specific counts for logging
//                    campaignStepCounts.computeIfAbsent(activity.step, k -> new HashMap<>())
//                            .merge(activity.eventType, 1, Integer::sum);
//                }
//
//                // Log detailed step breakdown for this campaign
//                log.append("\n📈 Campaign Activity Breakdown:\n");
//                log.append("Total activities found: ").append(activities.size()).append("\n");
//
//                if (!campaignStepCounts.isEmpty()) {
//                    log.append("\nSubsequence breakdown:\n");
//                    campaignStepCounts.entrySet().stream()
//                            .sorted(Map.Entry.comparingByKey())
//                            .forEach(stepEntry -> {
//                                String step = stepEntry.getKey();
//                                Map<Integer, Integer> eventCounts = stepEntry.getValue();
//                                int stepTotal = eventCounts.values().stream().mapToInt(Integer::intValue).sum();
//
//                                log.append("  ").append(step).append(": ").append(stepTotal).append(" activities");
//
//                                // Show event type breakdown for this step
//                                if (eventCounts.size() > 1) {
//                                    log.append(" (");
//                                    eventCounts.entrySet().stream()
//                                            .sorted(Map.Entry.comparingByKey())
//                                            .forEach(eventEntry -> {
//                                                log.append("Type ").append(eventEntry.getKey())
//                                                        .append(":").append(eventEntry.getValue()).append(" ");
//                                            });
//                                    log.append(")");
//                                }
//                                log.append("\n");
//                            });
//                } else {
//                    log.append("  No valid activities with step/event_type data\n");
//                }
//
//                log.append("───────────────────────────────────────────────────────────────\n\n");
//
//                // Rate limiting delay
//                if (i < campaigns.size() - 1) {
//                    safeSleep(CAMPAIGN_DELAY_MS);
//                }
//            }
//
//            // Ensure we use all discovered event types in the final report
//            log.append("🔢 ALL EVENT TYPES DISCOVERED: ").append(allDiscoveredEventTypes).append("\n");
//            log.append("🔢 EVENT TYPES WITH VALID STEPS: ").append(allEventTypes).append("\n");
//
//            // Use the complete set of discovered event types for Excel export
//            Set<Integer> finalEventTypes = new TreeSet<>(allDiscoveredEventTypes);
//            if (finalEventTypes.isEmpty()) {
//                finalEventTypes = allEventTypes; // Fallback to step-based event types
//            }
//
//            // Overall summary
//            log.append("📊 OVERALL ANALYSIS SUMMARY:\n");
//            log.append("═══════════════════════════════════════════════════════════════\n");
//            log.append("Total Campaigns Processed: ").append(campaigns.size()).append("\n");
//            log.append("Total Activities Found: ").append(totalActivities).append("\n");
//            log.append("Unique Steps/Subsequences: ").append(allSteps.size()).append(" ").append(allSteps).append("\n");
//            log.append("Event Types Found (with steps): ").append(allEventTypes.size()).append(" ").append(allEventTypes).append("\n");
//            log.append("ALL Event Types Discovered: ").append(finalEventTypes.size()).append(" ").append(finalEventTypes).append("\n\n");
//
//            // Campaign-wise activity summary
//            log.append("📋 Campaign Activity Summary:\n");
//            campaignActivityCounts.entrySet().stream()
//                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
//                    .forEach(entry -> {
//                        log.append("  ").append(entry.getKey().substring(0, Math.min(60, entry.getKey().length())))
//                                .append("... : ").append(entry.getValue()).append(" activities\n");
//                    });
//
//            // Global step summary
//            log.append("\n📈 Global Subsequence Summary:\n");
//            stepEventCounts.entrySet().stream()
//                    .sorted(Map.Entry.comparingByKey())
//                    .forEach(stepEntry -> {
//                        String step = stepEntry.getKey();
//                        Map<Integer, Integer> eventCounts = stepEntry.getValue();
//                        int stepTotal = eventCounts.values().stream().mapToInt(Integer::intValue).sum();
//
//                        log.append("  ").append(step).append(": ").append(stepTotal).append(" total activities");
//
//                        if (eventCounts.size() > 1) {
//                            log.append(" (");
//                            eventCounts.entrySet().stream()
//                                    .sorted(Map.Entry.comparingByKey())
//                                    .forEach(eventEntry -> {
//                                        log.append("Type ").append(eventEntry.getKey())
//                                                .append(":").append(eventEntry.getValue()).append(" ");
//                                    });
//                            log.append(")");
//                        }
//                        log.append("\n");
//                    });
//
//            log.append("═══════════════════════════════════════════════════════════════\n\n");
//
//            // Step 3: Export to Excel with ALL discovered event types
//            exportEventTypeAnalysisToExcel(stepEventCounts, allSteps, finalEventTypes, fromDateStr, toDateStr, log);
//
//            log.append("✅ Event Type Analysis completed successfully!\n");
//            log.append("📁 Excel report ready for download.\n");
//
//            return log.toString();
//
//        } catch (Exception e) {
//            return "❌ Event Type Analysis failed: " + e.getMessage() + "\n" +
//                    "Stack trace: " + Arrays.toString(e.getStackTrace());
//        }
//    }
//
//    private List<Campaign> fetchCampaignsWithNameBasedFiltering(LocalDate fromDate, LocalDate toDate, StringBuilder log) {
//        List<Campaign> matchingCampaigns = new ArrayList<>();
//        int skip = 0;
//        int limit = 100;
//        int batch = 1;
//        LocalDate earliestTargetDate = null;
//        boolean foundAnyMatching = false;
//
//        try {
//            log.append("🔍 Starting campaign fetch with name-based date filtering...\n");
//            log.append("Looking for campaigns with dates between ").append(fromDate).append(" and ").append(toDate).append("\n\n");
//
//            while (true) {
//                log.append("📡 Fetching campaigns batch ").append(batch).append(" (skip: ").append(skip).append(")\n");
//
//                String requestBody = "{\n" +
//                        "  \"limit\": " + limit + ",\n" +
//                        "  \"skip\": " + skip + ",\n" +
//                        "  \"search\": \"\",\n" +
//                        "  \"status\": null,\n" +
//                        "  \"include_tags\": true,\n" +
//                        "  \"tag\": null,\n" +
//                        "  \"sortColumn\": \"timestamp_created\",\n" +
//                        "  \"sortOrder\": \"desc\"\n" +
//                        "}";
//
//                RequestSpecification request = RestAssured.given()
//                        .baseUri(BASE_URL)
//                        .header("X-org-auth", API_KEY)
//                        .header("Content-Type", "application/json")
//                        .body(requestBody);
//
//                Response response = request.when()
//                        .post("/backend-alt/api/v1/campaign/list")
//                        .then()
//                        .extract()
//                        .response();
//
//                if (response.getStatusCode() != 200) {
//                    log.append("❌ Campaign API call failed with status: ").append(response.getStatusCode()).append("\n");
//                    if (response.getStatusCode() == 429) {
//                        log.append("⚠️ Rate limit hit. Waiting 5 seconds...\n");
//                        safeSleep(5000);
//                        continue;
//                    }
//                    break;
//                }
//
//                ObjectMapper mapper = new ObjectMapper();
//                JsonNode rootNode = mapper.readTree(response.getBody().asString());
//
//                if (!rootNode.isArray() || rootNode.size() == 0) {
//                    log.append("🏁 No more campaigns to fetch.\n");
//                    break;
//                }
//
//                boolean foundInBatch = false;
//                boolean reachedEarlierThanTarget = false;
//
//                for (JsonNode campaignNode : rootNode) {
//                    String timestamp = asText(campaignNode, "timestamp_created");
//                    String name = asText(campaignNode, "name");
//                    String id = asText(campaignNode, "id");
//                    JsonNode statusNode =campaignNode.get("Status")
//                    if (timestamp == null || name == null || id == null) continue;
//
//                    // CRITICAL: Filter out campaigns with status = 0
//                    int status = statusNode != null && !statusNode.isNull() ? statusNode.asInt() : -1;
//                    if (status == 0) {
//                        log.append("🚫 Skipping campaign with status=0: ").append(name.substring(0, Math.min(30, name.length()))).append("...\n");
//                        continue;
//                    }
//                    // Get year from timestamp
//                    int year;
//                    LocalDate timestampDate;
//                    try {
//                        Instant instant = Instant.from(ISO_FORMATTER.parse(timestamp));
//                        timestampDate = instant.atZone(ZoneOffset.UTC).toLocalDate();
//                        year = timestampDate.getYear();
//                    } catch (Exception e) {
//                        log.append("⚠️ Could not parse timestamp for campaign: ").append(name).append("\n");
//                        continue;
//                    }
//
//                    // Parse date from campaign name
//                    LocalDate campaignDate = parseDateFromCampaignName(name, year);
//
//                    if (campaignDate != null) {
//                        // Track earliest campaign date we've seen for stopping logic
//                        if (earliestTargetDate == null || campaignDate.isBefore(earliestTargetDate)) {
//                            earliestTargetDate = campaignDate;
//                        }
//
//                        // Check if campaign date is in our target range
//                        if (!campaignDate.isBefore(fromDate) && !campaignDate.isAfter(toDate)) {
//                            matchingCampaigns.add(new Campaign(id, name, timestampDate, campaignDate, year));
//                            foundInBatch = true;
//                            foundAnyMatching = true;
//
//                            log.append("✅ Match found: ").append(campaignDate).append(" - ")
//                                    .append(name.substring(0, Math.min(50, name.length()))).append("...\n");
//                        } else {
//                            log.append("📅 Campaign date ").append(campaignDate).append(" outside range - ")
//                                    .append(name.substring(0, Math.min(30, name.length()))).append("...\n");
//                        }
//
//                        // Check if we've reached campaigns much earlier than our target
//                        if (campaignDate.isBefore(fromDate.minusMonths(2))) {
//                            reachedEarlierThanTarget = true;
//                            log.append("⏹️ Reached campaigns much earlier than target range (").append(campaignDate).append(")\n");
//                        }
//                    } else {
//                        log.append("⚠️ Could not parse date from campaign name: ").append(name).append("\n");
//                    }
//                }
//
//                log.append("✅ Batch ").append(batch).append(" processed: ")
//                        .append(foundInBatch ? "found matching campaigns" : "no matches")
//                        .append(", total matches so far: ").append(matchingCampaigns.size()).append("\n");
//
//                // Stopping logic: if we've found some matching campaigns and now we're seeing much earlier dates
//                if (foundAnyMatching && reachedEarlierThanTarget) {
//                    log.append("🏁 Stopping search as we've reached campaigns much earlier than target range.\n");
//                    break;
//                }
//
//                if (rootNode.size() < limit) {
//                    log.append("🏁 Received less than limit, no more data.\n");
//                    break;
//                }
//
//                skip += limit;
//                batch++;
//                safeSleep(CAMPAIGN_DELAY_MS);
//            }
//
//            log.append("\n📊 Campaign filtering summary:\n");
//            log.append("Total batches processed: ").append(batch - 1).append("\n");
//            log.append("Campaigns matching date criteria: ").append(matchingCampaigns.size()).append("\n");
//            if (earliestTargetDate != null) {
//                log.append("Earliest campaign date found: ").append(earliestTargetDate).append("\n");
//            }
//            log.append("\n");
//
//        } catch (Exception e) {
//            log.append("❌ Error fetching campaigns: ").append(e.getMessage()).append("\n");
//        }
//
//        return matchingCampaigns;
//    }
//
//    private LocalDate parseDateFromCampaignName(String campaignName, int year) {
//        // Pattern to match date formats like "12_Aug", "5_July", "25_Dec", etc.
//        Pattern datePattern = Pattern.compile("(\\d{1,2})_(Jan|Feb|Mar|April|May|June|July|Aug|Sep|Oct|Nov|Dec)", Pattern.CASE_INSENSITIVE);
//        Matcher matcher = datePattern.matcher(campaignName);
//
//        if (matcher.find()) {
//            try {
//                int day = Integer.parseInt(matcher.group(1));
//                String monthStr = matcher.group(2);
//
//                // Normalize month string to match our map keys
//                monthStr = normalizeMonthString(monthStr);
//
//                Integer month = MONTH_MAP.get(monthStr);
//                if (month != null) {
//                    return LocalDate.of(year, month, day);
//                }
//            } catch (Exception e) {
//                // If parsing fails, return null
//                return null;
//            }
//        }
//        return null;
//    }
//
//    private String normalizeMonthString(String monthStr) {
//        // Normalize common variations
//        String normalized = monthStr.toLowerCase();
//        switch (normalized) {
//            case "january": return "Jan";
//            case "february": return "Feb";
//            case "march": return "Mar";
//            case "april": return "April";
//            case "may": return "May";
//            case "june": return "June";
//            case "july": return "July";
//            case "august": return "Aug";
//            case "september": return "Sep";
//            case "october": return "Oct";
//            case "november": return "Nov";
//            case "december": return "Dec";
//            default:
//                // For exact matches, capitalize first letter
//                return monthStr.substring(0, 1).toUpperCase() + monthStr.substring(1).toLowerCase();
//        }
//    }
//
//    // Keep all the existing methods unchanged
//    private List<ActivityData> fetchActivityDataForCampaignDetailed(String campaignId, String campaignName, StringBuilder log) {
//        List<ActivityData> activities = new ArrayList<>();
//        String beforeId = null;
//        int limit = 1000;
//        int batch = 1;
//        int totalFetched = 0;
//
//        // Track event types found in this campaign
//        Set<Integer> campaignEventTypes = new HashSet<>();
//
//        try {
//            log.append("🔍 Fetching activity data for campaign...\n");
//
//            while (true) {
//                String endpoint = "/backend-alt/api/v1/activity/list?campaign_id=" + campaignId + "&limit=" + limit;
//                if (beforeId != null) {
//                    endpoint += "&before_id=" + beforeId;
//                }
//
//                log.append("   📡 Activity batch ").append(batch).append(" (limit: ").append(limit);
//                if (beforeId != null) {
//                    log.append(", before_id: ").append(beforeId.substring(0, Math.min(15, beforeId.length()))).append("...");
//                }
//                log.append(")\n");
//
//                RequestSpecification request = RestAssured.given()
//                        .baseUri(BASE_URL)
//                        .header("X-org-auth", API_KEY)
//                        .header("Content-Type", "application/json");
//
//                Response response = request.when()
//                        .get(endpoint)
//                        .then()
//                        .extract()
//                        .response();
//
//                if (response.getStatusCode() != 200) {
//                    log.append("   ❌ Activity API call failed with status: ").append(response.getStatusCode()).append("\n");
//                    if (response.getStatusCode() == 429) {
//                        log.append("   ⚠️ Rate limit hit. Waiting 5 seconds...\n");
//                        safeSleep(5000);
//                        continue;
//                    }
//                    break;
//                }
//
//                ObjectMapper mapper = new ObjectMapper();
//                JsonNode rootNode = mapper.readTree(response.getBody().asString());
//                JsonNode activityHistory = rootNode.get("activity_history");
//
//                if (activityHistory == null || !activityHistory.isArray() || activityHistory.size() == 0) {
//                    log.append("   🏁 No more activity data for this campaign.\n");
//                    break;
//                }
//
//                int batchSize = activityHistory.size();
//                int validActivities = 0;
//                String lastId = null;
//
//                Map<String, Integer> batchStepCounts = new HashMap<>();
//                Map<Integer, Integer> batchEventTypeCounts = new HashMap<>();
//
//                for (JsonNode activity : activityHistory) {
//                    String step = asText(activity, "step");
//                    JsonNode eventTypeNode = activity.get("event_type");
//                    String id = asText(activity, "id");
//                    String contact = asText(activity, "contact");
//                    String timestamp = asText(activity, "timestamp_created");
//
//                    // CRITICAL: Always track event types, even if step is null
//                    if (eventTypeNode != null && !eventTypeNode.isNull()) {
//                        int eventType = eventTypeNode.asInt();
//                        campaignEventTypes.add(eventType);
//                        batchEventTypeCounts.merge(eventType, 1, Integer::sum);
//
//                        // Add to global tracker immediately
//                        allDiscoveredEventTypes.add(eventType);
//
//                        // Handle step - use "NULL_STEP" for null values, or the actual step
//                        String processedStep = (step != null && !step.trim().isEmpty()) ? step.trim() : "NULL_STEP";
//
//                        // Always add to activities with processed step
//                        activities.add(new ActivityData(processedStep, eventType, contact, timestamp));
//                        validActivities++;
//                        batchStepCounts.merge(processedStep, 1, Integer::sum);
//
//                        if (step == null || step.trim().isEmpty()) {
//                            log.append("   ⚠️ Found event_type ").append(eventType).append(" with null/empty step - using 'NULL_STEP'\n");
//                        }
//                    } else {
//                        // Track activities with step but no event_type
//                        if (step != null && !step.trim().isEmpty()) {
//                            log.append("   ⚠️ Found step '").append(step).append("' but event_type is null/missing\n");
//                        }
//                    }
//
//                    if (id != null) {
//                        lastId = id;
//                    }
//                }
//
//                totalFetched += batchSize;
//                beforeId = lastId;
//
//                log.append("   ✅ Batch ").append(batch).append(": ").append(batchSize).append(" raw activities, ")
//                        .append(validActivities).append(" processed (including NULL_STEP)\n");
//
//                // Log event type distribution for this batch
//                if (!batchEventTypeCounts.isEmpty()) {
//                    log.append("      Event types in batch: ");
//                    batchEventTypeCounts.entrySet().stream()
//                            .sorted(Map.Entry.comparingByKey())
//                            .forEach(entry -> log.append("Type ").append(entry.getKey()).append(":").append(entry.getValue()).append(" "));
//                    log.append("\n");
//                }
//
//                // Log step distribution for this batch if there are valid activities
//                if (!batchStepCounts.isEmpty()) {
//                    log.append("      Steps in batch: ");
//                    batchStepCounts.entrySet().stream()
//                            .sorted(Map.Entry.comparingByKey())
//                            .forEach(entry -> log.append(entry.getKey()).append(":").append(entry.getValue()).append(" "));
//                    log.append("\n");
//                }
//
//                if (activityHistory.size() < limit || beforeId == null) {
//                    log.append("   🏁 Reached end of activity data (received less than limit or no beforeId).\n");
//                    break;
//                }
//
//                batch++;
//                safeSleep(ACTIVITY_DELAY_MS);
//            }
//
//            log.append("📊 Campaign activity summary: ").append(totalFetched).append(" raw activities fetched, ")
//                    .append(activities.size()).append(" activities processed (including NULL_STEP cases)\n");
//
//            // Log all event types found in this campaign
//            if (!campaignEventTypes.isEmpty()) {
//                log.append("🔢 Event types found in this campaign: ").append(campaignEventTypes).append("\n");
//            }
//
//        } catch (Exception e) {
//            log.append("   ❌ Error fetching activities for campaign ").append(campaignId).append(": ").append(e.getMessage()).append("\n");
//            e.printStackTrace();
//        }
//
//        return activities;
//    }
//
//    private void safeSleep(long millis) {
//        try {
//            Thread.sleep(millis);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
//    }
//
//    private void exportEventTypeAnalysisToExcel(Map<String, Map<Integer, Integer>> stepEventCounts,
//                                                Set<String> allSteps, Set<Integer> allEventTypes,
//                                                String fromDate, String toDate, StringBuilder log) {
//        try (Workbook workbook = new XSSFWorkbook()) {
//
//            // Sheet 1: Event Type Analysis Matrix
//            createEventTypeAnalysisSheet(workbook, stepEventCounts, allSteps, allEventTypes, fromDate, toDate);
//
//            // Sheet 2: Campaign Activity Summary
//            createCampaignActivitySheet(workbook, fromDate, toDate);
//
//            // Save file
//            try (FileOutputStream fileOut = new FileOutputStream(EVENT_EXCEL_FILE_PATH)) {
//                workbook.write(fileOut);
//            }
//
//            log.append("📊 Excel report with 2 sheets generated successfully!\n");
//            log.append("   📋 Sheet 1: Event Type Analysis Matrix\n");
//            log.append("   📋 Sheet 2: Campaign Activity Summary\n");
//            log.append("📁 File saved: ").append(EVENT_EXCEL_FILE_PATH).append("\n");
//
//        } catch (IOException e) {
//            log.append("❌ Error creating Excel file: ").append(e.getMessage()).append("\n");
//            throw new RuntimeException("Error creating Excel file", e);
//        }
//    }
//
//    private void createEventTypeAnalysisSheet(Workbook workbook, Map<String, Map<Integer, Integer>> stepEventCounts,
//                                              Set<String> allSteps, Set<Integer> allEventTypes,
//                                              String fromDate, String toDate) {
//        Sheet sheet = workbook.createSheet("Event Type Analysis");
//
//        // Create header style
//        CellStyle headerStyle = workbook.createCellStyle();
//        Font headerFont = workbook.createFont();
//        headerFont.setBold(true);
//        headerFont.setFontHeightInPoints((short) 12);
//        headerStyle.setFont(headerFont);
//        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
//        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
//        addBorder(headerStyle);
//
//        // Create data style
//        CellStyle dataStyle = workbook.createCellStyle();
//        addBorder(dataStyle);
//
//        // Create title
//        Row titleRow = sheet.createRow(0);
//        Cell titleCell = titleRow.createCell(0);
//        titleCell.setCellValue("Event Type Analysis Report (" + fromDate + " to " + toDate + ") - Name-Based Filtering");
//        titleCell.setCellStyle(headerStyle);
//
//        // Create headers
//        Row headerRow = sheet.createRow(2);
//        headerRow.createCell(0).setCellValue("Subsequence");
//        headerRow.getCell(0).setCellStyle(headerStyle);
//
//        int colIndex = 1;
//        for (Integer eventType : allEventTypes) {
//            Cell cell = headerRow.createCell(colIndex++);
//            cell.setCellValue("Event Type " + eventType);
//            cell.setCellStyle(headerStyle);
//        }
//
//        // Add total column
//        Cell totalCell = headerRow.createCell(colIndex);
//        totalCell.setCellValue("Total");
//        totalCell.setCellStyle(headerStyle);
//
//        // Fill data rows
//        int rowIndex = 3;
//        for (String step : allSteps) {
//            Row dataRow = sheet.createRow(rowIndex++);
//
//            // Step name
//            Cell stepCell = dataRow.createCell(0);
//            stepCell.setCellValue(step);
//            stepCell.setCellStyle(dataStyle);
//
//            Map<Integer, Integer> eventCounts = stepEventCounts.getOrDefault(step, new HashMap<>());
//            int rowTotal = 0;
//
//            // Event type counts
//            colIndex = 1;
//            for (Integer eventType : allEventTypes) {
//                int count = eventCounts.getOrDefault(eventType, 0);
//                Cell countCell = dataRow.createCell(colIndex++);
//                countCell.setCellValue(count);
//                countCell.setCellStyle(dataStyle);
//                rowTotal += count;
//            }
//
//            // Row total
//            Cell rowTotalCell = dataRow.createCell(colIndex);
//            rowTotalCell.setCellValue(rowTotal);
//            rowTotalCell.setCellStyle(dataStyle);
//        }
//
//        // Add summary totals row
//        Row totalRow = sheet.createRow(rowIndex + 1);
//        Cell totalLabelCell = totalRow.createCell(0);
//        totalLabelCell.setCellValue("TOTAL");
//        totalLabelCell.setCellStyle(headerStyle);
//
//        colIndex = 1;
//        int grandTotal = 0;
//        for (Integer eventType : allEventTypes) {
//            int columnTotal = 0;
//            for (String step : allSteps) {
//                columnTotal += stepEventCounts.getOrDefault(step, new HashMap<>()).getOrDefault(eventType, 0);
//            }
//            Cell colTotalCell = totalRow.createCell(colIndex++);
//            colTotalCell.setCellValue(columnTotal);
//            colTotalCell.setCellStyle(headerStyle);
//            grandTotal += columnTotal;
//        }
//
//        // Grand total
//        Cell grandTotalCell = totalRow.createCell(colIndex);
//        grandTotalCell.setCellValue(grandTotal);
//        grandTotalCell.setCellStyle(headerStyle);
//
//        // Auto-size columns
//        for (int i = 0; i <= allEventTypes.size() + 1; i++) {
//            sheet.autoSizeColumn(i);
//            if (sheet.getColumnWidth(i) < 3000) {
//                sheet.setColumnWidth(i, 3000);
//            }
//        }
//    }
//
//    private void createCampaignActivitySheet(Workbook workbook, String fromDate, String toDate) {
//        Sheet sheet = workbook.createSheet("Campaign Activity Summary");
//
//        // Create header style
//        CellStyle headerStyle = workbook.createCellStyle();
//        Font headerFont = workbook.createFont();
//        headerFont.setBold(true);
//        headerFont.setFontHeightInPoints((short) 12);
//        headerStyle.setFont(headerFont);
//        headerStyle.setFillForegroundColor(IndexedColors.ORANGE.getIndex());
//        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
//        addBorder(headerStyle);
//
//        // Create data style
//        CellStyle dataStyle = workbook.createCellStyle();
//        addBorder(dataStyle);
//        dataStyle.setWrapText(true);
//
//        // Create title
//        Row titleRow = sheet.createRow(0);
//        Cell titleCell = titleRow.createCell(0);
//        titleCell.setCellValue("Campaign Activity Summary (" + fromDate + " to " + toDate + ")");
//        titleCell.setCellStyle(headerStyle);
//
//        // Create headers
//        Row headerRow = sheet.createRow(2);
//        headerRow.createCell(0).setCellValue("Campaign Name");
//        headerRow.createCell(1).setCellValue("Activity Data");
//        headerRow.getCell(0).setCellStyle(headerStyle);
//        headerRow.getCell(1).setCellStyle(headerStyle);
//
//        // Fill data rows with campaign activity counts
//        int rowIndex = 3;
//        int totalActivities = 0;
//
//        // Sort campaigns by activity count (descending)
//        List<Map.Entry<String, Integer>> sortedCampaigns = campaignActivityCounts.entrySet().stream()
//                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
//                .collect(java.util.stream.Collectors.toList());
//
//        for (Map.Entry<String, Integer> entry : sortedCampaigns) {
//            Row dataRow = sheet.createRow(rowIndex++);
//
//            // Campaign name
//            Cell nameCell = dataRow.createCell(0);
//            nameCell.setCellValue(entry.getKey());
//            nameCell.setCellStyle(dataStyle);
//
//            // Activity count
//            Cell countCell = dataRow.createCell(1);
//            countCell.setCellValue(entry.getValue());
//            countCell.setCellStyle(dataStyle);
//
//            totalActivities += entry.getValue();
//        }
//
//        // Add total row
//        Row totalRow = sheet.createRow(rowIndex + 1);
//        Cell totalLabelCell = totalRow.createCell(0);
//        totalLabelCell.setCellValue("TOTAL CAMPAIGNS: " + sortedCampaigns.size());
//        totalLabelCell.setCellStyle(headerStyle);
//
//        Cell totalCountCell = totalRow.createCell(1);
//        totalCountCell.setCellValue("TOTAL ACTIVITIES: " + totalActivities);
//        totalCountCell.setCellStyle(headerStyle);
//
//        // Auto-size columns
//        sheet.setColumnWidth(0, 15000); // Campaign name column - wider
//        sheet.setColumnWidth(1, 4000);  // Activity data column
//    }
//
//    private static String asText(JsonNode node, String field) {
//        JsonNode fieldNode = node.get(field);
//        return (fieldNode != null && !fieldNode.isNull()) ? fieldNode.asText() : null;
//    }
//
//    private static void addBorder(CellStyle style) {
//        style.setBorderBottom(BorderStyle.THIN);
//        style.setBorderTop(BorderStyle.THIN);
//        style.setBorderLeft(BorderStyle.THIN);
//        style.setBorderRight(BorderStyle.THIN);
//    }
//
//    public File getLatestEventTypeExcelFile() {
//        File file = new File(EVENT_EXCEL_FILE_PATH);
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EventTypeAnalyzerService {

    // ---- CONFIG ----
    private static final String BASE_URL = API.BASE_URL;
    private static final String API_KEY = API.API_KEY;
    private static final String EVENT_EXCEL_FILE_PATH = System.getProperty("java.io.tmpdir") + "event_type_analysis_report.xlsx";

    // Rate limiting
    private static final long CAMPAIGN_DELAY_MS = 300;
    private static final long ACTIVITY_DELAY_MS = 150;

    // Date formats
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]'Z'")
            .withZone(ZoneOffset.UTC);

    // Instance variables to store data for Excel export
    private Map<String, Integer> campaignActivityCounts = new HashMap<>();
    private Set<Integer> allDiscoveredEventTypes = new TreeSet<>(); // Global event types tracker
    private Map<String, Map<Integer, Integer>> campaignEventTypeCounts = new HashMap<>(); // Campaign -> EventType -> Count

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
        LocalDate timestampDate; // Date from timestamp_created
        LocalDate campaignDate;  // Date parsed from campaign name
        int year;

        Campaign(String id, String name, LocalDate timestampDate, LocalDate campaignDate, int year) {
            this.id = id;
            this.name = name;
            this.timestampDate = timestampDate;
            this.campaignDate = campaignDate;
            this.year = year;
        }
    }

    private static class ActivityData {
        String step;
        int eventType;
        String contact;
        String timestamp;

        ActivityData(String step, int eventType, String contact, String timestamp) {
            this.step = step;
            this.eventType = eventType;
            this.contact = contact;
            this.timestamp = timestamp;
        }
    }

    public String analyzeEventTypeByDateRange(String fromDateStr, String toDateStr) {
        try {
            StringBuilder log = new StringBuilder();
            log.append("🎯 Event Type Analysis for date range: ").append(fromDateStr).append(" to ").append(toDateStr).append("\n");
            log.append("🚀 Starting Event Type Analysis with Name-Based Date Filtering...\n\n");

            LocalDate fromDate = LocalDate.parse(fromDateStr, DATE_FORMATTER);
            LocalDate toDate = LocalDate.parse(toDateStr, DATE_FORMATTER);

            log.append("📅 Target Date Range: ").append(fromDate).append(" to ").append(toDate).append("\n");
            log.append("🔍 Filtering Method: Campaign name date + timestamp year\n\n");

            // Step 1: Fetch campaigns and filter by name-based date
            List<Campaign> campaigns = fetchCampaignsWithNameBasedFiltering(fromDate, toDate, log);

            if (campaigns.isEmpty()) {
                log.append("❌ No campaigns found matching the date criteria.\n");
                return log.toString();
            }

            log.append("✅ Found ").append(campaigns.size()).append(" campaigns matching date criteria\n\n");

            // Step 2: Fetch activity data for all campaigns with detailed logging
            Map<String, Map<Integer, Integer>> stepEventCounts = new HashMap<>();
            Set<Integer> allEventTypes = new TreeSet<>();
            Set<String> allSteps = new TreeSet<>();

            int totalActivities = 0;
            campaignActivityCounts.clear(); // Clear previous data
            allDiscoveredEventTypes.clear(); // Clear previous event types
            campaignEventTypeCounts.clear(); // Clear previous campaign event type data

            for (int i = 0; i < campaigns.size(); i++) {
                Campaign campaign = campaigns.get(i);
                log.append("═══════════════════════════════════════════════════════════════\n");
                log.append("📊 Processing campaign ").append(i + 1).append("/").append(campaigns.size()).append("\n");
                log.append("Campaign ID: ").append(campaign.id).append("\n");
                log.append("Campaign Name: ").append(campaign.name).append("\n");
                log.append("Timestamp Date: ").append(campaign.timestampDate).append(" (Year: ").append(campaign.year).append(")\n");
                log.append("Campaign Date (from name): ").append(campaign.campaignDate).append("\n");
                log.append("═══════════════════════════════════════════════════════════════\n");

                List<ActivityData> activities = fetchActivityDataForCampaignDetailed(campaign.id, campaign.name, log);
                totalActivities += activities.size();
                campaignActivityCounts.put(campaign.name, activities.size());

                // Track step counts for this campaign
                Map<String, Map<Integer, Integer>> campaignStepCounts = new HashMap<>();
                Map<Integer, Integer> campaignEventTypes = new HashMap<>();

                // Process activities and count event types per step
                for (ActivityData activity : activities) {
                    allSteps.add(activity.step);
                    allEventTypes.add(activity.eventType);
                    allDiscoveredEventTypes.add(activity.eventType); // Add to global tracker

                    // Global counts
                    stepEventCounts.computeIfAbsent(activity.step, k -> new HashMap<>())
                            .merge(activity.eventType, 1, Integer::sum);

                    // Campaign-specific counts for logging
                    campaignStepCounts.computeIfAbsent(activity.step, k -> new HashMap<>())
                            .merge(activity.eventType, 1, Integer::sum);

                    // Track event types for this campaign (for Sheet 2)
                    campaignEventTypes.merge(activity.eventType, 1, Integer::sum);
                }

                // Store campaign event type counts for Excel export
                campaignEventTypeCounts.put(campaign.name, campaignEventTypes);

                // Log detailed step breakdown for this campaign
                log.append("\n📈 Campaign Activity Breakdown:\n");
                log.append("Total activities found: ").append(activities.size()).append("\n");

                // Log event type breakdown for this campaign
                if (!campaignEventTypes.isEmpty()) {
                    log.append("Event type breakdown: ");
                    campaignEventTypes.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .forEach(entry -> log.append("Type ").append(entry.getKey())
                                    .append(":").append(entry.getValue()).append(" "));
                    log.append("\n");
                }

                if (!campaignStepCounts.isEmpty()) {
                    log.append("\nSubsequence breakdown:\n");
                    campaignStepCounts.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .forEach(stepEntry -> {
                                String step = stepEntry.getKey();
                                Map<Integer, Integer> eventCounts = stepEntry.getValue();
                                int stepTotal = eventCounts.values().stream().mapToInt(Integer::intValue).sum();

                                log.append("  ").append(step).append(": ").append(stepTotal).append(" activities");

                                // Show event type breakdown for this step
                                if (eventCounts.size() > 1) {
                                    log.append(" (");
                                    eventCounts.entrySet().stream()
                                            .sorted(Map.Entry.comparingByKey())
                                            .forEach(eventEntry -> {
                                                log.append("Type ").append(eventEntry.getKey())
                                                        .append(":").append(eventEntry.getValue()).append(" ");
                                            });
                                    log.append(")");
                                }
                                log.append("\n");
                            });
                } else {
                    log.append("  No valid activities with step/event_type data\n");
                }

                log.append("───────────────────────────────────────────────────────────────\n\n");

                // Rate limiting delay
                if (i < campaigns.size() - 1) {
                    safeSleep(CAMPAIGN_DELAY_MS);
                }
            }

            // Ensure we use all discovered event types in the final report
            log.append("🔢 ALL EVENT TYPES DISCOVERED: ").append(allDiscoveredEventTypes).append("\n");
            log.append("🔢 EVENT TYPES WITH VALID STEPS: ").append(allEventTypes).append("\n");

            // Use the complete set of discovered event types for Excel export
            Set<Integer> finalEventTypes = new TreeSet<>(allDiscoveredEventTypes);
            if (finalEventTypes.isEmpty()) {
                finalEventTypes = allEventTypes; // Fallback to step-based event types
            }

            // Overall summary
            log.append("📊 OVERALL ANALYSIS SUMMARY:\n");
            log.append("═══════════════════════════════════════════════════════════════\n");
            log.append("Total Campaigns Processed: ").append(campaigns.size()).append("\n");
            log.append("Total Activities Found: ").append(totalActivities).append("\n");
            log.append("Unique Steps/Subsequences: ").append(allSteps.size()).append(" ").append(allSteps).append("\n");
            log.append("Event Types Found (with steps): ").append(allEventTypes.size()).append(" ").append(allEventTypes).append("\n");
            log.append("ALL Event Types Discovered: ").append(finalEventTypes.size()).append(" ").append(finalEventTypes).append("\n\n");

            // Campaign-wise activity summary
            log.append("📋 Campaign Activity Summary:\n");
            campaignActivityCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(entry -> {
                        log.append("  ").append(entry.getKey().substring(0, Math.min(60, entry.getKey().length())))
                                .append("... : ").append(entry.getValue()).append(" activities\n");
                    });

            // Global step summary
            log.append("\n📈 Global Subsequence Summary:\n");
            stepEventCounts.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(stepEntry -> {
                        String step = stepEntry.getKey();
                        Map<Integer, Integer> eventCounts = stepEntry.getValue();
                        int stepTotal = eventCounts.values().stream().mapToInt(Integer::intValue).sum();

                        log.append("  ").append(step).append(": ").append(stepTotal).append(" total activities");

                        if (eventCounts.size() > 1) {
                            log.append(" (");
                            eventCounts.entrySet().stream()
                                    .sorted(Map.Entry.comparingByKey())
                                    .forEach(eventEntry -> {
                                        log.append("Type ").append(eventEntry.getKey())
                                                .append(":").append(eventEntry.getValue()).append(" ");
                                    });
                            log.append(")");
                        }
                        log.append("\n");
                    });

            log.append("═══════════════════════════════════════════════════════════════\n\n");

            // Step 3: Export to Excel with ALL discovered event types
            exportEventTypeAnalysisToExcel(stepEventCounts, allSteps, finalEventTypes, fromDateStr, toDateStr, log);

            log.append("✅ Event Type Analysis completed successfully!\n");
            log.append("📁 Excel report ready for download.\n");

            return log.toString();

        } catch (Exception e) {
            return "❌ Event Type Analysis failed: " + e.getMessage() + "\n" +
                    "Stack trace: " + Arrays.toString(e.getStackTrace());
        }
    }

    private List<Campaign> fetchCampaignsWithNameBasedFiltering(LocalDate fromDate, LocalDate toDate, StringBuilder log) {
        List<Campaign> matchingCampaigns = new ArrayList<>();
        int skip = 0;
        int limit = 100;
        int batch = 1;
        LocalDate earliestTargetDate = null;
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
                        // Track earliest campaign date we've seen for stopping logic
                        if (earliestTargetDate == null || campaignDate.isBefore(earliestTargetDate)) {
                            earliestTargetDate = campaignDate;
                        }

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

                // Stopping logic: if we've found some matching campaigns and now we're seeing much earlier dates
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
            if (earliestTargetDate != null) {
                log.append("Earliest campaign date found: ").append(earliestTargetDate).append("\n");
            }
            log.append("Note: Campaigns with status=0 are automatically excluded\n");
            log.append("\n");

        } catch (Exception e) {
            log.append("❌ Error fetching campaigns: ").append(e.getMessage()).append("\n");
        }

        return matchingCampaigns;
    }

    private LocalDate parseDateFromCampaignName(String campaignName, int year) {
        // Pattern to match date formats like "12_Aug", "5_July", "25_Dec", etc.
        Pattern datePattern = Pattern.compile("(\\d{1,2})_(Jan|Feb|Mar|April|May|June|July|Aug|Sep|Oct|Nov|Dec)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = datePattern.matcher(campaignName);

        if (matcher.find()) {
            try {
                int day = Integer.parseInt(matcher.group(1));
                String monthStr = matcher.group(2);

                // Normalize month string to match our map keys
                monthStr = normalizeMonthString(monthStr);

                Integer month = MONTH_MAP.get(monthStr);
                if (month != null) {
                    return LocalDate.of(year, month, day);
                }
            } catch (Exception e) {
                // If parsing fails, return null
                return null;
            }
        }
        return null;
    }

    private String normalizeMonthString(String monthStr) {
        // Normalize common variations
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
                // For exact matches, capitalize first letter
                return monthStr.substring(0, 1).toUpperCase() + monthStr.substring(1).toLowerCase();
        }
    }

    // Keep all the existing methods unchanged
    private List<ActivityData> fetchActivityDataForCampaignDetailed(String campaignId, String campaignName, StringBuilder log) {
        List<ActivityData> activities = new ArrayList<>();
        String beforeId = null;
        int limit = 1000;
        int batch = 1;
        int totalFetched = 0;

        // Track event types found in this campaign
        Set<Integer> campaignEventTypes = new HashSet<>();

        try {
            log.append("🔍 Fetching activity data for campaign...\n");

            while (true) {
                String endpoint = "/backend-alt/api/v1/activity/list?campaign_id=" + campaignId + "&limit=" + limit;
                if (beforeId != null) {
                    endpoint += "&before_id=" + beforeId;
                }

                log.append("   📡 Activity batch ").append(batch).append(" (limit: ").append(limit);
                if (beforeId != null) {
                    log.append(", before_id: ").append(beforeId.substring(0, Math.min(15, beforeId.length()))).append("...");
                }
                log.append(")\n");

                RequestSpecification request = RestAssured.given()
                        .baseUri(BASE_URL)
                        .header("X-org-auth", API_KEY)
                        .header("Content-Type", "application/json");

                Response response = request.when()
                        .get(endpoint)
                        .then()
                        .extract()
                        .response();

                if (response.getStatusCode() != 200) {
                    log.append("   ❌ Activity API call failed with status: ").append(response.getStatusCode()).append("\n");
                    if (response.getStatusCode() == 429) {
                        log.append("   ⚠️ Rate limit hit. Waiting 5 seconds...\n");
                        safeSleep(5000);
                        continue;
                    }
                    break;
                }

                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(response.getBody().asString());
                JsonNode activityHistory = rootNode.get("activity_history");

                if (activityHistory == null || !activityHistory.isArray() || activityHistory.size() == 0) {
                    log.append("   🏁 No more activity data for this campaign.\n");
                    break;
                }

                int batchSize = activityHistory.size();
                int validActivities = 0;
                String lastId = null;

                Map<String, Integer> batchStepCounts = new HashMap<>();
                Map<Integer, Integer> batchEventTypeCounts = new HashMap<>();

                for (JsonNode activity : activityHistory) {
                    String step = asText(activity, "step");
                    JsonNode eventTypeNode = activity.get("event_type");
                    String id = asText(activity, "id");
                    String contact = asText(activity, "contact");
                    String timestamp = asText(activity, "timestamp_created");

                    // CRITICAL: Always track event types, even if step is null
                    if (eventTypeNode != null && !eventTypeNode.isNull()) {
                        int eventType = eventTypeNode.asInt();
                        campaignEventTypes.add(eventType);
                        batchEventTypeCounts.merge(eventType, 1, Integer::sum);

                        // Add to global tracker immediately
                        allDiscoveredEventTypes.add(eventType);

                        // Handle step - use "NULL_STEP" for null values, or the actual step
                        String processedStep = (step != null && !step.trim().isEmpty()) ? step.trim() : "NULL_STEP";

                        // Always add to activities with processed step
                        activities.add(new ActivityData(processedStep, eventType, contact, timestamp));
                        validActivities++;
                        batchStepCounts.merge(processedStep, 1, Integer::sum);

                        if (step == null || step.trim().isEmpty()) {
                            log.append("   ⚠️ Found event_type ").append(eventType).append(" with null/empty step - using 'NULL_STEP'\n");
                        }
                    } else {
                        // Track activities with step but no event_type
                        if (step != null && !step.trim().isEmpty()) {
                            log.append("   ⚠️ Found step '").append(step).append("' but event_type is null/missing\n");
                        }
                    }

                    if (id != null) {
                        lastId = id;
                    }
                }

                totalFetched += batchSize;
                beforeId = lastId;

                log.append("   ✅ Batch ").append(batch).append(": ").append(batchSize).append(" raw activities, ")
                        .append(validActivities).append(" processed (including NULL_STEP)\n");

                // Log event type distribution for this batch
                if (!batchEventTypeCounts.isEmpty()) {
                    log.append("      Event types in batch: ");
                    batchEventTypeCounts.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .forEach(entry -> log.append("Type ").append(entry.getKey()).append(":").append(entry.getValue()).append(" "));
                    log.append("\n");
                }

                // Log step distribution for this batch if there are valid activities
                if (!batchStepCounts.isEmpty()) {
                    log.append("      Steps in batch: ");
                    batchStepCounts.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .forEach(entry -> log.append(entry.getKey()).append(":").append(entry.getValue()).append(" "));
                    log.append("\n");
                }

                if (activityHistory.size() < limit || beforeId == null) {
                    log.append("   🏁 Reached end of activity data (received less than limit or no beforeId).\n");
                    break;
                }

                batch++;
                safeSleep(ACTIVITY_DELAY_MS);
            }

            log.append("📊 Campaign activity summary: ").append(totalFetched).append(" raw activities fetched, ")
                    .append(activities.size()).append(" activities processed (including NULL_STEP cases)\n");

            // Log all event types found in this campaign
            if (!campaignEventTypes.isEmpty()) {
                log.append("🔢 Event types found in this campaign: ").append(campaignEventTypes).append("\n");
            }

        } catch (Exception e) {
            log.append("   ❌ Error fetching activities for campaign ").append(campaignId).append(": ").append(e.getMessage()).append("\n");
            e.printStackTrace();
        }

        return activities;
    }

    private void safeSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void exportEventTypeAnalysisToExcel(Map<String, Map<Integer, Integer>> stepEventCounts,
                                                Set<String> allSteps, Set<Integer> allEventTypes,
                                                String fromDate, String toDate, StringBuilder log) {
        try (Workbook workbook = new XSSFWorkbook()) {

            // Sheet 1: Event Type Analysis Matrix
            createEventTypeAnalysisSheet(workbook, stepEventCounts, allSteps, allEventTypes, fromDate, toDate);

            // Sheet 2: Campaign Activity Summary
            createCampaignActivitySheet(workbook, fromDate, toDate);

            // Save file
            try (FileOutputStream fileOut = new FileOutputStream(EVENT_EXCEL_FILE_PATH)) {
                workbook.write(fileOut);
            }

            log.append("📊 Excel report with 2 sheets generated successfully!\n");
            log.append("   📋 Sheet 1: Event Type Analysis Matrix\n");
            log.append("   📋 Sheet 2: Campaign Activity Summary\n");
            log.append("📁 File saved: ").append(EVENT_EXCEL_FILE_PATH).append("\n");

        } catch (IOException e) {
            log.append("❌ Error creating Excel file: ").append(e.getMessage()).append("\n");
            throw new RuntimeException("Error creating Excel file", e);
        }
    }

    private void createEventTypeAnalysisSheet(Workbook workbook, Map<String, Map<Integer, Integer>> stepEventCounts,
                                              Set<String> allSteps, Set<Integer> allEventTypes,
                                              String fromDate, String toDate) {
        Sheet sheet = workbook.createSheet("Event Type Analysis");

        // Create header style
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 12);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        addBorder(headerStyle);

        // Create data style
        CellStyle dataStyle = workbook.createCellStyle();
        addBorder(dataStyle);

        // Create title
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Event Type Analysis Report (" + fromDate + " to " + toDate + ") - Name-Based Filtering");
        titleCell.setCellStyle(headerStyle);

        // Create headers
        Row headerRow = sheet.createRow(2);
        headerRow.createCell(0).setCellValue("Subsequence");
        headerRow.getCell(0).setCellStyle(headerStyle);

        int colIndex = 1;
        for (Integer eventType : allEventTypes) {
            Cell cell = headerRow.createCell(colIndex++);
            cell.setCellValue("Event Type " + eventType);
            cell.setCellStyle(headerStyle);
        }

        // Add total column
        Cell totalCell = headerRow.createCell(colIndex);
        totalCell.setCellValue("Total");
        totalCell.setCellStyle(headerStyle);

        // Fill data rows
        int rowIndex = 3;
        for (String step : allSteps) {
            Row dataRow = sheet.createRow(rowIndex++);

            // Step name
            Cell stepCell = dataRow.createCell(0);
            stepCell.setCellValue(step);
            stepCell.setCellStyle(dataStyle);

            Map<Integer, Integer> eventCounts = stepEventCounts.getOrDefault(step, new HashMap<>());
            int rowTotal = 0;

            // Event type counts
            colIndex = 1;
            for (Integer eventType : allEventTypes) {
                int count = eventCounts.getOrDefault(eventType, 0);
                Cell countCell = dataRow.createCell(colIndex++);
                countCell.setCellValue(count);
                countCell.setCellStyle(dataStyle);
                rowTotal += count;
            }

            // Row total
            Cell rowTotalCell = dataRow.createCell(colIndex);
            rowTotalCell.setCellValue(rowTotal);
            rowTotalCell.setCellStyle(dataStyle);
        }

        // Add summary totals row
        Row totalRow = sheet.createRow(rowIndex + 1);
        Cell totalLabelCell = totalRow.createCell(0);
        totalLabelCell.setCellValue("TOTAL");
        totalLabelCell.setCellStyle(headerStyle);

        colIndex = 1;
        int grandTotal = 0;
        for (Integer eventType : allEventTypes) {
            int columnTotal = 0;
            for (String step : allSteps) {
                columnTotal += stepEventCounts.getOrDefault(step, new HashMap<>()).getOrDefault(eventType, 0);
            }
            Cell colTotalCell = totalRow.createCell(colIndex++);
            colTotalCell.setCellValue(columnTotal);
            colTotalCell.setCellStyle(headerStyle);
            grandTotal += columnTotal;
        }

        // Grand total
        Cell grandTotalCell = totalRow.createCell(colIndex);
        grandTotalCell.setCellValue(grandTotal);
        grandTotalCell.setCellStyle(headerStyle);

        // Auto-size columns
        for (int i = 0; i <= allEventTypes.size() + 1; i++) {
            sheet.autoSizeColumn(i);
            if (sheet.getColumnWidth(i) < 3000) {
                sheet.setColumnWidth(i, 3000);
            }
        }
    }

    private void createCampaignActivitySheet(Workbook workbook, String fromDate, String toDate) {
        Sheet sheet = workbook.createSheet("Campaign Activity Summary");

        // Create header style
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 12);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.ORANGE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        addBorder(headerStyle);

        // Create data style
        CellStyle dataStyle = workbook.createCellStyle();
        addBorder(dataStyle);
        dataStyle.setWrapText(true);

        // CRITICAL: Ensure we have all event types from all campaigns
        Set<Integer> allEventTypesForSheet = new TreeSet<>(allDiscoveredEventTypes);

        // Double-check by collecting from campaign data as well
        for (Map<Integer, Integer> campaignEvents : campaignEventTypeCounts.values()) {
            allEventTypesForSheet.addAll(campaignEvents.keySet());
        }

        // Create title
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Campaign Activity Summary with Event Type Breakdown (" + fromDate + " to " + toDate + ")");
        titleCell.setCellStyle(headerStyle);

        // Add info row about event types
        Row infoRow = sheet.createRow(1);
        Cell infoCell = infoRow.createCell(0);
        infoCell.setCellValue("Event Types Included: " + allEventTypesForSheet.toString());
        infoCell.setCellStyle(dataStyle);

        // Create headers - Campaign Name + Event Type columns + Total Activity Data
        Row headerRow = sheet.createRow(3);
        headerRow.createCell(0).setCellValue("Campaign Name");
        headerRow.getCell(0).setCellStyle(headerStyle);

        // Add event type columns - GUARANTEED to include ALL discovered event types
        int colIndex = 1;
        List<Integer> sortedEventTypes = new ArrayList<>(allEventTypesForSheet);
        for (Integer eventType : sortedEventTypes) {
            Cell eventTypeCell = headerRow.createCell(colIndex++);
            eventTypeCell.setCellValue("Event Type " + eventType);
            eventTypeCell.setCellStyle(headerStyle);
        }

        // Add total activity data column
        Cell totalActivityCell = headerRow.createCell(colIndex);
        totalActivityCell.setCellValue("Total Activity Data");
        totalActivityCell.setCellStyle(headerStyle);

        // Fill data rows with campaign activity counts and event type breakdown
        int rowIndex = 4;
        int totalActivities = 0;
        Map<Integer, Integer> globalEventTypeTotals = new HashMap<>();

        // Sort campaigns by total activity count (descending)
        List<Map.Entry<String, Integer>> sortedCampaigns = campaignActivityCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(java.util.stream.Collectors.toList());

        for (Map.Entry<String, Integer> entry : sortedCampaigns) {
            String campaignName = entry.getKey();
            int totalActivityCount = entry.getValue();

            Row dataRow = sheet.createRow(rowIndex++);

            // Campaign name
            Cell nameCell = dataRow.createCell(0);
            nameCell.setCellValue(campaignName);
            nameCell.setCellStyle(dataStyle);

            // Event type counts for this campaign
            Map<Integer, Integer> campaignEventTypes = campaignEventTypeCounts.getOrDefault(campaignName, new HashMap<>());
            colIndex = 1;

            // CRITICAL: Use ALL discovered event types, ensuring none are missed
            for (Integer eventType : sortedEventTypes) {
                int eventCount = campaignEventTypes.getOrDefault(eventType, 0);
                Cell eventCell = dataRow.createCell(colIndex++);
                eventCell.setCellValue(eventCount);
                eventCell.setCellStyle(dataStyle);

                // Add to global totals
                globalEventTypeTotals.merge(eventType, eventCount, Integer::sum);
            }

            // Total activity count
            Cell totalCell = dataRow.createCell(colIndex);
            totalCell.setCellValue(totalActivityCount);
            totalCell.setCellStyle(dataStyle);

            totalActivities += totalActivityCount;
        }

        // Add total row
        Row totalRow = sheet.createRow(rowIndex + 1);
        Cell totalLabelCell = totalRow.createCell(0);
        totalLabelCell.setCellValue("TOTAL (" + sortedCampaigns.size() + " campaigns)");
        totalLabelCell.setCellStyle(headerStyle);

        // Event type totals - GUARANTEED to include ALL event types
        colIndex = 1;
        for (Integer eventType : sortedEventTypes) {
            Cell totalEventCell = totalRow.createCell(colIndex++);
            totalEventCell.setCellValue(globalEventTypeTotals.getOrDefault(eventType, 0));
            totalEventCell.setCellStyle(headerStyle);
        }

        // Grand total activities
        Cell grandTotalCell = totalRow.createCell(colIndex);
        grandTotalCell.setCellValue(totalActivities);
        grandTotalCell.setCellStyle(headerStyle);

        // Auto-size columns
        sheet.setColumnWidth(0, 15000); // Campaign name column - wider
        for (int i = 1; i <= sortedEventTypes.size(); i++) {
            sheet.setColumnWidth(i, 3500); // Event type columns
        }
        sheet.setColumnWidth(colIndex, 4500); // Total activity column

        // Add verification row at the bottom
        Row verificationRow = sheet.createRow(rowIndex + 3);
        Cell verificationCell = verificationRow.createCell(0);
        verificationCell.setCellValue("✅ VERIFICATION: All " + allEventTypesForSheet.size() +
                " discovered event types included: " + allEventTypesForSheet.toString());
        verificationCell.setCellStyle(dataStyle);
    }

    private static String asText(JsonNode node, String field) {
        JsonNode fieldNode = node.get(field);
        return (fieldNode != null && !fieldNode.isNull()) ? fieldNode.asText() : null;
    }

    private static void addBorder(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    public File getLatestEventTypeExcelFile() {
        File file = new File(EVENT_EXCEL_FILE_PATH);
        return file.exists() ? file : null;
    }
}