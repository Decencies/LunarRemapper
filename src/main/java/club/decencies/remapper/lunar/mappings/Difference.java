package club.decencies.remapper.lunar.mappings;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author Decencies
 * @since 18/06/2021 22:51
 */
public class Difference {

    public enum MatchMethod {
        NAME,
        DESCRIPTOR,
        BOTH
    }

    @Getter
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class DifferenceType {
        protected final String owner;
        protected final String name;
        protected final String descriptor;
        protected final MatchMethod method;
    }

    private final Map<DifferenceType, DifferenceType> fieldMap = new HashMap<>();
    private final Map<DifferenceType, DifferenceType> methodMap = new HashMap<>();

    public Optional<DifferenceType> getField(DifferenceType name) {
        if (name.getMethod() == MatchMethod.BOTH) {
            return Optional.ofNullable(fieldMap.get(name));
        } else {
            if (name.getMethod() == MatchMethod.NAME) {
                return fieldMap.entrySet().stream().filter(e -> e.getKey().name.equals(name.name) && e.getKey().owner.equals(name.owner)).findFirst().map(Map.Entry::getValue);
            } else {
                return fieldMap.entrySet().stream().filter(e -> e.getKey().descriptor.equals(name.descriptor) && e.getKey().owner.equals(name.owner)).findFirst().map(Map.Entry::getValue);
            }
        }
    }


    public Optional<DifferenceType> getMethod(DifferenceType name) {
        if (name.getMethod() == MatchMethod.BOTH) {
            return Optional.ofNullable(methodMap.get(name));
        } else {
            if (name.getMethod() == MatchMethod.NAME) {
                return methodMap.entrySet().stream().filter(e -> e.getKey().name.equals(name.name) && e.getKey().owner.equals(name.owner)).findFirst().map(Map.Entry::getValue);
            } else {
                return methodMap.entrySet().stream().filter(e -> e.getKey().descriptor.equals(name.descriptor) && e.getKey().owner.equals(name.owner)).findFirst().map(Map.Entry::getValue);
            }
        }
    }

    public static Difference resolve(ClassNode original, ClassNode patch, MappingProvider mappingProvider) {

        final Difference difference = new Difference();

        final int originalFieldCount = original.fields.size();
        int fieldIndex = 0;
        for (FieldNode patchedField : patch.fields) {
            if (fieldIndex < originalFieldCount) {
                final FieldNode originalField = original.fields.get(fieldIndex);
                difference.fieldMap.put(new DifferenceType(patch.name, patchedField.name, patchedField.desc, MatchMethod.BOTH), new DifferenceType(original.name, originalField.name, originalField.desc, MatchMethod.BOTH));
            }
            fieldIndex++;
        }

        final int originalMethodCount = original.methods.size();
        int methodIndex = 0;
        for (MethodNode patchedMethod : patch.methods) {
            if (methodIndex < originalMethodCount) {
                final MethodNode originalMethod = original.methods.get(methodIndex);
                difference.methodMap.put(new DifferenceType(patch.name, patchedMethod.name, patchedMethod.desc, MatchMethod.BOTH), new DifferenceType(original.name, originalMethod.name, originalMethod.desc, MatchMethod.BOTH));
            }
            methodIndex++;
        }

        return difference;
    }

}
