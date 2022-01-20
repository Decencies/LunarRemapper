package club.decencies.remapper.lunar.pipeline.mixin.processor.type;

import club.decencies.remapper.lunar.mappings.MappingProvider;
import club.decencies.remapper.lunar.pipeline.LunarVersion;

import club.decencies.remapper.lunar.pipeline.mixin.MixinDefinitions;
import club.decencies.remapper.lunar.pipeline.mixin.processor.MethodProcessor;
import club.decencies.remapper.lunar.util.ClassUtil;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RedirectMethodProcessor extends MethodProcessor {

    @Override
    public void process(MethodNode mergedMethod, ClassNode mixinNode, ClassNode patchedNode, ClassNode originalNode, LunarVersion version, MappingProvider provider, Map<String, FieldNode> fields, Map<String, MethodNode> methods) {
        mixinNode.methods.stream().filter(md -> md.name.equals(strippedMethodName(mergedMethod.name))).findFirst().ifPresent(redirectMethod -> {
            final List<MethodNode> callers = ClassUtil.getCallers(mergedMethod, patchedNode).stream()
                    .filter(caller -> !caller.name.contains("$")).collect(Collectors.toList());

            if (callers.size() == 0) {
                System.err.printf("[REDIRECT] Merged method %s.%s%s does not have any callers.%n", patchedNode.name, mergedMethod.name, mergedMethod.desc);
                System.err.printf("\tMixin Redirect Method: %s%s%n", redirectMethod.name, redirectMethod.desc);
                return;
            }

            List<String> mappedCallers = new ArrayList<>();

            final AnnotationVisitor visitor = redirectMethod.visitAnnotation(MixinDefinitions.REDIRECT, true);

            for (MethodNode caller : callers) {
                int callerIndex = patchedNode.methods.indexOf(caller);

                if (callerIndex >= originalNode.methods.size()) {

//                    final List<MethodNode> nestedCallers = ClassUtil.getCallers(caller, patchedNode);
//
//                    if (nestedCallers.size() > 1) {
//                        System.err.printf("[REDIRECT] There are multiple callers for callee %s.%s%s (redirect: %s%s)%n", patchedNode.name, caller.name, caller.desc, redirectMethod.name, redirectMethod.desc);
//                    } else {
//                        caller = nestedCallers.get(0);
//                        callerIndex = patchedNode.methods.indexOf(caller);
//                        if (callerIndex != -1 && callerIndex >= originalNode.methods.size()) {
//                            System.err.printf("[REDIRECT] Nested caller %s.%s%s outside of original method stack (redirect: %s%s)%n.%n", patchedNode.name, caller.name, caller.desc, redirectMethod.name, redirectMethod.desc);
//                        }
//                    }

                    System.err.printf("[REDIRECT] Possible modified minecraft version... (missing %s method counterpart in minecraft JAR)\n", caller.name);
                    System.err.println("\tMixin: " + mixinNode.name);
                    System.err.println("\tObfuscated target class: " + patchedNode.name);
                    System.err.println("\tCaller: " + caller.name);
                    System.err.println("\tHandler Name: " + mergedMethod.name);
                    System.err.println("\tVanilla targetClass: " + originalNode.name);
                    System.err.println("\tPatched by OptiFine: " + version.getOptiFinePatch(originalNode.name).isPresent());
                    return;
                }

                MethodNode original = originalNode.methods.get(callerIndex);

                provider.lookupMethodName(originalNode.name, original).ifPresent(mappedCallers::add);

                if (mappedCallers.size() == 1) {
                    //visitor.visit("at", getRedirectAt(original.instructions, mergedMethod.instructions, merge, redirectMethod));
                } else {
                    System.err.println("Don't know how to handle multiple callers for @Redirect.");
                    System.err.println("\tCulprit: " + redirectMethod.name);
                    System.err.println("\tMapped Callers: ");
                    for (String mappedCaller : mappedCallers) {
                        System.err.println("\t\t- " + mappedCaller + " [" + original.name + original.desc + "]");
                    }
                    System.err.println("\tPatched Callers: ");
                    for (MethodNode patchedCaller : callers) {
                        System.err.println("\t\t- " + patchedCaller.name + patchedCaller.desc);
                    }
                }
            }

            visitor.visit("method", mappedCallers.size() == 1 ? mappedCallers.get(0) : mappedCallers.toArray(new String[0]));

            //handlerMethod.name = handlerMethod.name.substring(handlerMethod.name.indexOf("$") + 1);

            clone(mergedMethod.instructions, redirectMethod.instructions);
        });
    }

}
