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

package com.questdb.ql.impl;

import com.questdb.factory.JournalReaderFactory;
import com.questdb.factory.configuration.RecordMetadata;
import com.questdb.misc.Misc;
import com.questdb.ql.*;
import com.questdb.ql.ops.AbstractCombinedRecordSource;
import com.questdb.std.CharSink;

public class NoOpJournalRecordSource extends AbstractCombinedRecordSource {

    private final RecordSource delegate;
    private RecordCursor cursor;

    public NoOpJournalRecordSource(RecordSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public void close() {
        Misc.free(delegate);
    }

    @Override
    public RecordMetadata getMetadata() {
        return delegate.getMetadata();
    }

    @Override
    public RecordCursor prepareCursor(JournalReaderFactory factory, CancellationHandler cancellationHandler) {
        this.cursor = delegate.prepareCursor(factory, cancellationHandler);
        return this;
    }

    @Override
    public StorageFacade getStorageFacade() {
        return cursor.getStorageFacade();
    }

    @Override
    public boolean hasNext() {
        return false;
    }

    @Override
    public Record next() {
        return null;
    }

    @Override
    public Record newRecord() {
        return cursor.newRecord();
    }

    @Override
    public Record recordAt(long rowId) {
        return cursor.recordAt(rowId);
    }

    @Override
    public void recordAt(Record record, long atRowId) {
        cursor.recordAt(record, atRowId);
    }

    @Override
    public boolean supportsRowIdAccess() {
        return delegate.supportsRowIdAccess();
    }

    @Override
    public void toSink(CharSink sink) {
        sink.put('{');
        sink.putQuoted("op").put(':').putQuoted("NoOpJournalRecordSource").put(',');
        sink.putQuoted("src").put(':').put(delegate);
        sink.put('}');
    }
}
