package com.luokuiai.liquibase.dameng;

/** DM8 compatibility modes implemented by this adapter. */
public enum DamengCompatMode {
    /** Native DM_SQL behavior. */
    NATIVE(0),
    /** MySQL compatibility behavior. */
    MYSQL(4),
    /** PostgreSQL compatibility behavior. */
    POSTGRES(7);

    private final int value;

    DamengCompatMode(int value) {
        this.value = value;
    }

    /** @return the server's numeric {@code COMPATIBLE_MODE} value */
    public int getValue() {
        return value;
    }

    static DamengCompatMode fromValue(int value) {
        for (DamengCompatMode mode : values()) {
            if (mode.value == value) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unsupported DM8 COMPATIBLE_MODE="
                + value + "; supported modes are 0, 4, and 7");
    }
}
