package club.decencies.remapper.lunar.pipeline.mixin;

import club.decencies.remapper.lunar.mappings.MappingProvider;
import club.decencies.remapper.lunar.pipeline.LunarVersion;
import club.decencies.remapper.lunar.pipeline.TransformationMappings;
import club.decencies.remapper.lunar.pipeline.type.TweakerGenerator;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import lombok.RequiredArgsConstructor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;

@RequiredArgsConstructor
public class MixinClassProcessor {

    private static final String MIXIN_PACKAGE = "com.moonsworth.lunar.mixin";
    private static final String BRIDGE_PACKAGE = "com.moonsworth.lunar.bridge";
    private static final String MIXIN_CONFIG_FILE = "mixin.lunar.json";
    private static final String MIXIN_TWEAKER_NAME = "LunarTweaker";

    private static final boolean DEBUG = true;
    private static final boolean REMAP_MODE = true;
    private static final boolean EXPLICIT_OVERRIDE = false;

    private static final MixinMethod[] MIXIN_METHODS = MixinMethod.values();
    private final LunarVersion version;
    private final MappingProvider provider;
    private final List<String> mixinNames = new ArrayList<>();

    public void process(ClassNode patchedNode, ClassNode originalNode, TransformationMappings transformationMappings) {
        Map<MixinMethod, List<MethodNode>> methodListMap = new HashMap<>();

        for (MethodNode method : patchedNode.methods) {
            for (MixinMethod mixinMethod : MIXIN_METHODS) {
                if (mixinMethod.matches(method)) {
                    methodListMap.computeIfAbsent(mixinMethod, v -> new ArrayList<>()).add(method);
                    break;
                }
            }
        }

        final Map<String, FieldNode> fields = new HashMap<>();
        final Map<String, MethodNode> methods = new HashMap<>();
        final List<ClassNode> mixins = new ArrayList<>();

        for (MixinMethod mixinMethod : MIXIN_METHODS) {
            final List<MethodNode> methodNodes = methodListMap.get(mixinMethod);
            if (methodNodes != null) {
                for (MethodNode methodNode : methodNodes) {
                    final ClassNode mixin = getMixin(methodNode);
                    if (mixin == null) {
                        // todo add more info...
                        throw new RuntimeException("Mixin could not be found...");
                    } else {
                        //System.out.printf("[%s] Processing %s method from %s%n", mixinMethod.name(), methodNode.name, patchedNode.name);
                        mixinMethod.getProcessor().process(methodNode, mixin, patchedNode, originalNode, version, provider, fields, methods);
                        if (!mixins.contains(mixin)) {
                            mixins.add(mixin);
                        }
                    }
                }
            }
        }

        for (int index = 0; index < originalNode.fields.size() - 1; index++) {
            FieldNode minecraftField = originalNode.fields.get(index);
            FieldNode patchedField = patchedNode.fields.get(index);
//            if (originalNode.name.equals("avs")) {
//                System.out.println(patchedField.name + " : " + provider.lookupFieldName(originalNode.name, minecraftField).get());
//            }
            fields.put(patchedField.name, minecraftField);
        }

//        if (patchedNode.name.equals("net/minecraft/v1_8/eeppaesappshsapaahahahaes")) {
//            for (int index = 0; index < patchedNode.methods.size() - 1; index++) {
//                final MethodNode methodNode = patchedNode.methods.get(index);
//                boolean proxy = methodNode.visibleAnnotations != null && methodNode.visibleAnnotations.stream().anyMatch(a -> a.desc.equals(MixinDefinitions.PROXY));
//                System.out.println("::: " + index + " " + methodNode.name + methodNode.desc + (proxy ? " (Proxy)" : ""));
//            }
//        }


        for (int index = 0; index < originalNode.methods.size() - 1; index++) {
            MethodNode minecraftMethod = originalNode.methods.get(index);
            MethodNode patchedMethod = patchedNode.methods.get(index);
            methods.put(patchedMethod.name + patchedMethod.desc, minecraftMethod);
        }

        int mixinIndex = 0;

        final String mappedName = provider.lookupClassName(originalNode.name).orElseThrow(() -> new RuntimeException("Could not resolve name of class..."));

        final String trimmedPackage = mappedName.substring(0, mappedName.lastIndexOf('/')).replace("net/minecraft/", "");

        for (ClassNode mixinNode : mixins) {

            if (REMAP_MODE || DEBUG) {
                mixinNode.visitAnnotation("Ldebug/ObfuscatedMixinTarget;", true).visit("value", patchedNode.name);
                mixinNode.visitAnnotation("Ldebug/OriginalMixinName;", true).visit("value", mixinNode.name);
            }

            // add the Mixin suffix to the mapped name. e.g. MixinItem
            String suffix = "Mixin".concat(mappedName.substring(mappedName.lastIndexOf('/') + 1));

            // append a letter to this mixin name if there is more than one mixin to this class.
            if (mixins.size() > 1) {
                suffix += "ABCDEFGHIJKLMNOPQRSTUVWXYZ".charAt(mixinIndex++);
            }

            // format the name.
            String name = MIXIN_PACKAGE.replace(".", "/")
                    .concat("/")
                    .concat(trimmedPackage)
                    .concat("/")
                    .concat(suffix);

            mixinNames.add(name);

            transformationMappings.getClassRenameMap().put(mixinNode.name, name);

            Object value = mappedName.contains("$") ? mappedName : Type.getType("L" + mappedName + ";");

            mixinNode.visitAnnotation(MixinDefinitions.MIXIN, true).visit("value", value);

            for (FieldNode field : mixinNode.fields) {
                // add shadow annotation to minecraft field members of the mixin class.
                // checks if this field name is found in the patched node, since minecraft names are re-obfed, the name will not be found.
                if (patchedNode.fields.stream().noneMatch(fdmc -> fdmc.name.equals(field.name))) {
                    field.visitAnnotation(MixinDefinitions.SHADOW, true);
                }

                // todo
                if ((field.access & Opcodes.ACC_STATIC) != 0) {
                    //System.err.println("Need to refactor static field: " + field.name + " from " + mixinNode.name + " (" + name + ")");
                }
            }

            for (MethodNode method : mixinNode.methods) {
                if ((method.access & Opcodes.ACC_ABSTRACT) != 0) {
                    method.visitAnnotation(MixinDefinitions.SHADOW, true);
                }

                final Type type = Type.getType(method.desc);

                // todo causes stackunderflow on some classes.
                if (method.name.equals("<init>") && type.getArgumentTypes().length > 0) {
                    method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    for (int pIndex = 1; pIndex < type.getArgumentTypes().length; pIndex++) {
                        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, pIndex));
                    }
                    method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, mixinNode.superName, "<init>", type.getDescriptor()));
                }

                // todo do in main mapping...
                // unmap the method descriptor of all methods in the mixin class.
              //  method.desc = MappingUtil.unmapPatchedMethodDescriptor(method.desc, version, provider);
            }

            // remove no args constructor.
            mixinNode.methods.removeIf(method -> method.name.equals("<init>") && method.desc.equals("()V"));

