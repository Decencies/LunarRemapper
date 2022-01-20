package club.decencies.remapper.lunar.pipeline.mixin.processor.type;

import club.decencies.remapper.lunar.mappings.MappingProvider;
import club.decencies.remapper.lunar.pipeline.LunarVersion;
import club.decencies.remapper.lunar.pipeline.mixin.MixinDefinitions;
import club.decencies.remapper.lunar.pipeline.mixin.processor.MethodProcessor;

import org.objectweb.asm.tree.*;

import java.util.Map;
import java.util.Optional;

public class ProxyMethodProcessor extends MethodProcessor {

    // todo explicit exceptions.
    @Override
    public void process(MethodNode mergedMethod, ClassNode mixinNode, ClassNode patchedNode, ClassNode originalNode, LunarVersion version, MappingProvider provider, Map<String, FieldNode> fields, Map<String, MethodNode> methods) {
        String proxyTarget = getProxyTarget(mergedMethod);
        mixinNode.methods.stream().filter(md -> md.name.equals("proxy$" + stripProxyTarget(proxyTarget))).findFirst().ifPresent(proxyMethod -> {
            final Optional<MethodNode> first = patchedNode.methods.stream().filter(md -> md.name.equals("original$" + stripProxyTarget(proxyTarget))).findFirst();
            if (first.isPresent()) {
                MethodNode originalMethod = first.get();
                // switch the methods around (makes index based mapping just that little easier)
                {
                    final int proxyIndex = patchedNode.methods.indexOf(mergedMethod);
                    final int originalIndex = patchedNode.methods.indexOf(originalMethod);

                    patchedNode.methods.remove(originalMethod);

                    if (proxyIndex > patchedNode.methods.size()) {
                        patchedNode.methods.set(proxyIndex, originalMethod);
                    } else {
                        patchedNode.methods.add(originalMethod);
                    }

                    patchedNode.methods.remove(mergedMethod);
                    patchedNode.methods.set(originalIndex, mergedMethod);
                }

                clone(mergedMethod.instructions, proxyMethod.instructions);
                proxyMethod.visitAnnotation(MixinDefinitions.PROXY, true).visit("value", proxyTarget);
            } else {
                final int originalProxyMethodIndex = findOriginalProxyMethodIndex(patchedNode, originalNode, stripProxyTarget(proxyTarget), provider);
                if (originalProxyMethodIndex == -1) {
                    System.err.println("Critical error finding original proxy method...");
                } else {
                    patchedNode.methods.remove(mergedMethod);
                    patchedNode.methods.add(originalProxyMethodIndex, mergedMethod);

                    clone(mergedMethod.instructions, proxyMethod.instructions);
                    proxyMethod.visitAnnotation(MixinDefinitions.PROXY, true).visit("value", proxyTarget);
                }
            }
        });
    }

    protected int findOriginalProxyMethodIndex(ClassNode patchedNode, ClassNode originalNode, String strippedProxyTarget, MappingProvider provider) {
        for (MethodNode method : originalNode.methods) {
            final Optional<String> optionalMappedName = provider.lookupMethodName(originalNode.name, method);
            if (optionalMappedName.isPresent()) {
                final String mappedName = optionalMappedName.get();
                if (mappedName.equals(strippedProxyTarget)) {
                    return originalNode.methods.indexOf(method);
                }
            }
        }
        return -1;
    }

    protected String getProxyTarget(MethodNode methodNode) {
        if (methodNode.visibleAnnotations != null) {
            final Optional<AnnotationNode> first =
                    methodNode.visibleAnnotations.stream().filter(an -> an.desc.equals(MixinDefinitions.PROXY)).findFirst();
            if (first.isPresent()) {
                return (String) first.get().values.get(1);
            }
        }
        return null;
    }

    protected String stripProxyTarget(String proxyTarget) {
        if (proxyTarget.indexOf('(') != -1) {
            return proxyTarget.substring(0, proxyTarget.indexOf('('));
        }
        return proxyTarget;
    }

}
