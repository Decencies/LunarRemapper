package club.decencies.remapper.lunar.pipeline.mixin.processor.util.annotation;

import club.decencies.remapper.lunar.pipeline.mixin.MixinDefinitions;
import org.objectweb.asm.AnnotationVisitor;

public class Slice extends MixinAnnotation {

    public String id = "";
    public At from = new At("HEAD");
    public At to = new At("TAIL");

    @Override
    protected String getDescriptor() {
        return MixinDefinitions.SLICE;
    }

    @Override
    protected void annotate(AnnotationVisitor visitor) {
        if (!id.isEmpty()) {
            visitor.visit("id", id);
        }
        from.annotate(visitor.visitAnnotation("from", from.getDescriptor()));
        to.annotate(visitor.visitAnnotation("to", to.getDescriptor()));
    }
}
