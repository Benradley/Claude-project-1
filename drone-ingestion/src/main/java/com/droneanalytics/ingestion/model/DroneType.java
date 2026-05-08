package com.droneanalytics.ingestion.model;

/**
 * Identifies the drone manufacturer/autopilot system that produced the log file.
 */
public enum DroneType {
    DJI,        // DJI consumer drones (Mavic, Phantom, Mini, etc.)
    ARDUPILOT,  // ArduPilot autopilot (DataFlash .bin logs)
    PX4,        // PX4 autopilot (ULog .ulg files)
    MAVLINK,    // Any ground station TLOG recorded over MAVLink protocol
    PARROT      // Parrot drones (Anafi, Bebop, etc.)
}
