/*
 * Copyright (c) 2014-2015. Vlad Ilyushchenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nfsdb.lang;

import com.nfsdb.Journal;
import com.nfsdb.JournalWriter;
import com.nfsdb.exceptions.JournalConfigurationException;
import com.nfsdb.exceptions.JournalRuntimeException;
import com.nfsdb.exp.RecordSourcePrinter;
import com.nfsdb.exp.StringSink;
import com.nfsdb.factory.configuration.JournalConfigurationBuilder;
import com.nfsdb.lang.cst.StatefulJournalSource;
import com.nfsdb.lang.cst.impl.join.InnerSkipJoin;
import com.nfsdb.lang.cst.impl.join.NestedLoopLeftOuterJoin;
import com.nfsdb.lang.cst.impl.jsrc.JournalSourceImpl;
import com.nfsdb.lang.cst.impl.jsrc.StatefulJournalSourceImpl;
import com.nfsdb.lang.cst.impl.ksrc.SingleKeySource;
import com.nfsdb.lang.cst.impl.psrc.JournalPartitionSource;
import com.nfsdb.lang.cst.impl.qry.Record;
import com.nfsdb.lang.cst.impl.qry.RecordSource;
import com.nfsdb.lang.cst.impl.ref.StringRef;
import com.nfsdb.lang.cst.impl.ref.SymbolXTabVariableSource;
import com.nfsdb.lang.cst.impl.rsrc.AllRowSource;
import com.nfsdb.lang.cst.impl.rsrc.KvIndexRowSource;
import com.nfsdb.lang.cst.impl.rsrc.KvIndexTopRowSource;
import com.nfsdb.lang.cst.impl.rsrc.SkipSymbolRowSource;
import com.nfsdb.model.Album;
import com.nfsdb.model.Band;
import com.nfsdb.test.tools.JournalTestFactory;
import com.nfsdb.utils.Files;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class JoinSymbolOnSymbolTest {

    @Rule
    public final JournalTestFactory factory;
    private final StringSink sink = new StringSink();
    private final RecordSourcePrinter out = new RecordSourcePrinter(sink);
    private JournalWriter<Band> bw;
    private JournalWriter<Album> aw;

    public JoinSymbolOnSymbolTest() {
        try {
            this.factory = new JournalTestFactory(
                    new JournalConfigurationBuilder() {{
                        $(Band.class)
                                .$sym("name").index()
                                .$sym("type")
                                .$bin("image")
                                .$ts()
                        ;

                        $(Album.class)
                                .$sym("band").index()
                                .$sym("name").index()
                                .$ts("releaseDate");

                    }}.build(Files.makeTempDir())
            );
        } catch (JournalConfigurationException e) {
            throw new JournalRuntimeException(e);
        }

//        out = new JournalEntryPrinter(new FlexBufferSink(new FileOutputStream(FileDescriptor.out).getChannel()), false);
    }

    @Before
    public void setUp() throws Exception {
        bw = factory.writer(Band.class);
        aw = factory.writer(Album.class);
    }

    @Test
    public void testInnerOneToManyHead() throws Exception {

        final String expected = "1970-01-01T00:00:00.000Z\tband1\thttp://band1.com\trock\t\tband1\talbum BZ\tpop\t1970-01-01T00:00:00.000Z\n" +
                "1970-01-01T00:00:00.000Z\tband1\thttp://band1.com\trock\t\tband1\talbum X\tpop\t1970-01-01T00:00:00.000Z\n" +
                "1970-01-01T00:00:00.000Z\tband3\thttp://band3.com\tjazz\t\tband3\talbum Y\tmetal\t1970-01-01T00:00:00.000Z\n";

        bw.append(new Band().setName("band1").setType("rock").setUrl("http://band1.com"));
        bw.append(new Band().setName("band2").setType("hiphop").setUrl("http://band2.com"));
        bw.append(new Band().setName("band3").setType("jazz").setUrl("http://band3.com"));

        bw.commit();

        aw.append(new Album().setName("album X").setBand("band1").setGenre("pop"));
        aw.append(new Album().setName("album BZ").setBand("band1").setGenre("rock"));
        aw.append(new Album().setName("album BZ").setBand("band1").setGenre("pop"));
        aw.append(new Album().setName("album Y").setBand("band3").setGenre("metal"));

        aw.commit();

        StringRef band = new StringRef("band");
        StringRef name = new StringRef("name");

        // from band join album head by name
        // **inner join
        // **join first head after
        StatefulJournalSourceImpl master;

        out.print(
                new InnerSkipJoin(
                        new NestedLoopLeftOuterJoin(
                                master = new StatefulJournalSourceImpl(
                                        new JournalSourceImpl(new JournalPartitionSource(bw, false), new AllRowSource())
                                )
                                ,
                                new JournalSourceImpl(new JournalPartitionSource(aw, false), new SkipSymbolRowSource(
                                        new KvIndexRowSource(
                                                band
                                                , new SingleKeySource(new SymbolXTabVariableSource(master, "name", "band"))
                                        )
                                        , name
                                ))
                        )
                )
        );
        Assert.assertEquals(expected, sink.toString());
    }

    @Test
    public void testInnerOneToManyHeadFilter() throws Exception {

        final String expected = "1970-01-01T00:00:00.000Z\tband1\thttp://band1.com\trock\t\tband1\talbum BZ\tpop\t1970-01-01T00:00:00.000Z\n" +
                "1970-01-01T00:00:00.000Z\tband1\thttp://band1.com\trock\t\tband1\talbum BZ\trock\t1970-01-01T00:00:00.000Z\n" +
                "1970-01-01T00:00:00.000Z\tband1\thttp://band1.com\trock\t\tband1\talbum X\tpop\t1970-01-01T00:00:00.000Z\n" +
                "1970-01-01T00:00:00.000Z\tband3\thttp://band3.com\tjazz\t\tband3\talbum Y\tmetal\t1970-01-01T00:00:00.000Z\n";

        bw.append(new Band().setName("band1").setType("rock").setUrl("http://band1.com"));
        bw.append(new Band().setName("band2").setType("hiphop").setUrl("http://band2.com"));
        bw.append(new Band().setName("band3").setType("jazz").setUrl("http://band3.com"));

        bw.commit();

        aw.append(new Album().setName("album X").setBand("band1").setGenre("pop"));
        aw.append(new Album().setName("album BZ").setBand("band1").setGenre("rock"));
        aw.append(new Album().setName("album BZ").setBand("band1").setGenre("pop"));
        aw.append(new Album().setName("album Y").setBand("band3").setGenre("metal"));

        aw.commit();

        StringRef band = new StringRef("band");

        // from band join album head by name
        StatefulJournalSource master;

        out.print(
                new InnerSkipJoin(
                        new NestedLoopLeftOuterJoin(
                                master = new StatefulJournalSourceImpl(
                                        new JournalSourceImpl(new JournalPartitionSource(bw, false), new AllRowSource())
                                )
                                ,
                                new JournalSourceImpl(new JournalPartitionSource(aw, false), new KvIndexRowSource(
                                        band
                                        , new SingleKeySource(new SymbolXTabVariableSource(master, "name", "band"))
                                ))
                        )
                )
        );
        Assert.assertEquals(expected, sink.toString());
    }

    @Test
    public void testOuterOneToMany() throws Exception {

        final String expected = "1970-01-01T00:00:00.000Z\tband1\thttp://band1.com\trock\t\tband1\talbum BZ\trock\t1970-01-01T00:00:00.000Z\n" +
                "1970-01-01T00:00:00.000Z\tband1\thttp://band1.com\trock\t\tband1\talbum X\tpop\t1970-01-01T00:00:00.000Z\n" +
                "1970-01-01T00:00:00.000Z\tband2\thttp://band2.com\thiphop\t\tnull\tnull\t\t1970-01-01T00:00:00.000Z\n" +
                "1970-01-01T00:00:00.000Z\tband3\thttp://band3.com\tjazz\t\tband3\talbum Y\tmetal\t1970-01-01T00:00:00.000Z\n";

        bw.append(new Band().setName("band1").setType("rock").setUrl("http://band1.com"));
        bw.append(new Band().setName("band2").setType("hiphop").setUrl("http://band2.com"));
        bw.append(new Band().setName("band3").setType("jazz").setUrl("http://band3.com"));

        bw.commit();

        aw.append(new Album().setName("album X").setBand("band1").setGenre("pop"));
        aw.append(new Album().setName("album BZ").setBand("band1").setGenre("rock"));
        aw.append(new Album().setName("album Y").setBand("band3").setGenre("metal"));

        aw.commit();

        // from band outer join album
        // this is data-driven one to many
        out.print(buildSource(bw, aw));
        Assert.assertEquals(expected, sink.toString());
    }

    /**
     * Band and Album are joined on symbol. We want to do outer join so that
     * it shows Bands without Albums. Also Albums can be versioned on album name symbol.
     * Here we select Bands and latest versions of their Albums.
     *
     * @throws Exception
     */
    @Test
    public void testOuterOneToManyHead() throws Exception {

        final String expected = "1970-01-01T00:00:00.000Z\tband1\thttp://band1.com\trock\t\tband1\talbum BZ\tpop\t1970-01-01T00:00:00.000Z\n" +
                "1970-01-01T00:00:00.000Z\tband1\thttp://band1.com\trock\t\tband1\talbum X\tpop\t1970-01-01T00:00:00.000Z\n" +
                "1970-01-01T00:00:00.000Z\tband2\thttp://band2.com\thiphop\t\tnull\tnull\t\t1970-01-01T00:00:00.000Z\n" +
                "1970-01-01T00:00:00.000Z\tband3\thttp://band3.com\tjazz\t\tband3\talbum Y\tmetal\t1970-01-01T00:00:00.000Z\n";

        bw.append(new Band().setName("band1").setType("rock").setUrl("http://band1.com"));
        bw.append(new Band().setName("band2").setType("hiphop").setUrl("http://band2.com"));
        bw.append(new Band().setName("band3").setType("jazz").setUrl("http://band3.com"));

        bw.commit();

        aw.append(new Album().setName("album X").setBand("band1").setGenre("pop"));
        aw.append(new Album().setName("album BZ").setBand("band1").setGenre("rock"));
        aw.append(new Album().setName("album BZ").setBand("band1").setGenre("pop"));
        aw.append(new Album().setName("album Y").setBand("band3").setGenre("metal"));

        aw.commit();


        // from band outer join album head by name
        // **head by name is applied after join
        StringRef band = new StringRef("band");
        StringRef name = new StringRef("name");

        StatefulJournalSource master;

        out.print(
                new NestedLoopLeftOuterJoin(
                        master = new StatefulJournalSourceImpl(
                                new JournalSourceImpl(new JournalPartitionSource(bw, false), new AllRowSource())
                        )
                        ,
                        new JournalSourceImpl(new JournalPartitionSource(aw, false), new SkipSymbolRowSource(
                                new KvIndexRowSource(
                                        band
                                        , new SingleKeySource(new SymbolXTabVariableSource(master, "name", "band"))
                                )
                                , name
                        ))
                )
        );
        Assert.assertEquals(expected, sink.toString());
    }

    @Test
    public void testOuterOneToOne() throws Exception {

        final String expected = "1970-01-01T00:00:00.000Z\tband1\thttp://band1.com\trock\t\tband1\talbum X\tpop\t1970-01-01T00:00:00.000Z\n" +
                "1970-01-01T00:00:00.000Z\tband2\thttp://band2.com\thiphop\t\tnull\tnull\t\t1970-01-01T00:00:00.000Z\n" +
                "1970-01-01T00:00:00.000Z\tband3\thttp://band3.com\tjazz\t\tband3\talbum Y\tmetal\t1970-01-01T00:00:00.000Z\n";


        bw.append(new Band().setName("band1").setType("rock").setUrl("http://band1.com"));
        bw.append(new Band().setName("band2").setType("hiphop").setUrl("http://band2.com"));
        bw.append(new Band().setName("band3").setType("jazz").setUrl("http://band3.com"));

        bw.commit();

        aw.append(new Album().setName("album X").setBand("band1").setGenre("pop"));
        aw.append(new Album().setName("album Y").setBand("band3").setGenre("metal"));

        aw.commit();

        // from band outer join album
        out.print(buildSource(bw, aw));
        Assert.assertEquals(expected, sink.toString());
    }

    @Test
    public void testOuterOneToOneHead() throws Exception {

        final String expected = "band1\talbum X\tpop\t1970-01-01T00:00:00.000Z\t1970-01-01T00:00:00.000Z\tband1\thttp://new.band1.com\tjazz\t\n" +
                "band1\talbum BZ\trock\t1970-01-01T00:00:00.000Z\t1970-01-01T00:00:00.000Z\tband1\thttp://new.band1.com\tjazz\t\n" +
                "band3\talbum Y\tmetal\t1970-01-01T00:00:00.000Z\t1970-01-01T00:00:00.000Z\tband3\thttp://band3.com\tjazz\t\n";

        bw.append(new Band().setName("band1").setType("rock").setUrl("http://band1.com"));
        bw.append(new Band().setName("band2").setType("hiphop").setUrl("http://band2.com"));
        bw.append(new Band().setName("band3").setType("jazz").setUrl("http://band3.com"));
        bw.append(new Band().setName("band1").setType("jazz").setUrl("http://new.band1.com"));

        bw.commit();

        aw.append(new Album().setName("album X").setBand("band1").setGenre("pop"));
        aw.append(new Album().setName("album BZ").setBand("band1").setGenre("rock"));
        aw.append(new Album().setName("album Y").setBand("band3").setGenre("metal"));

        aw.commit();

        // from album join band head by name
        StringRef name = new StringRef("name");
        StatefulJournalSource master;

        out.print(new NestedLoopLeftOuterJoin(
                master = new StatefulJournalSourceImpl(
                        new JournalSourceImpl(new JournalPartitionSource(aw, false), new AllRowSource())
                )
                ,
                new JournalSourceImpl(new JournalPartitionSource(bw, false), new KvIndexTopRowSource(
                        name
                        , new SingleKeySource(new SymbolXTabVariableSource(master, "band", "name"))
                        , null
                ))
        ));

        Assert.assertEquals(expected, sink.toString());
    }

    private RecordSource<? extends Record> buildSource(Journal<Band> bw, Journal<Album> aw) {
        StringRef band = new StringRef("band");
        StatefulJournalSource master;
        return new NestedLoopLeftOuterJoin(
                master = new StatefulJournalSourceImpl(
                        new JournalSourceImpl(new JournalPartitionSource(bw, false), new AllRowSource())
                )
                ,
                new JournalSourceImpl(new JournalPartitionSource(aw, false), new KvIndexRowSource(band
                        , new SingleKeySource(new SymbolXTabVariableSource(master, "name", "band"))
                ))
        );
    }
}