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

package com.questdb.ql;

import com.questdb.JournalEntryWriter;
import com.questdb.JournalWriter;
import com.questdb.ex.ParserException;
import com.questdb.factory.configuration.JournalStructure;
import com.questdb.misc.Dates;
import com.questdb.misc.Rnd;
import com.questdb.ql.parser.AbstractOptimiserTest;
import com.questdb.ql.parser.QueryError;
import com.questdb.std.ObjList;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class SymbolNullQueryTest extends AbstractOptimiserTest {
    @BeforeClass
    public static void setUp() throws Exception {

        int tradeCount = 100;
        int quoteCount = 300;

        JournalWriter trades = factory.writer(
                new JournalStructure("trades").
                        $int("quoteId").
                        $sym("tag1").
                        $double("amount").
                        recordCountHint(tradeCount).
                        $ts()
        );

        JournalWriter quotes = factory.writer(
                new JournalStructure("quotes").
                        $int("quoteId").
                        $sym("tag").
                        $double("rate").
                        recordCountHint(quoteCount).
                        $ts()
        );

        int tsIncrementMax = 10000;

        long timestamp = Dates.parseDateTime("2015-03-23T00:00:00.000Z");

        Rnd rnd = new Rnd();
        ObjList<String> tags = new ObjList<>();
        for (int i = 0; i < 500; i++) {
            tags.add(rnd.nextBoolean() ? rnd.nextString(rnd.nextInt() & 15) : null);
        }

        for (int i = 0; i < quoteCount; i++) {
            JournalEntryWriter w = quotes.entryWriter();
            w.putInt(0, i);
            w.putSym(1, tags.getQuick(rnd.nextPositiveInt() % tags.size()));
            w.putDouble(2, rnd.nextDouble());
            w.putDate(3, timestamp += rnd.nextPositiveInt() % tsIncrementMax);
            w.append();
        }
        quotes.commit();


        timestamp = Dates.parseDateTime("2015-03-23T00:00:00.000Z");

        for (int i = 0; i < tradeCount; i++) {
            JournalEntryWriter w = trades.entryWriter();
            w.putInt(0, rnd.nextPositiveInt() % quoteCount);
            w.putSym(1, tags.getQuick(rnd.nextPositiveInt() % tags.size()));
            w.putDouble(2, rnd.nextDouble());
            w.putDate(3, timestamp += rnd.nextPositiveInt() % tsIncrementMax);
            w.append();
        }

        quotes.close();
        trades.close();
    }

    @Test
    public void testAsOfLeftNull() throws Exception {
        final String expected = "quoteId\ttag1\tamount\ttimestamp\tquoteId\ttag\trate\ttimestamp\n" +
                "167\tnull\t0.000269699180\t2015-03-23T00:00:00.041Z\tNaN\tnull\tNaN\t\n" +
                "185\tnull\t320.000000000000\t2015-03-23T00:00:03.905Z\t0\tnull\t1.803355813026\t2015-03-23T00:00:00.213Z\n" +
                "208\tnull\t0.000283450820\t2015-03-23T00:00:09.777Z\t1\tnull\t0.000070708433\t2015-03-23T00:00:06.860Z\n" +
                "253\tnull\t0.000006260114\t2015-03-23T00:00:14.319Z\t4\tnull\t0.000000004546\t2015-03-23T00:00:13.174Z\n" +
                "280\tnull\t0.000001666620\t2015-03-23T00:00:21.324Z\tNaN\tnull\tNaN\t\n" +
                "250\tnull\t391.318801879883\t2015-03-23T00:00:38.605Z\t9\tVQ\t-453.500000000000\t2015-03-23T00:00:37.483Z\n" +
                "189\tnull\t638.125000000000\t2015-03-23T00:00:54.293Z\t13\tnull\t367.500000000000\t2015-03-23T00:00:53.663Z\n" +
                "79\tnull\t0.000000003843\t2015-03-23T00:01:00.686Z\tNaN\tnull\tNaN\t\n" +
                "160\tnull\t-804.000000000000\t2015-03-23T00:01:22.281Z\t17\tnull\t0.000010729342\t2015-03-23T00:01:16.171Z\n" +
                "146\tnull\t114.411033630371\t2015-03-23T00:01:35.805Z\t20\tVPPLIPRMDB\t-512.000000000000\t2015-03-23T00:01:31.409Z\n" +
                "275\tnull\t0.000000135166\t2015-03-23T00:01:46.298Z\t26\tnull\t227.781250000000\t2015-03-23T00:01:45.081Z\n" +
                "38\tnull\t0.000006406346\t2015-03-23T00:01:51.405Z\t27\tnull\t-881.312500000000\t2015-03-23T00:01:50.570Z\n" +
                "174\tnull\t0.003814977244\t2015-03-23T00:01:54.120Z\t28\tMCGFNWGRMDGGI\t0.018065215554\t2015-03-23T00:01:52.498Z\n" +
                "32\tnull\t0.000000000000\t2015-03-23T00:02:33.565Z\t33\tWZNF\t389.500000000000\t2015-03-23T00:02:27.245Z\n" +
                "25\tnull\t0.000000521190\t2015-03-23T00:02:38.743Z\t35\tBKFIJZZYNPP\t1.767193734646\t2015-03-23T00:02:38.532Z\n" +
                "247\tnull\t1.480640053749\t2015-03-23T00:03:05.144Z\t40\tBJFRPX\t3.300331056118\t2015-03-23T00:03:01.677Z\n" +
                "11\tnull\t0.000000837886\t2015-03-23T00:03:12.568Z\t41\tNRXGZSXUX\t-429.100708007813\t2015-03-23T00:03:08.533Z\n" +
                "242\tnull\t-448.687500000000\t2015-03-23T00:03:18.692Z\t43\tnull\t7.534469366074\t2015-03-23T00:03:18.528Z\n" +
                "125\tnull\t384.000000000000\t2015-03-23T00:03:24.655Z\tNaN\tnull\tNaN\t\n" +
                "38\tnull\t0.000000042007\t2015-03-23T00:03:56.155Z\t53\tnull\t-129.359375000000\t2015-03-23T00:03:55.883Z\n" +
                "61\tnull\t0.002688131004\t2015-03-23T00:03:59.991Z\t55\tnull\t260.000000000000\t2015-03-23T00:03:58.594Z\n" +
                "135\tnull\t11.436011791229\t2015-03-23T00:04:15.031Z\t57\tnull\t0.023898910731\t2015-03-23T00:04:09.740Z\n" +
                "67\tnull\t0.000000017864\t2015-03-23T00:04:25.683Z\t59\tnull\t67.070381164551\t2015-03-23T00:04:23.097Z\n" +
                "5\tnull\t0.001699611719\t2015-03-23T00:04:32.638Z\t60\tnull\t0.000019430104\t2015-03-23T00:04:26.550Z\n" +
                "197\tnull\t-639.687500000000\t2015-03-23T00:04:34.193Z\tNaN\tnull\tNaN\t\n" +
                "295\tnull\t3.262981295586\t2015-03-23T00:04:39.143Z\t61\tBKFIJZZYNPP\t97.859756469727\t2015-03-23T00:04:34.967Z\n" +
                "11\tnull\t1.727517306805\t2015-03-23T00:04:52.639Z\t63\tnull\t74.151687622070\t2015-03-23T00:04:47.655Z\n" +
                "47\tnull\t673.375000000000\t2015-03-23T00:04:56.064Z\t64\tQOLYXWCKYLSUWD\t-965.125000000000\t2015-03-23T00:04:55.914Z\n" +
                "97\tnull\t0.000031514332\t2015-03-23T00:05:04.467Z\t66\tKXPMSXQ\t0.000044817871\t2015-03-23T00:05:01.550Z\n" +
                "52\tnull\t-982.250000000000\t2015-03-23T00:05:06.948Z\t67\tGSVCLLERSM\t214.940444946289\t2015-03-23T00:05:05.637Z\n" +
                "101\tnull\t0.085098911077\t2015-03-23T00:05:07.455Z\t68\tnull\t0.374871194363\t2015-03-23T00:05:07.144Z\n" +
                "204\tnull\t-864.000000000000\t2015-03-23T00:05:14.905Z\t70\tQG\t0.417922869325\t2015-03-23T00:05:12.520Z\n" +
                "157\tnull\t0.000000038206\t2015-03-23T00:05:24.607Z\t73\tnull\t-941.475097656250\t2015-03-23T00:05:24.096Z\n" +
                "27\tnull\t0.128199812025\t2015-03-23T00:05:30.349Z\tNaN\tnull\tNaN\t\n" +
                "294\tnull\t32.320184707642\t2015-03-23T00:05:35.452Z\t75\tnull\t0.112449161708\t2015-03-23T00:05:34.377Z\n" +
                "175\tnull\t0.000000096432\t2015-03-23T00:05:37.064Z\tNaN\tnull\tNaN\t\n" +
                "140\tnull\t-384.000000000000\t2015-03-23T00:05:40.901Z\tNaN\tnull\tNaN\t\n" +
                "118\tnull\t-768.000000000000\t2015-03-23T00:05:50.089Z\t78\tnull\t0.000000443235\t2015-03-23T00:05:48.820Z\n" +
                "88\tnull\t0.000019480709\t2015-03-23T00:05:50.803Z\tNaN\tnull\tNaN\t\n" +
                "221\tnull\t-308.000000000000\t2015-03-23T00:05:54.120Z\tNaN\tnull\tNaN\t\n" +
                "214\tnull\t-1024.000000000000\t2015-03-23T00:06:00.720Z\t80\tFFLTRYZUZYJ\t-1024.000000000000\t2015-03-23T00:05:59.140Z\n" +
                "278\tnull\t100.157985687256\t2015-03-23T00:06:06.542Z\t81\tnull\t-116.000000000000\t2015-03-23T00:06:06.359Z\n" +
                "61\tnull\t-1024.000000000000\t2015-03-23T00:06:08.793Z\tNaN\tnull\tNaN\t\n" +
                "180\tnull\t0.223312780261\t2015-03-23T00:06:10.929Z\tNaN\tnull\tNaN\t\n" +
                "94\tnull\t0.000006785318\t2015-03-23T00:06:15.648Z\t84\tnull\t0.000000001883\t2015-03-23T00:06:13.867Z\n" +
                "242\tnull\t-557.027343750000\t2015-03-23T00:06:24.968Z\t85\tnull\t0.000000042277\t2015-03-23T00:06:21.010Z\n" +
                "68\tnull\t0.000000001771\t2015-03-23T00:06:26.396Z\tNaN\tnull\tNaN\t\n" +
                "64\tnull\t-512.546875000000\t2015-03-23T00:06:26.865Z\tNaN\tnull\tNaN\t\n" +
                "41\tnull\t0.000001296927\t2015-03-23T00:06:34.398Z\t87\tnull\t0.000199647140\t2015-03-23T00:06:28.009Z\n" +
                "40\tnull\t0.352436579764\t2015-03-23T00:06:44.446Z\t88\tONWE\t0.000125047416\t2015-03-23T00:06:36.408Z\n" +
                "127\tnull\t0.000750448322\t2015-03-23T00:07:07.023Z\t94\tNKG\t0.000032506398\t2015-03-23T00:07:06.427Z\n" +
                "284\tnull\t0.000000056363\t2015-03-23T00:07:24.963Z\t100\tKRGIIHY\t0.000000004512\t2015-03-23T00:07:22.960Z\n" +
                "69\tnull\t-64.000000000000\t2015-03-23T00:07:32.794Z\t102\tnull\t21.335580825806\t2015-03-23T00:07:28.848Z\n" +
                "206\tnull\t0.000001646644\t2015-03-23T00:07:40.787Z\t103\tnull\t89.232910156250\t2015-03-23T00:07:38.365Z\n" +
                "110\tnull\t882.590759277344\t2015-03-23T00:07:44.604Z\tNaN\tnull\tNaN\t\n" +
                "208\tnull\t0.000000045985\t2015-03-23T00:07:48.156Z\t104\t\t-512.000000000000\t2015-03-23T00:07:45.981Z\n" +
                "287\tnull\t-732.302734375000\t2015-03-23T00:07:54.893Z\t105\tDWWLEVMLKCJBEVL\t0.000000012333\t2015-03-23T00:07:48.554Z\n" +
                "208\tnull\t0.000000447563\t2015-03-23T00:08:04.779Z\t107\tnull\t0.006601167843\t2015-03-23T00:07:58.974Z\n" +
                "190\tnull\t0.000000005669\t2015-03-23T00:08:10.452Z\t108\tVPPLIPRMDB\t0.003471667413\t2015-03-23T00:08:06.561Z\n" +
                "266\tnull\t0.000002058839\t2015-03-23T00:08:14.465Z\tNaN\tnull\tNaN\t\n" +
                "15\tnull\t0.000000004841\t2015-03-23T00:08:23.335Z\t111\tRZUPVQFULM\t0.000000017550\t2015-03-23T00:08:22.158Z\n";
        assertThat(expected, "trades t asof join quotes q where tag1 = null", true);
        assertThat(expected, "trades t asof join quotes q where null = tag1", true);
    }

    @Test
    public void testAsOfPartitionedJoinLeftNull() throws Exception {
        final String expected = "quoteId\ttag1\tamount\ttimestamp\tquoteId\ttag\trate\ttimestamp\n" +
                "167\tnull\t0.000269699180\t2015-03-23T00:00:00.041Z\tNaN\tnull\tNaN\t\n" +
                "185\tnull\t320.000000000000\t2015-03-23T00:00:03.905Z\tNaN\tnull\tNaN\t\n" +
                "208\tnull\t0.000283450820\t2015-03-23T00:00:09.777Z\tNaN\tnull\tNaN\t\n" +
                "253\tnull\t0.000006260114\t2015-03-23T00:00:14.319Z\tNaN\tnull\tNaN\t\n" +
                "280\tnull\t0.000001666620\t2015-03-23T00:00:21.324Z\tNaN\tnull\tNaN\t\n" +
                "250\tnull\t391.318801879883\t2015-03-23T00:00:38.605Z\tNaN\tnull\tNaN\t\n" +
                "189\tnull\t638.125000000000\t2015-03-23T00:00:54.293Z\tNaN\tnull\tNaN\t\n" +
                "79\tnull\t0.000000003843\t2015-03-23T00:01:00.686Z\tNaN\tnull\tNaN\t\n" +
                "160\tnull\t-804.000000000000\t2015-03-23T00:01:22.281Z\tNaN\tnull\tNaN\t\n" +
                "146\tnull\t114.411033630371\t2015-03-23T00:01:35.805Z\tNaN\tnull\tNaN\t\n" +
                "275\tnull\t0.000000135166\t2015-03-23T00:01:46.298Z\tNaN\tnull\tNaN\t\n" +
                "38\tnull\t0.000006406346\t2015-03-23T00:01:51.405Z\tNaN\tnull\tNaN\t\n" +
                "174\tnull\t0.003814977244\t2015-03-23T00:01:54.120Z\tNaN\tnull\tNaN\t\n" +
                "32\tnull\t0.000000000000\t2015-03-23T00:02:33.565Z\t32\tFORGFIEVM\t0.000000013271\t2015-03-23T00:02:19.770Z\n" +
                "25\tnull\t0.000000521190\t2015-03-23T00:02:38.743Z\t25\tnull\t-1024.000000000000\t2015-03-23T00:01:41.578Z\n" +
                "247\tnull\t1.480640053749\t2015-03-23T00:03:05.144Z\tNaN\tnull\tNaN\t\n" +
                "11\tnull\t0.000000837886\t2015-03-23T00:03:12.568Z\t11\tnull\t109.844512939453\t2015-03-23T00:00:52.378Z\n" +
                "242\tnull\t-448.687500000000\t2015-03-23T00:03:18.692Z\tNaN\tnull\tNaN\t\n" +
                "125\tnull\t384.000000000000\t2015-03-23T00:03:24.655Z\tNaN\tnull\tNaN\t\n" +
                "38\tnull\t0.000000042007\t2015-03-23T00:03:56.155Z\t38\tnull\t88.440750122070\t2015-03-23T00:02:48.897Z\n" +
                "61\tnull\t0.002688131004\t2015-03-23T00:03:59.991Z\tNaN\tnull\tNaN\t\n" +
                "135\tnull\t11.436011791229\t2015-03-23T00:04:15.031Z\tNaN\tnull\tNaN\t\n" +
                "67\tnull\t0.000000017864\t2015-03-23T00:04:25.683Z\tNaN\tnull\tNaN\t\n" +
                "5\tnull\t0.001699611719\t2015-03-23T00:04:32.638Z\t5\tNEJRMDIKDISGQ\t0.000017155876\t2015-03-23T00:00:21.617Z\n" +
                "197\tnull\t-639.687500000000\t2015-03-23T00:04:34.193Z\tNaN\tnull\tNaN\t\n" +
                "295\tnull\t3.262981295586\t2015-03-23T00:04:39.143Z\tNaN\tnull\tNaN\t\n" +
                "11\tnull\t1.727517306805\t2015-03-23T00:04:52.639Z\tNaN\tnull\tNaN\t\n" +
                "47\tnull\t673.375000000000\t2015-03-23T00:04:56.064Z\t47\tRHHMGZJYYFLS\t0.005148356780\t2015-03-23T00:03:29.150Z\n" +
                "97\tnull\t0.000031514332\t2015-03-23T00:05:04.467Z\tNaN\tnull\tNaN\t\n" +
                "52\tnull\t-982.250000000000\t2015-03-23T00:05:06.948Z\t52\tX\t0.000004397260\t2015-03-23T00:03:52.621Z\n" +
                "101\tnull\t0.085098911077\t2015-03-23T00:05:07.455Z\tNaN\tnull\tNaN\t\n" +
                "204\tnull\t-864.000000000000\t2015-03-23T00:05:14.905Z\tNaN\tnull\tNaN\t\n" +
                "157\tnull\t0.000000038206\t2015-03-23T00:05:24.607Z\tNaN\tnull\tNaN\t\n" +
                "27\tnull\t0.128199812025\t2015-03-23T00:05:30.349Z\tNaN\tnull\tNaN\t\n" +
                "294\tnull\t32.320184707642\t2015-03-23T00:05:35.452Z\tNaN\tnull\tNaN\t\n" +
                "175\tnull\t0.000000096432\t2015-03-23T00:05:37.064Z\tNaN\tnull\tNaN\t\n" +
                "140\tnull\t-384.000000000000\t2015-03-23T00:05:40.901Z\tNaN\tnull\tNaN\t\n" +
                "118\tnull\t-768.000000000000\t2015-03-23T00:05:50.089Z\tNaN\tnull\tNaN\t\n" +
                "88\tnull\t0.000019480709\t2015-03-23T00:05:50.803Z\tNaN\tnull\tNaN\t\n" +
                "221\tnull\t-308.000000000000\t2015-03-23T00:05:54.120Z\tNaN\tnull\tNaN\t\n" +
                "214\tnull\t-1024.000000000000\t2015-03-23T00:06:00.720Z\tNaN\tnull\tNaN\t\n" +
                "278\tnull\t100.157985687256\t2015-03-23T00:06:06.542Z\tNaN\tnull\tNaN\t\n" +
                "61\tnull\t-1024.000000000000\t2015-03-23T00:06:08.793Z\tNaN\tnull\tNaN\t\n" +
                "180\tnull\t0.223312780261\t2015-03-23T00:06:10.929Z\tNaN\tnull\tNaN\t\n" +
                "94\tnull\t0.000006785318\t2015-03-23T00:06:15.648Z\tNaN\tnull\tNaN\t\n" +
                "242\tnull\t-557.027343750000\t2015-03-23T00:06:24.968Z\tNaN\tnull\tNaN\t\n" +
                "68\tnull\t0.000000001771\t2015-03-23T00:06:26.396Z\tNaN\tnull\tNaN\t\n" +
                "64\tnull\t-512.546875000000\t2015-03-23T00:06:26.865Z\tNaN\tnull\tNaN\t\n" +
                "41\tnull\t0.000001296927\t2015-03-23T00:06:34.398Z\t41\tNRXGZSXUX\t-429.100708007813\t2015-03-23T00:03:08.533Z\n" +
                "40\tnull\t0.352436579764\t2015-03-23T00:06:44.446Z\t40\tBJFRPX\t3.300331056118\t2015-03-23T00:03:01.677Z\n" +
                "127\tnull\t0.000750448322\t2015-03-23T00:07:07.023Z\tNaN\tnull\tNaN\t\n" +
                "284\tnull\t0.000000056363\t2015-03-23T00:07:24.963Z\tNaN\tnull\tNaN\t\n" +
                "69\tnull\t-64.000000000000\t2015-03-23T00:07:32.794Z\t69\tnull\t0.000000001976\t2015-03-23T00:05:12.110Z\n" +
                "206\tnull\t0.000001646644\t2015-03-23T00:07:40.787Z\tNaN\tnull\tNaN\t\n" +
                "110\tnull\t882.590759277344\t2015-03-23T00:07:44.604Z\tNaN\tnull\tNaN\t\n" +
                "208\tnull\t0.000000045985\t2015-03-23T00:07:48.156Z\tNaN\tnull\tNaN\t\n" +
                "287\tnull\t-732.302734375000\t2015-03-23T00:07:54.893Z\tNaN\tnull\tNaN\t\n" +
                "208\tnull\t0.000000447563\t2015-03-23T00:08:04.779Z\tNaN\tnull\tNaN\t\n" +
                "190\tnull\t0.000000005669\t2015-03-23T00:08:10.452Z\tNaN\tnull\tNaN\t\n" +
                "266\tnull\t0.000002058839\t2015-03-23T00:08:14.465Z\tNaN\tnull\tNaN\t\n" +
                "15\tnull\t0.000000004841\t2015-03-23T00:08:23.335Z\t15\tMYICCXZOUIC\t-504.062500000000\t2015-03-23T00:01:09.396Z\n";

        assertThat(expected, "trades t asof join quotes q on t.quoteId = q.quoteId where tag1 = null", true);
        assertThat(expected, "trades t asof join quotes q on t.quoteId = q.quoteId where null = tag1", true);
    }

    @Test
    public void testAsOfPartitionedJoinRightNull() throws Exception {
        final String expected = "quoteId\ttag1\tamount\ttimestamp\tquoteId\ttag\trate\ttimestamp\n" +
                "167\tnull\t0.000269699180\t2015-03-23T00:00:00.041Z\tNaN\tnull\tNaN\t\n" +
                "185\tnull\t320.000000000000\t2015-03-23T00:00:03.905Z\tNaN\tnull\tNaN\t\n" +
                "208\tnull\t0.000283450820\t2015-03-23T00:00:09.777Z\tNaN\tnull\tNaN\t\n" +
                "253\tnull\t0.000006260114\t2015-03-23T00:00:14.319Z\tNaN\tnull\tNaN\t\n" +
                "280\tnull\t0.000001666620\t2015-03-23T00:00:21.324Z\tNaN\tnull\tNaN\t\n" +
                "62\tUUQIDLVBVKH\t-655.093750000000\t2015-03-23T00:00:26.965Z\tNaN\tnull\tNaN\t\n" +
                "159\tOJIGFINKGQVZ\t52.300781250000\t2015-03-23T00:00:31.747Z\tNaN\tnull\tNaN\t\n" +
                "250\tnull\t391.318801879883\t2015-03-23T00:00:38.605Z\tNaN\tnull\tNaN\t\n" +
                "236\tQDSRDJWIMGPLRQU\t0.559725582600\t2015-03-23T00:00:46.696Z\tNaN\tnull\tNaN\t\n" +
                "189\tnull\t638.125000000000\t2015-03-23T00:00:54.293Z\tNaN\tnull\tNaN\t\n" +
                "206\tDDBHEVGXY\t0.000000983339\t2015-03-23T00:00:54.651Z\tNaN\tnull\tNaN\t\n" +
                "79\tnull\t0.000000003843\t2015-03-23T00:01:00.686Z\tNaN\tnull\tNaN\t\n" +
                "98\tI\t0.095442861319\t2015-03-23T00:01:08.481Z\tNaN\tnull\tNaN\t\n" +
                "160\tnull\t-804.000000000000\t2015-03-23T00:01:22.281Z\tNaN\tnull\tNaN\t\n" +
                "114\tFCKDHBQJPLXZGC\t295.336006164551\t2015-03-23T00:01:26.844Z\tNaN\tnull\tNaN\t\n" +
                "146\tnull\t114.411033630371\t2015-03-23T00:01:35.805Z\tNaN\tnull\tNaN\t\n" +
                "32\tUHNBCCPM\t28.844047546387\t2015-03-23T00:01:39.759Z\tNaN\tnull\tNaN\t\n" +
                "275\tnull\t0.000000135166\t2015-03-23T00:01:46.298Z\tNaN\tnull\tNaN\t\n" +
                "38\tnull\t0.000006406346\t2015-03-23T00:01:51.405Z\tNaN\tnull\tNaN\t\n" +
                "174\tnull\t0.003814977244\t2015-03-23T00:01:54.120Z\tNaN\tnull\tNaN\t\n" +
                "279\tREIJ\t0.001741334971\t2015-03-23T00:02:02.280Z\tNaN\tnull\tNaN\t\n" +
                "83\tTYLHXVPGHPSF\t-768.000000000000\t2015-03-23T00:02:10.007Z\tNaN\tnull\tNaN\t\n" +
                "164\tZWWCCNGTNLEGPUH\t0.000006020679\t2015-03-23T00:02:19.093Z\tNaN\tnull\tNaN\t\n" +
                "178\tQDSRDJWIMGPLRQU\t-482.000000000000\t2015-03-23T00:02:21.866Z\tNaN\tnull\tNaN\t\n" +
                "17\tIOVIKJS\t1.924843549728\t2015-03-23T00:02:24.538Z\tNaN\tnull\tNaN\t\n" +
                "25\tnull\t0.000000521190\t2015-03-23T00:02:38.743Z\t25\tnull\t-1024.000000000000\t2015-03-23T00:01:41.578Z\n" +
                "233\tV\t0.000000017818\t2015-03-23T00:02:43.116Z\tNaN\tnull\tNaN\t\n" +
                "112\tUVV\t979.898437500000\t2015-03-23T00:02:49.249Z\tNaN\tnull\tNaN\t\n" +
                "295\tJYED\t881.624389648438\t2015-03-23T00:02:58.778Z\tNaN\tnull\tNaN\t\n" +
                "247\tnull\t1.480640053749\t2015-03-23T00:03:05.144Z\tNaN\tnull\tNaN\t\n" +
                "188\tMDJTHMHZNV\t128.000000000000\t2015-03-23T00:03:11.761Z\tNaN\tnull\tNaN\t\n" +
                "11\tnull\t0.000000837886\t2015-03-23T00:03:12.568Z\tNaN\tnull\tNaN\t\n" +
                "242\tnull\t-448.687500000000\t2015-03-23T00:03:18.692Z\tNaN\tnull\tNaN\t\n" +
                "125\tnull\t384.000000000000\t2015-03-23T00:03:24.655Z\tNaN\tnull\tNaN\t\n" +
                "272\tOSBOS\t911.400665283203\t2015-03-23T00:03:34.502Z\tNaN\tnull\tNaN\t\n" +
                "13\tUMEUKVZI\t976.000000000000\t2015-03-23T00:03:48.769Z\t13\tnull\t367.500000000000\t2015-03-23T00:00:53.663Z\n" +
                "38\tnull\t0.000000042007\t2015-03-23T00:03:56.155Z\t38\tnull\t88.440750122070\t2015-03-23T00:02:48.897Z\n" +
                "61\tnull\t0.002688131004\t2015-03-23T00:03:59.991Z\tNaN\tnull\tNaN\t\n" +
                "217\tDWWLEVMLKCJBEVL\t-871.023925781250\t2015-03-23T00:04:05.708Z\tNaN\tnull\tNaN\t\n" +
                "135\tnull\t11.436011791229\t2015-03-23T00:04:15.031Z\tNaN\tnull\tNaN\t\n" +
                "230\tRZUPVQFULM\t10.552783966064\t2015-03-23T00:04:23.609Z\tNaN\tnull\tNaN\t\n" +
                "67\tnull\t0.000000017864\t2015-03-23T00:04:25.683Z\tNaN\tnull\tNaN\t\n" +
                "200\tCJOU\t0.000000498588\t2015-03-23T00:04:30.988Z\tNaN\tnull\tNaN\t\n" +
                "5\tnull\t0.001699611719\t2015-03-23T00:04:32.638Z\tNaN\tnull\tNaN\t\n" +
                "197\tnull\t-639.687500000000\t2015-03-23T00:04:34.193Z\tNaN\tnull\tNaN\t\n" +
                "295\tnull\t3.262981295586\t2015-03-23T00:04:39.143Z\tNaN\tnull\tNaN\t\n" +
                "11\tnull\t1.727517306805\t2015-03-23T00:04:52.639Z\t11\tnull\t109.844512939453\t2015-03-23T00:00:52.378Z\n" +
                "97\tnull\t0.000031514332\t2015-03-23T00:05:04.467Z\tNaN\tnull\tNaN\t\n" +
                "101\tnull\t0.085098911077\t2015-03-23T00:05:07.455Z\tNaN\tnull\tNaN\t\n" +
                "245\tUVDRHF\t152.633743286133\t2015-03-23T00:05:12.773Z\tNaN\tnull\tNaN\t\n" +
                "204\tnull\t-864.000000000000\t2015-03-23T00:05:14.905Z\tNaN\tnull\tNaN\t\n" +
                "157\tnull\t0.000000038206\t2015-03-23T00:05:24.607Z\tNaN\tnull\tNaN\t\n" +
                "27\tnull\t0.128199812025\t2015-03-23T00:05:30.349Z\tNaN\tnull\tNaN\t\n" +
                "294\tnull\t32.320184707642\t2015-03-23T00:05:35.452Z\tNaN\tnull\tNaN\t\n" +
                "145\tPHNIMYFFDTNP\t0.000007616574\t2015-03-23T00:05:36.249Z\tNaN\tnull\tNaN\t\n" +
                "175\tnull\t0.000000096432\t2015-03-23T00:05:37.064Z\tNaN\tnull\tNaN\t\n" +
                "140\tnull\t-384.000000000000\t2015-03-23T00:05:40.901Z\tNaN\tnull\tNaN\t\n" +
                "118\tnull\t-768.000000000000\t2015-03-23T00:05:50.089Z\tNaN\tnull\tNaN\t\n" +
                "88\tnull\t0.000019480709\t2015-03-23T00:05:50.803Z\tNaN\tnull\tNaN\t\n" +
                "209\tRIIYMHO\t0.036849732511\t2015-03-23T00:05:50.822Z\tNaN\tnull\tNaN\t\n" +
                "221\tnull\t-308.000000000000\t2015-03-23T00:05:54.120Z\tNaN\tnull\tNaN\t\n" +
                "214\tnull\t-1024.000000000000\t2015-03-23T00:06:00.720Z\tNaN\tnull\tNaN\t\n" +
                "278\tnull\t100.157985687256\t2015-03-23T00:06:06.542Z\tNaN\tnull\tNaN\t\n" +
                "219\tSBEOUOJSHRU\t0.007685951889\t2015-03-23T00:06:06.860Z\tNaN\tnull\tNaN\t\n" +
                "61\tnull\t-1024.000000000000\t2015-03-23T00:06:08.793Z\tNaN\tnull\tNaN\t\n" +
                "180\tnull\t0.223312780261\t2015-03-23T00:06:10.929Z\tNaN\tnull\tNaN\t\n" +
                "94\tnull\t0.000006785318\t2015-03-23T00:06:15.648Z\tNaN\tnull\tNaN\t\n" +
                "240\tPNHTDCEBYWXB\t0.000000007430\t2015-03-23T00:06:23.214Z\tNaN\tnull\tNaN\t\n" +
                "242\tnull\t-557.027343750000\t2015-03-23T00:06:24.968Z\tNaN\tnull\tNaN\t\n" +
                "68\tnull\t0.000000001771\t2015-03-23T00:06:26.396Z\tNaN\tnull\tNaN\t\n" +
                "64\tnull\t-512.546875000000\t2015-03-23T00:06:26.865Z\tNaN\tnull\tNaN\t\n" +
                "101\t\t0.003191990079\t2015-03-23T00:06:38.252Z\tNaN\tnull\tNaN\t\n" +
                "32\tSIMYDXUUSKCX\t0.091201189905\t2015-03-23T00:06:42.748Z\tNaN\tnull\tNaN\t\n" +
                "40\tnull\t0.352436579764\t2015-03-23T00:06:44.446Z\tNaN\tnull\tNaN\t\n" +
                "279\tNEJRMDIKDISGQ\t-186.680175781250\t2015-03-23T00:06:50.222Z\tNaN\tnull\tNaN\t\n" +
                "238\tSED\t0.000000019996\t2015-03-23T00:06:58.896Z\tNaN\tnull\tNaN\t\n" +
                "127\tnull\t0.000750448322\t2015-03-23T00:07:07.023Z\tNaN\tnull\tNaN\t\n" +
                "277\tIGENFELWWRSLBM\t-305.000000000000\t2015-03-23T00:07:16.365Z\tNaN\tnull\tNaN\t\n" +
                "284\tnull\t0.000000056363\t2015-03-23T00:07:24.963Z\tNaN\tnull\tNaN\t\n" +
                "69\tnull\t-64.000000000000\t2015-03-23T00:07:32.794Z\t69\tnull\t0.000000001976\t2015-03-23T00:05:12.110Z\n" +
                "206\tnull\t0.000001646644\t2015-03-23T00:07:40.787Z\tNaN\tnull\tNaN\t\n" +
                "2\tVQ\t0.000000002305\t2015-03-23T00:07:43.087Z\tNaN\tnull\tNaN\t\n" +
                "110\tnull\t882.590759277344\t2015-03-23T00:07:44.604Z\tNaN\tnull\tNaN\t\n" +
                "208\tnull\t0.000000045985\t2015-03-23T00:07:48.156Z\tNaN\tnull\tNaN\t\n" +
                "287\tnull\t-732.302734375000\t2015-03-23T00:07:54.893Z\tNaN\tnull\tNaN\t\n" +
                "81\tGK\t0.000279274151\t2015-03-23T00:07:54.905Z\tNaN\tnull\tNaN\t\n" +
                "208\tnull\t0.000000447563\t2015-03-23T00:08:04.779Z\tNaN\tnull\tNaN\t\n" +
                "190\tnull\t0.000000005669\t2015-03-23T00:08:10.452Z\tNaN\tnull\tNaN\t\n" +
                "266\tnull\t0.000002058839\t2015-03-23T00:08:14.465Z\tNaN\tnull\tNaN\t\n" +
                "270\tUDVIK\t0.000000391720\t2015-03-23T00:08:17.846Z\tNaN\tnull\tNaN\t\n" +
                "24\tSVTN\t123.810607910156\t2015-03-23T00:08:20.910Z\tNaN\tnull\tNaN\t\n" +
                "216\tZUFEV\t0.000000036476\t2015-03-23T00:08:23.335Z\tNaN\tnull\tNaN\t\n" +
                "15\tnull\t0.000000004841\t2015-03-23T00:08:23.335Z\tNaN\tnull\tNaN\t\n";
        assertThat(expected, "trades t asof join quotes q on t.quoteId = q.quoteId where tag = null", true);
        assertThat(expected, "trades t asof join quotes q on t.quoteId = q.quoteId where null = tag", true);
    }

    @Test
    public void testAsOfRightNull() throws Exception {
        final String expected = "quoteId\ttag1\tamount\ttimestamp\tquoteId\ttag\trate\ttimestamp\n" +
                "167\tnull\t0.000269699180\t2015-03-23T00:00:00.041Z\tNaN\tnull\tNaN\t\n" +
                "185\tnull\t320.000000000000\t2015-03-23T00:00:03.905Z\t0\tnull\t1.803355813026\t2015-03-23T00:00:00.213Z\n" +
                "208\tnull\t0.000283450820\t2015-03-23T00:00:09.777Z\t1\tnull\t0.000070708433\t2015-03-23T00:00:06.860Z\n" +
                "253\tnull\t0.000006260114\t2015-03-23T00:00:14.319Z\t4\tnull\t0.000000004546\t2015-03-23T00:00:13.174Z\n" +
                "280\tnull\t0.000001666620\t2015-03-23T00:00:21.324Z\tNaN\tnull\tNaN\t\n" +
                "62\tUUQIDLVBVKH\t-655.093750000000\t2015-03-23T00:00:26.965Z\t7\tnull\t279.477539062500\t2015-03-23T00:00:24.224Z\n" +
                "159\tOJIGFINKGQVZ\t52.300781250000\t2015-03-23T00:00:31.747Z\t8\tnull\t0.069348402321\t2015-03-23T00:00:30.911Z\n" +
                "236\tQDSRDJWIMGPLRQU\t0.559725582600\t2015-03-23T00:00:46.696Z\t10\tnull\t0.039842596278\t2015-03-23T00:00:43.910Z\n" +
                "189\tnull\t638.125000000000\t2015-03-23T00:00:54.293Z\t13\tnull\t367.500000000000\t2015-03-23T00:00:53.663Z\n" +
                "206\tDDBHEVGXY\t0.000000983339\t2015-03-23T00:00:54.651Z\tNaN\tnull\tNaN\t\n" +
                "79\tnull\t0.000000003843\t2015-03-23T00:01:00.686Z\tNaN\tnull\tNaN\t\n" +
                "98\tI\t0.095442861319\t2015-03-23T00:01:08.481Z\t14\tnull\t552.000000000000\t2015-03-23T00:01:03.502Z\n" +
                "15\t\t0.013024759479\t2015-03-23T00:01:17.114Z\t17\tnull\t0.000010729342\t2015-03-23T00:01:16.171Z\n" +
                "160\tnull\t-804.000000000000\t2015-03-23T00:01:22.281Z\tNaN\tnull\tNaN\t\n" +
                "275\tnull\t0.000000135166\t2015-03-23T00:01:46.298Z\t26\tnull\t227.781250000000\t2015-03-23T00:01:45.081Z\n" +
                "38\tnull\t0.000006406346\t2015-03-23T00:01:51.405Z\t27\tnull\t-881.312500000000\t2015-03-23T00:01:50.570Z\n" +
                "164\tZWWCCNGTNLEGPUH\t0.000006020679\t2015-03-23T00:02:19.093Z\t31\tnull\t17.086778640747\t2015-03-23T00:02:12.621Z\n" +
                "17\tIOVIKJS\t1.924843549728\t2015-03-23T00:02:24.538Z\tNaN\tnull\tNaN\t\n" +
                "233\tV\t0.000000017818\t2015-03-23T00:02:43.116Z\t36\tnull\t0.000040406951\t2015-03-23T00:02:41.818Z\n" +
                "112\tUVV\t979.898437500000\t2015-03-23T00:02:49.249Z\t38\tnull\t88.440750122070\t2015-03-23T00:02:48.897Z\n" +
                "295\tJYED\t881.624389648438\t2015-03-23T00:02:58.778Z\t39\tnull\t0.001135725528\t2015-03-23T00:02:54.956Z\n" +
                "11\tnull\t0.000000837886\t2015-03-23T00:03:12.568Z\tNaN\tnull\tNaN\t\n" +
                "242\tnull\t-448.687500000000\t2015-03-23T00:03:18.692Z\t43\tnull\t7.534469366074\t2015-03-23T00:03:18.528Z\n" +
                "125\tnull\t384.000000000000\t2015-03-23T00:03:24.655Z\tNaN\tnull\tNaN\t\n" +
                "37\t\t0.000000000000\t2015-03-23T00:03:40.522Z\t49\tnull\t0.003723925911\t2015-03-23T00:03:37.137Z\n" +
                "38\tnull\t0.000000042007\t2015-03-23T00:03:56.155Z\t53\tnull\t-129.359375000000\t2015-03-23T00:03:55.883Z\n" +
                "61\tnull\t0.002688131004\t2015-03-23T00:03:59.991Z\t55\tnull\t260.000000000000\t2015-03-23T00:03:58.594Z\n" +
                "217\tDWWLEVMLKCJBEVL\t-871.023925781250\t2015-03-23T00:04:05.708Z\tNaN\tnull\tNaN\t\n" +
                "135\tnull\t11.436011791229\t2015-03-23T00:04:15.031Z\t57\tnull\t0.023898910731\t2015-03-23T00:04:09.740Z\n" +
                "230\tRZUPVQFULM\t10.552783966064\t2015-03-23T00:04:23.609Z\t59\tnull\t67.070381164551\t2015-03-23T00:04:23.097Z\n" +
                "67\tnull\t0.000000017864\t2015-03-23T00:04:25.683Z\tNaN\tnull\tNaN\t\n" +
                "200\tCJOU\t0.000000498588\t2015-03-23T00:04:30.988Z\t60\tnull\t0.000019430104\t2015-03-23T00:04:26.550Z\n" +
                "5\tnull\t0.001699611719\t2015-03-23T00:04:32.638Z\tNaN\tnull\tNaN\t\n" +
                "197\tnull\t-639.687500000000\t2015-03-23T00:04:34.193Z\tNaN\tnull\tNaN\t\n" +
                "50\tDNZNL\t-258.093750000000\t2015-03-23T00:04:46.445Z\t62\tnull\t0.000000003979\t2015-03-23T00:04:44.544Z\n" +
                "11\tnull\t1.727517306805\t2015-03-23T00:04:52.639Z\t63\tnull\t74.151687622070\t2015-03-23T00:04:47.655Z\n" +
                "101\tnull\t0.085098911077\t2015-03-23T00:05:07.455Z\t68\tnull\t0.374871194363\t2015-03-23T00:05:07.144Z\n" +
                "204\tnull\t-864.000000000000\t2015-03-23T00:05:14.905Z\tNaN\tnull\tNaN\t\n" +
                "157\tnull\t0.000000038206\t2015-03-23T00:05:24.607Z\t73\tnull\t-941.475097656250\t2015-03-23T00:05:24.096Z\n" +
                "27\tnull\t0.128199812025\t2015-03-23T00:05:30.349Z\tNaN\tnull\tNaN\t\n" +
                "294\tnull\t32.320184707642\t2015-03-23T00:05:35.452Z\t75\tnull\t0.112449161708\t2015-03-23T00:05:34.377Z\n" +
                "145\tPHNIMYFFDTNP\t0.000007616574\t2015-03-23T00:05:36.249Z\tNaN\tnull\tNaN\t\n" +
                "175\tnull\t0.000000096432\t2015-03-23T00:05:37.064Z\tNaN\tnull\tNaN\t\n" +
                "140\tnull\t-384.000000000000\t2015-03-23T00:05:40.901Z\tNaN\tnull\tNaN\t\n" +
                "118\tnull\t-768.000000000000\t2015-03-23T00:05:50.089Z\t78\tnull\t0.000000443235\t2015-03-23T00:05:48.820Z\n" +
                "88\tnull\t0.000019480709\t2015-03-23T00:05:50.803Z\tNaN\tnull\tNaN\t\n" +
                "209\tRIIYMHO\t0.036849732511\t2015-03-23T00:05:50.822Z\tNaN\tnull\tNaN\t\n" +
                "221\tnull\t-308.000000000000\t2015-03-23T00:05:54.120Z\tNaN\tnull\tNaN\t\n" +
                "278\tnull\t100.157985687256\t2015-03-23T00:06:06.542Z\t81\tnull\t-116.000000000000\t2015-03-23T00:06:06.359Z\n" +
                "219\tSBEOUOJSHRU\t0.007685951889\t2015-03-23T00:06:06.860Z\tNaN\tnull\tNaN\t\n" +
                "61\tnull\t-1024.000000000000\t2015-03-23T00:06:08.793Z\tNaN\tnull\tNaN\t\n" +
                "180\tnull\t0.223312780261\t2015-03-23T00:06:10.929Z\tNaN\tnull\tNaN\t\n" +
                "94\tnull\t0.000006785318\t2015-03-23T00:06:15.648Z\t84\tnull\t0.000000001883\t2015-03-23T00:06:13.867Z\n" +
                "240\tPNHTDCEBYWXB\t0.000000007430\t2015-03-23T00:06:23.214Z\t85\tnull\t0.000000042277\t2015-03-23T00:06:21.010Z\n" +
                "242\tnull\t-557.027343750000\t2015-03-23T00:06:24.968Z\tNaN\tnull\tNaN\t\n" +
                "68\tnull\t0.000000001771\t2015-03-23T00:06:26.396Z\tNaN\tnull\tNaN\t\n" +
                "64\tnull\t-512.546875000000\t2015-03-23T00:06:26.865Z\tNaN\tnull\tNaN\t\n" +
                "41\tnull\t0.000001296927\t2015-03-23T00:06:34.398Z\t87\tnull\t0.000199647140\t2015-03-23T00:06:28.009Z\n" +
                "32\tSIMYDXUUSKCX\t0.091201189905\t2015-03-23T00:06:42.748Z\tNaN\tnull\tNaN\t\n" +
                "40\tnull\t0.352436579764\t2015-03-23T00:06:44.446Z\tNaN\tnull\tNaN\t\n" +
                "279\tNEJRMDIKDISGQ\t-186.680175781250\t2015-03-23T00:06:50.222Z\t91\tnull\t58.010286331177\t2015-03-23T00:06:46.575Z\n" +
                "277\tIGENFELWWRSLBM\t-305.000000000000\t2015-03-23T00:07:16.365Z\t96\tnull\t0.269549429417\t2015-03-23T00:07:14.324Z\n" +
                "69\tnull\t-64.000000000000\t2015-03-23T00:07:32.794Z\t102\tnull\t21.335580825806\t2015-03-23T00:07:28.848Z\n" +
                "206\tnull\t0.000001646644\t2015-03-23T00:07:40.787Z\t103\tnull\t89.232910156250\t2015-03-23T00:07:38.365Z\n" +
                "2\tVQ\t0.000000002305\t2015-03-23T00:07:43.087Z\tNaN\tnull\tNaN\t\n" +
                "110\tnull\t882.590759277344\t2015-03-23T00:07:44.604Z\tNaN\tnull\tNaN\t\n" +
                "81\tGK\t0.000279274151\t2015-03-23T00:07:54.905Z\tNaN\tnull\tNaN\t\n" +
                "208\tnull\t0.000000447563\t2015-03-23T00:08:04.779Z\t107\tnull\t0.006601167843\t2015-03-23T00:07:58.974Z\n" +
                "266\tnull\t0.000002058839\t2015-03-23T00:08:14.465Z\tNaN\tnull\tNaN\t\n" +
                "270\tUDVIK\t0.000000391720\t2015-03-23T00:08:17.846Z\t109\tnull\t135.283298492432\t2015-03-23T00:08:15.320Z\n" +
                "24\tSVTN\t123.810607910156\t2015-03-23T00:08:20.910Z\tNaN\tnull\tNaN\t\n" +
                "15\tnull\t0.000000004841\t2015-03-23T00:08:23.335Z\tNaN\tnull\tNaN\t\n";
        assertThat(expected, "trades t asof join quotes q where tag = null", true);
        assertThat(expected, "trades t asof join quotes q where null = tag", true);
    }

    @Test
    public void testFilterOnAsOfJoinedColumn() throws Exception {
        final String expected = "quoteId\ttag1\tamount\ttimestamp\tquoteId\ttag\trate\ttimestamp\n" +
                "279\tREIJ\t0.001741334971\t2015-03-23T00:02:02.280Z\t29\tBROMNXKUIZ\t0.000002532035\t2015-03-23T00:01:59.534Z\n";
        assertThat(expected, "trades t asof join quotes q where tag = 'BROMNXKUIZ'", true);
        assertThat(expected, "trades t asof join quotes q where 'BROMNXKUIZ' = tag", true);
    }

    @Test
    public void testFilterOnOuterJoinedColumns() throws Exception {
        final String expected = "quoteId\ttag1\tamount\ttimestamp\tquoteId\ttag\trate\ttimestamp\n" +
                "41\tnull\t0.000001296927\t2015-03-23T00:06:34.398Z\t41\tNRXGZSXUX\t-429.100708007813\t2015-03-23T00:03:08.533Z\n" +
                "287\tnull\t-732.302734375000\t2015-03-23T00:07:54.893Z\t287\tNRXGZSXUX\t-1015.128906250000\t2015-03-23T00:22:07.562Z\n";

        assertThat(expected, "trades t outer join quotes q on t.quoteId = q.quoteId where tag = 'NRXGZSXUX'", true);
        assertThat(expected, "trades t outer join quotes q on t.quoteId = q.quoteId where 'NRXGZSXUX' = tag", true);
    }

    @Test
    public void testFilterOnPartitionedAsOfJoinedColumn() throws Exception {
        final String expected = "quoteId\ttag1\tamount\ttimestamp\tquoteId\ttag\trate\ttimestamp\n" +
                "41\tnull\t0.000001296927\t2015-03-23T00:06:34.398Z\t41\tNRXGZSXUX\t-429.100708007813\t2015-03-23T00:03:08.533Z\n";
        assertThat(expected, "trades t asof join quotes q on t.quoteId = q.quoteId where tag = 'NRXGZSXUX'", true);
        assertThat(expected, "trades t asof join quotes q on t.quoteId = q.quoteId where 'NRXGZSXUX' = tag", true);
    }

    @Test
    public void testInvalidLambdaContext() throws Exception {
        try {
            expectFailure("trades where quoteId in (`quotes where tag ~ 'UM'`)");
        } catch (ParserException e) {
            Assert.assertEquals(25, QueryError.getPosition());
        }
    }

    @Test
    public void testOuterJoinLeftNull() throws Exception {
        final String expected = "quoteId\ttag1\tamount\ttimestamp\tquoteId\ttag\trate\ttimestamp\n" +
                "167\tnull\t0.000269699180\t2015-03-23T00:00:00.041Z\t167\tnull\t31.994654655457\t2015-03-23T00:12:48.728Z\n" +
                "185\tnull\t320.000000000000\t2015-03-23T00:00:03.905Z\t185\tQG\t170.000000000000\t2015-03-23T00:14:14.584Z\n" +
                "208\tnull\t0.000283450820\t2015-03-23T00:00:09.777Z\t208\tTYLHXVPGHPSF\t262.875000000000\t2015-03-23T00:16:13.032Z\n" +
                "253\tnull\t0.000006260114\t2015-03-23T00:00:14.319Z\t253\tnull\t4.114017963409\t2015-03-23T00:19:34.153Z\n" +
                "280\tnull\t0.000001666620\t2015-03-23T00:00:21.324Z\t280\tKOJEDNKRCGKSQD\t0.058121575043\t2015-03-23T00:21:47.324Z\n" +
                "250\tnull\t391.318801879883\t2015-03-23T00:00:38.605Z\t250\tnull\t277.000000000000\t2015-03-23T00:19:23.568Z\n" +
                "189\tnull\t638.125000000000\t2015-03-23T00:00:54.293Z\t189\tnull\t-738.307403564453\t2015-03-23T00:14:34.726Z\n" +
                "79\tnull\t0.000000003843\t2015-03-23T00:01:00.686Z\t79\tnull\t0.000004861412\t2015-03-23T00:05:57.121Z\n" +
                "160\tnull\t-804.000000000000\t2015-03-23T00:01:22.281Z\t160\tnull\t0.000001974584\t2015-03-23T00:12:13.375Z\n" +
                "146\tnull\t114.411033630371\t2015-03-23T00:01:35.805Z\t146\tBTKV\t-896.000000000000\t2015-03-23T00:11:05.282Z\n" +
                "275\tnull\t0.000000135166\t2015-03-23T00:01:46.298Z\t275\tnull\t-170.362548828125\t2015-03-23T00:21:22.488Z\n" +
                "38\tnull\t0.000006406346\t2015-03-23T00:01:51.405Z\t38\tnull\t88.440750122070\t2015-03-23T00:02:48.897Z\n" +
                "174\tnull\t0.003814977244\t2015-03-23T00:01:54.120Z\t174\tKWMDNZZBBUKOJS\t0.000000818166\t2015-03-23T00:13:28.095Z\n" +
                "32\tnull\t0.000000000000\t2015-03-23T00:02:33.565Z\t32\tFORGFIEVM\t0.000000013271\t2015-03-23T00:02:19.770Z\n" +
                "25\tnull\t0.000000521190\t2015-03-23T00:02:38.743Z\t25\tnull\t-1024.000000000000\t2015-03-23T00:01:41.578Z\n" +
                "247\tnull\t1.480640053749\t2015-03-23T00:03:05.144Z\t247\tRIIYMHO\t0.000000020133\t2015-03-23T00:19:12.159Z\n" +
                "11\tnull\t0.000000837886\t2015-03-23T00:03:12.568Z\t11\tnull\t109.844512939453\t2015-03-23T00:00:52.378Z\n" +
                "242\tnull\t-448.687500000000\t2015-03-23T00:03:18.692Z\t242\tnull\t102.382783889771\t2015-03-23T00:18:42.107Z\n" +
                "125\tnull\t384.000000000000\t2015-03-23T00:03:24.655Z\t125\tnull\t-128.000000000000\t2015-03-23T00:09:41.134Z\n" +
                "38\tnull\t0.000000042007\t2015-03-23T00:03:56.155Z\t38\tnull\t88.440750122070\t2015-03-23T00:02:48.897Z\n" +
                "61\tnull\t0.002688131004\t2015-03-23T00:03:59.991Z\t61\tBKFIJZZYNPP\t97.859756469727\t2015-03-23T00:04:34.967Z\n" +
                "135\tnull\t11.436011791229\t2015-03-23T00:04:15.031Z\t135\tnull\t0.000000000000\t2015-03-23T00:10:24.620Z\n" +
                "67\tnull\t0.000000017864\t2015-03-23T00:04:25.683Z\t67\tGSVCLLERSM\t214.940444946289\t2015-03-23T00:05:05.637Z\n" +
                "5\tnull\t0.001699611719\t2015-03-23T00:04:32.638Z\t5\tNEJRMDIKDISGQ\t0.000017155876\t2015-03-23T00:00:21.617Z\n" +
                "197\tnull\t-639.687500000000\t2015-03-23T00:04:34.193Z\t197\tnull\t618.812500000000\t2015-03-23T00:15:11.692Z\n" +
                "295\tnull\t3.262981295586\t2015-03-23T00:04:39.143Z\t295\tnull\t0.000079564978\t2015-03-23T00:22:43.107Z\n" +
                "11\tnull\t1.727517306805\t2015-03-23T00:04:52.639Z\t11\tnull\t109.844512939453\t2015-03-23T00:00:52.378Z\n" +
                "47\tnull\t673.375000000000\t2015-03-23T00:04:56.064Z\t47\tRHHMGZJYYFLS\t0.005148356780\t2015-03-23T00:03:29.150Z\n" +
                "97\tnull\t0.000031514332\t2015-03-23T00:05:04.467Z\t97\tnull\t0.000000008103\t2015-03-23T00:07:18.538Z\n" +
                "52\tnull\t-982.250000000000\t2015-03-23T00:05:06.948Z\t52\tX\t0.000004397260\t2015-03-23T00:03:52.621Z\n" +
                "101\tnull\t0.085098911077\t2015-03-23T00:05:07.455Z\t101\tnull\t-524.334808349609\t2015-03-23T00:07:26.447Z\n" +
                "204\tnull\t-864.000000000000\t2015-03-23T00:05:14.905Z\t204\tJYED\t0.000000613136\t2015-03-23T00:15:52.030Z\n" +
                "157\tnull\t0.000000038206\t2015-03-23T00:05:24.607Z\t157\tnull\t208.000000000000\t2015-03-23T00:11:56.847Z\n" +
                "27\tnull\t0.128199812025\t2015-03-23T00:05:30.349Z\t27\tnull\t-881.312500000000\t2015-03-23T00:01:50.570Z\n" +
                "294\tnull\t32.320184707642\t2015-03-23T00:05:35.452Z\t294\tnull\t0.291217848659\t2015-03-23T00:22:36.844Z\n" +
                "175\tnull\t0.000000096432\t2015-03-23T00:05:37.064Z\t175\t\t0.000000018550\t2015-03-23T00:13:32.392Z\n" +
                "140\tnull\t-384.000000000000\t2015-03-23T00:05:40.901Z\t140\tRYRFBVTMHGOOZ\t266.057495117188\t2015-03-23T00:10:42.021Z\n" +
                "118\tnull\t-768.000000000000\t2015-03-23T00:05:50.089Z\t118\tnull\t0.000000728812\t2015-03-23T00:09:06.419Z\n" +
                "88\tnull\t0.000019480709\t2015-03-23T00:05:50.803Z\t88\tONWE\t0.000125047416\t2015-03-23T00:06:36.408Z\n" +
                "221\tnull\t-308.000000000000\t2015-03-23T00:05:54.120Z\t221\tLKKHTWNWIF\t-336.000000000000\t2015-03-23T00:17:03.370Z\n" +
                "214\tnull\t-1024.000000000000\t2015-03-23T00:06:00.720Z\t214\tnull\t0.003775255405\t2015-03-23T00:16:42.900Z\n" +
                "278\tnull\t100.157985687256\t2015-03-23T00:06:06.542Z\t278\tRZUPVQFULM\t0.000013578421\t2015-03-23T00:21:40.650Z\n" +
                "61\tnull\t-1024.000000000000\t2015-03-23T00:06:08.793Z\t61\tBKFIJZZYNPP\t97.859756469727\t2015-03-23T00:04:34.967Z\n" +
                "180\tnull\t0.223312780261\t2015-03-23T00:06:10.929Z\t180\tnull\t0.000098954313\t2015-03-23T00:13:53.085Z\n" +
                "94\tnull\t0.000006785318\t2015-03-23T00:06:15.648Z\t94\tNKG\t0.000032506398\t2015-03-23T00:07:06.427Z\n" +
                "242\tnull\t-557.027343750000\t2015-03-23T00:06:24.968Z\t242\tnull\t102.382783889771\t2015-03-23T00:18:42.107Z\n" +
                "68\tnull\t0.000000001771\t2015-03-23T00:06:26.396Z\t68\tnull\t0.374871194363\t2015-03-23T00:05:07.144Z\n" +
                "64\tnull\t-512.546875000000\t2015-03-23T00:06:26.865Z\t64\tQOLYXWCKYLSUWD\t-965.125000000000\t2015-03-23T00:04:55.914Z\n" +
                "41\tnull\t0.000001296927\t2015-03-23T00:06:34.398Z\t41\tNRXGZSXUX\t-429.100708007813\t2015-03-23T00:03:08.533Z\n" +
                "40\tnull\t0.352436579764\t2015-03-23T00:06:44.446Z\t40\tBJFRPX\t3.300331056118\t2015-03-23T00:03:01.677Z\n" +
                "127\tnull\t0.000750448322\t2015-03-23T00:07:07.023Z\t127\tM\t-3.500000000000\t2015-03-23T00:09:51.474Z\n" +
                "284\tnull\t0.000000056363\t2015-03-23T00:07:24.963Z\t284\tnull\t646.667968750000\t2015-03-23T00:21:59.500Z\n" +
                "69\tnull\t-64.000000000000\t2015-03-23T00:07:32.794Z\t69\tnull\t0.000000001976\t2015-03-23T00:05:12.110Z\n" +
                "206\tnull\t0.000001646644\t2015-03-23T00:07:40.787Z\t206\tnull\t0.001282604615\t2015-03-23T00:15:59.559Z\n" +
                "110\tnull\t882.590759277344\t2015-03-23T00:07:44.604Z\t110\tnull\t-563.960998535156\t2015-03-23T00:08:21.310Z\n" +
                "208\tnull\t0.000000045985\t2015-03-23T00:07:48.156Z\t208\tTYLHXVPGHPSF\t262.875000000000\t2015-03-23T00:16:13.032Z\n" +
                "287\tnull\t-732.302734375000\t2015-03-23T00:07:54.893Z\t287\tNRXGZSXUX\t-1015.128906250000\t2015-03-23T00:22:07.562Z\n" +
                "208\tnull\t0.000000447563\t2015-03-23T00:08:04.779Z\t208\tTYLHXVPGHPSF\t262.875000000000\t2015-03-23T00:16:13.032Z\n" +
                "190\tnull\t0.000000005669\t2015-03-23T00:08:10.452Z\t190\tnull\t200.281250000000\t2015-03-23T00:14:39.938Z\n" +
                "266\tnull\t0.000002058839\t2015-03-23T00:08:14.465Z\t266\tGIUQZHEISQ\t0.006878267042\t2015-03-23T00:20:31.754Z\n" +
                "15\tnull\t0.000000004841\t2015-03-23T00:08:23.335Z\t15\tMYICCXZOUIC\t-504.062500000000\t2015-03-23T00:01:09.396Z\n";
        assertThat(expected, "trades t outer join quotes q on t.quoteId = q.quoteId where tag1 = null", true);
        assertThat(expected, "trades t outer join quotes q on t.quoteId = q.quoteId where null = tag1", true);
        assertPlan("+ 0[ cross ] t (filter: 'A' = tag1)\n" +
                        "+ 1[ outer ] q ON q.quoteId = t.quoteId\n" +
                        "\n",
                "trades t outer join quotes q on t.quoteId = q.quoteId where 'A' = tag1"
        );
    }

    @Test
    public void testOuterJoinRightNull() throws Exception {
        final String expected = "quoteId\ttag1\tamount\ttimestamp\tquoteId\ttag\trate\ttimestamp\n" +
                "167\tnull\t0.000269699180\t2015-03-23T00:00:00.041Z\t167\tnull\t31.994654655457\t2015-03-23T00:12:48.728Z\n" +
                "253\tnull\t0.000006260114\t2015-03-23T00:00:14.319Z\t253\tnull\t4.114017963409\t2015-03-23T00:19:34.153Z\n" +
                "62\tUUQIDLVBVKH\t-655.093750000000\t2015-03-23T00:00:26.965Z\t62\tnull\t0.000000003979\t2015-03-23T00:04:44.544Z\n" +
                "159\tOJIGFINKGQVZ\t52.300781250000\t2015-03-23T00:00:31.747Z\t159\tnull\t-168.824218750000\t2015-03-23T00:12:04.161Z\n" +
                "250\tnull\t391.318801879883\t2015-03-23T00:00:38.605Z\t250\tnull\t277.000000000000\t2015-03-23T00:19:23.568Z\n" +
                "189\tnull\t638.125000000000\t2015-03-23T00:00:54.293Z\t189\tnull\t-738.307403564453\t2015-03-23T00:14:34.726Z\n" +
                "206\tDDBHEVGXY\t0.000000983339\t2015-03-23T00:00:54.651Z\t206\tnull\t0.001282604615\t2015-03-23T00:15:59.559Z\n" +
                "79\tnull\t0.000000003843\t2015-03-23T00:01:00.686Z\t79\tnull\t0.000004861412\t2015-03-23T00:05:57.121Z\n" +
                "98\tI\t0.095442861319\t2015-03-23T00:01:08.481Z\t98\tnull\t-615.625000000000\t2015-03-23T00:07:18.987Z\n" +
                "160\tnull\t-804.000000000000\t2015-03-23T00:01:22.281Z\t160\tnull\t0.000001974584\t2015-03-23T00:12:13.375Z\n" +
                "275\tnull\t0.000000135166\t2015-03-23T00:01:46.298Z\t275\tnull\t-170.362548828125\t2015-03-23T00:21:22.488Z\n" +
                "38\tnull\t0.000006406346\t2015-03-23T00:01:51.405Z\t38\tnull\t88.440750122070\t2015-03-23T00:02:48.897Z\n" +
                "279\tREIJ\t0.001741334971\t2015-03-23T00:02:02.280Z\t279\tnull\t0.000059029620\t2015-03-23T00:21:44.691Z\n" +
                "83\tTYLHXVPGHPSF\t-768.000000000000\t2015-03-23T00:02:10.007Z\t83\tnull\t173.568359375000\t2015-03-23T00:06:12.793Z\n" +
                "17\tIOVIKJS\t1.924843549728\t2015-03-23T00:02:24.538Z\t17\tnull\t0.000010729342\t2015-03-23T00:01:16.171Z\n" +
                "25\tnull\t0.000000521190\t2015-03-23T00:02:38.743Z\t25\tnull\t-1024.000000000000\t2015-03-23T00:01:41.578Z\n" +
                "233\tV\t0.000000017818\t2015-03-23T00:02:43.116Z\t233\tnull\t274.816894531250\t2015-03-23T00:18:08.052Z\n" +
                "112\tUVV\t979.898437500000\t2015-03-23T00:02:49.249Z\t112\tnull\t274.169425964355\t2015-03-23T00:08:30.476Z\n" +
                "295\tJYED\t881.624389648438\t2015-03-23T00:02:58.778Z\t295\tnull\t0.000079564978\t2015-03-23T00:22:43.107Z\n" +
                "188\tMDJTHMHZNV\t128.000000000000\t2015-03-23T00:03:11.761Z\t188\tnull\t0.001495893754\t2015-03-23T00:14:30.135Z\n" +
                "11\tnull\t0.000000837886\t2015-03-23T00:03:12.568Z\t11\tnull\t109.844512939453\t2015-03-23T00:00:52.378Z\n" +
                "242\tnull\t-448.687500000000\t2015-03-23T00:03:18.692Z\t242\tnull\t102.382783889771\t2015-03-23T00:18:42.107Z\n" +
                "125\tnull\t384.000000000000\t2015-03-23T00:03:24.655Z\t125\tnull\t-128.000000000000\t2015-03-23T00:09:41.134Z\n" +
                "272\tOSBOS\t911.400665283203\t2015-03-23T00:03:34.502Z\t272\tnull\t152.160076141357\t2015-03-23T00:21:08.131Z\n" +
                "13\tUMEUKVZI\t976.000000000000\t2015-03-23T00:03:48.769Z\t13\tnull\t367.500000000000\t2015-03-23T00:00:53.663Z\n" +
                "38\tnull\t0.000000042007\t2015-03-23T00:03:56.155Z\t38\tnull\t88.440750122070\t2015-03-23T00:02:48.897Z\n" +
                "217\tDWWLEVMLKCJBEVL\t-871.023925781250\t2015-03-23T00:04:05.708Z\t217\tnull\t192.425888061523\t2015-03-23T00:16:57.051Z\n" +
                "135\tnull\t11.436011791229\t2015-03-23T00:04:15.031Z\t135\tnull\t0.000000000000\t2015-03-23T00:10:24.620Z\n" +
                "200\tCJOU\t0.000000498588\t2015-03-23T00:04:30.988Z\t200\tnull\t2.903524160385\t2015-03-23T00:15:28.505Z\n" +
                "197\tnull\t-639.687500000000\t2015-03-23T00:04:34.193Z\t197\tnull\t618.812500000000\t2015-03-23T00:15:11.692Z\n" +
                "295\tnull\t3.262981295586\t2015-03-23T00:04:39.143Z\t295\tnull\t0.000079564978\t2015-03-23T00:22:43.107Z\n" +
                "11\tnull\t1.727517306805\t2015-03-23T00:04:52.639Z\t11\tnull\t109.844512939453\t2015-03-23T00:00:52.378Z\n" +
                "97\tnull\t0.000031514332\t2015-03-23T00:05:04.467Z\t97\tnull\t0.000000008103\t2015-03-23T00:07:18.538Z\n" +
                "101\tnull\t0.085098911077\t2015-03-23T00:05:07.455Z\t101\tnull\t-524.334808349609\t2015-03-23T00:07:26.447Z\n" +
                "245\tUVDRHF\t152.633743286133\t2015-03-23T00:05:12.773Z\t245\tnull\t0.000001090484\t2015-03-23T00:19:07.628Z\n" +
                "157\tnull\t0.000000038206\t2015-03-23T00:05:24.607Z\t157\tnull\t208.000000000000\t2015-03-23T00:11:56.847Z\n" +
                "27\tnull\t0.128199812025\t2015-03-23T00:05:30.349Z\t27\tnull\t-881.312500000000\t2015-03-23T00:01:50.570Z\n" +
                "294\tnull\t32.320184707642\t2015-03-23T00:05:35.452Z\t294\tnull\t0.291217848659\t2015-03-23T00:22:36.844Z\n" +
                "118\tnull\t-768.000000000000\t2015-03-23T00:05:50.089Z\t118\tnull\t0.000000728812\t2015-03-23T00:09:06.419Z\n" +
                "209\tRIIYMHO\t0.036849732511\t2015-03-23T00:05:50.822Z\t209\tnull\t0.286881171167\t2015-03-23T00:16:14.200Z\n" +
                "214\tnull\t-1024.000000000000\t2015-03-23T00:06:00.720Z\t214\tnull\t0.003775255405\t2015-03-23T00:16:42.900Z\n" +
                "219\tSBEOUOJSHRU\t0.007685951889\t2015-03-23T00:06:06.860Z\t219\tnull\t261.593750000000\t2015-03-23T00:17:01.024Z\n" +
                "180\tnull\t0.223312780261\t2015-03-23T00:06:10.929Z\t180\tnull\t0.000098954313\t2015-03-23T00:13:53.085Z\n" +
                "242\tnull\t-557.027343750000\t2015-03-23T00:06:24.968Z\t242\tnull\t102.382783889771\t2015-03-23T00:18:42.107Z\n" +
                "68\tnull\t0.000000001771\t2015-03-23T00:06:26.396Z\t68\tnull\t0.374871194363\t2015-03-23T00:05:07.144Z\n" +
                "101\t\t0.003191990079\t2015-03-23T00:06:38.252Z\t101\tnull\t-524.334808349609\t2015-03-23T00:07:26.447Z\n" +
                "279\tNEJRMDIKDISGQ\t-186.680175781250\t2015-03-23T00:06:50.222Z\t279\tnull\t0.000059029620\t2015-03-23T00:21:44.691Z\n" +
                "238\tSED\t0.000000019996\t2015-03-23T00:06:58.896Z\t238\tnull\t0.001861780300\t2015-03-23T00:18:23.957Z\n" +
                "284\tnull\t0.000000056363\t2015-03-23T00:07:24.963Z\t284\tnull\t646.667968750000\t2015-03-23T00:21:59.500Z\n" +
                "69\tnull\t-64.000000000000\t2015-03-23T00:07:32.794Z\t69\tnull\t0.000000001976\t2015-03-23T00:05:12.110Z\n" +
                "206\tnull\t0.000001646644\t2015-03-23T00:07:40.787Z\t206\tnull\t0.001282604615\t2015-03-23T00:15:59.559Z\n" +
                "2\tVQ\t0.000000002305\t2015-03-23T00:07:43.087Z\t2\tnull\t0.000000004051\t2015-03-23T00:00:11.420Z\n" +
                "110\tnull\t882.590759277344\t2015-03-23T00:07:44.604Z\t110\tnull\t-563.960998535156\t2015-03-23T00:08:21.310Z\n" +
                "81\tGK\t0.000279274151\t2015-03-23T00:07:54.905Z\t81\tnull\t-116.000000000000\t2015-03-23T00:06:06.359Z\n" +
                "190\tnull\t0.000000005669\t2015-03-23T00:08:10.452Z\t190\tnull\t200.281250000000\t2015-03-23T00:14:39.938Z\n" +
                "24\tSVTN\t123.810607910156\t2015-03-23T00:08:20.910Z\t24\tnull\t-421.400390625000\t2015-03-23T00:01:41.440Z\n" +
                "216\tZUFEV\t0.000000036476\t2015-03-23T00:08:23.335Z\t216\tnull\t512.000000000000\t2015-03-23T00:16:53.939Z\n";

        assertThat(expected, "trades t outer join quotes q on t.quoteId = q.quoteId where tag = null", true);
        assertThat(expected, "trades t outer join quotes q on t.quoteId = q.quoteId where null = tag", true);
    }

    @Test
    public void testRegexFilterOnAsOfJoinedColumn() throws Exception {
        final String expected = "quoteId\ttag1\tamount\ttimestamp\tquoteId\ttag\trate\ttimestamp\n" +
                "279\tREIJ\t0.001741334971\t2015-03-23T00:02:02.280Z\t29\tBROMNXKUIZ\t0.000002532035\t2015-03-23T00:01:59.534Z\n";
        assertThat(expected, "trades t asof join quotes q where tag ~ 'BROMNXKUIZ'", true);
    }

    @Test
    public void testWhereColumnAlias() throws Exception {
        assertThat("quoteId\ttag1\tamount\ttimestamp\tquoteId\ttag\trate\ttimestamp\n" +
                        "50\tDNZNL\t-258.093750000000\t2015-03-23T00:04:46.445Z\t50\tVUYGMBMKSCPWLZK\t0.000000011817\t2015-03-23T00:03:43.678Z\n",
                "trades t asof join quotes q on q.quoteId = t.quoteId where q.tag ~ 'B' and t.quoteId = 50", true);
    }

    @Test
    public void testWhereColumnAlias2() throws Exception {
        assertThat("quoteId\ttag1\tamount\ttimestamp\tquoteId\ttag\trate\ttimestamp\n" +
                        "50\tDNZNL\t-258.093750000000\t2015-03-23T00:04:46.445Z\t50\tVUYGMBMKSCPWLZK\t0.000000011817\t2015-03-23T00:03:43.678Z\n",
                "trades t asof join quotes q on q.quoteId = t.quoteId where q.tag ~ 'B' and q.quoteId = 50", true);
    }

}
