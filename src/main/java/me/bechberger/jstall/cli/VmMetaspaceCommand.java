package me.bechberger.jstall.cli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.jstall.analyzer.Analyzer;
import me.bechberger.jstall.analyzer.impl.VmMetaspaceAnalyzer;

/**
 * Shows VM.metaspace analysis.
 */
@Command(
    name = "vm-metaspace",
    description = "Show VM.metaspace summary and trend"
)
public class VmMetaspaceCommand extends BaseAnalyzerCommand {

    @Override
    protected Analyzer getAnalyzer() {
        return new VmMetaspaceAnalyzer();
    }
}
