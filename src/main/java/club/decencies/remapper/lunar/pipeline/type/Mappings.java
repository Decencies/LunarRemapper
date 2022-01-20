package club.decencies.remapper.lunar.pipeline.type;

import club.decencies.remapper.lunar.mappings.MappingProvider;
import club.decencies.remapper.lunar.pipeline.LunarVersion;

import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class Mappings extends Remapper {

    private static final String PROXY = "Lorg/spongepowered/asm/mixin/Proxy;";

    private final Map<String, Map<String, String>> methodMap = new HashMap<>();
    private final Map<String, Map<String, String>> fieldMap = new HashMap<>();
    private final Map<String, String> mixinMap = new HashMap<>();

    private final LunarVersion version;
    private final MappingProvider provider;

    public Mappings(LunarVersion version, MappingProvider provider) {
        this.version = version;
        this.provider = provider;
    }

    public void putMixin(String oldName, String newName) {
        mixinMap.put(oldName, newName);
    }

    @Override
    public String mapFieldName(String owner, String name, String descriptor) {
        if (fieldMap.containsKey(owner)) {
            final Map<String, String> map = fieldMap.get(owner);
            if (map.containsKey(name)) {
                return map.get(name);
            }
        } else if (owner.startsWith("lunar/") && !mixinMap.containsKey(owner)) {
            AtomicReference<String> remappedName = new AtomicReference<>(name);
            version.getClass(owner).ifPresent(ownerNode -> {
                version.getPatchedMClass(ownerNode.superName).ifPresent(superNode -> {
                    superNode.fields.stream().filter(fd -> fieldNameDescriptorMatch(fd, name, descriptor)).findFirst().ifPresent(fd -> {
                        int superFieldIndex = superNode.fields.indexOf(fd);
                        version.getMapping(ownerNode.superName).ifPresent(proguardSuperName -> {
                            version.getMcClass(proguardSuperName).ifPresent(mcSuperNode -> {
                                final FieldNode fieldNode = mcSuperNode.fields.get(superFieldIndex);
                                provider.lookupFieldName(proguardSuperName, fieldNode).ifPresent(remappedName::set);
                            });
                        });
                    });
                });
            });
            return remappedName.get();
        }
        return super.mapFieldName(owner, name, descriptor);
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {
        if (methodMap.containsKey(owner)) {
            final Map<String, String> map = methodMap.get(owner);
            if (map.containsKey(name)) {
                return map.get(name);
            }
        } else if (owner.startsWith("lunar/") && !mixinMap.containsKey(owner) && !name.startsWith("bridge$")) {

            AtomicReference<String> remappedName = new AtomicReference<>(name);
            AtomicBoolean remapped = new AtomicBoolean(false);

            version.getClass(owner).ifPresent(ownerNode -> {
                ownerNode.interfaces.stream().map(version::getPatchedMClass).filter(Optional::isPresent).map(Optional::get).forEach(itfNode -> {
                    if (remapped.get()) return;
                    itfNode.methods.stream().filter(md -> methodNameDescriptorMatch(md, name, descriptor)).findFirst().ifPresent(md -> {
                        int superMethodIndex = itfNode.methods.indexOf(md);
                        version.getMapping(itfNode.name).ifPresent(proguardItfName -> {
                            version.getMcClass(proguardItfName).ifPresent(mcItfNode -> {
                                final MethodNode methodNode = mcItfNode.methods.get(superMethodIndex);
                                provider.lookupMethodName(proguardItfName, methodNode).ifPresent(mapping -> {
                                    remappedName.set(mapping);
                                    remapped.set(true);
                                });
                            });
                        });
                    });
                });

                if (!remapped.get()) {
                    version.getPatchedMClass(ownerNode.superName).ifPresent(superNode -> {
                        superNode.methods.stream().filter(md -> methodNameDescriptorMatch(md, name, descriptor)).findFirst().ifPresent(md -> {
                            int superMethodIndex = superNode.methods.indexOf(md);
                            version.getMapping(ownerNode.superName).ifPresent(proguardSuperName -> {
                                version.getMcClass(proguardSuperName).ifPresent(mcSuperNode -> {
                                    if (superMethodIndex > mcSuperNode.methods.size() - 1) {
                                        // todo
                                        System.err.println("Has Annotations: " + (md.visibleAnnotations != null));
                                        provider.lookupClassName(mcSuperNode.name).ifPresent(_name ->
                                                System.err.println("Mojang Super Name: " + _name)
                                        );
                                        System.err.println("Lunar Super Name: " + ownerNode.superName);
                                        System.err.println("Method Name: " + md.name);
                                        //System.out.println(name);
                                        System.err.println("Lunar Owner Name: " + owner);
                                    } else {
                                        final MethodNode methodNode = mcSuperNode.methods.get(superMethodIndex);
                                        provider.lookupMethodName(proguardSuperName, methodNode).ifPresent(mapping -> {
                                            remappedName.set(mapping);
                                            remapped.set(true);
                                        });
                                    }
                                });
                            });
                        });
                    });
                }
            });

            if (remappedName.get().matches("[abepsh]{25}")) {
                System.err.println("Epic mapping fail.");
                System.err.println("Owner: " + owner);
                System.err.println("Name: " + name);
                System.err.println("Desc: " + descriptor);
            }

            return remappedName.get();

        }
        return super.mapMethodName(owner, name, descriptor);
    }

    @Override
    public String map(String internalName) {
        if (mixinMap.containsKey(internalName)) {
            return mixinMap.get(internalName);
        }
        return super.map(internalName);
    }

    public void process(ClassNode original, ClassNode patched) {
        provider.lookupClassName(original.name).ifPresent(name -> mixinMap.put(patched.name, name));

        int index = 0;

        for (FieldNode fieldNode : patched.fields) {
            if (index < original.fields.size()) {
                FieldNode minecraftField = original.fields.get(index);
                provider.lookupFieldName(original.name, minecraftField).ifPresent(name -> {
                    fieldMap.computeIfAbsent(patched.name, v -> new HashMap<>()).put(fieldNode.name, name);
                });
                index++;
            } else break; // break non-minecraft fields.
        }

        index = 0;

        for (MethodNode method : patched.methods) {
            if (index < original.methods.size()) {
                MethodNode minecraftMethod = original.methods.get(index);
                provider.lookupMethodName(original.name, minecraftMethod).ifPresent(name -> {
                    methodMap.computeIfAbsent(patched.name, v -> new HashMap<>()).put(method.name, name);
                });
                index++;
            }
//            if (isProxyMethod(method)) {
//                final String proxyTarget = getVisibleAnnotationValue(method, 0);
//                //System.out.println(patched.name + "." + method.name + method.desc + " : " + proxyTarget);
//                methodMap.computeIfAbsent(patched.name, v -> new HashMap<>()).put(method.name, stripProxyTarget(proxyTarget));
//            }
        }
    }

    /**
     * Strip the proxy target to its method name.
     * e.g. renderString(Ljava/lang/String;FFIZ)I -> renderString
     *
     * @param proxyTarget the raw proxy target string.
     * @return the stripped proxy target.
     */
    protected String stripProxyTarget(String proxyTarget) {
        if (proxyTarget.indexOf('(') != -1) {
            return proxyTarget.substring(0, proxyTarget.indexOf('('));
        }
        return proxyTarget;
    }

    private boolean isProxyMethod(MethodNode methodNode) {
        return methodNode.visibleAnnotations != null && Objects.equals(methodNode.visibleAnnotations.get(0).desc, PROXY);
    }

    private boolean methodNameDescriptorMatch(MethodNode methodNode, String name, String descriptor) {
        return methodNode.name.equals(name) && methodNode.desc.equals(descriptor);
    }

    private boolean fieldNameDescriptorMatch(FieldNode fieldNode, String name, String descriptor) {
        return fieldNode.name.equals(name) && fieldNode.desc.equals(descriptor);
    }

    private String getVisibleAnnotationValue(MethodNode methodNode, int annotationOffset) {
        return ((String) methodNode.visibleAnnotations.get(annotationOffset).values.get(1)).replace(".", "/");
    }


}
