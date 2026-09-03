package com.luokuiai.liquibase.kingbase;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class KingbasePostgresDatabaseTest {
    @Test
    void returnsKingbaseDriverForKingbaseUrl() {
        KingbasePostgresDatabase database = new KingbasePostgresDatabase();

        assertEquals(KingbaseSupport.DRIVER_CLASS,
                database.getDefaultDriver("jdbc:kingbase8://localhost:54321/test"));
    }

    @Test
    void exposesKingbaseShortName() {
        KingbasePostgresDatabase database = new KingbasePostgresDatabase();

        assertEquals("kingbase", database.getShortName());
    }
}
