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

import com.questdb.JournalMode;
import com.questdb.ex.JournalException;
import com.questdb.ex.JournalNoSuchFileException;
import com.questdb.ex.JournalRuntimeException;
import com.questdb.log.Log;
import com.questdb.log.LogFactory;
import com.questdb.misc.*;
import com.questdb.std.ObjList;

import java.io.*;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class MemoryFile implements Closeable {

    private static final Log LOG = LogFactory.getLog(MemoryFile.class);
    // reserve first 8 bytes in the file for storing pointer to logical end of file
    // so the actual data begins from "DATA_OFFSET"
    private final static int DATA_OFFSET = 8;
    private final File file;
    private final int journalMode;
    private int bitHint;
    private FileChannel channel;
    private MappedByteBuffer offsetBuffer;
    private ObjList<MappedByteBuffer> buffers;
    private ObjList<ByteBufferWrapper> stitches;
    private MappedByteBuffer cachedBuffer;
    private long cachedBufferLo = -1;
    private long cachedBufferHi = -1;
    private long cachedAppendOffset = -1;
    private long cachedAddress;
    private long offsetDirectAddr;
    private boolean unlockedBuffers = true;

    public MemoryFile(File file, int bitHint, int journalMode) throws JournalException {
        this.file = file;
        this.journalMode = journalMode;
        if (bitHint < 2) {
            LOG.info().$("BitHint is too small for ").$(file).$();
        }
        this.bitHint = bitHint;
        long size = open();
        this.buffers = new ObjList<>((int) (size >>> bitHint) + 1);
        this.stitches = new ObjList<>(buffers.size());
    }

    public long addressOf(long offset, int size) {
        if (offset > cachedBufferLo && offset + size < cachedBufferHi) {
            return cachedAddress + offset - cachedBufferLo - 1;
        } else {
            return allocateAddress(offset, size);
        }
    }

    @Override
    public void close() {
        unmap();
        this.channel = Misc.free(channel);
    }

    public void compact() throws JournalException {
        close();
        try {
            openInternal("rw");
            try {
                long newSize = getAppendOffset() + DATA_OFFSET;
                offsetBuffer = ByteBuffers.release(offsetBuffer);
                LOG.debug().$("Compacting ").$(this).$(" to ").$(newSize).$(" bytes").$();
                channel.truncate(newSize).close();
            } catch (IOException e) {
                throw new JournalException("Could not compact %s to %d bytes", e, getFullFileName(), getAppendOffset());
            } finally {
                close();
            }
        } finally {
            open();
        }
    }

    public void delete() {
        close();
        Files.delete(file);
    }

    public void force() {
        int stitchesSize = stitches.size();
        offsetBuffer.force();
        for (int i = 0, k = buffers.size(); i < k; i++) {
            MappedByteBuffer b = buffers.getQuick(i);
            if (b != null) {
                b.force();
            }

            if (i < stitchesSize) {
                ByteBufferWrapper s = stitches.getQuick(i);
                if (s != null) {
                    s.getByteBuffer().force();
                }
            }
        }
    }

    public long getAppendOffset() {
        if (cachedAppendOffset != -1 && (journalMode == JournalMode.APPEND || journalMode == JournalMode.BULK_APPEND)) {
            return cachedAppendOffset;
        } else {
            if (offsetBuffer != null) {
                return cachedAppendOffset = Unsafe.getUnsafe().getLong(offsetDirectAddr);
            }
            return -1L;
        }
    }

    public void setAppendOffset(long offset) {
        Unsafe.getUnsafe().putLong(offsetDirectAddr, cachedAppendOffset = offset);
    }

    public MappedByteBuffer getBuffer(long offset, int size) {
        if (offset > cachedBufferLo && offset + size < cachedBufferHi) {
            cachedBuffer.position((int) (offset - cachedBufferLo - 1));
        } else {
            cachedBuffer = getBufferInternal(offset, size);
            cachedBufferLo = offset - cachedBuffer.position() - 1;
            cachedBufferHi = cachedBufferLo + cachedBuffer.limit() + 2;
            cachedAddress = ByteBuffers.getAddress(cachedBuffer);
        }
        return cachedBuffer;
    }

    public void lockBuffers() {
        unlockedBuffers = false;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "[file=" + file + ", appendOffset=" + getAppendOffset() + ']';
    }

    public void unlockBuffers() {
        unlockedBuffers = true;
    }

    private long allocateAddress(long offset, int size) {
        cachedBuffer = getBufferInternal(offset, size);
        cachedBufferLo = offset - cachedBuffer.position() - 1;
        cachedBufferHi = cachedBufferLo + cachedBuffer.limit() + 2;
        cachedAddress = ByteBuffers.getAddress(cachedBuffer);
        return cachedAddress + cachedBuffer.position();
    }

    private MappedByteBuffer createMappedBuffer(int index, int bufferSize, long bufferOffset) {
        MappedByteBuffer buffer;
        buffer = mapBufferInternal(bufferOffset, bufferSize);
        assert bufferSize > 0;
        buffers.extendAndSet(index, buffer);
        if (unlockedBuffers) {
            switch (journalMode) {
                case JournalMode.BULK_READ:
                case JournalMode.BULK_APPEND:
                    // for bulk operations unmap all buffers except for current one
                    // this is to prevent OS paging large files.
                    cachedBuffer = null;
                    cachedBufferLo = cachedBufferHi = -1;
                    int ssz = stitches.size();
                    for (int i = index - 1; i > -1; i--) {
                        ByteBuffers.release(buffers.getAndSetQuick(i, null));
                        if (i < ssz) {
                            Misc.free(stitches.getAndSetQuick(i, null));
                        }
                    }
                    break;
                default:
                    break;
            }
        }
        return buffer;
    }

    private MappedByteBuffer createStitchBuffer(int size, int index, long stitchOffset) {
        ByteBufferWrapper bufferWrapper = stitches.getQuiet(index);
        if (bufferWrapper != null) {
            // if we already have a stitch for this buffer
            // it could be too small for the size
            // if that's the case - discard the existing stitch and create a larger one.
            if (bufferWrapper.getOffset() != stitchOffset || bufferWrapper.getByteBuffer().limit() < size) {
                bufferWrapper.close();
                bufferWrapper = null;
            } else {
                bufferWrapper.getByteBuffer().rewind();
            }
        }

        if (bufferWrapper == null) {
            bufferWrapper = new ByteBufferWrapper(stitchOffset, mapBufferInternal(stitchOffset, size));
            stitches.extendAndSet(index, bufferWrapper);
        }

        return bufferWrapper.getByteBuffer();
    }

    private MappedByteBuffer getBufferInternal(long offset, int size) {

        int bufferSize = 1 << bitHint;
        int index = (int) (offset >>> bitHint);
        long bufferOffset = ((long) index) << ((long) bitHint);
        int bufferPos = (int) (offset - bufferOffset);

        MappedByteBuffer buffer = buffers.getQuiet(index);

        // this may occur when journal is refreshed.
        if (buffer != null && buffer.limit() < bufferPos) {
            buffer = ByteBuffers.release(buffer);
        }

        if (buffer == null) {
            buffer = createMappedBuffer(index, bufferSize, bufferOffset);
        }

        buffer.position(bufferPos);

        if (buffer.remaining() < size) {
            // if the desired size is larger than remaining buffer we need to crate
            // a stitch buffer, which would accommodate the size
            return createStitchBuffer(size, index, bufferOffset + bufferPos);
        }
        return buffer;
    }

    private String getFullFileName() {
        return this.file.getAbsolutePath();
    }

    private MappedByteBuffer mapBufferInternal(long offset, int size) {
        long actualOffset = offset + DATA_OFFSET;

        try {
            MappedByteBuffer buf;
            switch (journalMode) {
                case JournalMode.READ:
                case JournalMode.BULK_READ:
                    // make sure size does not extend beyond actual file size, otherwise
                    // java would assume we want to write and throw an exception
                    long sz;
                    if (actualOffset + ((long) size) > channel.size()) {
                        sz = channel.size() - actualOffset;
                    } else {
                        sz = size;
                    }
                    assert sz > 0;
                    buf = channel.map(FileChannel.MapMode.READ_ONLY, actualOffset, sz);
                    break;
                default:
                    buf = channel.map(FileChannel.MapMode.READ_WRITE, actualOffset, size);
                    break;
            }
            buf.order(ByteOrder.LITTLE_ENDIAN);
            return buf;
        } catch (IOException e) {
            throw new JournalRuntimeException("Failed to memory map: %s", e, file.getAbsolutePath());
        }
    }

    private long open() throws JournalException {
        String m;
        switch (journalMode) {
            case JournalMode.READ:
            case JournalMode.BULK_READ:
                m = "r";
                break;
            default:
                m = "rw";
                break;
        }
        return openInternal(m);
    }

    private long openInternal(String mode) throws JournalException {

        File pf = file.getParentFile();
        if (pf == null) {
            throw new JournalException("Expected a file: " + file);
        }

        if (!pf.exists() && !pf.mkdirs()) {
            throw new JournalException("Could not create directories: %s", file.getParentFile().getAbsolutePath());
        }

        try {
            this.channel = new RandomAccessFile(file, mode).getChannel();
        } catch (FileNotFoundException e) {
            throw new JournalNoSuchFileException(e);
        }

        long size;
        try {
            size = channel.size();
        } catch (IOException e) {
            throw new JournalException(e);
        }

        try {
            if ("r".equals(mode)) {
                this.offsetBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, Math.min(size, 8));
            } else {
                this.offsetBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, 8);
            }
            offsetBuffer.order(ByteOrder.LITTLE_ENDIAN);
            offsetDirectAddr = ByteBuffers.getAddress(offsetBuffer);
            // adjust bitHint to make sure it is not too small
            long offset = getAppendOffset();
            if (offset > 0) {
                if (offset > Integer.MAX_VALUE) {
                    bitHint = 30;
                } else {
                    int hint = Numbers.msb((int) offset);
                    if (hint < 0) {
                        bitHint = 30;
                    } else if (hint > bitHint) {
                        bitHint = hint;
                    }
                }
            }
        } catch (IOException e) {
            try {
                this.channel.close();
            } catch (IOException ignore) {
                // ignore
            }
            this.channel = null;
            throw new JournalException(e);
        }

        return size;
    }

    int pageRemaining(long offset) {
        if (offset > cachedBufferLo && offset < cachedBufferHi) {
            return (int) (cachedBufferHi - offset - 1);
        } else {
            return 0;
        }
    }

    private void unmap() {
        for (int i = 0, k = buffers.size(); i < k; i++) {
            ByteBuffers.release(buffers.getQuick(i));
        }
        for (int i = 0, k = stitches.size(); i < k; i++) {
            Misc.free(stitches.getQuick(i));
        }
        cachedBuffer = null;
        cachedBufferLo = cachedBufferHi = -1;
        buffers.clear();
        stitches.clear();

        offsetBuffer = ByteBuffers.release(offsetBuffer);
        assert offsetBuffer == null;
    }
}
