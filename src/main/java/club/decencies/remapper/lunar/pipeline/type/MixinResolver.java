package club.decencies.remapper.lunar.pipeline.type;

import club.decencies.remapper.lunar.mappings.MappingProvider;
import club.decencies.remapper.lunar.pipeline.LunarVersion;
import club.decencies.remapper.lunar.util.ClassUtil;
import club.decencies.remapper.lunar.util.InstructionUtil;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * This class assumes that all members of a patched class have different names for every method.
 * This may change with newer version of Lunar Client.
 *
 * @author Decencies
 * @since 25/09/2021 05:42
 */
public class MixinResolver {

    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final String PROXY = "Lorg/spongepowered/asm/mixin/Proxy;";
    private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
    private static final String SLICE = "Lorg/spongepowered/asm/mixin/injection/Slice;";
    private static final String MODIFY_ARG = "Lorg/spongepowered/asm/mixin/injection/ModifyArg;";
    private static final String MODIFY_ARGS = "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;";
    private static final String MODIFY_CONSTANT = "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;";
    private static final String MODIFY_VARIABLE = "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;";

    private static final String MIXIN_MERGED = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";

    private static final String LOCAL_CAPTURE = "Lorg/spongepowered/asm/mixin/injection/callback/LocalCapture;";
    private static final String CALLBACK_INFO = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";

    private static final String MIXIN_PACKAGE = "com.moonsworth.lunar.mixin";
    private static final String MIXIN_CONFIG_FILE = "mixin.lunar.json";
    private static final String MIXIN_TWEAKER_NAME = "LunarTweaker";

    private static final boolean DEBUG = true;
    private static final boolean REMAP_MODE = true;
    private static final boolean EXPLICIT_OVERRIDE = false;

    private final List<String> mixins = new ArrayList<>();

