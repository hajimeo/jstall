package me.bechberger.jstall.cli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.jstall.analyzer.Analyzer;
import me.bechberger.jstall.analyzer.impl.VmClassloaderStatsAnalyzer;

/**
 * Shows VM.classloader_stats analysis.
 */
@Command(
    name = "vm-classloader-stats",
    description = "Show VM.classloader_stats grouped by classloader type"
)
public class VmClassloaderStatsCommand extends BaseAnalyzerCommand {

    @Override
    protected Analyzer getAnalyzer() {
        return new VmClassloaderStatsAnalyzer();
    }
}
