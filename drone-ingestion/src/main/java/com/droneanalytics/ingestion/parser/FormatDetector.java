package com.droneanalytics.ingestion.parser;

import java.util.Arrays;

/**
 * Detects the drone log format from file content and returns the appropriate parser.
 *
 * Detection order matters — check more specific signatures before generic ones.
 */
public class FormatDetector {

    // PX4 ULog magic: "ULog" (4 bytes) + 0x01 0x12 0x35 (3 bytes) = 7 bytes total
    private static final byte[] ULOG_MAGIC = {
        0x55, 0x4C, 0x6F, 0x67, 0x01, 0x12, 0x35
    };

    // ArduPilot DataFlash message header bytes (HEAD_BYTE1, HEAD_BYTE2)
    private static final byte ARDUPILOT_HEAD1 = (byte) 0xA3;
    private static final byte ARDUPILOT_HEAD2 = (byte) 0x95;

    // MAVLink v1 start byte
    private static final byte MAVLINK_V1_STX = (byte) 0xFE;
    // MAVLink v2 start byte
    private static final byte MAVLINK_V2_STX = (byte) 0xFD;

    private static final LogParser[] PARSERS = {
        new Px4UlogParser(),
        new ArduPilotBinParser(),
        new MavlinkTlogParser(),
        new DjiTxtParser(),
        new ParrotJsonParser()
    };

    /**
     * Detects the format of the given file and returns the matching parser.
     *
     * @param fileData The complete file contents.
     * @param filename The filename including extension (used as secondary hint).
     * @return A parser that can handle this file.
     * @throws ParseException if no parser recognises the format.
     */
    public static LogParser detect(byte[] fileData, String filename) throws ParseException {
        if (fileData == null || fileData.length == 0) {
            throw new ParseException("File is empty");
        }

        // Supply up to 32 bytes as the detection header
        byte[] header = Arrays.copyOf(fileData, Math.min(32, fileData.length));

        for (LogParser parser : PARSERS) {
            if (parser.canParse(header, filename)) {
                return parser;
            }
        }

        throw new ParseException(
            "Unrecognised drone log format: " + filename +
            ". Supported formats: DJI .txt, ArduPilot .bin, PX4 .ulg, MAVLink .tlog, Parrot .json"
        );
    }

    // Package-private helpers used by individual canParse() implementations

    static boolean startsWithBytes(byte[] data, byte[] magic) {
        if (data.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) return false;
        }
        return true;
    }

    static byte[] getUlogMagic() { return ULOG_MAGIC; }
    static byte getArduPilotHead1() { return ARDUPILOT_HEAD1; }
    static byte getArduPilotHead2() { return ARDUPILOT_HEAD2; }
    static byte getMavlinkV1Stx() { return MAVLINK_V1_STX; }
    static byte getMavlinkV2Stx() { return MAVLINK_V2_STX; }
}
