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
import org.openjdk.jmh.logic.BlackHole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class CustomThreadLocalBenchmark {
    private static final int SIZE = 1 << 10;
    private static final Object[] INSTANCES = new Object[SIZE];
    private static final AtomicInteger I = new AtomicInteger(0);

    static {
        for (int i = 0; i < SIZE; i++) {
            INSTANCES[i] = i % 10 == 0 ? null : new Object();
        }
    }

    private final MyThreadLocal<Object> custom = new MyThreadLocal<Object>() {
        @Override
        protected Object initialValue() {
            return instance();
        }
    };
    private final ThreadLocal<Object> original = new ThreadLocal<Object>() {
        @Override
        protected Object initialValue() {
            return instance();
        }
    };

    private static Object instance() {
        int i = I.accumulateAndGet(1, (left, right) -> (left + right) & (SIZE - 1));

        return INSTANCES[i];
    }

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
    public Object customGetGet(BlackHole hole) {
        hole.consume(custom.get());
        return custom.get();
    }

    @GenerateMicroBenchmark
    public void customSet() {
        custom.set(instance());
    }

    @GenerateMicroBenchmark
    public void customRemoveSet() {
        custom.remove();
        custom.set(instance());
    }

    @GenerateMicroBenchmark
    public void customGetSet(BlackHole hole) {
        hole.consume(custom.get());
        custom.set(instance());
    }

    @GenerateMicroBenchmark
    public void customRemove() {
        custom.remove();
    }

    @GenerateMicroBenchmark
    public void customGetRemove(BlackHole hole) {
        hole.consume(custom.get());
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
    public Object originalGetGet(BlackHole hole) {
        hole.consume(original.get());
        return original.get();
    }

    @GenerateMicroBenchmark
    public void originalSet() {
        original.set(instance());
    }

    @GenerateMicroBenchmark
    public void originalRemoveSet() {
        original.remove();
        original.set(instance());
    }

    @GenerateMicroBenchmark
    public void originalGetSet(BlackHole hole) {
        hole.consume(original.get());
        original.set(instance());
    }

    @GenerateMicroBenchmark
    public void originalRemove() {
        original.remove();
    }

    @GenerateMicroBenchmark
    public void originalGetRemove(BlackHole hole) {
        hole.consume(original.get());
        original.remove();
    }

    @GenerateMicroBenchmark
    public void originalRemoveRemove() {
        original.remove();
        original.remove();
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(".*" + CustomThreadLocalBenchmark.class.getSimpleName() + ".*")
                .warmupIterations(15)
                .measurementIterations(15)
                .threads(5)
                .forks(5)
                .build();

        new Runner(opt).run();
    }
}
