package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * generation-runs.md "Slice 11_run-failures" RunStartupSweepTest: the startup sweep is a SmartInitializingSingleton that
 * executes its sweep statement exactly once. Built by reflection (mocks for every constructor parameter) so RED compiles.
 */
// @trace FR-32
class RunStartupSweepTest {

    private static final Pattern WRITE = Pattern.compile("(?i)update|fail|interrupt|sweep|execute");

    private static Class<?> type() {
        try {
            return Class.forName("com.oracul.app.runs.RunStartupSweep");
        } catch (ClassNotFoundException e) {
            throw new AssertionError("com.oracul.app.runs.RunStartupSweep is missing");
        }
    }

    @Test
    void implementsSmartInitializingSingleton() {
        assertThat(SmartInitializingSingleton.class).isAssignableFrom(type());
    }

    @Test
    void afterSingletonsInstantiatedRunsTheSweepStatementOnce() throws Exception {
        Class<?> type = type();
        Constructor<?> ctor = type.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        List<Object> mocks = new ArrayList<>();
        Object[] args = new Object[ctor.getParameterCount()];
        Class<?>[] types = ctor.getParameterTypes();
        for (int i = 0; i < args.length; i++) {
            if (types[i] == Clock.class) {
                args[i] = Clock.systemUTC();
            } else {
                args[i] = Mockito.mock(types[i]);
                mocks.add(args[i]);
            }
        }
        Object sweep;
        try {
            sweep = ctor.newInstance(args);
        } catch (InvocationTargetException e) {
            throw new AssertionError("constructor failed", e.getCause());
        }
        ((SmartInitializingSingleton) sweep).afterSingletonsInstantiated();
        long writes = mocks.stream().flatMap(m -> Mockito.mockingDetails(m).getInvocations().stream())
            .filter(inv -> WRITE.matcher(inv.getMethod().getName()).find()).count();
        assertThat(writes).as("sweep statements executed on %s", mocks.stream()
            .map(m -> Mockito.mockingDetails(m).getMockCreationSettings().getTypeToMock().getSimpleName()).toList())
            .isEqualTo(1);
    }
}
