package pl.viko.itemalchemyaddon.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.collection.DefaultedList;

public class ItemInventory implements Inventory {
    private final ItemStack stack;
    private final DefaultedList<ItemStack> items;

    public ItemInventory(ItemStack stack, int size) {
        this.stack = stack;
        this.items = DefaultedList.ofSize(size, ItemStack.EMPTY);
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
    public void clear() {
        items.clear();
        markDirty();
    }
}
