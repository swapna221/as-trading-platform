package com.trading.manualorderservice.optionfilter;


import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import com.opencsv.CSVReader;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DhanOptionHelper {
    private static final String CSV_URL = "https://images.dhan.co/api-data/api-scrip-master.csv";
    public static List<Map<String, String>> loadMasterCsv() throws Exception {
        List<Map<String, String>> records = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new InputStreamReader(new URL(CSV_URL).openStream()))) {
            String[] headers = reader.readNext();
            if (headers == null) throw new RuntimeException("Empty CSV");

            System.out.println("CSV Headers: " + Arrays.toString(headers));
            String[] row;
            int rowCount = 0;

            while ((row = reader.readNext()) != null) {
                if (row.length != headers.length) {
                    System.out.println("Skipping row due to mismatched column count.");
                    continue;
                }
                Map<String, String> map = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    map.put(headers[i].trim(), row[i].trim());
                }
                records.add(map);
                rowCount++;
            }

            System.out.println("Total rows loaded from CSV: " + rowCount);
            if (!records.isEmpty()) {
                System.out.println("Sample record: " + records.get(0));
            }
        } catch (Exception e) {
            log.error("Error reading CSV: {}" ,e.getMessage());
        }

        return records;
    }
}