package me.bechberger.jstall.cli.record;

import me.bechberger.femtocli.annotations.Command;

import java.util.concurrent.Callable;

@Command(
    name = "record",
    description = "Record all data into a zip for later analysis",
    subcommands = {
        RecordCommand.class,
        RecordExtractCommand.class,
        RecordSummaryCommand.class
    },
    defaultSubcommand = RecordCommand.class
)
public class RecordMainCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        return 0;
    }
}
