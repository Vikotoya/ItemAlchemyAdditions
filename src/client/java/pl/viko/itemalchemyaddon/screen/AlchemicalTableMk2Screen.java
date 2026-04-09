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

/**
 * Client-side screen for the Alchemical Table Mk2.
 *
 * <p>In addition to the standard 19-slot inventory inherited from
 * {@link AlchemicalTableMk2ScreenHandler}, this screen renders:</p>
 * <ul>
 *   <li>A scrollable grid of items available for transmutation, drawn on the
 *       right side of the GUI.</li>
 *   <li>A row of creative-inventory-style tabs for filtering the item list by
 *       item group.</li>
 *   <li>Filter and Sort buttons for further narrowing or reordering items.</li>
 *   <li>The player's current EMC balance in the foreground layer.</li>
 * </ul>
 *
 * <p>Clicking an item in the transmutation list sends a
 * {@link pl.viko.itemalchemyaddon.networking.packet.RequestItemC2SPacket}
 * to the server to deduct EMC and grant the item.</p>
 */
public class AlchemicalTableMk2Screen extends HandledScreen<AlchemicalTableMk2ScreenHandler> {

    // ── Filter / Sort enums ─────────────────────────────────────────────

    /**
     * Determines which items appear in the transmutation list.
     */
    private enum FilterMode {
        /** Only items the player's team has already learned. */
        KNOWN_ONLY("K"),
        /** Items that have a positive EMC value (learned or not). */
        KNOWN_AND_UNLEARNED("K&U"),
        /** Every item from the selected item-group tab. */
        ALL("All");

        private final String label;

        FilterMode(String label) { this.label = label; }

        public String getLabel() { return label; }

        public FilterMode next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    /**
     * Controls the ordering of items in the transmutation list.
     */
    private enum SortMode {
        DEFAULT("Default"),
        A_Z("A-Z"),
        Z_A("Z-A"),
        EMC_ASC("EMC Asc"),
        EMC_DESC("EMC Desc");

        private final String label;

        SortMode(String label) { this.label = label; }

        public String getLabel() { return label; }

        public SortMode next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    // ── Constants ────────────────────────────────────────────────────────

    private static final Identifier TEXTURE = new Identifier(ItemAlchemyAddon.MOD_ID, "textures/gui/alchemical_table_mk2_gui.png");

    private static final int FILTER_BUTTON_X_OFFSET = 200;
    private static final int FILTER_BUTTON_Y_OFFSET = 170;
    private static final int SORT_BUTTON_X_OFFSET = 120;
    private static final int SORT_BUTTON_Y_OFFSET = 170;
    private static final int BUTTON_WIDTH = 70;
    private static final int BUTTON_HEIGHT = 20;

    /** Number of item columns in the transmutation grid. */
    private static final int ROW_COUNT = 10;

    // ── Fields ───────────────────────────────────────────────────────────

    private List<ItemStack> itemsToShow = new ArrayList<>();
    private float scrollOffset;
    private boolean isDragging;
    private int listX, listY, listWidth, listHeight;

    private final List<ItemGroup> itemGroups = new ArrayList<>();
    private int selectedItemGroupIndex;
    private float tabsScrollOffset;
    private boolean isTabsDragging;

    private FilterMode currentFilterMode = FilterMode.KNOWN_ONLY;
    private SortMode currentSortMode = SortMode.DEFAULT;

    private int filterButtonX, filterButtonY;
    private int sortButtonX, sortButtonY;

    // ── Constructor ──────────────────────────────────────────────────────

    public AlchemicalTableMk2Screen(AlchemicalTableMk2ScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 284;
        this.backgroundHeight = 276;
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        this.listX = this.x + 80;
        this.listY = this.y + 36;
        this.listWidth = 180;
        this.listHeight = 124;

        this.filterButtonX = this.x + FILTER_BUTTON_X_OFFSET;
        this.filterButtonY = this.y + FILTER_BUTTON_Y_OFFSET;
        this.sortButtonX = this.x + SORT_BUTTON_X_OFFSET;
        this.sortButtonY = this.y + SORT_BUTTON_Y_OFFSET;

        this.itemGroups.clear();
        this.itemGroups.addAll(ItemGroups.getGroups());

        // Move the "Search" tab to the front so it acts as the default view
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

        updateItemsBasedOnTab();
    }

    // ── Public helpers ───────────────────────────────────────────────────

