package org.skywalking.apm.agent.core.plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.bytebuddy.pool.TypePool;

/**
 * The <code>WitnessClassFinder</code> represents a pool of {@link TypePool}s,
 * each {@link TypePool} matches a {@link ClassLoader},
 * which helps to find the class define existed or not.
 *
 * @author wusheng
 */
public enum WitnessClassFinder {
    INSTANCE;

    private Map<ClassLoader, TypePool> poolMap = new ConcurrentHashMap<ClassLoader, TypePool>();

    /**
     * @param witnessClass
     * @param classLoader for finding the witnessClass
     * @return true, if the given witnessClass exists, through the given classLoader.
     */
    public boolean exist(String witnessClass, ClassLoader classLoader) {
        ClassLoader mappingKey = classLoader == null ? NullClassLoader.INSTANCE : classLoader;
        if (!poolMap.containsKey(witnessClass)) {
            synchronized (poolMap) {
                if (!poolMap.containsKey(witnessClass)) {
                    TypePool classTypePool = classLoader == null ? TypePool.Default.ofClassPath() : TypePool.Default.of(classLoader);
                    poolMap.put(mappingKey, classTypePool);
                }
            }
        }
        TypePool typePool = poolMap.get(mappingKey);
        TypePool.Resolution witnessClassResolution = typePool.describe(witnessClass);
        return witnessClassResolution.isResolved();
    }
}

final class NullClassLoader extends ClassLoader {
    static NullClassLoader INSTANCE = new NullClassLoader();
}
