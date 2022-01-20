package club.decencies.remapper.lunar.pipeline.mixin.processor.type;

import club.decencies.remapper.lunar.mappings.MappingProvider;
import club.decencies.remapper.lunar.pipeline.LunarVersion;
import club.decencies.remapper.lunar.pipeline.mixin.MixinDefinitions;
import club.decencies.remapper.lunar.pipeline.mixin.processor.MethodProcessor;
import club.decencies.remapper.lunar.pipeline.mixin.processor.util.ClassPredicates;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class OverwriteMethodProcessor extends MethodProcessor {

    private final List<MethodNode> rearranged = new ArrayList<>();

    @Override
    public void process(MethodNode mergedMethod, ClassNode mixinNode, ClassNode patchedNode, ClassNode originalNode, LunarVersion version, MappingProvider provider, Map<String, FieldNode> fields, Map<String, MethodNode> methods) {
        getOverwriteMethod(patchedNode, originalNode, mixinNode, mergedMethod, version, provider).ifPresent(overwriteMethod -> {

            final AtomicInteger resolved = new AtomicInteger();
            final List<String> resolvedNames = new ArrayList<>();

            for (MethodNode originalMethod : originalNode.methods) {
                provider.lookupMethodName(originalNode.name, originalMethod).ifPresent(name -> {
                    final String desc = unmapProguardToPatchedMethodDescriptor(originalMethod.desc, version);
                    if (ClassPredicates.mndm(name, desc).test(overwriteMethod) && !resolvedNames.contains(name + desc)) {
                        final int methodIndex = originalNode.methods.indexOf(originalMethod);
                        patchedNode.methods.remove(mergedMethod);
                        patchedNode.methods.add(methodIndex, mergedMethod);
                        resolved.getAndIncrement();
                        resolvedNames.add(name + desc);
                        rearranged.add(mergedMethod);
                        methods.put(mergedMethod.name + mergedMethod.desc, mergedMethod);
                    }
                });
            }

            InsnList clone = clone(mergedMethod.instructions, overwriteMethod.instructions);
            //retargetInstructionList(version, provider, clone, patchedNode, originalNode.name, mixinNode, fields, methods);
            overwriteMethod.visitAnnotation(MixinDefinitions.OVERWRITE, true);
        });
    }

    private String unmapProguardToPatchedMethodDescriptor(String descriptor, LunarVersion version) {
        Type type = Type.getType(descriptor);
        int index = 0;
        Type[] types = type.getArgumentTypes();
        for (Type argumentType : types) {
            int finalIndex = index++;
            if (argumentType.getSort() == Type.ARRAY) {
                String name = argumentType.getElementType().getInternalName();
                version.getMappingReversed(name).ifPresent(mappedName -> {
                    StringBuilder builder = new StringBuilder();
                    for (int i = 0; i < argumentType.getDimensions(); i++) {
                        builder.append("[");
                    }
                    builder.append("L").append(mappedName).append(";");
                    types[finalIndex] = Type.getType(builder.toString());
                });
            } else if (argumentType.getSort() == Type.OBJECT) {
                String name = argumentType.getInternalName();
                version.getMappingReversed(name)
                        .ifPresent(mappedName -> types[finalIndex] = Type.getType("L" + mappedName + ";"));
            }
        }

        StringBuilder methodDescriptorBuilder = new StringBuilder();

        methodDescriptorBuilder.append("(");
        for (Type argumentType : types) {
            methodDescriptorBuilder.append(argumentType.getDescriptor());
        }
        methodDescriptorBuilder.append(")");

        Type returnType = type.getReturnType();

        String name = version.getMappingReversed(returnType.getInternalName())
                .orElseGet(returnType::getInternalName);

        if (name.length() == 1 || (name.startsWith("L") && name.endsWith(";"))) {
            methodDescriptorBuilder.append(name);
        } else {
            methodDescriptorBuilder.append("L").append(name).append(";");
        }

        return methodDescriptorBuilder.toString();
    }

    /**
     * Find the {@code @Overwrite} method in the mixin class.
     *
     * @param patched          the patched class node that contains the {@code patchedOverwrite}.
     * @param mixin            the mixin class to find the method in.
     * @param patchedOverwrite the patched overwrite method in the patched class.
     * @return {@link Optional#empty()} if no matching method was found, or the method pair in an {@link Optional}.
     */
    protected Optional<MethodNode> getOverwriteMethod(ClassNode patched, ClassNode original, ClassNode mixin, MethodNode patchedOverwrite, LunarVersion version, MappingProvider provider) {
        int patchedOverwriteIndex = patched.methods.indexOf(patchedOverwrite);

        int firstMemberIndex = -1;

        int index = 0;
        for (MethodNode method : patched.methods) {
            final String mixinName = getMixin(method);
            if (mixinName != null) {
                if (mixinName.equals(mixin.name) && !rearranged.contains(method)) {
                    firstMemberIndex = index;
                    break;
                }
            }
            index++;
        }

        int mixinMemberIndex = patchedOverwriteIndex - firstMemberIndex;

        int offset = 1; // skip constructor

        for (int i = 0; i < mixin.methods.size(); i++) {
            final MethodNode methodNode = mixin.methods.get(i);
            if ((methodNode.access & Opcodes.ACC_ABSTRACT) != 0) {
                offset++;
            } else if (!methodNode.name.endsWith("init>")) {
                break;
            }
        }

        mixinMemberIndex += offset;

        if (mixinMemberIndex > mixin.methods.size() - 1 || 0 > mixinMemberIndex) {
            System.err.println("Critical error while remapping @Overwrite for method " + patchedOverwrite.name + patchedOverwrite.desc + " in " + patched.name + " (index: " + mixinMemberIndex + ")");
        } else {
            final MethodNode mixinOverwriteMethod = mixin.methods.get(mixinMemberIndex);
            if (mixinOverwriteMethod.name.matches("[a-z]{25}")) {

                AtomicBoolean remapped = new AtomicBoolean(false);

                mixin.interfaces.stream().map(version::getPatchedMClass).filter(Optional::isPresent).map(Optional::get).forEach(itfNode -> {
                    if (remapped.get()) return;
                    itfNode.methods.stream().filter(ClassPredicates.mndm(mixinOverwriteMethod)).findFirst().ifPresent(md -> {
                        int superMethodIndex = itfNode.methods.indexOf(md);
                        version.getMapping(itfNode.name).ifPresent(proguardItfName -> {
                            version.getMcClass(proguardItfName).ifPresent(mcItfNode -> {
                                final MethodNode methodNode = mcItfNode.methods.get(superMethodIndex);
                                provider.lookupMethodName(proguardItfName, methodNode).ifPresent(mapping -> {
                                    mixinOverwriteMethod.name = mapping;
                                    remapped.set(true);
                                });
                            });
                        });
                    });
                });

                if (!remapped.get()) {
                    version.getPatchedMClass(mixin.superName).ifPresent(superNode -> {
                        superNode.methods.stream().filter(ClassPredicates.mndm(mixinOverwriteMethod)).findFirst().ifPresent(md -> {
                            int superMethodIndex = superNode.methods.indexOf(md);
                            version.getMapping(mixin.superName).ifPresent(proguardSuperName -> {
                                version.getMcClass(proguardSuperName).ifPresent(mcSuperNode -> {
                                    final MethodNode methodNode = mcSuperNode.methods.get(superMethodIndex);
                                    provider.lookupMethodName(proguardSuperName, methodNode).ifPresent(mapping -> {
                                        mixinOverwriteMethod.name = mapping;
                                        remapped.set(true);
                                    });
                                });
                            });
                        });
                    });
                }

                if (!remapped.get()) {
                    System.err.println("Critical error while remapping @Overwrite for method " + patchedOverwrite.name);
                    System.err.println("- Owner: " + patched.name);
                }
            }
            return Optional.of(mixinOverwriteMethod);
        }
        return Optional.empty();
    }

}
