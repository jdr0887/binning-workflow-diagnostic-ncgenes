package org.renci.canvas.binning.diagnostic.ncgenes.commons;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.renci.canvas.binning.core.BinningException;
import org.renci.canvas.binning.core.GATKDepthInterval;
import org.renci.canvas.binning.core.IRODSUtils;
import org.renci.canvas.binning.core.diagnostic.AbstractLoadCoverageCallable;
import org.renci.canvas.dao.CANVASDAOBeanService;
import org.renci.canvas.dao.clinbin.model.DiagnosticBinningJob;
import org.renci.common.exec.BashExecutor;
import org.renci.common.exec.CommandInput;
import org.renci.common.exec.CommandOutput;
import org.renci.common.exec.ExecutorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoadCoverageCallable extends AbstractLoadCoverageCallable {

    private static final Logger logger = LoggerFactory.getLogger(LoadCoverageCallable.class);

    public LoadCoverageCallable(CANVASDAOBeanService daoBean, DiagnosticBinningJob binningJob) {
        super(daoBean, binningJob);
    }

    @Override
    public File getAllIntervalsFile(Integer listVersion) {
        logger.debug("ENTERING getAllIntervalsFile(Integer)");
        String binningIntervalsHome = System.getenv("BINNING_INTERVALS_HOME");
        File allIntervalsFile = new File(String.format("%s/NCGenes/all/allintervals.v%d.txt", binningIntervalsHome, listVersion));
        logger.info("all intervals file: {}", allIntervalsFile.getAbsolutePath());
        return allIntervalsFile;
    }

    @Override
    public File getDepthFile(String participant, Integer listVersion) throws BinningException {
        logger.debug("ENTERING getDepthFile(String, Integer)");
        Map<String, String> avuMap = new HashMap<String, String>();
        avuMap.put("ParticipantId", participant);
        avuMap.put("MaPSeqStudyName", "NC_GENES");
        avuMap.put("MaPSeqWorkflowName", "NCGenesBaseline");
        avuMap.put("MaPSeqWorkflowName", "NCGenesDX");
        avuMap.put("MaPSeqJobName", "GATKDepthOfCoverage");
        avuMap.put("MaPSeqMimeType", "TEXT_PLAIN");
        avuMap.put("DxVersion", listVersion.toString());
        String irodsFile = IRODSUtils.findFile(avuMap, ".sample_interval_summary");
        logger.info("irodsFile = {}", irodsFile);
        Path participantPath = Paths.get(System.getProperty("karaf.data"), "tmp", "NC_GENES", participant);
        participantPath.toFile().mkdirs();
        File depthFile = IRODSUtils.getFile(irodsFile, participantPath.toString());
        logger.info("depthFile: {}", depthFile.getAbsolutePath());
        return depthFile;
    }

    @Override
    public void processIntervals(SortedSet<GATKDepthInterval> allIntervalSet, File depthFile, String participant, Integer listVersion)
            throws BinningException {
        logger.debug("ENTERING processIntervals(SortedSet<GATKDepthInterval>, File, String, Integer)");
        try {

            List<String> existingIntervals = new ArrayList<>();
            allIntervalSet.forEach(a -> {
                existingIntervals
                        .add(String.format("%s:%s-%s", a.getContig(), a.getStartPosition().toString(), a.getEndPosition().toString()));
            });

            List<String> foundIntervals = new ArrayList<>();

            try (Reader in = new FileReader(depthFile)) {
                Iterable<CSVRecord> records = CSVFormat.TDF.parse(in);
                for (CSVRecord record : records) {
                    String target = record.get(0);
                    if (target.contains("-") || record.getRecordNumber() == 1) {
                        foundIntervals.add(target);
                        continue;
                    }
                    foundIntervals.add(String.format("%s-%s", target, target.split(":")[1]));
                }
            }

            if (foundIntervals.contains("Target")) {
                foundIntervals.remove("Target");
            }

            Collection<String> missingIntervals = CollectionUtils.disjunction(existingIntervals, foundIntervals);

            // fix exon coverage
            if (CollectionUtils.isNotEmpty(missingIntervals)) {

                Map<String, String> avuMap = new HashMap<String, String>();

                avuMap.put("ParticipantId", participant);
                avuMap.put("MaPSeqStudyName", "NC_GENES");
                avuMap.put("MaPSeqWorkflowName", "NCGenesBaseline");

                // find/get bam
                avuMap.put("MaPSeqJobName", "GATKTableRecalibration");
                avuMap.put("MaPSeqMimeType", "APPLICATION_BAM");
                String irodsFile = IRODSUtils.findFile(avuMap);
                Path participantPath = Paths.get(System.getProperty("karaf.data"), "tmp", "NC_GENES", participant);
                participantPath.toFile().mkdirs();
                File bamFile = IRODSUtils.getFile(irodsFile, participantPath.toString());

                // find/get bai
                avuMap.put("MaPSeqJobName", "SAMToolsIndex");
                avuMap.put("MaPSeqMimeType", "APPLICATION_BAM_INDEX");
                irodsFile = IRODSUtils.findFile(avuMap);
                IRODSUtils.getFile(irodsFile, participantPath.toString());

                File allMissingIntervalsFile = new File(
                        String.format("%s/allm.v%d.interval_list", participantPath.toString(), listVersion));

                for (String missingInterval : missingIntervals) {
                    logger.debug("missingInterval: {}", missingInterval);
                    FileUtils.write(allMissingIntervalsFile, String.format("%s%n", missingInterval), true);
                    File missingSampleIntervalSummaryFile = runGATKDepthOfCoverageJob(bamFile, listVersion);

                    // reintegrate output
                    List<String> missingSampleIntervalSummaryFileLines = FileUtils.readLines(missingSampleIntervalSummaryFile);

                    if (missingSampleIntervalSummaryFileLines.size() == 1) {
                        // only contains header...missing results?
                        throw new BinningException("Missing coverage data");
                    }

                    if (missingSampleIntervalSummaryFileLines.size() > 1) {
                        // remove header
                        String lineToInject = missingSampleIntervalSummaryFileLines.get(1);
                        allIntervalSet.add(new GATKDepthInterval(lineToInject));

                        // File destFile = new File(depthFile.getParentFile(),
                        // depthFile.getName().replace(".sample_interval_summary", ".sample_interval_summary.orig"));
                        // FileUtils.moveFile(depthFile, destFile);
                        //
                        // try (FileWriter fw = new FileWriter(depthFile); BufferedWriter bw = new BufferedWriter(fw)) {
                        // for (GATKDepthInterval interval : intervalSet) {
                        // bw.write(interval.toString());
                        // bw.flush();
                        // }
                        // }

                    }
                }
            }
        } catch (IOException e) {
            throw new BinningException(e);
        }

    }

    private File runGATKDepthOfCoverageJob(File bamFile, Integer listVersion) throws BinningException {
        String referenceSequence = "/projects/mapseq/data/references/BUILD.37.1/bwa061sam0118/BUILD.37.1.sorted.shortid.fa";
        CommandInput commandInput = new CommandInput(String.format(
                "$JAVA7_HOME/bin/java -Xmx4g -jar $GATK_HOME/GenomeAnalysisTK.jar -T DepthOfCoverage -I %1$s -L m.v%2$s.interval_list -o missing.v%2$s -R %3$s -im OVERLAPPING_ONLY -omitLocusTable -omitBaseOutput -omitSampleSummary -ct 1 -ct 2 -ct 5 -ct 8 -ct 10 -ct 15 -ct 20 -ct 30 -ct 50",
                bamFile.getName(), listVersion, referenceSequence));
        commandInput.setWorkDir(bamFile.getParentFile());
        try {
            CommandOutput commandOutput = BashExecutor.getInstance().execute(commandInput);
            if (commandOutput.getExitCode() != 0) {
                throw new BinningException(String.format("Failed to run GATK DepthOfCoverage"));
            }
        } catch (ExecutorException e) {
            throw new BinningException(e);
        }
        return new File(bamFile.getParentFile(), String.format("missing.v%s", listVersion));
    }

}
