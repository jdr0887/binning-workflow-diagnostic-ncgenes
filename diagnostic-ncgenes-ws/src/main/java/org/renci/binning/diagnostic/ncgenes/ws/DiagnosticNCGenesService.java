package org.renci.binning.diagnostic.ncgenes.ws;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.renci.binning.core.diagnostic.DiagnosticBinningJobInfo;
import org.renci.binning.dao.clinbin.model.DiagnosticStatusType;

@Path("/DiagnosticNCGenesService/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface DiagnosticNCGenesService {

    @POST
    @Path("/submit")
    public Response submit(DiagnosticBinningJobInfo info);

    @GET
    @Path("/status/{binningJobId}")
    public DiagnosticStatusType status(@PathParam("binningJobId") Integer binningJobId);

}
