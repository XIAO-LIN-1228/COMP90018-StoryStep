package com.comp90018.storystep.data.repository;

import com.comp90018.storystep.domain.theme.*;
import java.util.*;

/** Original short summaries of the linked university sources; no downloaded map tiles. */
public final class ThemeCatalog {
    private ThemeCatalog() { }
    public static ThemeDefinition parkville() {
        return new ThemeDefinition("parkville-layers", "Parkville: buildings and botany",
                "Discover how architecture and a teaching garden tell different parts of campus history. "
                + "Three outdoor observation stops. Follow public paths and local access signs; "
                + "directions are approximate, not a walking route. Stories are bundled for offline reading.", 1,
                Arrays.asList(
                    new ThemeStop("old-arts", "Old Arts Building",
                        "Old Arts was built between 1919 and 1924. Its stone exterior and tower have become "
                        + "a familiar campus image. Look at how the tower changes the building's silhouette, "
                        + "then compare this later building with the older quadrangle nearby.",
                        "https://library.unimelb.edu.au/asc/collections/archives/resources/research-guides/keys-to-the-past/keys/key-49",
                        -37.7976631, 144.9600878,
                        "Look up at Old Arts. Which feature makes its silhouette distinctive?",
                        Arrays.asList("A clock tower", "A glass dome", "A lighthouse lantern"), 0),
                    new ThemeStop("old-quad", "Old Quadrangle",
                        "The Old Quad began taking shape in the 1850s. Its early wings formed a core of "
                        + "the new university. Observe the repeated arches from an accessible public path: "
                        + "their rhythm connects separate parts of the building around an open space.",
                        "https://www.unimelb.edu.au/old-quad/about/our-history",
                        -37.79806, 144.96073,
                        "Which repeated shape can you observe around the quadrangle?",
                        Arrays.asList("Windmill blades", "Arches", "Chimney pots only"), 1),
                    new ThemeStop("system-garden", "System Garden",
                        "Established in 1856, the System Garden was designed for teaching botany. "
                        + "Its surviving central tower once belonged to a conservatory. Look for the "
                        + "relationship between the planted beds and this built structure: this is "
                        + "a learning space made from living collections as well as architecture.",
                        "https://sustainablecampus.unimelb.edu.au/the-system-garden",
                        -37.79678, 144.95916,
                        "What surviving built feature stands among the garden beds?",
                        Arrays.asList("A railway platform", "A stadium roof", "A central tower"), 2)));
    }
}
