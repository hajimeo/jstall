package me.bechberger.jstall.analyzer.impl;

import me.bechberger.jstall.analyzer.Analyzer;
import me.bechberger.jstall.analyzer.BaseAnalyzer;
import me.bechberger.jstall.analyzer.AnalyzerResult;
import me.bechberger.jstall.analyzer.DumpRequirement;
import me.bechberger.jstall.analyzer.ResolvedData;
import me.bechberger.jstall.provider.requirement.DataRequirements;
import me.bechberger.jstall.runner.AnalyzerRunner;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Meta-analyzer that runs multiple analyzers in sequence.
 *
 * Combines the results of DeadLockAnalyzer, MostWorkAnalyzer, ThreadsAnalyzer, and DependencyGraphAnalyzer.
 */
public class StatusAnalyzer extends BaseAnalyzer {


    private final List<? extends Analyzer> ANALYZERS = List.of(
        new DeadLockAnalyzer(),
        new MostWorkAnalyzer(),
        new ThreadsAnalyzer(),
        new DependencyGraphAnalyzer(),
        new SystemProcessAnalyzer(),
        new JvmSupportAnalyzer()
    );

    @Override
    public String name() {
        return "status";
    }

    @Override
    public Set<String> supportedOptions() {
        // Supports all options from constituent analyzers

        return ANALYZERS.stream()
            .flatMap(analyzer -> analyzer.supportedOptions().stream())
            .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public DumpRequirement dumpRequirement() {
        // Needs multiple dumps for MostWorkAnalyzer
        return DumpRequirement.MANY;
    }

    @SuppressWarnings("unchecked")
    @Override
    public DataRequirements getDataRequirements(Map<String, Object> options) {
        DataRequirements merged = DataRequirements.empty();
        for (Analyzer analyzer : (List<Analyzer>) ANALYZERS) {
            merged = merged.merge(analyzer.getDataRequirements(options));
        }
        return merged;
    }

    @SuppressWarnings("unchecked")
    @Override
    public AnalyzerResult analyze(ResolvedData data, Map<String, Object> options) {
        AnalyzerRunner runner = new AnalyzerRunner();

        var runResult = runner.runAnalyzers((List<Analyzer>) ANALYZERS, data.dumps(), options);

        return AnalyzerResult.withExitCode(runResult.output(), runResult.exitCode());
    }
}