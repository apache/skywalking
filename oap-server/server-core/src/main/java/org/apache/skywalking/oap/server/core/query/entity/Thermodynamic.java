/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.core.query.entity;

import java.util.*;
import lombok.*;

/**
 * @author peng-yongsheng
 */
@Getter
public class Thermodynamic {
    private final List<List<Long>> nodes;
    @Setter private int axisYStep;

    public Thermodynamic() {
        this.nodes = new ArrayList<>();
    }

    public void fromMatrixData(List<List<Long>> thermodynamicValueMatrix, int numOfSteps) {
        thermodynamicValueMatrix.forEach(columnOfThermodynamic -> {
                if (columnOfThermodynamic.size() == 0) {
                    if (numOfSteps > 0) {
                        for (int i = 0; i < numOfSteps; i++) {
                            columnOfThermodynamic.add(0L);
                        }
                    }
                }
            }
        );

        for (int colNum = 0; colNum < thermodynamicValueMatrix.size(); colNum++) {
            List<Long> column = thermodynamicValueMatrix.get(colNum);
            for (int rowNum = 0; rowNum < column.size(); rowNum++) {
                Long value = column.get(rowNum);
                this.setNodeValue(colNum, rowNum, value);
            }
        }
    }

    private void setNodeValue(int columnNum, int rowNum, Long value) {
        List<Long> element = new ArrayList<>(3);
        element.add((long)columnNum);
        element.add((long)rowNum);
        element.add(value);
        nodes.add(element);
    }
}