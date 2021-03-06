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

package com.questdb.net.http;

import com.questdb.ex.NetworkError;
import com.questdb.iter.clock.Clock;
import com.questdb.log.Log;
import com.questdb.log.LogFactory;
import com.questdb.misc.Files;
import com.questdb.misc.Misc;
import com.questdb.misc.Net;
import com.questdb.mp.*;
import com.questdb.net.Kqueue;
import com.questdb.net.NetworkChannelImpl;
import com.questdb.std.LongMatrix;

import java.io.IOException;

public class KQueueDispatcher extends SynchronizedJob implements IODispatcher {
    private static final Log LOG = LogFactory.getLog(KQueueDispatcher.class);

    private final long socketFd;
    private final RingQueue<IOEvent> ioQueue;
    private final Sequence ioSequence;
    private final RingQueue<IOEvent> interestQueue;
    private final MPSequence interestPubSequence;
    private final SCSequence interestSubSequence = new SCSequence();
    private final Clock clock;
    private final ServerConfiguration configuration;
    private final Kqueue kqueue;
    private final int timeout;
    private final LongMatrix<IOContext> pending = new LongMatrix<>(2);
    private final int maxConnections;
    private final int capacity;
    private int connectionCount = 0;

    public KQueueDispatcher(
            CharSequence ip,
            int port,
            RingQueue<IOEvent> ioQueue,
            Sequence ioSequence,
            Clock clock,
            ServerConfiguration configuration,
            int capacity
    ) {
        this.ioQueue = ioQueue;
        this.ioSequence = ioSequence;
        this.interestQueue = new RingQueue<>(IOEvent.FACTORY, ioQueue.getCapacity());
        this.interestPubSequence = new MPSequence(interestQueue.getCapacity());
        this.interestPubSequence.then(this.interestSubSequence).then(this.interestPubSequence);
        this.clock = clock;
        this.configuration = configuration;
        this.maxConnections = configuration.getHttpMaxConnections();
        this.timeout = configuration.getHttpTimeout();
        this.capacity = capacity;

        // bind socket
        this.kqueue = new Kqueue(capacity);
        this.socketFd = Net.socketTcp(false);
        if (Net.bind(this.socketFd, ip, port)) {
            Net.listen(this.socketFd, 128);
            this.kqueue.listen(socketFd);
            LOG.debug().$("Listening socket: ").$(socketFd).$();
        } else {
            throw new NetworkError("Failed to bind socket");
        }
    }

    @Override
    public void close() throws IOException {
        this.kqueue.close();
        Files.close(socketFd);
        int n = pending.size();
        for (int i = 0; i < n; i++) {
            Misc.free(pending.get(i));
        }
        pending.zapTop(n);
    }

    @Override
    public int getConnectionCount() {
        return connectionCount;
    }

    @Override
    public void registerChannel(IOContext context, int channelStatus) {
        long cursor = interestPubSequence.nextBully();
        IOEvent evt = interestQueue.get(cursor);
        evt.context = context;
        evt.channelStatus = channelStatus;
        LOG.debug().$("Re-queuing ").$(context.channel.getFd()).$();
        interestPubSequence.done(cursor);
    }

    private long accept() {
        long _fd = Net.accept(socketFd);
        LOG.info().$(" Connected ").$(_fd).$();

        // something not right
        if (_fd < 0) {
            LOG.error().$("Error in accept: ").$(_fd).$();
            return -1;
        }

        if (Net.configureNonBlocking(_fd) < 0) {
            LOG.error().$("Cannot make FD non-blocking").$();
            Files.close(_fd);
            return -1;
        }

        connectionCount++;

        if (connectionCount > maxConnections) {
            LOG.info().$("Too many connections, kicking out ").$(_fd).$();
            Files.close(_fd);
            connectionCount--;
            return -1;
        }

        return _fd;
    }

    private void addPending(long _fd, long timestamp) {
        // append to pending
        // all rows below watermark will be registered with kqueue
        int r = pending.addRow();
        LOG.debug().$(" Matrix row ").$(r).$(" for ").$(_fd).$();
        pending.set(r, 0, timestamp);
        pending.set(r, 1, _fd);
        NetworkChannelImpl channel = new NetworkChannelImpl(_fd);
        pending.set(r, new IOContext(channel, configuration, clock));
    }

    private void disconnect(IOContext context, int disconnectReason) {
        LOG.info().$("Disconnected ").$(context.channel.getFd()).$(": ").$(DisconnectReason.nameOf(disconnectReason)).$();
        context.close();
        connectionCount--;
    }

