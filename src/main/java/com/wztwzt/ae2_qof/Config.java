package com.wztwzt.ae2_qof;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static String greeting = "Hello World";

    public static int exIOPortTransferContentsRate = 1024;

    public static int smartDoublingMaxRounds = 64;

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
        smartDoublingMaxRounds = configuration.getInt(
            "smartDoublingMaxRounds",
            Configuration.CATEGORY_GENERAL,
            smartDoublingMaxRounds,
            1,
            4096,
            "Maximum multiplier for Smart Doubling: how many pattern rounds an ME Interface with the Smart Doubling toggle enabled may receive at once from the crafting CPU.");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
