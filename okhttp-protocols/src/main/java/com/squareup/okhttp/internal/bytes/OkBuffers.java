/*
 * Copyright (C) 2014 Square, Inc.
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
package com.squareup.okhttp.internal.bytes;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class OkBuffers {
  private OkBuffers() {
  }

  /** Returns a sink that writes to {@code out}. */
  public static Sink sink(final OutputStream out) {
    return new Sink() {
      @Override public void write(OkBuffer source, long byteCount, Deadline deadline)
          throws IOException {
        source.checkByteCount(byteCount);
        while (byteCount > 0) {
          deadline.throwIfReached();
          Segment head = source.head;
          int toCopy = (int) Math.min(byteCount, head.limit - head.pos);
          out.write(head.data, head.pos, toCopy);

          head.pos += toCopy;
          byteCount -= toCopy;
          source.byteCount -= toCopy;

          if (head.pos == head.limit) {
            source.head = head.pop();
            SegmentPool.INSTANCE.recycle(head);
          }
        }
      }

      @Override public void flush(Deadline deadline) throws IOException {
        out.flush();
      }

      @Override public void close(Deadline deadline) throws IOException {
        out.close();
      }

      @Override public String toString() {
        return "sink(" + out + ")";
      }
    };
  }

  /** Returns a source that reads from {@code in}. */
  public static Source source(final InputStream in) {
    return new Source() {
      @Override public long read(
          OkBuffer sink, long byteCount, Deadline deadline) throws IOException {
        if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        deadline.throwIfReached();
        Segment tail = sink.writableSegment();
        int maxToCopy = (int) Math.min(byteCount, Segment.SIZE - tail.limit);
        int bytesRead = in.read(tail.data, tail.limit, maxToCopy);
        if (bytesRead == -1) return -1;
        tail.limit += bytesRead;
        sink.byteCount += bytesRead;
        return bytesRead;
      }

      @Override public void close(Deadline deadline) throws IOException {
        in.close();
      }

      @Override public String toString() {
        return "source(" + in + ")";
      }
    };
  }
}
