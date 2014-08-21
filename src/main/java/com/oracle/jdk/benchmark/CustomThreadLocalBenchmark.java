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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class CustomThreadLocalBenchmark {
    private static final int SIZE = 1 << 10;

    @State(Scope.Thread)
    public static class Instances {
        private final Object[] arr = new Object[SIZE];
        private int index;

        @Setup
        public void setup() {
            for (int i = 0; i < SIZE; i++) {
                arr[i] = i % 10 == 0 ? null : new Object();
            }
        }

        private Object instance() {
            index = (index + 1) & (SIZE - 1);

            return arr[index];
        }
    }

    @State(Scope.Benchmark)
    public static class TLS {
        private MyThreadLocal<Object> custom;
        private ThreadLocal<Object> original;

        @Setup
        public void setup(final Instances i) {
            custom = new MyThreadLocal<Object>() {
                @Override
                protected Object initialValue() {
                    return i.instance();
                }
            };
            original = new ThreadLocal<Object>() {
                @Override
                protected Object initialValue() {
                    return i.instance();
                }
            };
        }
    }

    @Benchmark
    public void customGet(TLS tls, Instances is, Blackhole hole) {
        hole.consume(tls.custom.get());
    }

    @Benchmark
    public void customRemoveGet(TLS tls, Instances is, Blackhole hole) {
        tls.custom.remove();
        hole.consume(tls.custom.get());
    }

    @Benchmark
    public void customGetGet(TLS tls, Instances is, Blackhole hole) {
        hole.consume(tls.custom.get());
        hole.consume(tls.custom.get());
    }

    @Benchmark
    public void customSet(TLS tls, Instances is) {
        tls.custom.set(is.instance());
    }

    @Benchmark
    public void customRemoveSet(TLS tls, Instances is) {
        tls.custom.remove();
        tls.custom.set(is.instance());
    }

    @Benchmark
    public void customGetSet(TLS tls, Instances is, Blackhole hole) {
        hole.consume(tls.custom.get());
        tls.custom.set(is.instance());
    }

    @Benchmark
    public void customRemove(TLS tls, Instances is) {
        tls.custom.remove();
    }

    @Benchmark
    public void customGetRemove(TLS tls, Instances is, Blackhole hole) {
        hole.consume(tls.custom.get());
        tls.custom.remove();
    }

    @Benchmark
    public void customRemoveRemove(TLS tls, Instances is) {
        tls.custom.remove();
        tls.custom.remove();
    }

    @Benchmark
    public void originalGet(TLS tls, Instances is, Blackhole hole){
        hole.consume(tls.original.get());
    }

    @Benchmark
    public void originalRemoveGet(TLS tls, Instances is, Blackhole hole) {
        tls.original.remove();
        hole.consume(tls.original.get());
    }

    @Benchmark
    public void originalGetGet(TLS tls, Instances is, Blackhole hole) {
        hole.consume(tls.original.get());
        hole.consume(tls.original.get());
    }

    @Benchmark
    public void originalSet(TLS tls, Instances is) {
        tls.original.set(is.instance());
    }

    @Benchmark
    public void originalRemoveSet(TLS tls, Instances is) {
        tls.original.remove();
        tls.original.set(is.instance());
    }

    @Benchmark
    public void originalGetSet(TLS tls, Instances is, Blackhole hole) {
        hole.consume(tls.original.get());
        tls.original.set(is.instance());
    }

    @Benchmark
    public void originalRemove(TLS tls, Instances is) {
        tls.original.remove();
    }

    @Benchmark
    public void originalGetRemove(TLS tls, Instances is, Blackhole hole) {
        hole.consume(tls.original.get());
        tls.original.remove();
    }

    @Benchmark
    public void originalRemoveRemove(TLS tls, Instances is) {
        tls.original.remove();
        tls.original.remove();
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(".*" + CustomThreadLocalBenchmark.class.getSimpleName() + ".*")
                .warmupIterations(5)
                .measurementIterations(5)
                .threads(6)
                .forks(1)
                .build();

        new Runner(opt).run();
    }
}
