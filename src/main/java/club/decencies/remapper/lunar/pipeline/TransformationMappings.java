package club.decencies.remapper.lunar.pipeline;

import club.decencies.remapper.lunar.mappings.MappingProvider;

import club.decencies.remapper.lunar.pipeline.mixin.MixinDefinitions;
import club.decencies.remapper.lunar.pipeline.mixin.MixinMethod;
import club.decencies.remapper.lunar.pipeline.mixin.processor.util.ClassPredicates;
import lombok.RequiredArgsConstructor;

import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;

import java.util.HashMap;

import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
public class TransformationMappings extends Remapper {

    public static boolean DONT_TRANSFORM = false;

    private final LunarVersion version;
    private final MappingProvider provider;

    private static final String OBFUSCATION_DICT = "[abehps]{25}";
    private static final String OBFUSCATED_PACKAGE = "net/minecraft/v";

    private Map<String, Map<String, FieldNode>> fieldMap = new HashMap<>();
    private Map<String, Map<String, MethodNode>> methodMap = new HashMap<>();
    private Map<String, String> patchedMixinCorrelationMap = new HashMap<>();
    private Map<String, String> classRenameMap = new HashMap<>();

    public void addFields(ClassNode patchedNode, Map<String, FieldNode> fields) {
        fieldMap.put(patchedNode.name, fields);
    }

    public void addMethods(ClassNode patchedNode, Map<String, MethodNode> methods) {
        methodMap.put(patchedNode.name, methods);
    }

    public void addMixinCorrelation(String patched, String mixin) {
        patchedMixinCorrelationMap.put(patched, mixin);
    }

    @Override
    public String map(String internalName) {

        if (classRenameMap.containsKey(internalName)) {
            return classRenameMap.get(internalName);
        }

        if (DONT_TRANSFORM) return internalName;

        if (internalName.startsWith(OBFUSCATED_PACKAGE)) {
            return version.getMapping(internalName).flatMap(provider::lookupClassName)
                    .orElse(internalName);
                    //.orElseThrow(() -> new RuntimeException(String.format("Could not resolve mapping for name %s.", internalName)));
        }
        return super.map(internalName);
    }

