package club.decencies.remapper.lunar.pipeline.mixin.processor.type;

import club.decencies.remapper.lunar.mappings.MappingProvider;
import club.decencies.remapper.lunar.pipeline.LunarVersion;
import club.decencies.remapper.lunar.pipeline.mixin.MixinDefinitions;
import club.decencies.remapper.lunar.pipeline.mixin.processor.MethodProcessor;

import club.decencies.remapper.lunar.pipeline.mixin.processor.util.ClassPredicates;
import club.decencies.remapper.lunar.pipeline.type.MethodInfo;
import club.decencies.remapper.lunar.util.ClassUtil;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.stream.Collectors;

public class HandlerMethodProcessor extends MethodProcessor {

    @Override
    public void process(MethodNode mergedMethod, ClassNode mixinNode, ClassNode patchedNode, ClassNode originalNode, LunarVersion version, MappingProvider provider, Map<String, FieldNode> fields, Map<String, MethodNode> methods) {
        MethodNode handlerMethod = mixinNode.methods.stream()
                .filter(ClassPredicates.mndm(strippedMethodName(mergedMethod.name), mergedMethod.desc))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Could not find handler method %s in mixin %s."));

        List<MethodNode> callers = ClassUtil.getCallers(mergedMethod, patchedNode).stream()
                .filter(caller -> !caller.name.contains("$")).collect(Collectors.toList());

        if (callers.size() == 0) {
            //clone(mergedMethod.instructions, handlerMethod.instructions);
            System.err.printf("[HANDLER] Merged method %s.%s%s does not have any callers.%n", patchedNode.name, mergedMethod.name, mergedMethod.desc);
            System.err.printf("\tMixin Handler Method: %s%s%n", handlerMethod.name, handlerMethod.desc);
            return;
            //throw new RuntimeException("Could not find caller for %s in %s.");
        }

        List<String> mappedCallers = new ArrayList<>();

        final AnnotationVisitor visitor = handlerMethod.visitAnnotation(MixinDefinitions.INJECT, true);

        for (MethodNode caller : callers) {
            int callerIndex = patchedNode.methods.indexOf(caller);

            // todo if caller
            if (callerIndex != -1 && callerIndex >= originalNode.methods.size()) {
//
//                final List<MethodNode> nestedCallers = ClassUtil.getCallers(caller, patchedNode);
//
//                if (nestedCallers.size() > 1) {
//                    System.err.printf("[HANDLER] There are multiple callers for callee %s.%s%s (handler: %s%s)%n", patchedNode.name, caller.name, caller.desc, handlerMethod.name, handlerMethod.desc);
//                } else {
//                    caller = nestedCallers.get(0);
//                    callerIndex = patchedNode.methods.indexOf(caller);
//                    if (callerIndex != -1 && callerIndex >= originalNode.methods.size()) {
//                        System.err.printf("[HANDLER] Nested caller %s.%s%s outside of original method stack (handler: %s%s)%n.%n", patchedNode.name, caller.name, caller.desc, handlerMethod.name, handlerMethod.desc);
//                    }
//                }
                    System.err.printf("[HANDLER] Possible modified minecraft version... (missing %s method counterpart in minecraft JAR)\n", caller.name);
                    System.out.println("\tMixin: " + mixinNode.name);
                    System.out.println("\tObfuscated target class: " + patchedNode.name);
                    System.out.println("\tCaller: " + caller.name);
                    System.out.println("\tHandler Name: " + mergedMethod.name);
                    System.out.println("\tVanilla targetClass: " + originalNode.name);
                    System.out.println("\tPatched by OptiFine: " + version.getOptiFinePatch(originalNode.name).isPresent());
                    return;
            }

            MethodNode original = originalNode.methods.get(callerIndex);

            provider.lookupMethodName(originalNode.name, original).ifPresent(mappedCallers::add);

            if (mappedCallers.size() == 1) {
                visitor.visit("at", getInjectAt(original.instructions, caller.instructions, mergedMethod, handlerMethod));
            } else {
                System.err.println("Don't know how to handle multiple callers for @Inject.");
                System.err.println("\tCulprit: " + handlerMethod.name);
            }
        }


        visitor.visit("method", mappedCallers.size() == 1 ? mappedCallers.get(0) : mappedCallers.toArray(new String[0]));

        if (getMethodInfo(mergedMethod).isCancellable()) {
            visitor.visit("cancellable", true);
        }

        if (getMethodInfo(mergedMethod).isLocals()) {
            visitor.visitEnum("locals", MixinDefinitions.LOCAL_CAPTURE, "CAPTURE_FAILEXCEPTION");
        }

        //handlerMethod.name = handlerMethod.name.substring(handlerMethod.name.indexOf("$") + 1);

        clone(mergedMethod.instructions, handlerMethod.instructions);

        //                .ifPresent(handlerMethod -> {
//            ClassUtil.getCaller(mergedMethod, patchedNode).ifPresent(caller -> {
//
//                int callerIndex = patchedNode.methods.indexOf(caller);
//
//                if (callerIndex != -1 && callerIndex >= originalNode.methods.size()) {
//                    System.err.printf("[handler] Possible modified minecraft version... (missing %s method counterpart in minecraft JAR)\n", caller.name);
//                    System.out.println("Mixin: " + mixinNode.name);
//                    System.out.println("Obfuscated target class: " + patchedNode.name);
//                    System.out.println("Caller: " + caller.name);
//                    System.out.println("Handler Name: " + mergedMethod.name);
//                    System.out.println("Vanilla targetClass: " + originalNode.name);
//                    System.out.println("Patched by OptiFine: " + version.getOptiFinePatch(originalNode.name).isPresent());
//                    return;
//                }
//
//                MethodNode original = originalNode.methods.get(callerIndex);
//
//                final AnnotationVisitor visitor = handlerMethod.visitAnnotation(MixinDefinitions.INJECT, true);
//
//                provider.lookupMethodName(originalNode.name, original)
//                        .ifPresent(name -> visitor.visit("method", name));
//
//                visitor.visit("at", getInjectAt(original.instructions, caller.instructions, mergedMethod, handlerMethod));
//
//                if (getMethodInfo(mergedMethod).isCancellable()) {
//                    visitor.visit("cancellable", true);
//                }
//
//                if (getMethodInfo(mergedMethod).isLocals()) {
//                    visitor.visitEnum("locals", MixinDefinitions.LOCAL_CAPTURE, "CAPTURE_FAILEXCEPTION");
//                }
//
//                // remove impl$ prefix
//                handlerMethod.name = handlerMethod.name.substring(handlerMethod.name.indexOf("$") + 1);
//
//                clone(mergedMethod.instructions, handlerMethod.instructions);
//                //retargetInstructionList(version, provider, clone, patchedNode, originalNode.name, mixinNode, fields, methods);
//            });
//        });
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

    private AnnotationNode getInjectAt(InsnList original, InsnList patched, MethodNode mergedHandlerMethod, MethodNode handlerMethod) {
        final AnnotationNode annotation = new AnnotationNode(MixinDefinitions.AT);

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
                    if (ClassPredicates.mndm(methodInsnNode.name, methodInsnNode.desc).test(mergedHandlerMethod)) {
                        if (patched.size() == index + 1) {
                            System.out.println("RETURN");
                            annotation.visit("value", "RETURN");
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

}
