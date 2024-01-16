package org.apache.skywalking.oap.server.core.Hierarchy;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.oap.server.core.query.HierarchyQueryService;
import org.apache.skywalking.oap.server.core.query.type.HierarchyRelatedService;
import org.apache.skywalking.oap.server.core.query.type.HierarchyServiceRelation;
import org.apache.skywalking.oap.server.core.query.type.ServiceHierarchy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.powermock.reflect.Whitebox;

import static org.mockito.Mockito.mock;

public class HierarchyQueryServiceTest {
    private HierarchyQueryService hierarchyQueryService;

    @BeforeEach
    public void init() {
        hierarchyQueryService = mock(HierarchyQueryService.class);
    }

    @Test
    public void testBuildServiceRelation() throws Exception {
        ServiceHierarchy hierarchy = invokeBuildServiceRelation();
        Assertions.assertEquals(9, hierarchy.getRelations().size());
        Assertions.assertEquals(mockHierarchy(false), hierarchy);
    }

    @Test
    public void testFilterConjecturableRelations() throws Exception {
        ServiceHierarchy hierarchy = Whitebox.invokeMethod(
            hierarchyQueryService, "filterConjecturableRelations", mockCache(), invokeBuildServiceRelation());
        Assertions.assertEquals(5, hierarchy.getRelations().size());
        Assertions.assertEquals(mockHierarchy(true), hierarchy);
    }

    private ServiceHierarchy invokeBuildServiceRelation() throws Exception {
        ServiceHierarchy hierarchy = new ServiceHierarchy();
        HierarchyRelatedService serviceA = new HierarchyRelatedService();
        serviceA.setId("A");
        Whitebox.invokeMethod(
            hierarchyQueryService, "buildServiceRelation", mockCache(), hierarchy, serviceA, 10,
            HierarchyQueryService.HierarchyDirection.All
        );
        return hierarchy;
    }

    private Map<HierarchyRelatedService, HierarchyQueryService.ServiceRelations> mockCache() {
        // A->B->C->D->E
        // A->C
        // A->D
        // A->F
        // B->E
        // C->F
        HierarchyRelatedService serviceA = new HierarchyRelatedService();
        serviceA.setId("A");
        HierarchyRelatedService serviceB = new HierarchyRelatedService();
        serviceB.setId("B");
        HierarchyRelatedService serviceC = new HierarchyRelatedService();
        serviceC.setId("C");
        HierarchyRelatedService serviceD = new HierarchyRelatedService();
        serviceD.setId("D");
        HierarchyRelatedService serviceE = new HierarchyRelatedService();
        serviceE.setId("E");
        HierarchyRelatedService serviceF = new HierarchyRelatedService();
        serviceF.setId("F");

        Map<HierarchyRelatedService, HierarchyQueryService.ServiceRelations> serviceRelationsMap = new HashMap<>();
        HierarchyQueryService.ServiceRelations serviceRelationsA = new HierarchyQueryService.ServiceRelations();
        serviceRelationsA.getLowerServices().add(serviceB);
        serviceRelationsA.getLowerServices().add(serviceC);
        serviceRelationsA.getLowerServices().add(serviceD);
        serviceRelationsMap.put(serviceA, serviceRelationsA);

        HierarchyQueryService.ServiceRelations serviceRelationsB = new HierarchyQueryService.ServiceRelations();
        serviceRelationsB.getUpperServices().add(serviceA);
        serviceRelationsB.getLowerServices().add(serviceC);
        serviceRelationsB.getLowerServices().add(serviceE);
        serviceRelationsMap.put(serviceB, serviceRelationsB);

        HierarchyQueryService.ServiceRelations serviceRelationsC = new HierarchyQueryService.ServiceRelations();
        serviceRelationsC.getUpperServices().add(serviceA);
        serviceRelationsC.getUpperServices().add(serviceB);
        serviceRelationsC.getLowerServices().add(serviceD);
        serviceRelationsC.getLowerServices().add(serviceF);
        serviceRelationsMap.put(serviceC, serviceRelationsC);

        HierarchyQueryService.ServiceRelations serviceRelationsD = new HierarchyQueryService.ServiceRelations();
        serviceRelationsD.getUpperServices().add(serviceC);
        serviceRelationsD.getUpperServices().add(serviceA);
        serviceRelationsD.getLowerServices().add(serviceE);
        serviceRelationsMap.put(serviceD, serviceRelationsD);

        HierarchyQueryService.ServiceRelations serviceRelationsE = new HierarchyQueryService.ServiceRelations();
        serviceRelationsE.getUpperServices().add(serviceD);
        serviceRelationsE.getUpperServices().add(serviceB);
        serviceRelationsMap.put(serviceE, serviceRelationsE);

        HierarchyQueryService.ServiceRelations serviceRelationsF = new HierarchyQueryService.ServiceRelations();
        serviceRelationsF.getUpperServices().add(serviceA);
        serviceRelationsF.getUpperServices().add(serviceC);
        serviceRelationsMap.put(serviceF, serviceRelationsF);

        return serviceRelationsMap;
    }

