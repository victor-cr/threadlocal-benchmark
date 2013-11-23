/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.jdk.benchmark;

import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.GenerateMicroBenchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class CustomThreadLocalBenchmark {
    private static final Object INSTANCE = new Object();

    private final MyThreadLocal<Object> custom = new MyThreadLocal<Object>(){
        @Override
        protected Object initialValue() {
            return INSTANCE;
        }
    };

    private final ThreadLocal<Object> original = new ThreadLocal<Object>(){
        @Override
        protected Object initialValue() {
            return INSTANCE;
        }
    };

    @GenerateMicroBenchmark
    public Object customGet() {
        return custom.get();
    }

    @GenerateMicroBenchmark
    public Object customRemoveGet() {
        custom.remove();
        return custom.get();
    }

    @GenerateMicroBenchmark
    public Object customGetGet() {
        custom.get();
        return custom.get();
    }

    @GenerateMicroBenchmark
    public void customSet() {
        custom.set(INSTANCE);
    }

    @GenerateMicroBenchmark
    public void customRemoveSet() {
        custom.remove();
        custom.set(INSTANCE);
    }

    @GenerateMicroBenchmark
    public void customGetSet() {
        custom.get();
        custom.set(INSTANCE);
    }

    @GenerateMicroBenchmark
    public void customRemove() {
        custom.remove();
    }

    @GenerateMicroBenchmark
    public void customGetRemove() {
        custom.get();
        custom.remove();
    }

    @GenerateMicroBenchmark
    public void customRemoveRemove() {
        custom.remove();
        custom.remove();
    }

    @GenerateMicroBenchmark
    public Object originalGet() {
        return original.get();
    }

    @GenerateMicroBenchmark
    public Object originalRemoveGet() {
        original.remove();
        return original.get();
    }

    @GenerateMicroBenchmark
    public Object originalGetGet() {
        original.get();
        return original.get();
    }

    @GenerateMicroBenchmark
    public void originalSet() {
        original.set(INSTANCE);
    }

    @GenerateMicroBenchmark
    public void originalRemoveSet() {
        original.remove();
        original.set(INSTANCE);
    }

    @GenerateMicroBenchmark
    public void originalGetSet() {
        original.get();
        original.set(INSTANCE);
    }

    @GenerateMicroBenchmark
    public void originalRemove() {
        original.remove();
    }

    @GenerateMicroBenchmark
    public void originalGetRemove() {
        original.get();
        original.remove();
    }

    @GenerateMicroBenchmark
    public void originalRemoveRemove() {
        original.remove();
        original.remove();
    }
}
