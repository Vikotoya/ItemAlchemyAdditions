package pl.viko.itemalchemyaddon.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.pitan76.mcpitanlib.api.entity.Player;
import net.pitan76.mcpitanlib.api.util.InventoryUtil;
import net.pitan76.mcpitanlib.api.util.collection.ItemStackList;
import net.pitan76.mcpitanlib.api.util.inventory.ICompatInventory;

/**
 * An {@link ICompatInventory} implementation backed by an {@link ItemStack}'s NBT data.
 *
 * <p>All mutations are immediately serialized back into the host stack's
 * {@code "Items"} NBT tag, so the inventory contents persist as long as the
 * stack itself does.</p>
 */
public class ItemInventory implements ICompatInventory {

    private final ItemStack stack;
    private final ItemStackList items;

    /**
     * Creates a new item-backed inventory.
     *
     * @param stack the host item stack whose NBT stores the inventory
     * @param size  the number of slots in this inventory
     */
    public ItemInventory(ItemStack stack, int size) {
        this.stack = stack;
        this.items = ItemStackList.ofSize(size, ItemStack.EMPTY);
        NbtCompound nbt = stack.getOrCreateNbt();
        if (nbt.contains("Items")) {
            Inventories.readNbt(nbt, this.items);
        }
    }

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemStack : this.items) {
            if (!itemStack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack result = Inventories.splitStack(this.items, slot, amount);
        if (!result.isEmpty()) {
            markDirty();
        }
        return result;
    }

    @Override
    public ItemStack removeStack(int slot) {
        return Inventories.removeStack(this.items, slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) {
            stack.setCount(getMaxCountPerStack());
        }
        markDirty();
    }

    /** Serializes the current inventory contents back into the host stack's NBT. */
    @Override
    public void markDirty() {
        NbtCompound nbt = stack.getOrCreateNbt();
        Inventories.writeNbt(nbt, this.items);
        stack.setNbt(nbt);
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return true;
    }

    @Override
    public boolean canPlayerUse(Player player) {
        return true;
    }

    @Override
    public void clear() {
        items.clear();
        markDirty();
    }
}
