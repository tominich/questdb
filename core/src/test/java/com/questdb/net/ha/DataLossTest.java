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

package com.questdb.net.ha;

import com.questdb.JournalWriter;
import com.questdb.model.Quote;
import com.questdb.net.ha.config.ClientConfig;
import com.questdb.net.ha.config.ServerConfig;
import com.questdb.net.ha.config.ServerNode;
import com.questdb.store.TxListener;
import com.questdb.test.tools.AbstractTest;
import com.questdb.test.tools.TestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DataLossTest extends AbstractTest {

    @Test
    public void testDiscardFile() throws Exception {

        // create master journal
        JournalWriter<Quote> master = factory.writer(Quote.class, "master");
        TestUtils.generateQuoteData(master, 300, master.getMaxTimestamp());
        master.commit();

        // publish master out
        JournalServer server = new JournalServer(
                new ServerConfig() {{
                    addNode(new ServerNode(0, "localhost"));
                    setEnableMultiCast(false);
                    setHeartbeatFrequency(50);
                }}
                , factory);
        server.publish(master);
        server.start();

        final AtomicInteger counter = new AtomicInteger();

        // equalize slave
        JournalClient client = new JournalClient(new ClientConfig("localhost") {{
            setEnableMultiCast(false);
        }}, factory);
        client.subscribe(Quote.class, "master", "slave", new TxListener() {
            @Override
            public void onCommit() {
                counter.incrementAndGet();
            }

            @Override
            public void onError() {

            }
        });
        client.start();

        TestUtils.assertCounter(counter, 1, 1, TimeUnit.SECONDS);

        // stop client to be able to add to slave manually
        client.halt();


        // add more data to slave
        JournalWriter<Quote> slave = factory.writer(Quote.class, "slave");
        TestUtils.generateQuoteData(slave, 200, slave.getMaxTimestamp());
        slave.commit();
        slave.close();

        // synchronise slave again
        client = new JournalClient(new ClientConfig("localhost"), factory);
        client.subscribe(Quote.class, "master", "slave", new TxListener() {
            @Override
            public void onCommit() {
                counter.incrementAndGet();
            }

            @Override
            public void onError() {

            }
        });
        client.start();

        TestUtils.generateQuoteData(master, 145, master.getMaxTimestamp());
        master.commit();

        TestUtils.assertCounter(counter, 2, 5, TimeUnit.SECONDS);
        client.halt();

        slave = factory.writer(Quote.class, "slave");
        TestUtils.assertDataEquals(master, slave);
        Assert.assertEquals(master.getTxn(), slave.getTxn());
        Assert.assertEquals(master.getTxPin(), slave.getTxPin());

        server.halt();
    }
}
