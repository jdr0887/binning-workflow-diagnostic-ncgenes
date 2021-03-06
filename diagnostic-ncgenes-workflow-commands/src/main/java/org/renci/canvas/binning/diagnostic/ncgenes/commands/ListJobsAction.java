package org.renci.canvas.binning.diagnostic.ncgenes.commands;

import java.text.DateFormat;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.renci.canvas.dao.CANVASDAOBeanService;
import org.renci.canvas.dao.clinbin.model.DiagnosticBinningJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "diagnostic-ncgenes", name = "list-jobs", description = "List NCGenes DiagnosticBinningJobs")
@Service
public class ListJobsAction implements Action {

    private static final Logger logger = LoggerFactory.getLogger(ListJobsAction.class);

    @Reference
    private CANVASDAOBeanService daoBeanService;

    public ListJobsAction() {
        super();
    }

    @Override
    public Object execute() throws Exception {
        logger.debug("ENTERING execute()");

        DiagnosticBinningJob example = new DiagnosticBinningJob();
        example.setStudy("NCGENES Study");

        List<DiagnosticBinningJob> foundBinningJobs = daoBeanService.getDiagnosticBinningJobDAO().findByExample(example);
        StringBuilder sb = new StringBuilder();
        try (Formatter formatter = new Formatter(sb, Locale.US)) {
            if (CollectionUtils.isNotEmpty(foundBinningJobs)) {
                String format = "%1$-8s %2$-18s %3$-20s %4$-8s %5$-8s %6$-14s %7$-14s %8$-26s %9$-22s %10$-22s %11$s%n";
                formatter.format(format, "ID", "Participant", "Study", "Gender", "DX", "ListVersion", "Assembly", "Status", "Start", "Stop",
                        "VCF");

                foundBinningJobs.sort((a, b) -> a.getId().compareTo(b.getId()));

                for (DiagnosticBinningJob binningJob : foundBinningJobs) {

                    String formattedStart = "";
                    if (binningJob.getStart() != null) {
                        formattedStart = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(binningJob.getStart());
                    }

                    String formattedStop = "";
                    if (binningJob.getStop() != null) {
                        formattedStop = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(binningJob.getStop());
                    }

                    formatter.format(format, binningJob.getId().toString(), binningJob.getParticipant(), binningJob.getStudy(),
                            binningJob.getGender(), binningJob.getDx().getId(), binningJob.getListVersion(),
                            binningJob.getAssembly() != null ? binningJob.getAssembly().getId() : "", binningJob.getStatus().getId(),
                            formattedStart, formattedStop, binningJob.getVcfFile());
                    formatter.flush();
                }
            } else {
                formatter.format("No DiagnosticBinningJobs found: %s", example.toString());
            }
            System.out.println(formatter.toString());
        }
        return null;
    }

}
