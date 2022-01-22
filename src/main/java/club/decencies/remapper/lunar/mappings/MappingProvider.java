package club.decencies.remapper.lunar.mappings;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author Decencies
 * @since 29/06/2021 16:31
 */
public interface MappingProvider {

    Optional<String> lookupClassName(String className);

    Optional<String> lookupFieldName(String className, FieldNode fieldNode);

    Optional<String> lookupFieldName(String className, String name);

    Optional<String> lookupMethodName(String className, MethodNode methodNode);

    Optional<String> lookupMethodName(String className, String name, String desc);

    Optional<String> lookupMethodParameter(String className, String name, String desc, int index);

    //Optional<String> reverseMojangToSRGMethodNameLookup(String name);

    default void pushFields(File fields) {
        throw new UnsupportedOperationException("");
    }

    default void pushMethods(File file) {
        throw new UnsupportedOperationException("");
    }

    default void pushParameters(File file) {
        throw new UnsupportedOperationException("");
    }

}
