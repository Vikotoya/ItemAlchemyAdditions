package pl.viko.itemalchemyaddon.item;

import net.minecraft.item.Item;
import net.pitan76.mcpitanlib.api.item.CreativeTabBuilder;
import net.pitan76.mcpitanlib.api.item.v2.CompatibleItemSettings;
import net.pitan76.mcpitanlib.api.registry.result.RegistryResult;
import net.pitan76.mcpitanlib.api.util.ItemStackUtil;
import net.pitan76.mcpitanlib.api.util.TextUtil;

import java.util.function.Supplier;

import static pl.viko.itemalchemyaddon.ItemAlchemyAddon._id;
import static pl.viko.itemalchemyaddon.ItemAlchemyAddon.registry;

/**
 * Registers all custom items and the creative-mode item group for this mod.
 */
public class ModItems {

    /** The Alchemical Table Mk2 item (max stack size 1). */
    public static RegistryResult<Item> ALCHEMICAL_TABLE_MK2;

    /** Creative-mode tab containing this mod's items. */
    public static final CreativeTabBuilder ITEM_ALCHEMY_ADDON_GROUP = CreativeTabBuilder.create(_id("item_alchemy_addon_group"))
            .setDisplayName(TextUtil.translatable("itemgroup.itemalchemyaddon"))
            .setIcon(() -> ItemStackUtil.create(ALCHEMICAL_TABLE_MK2.getOrNull()));

    private static RegistryResult<Item> registerItem(String name, Supplier<Item> item) {
        return registry.registerItem(_id(name), item);
    }

    /**
     * Forces class loading, which triggers the static field initialisers above
     * and therefore registers all items and the item group.
     */
    public static void registerModItems() {
        ALCHEMICAL_TABLE_MK2 = registerItem("alchemical_table_mk2",
                () -> new AlchemicalTableMk2Item(new CompatibleItemSettings(_id("alchemical_table_mk2")).maxCount(1).addGroup(ITEM_ALCHEMY_ADDON_GROUP)));

        registry.registerItemGroup(ITEM_ALCHEMY_ADDON_GROUP);
    }
}
