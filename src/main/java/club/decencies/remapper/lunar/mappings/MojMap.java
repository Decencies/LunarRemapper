package club.decencies.remapper.lunar.mappings;

import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Decencies
 * @since 29/06/2021 11:14
 */
public class MojMap implements MappingProvider {

    public Map<String, String> classes = new HashMap<>();
    public Map<String, Map<String, String>> fields = new HashMap<>();
    public Map<String, Map<String, String>> methods = new HashMap<>();

    public MojMap(File file) {
        //if (true) return; // todo fix
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            AtomicReference<String> lastClassName = new AtomicReference<>();
            reader.lines().forEach(line -> {
                if (line.startsWith("#")) return;
                if (line.startsWith("    ")) {
                    line = line.substring(4); // remove spaces.
                    line = line.substring(Math.max(0, line.lastIndexOf(':'))); // remove line numbers
                    String oldName = line.substring(line.lastIndexOf(' '));
                    String[] split = line.split(" ");
                    String structure = split[1];
                    if (structure.contains("(")) {
                        String returnType = parseType(split[0]);
                        String methodName = structure.substring(0, structure.indexOf('(') + 1);
                        String methodSignature = parseType(structure.substring(structure.indexOf('('), structure.indexOf(')') + 1));
                        //fields.computeIfAbsent(lastClassName.get(), v -> new HashMap<>()).put(oldName, methodName);
                    } else {
                        fields.computeIfAbsent(lastClassName.get(), v -> new HashMap<>()).put(oldName, structure);
                    }
                } else {
                    String trimmed = line.replaceAll(" ", "");
                    trimmed = trimmed.replace(":", "");
                    String[] split = trimmed.split("->");
                    String mappedName = split[0];
                    String unmappedName = split[1];
                    lastClassName.set(unmappedName);
                    classes.put(unmappedName.replace(".", "/"), mappedName.replace(".", "/"));
                }
            });
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private String parseType(String type) {
        switch (type) {
            case "void":
                return "V";
            case "int":
                return "I";
            case "boolean":
                return "Z";
            case "double":
                return "D";
            case "float":
                return "F";
            case "char":
                return "C";
            case "long":
                return "J";
            default:
                return "L" + type.replaceAll("\\.", "/") + ";";
        }
    }

    public Optional<String> get(String name) {
        return Optional.ofNullable(classes.get(name));
    }

    @Override public Optional<String> lookupClassName(String className) {
        return Optional.ofNullable(classes.get(className));
    }

    @Override public Optional<String> lookupFieldName(String className, FieldNode fieldNode) {
        if (fieldNode == null) return Optional.empty();
        return lookupFieldName(className, fieldNode.name);
    }

    @Override
    public Optional<String> lookupFieldName(String className, String name) {
        return Optional.ofNullable(fields.get(className).get(name));
    }

    @Override public Optional<String> lookupMethodName(String className, MethodNode methodNode) {
        if (methodNode == null) return Optional.empty();
        return lookupMethodName(className, methodNode.name, methodNode.desc);
    }

    @Override
    public Optional<String> lookupMethodName(String className, String name, String desc) {
        return Optional.ofNullable(methods.get(className).get(name + desc));
    }

    @Override
    public Optional<String> lookupMethodParameter(String className, String name, String desc, int index) {
        return Optional.empty();
    }

}
