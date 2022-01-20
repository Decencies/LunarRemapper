package club.decencies.remapper.lunar.pipeline.mixin.processor.util;

import club.decencies.remapper.lunar.mappings.MappingProvider;
import club.decencies.remapper.lunar.pipeline.LunarVersion;
import org.objectweb.asm.Type;

public class MappingUtil {

    /**
     * Unmaps a Lunar obfuscated method descriptor to Mojang names.
     * e.g. (Lnet/minecraft/v1_12/abcd;)Lnet/minecraft/v1_12/efgh; -> (Lnet/minecraft/entity/Entity;)Lnet/minecraft/world/World;
     *
     * @param descriptor the obfuscated method descriptor.
     * @param lunar      the lunar version instance.
     * @param provider   the mapping provider.
     * @return the unmapped method descriptor.
     */
    public static String unmapPatchedMethodDescriptor(String descriptor, LunarVersion lunar, MappingProvider provider) {
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


}
