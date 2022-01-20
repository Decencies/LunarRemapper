package club.decencies.remapper.lunar.util;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.stream.Collectors;

public class ClassUtil {

    @SuppressWarnings("all")
    private static final String META_STRING = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";

    public static List<String> strings(ClassNode node) {
        final List<String> strings = new ArrayList<>();
        if (node.fields != null) {
            Collection<String> fieldStrings = node.fields.stream()
                    .filter(f -> f.desc.equals(Type.getDescriptor(String.class)) && f.value != null)
                    .map(f -> (String) f.value)
                    .collect(Collectors.toList());
            strings.addAll(fieldStrings);
        }
        node.methods.stream().filter(m -> m.instructions != null).forEach(m -> m.instructions.forEach(i -> {
            if (i.getOpcode() == Opcodes.LDC) {
                LdcInsnNode ldc = (LdcInsnNode) i;
                if (ldc.cst instanceof String) {
                    strings.add((String) ldc.cst);
                }
            }
        }));
        return strings;
    }

    public static List<String> imports(ClassNode node) {
        final List<String> imports = new ArrayList<>();
        node.methods.stream().filter(m -> m.instructions != null).forEach(m -> m.instructions.forEach(i -> {
            if (i instanceof MethodInsnNode) {
                MethodInsnNode methodInsnNode = (MethodInsnNode) i;
                if (!methodInsnNode.owner.equals(node.name)) {
                    boolean isJavaClass = true;
                    try {
                        Class.forName(methodInsnNode.owner.replace("/", "."), false, ClassLoader.getSystemClassLoader());
                    } catch (ClassNotFoundException ignored) {
                        isJavaClass = false;
                    }
                    if (!isJavaClass) {
                        imports.add(methodInsnNode.owner);
                    }
                }
            }
        }));
        if (node.interfaces != null) imports.addAll(node.interfaces);
        if (!node.superName.equals("java/lang/Object")) imports.add(node.superName);
        return imports;
    }

    public static List<String> mixins(ClassNode classNode) {
        List<String> list = new ArrayList<>();
        classNode.methods.stream()
                .filter(m -> m.visibleAnnotations != null)
                .map(m -> m.visibleAnnotations.stream().filter(a -> a.desc.equals(META_STRING)).findFirst())
                .filter(Optional::isPresent)
                .forEach(a -> {
                    String mixin = ((String) a.get().values.get(1)).replace(".", "/");
                    if (!list.contains(mixin)) list.add(mixin);
                });
        return list;
    }

    public static boolean hasMixins(ClassNode classNode) {
        if (classNode.methods.size() == 0) return false;
        MethodNode node = classNode.methods.get(classNode.methods.size() - 1);
        boolean mixin = node.visibleAnnotations != null && node.visibleAnnotations.stream().anyMatch(a -> a.desc.equals(META_STRING));
        return mixin || classNode.methods.stream().anyMatch(m -> m.name.contains("$"));
    }

    public static boolean doesMethodCallTarget(MethodNode method, MethodNode target) {
        boolean calls = false;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getType() == AbstractInsnNode.METHOD_INSN) {
                MethodInsnNode methodInsnNode = (MethodInsnNode) instruction;
                if (methodInsnNode.name.equals(target.name) && methodInsnNode.desc.equals(target.desc)) {
                    calls = true;
                }
            }
        }
        return calls;
    }

    public static List<MethodNode> getCallers(MethodNode method, ClassNode node) {
        return node.methods.stream().filter(md -> doesMethodCallTarget(md, method)).collect(Collectors.toList());
    }

    public static Optional<MethodNode> getCaller(MethodNode method, ClassNode node) {
        return node.methods.stream().filter(md -> doesMethodCallTarget(md, method)).findFirst();
    }

    public static boolean isFlag(int source, int... flags) {
        boolean state = false;
        for (int flag : flags) state |= (source & flag) == flag;
        return state;
    }


}
