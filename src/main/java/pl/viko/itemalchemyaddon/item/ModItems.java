package pl.viko.itemalchemyaddon.item;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import pl.viko.itemalchemyaddon.ItemAlchemyAddon;

/**
 * Registers all custom items and the creative-mode item group for this mod.
 */
public class ModItems {

    /** The Alchemical Table Mk2 item (max stack size 1). */
    public static final Item ALCHEMICAL_TABLE_MK2 = registerItem("alchemical_table_mk2",
            new AlchemicalTableMk2Item(new FabricItemSettings().maxCount(1)));

    /** Creative-mode tab containing this mod's items. */
    public static final ItemGroup ITEM_ALCHEMY_ADDON_GROUP = Registry.register(Registries.ITEM_GROUP,
            new Identifier(ItemAlchemyAddon.MOD_ID, "item_alchemy_addon_group"),
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemgroup.itemalchemyaddon"))
                    .icon(() -> new ItemStack(ALCHEMICAL_TABLE_MK2))
                    .entries((displayContext, entries) -> entries.add(ALCHEMICAL_TABLE_MK2))
                    .build());

    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(ItemAlchemyAddon.MOD_ID, name), item);
    }

    /**
     * Forces class loading, which triggers the static field initialisers above
     * and therefore registers all items and the item group.
     */
    public static void registerModItems() {
        // Intentionally empty — class loading performs the registration.
    }
}
