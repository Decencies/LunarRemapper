package club.decencies.remapper.lunar.pipeline.mixin;

import club.decencies.remapper.lunar.pipeline.mixin.processor.*;
import club.decencies.remapper.lunar.pipeline.mixin.processor.type.*;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.objectweb.asm.tree.MethodNode;

@Getter
@RequiredArgsConstructor
public enum MixinMethod {

    OVERWRITE(MixinDefinitions.OVERWRITE, new OverwriteMethodProcessor()),
    PROXY(MixinDefinitions.PROXY, new ProxyMethodProcessor()),
    BRIDGE("bridge$", new BridgeMethodProcessor()),
    REDIRECT("redirect$", new RedirectMethodProcessor()),
    MODIFY_CONSTANT("constant$", new ModifyConstantMethodProcessor()),
    MODIFY_VARIABLE("localvar$", new ModifyVariableMethodProcessor()),
    HANDLER("handler$", new HandlerMethodProcessor());

    private final String pattern;
    private final MethodProcessor processor;

    public boolean matches(MethodNode methodNode) {
        return pattern.startsWith("L") ? methodNode.visibleAnnotations != null && methodNode.visibleAnnotations.stream().anyMatch(va -> va.desc.equals(pattern)) : methodNode.name.startsWith(pattern);
    }

}
