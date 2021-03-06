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

package com.questdb.ql.parser;

import com.questdb.PartitionBy;
import com.questdb.ex.NumericException;
import com.questdb.ex.ParserException;
import com.questdb.factory.configuration.GenericIntBuilder;
import com.questdb.factory.configuration.GenericStringBuilder;
import com.questdb.factory.configuration.GenericSymbolBuilder;
import com.questdb.factory.configuration.JournalStructure;
import com.questdb.misc.Chars;
import com.questdb.misc.Misc;
import com.questdb.misc.Numbers;
import com.questdb.ql.model.*;
import com.questdb.std.CharSequenceHashSet;
import com.questdb.std.CharSequenceIntHashMap;
import com.questdb.std.ObjList;
import com.questdb.std.ObjectPool;
import com.questdb.store.ColumnType;

public final class QueryParser {

    public static final int MAX_ORDER_BY_COLUMNS = 1560;
    private static final CharSequenceHashSet journalAliasStop = new CharSequenceHashSet();
    private static final CharSequenceHashSet columnAliasStop = new CharSequenceHashSet();
    private static final CharSequenceHashSet groupByStopSet = new CharSequenceHashSet();
    private static final CharSequenceIntHashMap joinStartSet = new CharSequenceIntHashMap();
    private final ObjectPool<ExprNode> exprNodePool = new ObjectPool<>(ExprNode.FACTORY, 128);
    private final Lexer lexer = new Lexer();
    private final ExprParser exprParser = new ExprParser(lexer, exprNodePool);
    private final ExprAstBuilder astBuilder = new ExprAstBuilder();
    private final ObjectPool<QueryModel> queryModelPool = new ObjectPool<>(QueryModel.FACTORY, 8);
    private final ObjectPool<QueryColumn> queryColumnPool = new ObjectPool<>(QueryColumn.FACTORY, 64);
    private final ObjectPool<AnalyticColumn> analyticColumnPool = new ObjectPool<>(AnalyticColumn.FACTORY, 8);

    public Statement parse(CharSequence query) throws ParserException {
        queryModelPool.clear();
        queryColumnPool.clear();
        exprNodePool.clear();
        analyticColumnPool.clear();
        return parseInternal(query);
    }

    private ParserException err(String msg) {
        return QueryError.$(lexer.position(), msg);
    }

    private ExprNode expectExpr() throws ParserException {
        ExprNode n = expr();
        if (n == null) {
            throw QueryError.$(lexer.position(), "Expression expected");
        }
        return n;
    }

    private void expectTok(CharSequence tok, CharSequence expected) throws ParserException {
        if (tok == null || !Chars.equals(tok, expected)) {
            throw QueryError.position(lexer.position()).$('\'').$(expected).$("' expected").$();
        }
    }

    private ExprNode expr() throws ParserException {
        astBuilder.reset();
        exprParser.parseExpr(astBuilder);
        return astBuilder.poll();
    }

    private boolean isFieldTerm(CharSequence tok) {
        return Chars.equals(tok, ')') || Chars.equals(tok, ',');
    }

    private ExprNode literal() {
        CharSequence tok = lexer.optionTok();
        if (tok == null) {
            return null;
        }
        return exprNodePool.next().of(ExprNode.LITERAL, Chars.stripQuotes(tok.toString()), 0, lexer.position());
    }

    private ExprNode makeJoinAlias(int index) {
        StringBuilder b = Misc.getThreadLocalBuilder();
        ExprNode node = exprNodePool.next();
        node.token = b.append("_xQdbA").append(index).toString();
        node.type = ExprNode.LITERAL;
        return node;
    }

    private ExprNode makeModelAlias(String modelAlias, ExprNode node) {
        StringBuilder b = Misc.getThreadLocalBuilder();
        ExprNode exprNode = exprNodePool.next();
        b.append(modelAlias).append('.').append(node.token);
        exprNode.token = b.toString();
        exprNode.type = ExprNode.LITERAL;
        exprNode.position = node.position;
        return exprNode;
    }

