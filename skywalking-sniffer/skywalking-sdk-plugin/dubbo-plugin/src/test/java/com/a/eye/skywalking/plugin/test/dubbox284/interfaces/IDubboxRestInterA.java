package com.a.eye.skywalking.plugin.test.dubbox284.interfaces;

import com.a.eye.skywalking.plugin.test.dubbox283.interfaces.param.DubboxRestInterAParameter;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/rest-a")
@Consumes({MediaType.APPLICATION_JSON})
@Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
public interface IDubboxRestInterA {

    @Path("/doBusiness")
    @POST
    String doBusiness(DubboxRestInterAParameter paramA);
}