//            final ListIterator<String> interfaces = mixinNode.interfaces.listIterator();
//            while (interfaces.hasNext()) {
//                String interfaceName = interfaces.next();
//                version.getMapping(interfaceName).flatMap(provider::lookupClassName).ifPresent(interfaces::set);
//            }
//
//            version.getMapping(mixinNode.superName).flatMap(provider::lookupClassName).ifPresent(mcSuperName -> mixinNode.superName = mcSuperName);
        }

        boolean hasBridges = false;

        if (patchedNode.interfaces != null) {
            for (String face : patchedNode.interfaces) {
                if (face.startsWith("lunar/") && !transformationMappings.getClassRenameMap().containsKey(face)) {

                    String suffix = mappedName.substring(mappedName.lastIndexOf('/') + 1) + "Bridge";
//
//                    if (patchedNode.interfaces.size() > 1) {
//                        suffix += "ABCDEFGHIJKLMNOPQRSTUVWXYZ".charAt(++bridgeIndex);
//                    }

                    String name = BRIDGE_PACKAGE.replace(".", "/")
                            .concat("/")
                            .concat(trimmedPackage)
                            .concat("/")
                            .concat(suffix);

                    if (transformationMappings.getClassRenameMap().containsValue(name)) {
                        continue;
                    }

                    transformationMappings.getClassRenameMap().put(face, name);

                    hasBridges = true;
                }
            }
        }

//        if (hasBridges && mixins.size() == 0) {
//            for (ClassNode cls : version.getClasses()) {
//                if (cls.interfaces != null && patchedNode.interfaces.containsAll(cls.interfaces)) {
//
//                    String suffix = "Mixin".concat(mappedName.substring(mappedName.lastIndexOf('/') + 1));
//
//                    // format the name.
//                    String name = MIXIN_PACKAGE.replace(".", "/")
//                            .concat("/")
//                            .concat(trimmedPackage)
//                            .concat("/")
//                            .concat(suffix);
//
//                    if (!transformationMappings.getClassRenameMap().containsValue(name)) {
//                        Object value = mappedName.contains("$") ? mappedName : Type.getType("L" + mappedName + ";");
//                        cls.visitAnnotation(MixinDefinitions.MIXIN, true).visit("value", value);
//
//                        transformationMappings.getClassRenameMap().put(cls.name, name);
//                    }
//                }
//            }
//        }

        transformationMappings.addFields(patchedNode, fields);
        transformationMappings.addMethods(patchedNode, methods);
    }

    protected ClassNode getMixin(MethodNode methodNode) {
        if (methodNode.visibleAnnotations != null) {
            final Optional<AnnotationNode> first =
                    methodNode.visibleAnnotations.stream().filter(an -> an.desc.equals(MixinDefinitions.MIXIN_MERGED)).findFirst();
            if (first.isPresent()) {
                String mixinName = ((String) first.get().values.get(1)).replaceAll("\\.", "/");
                return version.getClass(mixinName).get();
            }
        }
        return null;
    }

    private String generateJson() {
        final JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("required", true);
        jsonObject.addProperty("compatibilityLevel", "JAVA_8");
        jsonObject.addProperty("package", MIXIN_PACKAGE);
        jsonObject.addProperty("refmap", MIXIN_CONFIG_FILE);
        jsonObject.addProperty("verbose", true);
        final JsonArray mixinArray = new JsonArray();
        for (String mixin : mixinNames) {
            mixinArray.add(mixin.substring(MIXIN_PACKAGE.length() + 1).replace("/", "."));
        }
        jsonObject.add("mixins", mixinArray);
        return new GsonBuilder().setPrettyPrinting().create().toJson(jsonObject);
    }

    public void postTransform() {
        version.addResource(MIXIN_CONFIG_FILE, generateJson().getBytes());
        String tweakerName = MIXIN_PACKAGE.concat(".").concat(MIXIN_TWEAKER_NAME).replace(".", "/");
        version.addResource(tweakerName.concat(".class"), TweakerGenerator.generate(tweakerName, MIXIN_CONFIG_FILE, version.isUsingOptiFine()));
    }

}
