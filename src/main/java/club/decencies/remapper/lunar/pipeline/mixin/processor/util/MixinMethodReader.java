package club.decencies.remapper.lunar.pipeline.mixin.processor.util;

import club.decencies.remapper.lunar.util.InstructionUtil;
import org.objectweb.asm.Label;
import org.objectweb.asm.tree.*;

import java.util.List;

public class MixinMethodReader {

    /**
     * Removes the injected instructions from Mixin members from the patched method.
     */
    public InsnList removeInjectedInstructionsFrom(MethodNode patchedMethod, List<String> injectedMethodNames) {

        InsnList list = InstructionUtil.cloneInsnList(patchedMethod.instructions);

        if (injectedMethodNames.size() == 0) {
            System.err.println("Redundant call to removeInjectedInstructionsFrom without any injected method names.");
            return list;
        }

        int labelStart = -1;
        boolean expectedDeletion = false;

        for (AbstractInsnNode instruction : list) {
            if (instruction.getType() == AbstractInsnNode.LABEL) {
                if (expectedDeletion) {
                    int index = list.indexOf(instruction);
                    for (int i = labelStart; i < index; i++) {
                        list.remove(list.get(i));
                    }
                } else labelStart = list.indexOf(instruction);
            }
            if (labelStart > 0) {
                if (instruction.getType() == AbstractInsnNode.METHOD_INSN) {
                    MethodInsnNode methodInsnNode = (MethodInsnNode) instruction;
                    if (injectedMethodNames.contains(methodInsnNode.name)) {
                        expectedDeletion = true;
                    }
                }
            }
        }
        return list;
    }

}
