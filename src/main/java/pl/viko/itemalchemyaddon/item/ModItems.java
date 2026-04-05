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

public class ModItems {

    // 1. Definiujemy nasz nowy przedmiot
    public static final Item ALCHEMICAL_TABLE_MK2 = registerItem("alchemical_table_mk2",
            new AlchemicalTableMk2Item(new FabricItemSettings().maxCount(1)));

    // 2. Tworzymy nową zakładkę w trybie kreatywnym dla naszego addonu
    public static final ItemGroup ITEM_ALCHEMY_ADDON_GROUP = Registry.register(Registries.ITEM_GROUP,
            new Identifier(ItemAlchemyAddon.MOD_ID, "item_alchemy_addon_group"),
            FabricItemGroup.builder()
                    .displayName(Text.translatable("itemgroup.itemalchemyaddon"))
                    .icon(() -> new ItemStack(ALCHEMICAL_TABLE_MK2))
                    .entries((displayContext, entries) -> {
                        // Dodajemy nasz stół do tej zakładki
                        entries.add(ALCHEMICAL_TABLE_MK2);
                    }).build());


    // Prywatna metoda pomocnicza do rejestracji przedmiotów
    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(ItemAlchemyAddon.MOD_ID, name), item);
    }

    // Metoda, którą wywołamy, aby uruchomić rejestrację
    public static void registerModItems() {
        // Ta linijka jest pusta, ale jej wywołanie w głównej klasie moda
        // spowoduje załadowanie tej klasy i wykonanie powyższego kodu.
    }
}
