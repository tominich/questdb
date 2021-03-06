/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2016 Appsicle
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package org.questdb.examples.ha.cluster;

import com.questdb.Journal;
import com.questdb.JournalIterators;
import com.questdb.JournalKey;
import com.questdb.PartitionBy;
import com.questdb.factory.JournalFactory;
import com.questdb.factory.configuration.JournalConfigurationBuilder;
import com.questdb.net.ha.JournalClient;
import com.questdb.net.ha.config.ClientConfig;
import com.questdb.store.TxListener;
import org.questdb.examples.model.Price;

public class ClusterConsumerMain {
    public static void main(String[] args) throws Exception {

        final JournalFactory factory = new JournalFactory(new JournalConfigurationBuilder() {{
            $(Price.class).$ts();
        }}.build(args[0]));

        final JournalClient client = new JournalClient(new ClientConfig("192.168.1.81:7080,192.168.1.81:7090") {{
            getReconnectPolicy().setRetryCount(6);
            getReconnectPolicy().setSleepBetweenRetriesMillis(1);
            getReconnectPolicy().setLoginRetryCount(2);
        }}, factory);

        final Journal<Price> reader = factory.bulkReader(new JournalKey<>(Price.class, "price-copy", PartitionBy.NONE, 1000000000));

        client.subscribe(Price.class, null, "price-copy", 1000000000, new TxListener() {
            @Override
            public void onCommit() {
                int count = 0;
                long t = 0;
                for (Price p : JournalIterators.incrementBufferedIterator(reader)) {
                    if (count == 0) {
                        t = p.getNanos();
                    }
                    count++;
                }
                if (t == 0) {
                    System.out.println("no data received");
                } else {
                    System.out.println("took: " + (System.currentTimeMillis() - t) + ", count=" + count);
                }
            }

            @Override
            public void onError() {
                System.out.println("there was an error");
            }
        });
        client.start();

        System.out.println("Client started");
    }
}
