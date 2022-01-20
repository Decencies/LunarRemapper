package club.decencies.remapper.lunar.pipeline;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import lombok.Getter;
import lombok.Setter;
import org.objectweb.asm.tree.ClassNode;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Decencies
 * @since 29/06/2021 12:38
 */
public class LunarVersion {

    private final BiMap<String, String> mappings = HashBiMap.create();
    private final BiMap<String, String> export = HashBiMap.create();
    @Getter
    private final List<String> differenceMappings = new ArrayList<>();
    private final Map<String, byte[]> lunarPatches = new HashMap<>();
    private final Map<String, byte[]> optiFinePatches = new HashMap<>();
    @Getter
    private final Map<String, ClassNode> classes = new HashMap<>();
    @Getter
    private final Map<String, byte[]> resources = new HashMap<>();
    private final Map<String, ClassNode> mcClasses = new HashMap<>();
    @Getter
    private final Map<String, ClassNode> patchedMcClasses = new HashMap<>();



    @Getter
    @Setter
    private String mcVer;

    @Getter
    @Setter
    private String commit;

    public Optional<String> getMapping(String name) {
        return Optional.ofNullable(mappings.get(name));
    }

    public Optional<String> getMappingReversed(String name) {
        return Optional.ofNullable(mappings.inverse().get(name));
    }

    public List<ClassNode> getMCClasses() {
        return new ArrayList<>(mcClasses.values());
    }

    public List<ClassNode> getClasses() {
        return new ArrayList<>(classes.values());
    }

    public Collection<ClassNode> getPatchedClasses() {
        return patchedMcClasses.values();
    }

    public Optional<byte[]> getLunarPatch(String name) {
        return Optional.ofNullable(lunarPatches.get(name));
    }

    public Optional<byte[]> getOptiFinePatch(String name) {
        return Optional.ofNullable(optiFinePatches.get(name));
    }

    public Optional<ClassNode> getClass(String name) {
        return Optional.ofNullable(classes.get(name));
    }

    public Optional<byte[]> getResource(String name) {
        return Optional.ofNullable(resources.get(name));
    }

    public Optional<ClassNode> getMcClass(String name) {
        return Optional.ofNullable(mcClasses.get(name));
    }

    public Optional<ClassNode> getPatchedMClass(String name) {
        return Optional.ofNullable(patchedMcClasses.get(name));
    }

    public void addExport(String name, String newName) {
        if (!newName.contains("/")) {
            newName = name.substring(0, name.lastIndexOf('/') + 1) + newName;
        }
        if (export.containsValue(newName)) {
            addExport(name, newName + 'j');
            return;
        }
        export.put(name, newName);
    }

    public void addMapping(String name, String newName) {
        mappings.put(name, newName);
    }

    public void addLunarPatch(String name, byte[] patch) {
        lunarPatches.put(name, patch);
    }

    public void addOptiFinePatch(String name, byte[] patch) {
        optiFinePatches.put(name, patch);
    }

    public void addClass(String name, ClassNode node) {
        classes.put(name, node);
    }

    public byte[] addResource(String name, byte[] bytes) {
        resources.put(name, bytes);
        return bytes;
    }

    public void addMcClass(String name, ClassNode node) {
        mcClasses.put(name, node);
    }

    public void addPatchedMcClass(String name, ClassNode node) {
        patchedMcClasses.put(name, node);
    }

    public boolean isUsingOptiFine() {
        return optiFinePatches.size() > 0;
    }

    public boolean hasVerBeenSet() {
        return mcVer != null;
    }

    public Map<String, String> getExports() {
        return export;
    }
}
