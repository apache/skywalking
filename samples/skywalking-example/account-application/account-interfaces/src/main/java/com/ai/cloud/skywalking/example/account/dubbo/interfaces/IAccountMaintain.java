package com.ai.cloud.skywalking.example.account.dubbo.interfaces;

import com.ai.cloud.skywalking.example.account.dubbo.interfaces.param.AccountInfo;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;


@Path("/accountMaintain")
@Consumes({MediaType.APPLICATION_JSON})
@Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_XML})
public interface IAccountMaintain {

    @Path("/create")
    @POST
    String create(AccountInfo accountInfo);
}
