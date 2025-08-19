package com.debugger.entity.nbt;

import net.minecraft.nbt.NbtElement;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public enum NbtType {
    END(NbtElement.END_TYPE, Formatting.WHITE),
    BYTE(NbtElement.BYTE_TYPE, Formatting.BLUE),
    SHORT(NbtElement.SHORT_TYPE, Formatting.GOLD),
    INT(NbtElement.INT_TYPE, Formatting.GOLD),
    LONG(NbtElement.LONG_TYPE, Formatting.GOLD),
    FLOAT(NbtElement.FLOAT_TYPE, Formatting.GOLD),
    DOUBLE(NbtElement.DOUBLE_TYPE, Formatting.GOLD),
    STRING(NbtElement.STRING_TYPE, Formatting.GREEN),
    LIST(NbtElement.LIST_TYPE, Formatting.DARK_AQUA),
    COMPOUND(NbtElement.COMPOUND_TYPE, Formatting.DARK_GRAY);

    private final byte typeId;
    private final Formatting color;

    NbtType(byte typeId, Formatting color) {
        this.typeId = typeId;
        this.color = color;
    }

    public static NbtType fromId(byte id) {
        for (NbtType type : values()) {
            if (type.typeId == id) return type;
        }
        return END;
    }
}