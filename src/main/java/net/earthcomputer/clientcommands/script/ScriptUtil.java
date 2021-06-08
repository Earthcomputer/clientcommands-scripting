package net.earthcomputer.clientcommands.script;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.AbstractNbtList;
import net.minecraft.nbt.AbstractNbtNumber;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtByteArray;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.nbt.NbtLongArray;
import net.minecraft.nbt.NbtShort;
import net.minecraft.nbt.NbtString;
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

    public static Object fromNbt(NbtElement tag) {
        if (tag instanceof NbtCompound) {
            return fromNbtCompound((NbtCompound) tag);
        } else if (tag instanceof AbstractNbtList) {
            return fromNbtList((AbstractNbtList<?>) tag);
        } else if (tag instanceof NbtString) {
            return tag.asString();
        } else if (tag instanceof NbtLong) {
            return ((NbtLong) tag).longValue();
        } else if (tag instanceof AbstractNbtNumber) {
            return ((AbstractNbtNumber) tag).doubleValue();
        } else {
            throw new IllegalStateException("Unknown tag type " + tag.getType());
        }
    }

    public static Object fromNbtCompound(NbtCompound tag) {
        Map<String, Object> map = new HashMap<>(tag.getSize());
        tag.getKeys().forEach(key -> map.put(key, fromNbt(tag.get(key))));
        return map;
    }

    public static Object fromNbtList(AbstractNbtList<?> tag) {
        List<Object> list = new ArrayList<>(tag.size());
        tag.forEach(val -> list.add(fromNbt(val)));
        return list;
    }

    public static NbtElement toNbt(Value obj) {
        if (obj.isBoolean()) {
            return NbtByte.of(obj.asBoolean());
        } else if (obj.isNumber()) {
            if (obj.fitsInByte()) {
                return NbtByte.of(obj.asByte());
            } else if (obj.fitsInShort()) {
                return NbtShort.of(obj.asShort());
            } else if (obj.fitsInInt()) {
                return NbtInt.of(obj.asInt());
            } else if (obj.fitsInFloat()) {
                return NbtFloat.of(obj.asFloat());
            } else if (obj.fitsInLong()) {
                return NbtLong.of(obj.asLong());
            } else if (obj.fitsInDouble()) {
                return NbtDouble.of(obj.asDouble());
            } else {
                return NbtDouble.of(Double.NaN);
            }
        } else if (obj.isString()) {
            return NbtString.of(obj.asString());
        } else if (obj.hasArrayElements()) {
            return arrayToNbtList(obj);
        } else {
            return objectToNbtCompound(obj);
        }
    }

    private static NbtCompound objectToNbtCompound(Value obj) {
        NbtCompound compound = new NbtCompound();
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

    private static AbstractNbtList<?> arrayToNbtList(Value array) {
        byte type = getArrayType(array);
        int len = (int) array.getArraySize();
        AbstractNbtList<?> listTag;
        if (type == 1) // byte
            listTag = new NbtByteArray(new byte[len]);
        else if (type == 2 || type == 3) // short, int
            listTag = new NbtIntArray(new int[len]);
        else if (type == 4) // long
            listTag = new NbtLongArray(new long[len]);
        else
            listTag = new NbtList();

        for (int index = 0; index < len; index++) {
            Value element = array.getArrayElement(index);
            NbtElement elementTag = toNbt(element);
            if (type <= 4) { // integral number
                if (!(elementTag instanceof AbstractNbtNumber))
                    throw new IllegalStateException();
                AbstractNbtNumber num = (AbstractNbtNumber) elementTag;
                if (type == 1) // byte
                    ((NbtByteArray) listTag).getByteArray()[index] = num.byteValue();
                else if (type == 2 || type == 3) // short, int
                    ((NbtIntArray) listTag).getIntArray()[index] = num.intValue();
                else if (type == 4) // long
                    ((NbtLongArray) listTag).getLongArray()[index] = num.longValue();
            } else if (type == 7 || type == 11 || type == 12) { // integral arrays
                if (!(elementTag instanceof AbstractNbtList))
                    throw new IllegalStateException();
                AbstractNbtList<?> converted;
                if (type == 7) { // byte array
                    if (elementTag instanceof NbtByteArray) {
                        converted = (NbtByteArray) elementTag;
                    } else if (elementTag instanceof NbtIntArray) {
                        int[] from = ((NbtIntArray) elementTag).getIntArray();
                        byte[] to = new byte[from.length];
                        for (int i = 0; i < from.length; i++)
                            to[i] = (byte) from[i];
                        converted = new NbtByteArray(to);
                    } else {
                        long[] from = ((NbtLongArray) elementTag).getLongArray();
                        byte[] to = new byte[from.length];
                        for (int i = 0; i < from.length; i++)
                            to[i] = (byte) from[i];
                        converted = new NbtByteArray(to);
                    }
                } else if (type == 11) { // int array
                    if (elementTag instanceof NbtByteArray) {
                        byte[] from = ((NbtByteArray) elementTag).getByteArray();
                        int[] to = new int[from.length];
                        for (int i = 0; i < from.length; i++)
                            to[i] = from[i];
                        converted = new NbtIntArray(to);
                    } else if (elementTag instanceof NbtIntArray) {
                        converted = (NbtIntArray) elementTag;
                    } else {
                        long[] from = ((NbtLongArray) elementTag).getLongArray();
                        int[] to = new int[from.length];
                        for (int i = 0; i < from.length; i++)
                            to[i] = (int) from[i];
                        converted = new NbtIntArray(to);
                    }
                } else { // long array
                    if (elementTag instanceof NbtByteArray) {
                        byte[] from = ((NbtByteArray) elementTag).getByteArray();
                        long[] to = new long[from.length];
                        for (int i = 0; i < from.length; i++)
                            to[i] = from[i];
                        converted = new NbtLongArray(to);
                    } else if (elementTag instanceof NbtIntArray) {
                        int[] from = ((NbtIntArray) elementTag).getIntArray();
                        long[] to = new long[from.length];
                        for (int i = 0; i < from.length; i++)
                            to[i] = from[i];
                        converted = new NbtLongArray(to);
                    } else {
                        converted = (NbtLongArray) elementTag;
                    }
                }
                ((NbtList) listTag).add(converted);
            } else {
                ((NbtList) listTag).add(elementTag);
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
                Object tag = fromNbt(stack.writeNbt(new NbtCompound()));
                return asBoolean(func.call(tag));
            };
        } else {
            NbtElement nbt = toNbt(obj);
            if (!(nbt instanceof NbtCompound))
                throw new IllegalArgumentException(obj.toString());
            return stack -> NbtHelper.matches(nbt, stack.writeNbt(new NbtCompound()), true);
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
