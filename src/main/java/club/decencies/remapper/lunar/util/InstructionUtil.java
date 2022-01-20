package club.decencies.remapper.lunar.util;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;

import java.util.HashMap;
import java.util.Map;

public class InstructionUtil {

    public static Map<LabelNode, LabelNode> cloneLabels(InsnList insns) {
        HashMap<LabelNode, LabelNode> labelMap = new HashMap<>();
        for (AbstractInsnNode insn = insns.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getType() == AbstractInsnNode.LABEL) {
                labelMap.put((LabelNode) insn, new LabelNode());
            }
        }
        return labelMap;
    }

    public static InsnList cloneInsnList(InsnList insns) {
        return cloneInsnList(cloneLabels(insns), insns);
    }

    public static InsnList cloneInsnList(Map<LabelNode, LabelNode> labelMap, InsnList insns) {
        InsnList clone = new InsnList();
        for (AbstractInsnNode insn = insns.getFirst(); insn != null; insn = insn.getNext()) {
            clone.add(insn.clone(labelMap));
        }

        return clone;
    }

}
