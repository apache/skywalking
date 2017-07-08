package org.skywalking.apm.network.trace.component;

/**
 * The <code>Component</code> represents component library,
 * which has been supported by skywalking sniffer.
 *
 * The supported list is in {@link ComponentsDefine}.
 *
 * @author wusheng
 */
public interface Component {
    int getId();

    String getName();
}
