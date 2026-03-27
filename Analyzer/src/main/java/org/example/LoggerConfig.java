package org.example;

import java.io.IOException;
import java.util.logging.*;

public class LoggerConfig {
    public static void setup() {
        try {
            // Get the root logger (so all classes inherit it)
            Logger rootLogger = Logger.getLogger("");

            // Remove default handlers (if you want custom ones)
            for (Handler handler : rootLogger.getHandlers()) {
                rootLogger.removeHandler(handler);
            }

            // Create a new FileHandler (overwrite file each run)
            FileHandler fileHandler = new FileHandler("app.log", false);
            fileHandler.setFormatter(new SimpleFormatter());

            // Create a ConsoleHandler
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new SimpleFormatter());

            // Add handlers to root logger
            rootLogger.addHandler(fileHandler);
            rootLogger.addHandler(consoleHandler);

            // Set global log level
            rootLogger.setLevel(Level.ALL);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
