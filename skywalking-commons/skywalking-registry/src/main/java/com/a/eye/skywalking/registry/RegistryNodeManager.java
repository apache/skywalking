package com.a.eye.skywalking.registry;

import com.a.eye.skywalking.registry.api.RegistryNode;

import java.util.ArrayList;
import java.util.List;

public class RegistryNodeManager {
    private List<String> connectionURLOfPreviousChanged;


    public RegistryNodeManager() {
        connectionURLOfPreviousChanged = new ArrayList<String>();
    }

    public List<RegistryNode> calculateChangeOfConnectionURL(final List<String> currentConnectionURL) {
        List<RegistryNode> connectionURLS = new ArrayList<RegistryNode>();
        for (String URL : currentConnectionURL) {
            if (!connectionURLOfPreviousChanged.contains(URL)) {
                connectionURLS.add(new RegistryNode(URL, RegistryNode.ChangeType.ADDED));
            }
        }

        for (String URL : connectionURLOfPreviousChanged) {
            if (!currentConnectionURL.contains(URL)) {
                connectionURLS.add(new RegistryNode(URL, RegistryNode.ChangeType.REMOVED));
            }
        }

        connectionURLOfPreviousChanged = new ArrayList<String>(currentConnectionURL);
        return connectionURLS;
    }

    public List<String> getConnectionURLOfPreviousChanged() {
        return new ArrayList<String>(connectionURLOfPreviousChanged);
    }
}
