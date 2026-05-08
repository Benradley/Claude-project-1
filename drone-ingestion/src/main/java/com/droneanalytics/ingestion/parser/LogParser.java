package com.droneanalytics.ingestion.parser;

import com.droneanalytics.ingestion.model.DroneType;
import com.droneanalytics.ingestion.model.TelemetryPoint;

import java.util.List;

/**
 * Strategy interface for drone log parsers.
 * Each parser handles one file format and converts it into a list of TelemetryPoints.
 */
public interface LogParser {

    /**
     * Returns true if this parser can handle the given file.
     *
     * @param fileHeader The first 32 bytes of the file (used for magic-byte detection).
     * @param filename   The filename including extension (used as a secondary hint).
     */
    boolean canParse(byte[] fileHeader, String filename);

    /**
     * Parses the entire file and returns normalized telemetry points in chronological order.
     *
     * @param fileData The complete file contents.
     * @return List of TelemetryPoints, never null, may be empty if the file has no usable data.
     * @throws ParseException if the file is malformed and cannot be recovered from.
     */
    List<TelemetryPoint> parse(byte[] fileData) throws ParseException;

    /**
     * Returns the drone type this parser produces.
     */
    DroneType getDroneType();
}