    /**
     * Returns the {@link ItemStack} from the virtual transmutation list that
     * the mouse is currently hovering over, or {@code null} if none.
     *
     * <p>This is used by the EMC-edit key handler to identify items that are
     * not backed by real inventory slots.</p>
     *
     * @param mouseX scaled mouse X coordinate
     * @param mouseY scaled mouse Y coordinate
     * @return the hovered stack, or {@code null}
     */
    @Nullable
    public ItemStack getHoveredStackFromList(double mouseX, double mouseY) {
        for (int i = 0; i < itemsToShow.size(); i++) {
            int itemX = listX + (i % ROW_COUNT) * 18;
            int itemY = listY + (i / ROW_COUNT) * 18 - (int) this.scrollOffset;

            if (itemY >= listY - 18 && itemY < listY + listHeight) {
                if (mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16) {
                    return itemsToShow.get(i);
                }
            }
        }
        return null;
    }

    // ── Item list logic ──────────────────────────────────────────────────

    /**
     * Rebuilds {@link #itemsToShow} by applying the current tab, filter mode,
     * and sort mode.
     */
    private void updateItemsBasedOnTab() {
        this.itemsToShow.clear();

        // 1. Retrieve the player's team-learned item IDs
        List<String> learnedItemIds = new ArrayList<>();
        if (ItemAlchemyClient.itemAlchemyNbt != null) {
            TeamState teamState = new TeamState();
            teamState.readNbt(ItemAlchemyClient.itemAlchemyNbt.getCompound("team"));
            learnedItemIds = teamState.registeredItems;
        }

        // 2. Collect base items from the selected tab
        ItemGroup selectedGroup = this.itemGroups.get(this.selectedItemGroupIndex);
        Collection<ItemStack> baseItems;

        if (selectedGroup.getType() == ItemGroup.Type.SEARCH) {
            baseItems = new ArrayList<>();
            Registries.ITEM.forEach(item -> baseItems.add(item.getDefaultStack()));
        } else {
            baseItems = selectedGroup.getDisplayStacks();
        }

        // 3. Filter
        List<ItemStack> filteredItems = new ArrayList<>();
        switch (currentFilterMode) {
            case KNOWN_ONLY -> {
                for (ItemStack stack : baseItems) {
                    if (learnedItemIds.contains(ItemUtil.toId(stack.getItem()).toString())) {
                        filteredItems.add(stack);
                    }
                }
            }
            case KNOWN_AND_UNLEARNED -> {
                for (ItemStack stack : baseItems) {
                    if (EMCManager.get(stack.getItem()) > 0) {
                        filteredItems.add(stack);
                    }
                }
            }
            case ALL -> filteredItems.addAll(baseItems);
        }

        // 4. Sort
        switch (currentSortMode) {
            case A_Z -> filteredItems.sort(Comparator.comparing(stack -> stack.getName().getString()));
            case Z_A -> filteredItems.sort(Comparator.comparing((ItemStack stack) -> stack.getName().getString()).reversed());
            case EMC_ASC -> filteredItems.sort(Comparator.comparingLong(stack -> EMCManager.get(stack.getItem())));
            case EMC_DESC -> filteredItems.sort(Comparator.comparingLong((ItemStack stack) -> EMCManager.get(stack.getItem())).reversed());
            case DEFAULT -> { /* preserve natural ordering */ }
        }

        this.itemsToShow = filteredItems;
    }

    // ── Rendering ────────────────────────────────────────────────────────

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

        // Periodically refresh the item list (~every second)
        if (this.client != null && this.client.player != null && this.client.player.age % 20 == 0) {
            updateItemsBasedOnTab();
        }

        // ── Tabs (Part 1: inactive tabs drawn behind the GUI) ──
        int tabX = this.x + 8;
        int tabY = this.y - 26;
        int tabWidth = 26;
        int tabHeight = 32;
        int tabMargin = 2;

        for (int i = 0; i < this.itemGroups.size(); i++) {
            if (i != this.selectedItemGroupIndex) {
                int currentTabX = tabX + i * (tabWidth + tabMargin) - (int) this.tabsScrollOffset;
                if (currentTabX < this.x + this.backgroundWidth - 8 && currentTabX > this.x + 8 - tabWidth) {
                    context.drawTexture(new Identifier("minecraft", "textures/gui/container/creative_inventory/tabs.png"), currentTabX, tabY, 0, 0, tabWidth, tabHeight, 256, 256);
                }
            }
        }

        // Main background + slot layer
        super.render(context, mouseX, mouseY, delta);

        // ── Tabs (Part 2: active tab + icons, drawn on top) ──
        context.enableScissor(this.x, 0, this.x + this.backgroundWidth, this.y + this.backgroundHeight);

