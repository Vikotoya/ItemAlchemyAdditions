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
import pl.viko.itemalchemyaddon.screen.AlchemicalTableMk2ScreenHandler.GuiMode;

import java.text.NumberFormat;
import java.util.*;

/**
 * Client-side screen for the Alchemical Table Mk2.
 *
 * <p>Operates in two modes ({@link GuiMode#BURNING} and {@link GuiMode#UNLEARNING}),
 * synchronised from the server via the screen handler's property delegate.</p>
 *
 * <p>All interactive widgets are rendered as icon textures using
 * {@link DrawContext#drawTexture}. Filter and Sort buttons are available in
 * both modes. Mode-specific widgets (burn slot, learn toggle, confirm/deny)
 * are only rendered and clickable in their respective mode.</p>
 */
public class AlchemicalTableMk2Screen extends HandledScreen<AlchemicalTableMk2ScreenHandler> {

    // ── Filter / Sort enums ─────────────────────────────────────────────

    private enum FilterMode {
        KNOWN("filter_known"),
        ALL("filter_all"),
        UNKNOWN("filter_unknown"),
        EMC_NULL("filter_emc_null");

        private final Identifier texture;

        FilterMode(String textureName) {
            this.texture = new Identifier(ItemAlchemyAddon.MOD_ID, "textures/gui/widgets/" + textureName + ".png");
        }

