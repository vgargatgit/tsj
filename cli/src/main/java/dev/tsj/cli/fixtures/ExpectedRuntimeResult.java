package dev.tsj.cli.fixtures;

/**
 * Expected process result for one runtime target.
 *
 * @param exitCode expected process exit code
 * @param stdout expected stdout content or substring
 * @param stderr expected stderr content or substring
 * @param stdoutMode match mode for stdout
 * @param stderrMode match mode for stderr
 */
public record ExpectedRuntimeResult(
        int exitCode,
        String stdout,
        String stderr,
        MatchMode stdoutMode,
        MatchMode stderrMode
) {
}
