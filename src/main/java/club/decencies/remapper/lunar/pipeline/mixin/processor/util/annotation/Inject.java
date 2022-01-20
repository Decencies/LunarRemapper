package club.decencies.remapper.lunar.pipeline.mixin.processor.util.annotation;

import club.decencies.remapper.lunar.pipeline.mixin.MixinDefinitions;
import org.objectweb.asm.AnnotationVisitor;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.ArrayList;
import java.util.List;

public class Inject extends MixinAnnotation {

    private List<String> method = new ArrayList<>();
    private List<Slice> slice = new ArrayList<>();
    private List<At> at = new ArrayList<>();
    private boolean cancellable = false;
    private LocalCapture locals = LocalCapture.NO_CAPTURE;
    public boolean remap = true;
    private int require = -1;
    public int expect = 1;
    public int allow = -1;
    public String constraints = "";

    @Override
    protected String getDescriptor() {
        return MixinDefinitions.INJECT;
    }

    @Override
    protected void annotate(AnnotationVisitor visitor) {
        AnnotationVisitor methodsArray = visitor.visitArray("method");
        method.forEach(method -> methodsArray.visit(null, method));
        AnnotationVisitor sliceArray = visitor.visitArray("slice");
        slice.forEach(slice -> slice.annotate(sliceArray.visitAnnotation(null, slice.getDescriptor())));
        AnnotationVisitor atArray = visitor.visitArray("at");
        at.forEach(at -> at.annotate(atArray.visitAnnotation(null, at.getDescriptor())));
        visitor.visit("cancellable", cancellable);
        visitor.visit("cancellable", cancellable);
    }

}
