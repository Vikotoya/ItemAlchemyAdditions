package pl.viko.itemalchemyaddon.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.pitan76.itemalchemy.EMCManager;
import net.pitan76.itemalchemy.ItemAlchemyClient;
import net.pitan76.itemalchemy.data.TeamState;
import net.pitan76.mcpitanlib.api.entity.Player;
import net.pitan76.mcpitanlib.api.util.item.ItemUtil;
import org.jetbrains.annotations.Nullable;
import pl.viko.itemalchemyaddon.ItemAlchemyAddon;
import pl.viko.itemalchemyaddon.networking.ModMessages;

import java.text.NumberFormat;
import java.util.*;

public class AlchemicalTableMk2Screen extends HandledScreen<AlchemicalTableMk2ScreenHandler> {

    // --- ENUMS DLA NOWYCH TRYBÓW ---
    private enum FilterMode {
        KNOWN_ONLY("K"),
        KNOWN_AND_UNLEARNED("K&U"),
        ALL("All");

        private final String name;
        FilterMode(String name) { this.name = name; }
        public String getName() { return name; }

        public FilterMode getNext() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    private enum SortMode {
        DEFAULT("Default"),
        A_Z("A-Z"),
        Z_A("Z-A"),
        EMC_ASC("EMC Asc"),
        EMC_DESC("EMC Desc");

        private final String name;
        SortMode(String name) { this.name = name; }
        public String getName() { return name; }

        public SortMode getNext() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    private static final Identifier TEXTURE = new Identifier(ItemAlchemyAddon.MOD_ID, "textures/gui/alchemical_table_mk2_gui.png");

    private List<ItemStack> itemsToShow = new ArrayList<>();
    private float scrollOffset;
    private boolean isDragging;
    private int listX, listY, listWidth, listHeight;

    private final List<ItemGroup> itemGroups = new ArrayList<>();
    private int selectedItemGroupIndex = 0;
    private float tabsScrollOffset;
    private boolean isTabsDragging;

    // --- ZMIENNE DLA NOWYCH FUNKCJI ---
    private FilterMode currentFilterMode = FilterMode.KNOWN_ONLY;
    private SortMode currentSortMode = SortMode.DEFAULT;

    // --- POPRAWKA: Konfigurowalne pozycje przycisków ---
    private static final int FILTER_BUTTON_X_OFFSET = 200;
    private static final int FILTER_BUTTON_Y_OFFSET = 170;
    private static final int SORT_BUTTON_X_OFFSET = 120;
    private static final int SORT_BUTTON_Y_OFFSET = 170;
    private static final int BUTTON_WIDTH = 70;
    private static final int BUTTON_HEIGHT = 20;

    private int filterButtonX, filterButtonY;
    private int sortButtonX, sortButtonY;
    //private int lastKnownItemCount = -1; // Nowa zmienna

    public AlchemicalTableMk2Screen(AlchemicalTableMk2ScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 284;
        this.backgroundHeight = 276;
    }

    @Override
    protected void init() {
        super.init();
        // Twoje współrzędne
        this.listX = this.x + 80;
        this.listY = this.y + 36;
        this.listWidth = 180;
        this.listHeight = 124;

        // --- POZYCJE PRZYCISKÓW ---
        this.filterButtonX = this.x + FILTER_BUTTON_X_OFFSET;
        this.filterButtonY = this.y + FILTER_BUTTON_Y_OFFSET;
        this.sortButtonX = this.x + SORT_BUTTON_X_OFFSET;
        this.sortButtonY = this.y + SORT_BUTTON_Y_OFFSET;

        // Pobieranie i sortowanie zakładek (bez zmian)
        this.itemGroups.clear();
        this.itemGroups.addAll(ItemGroups.getGroups());
        ItemGroup searchGroup = null;
        for (ItemGroup group : this.itemGroups) {
            if (group.getType() == ItemGroup.Type.SEARCH) {
                searchGroup = group;
                break;
            }
        }
        if (searchGroup != null) {
            this.itemGroups.remove(searchGroup);
            this.itemGroups.add(0, searchGroup);
        }
        this.selectedItemGroupIndex = 0;

        updateItemsBasedOnTab(); // Aktualizujemy listę
    }


    @Nullable
    public ItemStack getHoveredStackFromList(double mouseX, double mouseY) {
        int rowCount = 10;
        // Pętla sprawdza każdy przedmiot z naszej listy
        for (int i = 0; i < itemsToShow.size(); i++) {
            int itemX = listX + (i % rowCount) * 18;
            int itemY = listY + (i / rowCount) * 18 - (int)this.scrollOffset;

            // Sprawdzamy, czy przedmiot jest widoczny
            if (itemY >= listY - 18 && itemY < listY + listHeight) {
                // Sprawdzamy, czy myszka jest nad tym przedmiotem
                if (mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16) {
                    // Jeśli tak, zwracamy ten przedmiot
                    return itemsToShow.get(i);
                }
            }
        }
        // Jeśli nie znaleziono żadnego przedmiotu, zwracamy null
        return null;
    }


    private void updateItemsBasedOnTab() {
        this.itemsToShow.clear();
        //this.scrollOffset = 0;

        // --- GŁÓWNA LOGIKA FILTROWANIA I SORTOWANIA ---

        // 1. POBIERANIE DANYCH
        List<String> learnedItemIds = new ArrayList<>();
        if (ItemAlchemyClient.itemAlchemyNbt != null) {
            TeamState teamState = new TeamState();
            teamState.readNbt(ItemAlchemyClient.itemAlchemyNbt.getCompound("team"));
            learnedItemIds = teamState.registeredItems;
        }

        ItemGroup selectedGroup = this.itemGroups.get(this.selectedItemGroupIndex);
        Collection<ItemStack> baseItems;

        // --- POPRAWKA: Logika pobierania przedmiotów dla wszystkich zakładek ---
        if (selectedGroup.getType() == ItemGroup.Type.SEARCH) {
            baseItems = new ArrayList<>();
            // Dla zakładki "Search", pobieramy wszystkie przedmioty z gry
            Registries.ITEM.forEach(item -> baseItems.add(item.getDefaultStack()));
        } else {
            // Dla innych zakładek, pobieramy ich standardową zawartość
            baseItems = selectedGroup.getDisplayStacks();
        }

        // 2. FILTROWANIE
        List<ItemStack> filteredItems = new ArrayList<>();
        switch (currentFilterMode) {
            case KNOWN_ONLY:
                for (ItemStack stack : baseItems) {
                    if (learnedItemIds.contains(ItemUtil.toId(stack.getItem()).toString())) {
                        filteredItems.add(stack);
                    }
                }
                break;
            case KNOWN_AND_UNLEARNED:
                for (ItemStack stack : baseItems) {
                    if (EMCManager.get(stack.getItem()) > 0) {
                        filteredItems.add(stack);
                    }
                }
                break;
            case ALL:
                filteredItems.addAll(baseItems);
                break;
        }

        // 3. SORTOWANIE
        switch (currentSortMode) {
            case A_Z:
                filteredItems.sort(Comparator.comparing(stack -> stack.getName().getString()));
                break;
            case Z_A:
                filteredItems.sort(Comparator.comparing((ItemStack stack) -> stack.getName().getString()).reversed());
                break;
            case EMC_ASC:
                filteredItems.sort(Comparator.comparingLong(stack -> EMCManager.get(stack.getItem())));
                break;
            case EMC_DESC:
                filteredItems.sort(Comparator.comparingLong((ItemStack stack) -> EMCManager.get(stack.getItem())).reversed());
                break;
            case DEFAULT:
                // Pozostawiamy domyślną kolejność
                break;
        }

        this.itemsToShow = filteredItems;
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        long currentEmc = EMCManager.getEmcFromPlayer(new Player(this.client.player));
        String emcText = NumberFormat.getNumberInstance(Locale.US).format(currentEmc);
        context.drawText(this.textRenderer, "EMC: " + emcText, 8, 167, 0x404040, false);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight, backgroundWidth, backgroundHeight);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {

        // --- NOWA LOGIKA: ODŚWIEŻANIE LISTY ---
        // Co 20 ticków (1 sekunda), odświeżamy listę przedmiotów
        if (this.client != null && this.client.player != null && this.client.player.age % 20 == 0) {
            updateItemsBasedOnTab();
        }

        // --- RYSOWANIE ZAKŁADEK (CZĘŚĆ 1: NIEAKTYWNE ZAKŁADKI) ---
        int tabX = this.x + 8;
        int tabY = this.y - 26; // Obniżamy zakładki o dodatkowe 2 piksele
        int tabWidth = 26;
        int tabHeight = 32;
        int tabMargin = 2; // Odstęp między zakładkami

        // Rysujemy najpierw nieaktywne zakładki, aby były "pod" głównym GUI
        for (int i = 0; i < this.itemGroups.size(); i++) {
            if (i != this.selectedItemGroupIndex) {
                int currentTabX = tabX + i * (tabWidth + tabMargin) - (int)this.tabsScrollOffset;
                if (currentTabX < this.x + this.backgroundWidth - 8 && currentTabX > this.x + 8 - tabWidth) {
                    context.drawTexture(new Identifier("minecraft", "textures/gui/container/creative_inventory/tabs.png"), currentTabX, tabY, 0, 0, tabWidth, tabHeight, 256, 256);
                }
            }
        }

        // Rysujemy główne tło i sloty
        super.render(context, mouseX, mouseY, delta);

        // --- RYSOWANIE ZAKŁADEK (CZĘŚĆ 2: AKTYWNA ZAKŁADKA I IKONY) ---
        context.enableScissor(this.x, 0, this.x + this.backgroundWidth, this.y + this.backgroundHeight);

        for (int i = 0; i < this.itemGroups.size(); i++) {
            int currentTabX = tabX + i * (tabWidth + tabMargin) - (int)this.tabsScrollOffset;
            int currentTabY = tabY;

            if (currentTabX < this.x + this.backgroundWidth - 8 && currentTabX > this.x + 8 - tabWidth) {
                if (i == this.selectedItemGroupIndex) {
                    currentTabY -= 2;
                    context.drawTexture(new Identifier("minecraft", "textures/gui/container/creative_inventory/tabs.png"), currentTabX, currentTabY, 0, 32, tabWidth, tabHeight, 256, 256);
                }
                context.drawItem(this.itemGroups.get(i).getIcon(), currentTabX + 5, currentTabY + 8);
            }
        }

        context.disableScissor();

        // --- RYSOWANIE PASKA PRZEWIJANIA ZAKŁADEK ---
        int tabsScrollbarX = this.x + 8;
        int tabsScrollbarY = this.y + 4; // Pasek jest pod zakładkami
        int tabsScrollbarWidth = this.backgroundWidth - 16;
        int totalTabsWidth = this.itemGroups.size() * (tabWidth + tabMargin) - tabMargin; // Pełna szerokość wszystkich zakładek
        int maxTabsScroll = Math.max(0, totalTabsWidth - tabsScrollbarWidth);
        if (maxTabsScroll > 0) {
            int handleWidth = (int)((float)tabsScrollbarWidth / totalTabsWidth * tabsScrollbarWidth);
            handleWidth = MathHelper.clamp(handleWidth, 8, tabsScrollbarWidth);
            int handleX = tabsScrollbarX + (int)(this.tabsScrollOffset / maxTabsScroll * (tabsScrollbarWidth - handleWidth));
            context.fill(handleX, tabsScrollbarY, handleX + handleWidth, tabsScrollbarY + 2, 0xFFC0C0C0);
        }

        // --- RYSOWANIE PRZYCISKÓW ---
        context.fill(filterButtonX, filterButtonY, filterButtonX + BUTTON_WIDTH, filterButtonY + BUTTON_HEIGHT, 0x80000000);
        context.drawCenteredTextWithShadow(this.textRenderer, "Filter: " + currentFilterMode.getName(), filterButtonX + BUTTON_WIDTH / 2, filterButtonY + 6, 0xFFFFFF);

        context.fill(sortButtonX, sortButtonY, sortButtonX + BUTTON_WIDTH, sortButtonY + BUTTON_HEIGHT, 0x80000000);
        context.drawCenteredTextWithShadow(this.textRenderer, "Sort: " + currentSortMode.getName(), sortButtonX + BUTTON_WIDTH / 2, sortButtonY + 6, 0xFFFFFF);


        // --- ZAKTUALIZOWANE RYSOWANIE PRZEDMIOTÓW ---
        ItemStack hoveredStack = null;
        int rowCount = 10;
        List<String> learnedItemIds = new ArrayList<>();
        if (ItemAlchemyClient.itemAlchemyNbt != null) {
            TeamState teamState = new TeamState();
            teamState.readNbt(ItemAlchemyClient.itemAlchemyNbt.getCompound("team"));
            learnedItemIds = teamState.registeredItems;
        }

        context.enableScissor(listX, listY, listX + listWidth, listY + listHeight);
        for (int i = 0; i < itemsToShow.size(); i++) {
            ItemStack stack = itemsToShow.get(i);
            int itemX = listX + (i % rowCount) * 18;
            int itemY = listY + (i / rowCount) * 18 - (int)this.scrollOffset;
            if (itemY >= listY - 18 && itemY < listY + listHeight) {

                boolean isLearned = learnedItemIds.contains(ItemUtil.toId(stack.getItem()).toString());
                boolean hasEmc = EMCManager.get(stack.getItem()) > 0;

                // Rysujemy przedmiot
                context.drawItem(stack, itemX, itemY);

                // Efekt czerwonego tła dla przedmiotów bez EMC w trybie "ALL"
                if (currentFilterMode == FilterMode.ALL && !hasEmc) {
                    context.fill(itemX, itemY, itemX + 16, itemY + 16, 0x40FF0000);
                }

                // --- POPRAWKA: Efekt półprzezroczystości dla niepoznanych przedmiotów ---
                if (!isLearned && (currentFilterMode == FilterMode.KNOWN_AND_UNLEARNED || currentFilterMode == FilterMode.ALL)) {
                    context.fill(itemX, itemY, itemX + 16, itemY + 16, 0x80101010); // Półprzezroczysta czarna warstwa
                }

                if (mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16) {
                    context.fill(itemX, itemY, itemX + 16, itemY + 16, 0x80FFFFFF);
                    hoveredStack = stack;
                }
            }
        }
        context.disableScissor();

        // --- RYSOWANIE PASKA PRZEWIJANIA PRZEDMIOTÓW (bez zmian) ---
        int scrollbarX = listX + listWidth + 3;
        int scrollbarY = listY;
        int scrollbarHeight = listHeight;
        context.fill(scrollbarX, scrollbarY, scrollbarX + 6, scrollbarY + scrollbarHeight, 0xFF808080);
        int maxScroll = Math.max(0, (itemsToShow.size() + rowCount - 1) / rowCount * 18 - listHeight);
        if (maxScroll > 0) {
            int handleHeight = (int)((float)scrollbarHeight / (scrollbarHeight + maxScroll) * scrollbarHeight);
            handleHeight = net.minecraft.util.math.MathHelper.clamp(handleHeight, 8, scrollbarHeight);
            int handleY = scrollbarY + (int)(this.scrollOffset / maxScroll * (scrollbarHeight - handleHeight));
            context.fill(scrollbarX, handleY, scrollbarX + 6, handleY + handleHeight, 0xFFC0C0C0);
        }

        // --- RYSOWANIE TOOLTIPÓW (bez zmian) ---
        drawMouseoverTooltip(context, mouseX, mouseY);
        if (hoveredStack != null) {
            context.drawTooltip(this.textRenderer, getTooltipFromItem(this.client, hoveredStack), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int tabY = this.y - 26; // Zaktualizowana wysokość
        int tabHeight = 32;
        // Sprawdzamy, czy przewijamy nad zakładkami
        if (mouseY >= tabY && mouseY < tabY + tabHeight) {
            int tabsScrollbarWidth = this.backgroundWidth - 16;
            int tabWidthWithMargin = 26 + 2;
            int totalTabsWidth = this.itemGroups.size() * tabWidthWithMargin - 12;
            int maxTabsScroll = Math.max(0, totalTabsWidth - tabsScrollbarWidth);
            // Przewijamy o szerokość jednej zakładki
            this.tabsScrollOffset = (float)MathHelper.clamp(this.tabsScrollOffset - amount * tabWidthWithMargin, 0, maxTabsScroll);
            return true;
        }

        // Jeśli nie, przewijamy listę przedmiotów
        int rowCount = 10;
        int maxScroll = Math.max(0, (itemsToShow.size() + rowCount - 1) / rowCount * 18 - listHeight);
        this.scrollOffset = (float)MathHelper.clamp(this.scrollOffset - amount * 10, 0, maxScroll);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // --- OBSŁUGA NOWYCH PRZYCISKÓW ---
        if (mouseX >= filterButtonX && mouseX < filterButtonX + BUTTON_WIDTH && mouseY >= filterButtonY && mouseY < filterButtonY + BUTTON_HEIGHT) {
            this.currentFilterMode = this.currentFilterMode.getNext();
            updateItemsBasedOnTab();
            return true;
        }
        if (mouseX >= sortButtonX && mouseX < sortButtonX + BUTTON_WIDTH && mouseY >= sortButtonY && mouseY < sortButtonY + BUTTON_HEIGHT) {
            this.currentSortMode = this.currentSortMode.getNext();
            updateItemsBasedOnTab();
            return true;
        }

        // Kliknięcie na pasek przewijania przedmiotów
        int scrollbarX = listX + listWidth + 3;
        if (mouseX >= scrollbarX && mouseX < scrollbarX + 6 && mouseY >= listY && mouseY < listY + listHeight) {
            this.isDragging = true;
            return true;
        }

        // Kliknięcie na pasek przewijania zakładek
        int tabsScrollbarX = this.x + 8;
        int tabsScrollbarY = this.y + 4;
        int tabsScrollbarWidth = this.backgroundWidth - 16;
        if (mouseX >= tabsScrollbarX && mouseX < tabsScrollbarX + tabsScrollbarWidth && mouseY >= tabsScrollbarY && mouseY < tabsScrollbarY + 2) {
            this.isTabsDragging = true;
            return true;
        }

        // Kliknięcie na zakładkę
        int tabX = this.x + 8;
        int tabY = this.y - 26; // Zaktualizowana wysokość
        int tabWidth = 26;
        int tabMargin = 2;
        for (int i = 0; i < this.itemGroups.size(); i++) {
            int currentTabX = tabX + i * (tabWidth + tabMargin) - (int)this.tabsScrollOffset;
            int currentTabY = (i == this.selectedItemGroupIndex) ? tabY - 2 : tabY;
            // Sprawdzamy, czy klikamy na widoczną zakładkę
            if (mouseX >= currentTabX && mouseX < currentTabX + tabWidth && mouseY >= currentTabY && mouseY < currentTabY + 32
                    && currentTabX < this.x + this.backgroundWidth - 8 && currentTabX > this.x + 8 - tabWidth) {
                this.selectedItemGroupIndex = i;
                updateItemsBasedOnTab();
                return true;
            }
        }

        // Kliknięcie na przedmiot (zostaje bez zmian)
        if (mouseX >= listX && mouseX < listX + listWidth && mouseY >= listY && mouseY < listY + listHeight) {
            int index = (int)((mouseY - listY + scrollOffset) / 18) * 10 + (int)((mouseX - listX) / 18);
            if (index >= 0 && index < itemsToShow.size()) {
                ItemStack clickedStack = itemsToShow.get(index);
                int clickType = -1;
                boolean isShiftDown = hasShiftDown();
                if (button == 0 && !isShiftDown) clickType = 0;
                if (button == 1 && !isShiftDown) clickType = 1;
                if (button == 0 && isShiftDown)  clickType = 2;
                if (button == 1 && isShiftDown)  clickType = 3;
                if (clickType != -1) {
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeItemStack(clickedStack);
                    buf.writeInt(clickType);
                    ClientPlayNetworking.send(ModMessages.REQUEST_ITEM_ID, buf);
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // Przeciąganie paska przewijania przedmiotów
        if (this.isDragging) {
            int rowCount = 10;
            int maxScroll = Math.max(0, (itemsToShow.size() + rowCount - 1) / rowCount * 18 - listHeight);
            if (maxScroll > 0) {
                float scrollPercentage = (float)((mouseY - listY) / listHeight);
                this.scrollOffset = MathHelper.clamp(scrollPercentage * maxScroll, 0, maxScroll);
            }
            return true;
        }

        // Przeciąganie paska przewijania zakładek
        if (this.isTabsDragging) {
            int tabsScrollbarWidth = this.backgroundWidth - 16;
            int tabWidthWithMargin = 26 + 2;
            int totalTabsWidth = this.itemGroups.size() * tabWidthWithMargin - 2;
            int maxTabsScroll = Math.max(0, totalTabsWidth - tabsScrollbarWidth);
            if (maxTabsScroll > 0) {
                float scrollPercentage = (float)((mouseX - (this.x + 8)) / tabsScrollbarWidth);
                this.tabsScrollOffset = MathHelper.clamp(scrollPercentage * maxTabsScroll, 0, maxTabsScroll);
            }
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.isDragging = false;
            this.isTabsDragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
