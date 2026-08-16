package com.wztwzt.ae2_qof;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static String greeting = "Hello World";

    public static int exIOPortTransferContentsRate = 1024;

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        greeting = configuration.getString("greeting", Configuration.CATEGORY_GENERAL, greeting, "How shall I greet?");
        exIOPortTransferContentsRate = configuration.getInt(
            "exIOPortTransferContentsRate",
            Configuration.CATEGORY_GENERAL,
            exIOPortTransferContentsRate,
            1,
            Integer.MAX_VALUE,
            "Multiplier for the item transfer rate of the Enhanced IO Port. Base transfer quantity = 256.");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
