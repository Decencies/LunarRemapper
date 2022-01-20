package club.decencies.remapper.lunar.pipeline.mixin.processor.util;

import org.objectweb.asm.tree.MethodNode;

import java.util.function.Predicate;

public class ClassPredicates {

    public static Predicate<MethodNode> mndm(MethodNode methodNode) {
        return mndm(methodNode.name, methodNode.desc);
    }

    public static Predicate<MethodNode> mndm(String name, String desc) {
        return methodNode -> methodNode.name.equals(name) && methodNode.desc.equals(desc);
    }

}
