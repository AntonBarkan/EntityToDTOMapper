package com.anton.processing;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.anton.processing.annotations.FromClass;
import com.anton.processing.annotations.PathMapping;
import com.google.auto.service.AutoService;

@SupportedAnnotationTypes({
        "com.anton.processing.annotations.FromClass"
})
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@AutoService(Processor.class)
public class BuilderProcessor extends AbstractProcessor {


    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        for (Element i : roundEnv.getElementsAnnotatedWith(FromClass.class)) {

            var mirror = getAnnotationValue(i, FromClass.class, "value").get();

            var fildToMapInClass = roundEnv.getElementsAnnotatedWith(PathMapping.class).stream().filter(el -> el.getEnclosingElement().toString().equals(i.toString())).collect(Collectors.toList());
            try {
                writeBuilderFile(i, mirror, fildToMapInClass);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        return true;
    }

    public Optional<? extends AnnotationValue> getAnnotationValue(final Element element,
                                                                  final Class<? extends Annotation> annotationClass,
                                                                  final String name) {
        final Elements elementUtils = this.processingEnv.getElementUtils();
        var elementValues = elementUtils.getElementValuesWithDefaults(getAnnotationMirror(element, annotationClass).get());
        final Optional<? extends AnnotationValue> retValue = elementValues.keySet().stream()
                .filter(k -> k.getSimpleName().toString().equals(name))
                .map(k -> elementValues.get(k))
                .findAny();

        return retValue;
    }

    public Optional<? extends AnnotationMirror> getAnnotationMirror(final Element element,
                                                                    final Class<? extends Annotation> annotationClass) {
        final var annotationClassName = annotationClass.getName();
        final Optional<? extends AnnotationMirror> retValue = element.getAnnotationMirrors().stream()
                .filter(m -> m.getAnnotationType().toString().equals(annotationClassName))
                .findFirst();

        return retValue;
    }

    private void writeBuilderFile(Element from, AnnotationValue to, List<? extends Element> fildToMapInClass) throws IOException {
        var splitted = to.toString().split("\\.");
        var toName = splitted[splitted.length - 2];
        var fromName = from.getSimpleName().toString();
        var className = fromName + "To" + toName;
        String packageName = null;
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            packageName = className.substring(0, lastDot);
        }

        JavaFileObject builderFile = processingEnv.getFiler().createSourceFile(className);
        try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {

            if (packageName != null) {
                out.print("package ");
                out.print(packageName);
                out.println(";");
                out.println();
            }

            out.println("import org.mapstruct.Mapper;");
            out.println("import org.mapstruct.ReportingPolicy;");
            out.println("import org.mapstruct.Mapping;");

            out.println("import " + from.toString() + ";");
            out.println("import " + to.getValue() + ";");
            out.println();
            out.println("@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR, componentModel = \"spring\")");
            out.print("public interface ");
            out.print(className);
            out.println(" {");
            out.println();

            for (Element e : fildToMapInClass) {
                out.print("    ");
                out.print("@Mapping(target = \"" + e.getSimpleName().toString()
                        + "\", source = " + getAnnotationValue(fildToMapInClass.get(0), PathMapping.class, "value").get() + ")");
                out.println();
            }
            out.print("    " + fromName);
            out.print(" map(");
            out.print(toName);
            out.println(" value);");
            out.println();
            out.println("}");
        }
    }
}
