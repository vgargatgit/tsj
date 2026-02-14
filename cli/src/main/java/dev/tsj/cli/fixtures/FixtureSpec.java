package dev.tsj.cli.fixtures;

import java.nio.file.Path;
import java.util.List;

/**
 * Fixture definition loaded from fixture.properties.
 *
 * @param name fixture logical name
 * @param directory fixture root directory
 * @param entryFile input TypeScript entry file
 * @param nodeExpectation expected Node execution result
 * @param tsjExpectation expected TSJ execution result
 * @param assertNodeMatchesTsj whether to enforce Node-vs-TSJ direct equality
 * @param nodeArgs optional additional Node CLI args inserted before entry script
 * @param tsjArgs optional additional TSJ `run` args appended after `--out <dir>`
 */
public record FixtureSpec(
        String name,
        Path directory,
        Path entryFile,
        ExpectedRuntimeResult nodeExpectation,
        ExpectedRuntimeResult tsjExpectation,
        boolean assertNodeMatchesTsj,
        List<String> nodeArgs,
        List<String> tsjArgs
) {
}
