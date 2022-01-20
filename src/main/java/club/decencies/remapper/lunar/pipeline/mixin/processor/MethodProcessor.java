package club.decencies.remapper.lunar.pipeline.mixin.processor;

import club.decencies.remapper.lunar.mappings.MappingProvider;
import club.decencies.remapper.lunar.pipeline.LunarVersion;
import club.decencies.remapper.lunar.pipeline.mixin.MixinDefinitions;
import club.decencies.remapper.lunar.util.InstructionUtil;
import org.objectweb.asm.tree.*;

import java.util.Map;
import java.util.Optional;

public abstract class MethodProcessor {

    public abstract void process(MethodNode mergedMethod, ClassNode mixinNode, ClassNode patchedNode, ClassNode originalNode, LunarVersion version, MappingProvider provider, Map<String, FieldNode> fields, Map<String, MethodNode> methods);

    protected String getMixin(MethodNode methodNode) {
        if (methodNode.visibleAnnotations != null) {
            final Optional<AnnotationNode> first =
                    methodNode.visibleAnnotations.stream().filter(an -> an.desc.equals(MixinDefinitions.MIXIN_MERGED)).findFirst();
            if (first.isPresent()) {
                return ((String) first.get().values.get(1)).replaceAll("\\.", "/");
            }
        }
        return null;
    }

    protected InsnList clone(InsnList source, InsnList destination) {
        final InsnList clone = InstructionUtil.cloneInsnList(source);
        destination.add(clone);
        return clone;
    }

    protected String strippedMethodName(String name) {
        if (name.split("\\$").length == 2) {
            return name.split("\\$")[1];
        }
        return name.substring(name.indexOf(name.split("\\$")[2]));
    }

}
