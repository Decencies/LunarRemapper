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

/**
 * @author Decencies
 * @since 19/06/2021 13:18
 */
public class SrgMap implements MappingProvider {

    private final Map<String, String> classes = new HashMap<>();
    public final Map<String, Map<String, String>> srgFields = new HashMap<>();
    public final Map<String, Map<String, String>> srgMethods = new HashMap<>();
    public final Map<String, String> mojangFields = new HashMap<>();
    public final Map<String, String> mojangMethods = new HashMap<>();
    public final Map<String, String> parameters = new HashMap<>();

    public SrgMap(File srg) {
        try {
            (new BufferedReader(new InputStreamReader(new FileInputStream(srg), StandardCharsets.UTF_8))).lines().forEach((line) -> {
                String type = line.substring(0, 2);
                line = line.substring(4);
                if (!type.equals("PK")) {
                    switch (type) {
                        case "CL": {
                            String unmapped = line.substring(0, line.lastIndexOf(" "));
                            String mapped = line.substring(line.lastIndexOf(" ") + 1);
                            classes.put(unmapped, mapped);
                            break;
                        }
                        case "FD": {
                            String unmapped = line.substring(0, line.lastIndexOf(" ")).replace("/", ".");
                            String oldClassName = unmapped.split("\\.")[0];
                            String oldFieldName = unmapped.split("\\.")[1];
                            String newClassName = line.substring(line.lastIndexOf(" ") + 1);
                            String newFieldName = newClassName.substring(newClassName.lastIndexOf("/") + 1);
                            srgFields.computeIfAbsent(oldClassName, v -> new HashMap<>()).put(oldFieldName, newFieldName);
                            break;
                        }
                        case "MD": {
                            String[] split = line.split(" ");
                            String oldStructure = split[0];

                            String oldClassName = oldStructure.split("/")[0];
                            String oldMethodName = oldStructure.split("/")[1];
                            String oldSignature = split[1];

                            String newStructure = split[2];
                            String newMethodName = newStructure.substring(newStructure.lastIndexOf('/') + 1);
                            String newSignature = split[3];

                            srgMethods.computeIfAbsent(oldClassName, v -> new HashMap<>()).put(oldMethodName + oldSignature, newMethodName);
                            break;
                        }
                    }
                }
            });
        } catch (Exception exception) {
            exception.printStackTrace();
        }
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
        if (!srgFields.containsKey(className)) return Optional.of(name);
        final String value = srgFields.get(className).get(name);
        if (mojangFields.containsKey(value)) {
            return Optional.of(mojangFields.get(value));
        }
        return Optional.ofNullable(value);
    }

    @Override public Optional<String> lookupMethodName(String className, MethodNode methodNode) {
        if (methodNode == null) return Optional.empty();
        return lookupMethodName(className, methodNode.name, methodNode.desc);
    }

    @Override
    public Optional<String> lookupMethodName(String className, String name, String desc) {
        if (!srgMethods.containsKey(className)) return Optional.of(name);
        final String value = srgMethods.get(className).get(name + desc);
        if (mojangMethods.containsKey(value)) {
            return Optional.of(mojangMethods.get(value));
        }
        if (value == null) {
            return Optional.of(name);
        }
        return Optional.of(value);
    }

    @Override
    public Optional<String> lookupMethodParameter(String className, String name, String desc, int index) {
        if (!srgMethods.containsKey(className)) return Optional.of(name);
        final String value = srgMethods.get(className).get(name + desc);
        final String paramKey = "p_" + value.substring("func_".length(), value.lastIndexOf('_')) + "_";
        if (parameters.containsKey(value)) {
            return Optional.of(parameters.get(value));
        }
        return Optional.of(paramKey);
    }

    @Override
    public void pushFields(File file) {
        try {
            (new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))).lines().forEach((line) -> {
                String[] split = line.split(",");
                mojangFields.put(split[0], split[1]);
            });
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    @Override
    public void pushMethods(File file) {
        try {
            (new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))).lines().forEach((line) -> {
                String[] split = line.split(",");
                mojangMethods.put(split[0], split[1]);
            });
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    @Override
    public void pushParameters(File file) {
        try {
            (new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))).lines().forEach((line) -> {
                String[] split = line.split(",");
                parameters.put(split[0], split[1]);
            });
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

}
