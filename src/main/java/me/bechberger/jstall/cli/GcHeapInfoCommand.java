package me.bechberger.jstall.cli;

import me.bechberger.femtocli.annotations.Command;
import me.bechberger.jstall.analyzer.Analyzer;
import me.bechberger.jstall.analyzer.impl.GcHeapInfoAnalyzer;

/**
 * Shows GC.heap_info absolute values and delta against previous sample.
 */
@Command(
    name = "gc-heap-info",
    description = "Show GC.heap_info last absolute values and change"
)
public class GcHeapInfoCommand extends BaseAnalyzerCommand {

    @Override
    protected Analyzer getAnalyzer() {
        return new GcHeapInfoAnalyzer();
    }
}
