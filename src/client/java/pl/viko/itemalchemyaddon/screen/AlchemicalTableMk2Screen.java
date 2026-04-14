package pl.viko.itemalchemyaddon.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import net.minecraft.util.math.MathHelper;
import net.pitan76.itemalchemy.EMCManager;
import net.pitan76.itemalchemy.ItemAlchemyClient;
import net.pitan76.itemalchemy.data.TeamState;
import net.pitan76.mcpitanlib.api.client.gui.screen.SimpleHandledScreen;
import net.pitan76.mcpitanlib.api.client.render.handledscreen.DrawBackgroundArgs;
import net.pitan76.mcpitanlib.api.client.render.handledscreen.DrawMouseoverTooltipArgs;
import net.pitan76.mcpitanlib.api.client.render.handledscreen.KeyEventArgs;
import net.pitan76.mcpitanlib.api.client.render.handledscreen.RenderArgs;
import net.pitan76.mcpitanlib.api.network.v2.ClientNetworking;
import net.pitan76.mcpitanlib.api.util.CompatIdentifier;
import net.pitan76.mcpitanlib.api.util.client.RenderUtil;
import net.pitan76.mcpitanlib.api.util.item.ItemUtil;
import org.jetbrains.annotations.Nullable;
import pl.viko.itemalchemyaddon.ItemAlchemyAddon;
import pl.viko.itemalchemyaddon.networking.ModMessages;
import pl.viko.itemalchemyaddon.screen.AlchemicalTableMk2ScreenHandler.GuiMode;

import java.text.NumberFormat;
import java.util.*;

/**
 * Client-side screen for the Alchemical Table Mk2.
 */
public class AlchemicalTableMk2Screen extends SimpleHandledScreen<AlchemicalTableMk2ScreenHandler> {

    // ── Filter / Sort enums ─────────────────────────────────────────────

    private enum FilterMode {
        KNOWN("filter_known"),
        ALL("filter_all"),
        UNKNOWN("filter_unknown"),
        EMC_NULL("filter_emc_null");

        final CompatIdentifier texture;
        final CompatIdentifier hoveredTexture;

        FilterMode(String name) {
            this.texture = widgetTex(name);
            this.hoveredTexture = widgetTex(name + "_hovered");
        }

        FilterMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private enum SortMode {
        ID("sort_id"),
        ABC("sort_abc"),
        EMC_DESC("sort_emc_desc"),
        EMC_ASC("sort_emc_asc");

        final CompatIdentifier texture;
        final CompatIdentifier hoveredTexture;

        SortMode(String name) {
            this.texture = widgetTex(name);
            this.hoveredTexture = widgetTex(name + "_hovered");
        }

        SortMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    // ── Texture identifiers ─────────────────────────────────────────────

    private static final CompatIdentifier TEXTURE = CompatIdentifier.of(ItemAlchemyAddon.MOD_ID,
            "textures/gui/alchemical_table_mk2_gui.png");

    private static final CompatIdentifier BURN_SLOT_TEX = widgetTex("burn_slot");
    private static final CompatIdentifier BURN_SLOT_HOVER_TEX = widgetTex("burn_slot_hovered");
    private static final CompatIdentifier TOGGLE_UNLEARN_TEX = widgetTex("toggle_unlearn_mode");
    private static final CompatIdentifier TOGGLE_UNLEARN_HOVER_TEX = widgetTex("toggle_unlearn_mode_hovered");
    private static final CompatIdentifier TOGGLE_LEARN_ON_TEX = widgetTex("toggle_learn_on");
    private static final CompatIdentifier TOGGLE_LEARN_OFF_TEX = widgetTex("toggle_learn_off");
    private static final CompatIdentifier TEXT_LEARN_ON_TEX = widgetTex("text_learn_on");
    private static final CompatIdentifier TEXT_LEARN_OFF_TEX = widgetTex("text_learn_off");
    private static final CompatIdentifier CONFIRM_TEX = widgetTex("confirm");
    private static final CompatIdentifier CONFIRM_HOVER_TEX = widgetTex("confirm_hovered");
    private static final CompatIdentifier DENY_TEX = widgetTex("deny");
    private static final CompatIdentifier DENY_HOVER_TEX = widgetTex("deny_hovered");
    private static final CompatIdentifier CROSS_ICON_TEX = widgetTex("cross_icon");
    private static final CompatIdentifier SMALL_CROSS_TEX = widgetTex("small_cross");

    private static final CompatIdentifier TAB_ACTIVE_TEX = widgetTex("tab_active");
    private static final CompatIdentifier TAB_INACTIVE_TEX = widgetTex("tab_inactive");
    private static final CompatIdentifier TAB_SCROLL_LEFT_TEX = widgetTex("tab_scroll_left");
    private static final CompatIdentifier TAB_SCROLL_LEFT_HOVER_TEX = widgetTex("tab_scroll_left_hovered");
    private static final CompatIdentifier TAB_SCROLL_RIGHT_TEX = widgetTex("tab_scroll_right");
    private static final CompatIdentifier TAB_SCROLL_RIGHT_HOVER_TEX = widgetTex("tab_scroll_right_hovered");

    private static final CompatIdentifier SLIDER_TEX = widgetTex("slider");
    private static final CompatIdentifier SLIDER_HOVER_TEX = widgetTex("slider_hovered");

    private static CompatIdentifier widgetTex(String name) {
        return CompatIdentifier.of(ItemAlchemyAddon.MOD_ID, "textures/gui/widgets/" + name + ".png");
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

    // ── Tab layout constants ─────────────────────────────────────────────

    private static final int MAX_VISIBLE_TABS = 7;
    private static final int[] TAB_X_OFFSETS = {14, 41, 68, 95, 122, 149, 176};
    private static final int TAB_Y_OFFSET = -28;
    private static final int TAB_W = 26, TAB_H = 31;
    private static final int TAB_ICON_ACTIVE_DX = 5, TAB_ICON_ACTIVE_DY = 6;
    private static final int TAB_ICON_INACTIVE_DX = 5, TAB_ICON_INACTIVE_DY = 8;

    private static final int TAB_SCROLL_LEFT_X = 1, TAB_SCROLL_LEFT_Y = -23;
    private static final int TAB_SCROLL_RIGHT_X = 204, TAB_SCROLL_RIGHT_Y = -23;
    private static final int TAB_SCROLL_W = 11, TAB_SCROLL_H = 20;

    // ── Scrollbar constants ──────────────────────────────────────────────

    private static final int SLIDER_X = 196;
    private static final int SLIDER_TOP_Y = 33;
    private static final int SLIDER_BOTTOM_Y = 124;
    private static final int SLIDER_W = 12, SLIDER_H = 15;

    // ── Search bar constants ─────────────────────────────────────────────

    private static final int SEARCH_TEXT_X = 42, SEARCH_TEXT_Y = 18;
    private static final int SEARCH_TEXT_W = 146;
    private static final int SEARCH_TEXT_H = 12;
    private static final int SEARCH_CLICK_X = 29, SEARCH_CLICK_Y = 15;
    private static final int SEARCH_CLICK_W = 161, SEARCH_CLICK_H = 12;
    private static final int SEARCH_MAX_LENGTH = 40;

    /** Number of item columns in the transmutation grid. */
    private static final int ROW_COUNT = 9;

    // ── Fields ───────────────────────────────────────────────────────────

    private List<ItemStack> itemsToShow = new ArrayList<>();
    private float scrollOffset;
    private boolean isDragging;
    private float sliderGrabOffset;
    private int listX, listY, listWidth, listHeight;

    private final List<ItemGroup> itemGroups = new ArrayList<>();
    private int selectedItemGroupIndex;
    private int tabScrollIndex;

    private FilterMode currentFilterMode = FilterMode.KNOWN;
    private SortMode currentSortMode = SortMode.ID;

    private final Set<String> unlearnSelection = new LinkedHashSet<>();
    private boolean isDragSelecting;
    private GuiMode previousMode = GuiMode.BURNING;

    private List<String> cachedLearnedIds = new ArrayList<>();

    private TextFieldWidget searchField;

    // ── Constructor ──────────────────────────────────────────────────────

    public AlchemicalTableMk2Screen(AlchemicalTableMk2ScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        setBackgroundWidth(216);
        setBackgroundHeight(252);
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    @Override
    public void initOverride() {
        super.initOverride();
        this.listX = this.x + 30;
        this.listY = this.y + 33;
        this.listWidth = 162;
        this.listHeight = 106;

        this.itemGroups.clear();
        for (ItemGroup group : ItemGroups.getGroups()) {
            ItemGroup.Type type = group.getType();
            if (type == ItemGroup.Type.HOTBAR || type == ItemGroup.Type.INVENTORY) {
                continue;
            }
            if (type == ItemGroup.Type.CATEGORY && group.getIcon().getItem() == Items.COMMAND_BLOCK) {
                continue;
            }
            this.itemGroups.add(group);
        }

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
        this.tabScrollIndex = 0;

        this.searchField = new TextFieldWidget(this.textRenderer,
                this.x + SEARCH_TEXT_X, this.y + SEARCH_TEXT_Y,
                SEARCH_TEXT_W, SEARCH_TEXT_H, Text.empty());
        this.searchField.setMaxLength(SEARCH_MAX_LENGTH);
        this.searchField.setDrawsBackground(false);
        this.searchField.setEditableColor(0xFFFFFF);
        this.searchField.setChangedListener(text -> {
            this.scrollOffset = 0;
            updateItemsBasedOnTab();
        });

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

        String searchText = (searchField != null) ? searchField.getText().toLowerCase(Locale.ROOT) : "";
        if (!searchText.isEmpty()) {
            filteredItems.removeIf(stack -> {
                String name = stack.getName().getString().toLowerCase(Locale.ROOT);
                String id = ItemUtil.toId(stack.getItem()).toString().toLowerCase(Locale.ROOT);
                return !name.contains(searchText) && !id.contains(searchText);
            });
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
        long currentEmc = this.handler.getClientEmc();
        String emcText = NumberFormat.getNumberInstance(Locale.US).format(currentEmc);
        context.drawText(this.textRenderer, "EMC: " + emcText, 8, 144, 0x404040, false);
    }

    @Override
    public void drawBackgroundOverride(DrawBackgroundArgs args) {
        RenderUtil.setShaderToPositionTexProgram();
        RenderUtil.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int guiX = (width - backgroundWidth) / 2;
        int guiY = (height - backgroundHeight) / 2;

        // ── Inactive tabs (behind GUI edge) ──
        for (int slot = 0; slot < MAX_VISIBLE_TABS && tabScrollIndex + slot < itemGroups.size(); slot++) {
            int tabIndex = tabScrollIndex + slot;
            if (tabIndex != selectedItemGroupIndex) {
                RenderUtil.RendererUtil.drawTexture(args.drawObjectDM, TAB_INACTIVE_TEX,
                        guiX + TAB_X_OFFSETS[slot], guiY + TAB_Y_OFFSET,
                        0, 0, TAB_W, TAB_H, TAB_W, TAB_H);
            }
        }

        // ── Main GUI texture ──
        RenderUtil.RendererUtil.drawTexture(args.drawObjectDM, TEXTURE, guiX, guiY, 0, 0, backgroundWidth, backgroundHeight,
                backgroundWidth, backgroundHeight);

        // ── Active tab (on top of GUI edge) ──
        int activeSlot = selectedItemGroupIndex - tabScrollIndex;
        if (activeSlot >= 0 && activeSlot < MAX_VISIBLE_TABS) {
            RenderUtil.RendererUtil.drawTexture(args.drawObjectDM, TAB_ACTIVE_TEX,
                    guiX + TAB_X_OFFSETS[activeSlot], guiY + TAB_Y_OFFSET,
                    0, 0, TAB_W, TAB_H, TAB_W, TAB_H);
        }

        // ── Tab icons ──
        for (int slot = 0; slot < MAX_VISIBLE_TABS && tabScrollIndex + slot < itemGroups.size(); slot++) {
            int tabIndex = tabScrollIndex + slot;
            boolean isActive = (tabIndex == selectedItemGroupIndex);
            int tabPixelX = guiX + TAB_X_OFFSETS[slot];
            int tabPixelY = guiY + TAB_Y_OFFSET;
            int iconDx = isActive ? TAB_ICON_ACTIVE_DX : TAB_ICON_INACTIVE_DX;
            int iconDy = isActive ? TAB_ICON_ACTIVE_DY : TAB_ICON_INACTIVE_DY;
            args.drawObjectDM.getContext().drawItem(itemGroups.get(tabIndex).getIcon(), tabPixelX + iconDx, tabPixelY + iconDy);
        }

        // ── Tab scroll arrows ──
        if (itemGroups.size() > MAX_VISIBLE_TABS) {
            boolean leftHover = isInside(args.mouseX, args.mouseY,
                    guiX + TAB_SCROLL_LEFT_X, guiY + TAB_SCROLL_LEFT_Y, TAB_SCROLL_W, TAB_SCROLL_H);
            RenderUtil.RendererUtil.drawTexture(args.drawObjectDM, leftHover ? TAB_SCROLL_LEFT_HOVER_TEX : TAB_SCROLL_LEFT_TEX,
                    guiX + TAB_SCROLL_LEFT_X, guiY + TAB_SCROLL_LEFT_Y,
                    0, 0, TAB_SCROLL_W, TAB_SCROLL_H, TAB_SCROLL_W, TAB_SCROLL_H);

            boolean rightHover = isInside(args.mouseX, args.mouseY,
                    guiX + TAB_SCROLL_RIGHT_X, guiY + TAB_SCROLL_RIGHT_Y, TAB_SCROLL_W, TAB_SCROLL_H);
            RenderUtil.RendererUtil.drawTexture(args.drawObjectDM, rightHover ? TAB_SCROLL_RIGHT_HOVER_TEX : TAB_SCROLL_RIGHT_TEX,
                    guiX + TAB_SCROLL_RIGHT_X, guiY + TAB_SCROLL_RIGHT_Y,
                    0, 0, TAB_SCROLL_W, TAB_SCROLL_H, TAB_SCROLL_W, TAB_SCROLL_H);
        }

        // ── Persistent widgets with hover ──
        boolean filterHover = isInside(args.mouseX, args.mouseY, guiX + FILTER_X, guiY + FILTER_Y, WIDGET_SIZE, WIDGET_SIZE);
        RenderUtil.RendererUtil.drawTexture(args.drawObjectDM, filterHover ? currentFilterMode.hoveredTexture : currentFilterMode.texture,
                guiX + FILTER_X, guiY + FILTER_Y, 0, 0, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE);

        boolean sortHover = isInside(args.mouseX, args.mouseY, guiX + SORT_X, guiY + SORT_Y, WIDGET_SIZE, WIDGET_SIZE);
        RenderUtil.RendererUtil.drawTexture(args.drawObjectDM, sortHover ? currentSortMode.hoveredTexture : currentSortMode.texture,
                guiX + SORT_X, guiY + SORT_Y, 0, 0, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE);

        boolean unlearnHover = isInside(args.mouseX, args.mouseY, guiX + UNLEARN_TOGGLE_X, guiY + UNLEARN_TOGGLE_Y, WIDGET_SIZE, WIDGET_SIZE);
        RenderUtil.RendererUtil.drawTexture(args.drawObjectDM, unlearnHover ? TOGGLE_UNLEARN_HOVER_TEX : TOGGLE_UNLEARN_TEX,
                guiX + UNLEARN_TOGGLE_X, guiY + UNLEARN_TOGGLE_Y, 0, 0, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE);

        // ── Mode-specific widgets ──
        if (getMode() == GuiMode.BURNING) {
            boolean burnHover = isInside(args.mouseX, args.mouseY, guiX + BURN_SLOT_X, guiY + BURN_SLOT_Y, WIDGET_SIZE, WIDGET_SIZE);
            RenderUtil.RendererUtil.drawTexture(args.drawObjectDM, burnHover ? BURN_SLOT_HOVER_TEX : BURN_SLOT_TEX,
                    guiX + BURN_SLOT_X, guiY + BURN_SLOT_Y, 0, 0, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE);

            CompatIdentifier learnTex = this.handler.isLearnEnabled() ? TOGGLE_LEARN_ON_TEX : TOGGLE_LEARN_OFF_TEX;
            RenderUtil.RendererUtil.drawTexture(args.drawObjectDM, learnTex,
                    guiX + LEARN_TOGGLE_X, guiY + LEARN_TOGGLE_Y, 0, 0,
                    LEARN_TOGGLE_W, LEARN_TOGGLE_H, LEARN_TOGGLE_W, LEARN_TOGGLE_H);

            CompatIdentifier textTex = this.handler.isLearnEnabled() ? TEXT_LEARN_ON_TEX : TEXT_LEARN_OFF_TEX;
            RenderUtil.RendererUtil.drawTexture(args.drawObjectDM, textTex,
                    guiX + LEARN_TEXT_X, guiY + LEARN_TEXT_Y, 0, 0,
                    LEARN_TEXT_W, LEARN_TEXT_H, LEARN_TEXT_W, LEARN_TEXT_H);
        } else {
            boolean confirmHover = isInside(args.mouseX, args.mouseY, guiX + CONFIRM_X, guiY + CONFIRM_Y, WIDGET_SIZE, WIDGET_SIZE);
            RenderUtil.RendererUtil.drawTexture(args.drawObjectDM, confirmHover ? CONFIRM_HOVER_TEX : CONFIRM_TEX,
                    guiX + CONFIRM_X, guiY + CONFIRM_Y, 0, 0, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE);

            boolean denyHover = isInside(args.mouseX, args.mouseY, guiX + DENY_X, guiY + DENY_Y, WIDGET_SIZE, WIDGET_SIZE);
            RenderUtil.RendererUtil.drawTexture(args.drawObjectDM, denyHover ? DENY_HOVER_TEX : DENY_TEX,
                    guiX + DENY_X, guiY + DENY_Y, 0, 0, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE);
        }
    }

    @Override
    public void renderOverride(RenderArgs args) {
        this.callRenderBackground(args);

        GuiMode currentMode = getMode();
        if (currentMode != previousMode) {
            if (previousMode == GuiMode.UNLEARNING) {
                unlearnSelection.clear();
                refreshLearnedIds();
                updateItemsBasedOnTab();
            }
            previousMode = currentMode;
        }

        if (this.client != null && this.client.player != null && this.client.player.age % 20 == 0) {
            refreshLearnedIds();
            updateItemsBasedOnTab();
        }

        super.renderOverride(args);

        // ── Search field (rendered manually, not via addDrawableChild) ──
        if (this.searchField != null) {
            this.searchField.render(args.drawObjectDM.getContext(), args.mouseX, args.mouseY, args.delta);
        }

        // ── Item grid ──
        ItemStack hoveredStack = null;

        args.drawObjectDM.getContext().enableScissor(listX, listY, listX + listWidth, listY + listHeight);
        for (int i = 0; i < itemsToShow.size(); i++) {
            ItemStack stack = itemsToShow.get(i);
            int itemX = listX + (i % ROW_COUNT) * 18;
            int itemY = listY + (i / ROW_COUNT) * 18 - (int) this.scrollOffset;
            if (itemY >= listY - 18 && itemY < listY + listHeight) {
                String itemId = ItemUtil.toId(stack.getItem()).toString();
                boolean isLearned = cachedLearnedIds.contains(itemId);
                boolean hasEmc = EMCManager.get(stack.getItem()) > 0;

                boolean dimItem = !isLearned
                        && (currentFilterMode == FilterMode.UNKNOWN || currentFilterMode == FilterMode.ALL);
                if (dimItem) {
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.5F);
                }
                args.drawObjectDM.getContext().drawItem(stack, itemX, itemY);
                if (dimItem) {
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                }

                if (currentFilterMode == FilterMode.ALL && !hasEmc) {
                    args.drawObjectDM.getContext().getMatrices().push();
                    args.drawObjectDM.getContext().getMatrices().translate(0, 0, 200);
                    RenderUtil.RendererUtil.drawTexture(args.drawObjectDM, SMALL_CROSS_TEX,
                            itemX, itemY, 0, 0, 16, 16, 16, 16);
                    args.drawObjectDM.getContext().getMatrices().pop();
                }

                if (getMode() == GuiMode.UNLEARNING && unlearnSelection.contains(itemId)) {
                    args.drawObjectDM.getContext().getMatrices().push();
                    args.drawObjectDM.getContext().getMatrices().translate(0, 0, 200);
                    RenderUtil.RendererUtil.drawTexture(args.drawObjectDM, CROSS_ICON_TEX,
                            itemX - 1, itemY - 1, 0, 0, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE, WIDGET_SIZE);
                    args.drawObjectDM.getContext().getMatrices().pop();
                }

                if (args.mouseX >= itemX && args.mouseX < itemX + 16 && args.mouseY >= itemY && args.mouseY < itemY + 16) {
                    args.drawObjectDM.getContext().fill(itemX, itemY, itemX + 16, itemY + 16, 0x80FFFFFF);
                    hoveredStack = stack;
                }
            }
        }
        args.drawObjectDM.getContext().disableScissor();

        // ── Custom scrollbar ──
        int maxScroll = Math.max(0, (itemsToShow.size() + ROW_COUNT - 1) / ROW_COUNT * 18 - listHeight);
        int guiX = this.x;
        int guiY = this.y;
        int sliderRange = SLIDER_BOTTOM_Y - SLIDER_TOP_Y;
        int sliderY;
        if (maxScroll > 0) {
            sliderY = SLIDER_TOP_Y + (int) (this.scrollOffset / maxScroll * sliderRange);
        } else {
            sliderY = SLIDER_TOP_Y;
        }
        RenderUtil.RendererUtil.drawTexture(args.drawObjectDM, isDragging ? SLIDER_HOVER_TEX : SLIDER_TEX,
                guiX + SLIDER_X, guiY + sliderY, 0, 0, SLIDER_W, SLIDER_H, SLIDER_W, SLIDER_H);

        // ── Tab tooltip ──
        for (int slot = 0; slot < MAX_VISIBLE_TABS && tabScrollIndex + slot < itemGroups.size(); slot++) {
            int tabPixelX = guiX + TAB_X_OFFSETS[slot];
            int tabPixelY = guiY + TAB_Y_OFFSET;
            if (isInside(args.mouseX, args.mouseY, tabPixelX, tabPixelY, TAB_W, TAB_H)) {
                int tabIndex = tabScrollIndex + slot;
                ItemGroup group = this.itemGroups.get(tabIndex);
                Text tabName = (group.getType() == ItemGroup.Type.SEARCH)
                        ? Text.literal("All Items")
                        : group.getDisplayName();
                args.drawObjectDM.getContext().drawTooltip(this.textRenderer, List.of(tabName), args.mouseX, args.mouseY);
                break;
            }
        }

        // ── Item tooltip ──
        callDrawMouseoverTooltip(new DrawMouseoverTooltipArgs(args.drawObjectDM, args.mouseX, args.mouseY));
        if (hoveredStack != null) {
            args.drawObjectDM.getContext().drawTooltip(this.textRenderer, getTooltipFromItem(this.client, hoveredStack), args.mouseX, args.mouseY);
        }
    }

    // ── Input handling ───────────────────────────────────────────────────

    @Override
    public boolean keyPressed(KeyEventArgs args) {
        if (searchField != null && searchField.isFocused()) {
            if (args.keyCode == 256) {
                searchField.setFocused(false);
                return true;
            }
            searchField.keyPressed(args.keyCode, args.scanCode, args.modifiers);
            return true;
        }
        return super.keyPressed(args);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchField != null && searchField.isFocused()) {
            return searchField.charTyped(chr, modifiers);
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseY >= this.y + TAB_Y_OFFSET && mouseY < this.y + TAB_Y_OFFSET + TAB_H) {
            int maxTabScroll = Math.max(0, itemGroups.size() - MAX_VISIBLE_TABS);
            if (maxTabScroll > 0) {
                tabScrollIndex += (amount > 0) ? -1 : 1;
                tabScrollIndex = MathHelper.clamp(tabScrollIndex, 0, maxTabScroll);
            }
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

        // Always defocus search field first; refocus only if clicking its activation area
        if (searchField != null) {
            searchField.setFocused(false);
        }

        // ── Search bar activation area ──
        if (isInside(mouseX, mouseY,
                guiX + SEARCH_CLICK_X, guiY + SEARCH_CLICK_Y,
                SEARCH_CLICK_W, SEARCH_CLICK_H)) {
            if (searchField != null) {
                searchField.setFocused(true);
            }
            return true;
        }

        // ── Tab scroll arrows ──
        if (itemGroups.size() > MAX_VISIBLE_TABS) {
            if (isInside(mouseX, mouseY, guiX + TAB_SCROLL_LEFT_X, guiY + TAB_SCROLL_LEFT_Y, TAB_SCROLL_W, TAB_SCROLL_H)) {
                if (tabScrollIndex > 0) tabScrollIndex--;
                return true;
            }
            if (isInside(mouseX, mouseY, guiX + TAB_SCROLL_RIGHT_X, guiY + TAB_SCROLL_RIGHT_Y, TAB_SCROLL_W, TAB_SCROLL_H)) {
                int maxTabScroll = Math.max(0, itemGroups.size() - MAX_VISIBLE_TABS);
                if (tabScrollIndex < maxTabScroll) tabScrollIndex++;
                return true;
            }
        }

        // ── Tab click ──
        for (int slot = 0; slot < MAX_VISIBLE_TABS && tabScrollIndex + slot < itemGroups.size(); slot++) {
            int tabPixelX = guiX + TAB_X_OFFSETS[slot];
            int tabPixelY = guiY + TAB_Y_OFFSET;
            if (isInside(mouseX, mouseY, tabPixelX, tabPixelY, TAB_W, TAB_H)) {
                int newIndex = tabScrollIndex + slot;
                if (newIndex != selectedItemGroupIndex) {
                    this.selectedItemGroupIndex = newIndex;
                    this.scrollOffset = 0;
                    updateItemsBasedOnTab();
                }
                return true;
            }
        }

        // ── Persistent buttons ──

        if (isInside(mouseX, mouseY, guiX + FILTER_X, guiY + FILTER_Y, WIDGET_SIZE, WIDGET_SIZE)) {
            this.currentFilterMode = this.currentFilterMode.next();
            this.scrollOffset = 0;
            updateItemsBasedOnTab();
            return true;
        }

        if (isInside(mouseX, mouseY, guiX + SORT_X, guiY + SORT_Y, WIDGET_SIZE, WIDGET_SIZE)) {
            this.currentSortMode = this.currentSortMode.next();
            updateItemsBasedOnTab();
            return true;
        }

        if (isInside(mouseX, mouseY, guiX + UNLEARN_TOGGLE_X, guiY + UNLEARN_TOGGLE_Y, WIDGET_SIZE, WIDGET_SIZE)) {
            if (getMode() == GuiMode.UNLEARNING) {
                unlearnSelection.clear();
            }
            assert this.client != null && this.client.interactionManager != null;
            this.client.interactionManager.clickButton(this.handler.syncId,
                    AlchemicalTableMk2ScreenHandler.BUTTON_TOGGLE_MODE);
            return true;
        }

        // ── BURNING mode buttons ──

        if (getMode() == GuiMode.BURNING) {
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
            if (isInside(mouseX, mouseY, guiX + CONFIRM_X, guiY + CONFIRM_Y, WIDGET_SIZE, WIDGET_SIZE)) {
                sendUnlearnPacket();
                unlearnSelection.clear();
                return true;
            }

            if (isInside(mouseX, mouseY, guiX + DENY_X, guiY + DENY_Y, WIDGET_SIZE, WIDGET_SIZE)) {
                unlearnSelection.clear();
                assert this.client != null && this.client.interactionManager != null;
                this.client.interactionManager.clickButton(this.handler.syncId,
                        AlchemicalTableMk2ScreenHandler.BUTTON_DENY_UNLEARN);
                return true;
            }
        }

        // ── Custom scrollbar drag ──
        int sliderRange = SLIDER_BOTTOM_Y - SLIDER_TOP_Y;
        if (isInside(mouseX, mouseY, guiX + SLIDER_X, guiY + SLIDER_TOP_Y, SLIDER_W, sliderRange + SLIDER_H)) {
            int maxScrl = Math.max(1, (itemsToShow.size() + ROW_COUNT - 1) / ROW_COUNT * 18 - listHeight);
            int currentSliderY = guiY + SLIDER_TOP_Y + (maxScrl > 0
                    ? (int) (this.scrollOffset / maxScrl * sliderRange) : 0);
            if (mouseY >= currentSliderY && mouseY < currentSliderY + SLIDER_H) {
                this.sliderGrabOffset = (float) (mouseY - currentSliderY);
            } else {
                this.sliderGrabOffset = SLIDER_H / 2.0f;
                updateScrollFromMouse(mouseY);
            }
            this.isDragging = true;
            return true;
        }

        // ── Item list click ──
        if (mouseX >= listX && mouseX < listX + listWidth && mouseY >= listY && mouseY < listY + listHeight) {
            int index = (int) ((mouseY - listY + scrollOffset) / 18) * ROW_COUNT + (int) ((mouseX - listX) / 18);
            if (index >= 0 && index < itemsToShow.size()) {
                ItemStack clickedStack = itemsToShow.get(index);

                if (getMode() == GuiMode.UNLEARNING) {
                    if (button == 0) {
                        String itemId = ItemUtil.toId(clickedStack.getItem()).toString();
                        if (!unlearnSelection.remove(itemId)) {
                            unlearnSelection.add(itemId);
                        }
                        isDragSelecting = true;
                        return true;
                    }
                } else {
                    String itemId = ItemUtil.toId(clickedStack.getItem()).toString();
                    if (!cachedLearnedIds.contains(itemId)) {
                        return true;
                    }

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
                    ClientNetworking.send(ModMessages.REQUEST_ITEM_ID, buf);
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDragSelecting && getMode() == GuiMode.UNLEARNING) {
            ItemStack hoveredStack = getHoveredStackFromList(mouseX, mouseY);
            if (hoveredStack != null) {
                unlearnSelection.add(ItemUtil.toId(hoveredStack.getItem()).toString());
            }
            return true;
        }

        if (this.isDragging) {
            updateScrollFromMouse(mouseY);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.isDragging = false;
            this.isDragSelecting = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private static boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void updateScrollFromMouse(double mouseY) {
        int maxScroll = Math.max(1, (itemsToShow.size() + ROW_COUNT - 1) / ROW_COUNT * 18 - listHeight);
        float sliderTop = (float) (mouseY - sliderGrabOffset - this.y - SLIDER_TOP_Y);
        float scrollRange = SLIDER_BOTTOM_Y - SLIDER_TOP_Y;
        this.scrollOffset = MathHelper.clamp(sliderTop / scrollRange * maxScroll, 0, maxScroll);
    }

    private void sendUnlearnPacket() {
        if (unlearnSelection.isEmpty()) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(unlearnSelection.size());
        for (String id : unlearnSelection) {
            buf.writeString(id);
        }
        ClientNetworking.send(ModMessages.UNLEARN_ITEMS_ID, buf);
    }
}
