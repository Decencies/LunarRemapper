package club.decencies.remapper.lunar.pipeline.mixin.processor.type;

import club.decencies.remapper.lunar.mappings.MappingProvider;
import club.decencies.remapper.lunar.pipeline.LunarVersion;
import club.decencies.remapper.lunar.pipeline.mixin.processor.MethodProcessor;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Map;

public class ModifyVariableMethodProcessor extends MethodProcessor {

    @Override
    public void process(MethodNode mergedMethod, ClassNode mixinNode, ClassNode patchedNode, ClassNode originalNode, LunarVersion version, MappingProvider provider, Map<String, FieldNode> fields, Map<String, MethodNode> methods) {
        mixinNode.methods.stream().filter(md -> md.name.equals(strippedMethodName(mergedMethod.name))).findFirst().ifPresent(modifyVarMethod -> {
            if (modifyVarMethod.visibleAnnotations == null) {
                throw new RuntimeException(String.format("@ModifyVariable missing from %s.%s", mixinNode.name, modifyVarMethod.name));
            }
            clone(mergedMethod.instructions, modifyVarMethod.instructions);
        });
    }

}