    @Override
    public String mapFieldName(String owner, String name, String descriptor) {

        if (DONT_TRANSFORM) return name;

        if (name.matches(OBFUSCATION_DICT)) {
            if (owner.startsWith(OBFUSCATED_PACKAGE)) {

                String mappedName = version.getMapping(owner)
                        .orElseThrow(() -> new RuntimeException(String.format("No mapping for %s was found.", owner)));

                final Map<String, FieldNode> map = fieldMap.get(owner);

                if (map == null) {
                    throw new RuntimeException(String.format("No field mappings exist for %s.", owner));
                }

                final FieldNode fieldNode = map.get(name);

                if (fieldNode == null) {

                    ClassNode patchedNode = version.getPatchedMClass(owner).orElseThrow(() -> new RuntimeException(String.format("Could not get patched MC class %s.", owner)));

                    String s = unmapInheritedFieldMemberName(patchedNode, name);
                    if (s == null) {
                        System.err.printf("Could not find nested field member %s from %s.%n", name, owner);
                    } else {
                        return s;
                    }

                    System.err.printf("No proguard field mapping for %s.%s.%n", owner, name);
                    return name;
                }

                return provider.lookupFieldName(mappedName, fieldNode)
                        .orElseThrow(() -> new RuntimeException(String.format("No SRG field mapping for %s exist in %s.", fieldNode.name, mappedName)));

            }
            else if (owner.startsWith("lunar/")) {
                final Optional<ClassNode> lunarClass = version.getClass(owner);
                if (lunarClass.isPresent()) {
                    final String mappedName = unmapInheritedFieldMemberName(lunarClass.get(), name);
                    if (mappedName == null) {
                        System.err.printf("Could not find nested field member %s from %s.%n", name, owner);
                    } else {
                        return mappedName;
                    }
                }
            }
        }

        return super.mapFieldName(owner, name, descriptor);
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {

        if (DONT_TRANSFORM) return name;

        if (name.matches(OBFUSCATION_DICT)) {
            if (owner.startsWith(OBFUSCATED_PACKAGE)) {

                String mappedName = version.getMapping(owner)
                        .orElseThrow(() -> new RuntimeException(String.format("No mapping for %s was found.", owner)));

                final Map<String, MethodNode> map = methodMap.get(owner);

                if (map == null) {
                    throw new RuntimeException(String.format("No method mappings exist for %s.", owner));
                }

                final MethodNode methodNode = map.get(name + descriptor);

                if (methodNode == null) {

                    ClassNode patchedNode = version.getPatchedMClass(owner).orElseThrow(() -> new RuntimeException(String.format("Could not get patched MC class %s.", owner)));

                    String s = unmapInheritedMethodMemberName(patchedNode, name, descriptor);
                    if (s == null) {
                        System.err.printf("Could not find nested method member %s%s from %s.%n", name, descriptor, owner);
                    } else {
                        return s;
                    }

                    System.err.printf("No proguard method mapping for %s.%s%s.%n", owner, name, descriptor);
                    return name;
                }

                return provider.lookupMethodName(mappedName, methodNode)
                        .orElseThrow(() -> new RuntimeException(String.format("No SRG method mapping for %s exist in %s.", methodNode.name, mappedName)));
            } else if (owner.startsWith("lunar/")) {
                final Optional<ClassNode> lunarClass = version.getClass(owner);
                if (lunarClass.isPresent()) {
                    final String mappedName = unmapInheritedMethodMemberName(lunarClass.get(), name, descriptor);
                    if (mappedName == null) {
                        System.err.printf("Could not find nested method member %s from %s.%n", name, owner);
                    } else {
                        return mappedName;
                    }
                }
            }
        }

        return super.mapMethodName(owner, name, descriptor);
    }

//    @Override
//    public String mapInvokeDynamicMethodName(String name, String descriptor) {
//        return super.mapInvokeDynamicMethodName(name, descriptor);
//    }

    private String unmapInheritedMethodMemberName(ClassNode node, String name, String desc) {
        // if the class is a minecraft patched class...
        if (node.name.startsWith(OBFUSCATED_PACKAGE)) {

            if (node.superName.startsWith(OBFUSCATED_PACKAGE)) {
                final Optional<ClassNode> optionalPatchedMcClass = version.getPatchedMClass(node.superName);
                if (!optionalPatchedMcClass.isPresent()) {
                    throw new RuntimeException(String.format("Could not find patched minecraft class %s.", node.superName));
                }
                final ClassNode classNode = optionalPatchedMcClass.get();
                // if the owner contains the method we are looking for...
                if (classNode.methods.stream().anyMatch(ClassPredicates.mndm(name, desc))) {
                    String mappedName = unmapInheritedMethodMemberName(classNode, name, desc);
                    if (mappedName != null) {
                        return mappedName;
                    }
                }
            }

            final Optional<String> optionalMapping = version.getMapping(node.name);

            if (!optionalMapping.isPresent()) {
                System.err.printf("Could not find mapping for %s.%n", node.name);
                return name;
            }

            final String mapping = optionalMapping.get();

            final Optional<ClassNode> optionalMcClass = version.getMcClass(mapping);

            if (!optionalMcClass.isPresent()) {
                throw new RuntimeException(String.format("Could not find minecraft class %s.", mapping));
            }

            final ClassNode mcClass = optionalMcClass.get();

            int index = 0;
            for (MethodNode method : node.methods) {
                if (method.name.equals(name) && method.desc.equals(desc)) {
                    if (mcClass.methods.size() - 1 < index) {
                        System.out.println(method.visibleAnnotations != null && method.visibleAnnotations.stream().anyMatch(a -> a.desc.equals(MixinDefinitions.PROXY)));
                        System.out.println(method.name);
                        throw new RuntimeException(String.format("Could not map a minecraft method from %s. (index out of bounds, %s)", mcClass.name, index));
                    } else {
                        final MethodNode methodNode = mcClass.methods.get(index);
                        final Optional<String> optionalMappedName = provider.lookupMethodName(mcClass.name, methodNode);
                        if (!optionalMappedName.isPresent()) {
                            throw new RuntimeException(String.format("Could not map minecraft method %s from %s.", methodNode.name, mcClass.name));
                        }
                        return optionalMappedName.get();
                    }
                }
                index++;
            }
        } else if (node.name.startsWith("lunar/")) {
            if (node.interfaces != null) {
                for (String face : node.interfaces) {
                    // a patched minecraft interface...
                    if (face.startsWith(OBFUSCATED_PACKAGE)) {
                        final Optional<ClassNode> optionalPatchedMcClass = version.getPatchedMClass(face);
                        if (!optionalPatchedMcClass.isPresent()) {
                            throw new RuntimeException(String.format("Could not find patched minecraft class %s.", face));
                        }

                        final ClassNode classNode = optionalPatchedMcClass.get();

                        final String mappedName = unmapInheritedMethodMemberName(classNode, name, desc);

                        if (mappedName != null) {
                            return mappedName;
                        }
                    }
                }
            }
            if (node.superName.startsWith(OBFUSCATED_PACKAGE)) {
                final Optional<ClassNode> optionalPatchedMcClass = version.getPatchedMClass(node.superName);
                if (!optionalPatchedMcClass.isPresent()) {
                    throw new RuntimeException(String.format("Could not find patched minecraft class %s.", node.superName));
                }
                final ClassNode classNode = optionalPatchedMcClass.get();
                return unmapInheritedMethodMemberName(classNode, name, desc);
            }

        }
        return null;
    }

    private String unmapInheritedFieldMemberName(ClassNode node, String name) {

        // if the class is a minecraft patched class...
        if (node.name.startsWith(OBFUSCATED_PACKAGE)) {

            if (node.superName.startsWith(OBFUSCATED_PACKAGE)) {
                final Optional<ClassNode> optionalPatchedMcClass = version.getPatchedMClass(node.superName);
                if (!optionalPatchedMcClass.isPresent()) {
                    throw new RuntimeException(String.format("Could not find patched minecraft class %s.", node.superName));
                }
                final ClassNode classNode = optionalPatchedMcClass.get();
                String mappedName = unmapInheritedFieldMemberName(classNode, name);
                if (mappedName != null) {
                    return mappedName;
                }
            }

            final Optional<String> optionalMapping = version.getMapping(node.name);
            if (!optionalMapping.isPresent()) {
                System.err.printf("Could not find mapping for %s.%n", node.name);
                return name;
            }

            final String mapping = optionalMapping.get();

            final Optional<ClassNode> optionalMcClass = version.getMcClass(mapping);

            if (!optionalMcClass.isPresent()) {
                throw new RuntimeException(String.format("Could not find minecraft class %s.", mapping));
            }

            final ClassNode mcClass = optionalMcClass.get();

            int index = 0;
            for (FieldNode field : node.fields) {
                if (field.name.equals(name)) {
                    if (mcClass.fields.size() - 1 < index) {
                        throw new RuntimeException(String.format("Could not map a minecraft field from %s. (index out of bounds, %s)", mcClass.name, index));
                    } else {
                        final FieldNode fieldNode = mcClass.fields.get(index);
                        final Optional<String> optionalMappedName = provider.lookupFieldName(mcClass.name, fieldNode);
                        if (!optionalMappedName.isPresent()) {
                            throw new RuntimeException(String.format("Could not map minecraft field %s.%s.", mcClass.name, fieldNode.name));
                        }
                        return optionalMappedName.get();
                    }
                }
                index++;
            }
        } else if (node.name.startsWith("lunar/")) {
            if (node.interfaces != null) {
                for (String face : node.interfaces) {
                    // a patched minecraft interface...
                    if (face.startsWith(OBFUSCATED_PACKAGE)) {
                        final Optional<ClassNode> optionalPatchedMcClass = version.getPatchedMClass(face);
                        if (!optionalPatchedMcClass.isPresent()) {
                            throw new RuntimeException(String.format("Could not find patched minecraft class %s.", face));
                        }

                        final ClassNode classNode = optionalPatchedMcClass.get();

                        final String mappedName = unmapInheritedFieldMemberName(classNode, name);

                        if (mappedName != null) {
                            return mappedName;
                        }
                    }
                }
            }

            if (node.superName.startsWith(OBFUSCATED_PACKAGE)) {
                final Optional<ClassNode> optionalPatchedMcClass = version.getPatchedMClass(node.superName);
                if (!optionalPatchedMcClass.isPresent()) {
                    throw new RuntimeException(String.format("Could not find patched minecraft class %s.", node.superName));
                }
                final ClassNode classNode = optionalPatchedMcClass.get();
                return unmapInheritedFieldMemberName(classNode, name);
            }
        }

        return null;
    }

    /**
     *
     */
    public void lastResort(ClassNode owner, String name, String desc) {
        MethodNode caller = null;
        for (MethodNode method : owner.methods) {
            for (AbstractInsnNode insnNode : method.instructions) {
                if (insnNode.getType() == AbstractInsnNode.METHOD_INSN) {
                    MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;
                    if (methodInsnNode.name.equals(name) && methodInsnNode.desc.equals(desc)) {
                        caller = method;
                        break;
                    }
                }
            }
        }
        if (caller != null) {
            if (MixinMethod.PROXY.matches(caller)) {
                final ClassNode mixin = getMixin(caller);
                if (mixin != null) {
                    final Optional<MethodNode> first = mixin.methods.stream().filter(ClassPredicates.mndm(caller)).findFirst();
                    if (first.isPresent()) {
                        final MethodNode bridgeMethod = first.get();
                        for (AbstractInsnNode instruction : bridgeMethod.instructions) {

                        }
                    }
                }
            }
        }
    }

    protected ClassNode getMixin(MethodNode methodNode) {
        if (methodNode.visibleAnnotations != null) {
            final Optional<AnnotationNode> first =
                    methodNode.visibleAnnotations.stream().filter(an -> an.desc.equals(MixinDefinitions.MIXIN_MERGED)).findFirst();
            if (first.isPresent()) {
                String mixinName = ((String) first.get().values.get(1)).replaceAll("\\.", "/");
                return version.getClass(mixinName).get();
            }
        }
        return null;
    }

    public Map<String, String> getClassRenameMap() {
        return classRenameMap;
    }
}
