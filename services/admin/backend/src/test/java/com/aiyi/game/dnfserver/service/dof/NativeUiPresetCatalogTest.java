package com.aiyi.game.dnfserver.service.dof;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;

public class NativeUiPresetCatalogTest {
    @Test public void allNativeImagesDecodeAndMatchPinnedDimensions() throws Exception {
        NativeUiPresetCatalog catalog = new NativeUiPresetCatalog();
        assertEquals(15, catalog.list().size());
        Set<String> unique = new HashSet<>();
        for (Map<String,Object> preset : catalog.list()) {
            byte[] bytes = catalog.image((String)preset.get("id"));
            assertEquals(preset.get("sha256"), NativeUiState.sha256(bytes));
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            assertNotNull(image);
            assertEquals(preset.get("width"), image.getWidth());
            assertEquals(preset.get("height"), image.getHeight());
            unique.add((String)preset.get("sha256"));
        }
        // The native tool intentionally shares images across stretch variants.
        assertEquals(10, unique.size());
    }
    @Test public void arbitraryResourcePathsCannotBeRead() {
        NativeUiPresetCatalog catalog = new NativeUiPresetCatalog();
        for (String id : Arrays.asList("../ui-native-100.json", "/application.yml", "native-100/image", "", null)) {
            try { catalog.image(id); fail("Unknown ID accepted"); }
            catch (DofFailure e) { assertEquals(404, e.status); }
        }
    }
}
