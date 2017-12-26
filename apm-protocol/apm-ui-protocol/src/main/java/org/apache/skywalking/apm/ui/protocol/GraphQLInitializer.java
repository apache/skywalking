package org.apache.skywalking.apm.ui.protocol;

import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;
import graphql.schema.idl.FieldWiringEnvironment;
import graphql.schema.idl.InterfaceWiringEnvironment;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.WiringFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.io.File;

/**
 * GraphQL server initializer.
 * 
 * @author gaohongtao
 */
@SpringBootApplication
public class GraphQLInitializer extends SpringBootServletInitializer {
    
    private Logger logger = LoggerFactory.getLogger(GraphQLInitializer.class);
    
    public static void main(String[] args) throws Exception {
        ApplicationContext applicationContext = SpringApplication.run(GraphQLInitializer.class, args);
    }
    
    @Bean
    GraphQLSchema schema() {
        SchemaParser schemaParser = new SchemaParser();
        SchemaGenerator schemaGenerator = new SchemaGenerator();

        TypeDefinitionRegistry typeRegistry = new TypeDefinitionRegistry();
        typeRegistry.merge(schemaParser.parse(loadSchema("common.graphqls")));
        typeRegistry.merge(schemaParser.parse(loadSchema("trace.graphqls")));
        typeRegistry.merge(schemaParser.parse(loadSchema("overview-layer.graphqls")));
        RuntimeWiring wiring = buildRuntimeWiring();
        return schemaGenerator.makeExecutableSchema(typeRegistry, wiring);
    }

    private File loadSchema(final String s) {
        return new File(GraphQLInitializer.class.getClassLoader().getResource("ui-graphql/" + s).getFile());
    }

    private RuntimeWiring  buildRuntimeWiring() {
        WiringFactory dynamicWiringFactory = new WiringFactory() {
            @Override
            public boolean providesTypeResolver(final InterfaceWiringEnvironment environment) {
                return true;
            }
    
            @Override
            public TypeResolver getTypeResolver(final InterfaceWiringEnvironment environment) {
                return env -> GraphQLObjectType.newObject().build();
            }
    
            @Override
            public boolean providesDataFetcher(final FieldWiringEnvironment environment) {
                logger.info("data fetcher: {},{}", environment.getFieldDefinition(), environment.getParentType());
                return false;
            }
        };
        return RuntimeWiring.newRuntimeWiring()
                .wiringFactory(dynamicWiringFactory).build();
    }
}
