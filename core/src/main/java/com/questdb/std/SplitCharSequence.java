/*******************************************************************************
 * ___                  _   ____  ____
 * / _ \ _   _  ___  ___| |_|  _ \| __ )
 * | | | | | | |/ _ \/ __| __| | | |  _ \
 * | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 * \__\_\\__,_|\___||___/\__|____/|____/
 * <p>
 * Copyright (C) 2014-2016 Appsicle
 * <p>
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

package com.questdb.std;

public class SplitCharSequence extends AbstractCharSequence {
    private CharSequence lhs;
    private CharSequence rhs;
    private int rl;
    private int split;

    public void init(CharSequence lhs, CharSequence rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
        this.rl = rhs == null ? 0 : rhs.length();
        this.split = lhs == null ? 0 : lhs.length();
    }

    @Override
    public int length() {
        return split + rl;
    }

    @Override
    public char charAt(int index) {
        if (index < split) {
            return lhs.charAt(index);
        } else {
            return rhs.charAt(index - split);
        }
    }
}