        for (int i = 0; i < this.itemGroups.size(); i++) {
            int currentTabX = tabX + i * (tabWidth + tabMargin) - (int) this.tabsScrollOffset;
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

        // ── Tab scrollbar ──
        int tabsScrollbarX = this.x + 8;
        int tabsScrollbarY = this.y + 4;
        int tabsScrollbarWidth = this.backgroundWidth - 16;
        int totalTabsWidth = this.itemGroups.size() * (tabWidth + tabMargin) - tabMargin;
        int maxTabsScroll = Math.max(0, totalTabsWidth - tabsScrollbarWidth);
        if (maxTabsScroll > 0) {
            int handleWidth = (int) ((float) tabsScrollbarWidth / totalTabsWidth * tabsScrollbarWidth);
            handleWidth = MathHelper.clamp(handleWidth, 8, tabsScrollbarWidth);
            int handleX = tabsScrollbarX + (int) (this.tabsScrollOffset / maxTabsScroll * (tabsScrollbarWidth - handleWidth));
            context.fill(handleX, tabsScrollbarY, handleX + handleWidth, tabsScrollbarY + 2, 0xFFC0C0C0);
        }

        // ── Filter / Sort buttons ──
        context.fill(filterButtonX, filterButtonY, filterButtonX + BUTTON_WIDTH, filterButtonY + BUTTON_HEIGHT, 0x80000000);
        context.drawCenteredTextWithShadow(this.textRenderer, "Filter: " + currentFilterMode.getLabel(), filterButtonX + BUTTON_WIDTH / 2, filterButtonY + 6, 0xFFFFFF);

        context.fill(sortButtonX, sortButtonY, sortButtonX + BUTTON_WIDTH, sortButtonY + BUTTON_HEIGHT, 0x80000000);
        context.drawCenteredTextWithShadow(this.textRenderer, "Sort: " + currentSortMode.getLabel(), sortButtonX + BUTTON_WIDTH / 2, sortButtonY + 6, 0xFFFFFF);

        // ── Item grid ──
        ItemStack hoveredStack = null;
        List<String> learnedItemIds = new ArrayList<>();
        if (ItemAlchemyClient.itemAlchemyNbt != null) {
            TeamState teamState = new TeamState();
            teamState.readNbt(ItemAlchemyClient.itemAlchemyNbt.getCompound("team"));
            learnedItemIds = teamState.registeredItems;
        }

        context.enableScissor(listX, listY, listX + listWidth, listY + listHeight);
        for (int i = 0; i < itemsToShow.size(); i++) {
            ItemStack stack = itemsToShow.get(i);
            int itemX = listX + (i % ROW_COUNT) * 18;
            int itemY = listY + (i / ROW_COUNT) * 18 - (int) this.scrollOffset;
            if (itemY >= listY - 18 && itemY < listY + listHeight) {
                boolean isLearned = learnedItemIds.contains(ItemUtil.toId(stack.getItem()).toString());
                boolean hasEmc = EMCManager.get(stack.getItem()) > 0;

                context.drawItem(stack, itemX, itemY);

                // Red tint for items with no EMC value (visible in ALL mode)
                if (currentFilterMode == FilterMode.ALL && !hasEmc) {
                    context.fill(itemX, itemY, itemX + 16, itemY + 16, 0x40FF0000);
                }

                // Semi-transparent overlay for unlearned items
                if (!isLearned && (currentFilterMode == FilterMode.KNOWN_AND_UNLEARNED || currentFilterMode == FilterMode.ALL)) {
                    context.fill(itemX, itemY, itemX + 16, itemY + 16, 0x80101010);
                }

                if (mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16) {
                    context.fill(itemX, itemY, itemX + 16, itemY + 16, 0x80FFFFFF);
                    hoveredStack = stack;
                }
            }
        }
        context.disableScissor();

        // ── Item scrollbar ──
        int scrollbarX = listX + listWidth + 3;
        int scrollbarY = listY;
        int scrollbarHeight = listHeight;
        context.fill(scrollbarX, scrollbarY, scrollbarX + 6, scrollbarY + scrollbarHeight, 0xFF808080);
        int maxScroll = Math.max(0, (itemsToShow.size() + ROW_COUNT - 1) / ROW_COUNT * 18 - listHeight);
        if (maxScroll > 0) {
            int handleHeight = (int) ((float) scrollbarHeight / (scrollbarHeight + maxScroll) * scrollbarHeight);
            handleHeight = MathHelper.clamp(handleHeight, 8, scrollbarHeight);
            int handleY = scrollbarY + (int) (this.scrollOffset / maxScroll * (scrollbarHeight - handleHeight));
            context.fill(scrollbarX, handleY, scrollbarX + 6, handleY + handleHeight, 0xFFC0C0C0);
        }

        // ── Tooltips ──
        drawMouseoverTooltip(context, mouseX, mouseY);
        if (hoveredStack != null) {
            context.drawTooltip(this.textRenderer, getTooltipFromItem(this.client, hoveredStack), mouseX, mouseY);
        }
    }

    // ── Input handling ───────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int tabY = this.y - 26;
        int tabHeight = 32;

