package com.gtbridge.neonpatcher;

public enum CockpitReading {
    VOLTAGE(1, "Akkuspannung", "Spannung", "voltage", 1, "V", "79,6 V"),
    POWER(2, "Leistung", "Leistung", "power", 2, "kW", "0,83 kW"),
    CURRENT(4, "Akkustrom", "Akkustrom", "current", 0, "A", "10 A"),
    DRIVE(8, "Antriebsblock", "", "", 0, "", ""),
    PHASE(16, "Phasenstrom samt Balken", "Phasenstrom", "phase", 0, "A", "14 A"),
    MOTOR_TEMP(32, "Motortemperatur", "Motor", "temperature", 0, "°C", "28 °C"),
    ESC_TEMP(64, "ESC-Temperatur", "ESC", "controller", 0, "°C", "31 °C"),
    TRIP(128, "Trip", "Trip", "trip", 1, "km", "3,2 km"),
    CONSUMPTION(256, "Durchschnittsverbrauch", "Ø Verbrauch", "consumption", 1, "Wh/km", "24 Wh/km");

    final int bit;
    final String label;
    public final String tileLabel;
    public final String key;
    public final int precision;
    public final String unit;
    final String example;

    CockpitReading(int bit, String label, String tileLabel, String key, int precision, String unit, String example) {
        this.bit = bit;
        this.label = label;
        this.tileLabel = tileLabel;
        this.key = key;
        this.precision = precision;
        this.unit = unit;
        this.example = example;
    }

    public boolean isDriveReading() {
        return this == PHASE || this == MOTOR_TEMP || this == ESC_TEMP;
    }

    boolean hiddenIn(int mask) {
        return (bit & mask) != 0;
    }

    static int allowedMask() {
        int mask = 0;
        for (CockpitReading reading : values()) mask |= reading.bit;
        return mask;
    }
}
