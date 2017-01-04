package de.mineformers.cursesync.util;

import com.google.common.reflect.TypeToken;
import com.google.gson.*;

import java.io.File;
import java.lang.reflect.Type;

public class FileAdapter implements JsonSerializer<File>, JsonDeserializer<File>
{
    public static final Type TYPE = new TypeToken<File>()
    {
    }.getType();

    @Override
    public File deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException
    {
        return new File(json.getAsString());
    }

    @Override
    public JsonElement serialize(File src, Type typeOfSrc, JsonSerializationContext context)
    {
        return new JsonPrimitive(src.getAbsolutePath());
    }
}
