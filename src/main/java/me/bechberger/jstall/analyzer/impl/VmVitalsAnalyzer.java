package me.bechberger.jstall.analyzer.impl;

import me.bechberger.jstall.analyzer.Analyzer;
import me.bechberger.jstall.analyzer.AnalyzerResult;
import me.bechberger.jstall.analyzer.DumpRequirement;
import me.bechberger.jstall.analyzer.ResolvedData;
import me.bechberger.jstall.provider.requirement.CollectedData;
import me.bechberger.jstall.provider.requirement.DataRequirements;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Displays VM vitals information from VM.vitals jcmd command (SapMachine-specific).
 *
 * Shows the last n rows of VM.vitals data (configurable via --top option, default: 5).
 */
public class VmVitalsAnalyzer implements Analyzer {

    @Override
    public String name() {
        return "vm-vitals";
    }

    @Override
    public Set<String> supportedOptions() {
        return Set.of("top");
    }

    @Override
    public DumpRequirement dumpRequirement() {
        return DumpRequirement.ANY;
    }

    @Override
    public DataRequirements getDataRequirements(Map<String, Object> options) {
        return DataRequirements.builder()
            .addThreadDump()
            .addJcmdOnce("VM.vitals")
            .build();
    }

    @Override
    public AnalyzerResult analyze(ResolvedData data, Map<String, Object> options) {
        StringBuilder output = new StringBuilder();

        List<CollectedData> vitalsSamples = data.collectedData("vm-vitals");
        if (!vitalsSamples.isEmpty()) {
            String rawVitals = vitalsSamples.get(vitalsSamples.size() - 1).rawData();
            if (rawVitals != null && !rawVitals.isBlank()) {
                int top = getIntOption(options, "top", 5);
                String vmVitalsOutput = formatVmVitals(rawVitals, top);
                if (!vmVitalsOutput.isEmpty()) {
                    output.append(vmVitalsOutput);
                }
            }
        }

        if (output.isEmpty()) {
            return AnalyzerResult.nothing();
        }

        return AnalyzerResult.ok(output.toString());
    }

    private int getIntOption(Map<String, Object> options, String key, int defaultValue) {
        Object value = options.get(key);
        if (value instanceof Integer i) {
            return i;
        } else if (value instanceof Number n) {
            return n.intValue();
        }
        return defaultValue;
    }

    private String formatVmVitals(String rawVitals, int topN) {
        if (rawVitals == null || rawVitals.isBlank()) {
            return "";
        }

        String[] lines = rawVitals.split("\n");
        List<String> dataLines = new ArrayList<>();
        String headerLine = null;
        String columnHeaderLine = null;

        // Find header and data lines
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            
            // Find the column header (line with "comm used" pattern)
            if (headerLine == null && line.contains("comm") && line.contains("used")) {
                headerLine = lines[i];
                // The next line should be the column names
                if (i + 1 < lines.length) {
                    columnHeaderLine = lines[i + 1];
                }
                continue;
            }
            
            // Data lines start with a date (YYYY-MM-DD)
            if (line.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                dataLines.add(lines[i]);
            }
        }

        if (dataLines.isEmpty()) {
            return "";
        }

        // Get the last N data lines
        int startIndex = Math.max(0, dataLines.size() - topN);
        List<String> lastNLines = dataLines.subList(startIndex, dataLines.size());

        StringBuilder sb = new StringBuilder();
        sb.append("VM Vitals:\n");
        
        // Include header if available
        if (headerLine != null) {
            sb.append(headerLine).append("\n");
        }
        if (columnHeaderLine != null) {
            sb.append(columnHeaderLine).append("\n");
        }
        
        // Add the last N data lines
        for (String line : lastNLines) {
            sb.append(line).append("\n");
        }

        return sb.toString().trim();
    }

}
