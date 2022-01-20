package club.decencies.remapper.lunar.pipeline.mixin.processor.util.annotation;

import club.decencies.remapper.lunar.pipeline.mixin.MixinDefinitions;

import lombok.Setter;

import org.objectweb.asm.AnnotationVisitor;

@Setter
public class At extends MixinAnnotation {

    public At(String value) {
        this.value = value;
    }

    private enum Shift {
        NONE, BEFORE, AFTER, BY
    }

    private String value;
    private String target;
    private Shift shift;

    @Override
    protected String getDescriptor() {
        return MixinDefinitions.AT;
    }

    @Override
    protected void annotate(AnnotationVisitor visitor) {
        visitor.visit("value", value);
        visitor.visit("target", target);
        visitor.visitEnum("shift", MixinDefinitions.SHIFT, shift.name());
    }

}
