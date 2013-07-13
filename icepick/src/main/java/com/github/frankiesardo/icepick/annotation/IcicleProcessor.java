package com.github.frankiesardo.icepick.annotation;

import com.squareup.java.JavaWriter;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@SupportedAnnotationTypes("com.github.frankiesardo.icepick.annotation.Icicle")
public class IcicleProcessor extends AbstractProcessor {

    public static final String SUFFIX = "$$Icicle";

    @Override
    public boolean process(Set<? extends TypeElement> typeElements, RoundEnvironment env) {
        Set<? extends Element> elements = env.getElementsAnnotatedWith(Icicle.class);
        Map<TypeElement, Set<IcicleField>> fieldsByType = new HashMap<TypeElement, Set<IcicleField>>();
        IcicleConverter icicleConverter = new IcicleConverter(processingEnv.getTypeUtils(), processingEnv.getElementUtils());
        for (Element element : elements) {
            if (element.getModifiers().contains(Modifier.FINAL) ||
                    element.getModifiers().contains(Modifier.STATIC) ||
                    element.getModifiers().contains(Modifier.PROTECTED) ||
                    element.getModifiers().contains(Modifier.PRIVATE)) {
                error(element, "Field must not be private, protected, static or final");
                continue;
            }
            TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();
            Set<IcicleField> fields = fieldsByType.get(enclosingElement);
            if (fields == null) {
                fields = new LinkedHashSet<IcicleField>();
                fieldsByType.put(enclosingElement, fields);
            }
            String fieldName = element.getSimpleName().toString();
            String fieldKey = enclosingElement.getQualifiedName() + "." + fieldName;
            String fieldType = element.asType().toString();
            String fieldCommand = icicleConverter.convert(element.asType());
            fields.add(new IcicleField(fieldName, fieldKey, fieldType, fieldCommand));
        }

        for (Map.Entry<TypeElement, Set<IcicleField>> entry : fieldsByType.entrySet()) {
            try {
                JavaFileObject jfo = processingEnv.getFiler().createSourceFile(entry.getKey().getQualifiedName() + SUFFIX, entry.getKey());
                Writer writer = jfo.openWriter();
                JavaWriter jw = new JavaWriter(writer);
                IcicleWriter icicleWriter = new IcicleWriter(jw, SUFFIX);
                icicleWriter.writeClass(entry.getKey(), entry.getValue());
            } catch (IOException e) {
                error(entry.getKey(), "Impossible to create " + entry.getKey().getQualifiedName() + SUFFIX, e);
            }
        }
        return true;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    protected void error(Element element, String message, Object... args) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format(message, args), element);
    }
}