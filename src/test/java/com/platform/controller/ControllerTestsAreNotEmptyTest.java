package com.platform.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guard against the regression BP-64 exists to undo: {@code EmployeeControllerTest}
 * shipped for months as {@code class EmployeeControllerTest {}} - it compiled, CI
 * passed, and it implied controller coverage that did not exist. An empty test
 * class is worse than none, because it looks like coverage.
 *
 * <p>This asserts every {@code *ControllerTest} in this package actually has
 * executable test methods. It is deliberately a hardcoded list rather than a
 * classpath scan: no scanning library is on the test classpath, and the list is
 * short enough that adding a controller test without adding it here is a visible
 * omission in the same commit.
 */
class ControllerTestsAreNotEmptyTest {

    static Stream<Class<?>> controllerTests() {
        return Stream.of(
                AuthControllerTest.class,
                BusinessControllerTest.class,
                EmployeeControllerTest.class,
                BookingControllerTest.class);
    }

    @ParameterizedTest(name = "{0} has executable test methods")
    @MethodSource("controllerTests")
    void hasAtLeastOneExecutableTestMethod(Class<?> testClass) {
        long executable = Arrays.stream(testClass.getDeclaredMethods())
                .filter(ControllerTestsAreNotEmptyTest::isTestMethod)
                .count();

        assertTrue(executable > 0,
                testClass.getSimpleName() + " has no @Test / @ParameterizedTest methods - "
                        + "an empty controller test stub must not ship again (BP-64)");
    }

    private static boolean isTestMethod(Method m) {
        return m.isAnnotationPresent(Test.class) || m.isAnnotationPresent(ParameterizedTest.class);
    }
}
