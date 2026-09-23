package company.vk.edu.distrib.compute.test.urlshortener;

import java.io.File;
import java.lang.annotation.Annotation;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.support.ParameterDeclarations;
import org.junit.platform.commons.support.scanning.ClassFilter;
import org.junit.platform.commons.util.ReflectionUtils;

public class AbstractArgumentsProvider {

    private final Collection<Class<?>> factories;
    private final Class<?> factoryClass;

    public AbstractArgumentsProvider(Collection<Class<?>> factories, Class<?> factoryClass) {
        this.factories = factories;
        this.factoryClass = factoryClass;
    }

    public static Collection<Class<?>> findAnnotatedFactories(Class<? extends Annotation> annotation) {
        return Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .map(Path::of)
            .flatMap(root -> ReflectionUtils.streamAllClassesInClasspathRoot(
                root.toUri(),
                ClassFilter.of(aClass -> aClass.isAnnotationPresent(annotation))
            ))
            .distinct()
            .sorted(Comparator.comparing(Class::getName))
            .toList();
    }

    public Stream<? extends Arguments> provideArguments(ParameterDeclarations parameters, ExtensionContext context) {
        return factories.stream()
            .filter(factoryClass::isAssignableFrom)
            .filter(it -> Arrays.stream(it.getDeclaredConstructors())
                .anyMatch(ctor -> ctor.getParameterCount() == 0))
            .map(ReflectionUtils::newInstance)
            .map(Arguments::of);
    }
}
