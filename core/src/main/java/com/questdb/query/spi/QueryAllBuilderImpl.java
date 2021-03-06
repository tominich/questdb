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

package com.questdb.query.spi;

import com.questdb.Journal;
import com.questdb.ex.JournalException;
import com.questdb.misc.Interval;
import com.questdb.query.UnorderedResultSet;
import com.questdb.query.api.QueryAllBuilder;
import com.questdb.std.IntList;
import com.questdb.std.ObjList;
import com.questdb.store.SymbolTable;

public class QueryAllBuilderImpl<T> implements QueryAllBuilder<T> {

    private final Journal<T> journal;
    private final IntList symbolKeys = new IntList();
    private final ObjList<String> filterSymbols = new ObjList<>();
    private final IntList filterSymbolKeys = new IntList();
    private String symbol;
    private Interval interval;

    public QueryAllBuilderImpl(Journal<T> journal) {
        this.journal = journal;
    }

    @Override
    public UnorderedResultSet<T> asResultSet() throws JournalException {
        return journal.iteratePartitionsDesc(new QueryAllResultSetBuilder<T>(interval, symbol, symbolKeys, filterSymbols, filterSymbolKeys));
    }

    @Override
    public QueryAllBuilder<T> filter(String symbol, String value) {
        SymbolTable tab = journal.getSymbolTable(symbol);
        int key = tab.get(value);
        filterSymbols.add(symbol);
        filterSymbolKeys.add(key);
        return this;
    }

    @Override
    public void resetFilter() {
        filterSymbols.clear();
        filterSymbolKeys.clear();
    }

    @Override
    public QueryAllBuilder<T> slice(Interval interval) {
        setInterval(interval);
        return this;
    }

    public void setSymbol(String symbol, String... values) {
        this.symbol = symbol;
        SymbolTable symbolTable = journal.getSymbolTable(symbol);
        this.symbolKeys.clear();
        for (int i = 0; i < values.length; i++) {
            int key = symbolTable.getQuick(values[i]);
            if (key != SymbolTable.VALUE_NOT_FOUND) {
                symbolKeys.add(key);
            }
        }
    }

    private void setInterval(Interval interval) {
        this.interval = interval;
    }
}
