/*******************************************************************************
 *    ___                  _   ____  ____
 *   / _ \ _   _  ___  ___| |_|  _ \| __ )
 *  | | | | | | |/ _ \/ __| __| | | |  _ \
 *  | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *   \__\_\\__,_|\___||___/\__|____/|____/
 *
 * Copyright (C) 2014-2016 Appsicle
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************************/

package com.questdb.store;

import com.questdb.std.CharSequenceIntHashMap;
import com.questdb.std.IntIntHashMap;
import com.questdb.std.IntObjHashMap;
import com.questdb.std.ObjIntHashMap;

import java.nio.ByteBuffer;

public final class ColumnType {
    public static final int BOOLEAN = 1;
    public static final int BYTE = 2;
    public static final int DOUBLE = 4;
    public static final int FLOAT = 8;
    public static final int INT = 16;
    public static final int LONG = 32;
    public static final int SHORT = 64;
    public static final int STRING = 128;
    public static final int SYMBOL = 256;
    public static final int BINARY = 512;
    public static final int DATE = 1024;
    public static final int PARAMETER = 2048;
    public static final int TIMESTAMP = 4096;
    private static final ObjIntHashMap<Class> classMap = new ObjIntHashMap<>();
    private static final IntIntHashMap sizeMap = new IntIntHashMap();
    private static final IntObjHashMap<CharSequence> typeNameMap = new IntObjHashMap<>();
    private static final CharSequenceIntHashMap nameTypeMap = new CharSequenceIntHashMap();

    private ColumnType() {
    }

    public static int columnTypeOf(Class clazz) {
        return classMap.get(clazz);
    }

    public static int columnTypeOf(CharSequence name) {
        return nameTypeMap.get(name);
    }

    public static CharSequence nameOf(int columnType) {
        return typeNameMap.get(columnType);
    }

    public static int sizeOf(int columnType) {
        return sizeMap.get(columnType);
    }

    static {
        classMap.put(boolean.class, BOOLEAN);
        classMap.put(byte.class, BYTE);
        classMap.put(double.class, DOUBLE);
        classMap.put(float.class, FLOAT);
        classMap.put(int.class, INT);
        classMap.put(long.class, LONG);
        classMap.put(short.class, SHORT);
        classMap.put(String.class, STRING);
        classMap.put(ByteBuffer.class, BINARY);

        sizeMap.put(BOOLEAN, 1);
        sizeMap.put(BYTE, 1);
        sizeMap.put(DOUBLE, 8);
        sizeMap.put(FLOAT, 4);
        sizeMap.put(INT, 4);
        sizeMap.put(LONG, 8);
        sizeMap.put(SHORT, 2);
        sizeMap.put(STRING, 0);
        sizeMap.put(SYMBOL, 4);
        sizeMap.put(BINARY, 0);
        sizeMap.put(DATE, 8);
        sizeMap.put(PARAMETER, 0);

        typeNameMap.put(BOOLEAN, "BOOLEAN");
        typeNameMap.put(BYTE, "BYTE");
        typeNameMap.put(DOUBLE, "DOUBLE");
        typeNameMap.put(FLOAT, "FLOAT");
        typeNameMap.put(INT, "INT");
        typeNameMap.put(LONG, "LONG");
        typeNameMap.put(SHORT, "SHORT");
        typeNameMap.put(STRING, "STRING");
        typeNameMap.put(SYMBOL, "SYMBOL");
        typeNameMap.put(BINARY, "BINARY");
        typeNameMap.put(DATE, "DATE");
        typeNameMap.put(PARAMETER, "PARAMETER");

        nameTypeMap.put("BOOLEAN", BOOLEAN);
        nameTypeMap.put("BYTE", BYTE);
        nameTypeMap.put("DOUBLE", DOUBLE);
        nameTypeMap.put("FLOAT", FLOAT);
        nameTypeMap.put("INT", INT);
        nameTypeMap.put("LONG", LONG);
        nameTypeMap.put("SHORT", SHORT);
        nameTypeMap.put("STRING", STRING);
        nameTypeMap.put("SYMBOL", SYMBOL);
        nameTypeMap.put("BINARY", BINARY);
        nameTypeMap.put("DATE", DATE);
        nameTypeMap.put("PARAMETER", PARAMETER);
        nameTypeMap.put("TIMESTAMP", TIMESTAMP);
    }
}
