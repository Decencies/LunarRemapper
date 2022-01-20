package club.decencies.remapper.lunar.extensions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

public class ExtensionManager {

    private final ExtensionClassLoader classLoader = new ExtensionClassLoader();

    public List<Extension> loadExtensions() {
        final List<Extension> extensions = new ArrayList<>();
        final File file = new File("./extensions/");
        if (file.exists() && file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File extensionFile : files) {
                    try {
                        new JarFile(extensionFile); // check if the extension is actually a jar file.
                        classLoader.addURL(file.toURI().toURL());
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                }
            }
        }
        return extensions;
    }

}
