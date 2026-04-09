package pl.viko.itemalchemyaddon.client;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * Data-generation entry point.
 *
 * <p>Currently a placeholder — no data providers are registered yet.
 * Add providers to the created pack as the mod grows.</p>
 */
public class ItemAlchemyAddonDataGenerator implements DataGeneratorEntrypoint {

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
        FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
    }
}
