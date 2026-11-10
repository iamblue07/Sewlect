package com.sewlect.catalog.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CatalogCsvReader {

    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreSurroundingSpaces(true)
            .get();

    public List<StyleCsvRecord> readStyles(Path csvPath) {
        try (Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, CSV_FORMAT)) {

            List<StyleCsvRecord> records = new ArrayList<>();
            for (CSVRecord row : parser) {
                try {
                    records.add(toStyleCsvRecord(row));
                } catch (RuntimeException e) {
                    log.warn("Skipping malformed styles row at line {}: {}", row.getRecordNumber(), e.getMessage());
                }
            }
            return records;

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read styles CSV at " + csvPath, e);
        }
    }

    public Map<String, String> readImageLinks(Path csvPath) {
        try (Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, CSV_FORMAT)) {

            Map<String, String> linksByFilename = new HashMap<>();
            for (CSVRecord row : parser) {
                try {
                    linksByFilename.put(row.get("filename"), row.get("link"));
                } catch (RuntimeException e) {
                    log.warn("Skipping malformed images row at line {}: {}", row.getRecordNumber(), e.getMessage());
                }
            }
            return linksByFilename;

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read images CSV at " + csvPath, e);
        }
    }

    private StyleCsvRecord toStyleCsvRecord(CSVRecord row) {
        return new StyleCsvRecord(
                row.get("id"),
                row.get("gender"),
                row.get("masterCategory"),
                row.get("subCategory"),
                row.get("articleType"),
                row.get("baseColour"),
                row.get("season"),
                Integer.parseInt(row.get("year")),
                row.get("usage"),
                row.get("productDisplayName")
        );
    }
}