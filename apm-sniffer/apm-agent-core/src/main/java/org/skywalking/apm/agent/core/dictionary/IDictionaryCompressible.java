package org.skywalking.apm.agent.core.dictionary;

/**
 * The <code>IDictionaryCompressible</code> implementation are objects which supported compressing by dictionary.
 *
 *
 * @author wusheng
 */
public interface IDictionaryCompressible {
    /**
     * The Dictionary notifies, when compress key isn't found,
     * means compress fail this time.
     */
    void incomplete(String notFoundKey);
}