    private ExprNode makeOperation(String token, ExprNode lhs, ExprNode rhs) {
        ExprNode expr = exprNodePool.next();
        expr.token = token;
        expr.type = ExprNode.OPERATION;
        expr.position = 0;
        expr.paramCount = 2;
        expr.lhs = lhs;
        expr.rhs = rhs;
        return expr;
    }

    private CharSequence notTermTok() throws ParserException {
        CharSequence tok = tok();
        if (isFieldTerm(tok)) {
            throw err("Invalid column definition");
        }
        return tok;
    }

    private Statement parseCreateJournal() throws ParserException {
        JournalStructure structure = new JournalStructure(tok().toString());
        parseJournalFields(structure);
        CharSequence tok = lexer.optionTok();
        if (tok != null) {
            expectTok(tok, "partition");
            expectTok(tok(), "by");
            structure.partitionBy(PartitionBy.fromString(tok()));

            tok = lexer.optionTok();
        }

        if (tok != null) {
            throw QueryError.$(lexer.position(), "Unexpected token");
        }
        return new Statement(Statement.CREATE_JOURNAL, structure);
    }

    private Statement parseCreateStatement() throws ParserException {
        CharSequence tok = tok();
        if (Chars.equals(tok, "journal")) {
            return parseCreateJournal();
        }

        throw err("journal expected");
    }

    private CharSequence parseIntDefinition(GenericIntBuilder genericIntBuilder) throws ParserException {
        CharSequence tok = tok();

        if (isFieldTerm(tok)) {
            return tok;
        }

        expectTok(tok, "index");
        genericIntBuilder.index();

        if (isFieldTerm(tok = tok())) {
            return tok;
        }

        expectTok(tok, "buckets");

        try {
            genericIntBuilder.buckets(Numbers.parseInt(tok()));
        } catch (NumericException e) {
            throw err("expected number of buckets (int)");
        }

        return null;
    }

    Statement parseInternal(CharSequence query) throws ParserException {
        lexer.setContent(query);
        CharSequence tok = tok();
        if (Chars.equals(tok, "create")) {
            return parseCreateStatement();
        }

        lexer.unparse();
        return new Statement(Statement.QUERY_JOURNAL, parseQuery(false));
    }

    private QueryModel parseJoin(CharSequence tok, int joinType) throws ParserException {
        QueryModel joinModel = queryModelPool.next();
        joinModel.setJoinType(joinType);

        if (!Chars.equals(tok, "join")) {
            expectTok(tok(), "join");
        }

        tok = tok();

        if (Chars.equals(tok, '(')) {
            joinModel.setNestedModel(parseQuery(true));
            expectTok(tok(), ")");
        } else {
            lexer.unparse();
            joinModel.setJournalName(expr());
        }

        tok = lexer.optionTok();

        if (tok != null && !journalAliasStop.contains(tok)) {
            lexer.unparse();
            joinModel.setAlias(expr());
        } else {
            lexer.unparse();
        }

        tok = lexer.optionTok();

        if (joinType == QueryModel.JOIN_CROSS && tok != null && Chars.equals(tok, "on")) {
            throw QueryError.$(lexer.position(), "Cross joins cannot have join clauses");
        }

        switch (joinType) {
            case QueryModel.JOIN_ASOF:
                if (tok == null || !Chars.equals("on", tok)) {
                    lexer.unparse();
                    break;
                }
                // intentional fall through
            case QueryModel.JOIN_INNER:
            case QueryModel.JOIN_OUTER:
                expectTok(tok, "on");
                astBuilder.reset();
                exprParser.parseExpr(astBuilder);
                ExprNode expr;
                switch (astBuilder.size()) {
                    case 0:
                        throw QueryError.$(lexer.position(), "Expression expected");
                    case 1:
                        expr = astBuilder.poll();
                        if (expr.type == ExprNode.LITERAL) {
                            do {
                                joinModel.addJoinColumn(expr);
                            } while ((expr = astBuilder.poll()) != null);
                        } else {
                            joinModel.setJoinCriteria(expr);
                        }
                        break;
                    default:
                        while ((expr = astBuilder.poll()) != null) {
                            if (expr.type != ExprNode.LITERAL) {
                                throw QueryError.$(lexer.position(), "Column name expected");
                            }
                            joinModel.addJoinColumn(expr);
                        }
                        break;
                }
                break;
            default:
                lexer.unparse();
        }

        return joinModel;
    }