        // Scroll over the tab row
        if (mouseY >= tabY && mouseY < tabY + tabHeight) {
            int tabsScrollbarWidth = this.backgroundWidth - 16;
            int tabWidthWithMargin = 26 + 2;
            int totalTabsWidth = this.itemGroups.size() * tabWidthWithMargin - 12;
            int maxTabsScroll = Math.max(0, totalTabsWidth - tabsScrollbarWidth);
            this.tabsScrollOffset = (float) MathHelper.clamp(this.tabsScrollOffset - amount * tabWidthWithMargin, 0, maxTabsScroll);
            return true;
        }

        // Otherwise scroll the item list
        int maxScroll = Math.max(0, (itemsToShow.size() + ROW_COUNT - 1) / ROW_COUNT * 18 - listHeight);
        this.scrollOffset = (float) MathHelper.clamp(this.scrollOffset - amount * 10, 0, maxScroll);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Filter button
        if (mouseX >= filterButtonX && mouseX < filterButtonX + BUTTON_WIDTH
                && mouseY >= filterButtonY && mouseY < filterButtonY + BUTTON_HEIGHT) {
            this.currentFilterMode = this.currentFilterMode.next();
            updateItemsBasedOnTab();
            return true;
        }

        // Sort button
        if (mouseX >= sortButtonX && mouseX < sortButtonX + BUTTON_WIDTH
                && mouseY >= sortButtonY && mouseY < sortButtonY + BUTTON_HEIGHT) {
            this.currentSortMode = this.currentSortMode.next();
            updateItemsBasedOnTab();
            return true;
        }

        // Item scrollbar drag start
        int scrollbarX = listX + listWidth + 3;
        if (mouseX >= scrollbarX && mouseX < scrollbarX + 6 && mouseY >= listY && mouseY < listY + listHeight) {
            this.isDragging = true;
            return true;
        }

        // Tab scrollbar drag start
        int tabsScrollbarX = this.x + 8;
        int tabsScrollbarY = this.y + 4;
        int tabsScrollbarWidth = this.backgroundWidth - 16;
        if (mouseX >= tabsScrollbarX && mouseX < tabsScrollbarX + tabsScrollbarWidth
                && mouseY >= tabsScrollbarY && mouseY < tabsScrollbarY + 2) {
            this.isTabsDragging = true;
            return true;
        }

        // Tab click
        int tabX = this.x + 8;
        int tabY = this.y - 26;
        int tabWidth = 26;
        int tabMargin = 2;
        for (int i = 0; i < this.itemGroups.size(); i++) {
            int currentTabX = tabX + i * (tabWidth + tabMargin) - (int) this.tabsScrollOffset;
            int currentTabY = (i == this.selectedItemGroupIndex) ? tabY - 2 : tabY;
            if (mouseX >= currentTabX && mouseX < currentTabX + tabWidth && mouseY >= currentTabY && mouseY < currentTabY + 32
                    && currentTabX < this.x + this.backgroundWidth - 8 && currentTabX > this.x + 8 - tabWidth) {
                this.selectedItemGroupIndex = i;
                updateItemsBasedOnTab();
                return true;
            }
        }

        // Item click — send request to server
        if (mouseX >= listX && mouseX < listX + listWidth && mouseY >= listY && mouseY < listY + listHeight) {
            int index = (int) ((mouseY - listY + scrollOffset) / 18) * ROW_COUNT + (int) ((mouseX - listX) / 18);
            if (index >= 0 && index < itemsToShow.size()) {
                ItemStack clickedStack = itemsToShow.get(index);
                boolean isShiftDown = hasShiftDown();

                int clickType;
                if (button == 0 && !isShiftDown)      clickType = 0;
                else if (button == 1 && !isShiftDown)  clickType = 1;
                else if (button == 0)                   clickType = 2;
                else if (button == 1)                   clickType = 3;
                else return super.mouseClicked(mouseX, mouseY, button);

                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeItemStack(clickedStack);
                buf.writeInt(clickType);
                ClientPlayNetworking.send(ModMessages.REQUEST_ITEM_ID, buf);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.isDragging) {
            int maxScroll = Math.max(0, (itemsToShow.size() + ROW_COUNT - 1) / ROW_COUNT * 18 - listHeight);
            if (maxScroll > 0) {
                float scrollPercentage = (float) ((mouseY - listY) / listHeight);
                this.scrollOffset = MathHelper.clamp(scrollPercentage * maxScroll, 0, maxScroll);
            }
            return true;
        }

        if (this.isTabsDragging) {
            int tabsScrollbarWidth = this.backgroundWidth - 16;
            int tabWidthWithMargin = 26 + 2;
            int totalTabsWidth = this.itemGroups.size() * tabWidthWithMargin - 2;
            int maxTabsScroll = Math.max(0, totalTabsWidth - tabsScrollbarWidth);
            if (maxTabsScroll > 0) {
                float scrollPercentage = (float) ((mouseX - (this.x + 8)) / tabsScrollbarWidth);
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
