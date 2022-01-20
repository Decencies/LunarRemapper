package club.decencies.remapper.lunar.pipeline.type;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MethodInfo {
    private boolean cancellable;
    private boolean returnable;
    private boolean locals;
}