    private void parseJournalFields(JournalStructure struct) throws ParserException {
        if (!Chars.equals(tok(), '(')) {
            throw err("( expected");
        }

        while (true) {
            String name = notTermTok().toString();
            CharSequence tok = null;

            switch (ColumnType.columnTypeOf(notTermTok())) {
                case ColumnType.BYTE:
                    struct.$byte(name);
                    break;
                case ColumnType.INT:
                    tok = parseIntDefinition(struct.$int(name));
                    break;
                case ColumnType.DOUBLE:
                    struct.$double(name);
                    break;
                case ColumnType.BOOLEAN:
                    struct.$bool(name);
                    break;
                case ColumnType.FLOAT:
                    struct.$float(name);
                    break;
                case ColumnType.LONG:
                    struct.$long(name);
                    break;
                case ColumnType.SHORT:
                    struct.$short(name);
                    break;
                case ColumnType.STRING:
                    tok = parseStrDefinition(struct.$str(name));
                    break;
                case ColumnType.SYMBOL:
                    tok = parseSymDefinition(struct.$sym(name));
                    break;
                case ColumnType.BINARY:
                    struct.$bin(name);
                    break;
                case ColumnType.DATE:
                    struct.$date(name);
                    break;
                case ColumnType.TIMESTAMP:
                    struct.$ts(name);
                    break;
                default:
                    throw err("Unsupported type");
            }

            if (tok == null) {
                tok = tok();
            }

            if (Chars.equals(tok, ')')) {
                break;
            }

            if (!Chars.equals(tok, ',')) {
                throw err(", or ) expected");
            }
        }
    }

    private void parseLatestBy(QueryModel model) throws ParserException {
        expectTok(tok(), "by");
        model.setLatestBy(expr());
    }

