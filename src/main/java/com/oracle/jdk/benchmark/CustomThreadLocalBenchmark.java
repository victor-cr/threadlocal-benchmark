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

import org.openjdk.jmh.annotations.GenerateMicroBenchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

public class CustomThreadLocalBenchmark {

    @GenerateMicroBenchmark
    public Object customColdGet(ColdCustomContext context) {
        return context.threadLocal.get();
    }

    @GenerateMicroBenchmark
    public Object customWarmGet(WarmCustomContext context) {
        return context.threadLocal.get();
    }

    @GenerateMicroBenchmark
    public void customColdSet(ColdCustomContext context) {
        context.threadLocal.set(new Object());
    }

    @GenerateMicroBenchmark
    public void customWarmSet(WarmCustomContext context) {
        context.threadLocal.set(new Object());
    }

    @GenerateMicroBenchmark
    public void customNoRemove(ColdCustomContext context) {
        context.threadLocal.remove();
    }

    @GenerateMicroBenchmark
    public void customRemove(WarmCustomContext context) {
        context.threadLocal.remove();
    }

    @GenerateMicroBenchmark
    public Object originalColdGet(ColdOriginalContext context) {
        return context.threadLocal.get();
    }

    @GenerateMicroBenchmark
    public Object originalWarmGet(WarmOriginalContext context) {
        return context.threadLocal.get();
    }

    @GenerateMicroBenchmark
    public void originalColdSet(ColdOriginalContext context) {
        context.threadLocal.set(new Object());
    }

    @GenerateMicroBenchmark
    public void originalWarmSet(WarmOriginalContext context) {
        context.threadLocal.set(new Object());
    }

    @GenerateMicroBenchmark
    public void originalNoRemove(ColdOriginalContext context) {
        context.threadLocal.remove();
    }

    @GenerateMicroBenchmark
    public void originalRemove(WarmOriginalContext context) {
        context.threadLocal.remove();
    }

    public abstract static class CustomContext {
        protected final MyThreadLocal<Object> threadLocal = new MyThreadLocal<Object>(){
            @Override
            protected Object initialValue() {
                return new Object();
            }
        };
    }

    @State(Scope.Benchmark)
    public static class ColdCustomContext extends CustomContext {
        @Setup(Level.Invocation)
        public void setup() {
            threadLocal.remove();
        }
    }

    @State(Scope.Benchmark)
    public static class WarmCustomContext extends CustomContext {
        @Setup(Level.Invocation)
        public void setup() {
            threadLocal.get();
        }
    }

    public abstract static class OriginalContext {
        protected final ThreadLocal<Object> threadLocal = new ThreadLocal<Object>(){
            @Override
            protected Object initialValue() {
                return new Object();
            }
        };
    }

    @State(Scope.Benchmark)
    public static class ColdOriginalContext extends OriginalContext {
        @Setup(Level.Invocation)
        public void setup() {
            threadLocal.remove();
        }
    }

    @State(Scope.Benchmark)
    public static class WarmOriginalContext extends OriginalContext {
        @Setup(Level.Invocation)
        public void setup() {
            threadLocal.get();
        }
    }
}
