package com.LeadAnalysis.ESPAnalysis.service;

import com.LeadAnalysis.ESPAnalysis.config.API;
import io.restassured.response.Response;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;

public class CampaignDebugRunner {
    private static final String X_Org_Auth =API.API_KEY;
    private static final String BASE_URI =API.BASE_URL;

    public static void main(String[] args) {
        int limit = 1000;
        boolean isFirstCall = true;
        String before_id = null;
        Map<String,Integer> map = new HashMap<>();
        int total_count = 0;

        // --- EXCEL SETUP ---
        // Escape backslashes in the file path
        String filepath = "C:\\Users\\saira\\Downloads\\primary_replies_esp_report.xlsx";
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Activity Data");

        // Create Header Row
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Contact");
        headerRow.createCell(1).setCellValue("Timestamp Created (IST)");
        headerRow.createCell(2).setCellValue("Step (Analyzed)");

        int rowNum = 1; // Start writing data from the second row (index 1)

        // --- DATE FILTER SETUP ---i
        LocalDate fromDate = LocalDate.of(2025, 9, 18);
        LocalDate toDate = LocalDate.of(2025, 9, 25);
        boolean shouldContinue = true;

        // --- API PULL LOOP ---
        while(shouldContinue){
            String endpoint = "/backend-alt/api/v1/activity/list?campaign_id=98f92beb-8f67-4e49-bfaf-73ac65a3c9b7&limit=1000";
            if(!isFirstCall && before_id!=null){
                // Ensure to encode 'before_id' if necessary, but direct concatenation works for simple UUIDs
                endpoint += "&before_id=" + before_id;
            }

            Response response = given()
                    .baseUri(BASE_URI)
                    .header("X-Org-Auth",X_Org_Auth)
                    .when()
                    .get(endpoint)
                    .then()
                    .extract().response();

            List<Map<String,Object>> activityHistory  =response.jsonPath().getList("activity_history");

            if(activityHistory == null || activityHistory.isEmpty()){
                break;
            }

            // --- DATA PROCESSING LOOP ---
            for(Map<String,Object> obj: activityHistory){
                String timestampStr = (String) obj.get("timestamp_created");

                // Date Conversion and Validation
                ZonedDateTime zonedDateTimeUTC = ZonedDateTime.parse(timestampStr);
                ZonedDateTime zonedDateTimeIST = zonedDateTimeUTC.withZoneSameInstant(ZoneId.of("Asia/Kolkata"));
                LocalDate recordDate = zonedDateTimeIST.toLocalDate();

                if (recordDate.isAfter(toDate) || recordDate.isBefore(fromDate)) {
                    System.out.println("Stopping loop. Record with timestamp " + timestampStr + " is outside the date range (" + fromDate + " to " + toDate + ")");
                    shouldContinue = false;
                    break;
                }

                // --- CORE LOGIC & EXCEL WRITING ---
                String contact = (String) obj.get("contact");
                if(obj.get("step") !=null && Integer.parseInt(obj.get("event_type").toString()) == 1) {


                    int step = (obj.get("step").toString().charAt(2) - '0') + 1;
                    String stepStr = String.valueOf(step);

                    // Update Step Count Map
                    map.put(stepStr, map.getOrDefault(stepStr, 0) + 1);
                    total_count++;

                    // Write Data to Excel
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(contact);
                    row.createCell(1).setCellValue(zonedDateTimeIST.toString());
                    row.createCell(2).setCellValue(stepStr);
                }

                    isFirstCall = false;
            }

            if(!shouldContinue){
                break;
            }

            if(response.jsonPath().getList("activity_history").size()!=limit){
                break;
            }

            if (!activityHistory.isEmpty()) {
                before_id = activityHistory.get(activityHistory.size() - 1)
                        .get("id")
                        .toString();
            }
            System.out.println("totalCount: " + total_count);
        }

        // --- FINAL OUTPUT AND FILE SAVE ---
        System.out.println("Step Count: " + map);
        System.out.println("Total Count: " + total_count);

        try (FileOutputStream fileOut = new FileOutputStream(filepath)) {
            workbook.write(fileOut);
            System.out.println("Successfully wrote " + (rowNum - 1) + " records to Excel file: " + filepath);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error writing to Excel file: " + e.getMessage());
        } finally {
            try {
                workbook.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}