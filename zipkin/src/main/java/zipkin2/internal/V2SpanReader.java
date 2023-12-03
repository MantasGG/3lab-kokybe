/*
 * Copyright 2015-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin2.internal;

import java.io.IOException;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.internal.JsonCodec.JsonReader;
import zipkin2.internal.JsonCodec.JsonReaderAdapter;

public final class V2SpanReader implements JsonReaderAdapter<Span> {
  Span.Builder builder;

  @Override
  public Span fromJson(JsonReader reader) throws IOException {
    initializeBuilder();
    reader.beginObject();

    while (reader.hasNext()) {
      processNextField(reader);
    }

    reader.endObject();
    return builder.build();
  }

  private void initializeBuilder() {
    if (builder == null) {
      builder = Span.newBuilder();
    } else {
      builder.clear();
    }
  }

  private void processNextField(JsonReader reader) throws IOException {
    String nextName = reader.nextName();

    switch (nextName) {
      case "traceId":
      case "id":
        setStringField(nextName, reader.nextString());
        break;
      case "parentId":
        setStringField("parentId", reader.nextString());
        break;
      case "kind":
        builder.kind(Span.Kind.valueOf(reader.nextString()));
        break;
      case "name":
        setStringField("name", reader.nextString());
        break;
      case "timestamp":
      case "duration":
        setLongField(nextName, reader.nextLong());
        break;
      case "localEndpoint":
      case "remoteEndpoint":
        setEndpointField(nextName, reader);
        break;
      case "annotations":
        processAnnotations(reader);
        break;
      case "tags":
        processTags(reader);
        break;
      case "debug":
        setBooleanField("debug", reader.nextBoolean());
        break;
      case "shared":
        setBooleanField("shared", reader.nextBoolean());
        break;
      default:
        reader.skipValue();
    }
  }

  private void setStringField(String fieldName, String value) {
    switch (fieldName) {
      case "traceId":
        builder.traceId(value);
        break;
      case "id":
        builder.id(value);
        break;
      case "parentId":
        builder.parentId(value);
        break;
      case "name":
        builder.name(value);
        break;
    }
  }

  private void setLongField(String fieldName, long value) {
    switch (fieldName) {
      case "timestamp":
        builder.timestamp(value);
        break;
      case "duration":
        builder.duration(value);
        break;
    }
  }

  private void setEndpointField(String fieldName, JsonReader reader) throws IOException {
    switch (fieldName) {
      case "localEndpoint":
        builder.localEndpoint(ENDPOINT_READER.fromJson(reader));
        break;
      case "remoteEndpoint":
        builder.remoteEndpoint(ENDPOINT_READER.fromJson(reader));
        break;
    }
  }

  private void setBooleanField(String fieldName, boolean value) {
    switch (fieldName) {
      case "debug":
        if (value) builder.debug(true);
        break;
      case "shared":
        if (value) builder.shared(true);
        break;
    }
  }

  private void processAnnotations(JsonReader reader) throws IOException {
    reader.beginArray();
    while (reader.hasNext()) {
      processAnnotation(reader);
    }
    reader.endArray();
  }

  private void processAnnotation(JsonReader reader) throws IOException {
    reader.beginObject();
    Long timestamp = null;
    String value = null;

    while (reader.hasNext()) {
      String nextName = reader.nextName();
      switch (nextName) {
        case "timestamp":
          timestamp = reader.nextLong();
          break;
        case "value":
          value = reader.nextString();
          break;
        default:
          reader.skipValue();
      }
    }

    if (timestamp == null || value == null) {
      throw new IllegalArgumentException("Incomplete annotation at " + reader.getPath());
    }

    reader.endObject();
    builder.addAnnotation(timestamp, value);
  }

  private void processTags(JsonReader reader) throws IOException {
    reader.beginObject();
    while (reader.hasNext()) {
      processTag(reader);
    }
    reader.endObject();
  }

  private void processTag(JsonReader reader) throws IOException {
    String key = reader.nextName();
    if (reader.peekNull()) {
      throw new IllegalArgumentException("No value at " + reader.getPath());
    }
    builder.putTag(key, reader.nextString());
  }

  @Override public String toString() {
    return "Span";
  }

  static final JsonReaderAdapter<Endpoint> ENDPOINT_READER = new JsonReaderAdapter<Endpoint>() {
    @Override public Endpoint fromJson(JsonReader reader) throws IOException {
      Endpoint.Builder result = Endpoint.newBuilder();
      reader.beginObject();
      boolean readField = false;
      while (reader.hasNext()) {
        String nextName = reader.nextName();
        if (reader.peekNull()) {
          reader.skipValue();
          continue;
        }
        if (nextName.equals("serviceName")) {
          result.serviceName(reader.nextString());
          readField = true;
        } else if (nextName.equals("ipv4") || nextName.equals("ipv6")) {
          result.parseIp(reader.nextString());
          readField = true;
        } else if (nextName.equals("port")) {
          result.port(reader.nextInt());
          readField = true;
        } else {
          reader.skipValue();
        }
      }
      reader.endObject();
      return readField ? result.build() : null;
    }

    @Override public String toString() {
      return "Endpoint";
    }
  };
}