    /**
     * Resolve mixins from a given patched and original class combo.
     *
     * @param version      the lunar version instance.
     * @param provider     the mapping provider.
     * @param patchedNode  the patched class.
     * @param originalNode the original ProGuard minecraft class.
     */
    public void resolve(LunarVersion version, MappingProvider provider, ClassNode patchedNode, ClassNode originalNode, Map<String, String> mixinNameMap) {
        int index = 0;
        final Map<String, FieldNode> fields = new HashMap<>();
        for (FieldNode fieldNode : patchedNode.fields) {
            if (index < originalNode.fields.size()) {
                FieldNode minecraftField = originalNode.fields.get(index);
                fields.put(fieldNode.name, minecraftField);
                index++;
            } else break; // break non-minecraft fields.
        }

        List<ClassNode> mixinClasses = new ArrayList<>();

        index = 0;
        final Map<String, MethodNode> methods = new HashMap<>();

        final int overwrites = (int) patchedNode.methods.stream().filter(this::isOverwriteMethod).peek(overwrite -> {
            final String mixinName = getVisibleAnnotationValue(overwrite, 1);
            version.getClass(mixinName).ifPresent(mixinNode -> {
                getOverwriteMethod(patchedNode, mixinNode, overwrite, version, provider).ifPresent(overwriteMethod -> {
                    methods.put(overwriteMethod.name + overwriteMethod.desc, overwrite);
                });
            });
        }).count();

        if (overwrites > 0) {
            final AtomicInteger resolved = new AtomicInteger();
            final List<String> resolvedNames = new ArrayList<>();

            for (MethodNode originalMethod : originalNode.methods) {
                provider.lookupMethodName(originalNode.name, originalMethod).ifPresent(name -> {
                    final String desc = unmapProguardToPatchedMethodDescriptor(originalMethod.desc, version, provider);

                    final int methodIndex = originalNode.methods.indexOf(originalMethod);
                    if (methods.containsKey(name + desc) && !resolvedNames.contains(name + desc)) {
                        final MethodNode patchedOverwriteMethodNode = methods.get(name + desc);
                        patchedNode.methods.remove(patchedOverwriteMethodNode);
                        patchedNode.methods.add(methodIndex, patchedOverwriteMethodNode);
                        resolved.getAndIncrement();
                        resolvedNames.add(name + desc);
                        methods.put(patchedOverwriteMethodNode.name + patchedOverwriteMethodNode.desc, patchedOverwriteMethodNode);
                    }
                });
            }

            if (resolved.get() != overwrites) {
                System.err.println("not all overwrites were resolved for " + patchedNode.name + " (" + (overwrites - resolved.get()) + " could not be resolved)");
            }
        }

        final Iterator<MethodNode> patchedMethodIterator = new ArrayList<>(patchedNode.methods).iterator();

        while (patchedMethodIterator.hasNext()) {
            MethodNode methodNode = patchedMethodIterator.next();
            if (index < originalNode.methods.size()) {
                MethodNode minecraftMethod = originalNode.methods.get(index);
                methods.put(methodNode.name + methodNode.desc, minecraftMethod);
                index++;
                continue; // skip mixin members.
            }
            boolean keep = false;

            if (methodNode.name.contains("lambda$")) {
                final String mixinName = getVisibleAnnotationValue(methodNode, 0);

                version.getClass(mixinName).ifPresent(mixinNode -> {
                    mixinNode.methods.stream().filter(md -> md.name.contains("lambda$") && methodNode.name.contains(md.name)).findFirst().ifPresent(lambdaMethod -> {
                        InsnList clone = clone(methodNode.instructions, lambdaMethod.instructions);
                        retargetInstructionList(version, provider, clone, patchedNode, originalNode.name, mixinNode, fields, methods);
                        if (REMAP_MODE) lambdaMethod.access &= ~Opcodes.ACC_SYNTHETIC;
                    });
                    mixinClasses.add(mixinNode);
                });
            } else if (methodNode.name.startsWith("bridge$")) {
                final String mixinName = getVisibleAnnotationValue(methodNode, 0);
                version.getClass(mixinName).ifPresent(mixinNode -> {
                    mixinNode.methods.stream().filter(md -> methodNameDescriptorMatch(md, methodNode)).findFirst().ifPresent(bridgeMethod -> {
                        InsnList clone = clone(methodNode.instructions, bridgeMethod.instructions);
                        retargetInstructionList(version, provider, clone, patchedNode, originalNode.name, mixinNode, fields, methods);
                        if (REMAP_MODE && EXPLICIT_OVERRIDE) bridgeMethod.visitAnnotation("Ljava/lang/Override;", true);
                    });
                    mixinClasses.add(mixinNode);
                });
            } else if (isOverwriteMethod(methodNode)) {
                final String mixinName = getVisibleAnnotationValue(methodNode, 1);
                version.getClass(mixinName).ifPresent(mixinNode -> {
                    getOverwriteMethod(patchedNode, mixinNode, methodNode, version, provider).ifPresent(overwriteMethod -> {
                        InsnList clone = clone(methodNode.instructions, overwriteMethod.instructions);
                        retargetInstructionList(version, provider, clone, patchedNode, originalNode.name, mixinNode, fields, methods);
                        overwriteMethod.visitAnnotation(OVERWRITE, true);
                    });
                    mixinClasses.add(mixinNode);
                });
            } else if (isProxyMethod(methodNode)) {
                final String mixinName = getVisibleAnnotationValue(methodNode, 1);
                final String proxyTarget = getVisibleAnnotationValue(methodNode, 0);

                version.getClass(mixinName).ifPresent(mixinNode -> {
                    mixinNode.methods.stream().filter(md -> md.name.equals("proxy$" + stripProxyTarget(proxyTarget))).findFirst().ifPresent(proxyMethod -> {
                        patchedNode.methods.stream().filter(md -> md.name.equals("original$" + stripProxyTarget(proxyTarget))).findFirst().ifPresent(originalMethod -> {
                            InsnList clone = clone(methodNode.instructions, proxyMethod.instructions);
                            retargetInstructionList(version, provider, clone, patchedNode, originalNode.name, mixinNode, fields, methods);
                            proxyMethod.visitAnnotation(PROXY, true).visit("value", proxyTarget);

                            // switch the methods around (makes index based mapping just that little easier)
                            {
                                final int proxyIndex = patchedNode.methods.indexOf(methodNode);
                                final int originalIndex = patchedNode.methods.indexOf(originalMethod);

                                patchedNode.methods.remove(originalMethod);

                                if (proxyIndex > patchedNode.methods.size()) {
                                    patchedNode.methods.set(proxyIndex, originalMethod);
                                } else {
                                    patchedNode.methods.add(originalMethod);
                                }

                                patchedNode.methods.remove(methodNode);
                                patchedNode.methods.set(originalIndex, methodNode);
                            }
                        });
                    });
                    mixinClasses.add(mixinNode);
                });
            } else if (methodNode.name.startsWith("handler$")) {
                final String mixinName = getVisibleAnnotationValue(methodNode, 0);
                version.getClass(mixinName).ifPresent(mixinNode -> {
                    mixinNode.methods.stream().filter(md -> md.name.equals(strippedMethodName(methodNode.name))).findFirst().ifPresent(handlerMethod -> {
                        ClassUtil.getCaller(methodNode, patchedNode).ifPresent(caller -> {

                            // todo redo this.

                            int callerIndex = -1;

                            if (caller.visibleAnnotations != null && caller.visibleAnnotations.stream().anyMatch(a -> a.desc.equals(PROXY))) {
                                final String proxyTarget = getVisibleAnnotationValue(caller, 0);

                                for (MethodNode method : originalNode.methods) {
                                    final Optional<String> s = provider.lookupMethodName(originalNode.name, method);
                                    if (s.isPresent()) {
                                        if (s.get().equals(proxyTarget)) {
                                            callerIndex = originalNode.methods.indexOf(method);
                                        }
                                    }
                                }
                            } else {
                                callerIndex = patchedNode.methods.indexOf(caller);
                            }

                            if (callerIndex != -1 && callerIndex >= originalNode.methods.size()) {
                                System.err.printf("[handler] Possible modified minecraft version... (missing %s method counterpart in minecraft JAR)\n", caller.name);
                                System.out.println("Mixin: " + mixinName);
                                System.out.println("Obfuscated target class: " + patchedNode.name);
                                System.out.println("Caller: " + caller.name);
                                System.out.println("Handler Name: " + methodNode.name);
                                System.out.println("Vanilla targetClass: " + originalNode.name);
                                System.out.println("Patched by OptiFine: " + version.getOptiFinePatch(originalNode.name).isPresent());
                                return;
                            }

                            MethodNode original = originalNode.methods.get(callerIndex);

                            final AnnotationVisitor visitor = handlerMethod.visitAnnotation(INJECT, true);

                            provider.lookupMethodName(originalNode.name, original)
                                    .ifPresent(name -> visitor.visit("method", name));

                            visitor.visit("at", getInjectAt(original.instructions, caller.instructions, methodNode, handlerMethod));

                            if (getMethodInfo(methodNode).isCancellable()) {
                                visitor.visit("cancellable", true);
                            }

                            if (getMethodInfo(methodNode).isLocals()) {
                                visitor.visitEnum("locals", LOCAL_CAPTURE, "CAPTURE_FAILEXCEPTION");
                            }

                            // remove impl$ prefix
                            handlerMethod.name = handlerMethod.name.substring(handlerMethod.name.indexOf("$") + 1);

                            InsnList clone = clone(methodNode.instructions, handlerMethod.instructions);
                            retargetInstructionList(version, provider, clone, patchedNode, originalNode.name, mixinNode, fields, methods);
                        });
                    });
                    mixinClasses.add(mixinNode);
                });
            } else if (methodNode.name.startsWith("redirect$")) {
                final String mixinName = getVisibleAnnotationValue(methodNode, 0);
                version.getClass(mixinName).ifPresent(mixinNode -> {
                    mixinNode.methods.stream().filter(md -> md.name.equals(strippedMethodName(methodNode.name))).findFirst().ifPresent(redirectMethod -> {
                        ClassUtil.getCaller(methodNode, patchedNode).ifPresent(caller -> {
                            int callerIndex = patchedNode.methods.indexOf(caller);
                            if (callerIndex >= originalNode.methods.size()) {
                                System.err.printf("[redirect] Possible modified minecraft version... (missing %s method counterpart in minecraft JAR)\n", caller.name);
                                System.err.println("Mixin: " + mixinName);
                                System.err.println("Obfuscated target class: " + patchedNode.name);
                                System.err.println("Caller: " + caller.name);
                                System.err.println("Handler Name: " + methodNode.name);
                                System.err.println("Vanilla targetClass: " + originalNode.name);
                                System.err.println("Patched by OptiFine: " + version.getOptiFinePatch(originalNode.name).isPresent());
                                return;
                            }
                            MethodNode original = originalNode.methods.get(callerIndex);

                            final AnnotationVisitor visitor = redirectMethod.visitAnnotation(REDIRECT, true);

                            provider.lookupMethodName(originalNode.name, original).ifPresent(name -> visitor.visit("method", name));

                            visitor.visit("at", getRedirectAt(original.instructions, methodNode.instructions, methodNode, redirectMethod));

                            // remove impl$ prefix
                            redirectMethod.name = redirectMethod.name.substring(redirectMethod.name.indexOf("$") + 1);

                            retargetInstructionList(version, provider, methodNode.instructions, patchedNode, originalNode.name, mixinNode, fields, methods);
                            clone(methodNode.instructions, redirectMethod.instructions);

                        });
                    });
                    mixinClasses.add(mixinNode);
                });
            } else if (isVanillaMergedMethod(methodNode)) {
                final String mixinName = getVisibleAnnotationValue(methodNode, 0);
                version.getClass(mixinName).ifPresent(mixinNode -> {
                    mixinNode.methods.stream().filter(md -> methodNameDescriptorMatch(md, methodNode)).findFirst().ifPresent(method -> {
                        retargetInstructionList(version, provider, methodNode.instructions, patchedNode, originalNode.name, mixinNode, fields, methods);
                        clone(methodNode.instructions, method.instructions);
                    });
                    mixinClasses.add(mixinNode);
                });
            } else keep = true;
            if (!keep && !DEBUG)
                patchedMethodIterator.remove();
        }

        AtomicInteger mixinIndex = new AtomicInteger();

        patchedNode.interfaces.stream().filter(itf -> itf.startsWith("lunar/")).forEach(itf -> version.getClasses().forEach(clsNode -> {
            if (clsNode.interfaces.contains(itf) && (clsNode.access & Opcodes.ACC_INTERFACE) == 0) {
                if (clsNode.methods.size() == 1) { // constructor only.
                    mixinClasses.add(clsNode);
                }
            }
        }));


        final Collection<ClassNode> distinct = mixinClasses.stream().distinct().collect(Collectors.toList());
        final int size = distinct.size();
        distinct.forEach(mixinNode -> {
            // Add mixin annotation to the Mixin class.

            if (REMAP_MODE || DEBUG) {
                mixinNode.visitAnnotation("Ldebug/ObfuscatedMixinTarget;", true).visit("value", patchedNode.name);
                mixinNode.visitAnnotation("Ldebug/OriginalMixinName;", true).visit("value", mixinNode.name);
            }

            provider.lookupClassName(originalNode.name).ifPresent(mappedName -> {

                String suffix = "Mixin".concat(mappedName.substring(mappedName.lastIndexOf('/') + 1));
                if (size > 1) {
                    suffix += "ABCDEFGHIJKLMNOPQRSTUVWXYZ".charAt(mixinIndex.getAndIncrement());
                }
                String name = MIXIN_PACKAGE.replace(".", "/")
                        .concat("/")
                        .concat(mappedName.substring(0, mappedName.lastIndexOf('/')).replace("net/minecraft/", ""))
                        .concat("/")
                        .concat(suffix);

                mixinNameMap.put(mixinNode.name, name);

                Object value = mappedName.contains("$") ? mappedName : Type.getType("L" + mappedName + ";");

                mixinNode.visitAnnotation(MIXIN, true).visit("value", value);
                mixins.add(name);
            });

            mixinNode.fields.stream()
                    .peek(fd -> {
                        // add shadow annotation to minecraft field members of the mixin class.
                        if (patchedNode.fields.stream().noneMatch(fdmc -> fdmc.name.equals(fd.name))) {
                            version.getMapping(patchedNode.name).flatMap(version::getMcClass).ifPresent(mcClass -> {
                                mcClass.fields.stream()
                                        .filter(fdmc -> fd.name.equals(provider.lookupFieldName(mcClass.name, fdmc).get()))
                                        .findFirst()
                                        .ifPresent(fdmc -> {
                                            final int indexOf = mcClass.fields.indexOf(fdmc);
                                            final FieldNode fieldNode = patchedNode.fields.get(indexOf);
                                            if (fieldNode.visibleAnnotations != null) {
                                                for (AnnotationNode visibleAnnotation : fieldNode.visibleAnnotations) {
                                                    fd.visitAnnotation(visibleAnnotation.desc, true);
                                                }
                                            }
                                        });
                            });
                            fd.visitAnnotation(SHADOW, true);
                        }
                    }).peek(fd -> {
                        // todo pull static member initializer.
                        if ((fd.access & Opcodes.ACC_STATIC) != 0) {

                            // todo lookup initializer label.
                            patchedNode.methods.stream().filter(md -> md.name.equals("<clinit>")).findFirst().ifPresent(clinit -> {

                            });
                        }
                    })
                    .filter(fd -> fd.desc.length() > 1) // filter primitives.
                    .forEach(fd -> {
                        // Fix shadow field types.
                        Type type = Type.getType(fd.desc);
                        if (type.getSort() == Type.ARRAY) {
                            version.getMapping(type.getElementType().getInternalName()).flatMap(provider::lookupClassName).ifPresent(mappedName -> {
                                fd.desc = "[L" + mappedName + ";";
                            });
                        } else {
                            version.getMapping(type.getInternalName()).flatMap(provider::lookupClassName).ifPresent(mappedName -> {
                                fd.desc = "L" + mappedName + ";";
                            });
                        }
                    });

            mixinNode.methods
                    .forEach(md -> {

                        // add shadow annotation to abstract method members of the mixin class.
                        if ((md.access & Opcodes.ACC_ABSTRACT) != 0) {
                            // todo check parent for obf members.

                            // check if the shadow member is from a nested superclass.
                            // technically not all 26 chars of the alphabet are used, but we know they are all lower case.
                            if (md.name.matches("[a-z]{25}")) {
                                version.getMapping(mixinNode.superName).ifPresent(mapping -> {
                                    version.getPatchedMClass(mixinNode.superName).ifPresent(superNode -> {
                                        superNode.methods.stream().filter(snmd -> snmd.name.equals(md.name)).findFirst().ifPresent(match -> {
                                            version.getMcClass(mapping).ifPresent(mcClass -> {
                                                MethodNode node = mcClass.methods.get(superNode.methods.indexOf(match));
                                                provider.lookupMethodName(mapping, node).ifPresent(methodName -> {
                                                    md.name = methodName;
                                                });
                                            });
                                        });
                                    });
                                });
                            }

                            md.visitAnnotation(SHADOW, true);
                        }

//                        // todo pull static member.
//                        if ((md.access & Opcodes.ACC_PRIVATE) != 0 && (md.access & Opcodes.ACC_STATIC) != 0) {
//                            System.out.println("Static Member? : " + mixinNode.name + " : " + md.name);
//                        }

                        final Type type = Type.getType(md.desc);

                        if (md.name.equals("<init>") && type.getArgumentTypes().length > 0) {
                            md.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            for (int pIndex = 0; pIndex < type.getArgumentTypes().length; pIndex++) {
                                md.instructions.add(new VarInsnNode(Opcodes.ILOAD, ++pIndex));
                            }
                            md.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, mixinNode.superName, "<init>", type.getDescriptor()));
                        }

                        // unmap the method descriptor of all methods in the mixin class.
                        md.desc = unmapPatchedMethodDescriptor(md.desc, version, provider);
                    });

            mixinNode.methods.removeIf(next -> next.name.equals("<init>") && next.desc.equals("()V"));

            final ListIterator<String> interfaces = mixinNode.interfaces.listIterator();
            while (interfaces.hasNext()) {
                String interfaceName = interfaces.next();
                version.getMapping(interfaceName).flatMap(provider::lookupClassName).ifPresent(interfaces::set);
            }

            version.getMapping(mixinNode.superName).flatMap(provider::lookupClassName).ifPresent(name -> mixinNode.superName = name);


        });
    }

    /**
     * Get information about a patched method.
     *
     * @param methodNode the patched method to analyse.
     * @return the {@link MethodInfo} derived from this method.
     */
    public MethodInfo getMethodInfo(MethodNode methodNode) {
        final MethodInfo methodInfo = new MethodInfo();
        int callbackInfoPosition = -1;
        final Type[] argumentTypes = Type.getType(methodNode.desc).getArgumentTypes();
        for (int argumentIndex = 0; argumentIndex < argumentTypes.length; argumentIndex++) {
            final String internalName = argumentTypes[argumentIndex].getInternalName();
            if (internalName.contains("CallbackInfo")) {
                if (internalName.endsWith("Returnable")) {
                    methodInfo.setReturnable(true);
                }
                callbackInfoPosition = argumentIndex + 1;
                break;
            }
        }

        if (callbackInfoPosition != -1) {
            if (argumentTypes.length - 1 > callbackInfoPosition) {
                methodInfo.setLocals(true);
            }
            final ListIterator<AbstractInsnNode> iterator = methodNode.instructions.iterator();
            while (iterator.hasNext()) {
                AbstractInsnNode instruction = iterator.next();
                if (instruction.getOpcode() == Opcodes.ALOAD) {
                    VarInsnNode varInsnNode = (VarInsnNode) instruction;
                    if (varInsnNode.var == callbackInfoPosition) {
                        final AbstractInsnNode next = iterator.next();
                        if (next.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                            MethodInsnNode methodInsnNode = (MethodInsnNode) next;
                            if (methodInsnNode.owner.contains("CallbackInfo") && methodInsnNode.name.equals("cancel")) {
                                methodInfo.setCancellable(true);
                            }
                        }
                    }
                }
            }
        } // we should probably complain if this is false.
        return methodInfo;
    }

    // todo skip type instructions for CallbackInfo, and other calls.
    // todo check field redirects...
    private AnnotationNode getRedirectAt(InsnList original, InsnList patched, MethodNode mergedRedirectMethod, MethodNode redirectMethod) {
        final AnnotationNode annotationNode = new AnnotationNode(AT);
        annotationNode.visit("value", "INVOKE");
        // todo find the method target using the patched instruction list.
        annotationNode.visit("target", "FIND_ME");
        return annotationNode;
    }

    /*
        NEW org/spongepowered/asm/mixin/injection/callback/CallbackInfo<Returnable>
        DUP
        LDC "name" <mojang method name that the mixin method is targeting>
        ICONST_1   <cancellable state of this mixin method>
        INVOKESPECIAL org/spongepowered/asm/mixin/injection/callback/CallbackInfo<Returnable>.<init>(Ljava/lang/String;Z)V
        ASTORE ci  <callback info local name>
        ALOAD this
        ALOAD ...  <locals, if applicable>
        ALOAD ci   <callback info local name>
        INVOKEVIRTUAL impl$name(L...;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo<Returnable>;)V
     */

    private AnnotationNode getInjectAt(InsnList original, InsnList patched, MethodNode mergedHandlerMethod, MethodNode handlerMethod) {

        final AnnotationNode annotation = new AnnotationNode(AT);

        if (handlerMethod.name.endsWith("$head")) {
            annotation.visit("value", "HEAD");
        } else if (handlerMethod.name.endsWith("$tail")) {
            annotation.visit("value", "TAIL");
        } else {
            int index = 0;
            int stage = 0;

            for (AbstractInsnNode insnNode : patched) {
                if (insnNode instanceof TypeInsnNode) {
                    final TypeInsnNode typeInsnNode = (TypeInsnNode) insnNode;
                    if (typeInsnNode.desc.contains("CallbackInfo")) {
                        stage = 1;
                    }
                } else if (stage == 1 && insnNode instanceof LdcInsnNode) {
                    stage = 2;
                } else if (stage == 2 && insnNode.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                    stage = 0;
                    final MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;
                    if (methodNameDescriptorMatch(mergedHandlerMethod, methodInsnNode.name, methodInsnNode.desc)) {
                        if (patched.size() == index + 1) {
                            System.out.println("TAIL");
                            annotation.visit("value", "TAIL");
                        } else {
//                            for (int i = index; i < index + 3; i++) {
//                                final AbstractInsnNode ahead = patched.get(i);
//
//                            }
                        }

                    }
                }
                index++;
            }
        }

        return annotation;
    }

    // todo this should be called when all of the classes have been processed.
    /**
     * Retargets instructions from a patched method to a mixin method member.
     *
     * @param version      the lunar version instance.
     * @param provider     the mapping provider.
     * @param instructions the instructions list of the patched method.
     * @param patchedClass the patched class node.
     * @param originalName the ProGuard name of the minecraft class.
     * @param mixinClass   the mixin class.
     * @param fields       the map of Lunar obfuscated field names to their corresponding ProGuard field nodes.
     * @param methods      the map of Lunar obfuscated method names to their corresponding ProGuard method nodes.
     */
    private void retargetInstructionList(LunarVersion version, MappingProvider provider, InsnList instructions, ClassNode patchedClass, String originalName, ClassNode mixinClass, Map<String, FieldNode> fields, Map<String, MethodNode> methods) {
        instructions.forEach(instruction -> {

            if (instruction.getType() == AbstractInsnNode.FIELD_INSN) {
                FieldInsnNode fieldInsnNode = (FieldInsnNode) instruction;
                if (fieldInsnNode.owner.equals(patchedClass.name)) {
                    // set mojang name of the field if applicable, else fall-back to SRG.
                    provider.lookupFieldName(originalName, fields.get(fieldInsnNode.name)).ifPresent(name -> fieldInsnNode.name = name);
                    version.getMapping(patchedClass.superName)
                            .ifPresent(superName -> version.getMcClass(superName)
                                    .ifPresent(superNode -> version.getPatchedMClass(patchedClass.superName)
                                            .ifPresent(patchedSuperNode -> patchedSuperNode.fields.stream()
                                                    .filter(md -> md.name.equals(fieldInsnNode.name)).findFirst().ifPresent(patchedFieldNode -> {
                                                        final int patchedSuperIndex = patchedSuperNode.fields.indexOf(patchedFieldNode);
                                                        final FieldNode fieldNode = superNode.fields.get(patchedSuperIndex);
                                                        provider.lookupFieldName(superName, fieldNode).ifPresent(name -> fieldInsnNode.name = name);
                                                    }))));
                    // retarget the field instruction to the mixin class.
                    fieldInsnNode.owner = mixinClass.name;
                    if (fieldInsnNode.desc.length() > 1) {
                        final String stripped = fieldInsnNode.desc.substring(1, fieldInsnNode.desc.length() - 1);
                        if (stripped.startsWith("net/minecraft/v")) { // obfuscated class name
                            version.getMapping(stripped).flatMap(provider::lookupClassName).ifPresent(mappedName -> fieldInsnNode.desc = "L" + mappedName + ";");
                        }
                    }
                } else {
                    if (fieldInsnNode.owner.startsWith("net/minecraft/v")) { // obfuscated class name
                        version.getMapping(fieldInsnNode.owner).ifPresent(ownerProguardMapping -> {
                            version.getPatchedMClass(fieldInsnNode.owner).ifPresent(patchedOwner -> {
                                patchedOwner.fields.stream().filter(f -> f.name.equals(fieldInsnNode.name)).findFirst().ifPresent(field -> {
                                    int index = patchedOwner.fields.indexOf(field);
                                    version.getMcClass(ownerProguardMapping).ifPresent(mappedOwner -> {
                                        // todo should never fail..
                                        if (mappedOwner.fields.size() - 1 < index) {
                                            System.err.println("[ERROR] Field mapping failed:");
                                            System.err.println("\tField Owner: " + provider.lookupClassName(ownerProguardMapping).get());
                                            System.err.println("\tField Index: " + index);
                                            System.err.println("\tPatched Field Descriptor: " + field.desc);
                                            return;
                                        }
                                        final FieldNode fieldNode = mappedOwner.fields.get(index);
                                        provider.lookupFieldName(ownerProguardMapping, fieldNode).ifPresent(name -> fieldInsnNode.name = name);
                                        provider.lookupClassName(Type.getType(fieldNode.desc).getInternalName()).ifPresent(mappedName -> fieldInsnNode.desc = "L" + mappedName + ";");
                                    });
                                });
                            });
                            provider.lookupClassName(ownerProguardMapping).ifPresent(mojangOwnerMapping -> {
                                fieldInsnNode.owner = mojangOwnerMapping;
                            });
                        });
                    }
                }
            }

            if (instruction.getOpcode() == Opcodes.INVOKEDYNAMIC) {
                final InvokeDynamicInsnNode invokeDynamic = (InvokeDynamicInsnNode) instruction;

                int index = 0;
                for (Object bsmArg : invokeDynamic.bsmArgs) {

                    if (bsmArg instanceof Handle) {
                        final Handle bsm = (Handle) bsmArg;
                        final int tag = bsm.getTag();

                        String[] data = {bsm.getOwner(), bsm.getName(), bsm.getDesc()};
                        final boolean isInterface = bsm.isInterface();

                        if (data[0].equals(patchedClass.name)) {
                            data[0] = mixinClass.name;
                        } else {
                            version.getMapping(data[0]).flatMap(provider::lookupClassName).ifPresent(mapping -> data[0] = mapping);
                        }

                        if (data[1].contains("lambda$")) {
                            data[1] = data[1].substring(data[1].indexOf('$') + 1, data[1].lastIndexOf('$'));
                        }

                        data[2] = unmapPatchedMethodDescriptor(data[2], version, provider);

                        invokeDynamic.bsmArgs[index] = new Handle(tag, data[0], data[1], data[2], isInterface);
                    } else if (bsmArg instanceof Type) {
                        invokeDynamic.bsmArgs[index] = Type.getType(unmapPatchedMethodDescriptor(bsmArg.toString(), version, provider));
                    }

                    index++;
                }

                invokeDynamic.desc = unmapPatchedMethodDescriptor(invokeDynamic.desc, version, provider);

            } else if (instruction.getType() == AbstractInsnNode.METHOD_INSN) {
                final MethodInsnNode methodInsnNode = (MethodInsnNode) instruction;

                if (methodInsnNode.owner.equals(patchedClass.name)) {

                    MethodNode node = methods.get(methodInsnNode.name + methodInsnNode.desc);

                    if (node == null) {
                        final Optional<MethodNode> optional = patchedClass.methods.stream().filter(md -> md.name.equals(methodInsnNode.name)).findFirst();
                        if (optional.isPresent()) {
                            node = optional.get();
                        }
                    }

                    if (node != null && node.visibleAnnotations != null && node.visibleAnnotations.stream().anyMatch(a -> a.desc.equals(PROXY))) {
                        final String name = getVisibleAnnotationValue(node, 0);
                        methodInsnNode.name = stripProxyTarget(name);
                    } else {
                        // set mojang name of the method if applicable, else fall-back to SRG.
                        provider.lookupMethodName(originalName, node).ifPresent(name -> methodInsnNode.name = name);
                        version.getMapping(patchedClass.superName)
                                .ifPresent(superName -> version.getMcClass(superName)
                                        .ifPresent(superNode -> version.getPatchedMClass(patchedClass.superName)
                                                .ifPresent(patchedSuperNode -> patchedSuperNode.methods.stream()
                                                        .filter(md -> md.name.equals(methodInsnNode.name)).findFirst().ifPresent(patchedMethodNode -> {
                                                            final int patchedSuperIndex = patchedSuperNode.methods.indexOf(patchedMethodNode);
                                                            final MethodNode methodNode = superNode.methods.get(patchedSuperIndex);
                                                            provider.lookupMethodName(superName, methodNode).ifPresent(name -> methodInsnNode.name = name);
                                                        }))));
                    }
                    // re-type the method descriptor to Mojang names.
                    methodInsnNode.desc = unmapPatchedMethodDescriptor(methodInsnNode.desc, version, provider);
                    // retarget the method instruction to the mixin class.
                    methodInsnNode.owner = mixinClass.name;

                    // todo remove inner references to mixin members.
                    // todo doesn't always work since mixin method names are actual original lunar names lol...
                    if (methodInsnNode.name.contains("$")) {
                        methodInsnNode.name = strippedMethodName(methodInsnNode.name);
                    }

                } else {
                    if (methodInsnNode.owner.startsWith("net/minecraft/v")) { // obfuscated class name
                        version.getMapping(methodInsnNode.owner).ifPresent(ownerProguardMapping -> {
                            version.getPatchedMClass(methodInsnNode.owner).ifPresent(patchedOwner -> {
                                patchedOwner.methods.stream().filter(m -> m.name.equals(methodInsnNode.name) && m.desc.equals(methodInsnNode.desc)).findFirst().ifPresent(method -> {
                                    if (method.visibleAnnotations != null && method.visibleAnnotations.stream().anyMatch(a -> a.desc.equals(PROXY))) {
                                        final String name = getVisibleAnnotationValue(method, 0);
                                        methodInsnNode.name = stripProxyTarget(name);
                                    } else {
                                        int index = patchedOwner.methods.indexOf(method);
                                        version.getMcClass(ownerProguardMapping).ifPresent(mappedOwner -> {
                                            if (mappedOwner.methods.size() - 1 < index) {
                                                if (method.visibleAnnotations != null && method.visibleAnnotations.stream().anyMatch(a -> a.desc.equals(OVERWRITE))) {
                                                    System.err.println("[ERROR] SEX mapping failed:");
                                                    System.err.println("\tMethod Owner: " + provider.lookupClassName(ownerProguardMapping).get());
                                                    System.err.println("\tMethod Index: " + index);
                                                    System.err.println("\tMethod Name: " + method.name);
                                                    System.err.println("\tPatched Owner: " + patchedOwner.name);
                                                    System.err.println("\tPatched Method Descriptor: " + unmapPatchedMethodDescriptor(method.desc, version, provider));
                                                } else {
                                                    System.err.println("[ERROR] Method mapping failed:");
                                                    System.err.println("\tMethod Owner: " + provider.lookupClassName(ownerProguardMapping).get());
                                                    System.err.println("\tMethod Index: " + index);
                                                    System.err.println("\tMethod Name: " + method.name);
                                                    System.err.println("\tPatched Method Descriptor: " + unmapPatchedMethodDescriptor(method.desc, version, provider));
                                                }
                                                return;
                                            }
                                            final MethodNode methodNode = mappedOwner.methods.get(index);
                                            provider.lookupMethodName(ownerProguardMapping, methodNode).ifPresent(name -> methodInsnNode.name = name);
                                        });
                                    }
                                });
                            });
                            provider.lookupClassName(ownerProguardMapping).ifPresent(mojangOwnerMapping -> {
                                methodInsnNode.owner = mojangOwnerMapping;
                            });
                        });
                        methodInsnNode.desc = unmapPatchedMethodDescriptor(methodInsnNode.desc, version, provider);
                    }
                }
            }
            if (instruction.getType() == AbstractInsnNode.TYPE_INSN) {
                TypeInsnNode typeInsnNode = (TypeInsnNode) instruction;
                if (typeInsnNode.desc.length() > 1) { // skip primitives.
                    // re-type the descriptor to the Mojang name.
                    version.getMapping(typeInsnNode.desc).flatMap(provider::lookupClassName).ifPresent(mappedName -> typeInsnNode.desc = mappedName);
                }
            }
            if (instruction.getOpcode() == Opcodes.LDC) {
                LdcInsnNode ldcInsnNode = (LdcInsnNode) instruction;
                if (ldcInsnNode.cst instanceof Type) {
                    Type type = (Type) ldcInsnNode.cst;
                    final String internalName = type.getInternalName();
                    if (internalName.length() > 1) {
                        version.getMapping(internalName).flatMap(provider::lookupClassName).ifPresent(mappedName -> ldcInsnNode.cst = Type.getType("L" + mappedName + ";"));
                    }
                }
            }
        });
    }

    /**
     * Unmaps a Lunar obfuscated method descriptor to Mojang names.
     * e.g. (Lnet/minecraft/v1_12/abcd;)Lnet/minecraft/v1_12/efgh; -> (Lnet/minecraft/entity/Entity;)Lnet/minecraft/world/World;
     *
     * @param descriptor the obfuscated method descriptor.
     * @param lunar      the lunar version instance.
     * @param provider   the mapping provider.
     * @return the unmapped method descriptor.
     */
    private static String unmapPatchedMethodDescriptor(String descriptor, LunarVersion lunar, MappingProvider provider) {
        Type type = Type.getType(descriptor);
        int index = 0;
        Type[] types = type.getArgumentTypes();
        for (Type argumentType : types) {
            int finalIndex = index++;
            if (argumentType.getSort() == Type.ARRAY) {
                String name = argumentType.getElementType().getInternalName();
                lunar.getMapping(name).flatMap(provider::lookupClassName).ifPresent(mappedName -> {
                    StringBuilder builder = new StringBuilder();
                    for (int i = 0; i < argumentType.getDimensions(); i++) {
                        builder.append("[");
                    }
                    builder.append("L").append(mappedName).append(";");
                    types[finalIndex] = Type.getType(builder.toString());
                });
            } else if (argumentType.getSort() == Type.OBJECT) {
                String name = argumentType.getInternalName();
                lunar.getMapping(name).flatMap(provider::lookupClassName)
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

        String name = lunar.getMapping(returnType.getInternalName()).flatMap(provider::lookupClassName)
                .orElseGet(returnType::getInternalName);

        if (name.length() == 1 || (name.startsWith("L") && name.endsWith(";"))) {
            methodDescriptorBuilder.append(name);
        } else {
            methodDescriptorBuilder.append("L").append(name).append(";");
        }

        return methodDescriptorBuilder.toString();
    }

    private static String unmapProguardMethodDescriptor(String descriptor, MappingProvider provider) {
        Type type = Type.getType(descriptor);
        int index = 0;
        Type[] types = type.getArgumentTypes();
        for (Type argumentType : types) {
            int finalIndex = index++;
            if (argumentType.getSort() == Type.ARRAY) {
                String name = argumentType.getElementType().getInternalName();
                provider.lookupClassName(name).ifPresent(mappedName -> {
                    StringBuilder builder = new StringBuilder();
                    for (int i = 0; i < argumentType.getDimensions(); i++) {
                        builder.append("[");
                    }
                    builder.append("L").append(mappedName).append(";");
                    types[finalIndex] = Type.getType(builder.toString());
                });
            } else if (argumentType.getSort() == Type.OBJECT) {
                String name = argumentType.getInternalName();
                provider.lookupClassName(name)
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

        String name = provider.lookupClassName(returnType.getInternalName())
                .orElseGet(returnType::getInternalName);

        if (name.length() == 1 || (name.startsWith("L") && name.endsWith(";"))) {
            methodDescriptorBuilder.append(name);
        } else {
            methodDescriptorBuilder.append("L").append(name).append(";");
        }

        return methodDescriptorBuilder.toString();
    }

    private static String unmapProguardToPatchedMethodDescriptor(String descriptor, LunarVersion version, MappingProvider provider) {
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

    protected InsnList clone(InsnList source, InsnList destination) {
        final InsnList clone = InstructionUtil.cloneInsnList(source);
        destination.add(clone);
        return clone;
    }

    // todo.

    /**
     * Find the {@code @Overwrite} method in the mixin class.
     * Pair members:
     * First: The Mixin's {@link MethodNode}, can be null if no mixin class was provided.
     * Second: The {@link MethodNode} from the original classNode that is being overwritten.
     *
     * @param patched          the patched class node that contains the {@code patchedOverwrite}.
     * @param mixin            the mixin class to find the method in.
     * @param patchedOverwrite the patched overwrite method in the patched class.
     * @return {@link Optional#empty()} if no matching method was found, or the method pair in an {@link Optional}.
     */
    protected Optional<MethodNode> getOverwriteMethod(ClassNode patched, ClassNode mixin, MethodNode patchedOverwrite, LunarVersion version, MappingProvider provider) {
        int patchedOverwriteIndex = patched.methods.indexOf(patchedOverwrite);

        int firstMemberIndex = -1;

        int index = 0;
        for (MethodNode method : patched.methods) {
            final Optional<String> mixin1 = getMixin(method);
            if (mixin1.isPresent()) {
                if (mixin1.get().equals(mixin.name.replace("/", "."))) {
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

        if (mixinMemberIndex > mixin.methods.size() || 0 > mixinMemberIndex) {
            System.err.println("Critical error while remapping @Overwrite for method " + patchedOverwrite.name);
        } else {
            final MethodNode mixinOverwriteMethod = mixin.methods.get(mixinMemberIndex);
            if (mixinOverwriteMethod.name.matches("[a-z]{25}")) {

                AtomicBoolean remapped = new AtomicBoolean(false);

                mixin.interfaces.stream().map(version::getPatchedMClass).filter(Optional::isPresent).map(Optional::get).forEach(itfNode -> {
                    if (remapped.get()) return;
                    itfNode.methods.stream().filter(md -> methodNameDescriptorMatch(md, mixinOverwriteMethod)).findFirst().ifPresent(md -> {
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
                        superNode.methods.stream().filter(md -> methodNameDescriptorMatch(md, mixinOverwriteMethod)).findFirst().ifPresent(md -> {
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

    protected Optional<MethodNode> findProxyMethod(ClassNode original, String proxyTarget, MappingProvider provider) {
        return original.methods.stream().filter(md -> {
            final Optional<String> s = provider.lookupMethodName(original.name, md);
            return s.isPresent() && s.get().equals(stripProxyTarget(proxyTarget));
        }).findFirst();
    }

    protected int getFirstMethodCallIndex(MethodNode methodNode, MethodNode target, String targetOwner) {
        int calls = -1;
        //InsnList list = stripMixinInstructions(methodNode);
        for (AbstractInsnNode instruction : methodNode.instructions) {
            if (instruction.getType() == AbstractInsnNode.METHOD_INSN) {
                MethodInsnNode methodInsnNode = (MethodInsnNode) instruction;
                if (methodInsnNode.name.equals(target.name) && methodInsnNode.owner.equals(targetOwner) && methodInsnNode.desc.equals(target.desc)) {
                    calls = methodNode.instructions.indexOf(instruction);
                }
            }
        }
        return calls;
    }

    /**
     * Strip the proxy target to its method name.
     * e.g. renderString(Ljava/lang/String;FFIZ)I -> renderString
     *
     * @param proxyTarget the raw proxy target string.
     * @return the stripped proxy target.
     */
    protected String stripProxyTarget(String proxyTarget) {
        if (proxyTarget.indexOf('(') != -1) {
            return proxyTarget.substring(0, proxyTarget.indexOf('('));
        }
        return proxyTarget;
    }

    protected InsnList stripMixinInstructions(MethodNode methodNode) {
        final InsnList clone = InstructionUtil.cloneInsnList(methodNode.instructions);
        final ListIterator<AbstractInsnNode> iterator = clone.iterator();

        // Label label = null;
        int index = 0;
        boolean deleteOnNextLabel = false;
        while (iterator.hasNext()) {
            AbstractInsnNode insnNode = iterator.next();
            if (insnNode.getType() == AbstractInsnNode.LABEL) {
                //label = ((LabelNode) insnNode).getLabel();
                if (deleteOnNextLabel) {
                    iterator.remove();
                    while (index-- > 1) {
                        iterator.previous();
                        iterator.remove();
                    }
                    deleteOnNextLabel = false;
                }
                index = 0;
                continue;
            }

            if (insnNode.getOpcode() == Opcodes.NEW) {
                TypeInsnNode typeInsnNode = (TypeInsnNode) insnNode;
                if (typeInsnNode.desc.startsWith("org/spongepowered/asm/mixin/injection/callback/CallbackInfo")) {
                    deleteOnNextLabel = true;
                }
            }

            index++;
        }
        return clone;
    }

    private boolean isProxyMethod(MethodNode methodNode) {
        return methodNode.visibleAnnotations != null && Objects.equals(methodNode.visibleAnnotations.get(0).desc, PROXY);
    }

    private boolean isOverwriteMethod(MethodNode methodNode) {
        return methodNode.visibleAnnotations != null && Objects.equals(methodNode.visibleAnnotations.get(0).desc, OVERWRITE);
    }

    private boolean isVanillaMergedMethod(MethodNode methodNode) {
        return methodNode.visibleAnnotations != null && Objects.equals(methodNode.visibleAnnotations.get(0).desc, MIXIN_MERGED);
    }

    private boolean isMergedMethod(MethodNode methodNode) {
        return methodNode.visibleAnnotations != null && methodNode.visibleAnnotations.stream().anyMatch(a -> a.desc.equals(MIXIN_MERGED));
    }

    protected Optional<String> getMixin(MethodNode methodNode) {
        if (methodNode.visibleAnnotations != null) {
            return methodNode.visibleAnnotations.stream().filter(a -> a.desc.equals(MIXIN_MERGED)).findFirst().map(a -> (String) a.values.get(1));
        }
        return Optional.empty();
    }

    private String strippedMethodName(String name) {
        if (name.split("\\$").length == 2) {
            return name.split("\\$")[1];
        }
        return name.substring(name.indexOf(name.split("\\$")[2]));
    }

    private boolean methodNameDescriptorMatch(MethodNode methodNode1, MethodNode methodNode2) {
        return methodNameDescriptorMatch(methodNode1, methodNode2.name, methodNode1.desc);
    }

    private boolean methodNameDescriptorMatch(MethodNode methodNode1, String name, String desc) {
        return methodNode1.name.equals(name) && methodNode1.desc.equals(desc);
    }

    private String getVisibleAnnotationValue(MethodNode methodNode, int annotationOffset) {
        return ((String) methodNode.visibleAnnotations.get(annotationOffset).values.get(1)).replace(".", "/");
    }

    public String generateJson() {
        final JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("required", true);
        jsonObject.addProperty("compatibilityLevel", "JAVA_8");
        jsonObject.addProperty("package", MIXIN_PACKAGE);
        jsonObject.addProperty("refmap", MIXIN_CONFIG_FILE);
        jsonObject.addProperty("verbose", true);
        final JsonArray mixinArray = new JsonArray();
        for (String mixin : mixins) {
            mixinArray.add(mixin.substring(MIXIN_PACKAGE.length() + 1).replace("/", "."));
        }
        jsonObject.add("mixins", mixinArray);
        return new GsonBuilder().setPrettyPrinting().create().toJson(jsonObject);
    }

    public void postTransform(LunarVersion version) {
        version.addResource(MIXIN_CONFIG_FILE, generateJson().getBytes());
        String tweakerName = MIXIN_PACKAGE.concat(".").concat(MIXIN_TWEAKER_NAME).replace(".", "/");
        version.addResource(tweakerName.concat(".class"), TweakerGenerator.generate(tweakerName, MIXIN_CONFIG_FILE, version.isUsingOptiFine()));
    }
}
