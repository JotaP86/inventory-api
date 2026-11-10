package com.warehouse.inventory;

import com.warehouse.inventory.config.AppConfig;
import java.io.IOException;

public final class App {

    private App() {
    }

    public static void main(String[] args) throws IOException {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT %4$s %5$s%6$s%n");

        Application application = new Application(AppConfig.fromEnvironment());
        Runtime.getRuntime().addShutdownHook(new Thread(application::stop));
        application.start();
    }
}
