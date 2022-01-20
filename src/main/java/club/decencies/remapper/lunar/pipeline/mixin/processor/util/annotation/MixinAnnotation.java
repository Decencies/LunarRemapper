package club.decencies.remapper.lunar.pipeline.mixin.processor.util.annotation;

import org.objectweb.asm.AnnotationVisitor;

public abstract class MixinAnnotation {

    protected abstract String getDescriptor();

    protected abstract void annotate(AnnotationVisitor visitor);

}