    private QueryModel parseQuery(boolean subQuery) throws ParserException {

        CharSequence tok;
        QueryModel model = queryModelPool.next();

        tok = tok();

        // [select]
        if (tok != null && Chars.equals(tok, "select")) {
            parseSelectColumns(model);
            tok = tok();
        }

        // expect "(" in case of sub-query

        if (Chars.equals(tok, '(')) {
            model.setNestedModel(parseQuery(true));

            // expect closing bracket
            expectTok(tok(), ")");

            tok = lexer.optionTok();

            // check if tok is not "where" - should be alias

            if (tok != null && !journalAliasStop.contains(tok)) {
                lexer.unparse();
                model.setAlias(literal());
                tok = lexer.optionTok();
            }

            // expect [timestamp(column)]

            tok = parseTimestamp(tok, model);
        } else {

            lexer.unparse();

            // expect (journal name)

            model.setJournalName(literal());

            tok = lexer.optionTok();

            if (tok != null && !journalAliasStop.contains(tok)) {
                lexer.unparse();
                model.setAlias(literal());
                tok = lexer.optionTok();
            }

            // expect [timestamp(column)]

            tok = parseTimestamp(tok, model);

            // expect [latest by]

            if (tok != null && Chars.equals(tok, "latest")) {
                parseLatestBy(model);
                tok = lexer.optionTok();
            }
        }

        // expect multiple [[inner | outer | cross] join]

        int joinType;
        while (tok != null && (joinType = joinStartSet.get(tok)) != -1) {
            model.addJoinModel(parseJoin(tok, joinType));
            tok = lexer.optionTok();
        }

        // expect [where]

        if (tok != null && Chars.equals(tok, "where")) {
            model.setWhereClause(expr());
            tok = lexer.optionTok();
        }

        // expect [group by]

        if (tok != null && Chars.equals(tok, "sample")) {
            expectTok(tok(), "by");
            model.setSampleBy(expectExpr());
            tok = lexer.optionTok();
        }

        // expect [order by]

        if (tok != null && Chars.equals(tok, "order")) {
            expectTok(tok(), "by");
            do {
                tok = tok();

                if (Chars.equals(tok, ')')) {
                    throw err("Expression expected");
                }

                lexer.unparse();
                ExprNode n = expectExpr();

                if (n.type != ExprNode.LITERAL) {
                    throw err("Column name expected");
                }

                tok = lexer.optionTok();

                if (tok != null && Chars.equalsIgnoreCase(tok, "desc")) {

                    model.addOrderBy(n, QueryModel.ORDER_DIRECTION_DESCENDING);
                    tok = lexer.optionTok();

                } else {

                    model.addOrderBy(n, QueryModel.ORDER_DIRECTION_ASCENDING);

                    if (tok != null && Chars.equalsIgnoreCase(tok, "asc")) {
                        tok = lexer.optionTok();
                    }
                }

                if (model.getOrderBy().size() >= MAX_ORDER_BY_COLUMNS) {
                    throw err("Too many columns");
                }

            } while (tok != null && Chars.equals(tok, ','));
        }

        // expect [limit]
        if (tok != null && Chars.equals(tok, "limit")) {
            ExprNode lo = expr();
            ExprNode hi = null;

            tok = lexer.optionTok();
            if (tok != null && Chars.equals(tok, ',')) {
                hi = expr();
                tok = lexer.optionTok();
            }
            model.setLimit(lo, hi);
        }

        if (subQuery) {
            lexer.unparse();
        } else if (tok != null) {
            throw QueryError.position(lexer.position()).$("Unexpected token: ").$(tok).$();
        }

        resolveJoinColumns(model);

        return model;
    }

    private void parseSelectColumns(QueryModel model) throws ParserException {
        CharSequence tok;
        while (true) {
            ExprNode expr = expr();
            tok = tok();

            String alias;
            if (!columnAliasStop.contains(tok)) {
                alias = tok.toString();
                tok = tok();
            } else {
                alias = null;
            }

            if (Chars.equals(tok, "over")) {
                // analytic
                expectTok(tok(), "(");

                AnalyticColumn col = analyticColumnPool.next().of(alias, expr);
                tok = tok();

                if (Chars.equals(tok, "partition")) {
                    expectTok(tok(), "by");

                    ObjList<ExprNode> partitionBy = col.getPartitionBy();

                    do {
                        partitionBy.add(expectExpr());
                        tok = tok();
                    } while (Chars.equals(tok, ','));
                }

                if (Chars.equals(tok, "order")) {
                    expectTok(tok(), "by");

                    do {
                        ExprNode e = expectExpr();
                        tok = tok();

                        if (Chars.equalsIgnoreCase(tok, "desc")) {
                            col.addOrderBy(e, QueryModel.ORDER_DIRECTION_DESCENDING);
                            tok = tok();
                        } else {
                            col.addOrderBy(e, QueryModel.ORDER_DIRECTION_ASCENDING);
                            if (Chars.equalsIgnoreCase(tok, "asc")) {
                                tok = tok();
                            }
                        }
                    } while (Chars.equals(tok, ','));
                }

                if (!Chars.equals(tok, ')')) {
                    throw err(") expected");
                }

                model.addColumn(col);

                tok = tok();
            } else {
                model.addColumn(queryColumnPool.next().of(alias, expr));
            }

            if (Chars.equals(tok, "from")) {
                break;
            }

            if (!Chars.equals(tok, ',')) {
                throw err(",|from expected");
            }
        }
    }

