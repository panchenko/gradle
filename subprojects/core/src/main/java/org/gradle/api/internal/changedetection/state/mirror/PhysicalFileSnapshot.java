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

package org.gradle.api.internal.changedetection.state.mirror;

import org.gradle.internal.hash.HashCode;

public class PhysicalFileSnapshot implements PhysicalSnapshot {
    private final HashCode hash;
    private final long timestamp;
    private final String name;

    public PhysicalFileSnapshot(String name, long lastModified, HashCode contentMd5) {
        this.name = name;
        this.timestamp = lastModified;
        this.hash = contentMd5;
    }

    @Override
    public PhysicalSnapshot find(String[] segments, int offset) {
        if (segments.length == offset) {
            return this;
        }
        return null;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public PhysicalSnapshot add(String[] segments, int offset, PhysicalSnapshot snapshot) {
        if (segments.length == offset) {
            return this;
        }
        throw new UnsupportedOperationException("Cannot add children of file");
    }

    public HashCode getHash() {
        return hash;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
