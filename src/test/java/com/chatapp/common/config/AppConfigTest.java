package com.chatapp.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import static org.junit.jupiter.api.Assertions.*;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class AppConfigTest {
    @Test void fileTransportMustBeExplicitAndRelayPortBounded() {
        String oldMode = System.getProperty("CHATAPP_FILE_MODE");
        String oldPort = System.getProperty("CHATAPP_FILE_RELAY_PORT");
        try {
            for (String mode : new String[]{"p2p", "relay"}) {
                System.setProperty("CHATAPP_FILE_MODE", mode);
                assertEquals(mode, AppConfig.fileMode());
            }
            System.setProperty("CHATAPP_FILE_MODE", "other");
            assertThrows(SecurityException.class, AppConfig::fileMode);
            for (String port : new String[]{"0", "1023", "65536", "bad"}) {
                System.setProperty("CHATAPP_FILE_RELAY_PORT", port);
                assertThrows(SecurityException.class, AppConfig::relayPort);
            }
            System.setProperty("CHATAPP_FILE_RELAY_PORT", "8889");
            assertEquals(8889, AppConfig.relayPort());
        } finally {
            if (oldMode == null) System.clearProperty("CHATAPP_FILE_MODE"); else System.setProperty("CHATAPP_FILE_MODE", oldMode);
            if (oldPort == null) System.clearProperty("CHATAPP_FILE_RELAY_PORT"); else System.setProperty("CHATAPP_FILE_RELAY_PORT", oldPort);
        }
    }
    @Test void portAndTimeoutOverridesAreBoundedAndErrorsDoNotExposeValues() {
        String[] names = {"CHATAPP_SERVER_PORT", "CHATAPP_CONNECT_TIMEOUT_MS", "CHATAPP_REQUEST_TIMEOUT_MS", "CHATAPP_P2P_TIMEOUT_MS"};
        for (String name : names) {
            String previous = System.getProperty(name);
            try {
                System.setProperty(name, "12345");
                assertEquals(12345, AppConfig.integer(name, 1, 1, 65535));
                for (String invalid : new String[]{"-1", "0", "999999999999999", "secret-not-a-number"}) {
                    System.setProperty(name, invalid);
                    SecurityException error = assertThrows(SecurityException.class, () -> AppConfig.integer(name, 1, 1, 65535));
                    assertTrue(error.getMessage().contains(name));
                    assertFalse(error.getMessage().contains(invalid));
                }
                if (name.equals("CHATAPP_SERVER_PORT")) {
                    System.setProperty(name, "1023");
                    assertThrows(SecurityException.class, AppConfig::serverPort);
                    System.setProperty(name, "65536");
                    assertThrows(SecurityException.class, AppConfig::serverPort);
                } else {
                    System.setProperty(name, "300001");
                    assertThrows(SecurityException.class, () -> AppConfig.integer(name, 10000, 1, 300000));
                }
            } finally {
                if (previous == null) System.clearProperty(name); else System.setProperty(name, previous);
            }
        }
    }

    @Test void absentSettingUsesFallbackAndDatabasePathSupportsSpaces() {
        assertEquals("fallback", AppConfig.get("CHATAPP_TEST_ABSENT_SETTING", "fallback"));
        String previous = System.getProperty("CHATAPP_DB_PATH");
        try {
            System.setProperty("CHATAPP_DB_PATH", "custom data/chat.db");
            assertEquals(java.nio.file.Path.of("custom data/chat.db"), AppConfig.databasePath());
            System.setProperty("CHATAPP_DB_PATH", "\u0000");
            assertThrows(SecurityException.class, AppConfig::validate);
        } finally {
            if (previous == null) System.clearProperty("CHATAPP_DB_PATH"); else System.setProperty("CHATAPP_DB_PATH", previous);
        }
    }
}
