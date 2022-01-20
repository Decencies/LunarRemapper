package club.decencies.remapper.lunar.pipeline.mixin.processor.type;

import club.decencies.remapper.lunar.mappings.MappingProvider;
import club.decencies.remapper.lunar.pipeline.LunarVersion;
import club.decencies.remapper.lunar.pipeline.mixin.processor.MethodProcessor;
import club.decencies.remapper.lunar.pipeline.mixin.processor.util.ClassPredicates;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

import java.util.Map;

public class BridgeMethodProcessor extends MethodProcessor {

    @Override
    public void process(MethodNode mergedMethod, ClassNode mixinNode, ClassNode patchedNode, ClassNode originalNode, LunarVersion version, MappingProvider provider, Map<String, FieldNode> fields, Map<String, MethodNode> methods) {
        mixinNode.methods.stream().filter(ClassPredicates.mndm(mergedMethod)).findFirst().ifPresent(bridgeMethod -> {
            InsnList clone = clone(mergedMethod.instructions, bridgeMethod.instructions);
            //retargetInstructionList(version, provider, clone, patchedNode, originalNode.name, mixinNode, fields, methods);
        });
    }

}
