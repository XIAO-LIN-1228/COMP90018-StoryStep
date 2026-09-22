package com.comp90018.storystep.data.local;

import com.comp90018.storystep.domain.theme.*;
import com.google.gson.*;
import java.util.*;

/** Persist content, then validate it through domain constructors when loading it back. */
public final class ThemeJson {
    private static final Gson GSON = new Gson();
    private ThemeJson() { }
    public static String encode(ThemeDefinition theme) { return GSON.toJson(theme); }
    public static ThemeDefinition decode(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        List<ThemeStop> stops = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("stops")) {
            JsonObject s = element.getAsJsonObject();
            List<String> options = new ArrayList<>();
            for (JsonElement option : s.getAsJsonArray("options")) { options.add(option.getAsString()); }
            stops.add(new ThemeStop(s.get("id").getAsString(), s.get("name").getAsString(),
                    s.get("story").getAsString(), s.get("sourceUrl").getAsString(),
                    s.get("latitude").getAsDouble(), s.get("longitude").getAsDouble(),
                    s.get("question").getAsString(), options, s.get("correctAnswer").getAsInt()));
        }
        return new ThemeDefinition(root.get("id").getAsString(), root.get("title").getAsString(),
                root.get("description").getAsString(), root.get("version").getAsInt(), stops);
    }
}
