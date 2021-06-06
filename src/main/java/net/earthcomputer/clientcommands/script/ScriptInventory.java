package net.earthcomputer.clientcommands.script;

import com.google.common.collect.Lists;
import jdk.nashorn.api.scripting.JSObject;
import net.earthcomputer.clientcommands.interfaces.ISlot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.screen.HorseScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.Registry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class ScriptInventory {

    private final ScreenHandler container;

    ScriptInventory(ScreenHandler container) {
        this.container = container;
    }

    public String getType() {
        if (container instanceof PlayerScreenHandler)
            return "player";
        if (container instanceof CreativeInventoryScreen.CreativeScreenHandler)
            return "creative";
        if (container instanceof HorseScreenHandler)
            return "horse";
        ScreenHandlerType<?> type = container.getType();
        if (type == null)
            return null;
        return ScriptUtil.simplifyIdentifier(Registry.SCREEN_HANDLER.getId(type));
    }

    /**
     * If this is a player container, then slots are the hotbar, main inventory, armor, offhand, crafting result and crafting grid,
     * in that order.
     * Otherwise, they are the container items in order.
     */
    public List<Object> getItems() {
        List<Object> ret = new ArrayList<>();
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (container == player.playerScreenHandler) {
            for (int i = 0; i < player.inventory.size(); i++) {
                ret.add(ScriptUtil.fromNbt(player.inventory.getStack(i).toTag(new CompoundTag())));
            }
            // crafting grid
            for (int i = 0; i < 5; i++)
                ret.add(ScriptUtil.fromNbt(container.slots.get(i).getStack().toTag(new CompoundTag())));
        } else {
            for (Slot slot : container.slots) {
                if (slot.inventory != player.inventory) {
                    ret.add(ScriptUtil.fromNbt(slot.getStack().toTag(new CompoundTag())));
                }
            }
        }
        return ret;
    }

    public void click(Integer slot) {
        click(slot, null);
    }

    public void click(Integer slot, JSObject options) {
        String typeStr = options == null || !options.hasMember("type") ? null : ScriptUtil.asString(options.getMember("type"));
        SlotActionType type = typeStr == null ? SlotActionType.PICKUP :
                Arrays.stream(SlotActionType.values()).filter(it -> it.name().equalsIgnoreCase(typeStr)).findAny().orElse(SlotActionType.PICKUP);

        int mouseButton;
        if (type == SlotActionType.SWAP) {
            if (!options.hasMember("hotbarSlot"))
                throw new IllegalArgumentException("When the click type is swap, the options must also contain the hotbar slot to swap with");
            mouseButton = MathHelper.clamp(ScriptUtil.asNumber(options.getMember("hotbarSlot")).intValue(), 0, 8);
        } else if (type == SlotActionType.QUICK_CRAFT) {
            if (!options.hasMember("quickCraftStage"))
                throw new IllegalArgumentException("When the click type is quick_craft, the options must also contain the quick craft stage");
            int quickCraftStage = ScriptUtil.asNumber(options.getMember("quickCraftStage")).intValue();
            int button = options.hasMember("rightClick") && ScriptUtil.asBoolean(options.getMember("rightClick")) ? 1 : 0;
            mouseButton = ScreenHandler.packQuickCraftData(quickCraftStage, button);
        } else {
            if (options == null || !options.hasMember("rightClick"))
                mouseButton = 0;
            else
                mouseButton = ScriptUtil.asBoolean(options.getMember("rightClick")) ? 1 : 0;
        }

        int slotId;
        if (slot == null) {
            slotId = -999;
        } else {
            Slot theSlot = getSlot(slot);
            if (theSlot == null)
                throw new IllegalArgumentException("Slot not in open container");
            slotId = theSlot.id;
        }

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        MinecraftClient.getInstance().interactionManager.clickSlot(player.currentScreenHandler.syncId, slotId, mouseButton, type, player);
    }

    public Integer findSlot(Object item) {
        return findSlot(item, false);
    }

    public Integer findSlot(Object item, boolean reverse) {
        Predicate<ItemStack> itemPredicate = ScriptUtil.asItemStackPredicate(item);

        List<Slot> slots = getSlots();
        if (reverse)
            slots = Lists.reverse(slots);

        for (Slot slot : slots) {
            if (itemPredicate.test(slot.getStack())) {
                return getIdForSlot(slot);
            }
        }

        return null;
    }

    public List<Integer> findSlots(Object item, int count) {
        return findSlots(item, count, false);
    }

    public List<Integer> findSlots(Object item, int count, boolean reverse) {
        Predicate<ItemStack> itemPredicate = ScriptUtil.asItemStackPredicate(item);

        List<Slot> slots = getSlots();
        if (reverse)
            slots = Lists.reverse(slots);

        List<Integer> slotIds = new ArrayList<>();
        int itemsFound = 0;
        for (Slot slot : slots) {
            if (itemPredicate.test(slot.getStack())) {
                slotIds.add(getIdForSlot(slot));
                itemsFound += slot.getStack().getCount();
                if (count != -1 && itemsFound >= count)
                    break;
            }
        }

        return slotIds;
    }

    public int moveItems(Object item, int count) {
        return moveItems(item, count, false);
    }

    public int moveItems(Object item, int count, boolean reverse) {
        Predicate<ItemStack> itemPredicate = ScriptUtil.asItemStackPredicate(item);

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        assert player != null;
        ClientPlayerInteractionManager interactionManager = MinecraftClient.getInstance().interactionManager;
        assert interactionManager != null;

        List<Slot> slots = getSlots();
        if (reverse)
            slots = Lists.reverse(slots);

        int itemsFound = 0;
        for (Slot slot : slots) {
            if (itemPredicate.test(slot.getStack())) {
                interactionManager.clickSlot(player.currentScreenHandler.syncId, slot.id, 0, SlotActionType.QUICK_MOVE, player);
                itemsFound += slot.getStack().getCount();
                if (count != -1 && itemsFound >= count)
                    break;
            }
        }

        return itemsFound;
    }

    private Slot getSlot(int id) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        assert player != null;

        if (container == player.playerScreenHandler) {
            if (id < player.inventory.size()) {
                for (Slot slot : player.currentScreenHandler.slots) {
                    if (slot.inventory == player.inventory && ((ISlot) slot).getIndex() == id) {
                        return slot;
                    }
                }
            }
            if (container == player.currentScreenHandler) {
                return container.getSlot(id - player.inventory.size());
            }
        } else {
            int containerId = 0;
            for (int i = 0; i < container.slots.size(); i++) {
                Slot slot = container.getSlot(i);
                if (slot.inventory != player.inventory) {
                    if (id == containerId)
                        return slot;
                    containerId++;
                }
            }
        }

        return null;
    }

    private int getIdForSlot(Slot slot) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        assert player != null;

        if (container == player.playerScreenHandler) {
            if (slot.inventory == player.inventory) {
                return ((ISlot) slot).getIndex();
            } else {
                return ((ISlot) slot).getIndex() + player.inventory.size();
            }
        } else {
            int containerId = 0;
            for (int i = 0; i < container.slots.size(); i++) {
                Slot otherSlot = container.getSlot(i);
                if (otherSlot.inventory != player.inventory) {
                    if (otherSlot == slot)
                        return containerId;
                    containerId++;
                }
            }
            return -1;
        }
    }

    private List<Slot> getSlots() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        assert player != null;

        if (container == player.playerScreenHandler) {
            return player.currentScreenHandler.slots.stream()
                    .filter(slot -> slot.inventory == player.inventory)
                    .sorted(Comparator.comparingInt(this::getIdForSlot))
                    .collect(Collectors.toList());
        } else {
            return container.slots.stream()
                    .filter(slot -> slot.inventory != player.inventory)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public int hashCode() {
        return container.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof ScriptInventory)) return false;
        return container.equals(((ScriptInventory) o).container);
    }
}
