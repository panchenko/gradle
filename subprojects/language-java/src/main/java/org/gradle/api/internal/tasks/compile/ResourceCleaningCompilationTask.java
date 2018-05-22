/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks.compile;

import org.gradle.internal.concurrent.Stoppable;

import javax.annotation.processing.Processor;
import javax.tools.JavaCompiler;
import java.util.Locale;

public class ResourceCleaningCompilationTask implements JavaCompiler.CompilationTask {

    private final JavaCompiler.CompilationTask delegate;
    private final Stoppable stoppable;

    public ResourceCleaningCompilationTask(JavaCompiler.CompilationTask delegate, Stoppable stoppable) {
        this.delegate = delegate;
        this.stoppable = stoppable;
    }

    @Override
    public void setProcessors(Iterable<? extends Processor> processors) {
        delegate.setProcessors(processors);
    }

    @Override
    public void setLocale(Locale locale) {
        delegate.setLocale(locale);
    }

    @Override
    public Boolean call() {
        try {
            return delegate.call();
        } finally {
            cleanupZipCache();
            stoppable.stop();
        }
    }

    private void cleanupZipCache() {
        try {
            Class<?> zipFileIndexCache = Class.forName("com.sun.tools.javac.file.ZipFileIndexCache");
            Object instance = zipFileIndexCache.getMethod("getSharedInstance").invoke(null);
            zipFileIndexCache.getMethod("clearCache").invoke(instance);
        } catch (Throwable e) {
            // Not an OpenJDK-compatible compiler or signature changed
        }
    }
}