        public FilterMode next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    private enum SortMode {
        ID("sort_id"),
        ABC("sort_abc"),
        EMC_DESC("sort_emc_desc"),
        EMC_ASC("sort_emc_asc");

        private final Identifier texture;

        SortMode(String textureName) {
            this.texture = new Identifier(ItemAlchemyAddon.MOD_ID, "textures/gui/widgets/" + textureName + ".png");
        }

        public SortMode next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    // ── Texture identifiers ─────────────────────────────────────────────

    private static final Identifier TEXTURE = new Identifier(ItemAlchemyAddon.MOD_ID,
            "textures/gui/alchemical_table_mk2_gui.png");
    private static final Identifier BURN_SLOT_TEX = widgetTex("burn_slot");
    private static final Identifier TOGGLE_UNLEARN_TEX = widgetTex("toggle_unlearn_mode");
    private static final Identifier TOGGLE_LEARN_ON_TEX = widgetTex("toggle_learn_on");
    private static final Identifier TOGGLE_LEARN_OFF_TEX = widgetTex("toggle_learn_off");
    private static final Identifier TEXT_LEARN_ON_TEX = widgetTex("text_learn_on");
    private static final Identifier TEXT_LEARN_OFF_TEX = widgetTex("text_learn_off");
    private static final Identifier CONFIRM_TEX = widgetTex("confirm");
    private static final Identifier DENY_TEX = widgetTex("deny");
    private static final Identifier CROSS_ICON_TEX = widgetTex("cross_icon");

    private static Identifier widgetTex(String name) {
        return new Identifier(ItemAlchemyAddon.MOD_ID, "textures/gui/widgets/" + name + ".png");
    }

    // ── Widget layout constants (offsets from GUI origin) ────────────────

    private static final int WIDGET_SIZE = 18;

    private static final int FILTER_X = 7, FILTER_Y = 32;
    private static final int SORT_X = 7, SORT_Y = 50;
    private static final int UNLEARN_TOGGLE_X = 8, UNLEARN_TOGGLE_Y = 87;

    private static final int BURN_SLOT_X = 7, BURN_SLOT_Y = 122;
    private static final int LEARN_TOGGLE_X = 195, LEARN_TOGGLE_Y = 144;
    private static final int LEARN_TOGGLE_W = 14, LEARN_TOGGLE_H = 9;
    private static final int LEARN_TEXT_X = 163, LEARN_TEXT_Y = 144;
    private static final int LEARN_TEXT_W = 29, LEARN_TEXT_H = 7;

    private static final int CONFIRM_X = 8, CONFIRM_Y = 105;
    private static final int DENY_X = 8, DENY_Y = 123;

    /** Number of item columns in the transmutation grid. */
    private static final int ROW_COUNT = 9;

    // ── Fields ───────────────────────────────────────────────────────────

    private List<ItemStack> itemsToShow = new ArrayList<>();
    private float scrollOffset;
    private boolean isDragging;
    private int listX, listY, listWidth, listHeight;

    private final List<ItemGroup> itemGroups = new ArrayList<>();
    private int selectedItemGroupIndex;
    private float tabsScrollOffset;
    private boolean isTabsDragging;

    private FilterMode currentFilterMode = FilterMode.KNOWN;
    private SortMode currentSortMode = SortMode.ID;

    /** Item IDs selected for unlearning (client-side only). */
    private final Set<String> unlearnSelection = new LinkedHashSet<>();
    private boolean isDragSelecting;

    // ── Cached learned-items list (rebuilt once per second) ──────────────

    private List<String> cachedLearnedIds = new ArrayList<>();

    // ── Constructor ──────────────────────────────────────────────────────

    public AlchemicalTableMk2Screen(AlchemicalTableMk2ScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 216;
        this.backgroundHeight = 252;
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    @Override
    protected void init() {
        super.init();
        this.listX = this.x + 30;
        this.listY = this.y + 33;
        this.listWidth = 162;
        this.listHeight = 106;

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

        refreshLearnedIds();
        updateItemsBasedOnTab();
    }

    // ── Public helpers ───────────────────────────────────────────────────

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

    // ── Data helpers ─────────────────────────────────────────────────────

    private void refreshLearnedIds() {
        if (ItemAlchemyClient.itemAlchemyNbt != null) {
            TeamState teamState = new TeamState();
            teamState.readNbt(ItemAlchemyClient.itemAlchemyNbt.getCompound("team"));
            cachedLearnedIds = teamState.registeredItems;
        } else {
            cachedLearnedIds = new ArrayList<>();
        }
    }

    private GuiMode getMode() {
        return this.handler.getMode();
    }

    // ── Item list logic ──────────────────────────────────────────────────

    private void updateItemsBasedOnTab() {
        this.itemsToShow.clear();

        ItemGroup selectedGroup = this.itemGroups.get(this.selectedItemGroupIndex);
        Collection<ItemStack> baseItems;

        if (selectedGroup.getType() == ItemGroup.Type.SEARCH) {
            baseItems = new ArrayList<>();
            Registries.ITEM.forEach(item -> baseItems.add(item.getDefaultStack()));
        } else {
            baseItems = selectedGroup.getDisplayStacks();
        }

        List<ItemStack> filteredItems = new ArrayList<>();
        switch (currentFilterMode) {
            case KNOWN -> {
                for (ItemStack stack : baseItems) {
                    if (cachedLearnedIds.contains(ItemUtil.toId(stack.getItem()).toString())) {
                        filteredItems.add(stack);
                    }
                }
            }
            case ALL -> filteredItems.addAll(baseItems);
            case UNKNOWN -> {
                for (ItemStack stack : baseItems) {
                    if (EMCManager.get(stack.getItem()) > 0
                            && !cachedLearnedIds.contains(ItemUtil.toId(stack.getItem()).toString())) {
                        filteredItems.add(stack);
                    }
                }
            }
            case EMC_NULL -> {
                for (ItemStack stack : baseItems) {
                    if (EMCManager.get(stack.getItem()) <= 0) {
                        filteredItems.add(stack);
                    }
                }
            }
        }

        switch (currentSortMode) {
            case ABC -> filteredItems.sort(Comparator.comparing(s -> s.getName().getString()));
            case EMC_DESC -> filteredItems.sort(
                    Comparator.comparingLong((ItemStack s) -> EMCManager.get(s.getItem())).reversed());
            case EMC_ASC -> filteredItems.sort(
                    Comparator.comparingLong(s -> EMCManager.get(s.getItem())));
            case ID -> { /* preserve natural ordering */ }
        }

        this.itemsToShow = filteredItems;
    }

    // ── Rendering ────────────────────────────────────────────────────────

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        long currentEmc = EMCManager.getEmcFromPlayer(new Player(this.client.player));
        String emcText = NumberFormat.getNumberInstance(Locale.US).format(currentEmc);
        context.drawText(this.textRenderer, "EMC: " + emcText, 8, 144, 0x404040, false);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int guiX = (width - backgroundWidth) / 2;
        int guiY = (height - backgroundHeight) / 2;
        context.drawTexture(TEXTURE, guiX, guiY, 0, 0, backgroundWidth, backgroundHeight,
                backgroundWidth, backgroundHeight);

        // ── Persistent widgets (both modes) ──
        context.drawTexture(currentFilterMode.texture,
                guiX + FILTER_X, guiY + FILTER_Y, 0, 0, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE);
        context.drawTexture(currentSortMode.texture,
                guiX + SORT_X, guiY + SORT_Y, 0, 0, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE);
        context.drawTexture(TOGGLE_UNLEARN_TEX,
                guiX + UNLEARN_TOGGLE_X, guiY + UNLEARN_TOGGLE_Y, 0, 0, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE);

        if (getMode() == GuiMode.BURNING) {
            // Burn slot background
            context.drawTexture(BURN_SLOT_TEX,
                    guiX + BURN_SLOT_X, guiY + BURN_SLOT_Y, 0, 0, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE);

            // Learn toggle + label
            Identifier learnTex = this.handler.isLearnEnabled() ? TOGGLE_LEARN_ON_TEX : TOGGLE_LEARN_OFF_TEX;
            context.drawTexture(learnTex,
                    guiX + LEARN_TOGGLE_X, guiY + LEARN_TOGGLE_Y, 0, 0,
                    LEARN_TOGGLE_W, LEARN_TOGGLE_H, LEARN_TOGGLE_W, LEARN_TOGGLE_H);

            Identifier textTex = this.handler.isLearnEnabled() ? TEXT_LEARN_ON_TEX : TEXT_LEARN_OFF_TEX;
            context.drawTexture(textTex,
                    guiX + LEARN_TEXT_X, guiY + LEARN_TEXT_Y, 0, 0,
                    LEARN_TEXT_W, LEARN_TEXT_H, LEARN_TEXT_W, LEARN_TEXT_H);
        } else {
            // Unlearning mode widgets
            context.drawTexture(CONFIRM_TEX,
                    guiX + CONFIRM_X, guiY + CONFIRM_Y, 0, 0, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE);
            context.drawTexture(DENY_TEX,
                    guiX + DENY_X, guiY + DENY_Y, 0, 0, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {

        // Periodically refresh learned-items cache and item list (~every second)
        if (this.client != null && this.client.player != null && this.client.player.age % 20 == 0) {
            refreshLearnedIds();
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
                    context.drawTexture(new Identifier("minecraft",
                                    "textures/gui/container/creative_inventory/tabs.png"),
                            currentTabX, tabY, 0, 0, tabWidth, tabHeight, 256, 256);
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
                    context.drawTexture(new Identifier("minecraft",
                                    "textures/gui/container/creative_inventory/tabs.png"),
                            currentTabX, currentTabY, 0, 32, tabWidth, tabHeight, 256, 256);
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

        // ── Item grid ──
        ItemStack hoveredStack = null;

        context.enableScissor(listX, listY, listX + listWidth, listY + listHeight);
        for (int i = 0; i < itemsToShow.size(); i++) {
            ItemStack stack = itemsToShow.get(i);
            int itemX = listX + (i % ROW_COUNT) * 18;
            int itemY = listY + (i / ROW_COUNT) * 18 - (int) this.scrollOffset;
            if (itemY >= listY - 18 && itemY < listY + listHeight) {
                String itemId = ItemUtil.toId(stack.getItem()).toString();
                boolean isLearned = cachedLearnedIds.contains(itemId);
                boolean hasEmc = EMCManager.get(stack.getItem()) > 0;

                context.drawItem(stack, itemX, itemY);

                // Visual overlays based on filter mode
                if (currentFilterMode == FilterMode.ALL && !hasEmc) {
                    context.fill(itemX, itemY, itemX + 16, itemY + 16, 0x40FF0000);
                }
                if (!isLearned && (currentFilterMode == FilterMode.UNKNOWN || currentFilterMode == FilterMode.ALL)) {
                    context.fill(itemX, itemY, itemX + 16, itemY + 16, 0x80101010);
                }

                // Unlearning selection cross
                if (getMode() == GuiMode.UNLEARNING && unlearnSelection.contains(itemId)) {
                    context.drawTexture(CROSS_ICON_TEX,
                            itemX - 1, itemY - 1, 0, 0, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE);
                }

                // Hover highlight
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

        if (mouseY >= tabY && mouseY < tabY + tabHeight) {
            int tabsScrollbarWidth = this.backgroundWidth - 16;
            int tabWidthWithMargin = 26 + 2;
            int totalTabsWidth = this.itemGroups.size() * tabWidthWithMargin - 12;
            int maxTabsScroll = Math.max(0, totalTabsWidth - tabsScrollbarWidth);
            this.tabsScrollOffset = (float) MathHelper.clamp(
                    this.tabsScrollOffset - amount * tabWidthWithMargin, 0, maxTabsScroll);
            return true;
        }

        int maxScroll = Math.max(0, (itemsToShow.size() + ROW_COUNT - 1) / ROW_COUNT * 18 - listHeight);
        this.scrollOffset = (float) MathHelper.clamp(this.scrollOffset - amount * 10, 0, maxScroll);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int guiX = this.x;
        int guiY = this.y;

        // ── Persistent buttons (both modes) ──

        // Filter button
        if (isInside(mouseX, mouseY, guiX + FILTER_X, guiY + FILTER_Y, WIDGET_SIZE, WIDGET_SIZE)) {
            this.currentFilterMode = this.currentFilterMode.next();
            updateItemsBasedOnTab();
            return true;
        }

        // Sort button
        if (isInside(mouseX, mouseY, guiX + SORT_X, guiY + SORT_Y, WIDGET_SIZE, WIDGET_SIZE)) {
            this.currentSortMode = this.currentSortMode.next();
            updateItemsBasedOnTab();
            return true;
        }

        // Unlearn Mode Toggle
        if (isInside(mouseX, mouseY, guiX + UNLEARN_TOGGLE_X, guiY + UNLEARN_TOGGLE_Y, WIDGET_SIZE, WIDGET_SIZE)) {
            if (getMode() == GuiMode.UNLEARNING) {
                // Acts as Deny when already in unlearning mode
                unlearnSelection.clear();
            }
            assert this.client != null && this.client.interactionManager != null;
            this.client.interactionManager.clickButton(this.handler.syncId,
                    AlchemicalTableMk2ScreenHandler.BUTTON_TOGGLE_MODE);
            return true;
        }

        // ── BURNING mode buttons ──

        if (getMode() == GuiMode.BURNING) {
            // Virtual burn zone — burn the cursor stack on click
            if (isInside(mouseX, mouseY, guiX + BURN_SLOT_X, guiY + BURN_SLOT_Y, WIDGET_SIZE, WIDGET_SIZE)) {
                if (!this.handler.getCursorStack().isEmpty()) {
                    assert this.client != null && this.client.interactionManager != null;
                    int burnButton = (button == 0)
                            ? AlchemicalTableMk2ScreenHandler.BUTTON_BURN_ALL
                            : AlchemicalTableMk2ScreenHandler.BUTTON_BURN_ONE;
                    this.client.interactionManager.clickButton(this.handler.syncId, burnButton);
                    return true;
                }
            }

            // Learn toggle
            if (isInside(mouseX, mouseY, guiX + LEARN_TOGGLE_X, guiY + LEARN_TOGGLE_Y,
                    LEARN_TOGGLE_W, LEARN_TOGGLE_H)) {
                assert this.client != null && this.client.interactionManager != null;
                this.client.interactionManager.clickButton(this.handler.syncId,
                        AlchemicalTableMk2ScreenHandler.BUTTON_TOGGLE_LEARN);
                return true;
            }
        }

        // ── UNLEARNING mode buttons ──

        if (getMode() == GuiMode.UNLEARNING) {
            // Confirm
            if (isInside(mouseX, mouseY, guiX + CONFIRM_X, guiY + CONFIRM_Y, WIDGET_SIZE, WIDGET_SIZE)) {
                sendUnlearnPacket();
                unlearnSelection.clear();
                return true;
            }

            // Deny
            if (isInside(mouseX, mouseY, guiX + DENY_X, guiY + DENY_Y, WIDGET_SIZE, WIDGET_SIZE)) {
                unlearnSelection.clear();
                assert this.client != null && this.client.interactionManager != null;
                this.client.interactionManager.clickButton(this.handler.syncId,
                        AlchemicalTableMk2ScreenHandler.BUTTON_DENY_UNLEARN);
                return true;
            }
        }

        // ── Item scrollbar drag ──
        int scrollbarX = listX + listWidth + 3;
        if (mouseX >= scrollbarX && mouseX < scrollbarX + 6 && mouseY >= listY && mouseY < listY + listHeight) {
            this.isDragging = true;
            return true;
        }

        // ── Tab scrollbar drag ──
        int tabsScrollbarX = this.x + 8;
        int tabsScrollbarY = this.y + 4;
        int tabsScrollbarWidth = this.backgroundWidth - 16;
        if (mouseX >= tabsScrollbarX && mouseX < tabsScrollbarX + tabsScrollbarWidth
                && mouseY >= tabsScrollbarY && mouseY < tabsScrollbarY + 2) {
            this.isTabsDragging = true;
            return true;
        }

        // ── Tab click ──
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

        // ── Item list click ──
        if (mouseX >= listX && mouseX < listX + listWidth && mouseY >= listY && mouseY < listY + listHeight) {
            int index = (int) ((mouseY - listY + scrollOffset) / 18) * ROW_COUNT + (int) ((mouseX - listX) / 18);
            if (index >= 0 && index < itemsToShow.size()) {
                ItemStack clickedStack = itemsToShow.get(index);

                if (getMode() == GuiMode.UNLEARNING) {
                    // Toggle selection
                    if (button == 0) {
                        String itemId = ItemUtil.toId(clickedStack.getItem()).toString();
                        if (!unlearnSelection.remove(itemId)) {
                            unlearnSelection.add(itemId);
                        }
                        isDragSelecting = true;
                        return true;
                    }
                } else {
                    // BURNING mode — buy item
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
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // Drag-to-select in UNLEARNING mode
        if (isDragSelecting && getMode() == GuiMode.UNLEARNING) {
            ItemStack hoveredStack = getHoveredStackFromList(mouseX, mouseY);
            if (hoveredStack != null) {
                unlearnSelection.add(ItemUtil.toId(hoveredStack.getItem()).toString());
            }
            return true;
        }

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
            this.isDragSelecting = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private static boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void sendUnlearnPacket() {
        if (unlearnSelection.isEmpty()) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(unlearnSelection.size());
        for (String id : unlearnSelection) {
            buf.writeString(id);
        }
        ClientPlayNetworking.send(ModMessages.UNLEARN_ITEMS_ID, buf);
    }
}
