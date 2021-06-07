package net.earthcomputer.clientcommands.script;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.AbstractListTag;
import net.minecraft.nbt.AbstractNumberTag;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.registry.Registry;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class ScriptUtil {

    public static Object fromNbt(Tag tag) {
        if (tag instanceof CompoundTag) {
            return fromNbtCompound((CompoundTag) tag);
        } else if (tag instanceof AbstractListTag) {
            return fromNbtList((AbstractListTag<?>) tag);
        } else if (tag instanceof StringTag) {
            return tag.asString();
        } else if (tag instanceof LongTag) {
            return ((LongTag) tag).getLong();
        } else if (tag instanceof AbstractNumberTag) {
            return ((AbstractNumberTag) tag).getDouble();
        } else {
            throw new IllegalStateException("Unknown tag type " + tag.getType());
        }
    }

    public static Object fromNbtCompound(CompoundTag tag) {
        Map<String, Object> map = new HashMap<>(tag.getSize());
        tag.getKeys().forEach(key -> map.put(key, fromNbt(tag.get(key))));
        return map;
    }

    public static Object fromNbtList(AbstractListTag<?> tag) {
        List<Object> list = new ArrayList<>(tag.size());
        tag.forEach(val -> list.add(fromNbt(val)));
        return list;
    }

    public static Tag toNbt(Value obj) {
        if (obj.isBoolean()) {
            return ByteTag.of(obj.asBoolean());
        } else if (obj.isNumber()) {
            if (obj.fitsInByte()) {
                return ByteTag.of(obj.asByte());
            } else if (obj.fitsInShort()) {
                return ShortTag.of(obj.asShort());
            } else if (obj.fitsInInt()) {
                return IntTag.of(obj.asInt());
            } else if (obj.fitsInFloat()) {
                return FloatTag.of(obj.asFloat());
            } else if (obj.fitsInLong()) {
                return LongTag.of(obj.asLong());
            } else if (obj.fitsInDouble()) {
                return DoubleTag.of(obj.asDouble());
            } else {
                return DoubleTag.of(Double.NaN);
            }
        } else if (obj.isString()) {
            return StringTag.of(obj.asString());
        } else if (obj.hasArrayElements()) {
            return arrayToNbtList(obj);
        } else {
            return objectToNbtCompound(obj);
        }
    }

    private static CompoundTag objectToNbtCompound(Value obj) {
        CompoundTag compound = new CompoundTag();
        for (String key : obj.getMemberKeys()) {
            compound.put(key, toNbt(obj.getMember(key)));
        }
        return compound;
    }

    private static byte getArrayType(Value array) {
        byte type = 0;
        int len = (int) array.getArraySize();
        for (int i = 0; i < len; i++) {
            Value element = array.getArrayElement(i);
            if (element.isBoolean()) {
                return 1; // byte
            } else if (element.isNumber()) {
                byte newType;
                if (element.fitsInByte())
                    newType = 1; // byte
                else if (element.fitsInShort())
                    newType = 2; // short
                else if (element.fitsInInt())
                    newType = 3; // int
                else if (element.fitsInFloat())
                    newType = 5; // float
                else if (element.fitsInLong())
                    newType = 4; // long
                else
                    newType = 6; // double
                if (newType == 5) { // float
                    if (type <= 2) // byte, short
                        type = 5; // float
                    else
                        type = 6; // double
                } else if (newType == 6) // double
                    type = 6; // double
                else
                    type = (byte) Math.max(type, newType);
            } else if (element.isString()) {
                return 8; // string
            } else if (element.hasArrayElements()) {
                byte sublistType = getArrayType(element);
                byte newType;
                if (sublistType == 1) // byte
                    newType = 7; // byte array
                else if (sublistType == 2 || sublistType == 3)
                    newType = 11; // int array
                else if (sublistType == 4)
                    newType = 12; // long array
                else
                    return 9; // list
                type = (byte) Math.max(type, newType);
            } else {
                return 10; // compound
            }
        }
        return type;
    }

    private static AbstractListTag<?> arrayToNbtList(Value array) {
        byte type = getArrayType(array);
        int len = (int) array.getArraySize();
        AbstractListTag<?> listTag;
        if (type == 1) // byte
            listTag = new ByteArrayTag(new byte[len]);
        else if (type == 2 || type == 3) // short, int
            listTag = new IntArrayTag(new int[len]);
        else if (type == 4) // long
            listTag = new LongArrayTag(new long[len]);
        else
            listTag = new ListTag();

        for (int index = 0; index < len; index++) {
            Value element = array.getArrayElement(index);
            Tag elementTag = toNbt(element);
            if (type <= 4) { // integral number
                if (!(elementTag instanceof AbstractNumberTag))
                    throw new IllegalStateException();
                AbstractNumberTag num = (AbstractNumberTag) elementTag;
                if (type == 1) // byte
                    ((ByteArrayTag) listTag).getByteArray()[index] = num.getByte();
                else if (type == 2 || type == 3) // short, int
                    ((IntArrayTag) listTag).getIntArray()[index] = num.getInt();
                else if (type == 4) // long
                    ((LongArrayTag) listTag).getLongArray()[index] = num.getLong();
            } else if (type == 7 || type == 11 || type == 12) { // integral arrays
                if (!(elementTag instanceof AbstractListTag))
                    throw new IllegalStateException();
                AbstractListTag<?> converted;
                if (type == 7) { // byte array
                    if (elementTag instanceof ByteArrayTag) {
                        converted = (ByteArrayTag) elementTag;
                    } else if (elementTag instanceof IntArrayTag) {
                        int[] from = ((IntArrayTag) elementTag).getIntArray();
                        byte[] to = new byte[from.length];
                        for (int i = 0; i < from.length; i++)
                            to[i] = (byte) from[i];
                        converted = new ByteArrayTag(to);
                    } else {
                        long[] from = ((LongArrayTag) elementTag).getLongArray();
                        byte[] to = new byte[from.length];
                        for (int i = 0; i < from.length; i++)
                            to[i] = (byte) from[i];
                        converted = new ByteArrayTag(to);
                    }
                } else if (type == 11) { // int array
                    if (elementTag instanceof ByteArrayTag) {
                        byte[] from = ((ByteArrayTag) elementTag).getByteArray();
                        int[] to = new int[from.length];
                        for (int i = 0; i < from.length; i++)
                            to[i] = from[i];
                        converted = new IntArrayTag(to);
                    } else if (elementTag instanceof IntArrayTag) {
                        converted = (IntArrayTag) elementTag;
                    } else {
                        long[] from = ((LongArrayTag) elementTag).getLongArray();
                        int[] to = new int[from.length];
                        for (int i = 0; i < from.length; i++)
                            to[i] = (int) from[i];
                        converted = new IntArrayTag(to);
                    }
                } else { // long array
                    if (elementTag instanceof ByteArrayTag) {
                        byte[] from = ((ByteArrayTag) elementTag).getByteArray();
                        long[] to = new long[from.length];
                        for (int i = 0; i < from.length; i++)
                            to[i] = from[i];
                        converted = new LongArrayTag(to);
                    } else if (elementTag instanceof IntArrayTag) {
                        int[] from = ((IntArrayTag) elementTag).getIntArray();
                        long[] to = new long[from.length];
                        for (int i = 0; i < from.length; i++)
                            to[i] = from[i];
                        converted = new LongArrayTag(to);
                    } else {
                        converted = (LongArrayTag) elementTag;
                    }
                }
                ((ListTag) listTag).add(converted);
            } else {
                ((ListTag) listTag).add(elementTag);
            }
        }
        return listTag;
    }

    public static String simplifyIdentifier(Identifier id) {
        if (id == null)
            return "null";
        if ("minecraft".equals(id.getNamespace()))
            return id.getPath();
        else
            return id.toString();
    }

    public static boolean asBoolean(Value obj) {
        if (obj.isNull()) return false;
        if (obj.isBoolean()) return obj.asBoolean();
        if (obj.isNumber() && obj.fitsInDouble()) return obj.asDouble() != 0;
        throw new IllegalArgumentException("Cannot interpret " + obj + " as a boolean");
    }

    public static String asString(Value obj) {
        if (obj == null || obj.isNull()) return null;
        if (obj.isString()) return obj.asString();
        return String.valueOf(obj);
    }

    public static boolean isFunction(Value obj) {
        return obj.canExecute();
    }

    public static ScriptFunction asFunction(Value obj) {
        if (!obj.canExecute()) {
            throw new IllegalArgumentException("Cannot interpret " + obj + " as a function");
        }
        return obj::execute;
    }

    static Direction getDirectionFromString(String side) {
        if (side == null) {
            return null;
        }

        for (Direction dir : Direction.values()) {
            if (dir.name().equalsIgnoreCase(side)) {
                return dir;
            }
        }

        return null;
    }

    static Predicate<ItemStack> asItemStackPredicate(Value obj) {
        if (obj.isString()) {
            Item item = Registry.ITEM.get(new Identifier(asString(obj)));
            return stack -> stack.getItem() == item;
        } else if (isFunction(obj)) {
            ScriptFunction func = asFunction(obj);
            return stack -> {
                Object tag = fromNbt(stack.toTag(new CompoundTag()));
                return asBoolean(func.call(tag));
            };
        } else {
            Tag nbt = toNbt(obj);
            if (!(nbt instanceof CompoundTag))
                throw new IllegalArgumentException(obj.toString());
            return stack -> NbtHelper.matches(nbt, stack.toTag(new CompoundTag()), true);
        }
    }

    static <T> T unwrap(Value obj, Class<T> type) {
        if (obj.isHostObject() && obj.asHostObject() instanceof BeanWrapper) {
            obj = ((BeanWrapper) obj.asHostObject()).getDelegate();
        }
        if (obj.isHostObject() && type.isInstance(obj)) {
            return type.cast(obj.asHostObject());
        }
        return null;
    }
}
