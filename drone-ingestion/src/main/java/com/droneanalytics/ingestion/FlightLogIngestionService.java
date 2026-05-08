package com.droneanalytics.ingestion;

import com.droneanalytics.ingestion.model.TelemetryPoint;
import com.droneanalytics.ingestion.parser.FormatDetector;
import com.droneanalytics.ingestion.parser.LogParser;
import com.droneanalytics.ingestion.parser.ParseException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Entry point for the flight log ingestion pipeline.
 *
 * Usage:
 *   FlightLogIngestionService service = new FlightLogIngestionService();
 *   List<TelemetryPoint> points = service.ingest(Path.of("flight.bin"));
 *
 * The service automatically detects the file format, selects the correct parser,
 * and returns a chronologically ordered list of normalised TelemetryPoints.
 */
@Service
public class FlightLogIngestionService {

    /**
     * Reads a drone log file from disk, detects its format, and returns normalised telemetry.
     *
     * @param filePath Path to the log file.
     * @return Ordered list of TelemetryPoints. Never null; may be empty if the file has no usable data.
     * @throws IOException    If the file cannot be read.
     * @throws ParseException If the format is unrecognised or the file is malformed.
     */
    public List<TelemetryPoint> ingest(Path filePath) throws IOException, ParseException {
        byte[] fileData = Files.readAllBytes(filePath);
        String filename = filePath.getFileName().toString();
        LogParser parser = FormatDetector.detect(fileData, filename);
        List<TelemetryPoint> points = parser.parse(fileData);
        return points;
    }

    /**
     * Parses a drone log from raw bytes (e.g. from an HTTP upload buffer).
     *
     * @param fileData Raw file bytes.
     * @param filename Original filename including extension (used for format detection hint).
     */
    public List<TelemetryPoint> ingest(byte[] fileData, String filename) throws ParseException {
        LogParser parser = FormatDetector.detect(fileData, filename);
        return parser.parse(fileData);
    }
}
