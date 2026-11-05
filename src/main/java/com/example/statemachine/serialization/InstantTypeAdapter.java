package com.example.statemachine.serialization;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.time.Instant;

/** Stores timestamps as ISO-8601 strings without reflecting into JDK classes. */
public class InstantTypeAdapter extends TypeAdapter<Instant> {
    @Override
    public void write(JsonWriter writer, Instant value) throws IOException {
        writer.value(value.toString());
    }

    @Override
    public Instant read(JsonReader reader) throws IOException {
        return Instant.parse(reader.nextString());
    }
}