    private void enqueuePending(int watermark) {
        int index = 0;
        for (int i = watermark, sz = pending.size(), offset = 0; i < sz; i++, offset += Kqueue.SIZEOF_KEVENT) {
            kqueue.setOffset(offset);
            kqueue.readFD((int) pending.get(i, 1), pending.get(i, 0));
            LOG.debug().$("kqueued ").$(pending.get(i, 1)).$(" as ").$(index - 1).$();
            if (++index > capacity - 1) {
                kqueue.register(index);
                index = 0;
            }
        }
        if (index > 0) {
            kqueue.register(index);
            LOG.debug().$("Registered ").$(index).$();
        }
    }

    private int findPending(int fd, long ts) {
        int r = pending.binarySearch(ts);
        if (r < 0) {
            return r;
        }

        if (pending.get(r, 1) == fd) {
            return r;
        } else {
            return scanRow(r + 1, fd, ts);
        }
    }

    private void processIdleConnections(long deadline) {
        int count = 0;
        for (int i = 0, n = pending.size(); i < n && pending.get(i, 0) < deadline; i++, count++) {
            disconnect(pending.get(i), DisconnectReason.IDLE);
        }
        pending.zapTop(count);
    }

    private boolean processRegistrations(long timestamp) {
        long cursor;
        boolean useful = false;
        int count = 0;
        int offset = 0;
        while ((cursor = interestSubSequence.next()) > -1) {
            useful = true;
            IOEvent evt = interestQueue.get(cursor);
            IOContext context = evt.context;
            int channelStatus = evt.channelStatus;
            interestSubSequence.done(cursor);

            int fd = (int) context.channel.getFd();
            LOG.debug().$("Registering ").$(fd).$(" status ").$(channelStatus).$();
            kqueue.setOffset(offset);
            offset += Kqueue.SIZEOF_KEVENT;
            count++;
            switch (channelStatus) {
                case ChannelStatus.READ:
                    kqueue.readFD(fd, timestamp);
                    break;
                case ChannelStatus.WRITE:
                    kqueue.writeFD(fd, timestamp);
                    break;
                case ChannelStatus.DISCONNECTED:
                    disconnect(context, DisconnectReason.SILLY);
                    continue;
                case ChannelStatus.EOF:
                    disconnect(context, DisconnectReason.PEER);
                    continue;
                default:
                    break;
            }

            int r = pending.addRow();
            pending.set(r, 0, timestamp);
            pending.set(r, 1, fd);
            pending.set(r, context);


            if (count > capacity - 1) {
                kqueue.register(count);
                count = 0;
            }
        }

        if (count > 0) {
            kqueue.register(count);
        }

        return useful;
    }

    @Override
    protected boolean runSerially() {
        boolean useful = false;
        final int n = kqueue.poll();
        int watermark = pending.size();
        final long timestamp = clock.getTicks();
        int offset = 0;
        if (n > 0) {
            // check all activated FDs
            for (int i = 0; i < n; i++) {
                kqueue.setOffset(offset);
                offset += Kqueue.SIZEOF_KEVENT;
                int fd = kqueue.getFd();
                // this is server socket, accept if there aren't too many already
                if (fd == socketFd) {
                    long _fd = accept();
                    if (_fd < 0) {
                        continue;
                    }
                    addPending(_fd, timestamp);
                } else {
                    // find row in pending for two reasons:
                    // 1. find payload
                    // 2. remove row from pending, remaining rows will be timed out
                    int row = findPending(fd, kqueue.getData());
                    if (row < 0) {
                        LOG.error().$("Internal error: unknown FD: ").$(fd).$();
                        continue;
                    }

                    if (kqueue.getFlags() == Kqueue.EV_EOF) {
                        disconnect(pending.get(row), DisconnectReason.PEER);
                    } else {
                        long cursor = ioSequence.nextBully();
                        IOEvent evt = ioQueue.get(cursor);
                        evt.context = pending.get(row);
                        evt.channelStatus = kqueue.getFilter() == Kqueue.EVFILT_READ ? ChannelStatus.READ : ChannelStatus.WRITE;
                        ioSequence.done(cursor);
                        LOG.debug().$("Queuing ").$(kqueue.getFilter()).$(" on ").$(fd).$();
                    }
                    pending.deleteRow(row);
                    watermark--;
                }
            }

            // process rows over watermark
            if (watermark < pending.size()) {
                enqueuePending(watermark);
            }
            useful = true;
        }

        // process timed out connections
        long deadline = timestamp - timeout;
        if (pending.size() > 0 && pending.get(0, 0) < deadline) {
            processIdleConnections(deadline);
            useful = true;
        }

        return processRegistrations(timestamp) || useful;
    }

    private int scanRow(int r, int fd, long ts) {
        for (int i = r, n = pending.size(); i < n; i++) {
            // timestamps not match?
            if (pending.get(i, 0) != ts) {
                return -(i + 1);
            }

            if (pending.get(i, 1) == fd) {
                return i;
            }
        }
        return -1;
    }
}