    private ServiceHierarchy mockHierarchy(boolean filterRelations) {
        ServiceHierarchy hierarchy = new ServiceHierarchy();
        HierarchyServiceRelation relationAB = new HierarchyServiceRelation();
        HierarchyServiceRelation relationAC = new HierarchyServiceRelation();
        HierarchyServiceRelation relationAD = new HierarchyServiceRelation();
        HierarchyServiceRelation relationAF = new HierarchyServiceRelation();
        HierarchyServiceRelation relationBC = new HierarchyServiceRelation();
        HierarchyServiceRelation relationBE = new HierarchyServiceRelation();
        HierarchyServiceRelation relationCD = new HierarchyServiceRelation();
        HierarchyServiceRelation relationCF = new HierarchyServiceRelation();
        HierarchyServiceRelation relationDE = new HierarchyServiceRelation();

        HierarchyRelatedService serviceA = new HierarchyRelatedService();
        serviceA.setId("A");
        HierarchyRelatedService serviceB = new HierarchyRelatedService();
        serviceB.setId("B");
        HierarchyRelatedService serviceC = new HierarchyRelatedService();
        serviceC.setId("C");
        HierarchyRelatedService serviceD = new HierarchyRelatedService();
        serviceD.setId("D");
        HierarchyRelatedService serviceE = new HierarchyRelatedService();
        serviceE.setId("E");
        HierarchyRelatedService serviceF = new HierarchyRelatedService();
        serviceF.setId("F");
        //AB
        relationAB.setLowerService(serviceB);
        relationAB.setUpperService(serviceA);
        //AC
        relationAC.setLowerService(serviceC);
        relationAC.setUpperService(serviceA);
        //AD
        relationAD.setLowerService(serviceD);
        relationAD.setUpperService(serviceA);
        //AF
        relationAF.setLowerService(serviceF);
        relationAF.setUpperService(serviceA);
        //BC
        relationBC.setLowerService(serviceC);
        relationBC.setUpperService(serviceB);
        //BE
        relationBE.setLowerService(serviceE);
        relationBE.setUpperService(serviceB);
        //CD
        relationCD.setLowerService(serviceD);
        relationCD.setUpperService(serviceC);
        //CF
        relationCF.setLowerService(serviceF);
        relationCF.setUpperService(serviceC);
        //DE
        relationDE.setLowerService(serviceE);
        relationDE.setUpperService(serviceD);

        hierarchy.getRelations().add(relationAB);
        hierarchy.getRelations().add(relationBC);
        hierarchy.getRelations().add(relationCD);
        hierarchy.getRelations().add(relationCF);
        hierarchy.getRelations().add(relationDE);

        if (!filterRelations) {
            hierarchy.getRelations().add(relationAC);
            hierarchy.getRelations().add(relationAD);
            hierarchy.getRelations().add(relationAF);
            hierarchy.getRelations().add(relationBE);
        }

        return hierarchy;
    }
}
