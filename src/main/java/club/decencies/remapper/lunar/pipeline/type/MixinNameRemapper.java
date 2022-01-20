package club.decencies.remapper.lunar.pipeline.type;

import lombok.RequiredArgsConstructor;
import org.objectweb.asm.commons.Remapper;

import java.util.Map;

@RequiredArgsConstructor
public class MixinNameRemapper extends Remapper {

    protected final Map<String, String> map;

    @Override
    public String map(String internalName) {
        if (map.containsKey(internalName)) {
            return map.get(internalName);
        }
        return internalName;
    }

}
