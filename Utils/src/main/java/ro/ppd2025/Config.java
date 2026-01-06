package ro.ppd2025;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {
    private static final Properties props = new Properties();

    static {
        try (InputStream input = Config.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new RuntimeException("❌ Nu s-a găsit config.properties!");
            }
            props.load(input);
            System.out.println("✅ Configurație încărcată cu succes.");
        } catch (IOException e) {
            throw new RuntimeException("❌ Eroare la citirea config.properties", e);
        }
    }

    // --- SERVER ---
    public static int getServerPort() {
        return Integer.parseInt(props.getProperty("server.port", "5000"));
    }

    public static int getServerRuntimeMinutes() {
        return Integer.parseInt(props.getProperty("server.runtime.minutes", "3"));
    }

    public static int getThreadPoolSize() {
        return Integer.parseInt(props.getProperty("thread.pool.size", "10"));
    }

    public static int getAllocationPoolSize() {
        return Integer.parseInt(props.getProperty("allocation.pool.size", "4"));
    }

    // --- TIMEOUT ---
    public static int getTimeoutSignMs() {
        return Integer.parseInt(props.getProperty("timeout.sign.ms", "6000"));
    }

    // --- AUDIT ---
    public static int getAuditIntervalSec() {
        return Integer.parseInt(props.getProperty("audit.interval.sec", "5"));
    }

    public static String getAuditFile() {
        return props.getProperty("audit.file", "audit_log.txt");
    }

    // --- RESOURCES ---
    public static int getDepotCount() {
        return Integer.parseInt(props.getProperty("depot.count", "3"));
    }

    public static int getVehicleTypes() {
        return Integer.parseInt(props.getProperty("vehicle.types", "3"));
    }

    public static int getInitialVehiclesMin() {
        return Integer.parseInt(props.getProperty("initial.vehicles.min", "5"));
    }

    public static int getInitialVehiclesMax() {
        return Integer.parseInt(props.getProperty("initial.vehicles.max", "10"));
    }

    // --- CLIENT ---
    public static int getClientRequestIntervalSec() {
        return Integer.parseInt(props.getProperty("client.request.interval.sec", "3"));
    }

    public static int getClientConfirmDelayMinMs() {
        return Integer.parseInt(props.getProperty("client.confirm.delay.min.ms", "2000"));
    }

    public static int getClientConfirmDelayMaxMs() {
        return Integer.parseInt(props.getProperty("client.confirm.delay.max.ms", "8000"));
    }
}