    private CharSequence parseStrDefinition(GenericStringBuilder builder) throws ParserException {
        CharSequence tok = tok();

        if (isFieldTerm(tok)) {
            return tok;
        }

        expectTok(tok, "index");
        builder.index();

        if (isFieldTerm(tok = tok())) {
            return tok;
        }

        expectTok(tok, "buckets");

        try {
            builder.buckets(Numbers.parseInt(tok()));
        } catch (NumericException e) {
            throw err("expected number of buckets (string)");
        }

        return null;
    }

    private CharSequence parseSymDefinition(GenericSymbolBuilder builder) throws ParserException {
        CharSequence tok = tok();

        if (isFieldTerm(tok)) {
            return tok;
        }

        expectTok(tok, "index");
        builder.index();

        return null;
    }

    private CharSequence parseTimestamp(CharSequence tok, QueryModel model) throws ParserException {
        if (tok != null && Chars.equals(tok, "timestamp")) {
            expectTok(tok(), "(");
            model.setTimestamp(expr());
            expectTok(tok(), ")");
            return lexer.optionTok();
        }
        return tok;
    }

    private void resolveJoinColumns(QueryModel model) {
        ObjList<QueryModel> joinModels = model.getJoinModels();
        if (joinModels.size() == 0) {
            return;
        }

        String modelAlias;

        if (model.getAlias() != null) {
            modelAlias = model.getAlias().token;
        } else if (model.getJournalName() != null) {
            modelAlias = model.getJournalName().token;
        } else {
            ExprNode alias = makeJoinAlias(0);
            model.setAlias(alias);
            modelAlias = alias.token;
        }

        for (int i = 1, n = joinModels.size(); i < n; i++) {
            QueryModel jm = joinModels.getQuick(i);

            ObjList<ExprNode> jc = jm.getJoinColumns();
            if (jc.size() > 0) {

                String jmAlias;

                if (jm.getAlias() != null) {
                    jmAlias = jm.getAlias().token;
                } else if (jm.getJournalName() != null) {
                    jmAlias = jm.getJournalName().token;
                } else {
                    ExprNode alias = makeJoinAlias(i);
                    jm.setAlias(alias);
                    jmAlias = alias.token;
                }

                ExprNode joinCriteria = jm.getJoinCriteria();
                for (int j = 0, m = jc.size(); j < m; j++) {
                    ExprNode node = jc.getQuick(j);
                    ExprNode eq = makeOperation("=", makeModelAlias(modelAlias, node), makeModelAlias(jmAlias, node));
                    if (joinCriteria == null) {
                        joinCriteria = eq;
                    } else {
                        joinCriteria = makeOperation("and", joinCriteria, eq);
                    }
                }
                jm.setJoinCriteria(joinCriteria);
            }
        }
    }

    private CharSequence tok() throws ParserException {
        CharSequence tok = lexer.optionTok();
        if (tok == null) {
            throw err("Unexpected end of input");
        }
        return tok;
    }

    static {
        journalAliasStop.add("where");
        journalAliasStop.add("latest");
        journalAliasStop.add("join");
        journalAliasStop.add("inner");
        journalAliasStop.add("outer");
        journalAliasStop.add("asof");
        journalAliasStop.add("cross");
        journalAliasStop.add("sample");
        journalAliasStop.add("order");
        journalAliasStop.add("on");
        journalAliasStop.add("timestamp");
        journalAliasStop.add("limit");
        journalAliasStop.add(")");
        //
        columnAliasStop.add("from");
        columnAliasStop.add(",");
        columnAliasStop.add("over");
        //
        groupByStopSet.add("order");
        groupByStopSet.add(")");
        groupByStopSet.add(",");

        joinStartSet.put("join", QueryModel.JOIN_INNER);
        joinStartSet.put("inner", QueryModel.JOIN_INNER);
        joinStartSet.put("outer", QueryModel.JOIN_OUTER);
        joinStartSet.put("cross", QueryModel.JOIN_CROSS);
        joinStartSet.put("asof", QueryModel.JOIN_ASOF);
    }
}
